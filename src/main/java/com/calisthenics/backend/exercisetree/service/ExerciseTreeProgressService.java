package com.calisthenics.backend.exercisetree.service;

import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.exercisetree.dto.ExerciseNodeProgressDto;
import com.calisthenics.backend.exercisetree.entity.ExerciseProgressionEdge;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeNode;
import com.calisthenics.backend.exercisetree.type.ExerciseNodeProgressStatus;
import com.calisthenics.backend.exercisetree.type.MasteryCriteriaType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionExerciseRepository;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Computes athlete progression status (LOCKED, AVAILABLE, IN_PROGRESS, MASTERED) for each
 * ExerciseTreeNode using existing Personal Records and completed Workout Session history.
 *
 * Key invariant: Recording a single PR NEVER automatically marks an exercise MASTERED —
 * mastery requires meeting exercise-specific volume/benchmark criteria across the configured
 * minimum number of completed workout sessions.
 */
@Service
@RequiredArgsConstructor
public class ExerciseTreeProgressService {

    private final PersonalRecordRepository personalRecordRepository;
    private final WorkoutSessionExerciseRepository workoutSessionExerciseRepository;

    @Transactional(readOnly = true)
    public Map<Long, ExerciseNodeProgressDto> evaluateUserProgress(
            Long userId,
            List<ExerciseTreeNode> allNodes,
            List<ExerciseProgressionEdge> allEdges
    ) {
        if (userId == null || allNodes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<PersonalRecord> userPrs = personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId);
        List<WorkoutSessionExercise> completedSessionExercises =
                workoutSessionExerciseRepository.findByUserIdAndSessionStatusWithSets(userId, WorkoutSessionStatus.COMPLETED);

        Map<Long, ExerciseHistoryStats> statsByExerciseId = aggregateHistory(userPrs, completedSessionExercises);

        // Choose a canonical primary node per exerciseId for criteria evaluation
        Map<Long, ExerciseTreeNode> canonicalNodeByExerciseId = new LinkedHashMap<>();
        for (ExerciseTreeNode node : allNodes) {
            Long exId = node.getExercise().getId();
            ExerciseTreeNode existing = canonicalNodeByExerciseId.get(exId);
            if (existing == null || (!existing.isPrimaryPlacement() && node.isPrimaryPlacement())) {
                canonicalNodeByExerciseId.put(exId, node);
            }
        }

        // First pass: evaluate raw mastery & activity independent of prerequisites
        Map<Long, RawEvaluation> rawByExerciseId = new HashMap<>();
        for (Map.Entry<Long, ExerciseTreeNode> entry : canonicalNodeByExerciseId.entrySet()) {
            Long exId = entry.getKey();
            ExerciseTreeNode node = entry.getValue();
            ExerciseHistoryStats stats = statsByExerciseId.getOrDefault(exId, ExerciseHistoryStats.empty());
            rawByExerciseId.put(exId, evaluateRawCriteria(node, stats));
        }

        // Build incoming prerequisite & incoming NEXT maps
        Map<Long, Set<Long>> strictPrerequisitesByTarget = new HashMap<>();
        Map<Long, Set<Long>> incomingNextByTarget = new HashMap<>();
        for (ExerciseProgressionEdge edge : allEdges) {
            Long sourceId = edge.getSourceExercise().getId();
            Long targetId = edge.getTargetExercise().getId();
            if (edge.getRelationType() == ProgressionRelationType.PREREQUISITE) {
                strictPrerequisitesByTarget.computeIfAbsent(targetId, k -> new LinkedHashSet<>()).add(sourceId);
            } else if (edge.getRelationType() == ProgressionRelationType.NEXT) {
                incomingNextByTarget.computeIfAbsent(targetId, k -> new LinkedHashSet<>()).add(sourceId);
            }
        }

        // Second pass: determine final status (LOCKED vs AVAILABLE vs IN_PROGRESS vs MASTERED)
        Map<Long, ExerciseNodeProgressDto> result = new LinkedHashMap<>();
        for (Map.Entry<Long, ExerciseTreeNode> entry : canonicalNodeByExerciseId.entrySet()) {
            Long exId = entry.getKey();
            ExerciseTreeNode node = entry.getValue();
            RawEvaluation raw = rawByExerciseId.get(exId);

            Set<Long> strictPrereqs = strictPrerequisitesByTarget.getOrDefault(exId, Collections.emptySet());
            Set<Long> incomingNext = incomingNextByTarget.getOrDefault(exId, Collections.emptySet());

            List<Long> unmetPrereqs = new ArrayList<>();
            for (Long prereqId : strictPrereqs) {
                RawEvaluation prereqEval = rawByExerciseId.get(prereqId);
                if (prereqEval == null || !prereqEval.mastered()) {
                    unmetPrereqs.add(prereqId);
                }
            }

            // For branching NEXT paths (e.g., Frog Stand OR Planche Lean -> Tuck Planche),
            // mastering ANY incoming NEXT progression unlocks the path.
            boolean nextPathSatisfied = true;
            if (!incomingNext.isEmpty() && node.getTreeTier() > 1) {
                boolean anyNextMastered = incomingNext.stream()
                        .anyMatch(prevId -> {
                            RawEvaluation prevEval = rawByExerciseId.get(prevId);
                            return prevEval != null && prevEval.mastered();
                        });
                if (!anyNextMastered) {
                    nextPathSatisfied = false;
                    for (Long prevId : incomingNext) {
                        if (!unmetPrereqs.contains(prevId)) {
                            unmetPrereqs.add(prevId);
                        }
                    }
                }
            }

            boolean prerequisitesMet = unmetPrereqs.isEmpty() && nextPathSatisfied;
            ExerciseNodeProgressStatus status;
            String statusReason;

            if (raw.mastered()) {
                status = ExerciseNodeProgressStatus.MASTERED;
                statusReason = String.format("Mastered (%d/%d qualifying workouts completed)",
                        raw.qualifyingSessions(), raw.requiredSessions());
            } else if (raw.hasActivity()) {
                status = ExerciseNodeProgressStatus.IN_PROGRESS;
                statusReason = buildInProgressReason(node, raw);
            } else if (prerequisitesMet || node.getTreeTier() <= 1) {
                status = ExerciseNodeProgressStatus.AVAILABLE;
                statusReason = "Ready to train — " + (node.getMasterySummary() != null
                        ? node.getMasterySummary()
                        : "complete " + raw.requiredSessions() + " workouts to master");
            } else {
                status = ExerciseNodeProgressStatus.LOCKED;
                statusReason = String.format("Locked — master %d prerequisite progression%s to unlock",
                        unmetPrereqs.size(), unmetPrereqs.size() == 1 ? "" : "s");
            }

            result.put(exId, ExerciseNodeProgressDto.builder()
                    .exerciseId(exId)
                    .status(status)
                    .progressPercent(raw.progressPercent())
                    .completedSessions(raw.completedSessions())
                    .qualifyingSessions(raw.qualifyingSessions())
                    .requiredSessions(raw.requiredSessions())
                    .bestReps(raw.bestReps())
                    .bestHoldSeconds(raw.bestHoldSeconds())
                    .bestWeightKg(raw.bestWeightKg())
                    .personalRecordCount(raw.personalRecordCount())
                    .prerequisitesMet(prerequisitesMet)
                    .unmetPrerequisiteExerciseIds(unmetPrereqs)
                    .statusReason(statusReason)
                    .build());
        }

        return result;
    }

    private Map<Long, ExerciseHistoryStats> aggregateHistory(
            List<PersonalRecord> prs,
            List<WorkoutSessionExercise> sessionExercises
    ) {
        Map<Long, MutableStats> map = new HashMap<>();

        for (PersonalRecord pr : prs) {
            if (pr.getExercise() == null || pr.getExercise().getId() == null) {
                continue;
            }
            Long exId = pr.getExercise().getId();
            MutableStats s = map.computeIfAbsent(exId, k -> new MutableStats());
            s.personalRecordCount++;
            double val = pr.getRecordValue() != null ? pr.getRecordValue().doubleValue() : 0.0;
            PersonalRecordType type = pr.getRecordType();
            if (type == PersonalRecordType.MAX_REPS || type == PersonalRecordType.BODYWEIGHT) {
                s.bestReps = maxInt(s.bestReps, (int) Math.floor(val));
            } else if (type == PersonalRecordType.MAX_HOLD || type == PersonalRecordType.MAX_DURATION) {
                s.bestHoldSeconds = maxInt(s.bestHoldSeconds, (int) Math.floor(val));
            } else if (type == PersonalRecordType.MAX_WEIGHT) {
                s.bestWeightKg = maxDouble(s.bestWeightKg, val);
                if (pr.getSecondaryValue() != null) {
                    s.bestReps = maxInt(s.bestReps, pr.getSecondaryValue().intValue());
                }
            }
        }

        // Group completed sets by (exerciseId, workoutSessionId) so multiple sets in one workout count as 1 session
        Map<Long, Map<Long, List<WorkoutSessionSet>>> setsByExerciseAndSession = new HashMap<>();
        for (WorkoutSessionExercise wse : sessionExercises) {
            if (wse.getExercise() == null || wse.getExercise().getId() == null || wse.getWorkoutSession() == null) {
                continue;
            }
            Long exId = wse.getExercise().getId();
            Long sessionId = wse.getWorkoutSession().getId();
            List<WorkoutSessionSet> completedSets = wse.getSets().stream()
                    .filter(WorkoutSessionSet::isCompleted)
                    .toList();
            if (completedSets.isEmpty()) {
                continue;
            }
            setsByExerciseAndSession
                    .computeIfAbsent(exId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(sessionId, k -> new ArrayList<>())
                    .addAll(completedSets);
        }

        for (Map.Entry<Long, Map<Long, List<WorkoutSessionSet>>> entry : setsByExerciseAndSession.entrySet()) {
            Long exId = entry.getKey();
            MutableStats s = map.computeIfAbsent(exId, k -> new MutableStats());
            for (List<WorkoutSessionSet> sets : entry.getValue().values()) {
                s.sessionPerformances.add(sets);
                for (WorkoutSessionSet set : sets) {
                    if (set.getReps() != null && set.getReps() > 0) {
                        s.bestReps = maxInt(s.bestReps, set.getReps());
                    }
                    if (set.getHoldDurationSeconds() != null && set.getHoldDurationSeconds() > 0) {
                        s.bestHoldSeconds = maxInt(s.bestHoldSeconds, set.getHoldDurationSeconds());
                    }
                    if (set.getWeightKg() != null && set.getWeightKg().doubleValue() > 0) {
                        s.bestWeightKg = maxDouble(s.bestWeightKg, set.getWeightKg().doubleValue());
                    }
                }
            }
        }

        Map<Long, ExerciseHistoryStats> out = new HashMap<>();
        for (Map.Entry<Long, MutableStats> e : map.entrySet()) {
            MutableStats m = e.getValue();
            out.put(e.getKey(), new ExerciseHistoryStats(
                    m.sessionPerformances,
                    m.personalRecordCount,
                    m.bestReps,
                    m.bestHoldSeconds,
                    m.bestWeightKg
            ));
        }
        return out;
    }

    private RawEvaluation evaluateRawCriteria(ExerciseTreeNode node, ExerciseHistoryStats stats) {
        int requiredSessions = node.getMasteryMinCompletedSessions() != null && node.getMasteryMinCompletedSessions() > 0
                ? node.getMasteryMinCompletedSessions()
                : 3;
        int requiredSets = node.getMasteryTargetSets() != null && node.getMasteryTargetSets() > 0
                ? node.getMasteryTargetSets()
                : 3;

        int completedSessions = stats.sessionPerformances().size();
        int qualifyingSessions = 0;

        for (List<WorkoutSessionSet> sessionSets : stats.sessionPerformances()) {
            if (sessionMeetsCriteria(node, sessionSets, requiredSets)) {
                qualifyingSessions++;
            }
        }

        boolean hasActivity = completedSessions > 0 || stats.personalRecordCount() > 0;

        // Strict mastery rule: NEVER mastered from PRs alone; must complete required qualifying sessions.
        boolean mastered = qualifyingSessions >= requiredSessions;

        // Progress percentage combines session consistency (60%) + best performance vs target (40%),
        // capped at 95% until full mastery criteria are satisfied.
        int progressPercent;
        if (mastered) {
            progressPercent = 100;
        } else if (!hasActivity) {
            progressPercent = 0;
        } else {
            double sessionRatio = Math.min(1.0, (double) qualifyingSessions / requiredSessions);
            double completedFallbackRatio = Math.min(1.0, (double) completedSessions / requiredSessions) * 0.5;
            double effectiveSessionScore = Math.max(sessionRatio, completedFallbackRatio);
            double performanceRatio = computeBestPerformanceRatio(node, stats);
            int computed = (int) Math.round((effectiveSessionScore * 60.0) + (performanceRatio * 40.0));
            progressPercent = Math.max(5, Math.min(95, computed));
        }

        return new RawEvaluation(
                mastered,
                hasActivity,
                progressPercent,
                completedSessions,
                qualifyingSessions,
                requiredSessions,
                stats.bestReps(),
                stats.bestHoldSeconds(),
                stats.bestWeightKg(),
                stats.personalRecordCount()
        );
    }

    private boolean sessionMeetsCriteria(
            ExerciseTreeNode node,
            List<WorkoutSessionSet> sets,
            int requiredSets
    ) {
        MasteryCriteriaType type = node.getMasteryCriteriaType() != null
                ? node.getMasteryCriteriaType()
                : MasteryCriteriaType.COMPLETED_SESSIONS;

        return switch (type) {
            case REPS -> {
                int targetReps = node.getMasteryTargetReps() != null ? node.getMasteryTargetReps() : 8;
                long qualifyingSets = sets.stream()
                        .filter(s -> s.getReps() != null && s.getReps() >= targetReps)
                        .count();
                yield qualifyingSets >= requiredSets;
            }
            case HOLD_SECONDS -> {
                int targetHold = node.getMasteryTargetHoldSeconds() != null ? node.getMasteryTargetHoldSeconds() : 15;
                long qualifyingSets = sets.stream()
                        .filter(s -> s.getHoldDurationSeconds() != null && s.getHoldDurationSeconds() >= targetHold)
                        .count();
                yield qualifyingSets >= requiredSets;
            }
            case WEIGHTED_LOAD -> {
                int targetReps = node.getMasteryTargetReps() != null ? node.getMasteryTargetReps() : 5;
                double targetWeight = node.getMasteryTargetWeightKg() != null ? node.getMasteryTargetWeightKg() : 0.0;
                long qualifyingSets = sets.stream()
                        .filter(s -> s.getReps() != null && s.getReps() >= targetReps
                                && s.getWeightKg() != null && s.getWeightKg().doubleValue() >= targetWeight)
                        .count();
                yield qualifyingSets >= requiredSets;
            }
            case COMBINED -> {
                long qualifyingSets = sets.stream()
                        .filter(s -> (node.getMasteryTargetReps() == null || (s.getReps() != null && s.getReps() >= node.getMasteryTargetReps()))
                                && (node.getMasteryTargetHoldSeconds() == null || (s.getHoldDurationSeconds() != null && s.getHoldDurationSeconds() >= node.getMasteryTargetHoldSeconds()))
                                && (node.getMasteryTargetWeightKg() == null || (s.getWeightKg() != null && s.getWeightKg().doubleValue() >= node.getMasteryTargetWeightKg())))
                        .count();
                yield qualifyingSets >= requiredSets;
            }
            case COMPLETED_SESSIONS -> sets.size() >= Math.min(requiredSets, 1);
        };
    }

    private double computeBestPerformanceRatio(ExerciseTreeNode node, ExerciseHistoryStats stats) {
        MasteryCriteriaType type = node.getMasteryCriteriaType() != null
                ? node.getMasteryCriteriaType()
                : MasteryCriteriaType.COMPLETED_SESSIONS;

        return switch (type) {
            case REPS -> {
                if (node.getMasteryTargetReps() == null || node.getMasteryTargetReps() <= 0 || stats.bestReps() == null) {
                    yield 0.0;
                }
                yield Math.min(1.0, (double) stats.bestReps() / node.getMasteryTargetReps());
            }
            case HOLD_SECONDS -> {
                if (node.getMasteryTargetHoldSeconds() == null || node.getMasteryTargetHoldSeconds() <= 0 || stats.bestHoldSeconds() == null) {
                    yield 0.0;
                }
                yield Math.min(1.0, (double) stats.bestHoldSeconds() / node.getMasteryTargetHoldSeconds());
            }
            case WEIGHTED_LOAD -> {
                double repPart = (node.getMasteryTargetReps() != null && node.getMasteryTargetReps() > 0 && stats.bestReps() != null)
                        ? Math.min(1.0, (double) stats.bestReps() / node.getMasteryTargetReps()) : 0.0;
                double weightPart = (node.getMasteryTargetWeightKg() != null && node.getMasteryTargetWeightKg() > 0 && stats.bestWeightKg() != null)
                        ? Math.min(1.0, stats.bestWeightKg() / node.getMasteryTargetWeightKg()) : 0.0;
                yield (repPart + weightPart) / 2.0;
            }
            case COMBINED, COMPLETED_SESSIONS -> stats.sessionPerformances().isEmpty() ? 0.25 : 0.75;
        };
    }

    private String buildInProgressReason(ExerciseTreeNode node, RawEvaluation raw) {
        StringBuilder sb = new StringBuilder();
        sb.append(raw.qualifyingSessions()).append("/").append(raw.requiredSessions()).append(" qualifying workouts");
        if (raw.completedSessions() == 0 && raw.personalRecordCount() > 0) {
            sb.append(" · PR logged (complete ").append(raw.requiredSessions())
                    .append(" workouts meeting criteria to master)");
        } else if (node.getMasterySummary() != null && !node.getMasterySummary().isBlank()) {
            sb.append(" · Target: ").append(node.getMasterySummary());
        }
        return sb.toString();
    }

    private static Integer maxInt(Integer a, int b) {
        return a == null ? b : Math.max(a, b);
    }

    private static Double maxDouble(Double a, double b) {
        return a == null ? b : Math.max(a, b);
    }

    private static final class MutableStats {
        final List<List<WorkoutSessionSet>> sessionPerformances = new ArrayList<>();
        int personalRecordCount = 0;
        Integer bestReps = null;
        Integer bestHoldSeconds = null;
        Double bestWeightKg = null;
    }

    private record ExerciseHistoryStats(
            List<List<WorkoutSessionSet>> sessionPerformances,
            int personalRecordCount,
            Integer bestReps,
            Integer bestHoldSeconds,
            Double bestWeightKg
    ) {
        static ExerciseHistoryStats empty() {
            return new ExerciseHistoryStats(Collections.emptyList(), 0, null, null, null);
        }
    }

    private record RawEvaluation(
            boolean mastered,
            boolean hasActivity,
            int progressPercent,
            int completedSessions,
            int qualifyingSessions,
            int requiredSessions,
            Integer bestReps,
            Integer bestHoldSeconds,
            Double bestWeightKg,
            int personalRecordCount
    ) {}
}
