package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.aichat.dto.GeminiAssistantOutput;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationItemDto;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationsCardDto;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationsCardDto.MuscleGapInsightDto;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationsCardDto.PlanDayOptionDto;
import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto;
import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto.ConsultationFindingDto;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseMediaMapper;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import com.calisthenics.backend.exercise.muscle.MuscleId;
import com.calisthenics.backend.exercise.muscle.MuscleRegion;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.skill.service.SkillCatalogService;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context-aware exercise recommendation and workout routine consultation engine.
 * Reads the user's real active workout plan, muscle coverage, profile, PRs, and published catalog,
 * without ever inventing exercise IDs or mutating the schedule directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkoutRecommendationService {

    private static final double HIGH_WEEKLY_MUSCLE_SETS_THRESHOLD = 18.0;
    private static final int HIGH_DAY_EXERCISE_THRESHOLD = 6;
    private static final Pattern COUNT_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s+(?:[a-zA-ZÀ-ỹ-]+\\s+){0,3}(?:exercises?|movements?|bài tập)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_KEYWORDS = Pattern.compile("\\b(planche|front lever|back lever|handstand push[- ]?ups?|handstand|hspu|muscle[ -]?up|l[ -]?sit|human flag|pistol(?: squat)?|shrimp squat|nordic(?: curl)?|v[ -]?sit|dragon flag|iron cross|bench press|incline bench(?: press)?|overhead press|lat pulldown|barbell row|seated cable row|romanian deadlift|rdl|leg press|leg extension|leg curl|calf raise|cable crunch|lateral raise|bicep curl|triceps? extension|face pull|barbell squat)\\b", Pattern.CASE_INSENSITIVE);

    private final ExerciseRepository exerciseRepository;
    private final MuscleTaxonomyService muscleTaxonomyService;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutPlanAssignmentRepository workoutPlanAssignmentRepository;
    private final CoachClientRelationshipRepository coachClientRelationshipRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private GeminiApiClient geminiApiClient;

    public record RecommendationOutcome(
            String intent,
            String reply,
            String metadataJson,
            ExerciseRecommendationsCardDto card
    ) {}

    public record ConsultationOutcome(
            String intent,
            String reply,
            String metadataJson,
            WorkoutConsultationCardDto card
    ) {}

    private record PromptCriteria(
            Set<MuscleId> targetMuscles,
            Set<ExerciseCategory> targetCategories,
            List<String> skillKeywords,
            boolean wholeRoutineGapAnalysis,
            boolean lowerBodyFocus,
            boolean upperPushFocus,
            boolean upperPullFocus,
            boolean coreFocus,
            boolean bodyweightOnly,
            ExerciseTrainingType requestedTrainingType,
            String equipmentFilter,
            String focusLabel,
            String listHeading,
            String criteriaKey,
            int requestedCount,
            boolean hasExplicitFilter
    ) {}

    private record ActiveTopicHistory(
            String topicPrompt,
            String criteriaKey,
            String lastIntent,
            boolean usedSupplementalAi,
            ExerciseDeduplicator.DeduplicationState exclusions
    ) {}

    private record SupplementalCandidate(
            String name,
            String description,
            Exercise catalogMatch
    ) {}

    private record PlanSnapshot(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            Map<Long, List<WorkoutPlanExercise>> exercisesByDayId,
            Map<MuscleId, Double> weeklyMuscleSets,
            Map<Long, Map<MuscleId, Double>> dayMuscleSets,
            Map<Long, Set<String>> existingPlanDaysByExerciseId
    ) {}

    private record ScoredCandidate(
            Exercise exercise,
            MuscleTaxonomyService.ResolvedMuscleTargets targets,
            List<MuscleId> primaryMuscles,
            List<MuscleId> secondaryMuscles,
            List<String> filledGaps,
            List<String> alreadyInPlanDays,
            double score,
            String volumeWarning,
            String rationale
    ) {}

    @Transactional(readOnly = true)
    public RecommendationOutcome listExercises(
            Long userId,
            String prompt,
            List<AiMessage> priorHistory,
            boolean isAnotherFollowUp,
            boolean isVn
    ) {
        List<Exercise> publishedCatalog = loadPublishedCatalog();
        PromptCriteria rawCriteria = parsePromptCriteria(prompt, null);
        ActiveTopicHistory history = extractActiveTopicHistory(priorHistory, publishedCatalog, rawCriteria, isAnotherFollowUp);
        PromptCriteria effectiveCriteria = resolveEffectiveCriteria(rawCriteria, prompt, history, isAnotherFollowUp);

        if (isAnotherFollowUp && history.exclusions().isEmpty() && !rawCriteria.hasExplicitFilter()) {
            return new RecommendationOutcome(
                    "LIST_EXERCISES",
                    buildNoPriorTopicMessage(isVn),
                    null,
                    null
            );
        }

        List<ScoredCandidate> allMatches = rankCatalogCandidates(
                publishedCatalog, effectiveCriteria, emptyPlanSnapshot(), null, List.of(), Set.of(), List.of(), Map.of(), null, null, false, isVn
        );
        List<ScoredCandidate> unseenCatalog = allMatches.stream()
                .filter(sc -> !history.exclusions().isExcluded(sc.exercise()))
                .toList();

        int limit = Math.min(effectiveCriteria.requestedCount(), unseenCatalog.size());
        List<ScoredCandidate> chosenCatalog = unseenCatalog.subList(0, limit);
        List<SupplementalCandidate> supplemental = List.of();
        boolean usedSupplementalAi = false;

        int deficit = effectiveCriteria.requestedCount() - chosenCatalog.size();
        boolean sparseCatalog = allMatches.size() < 3 && (!isAnotherFollowUp || history.usedSupplementalAi());
        if (deficit > 0 && sparseCatalog && geminiApiClient != null) {
            ExerciseDeduplicator.DeduplicationState tempExclusions = copyDeduplicationState(history.exclusions());
            for (ScoredCandidate sc : chosenCatalog) {
                tempExclusions.recordExcludedCatalogExercise(sc.exercise());
            }
            supplemental = fetchSupplementalFromGemini(
                    prompt, effectiveCriteria, tempExclusions, deficit, publishedCatalog, false, isVn);
            usedSupplementalAi = !supplemental.isEmpty() || history.usedSupplementalAi();
        }

        if (chosenCatalog.isEmpty() && supplemental.isEmpty()) {
            String msg = isAnotherFollowUp
                    ? buildNoMoreExercisesMessage(effectiveCriteria, isVn)
                    : (isVn
                        ? "Hiện chưa có bài tập phù hợp nào trong danh mục."
                        : "No published exercises matching that focus are currently available in the catalog.");
            String exhaustedMeta = buildExerciseListMetadata(
                    effectiveCriteria, prompt, history, List.of(), List.of(), isAnotherFollowUp && history.usedSupplementalAi(), true);
            return new RecommendationOutcome("LIST_EXERCISES", msg, exhaustedMeta, null);
        }

        List<ExerciseRecommendationItemDto> catalogItems = chosenCatalog.stream()
                .map(sc -> toInformationalItem(sc, false))
                .toList();
        String markdown = formatListMarkdown(effectiveCriteria, catalogItems, supplemental, isVn);
        String meta = buildExerciseListMetadata(
                effectiveCriteria, prompt, history, catalogItems, supplemental, usedSupplementalAi, false);
        return new RecommendationOutcome("LIST_EXERCISES", markdown, meta, null);
    }

    @Transactional(readOnly = true)
    public RecommendationOutcome recommendExercises(
            Long userId,
            String prompt,
            WorkoutDayOfWeek selectedDayOfWeek,
            boolean isVn
    ) {
        return recommendExercises(
                userId, prompt, selectedDayOfWeek,
                planContextExplicitlyRequested(prompt), List.of(), false, isVn);
    }

    @Transactional(readOnly = true)
    public RecommendationOutcome recommendExercises(
            Long userId,
            String prompt,
            WorkoutDayOfWeek selectedDayOfWeek,
            boolean planContextRequested,
            List<AiMessage> priorHistory,
            boolean isAnotherFollowUp,
            boolean isVn
    ) {
        boolean includePlanAnalysis = planContextRequested && !isAnotherFollowUp;
        List<Exercise> publishedCatalog = loadPublishedCatalog();

        ClientProfile profile = includePlanAnalysis && userId != null
                ? clientProfileRepository.findByUserId(userId).orElse(null)
                : null;
        PromptCriteria rawCriteria = parsePromptCriteria(prompt, profile);
        ActiveTopicHistory history = extractActiveTopicHistory(priorHistory, publishedCatalog, rawCriteria, isAnotherFollowUp);
        PromptCriteria criteria = resolveEffectiveCriteria(rawCriteria, prompt, history, isAnotherFollowUp);

        if (!includePlanAnalysis) {
            if (isAnotherFollowUp && history.exclusions().isEmpty() && !rawCriteria.hasExplicitFilter()) {
                return new RecommendationOutcome(
                        "EXERCISE_RECOMMENDATION",
                        buildNoPriorTopicMessage(isVn),
                        null,
                        null
                );
            }

            List<ScoredCandidate> allMatches = rankCatalogCandidates(
                    publishedCatalog, criteria, emptyPlanSnapshot(), null, List.of(), Set.of(), List.of(), Map.of(), null, null, false, isVn
            );
            List<ScoredCandidate> unseenCatalog = allMatches.stream()
                    .filter(sc -> !history.exclusions().isExcluded(sc.exercise()))
                    .toList();

            int limit = Math.min(criteria.requestedCount(), unseenCatalog.size());
            List<ScoredCandidate> chosenCatalog = unseenCatalog.subList(0, limit);
            List<SupplementalCandidate> supplemental = List.of();
            boolean usedSupplementalAi = false;

            int deficit = criteria.requestedCount() - chosenCatalog.size();
            boolean sparseCatalog = allMatches.size() < 3 && (!isAnotherFollowUp || history.usedSupplementalAi());
            if (deficit > 0 && sparseCatalog && geminiApiClient != null) {
                ExerciseDeduplicator.DeduplicationState tempExclusions = copyDeduplicationState(history.exclusions());
                for (ScoredCandidate sc : chosenCatalog) {
                    tempExclusions.recordExcludedCatalogExercise(sc.exercise());
                }
                supplemental = fetchSupplementalFromGemini(
                        prompt, criteria, tempExclusions, deficit, publishedCatalog, true, isVn);
                usedSupplementalAi = !supplemental.isEmpty() || history.usedSupplementalAi();
            }

            if (chosenCatalog.isEmpty() && supplemental.isEmpty()) {
                String emptyMsg = isAnotherFollowUp
                        ? buildNoMoreExercisesMessage(criteria, isVn)
                        : (isVn
                            ? "Hiện chưa có bài tập phù hợp nào được xuất bản trong danh mục. Vui lòng thử lại sau."
                            : "No published exercises matching that focus are currently available in the catalog.");
                ExerciseRecommendationsCardDto emptyCard = ExerciseRecommendationsCardDto.builder()
                        .informationalOnly(true)
                        .focusLabel(criteria.focusLabel())
                        .criteriaKey(criteria.criteriaKey())
                        .topicPrompt(effectiveTopicPrompt(prompt, history))
                        .recommendations(List.of())
                        .build();
                String metaJson = injectExtraFieldsIntoJson(safeToJson(emptyCard), usedSupplementalAi || history.usedSupplementalAi(), supplemental);
                return new RecommendationOutcome("EXERCISE_RECOMMENDATION", emptyMsg, metaJson, emptyCard);
            }

            List<ExerciseRecommendationItemDto> items = chosenCatalog.stream()
                    .map(sc -> toInformationalItem(sc, true))
                    .toList();

            ExerciseRecommendationsCardDto card = ExerciseRecommendationsCardDto.builder()
                    .informationalOnly(true)
                    .focusLabel(criteria.focusLabel())
                    .criteriaKey(criteria.criteriaKey())
                    .topicPrompt(effectiveTopicPrompt(prompt, history))
                    .recommendations(items)
                    .build();

            Map<Long, Exercise> catalogById = publishedCatalog.stream()
                    .collect(Collectors.toMap(Exercise::getId, e -> e, (a, b) -> a));
            ExerciseRecommendationsCardDto validatedCard = validateAndSanitizeCard(card, catalogById);

            String markdown = formatInformationalRecommendationsMarkdown(criteria, validatedCard.getRecommendations(), supplemental, isVn);
            String metaJson = injectExtraFieldsIntoJson(safeToJson(validatedCard), usedSupplementalAi, supplemental);
            return new RecommendationOutcome("EXERCISE_RECOMMENDATION", markdown, metaJson, validatedCard);
        }

        // Explicit workout plan / day-context recommendation path (user asked to add to a day or inspect missing routine muscles)
        PlanSnapshot snapshot = loadPlanSnapshot(userId);
        List<PersonalRecord> recentPrs = userId != null
                ? personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId)
                : List.of();
        List<WorkoutSession> recentSessions = userId != null
                ? workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(userId, WorkoutSessionStatus.COMPLETED)
                : List.of();

        WorkoutPlanDay suggestedDay = resolveSuggestedTargetDay(prompt, selectedDayOfWeek, criteria, snapshot);
        WorkoutDayOfWeek suggestedDow = suggestedDay != null ? suggestedDay.getDayOfWeek() : null;

        if (publishedCatalog.isEmpty()) {
            String emptyMsg = isVn
                    ? "Hiện chưa có bài tập phù hợp nào được xuất bản trong danh mục. Vui lòng thử lại sau."
                    : "No published exercises matching that focus are currently available in the catalog.";
            ExerciseRecommendationsCardDto emptyCard = ExerciseRecommendationsCardDto.builder()
                    .focusLabel(criteria.focusLabel())
                    .criteriaKey(criteria.criteriaKey())
                    .topicPrompt(effectiveTopicPrompt(prompt, history))
                    .suggestedDayOfWeek(suggestedDow)
                    .availablePlanDays(buildPlanDayOptions(snapshot, isVn))
                    .recommendations(List.of())
                    .build();
            return new RecommendationOutcome("EXERCISE_RECOMMENDATION", emptyMsg, safeToJson(emptyCard), emptyCard);
        }

        List<MuscleGapInsightDto> undertrained = computeUndertrainedInsights(
                criteria, snapshot, suggestedDay, isVn);

        Set<Long> targetDayExerciseIds = new HashSet<>();
        List<WorkoutPlanExercise> targetDayExercises = List.of();
        if (suggestedDay != null) {
            targetDayExercises = snapshot.exercisesByDayId().getOrDefault(suggestedDay.getId(), List.of());
            for (WorkoutPlanExercise pe : targetDayExercises) {
                if (pe.getExercise() != null && pe.getExercise().getId() != null) {
                    targetDayExerciseIds.add(pe.getExercise().getId());
                }
            }
        }

        DifficultyLevel userDifficulty = resolveUserDifficulty(profile, recentSessions, recentPrs);
        Set<String> targetDayTextPatterns = extractMovementPatterns(targetDayExercises);
        Map<MuscleId, Double> referenceSets = suggestedDay != null
                ? snapshot.dayMuscleSets().getOrDefault(suggestedDay.getId(), snapshot.weeklyMuscleSets())
                : snapshot.weeklyMuscleSets();

        List<ScoredCandidate> ranked = new ArrayList<>();
        for (Exercise ex : publishedCatalog) {
            if (targetDayExerciseIds.contains(ex.getId())) {
                continue; // Do not recommend duplicates of exercises already in the target day
            }
            if (isAnotherFollowUp && history.exclusions().isExcluded(ex)) {
                continue;
            }
            ScoredCandidate scored = scoreCandidate(
                    ex, criteria, snapshot, suggestedDay, targetDayExercises,
                    targetDayTextPatterns, undertrained, referenceSets, userDifficulty, profile, true, isVn
            );
            if (scored != null) {
                ranked.add(scored);
            }
        }

        // If filtering out target day left nothing (rare edge case in tiny test catalogs), include existing with flag
        boolean includedAlreadyOnTargetDay = false;
        if (ranked.isEmpty() && !targetDayExerciseIds.isEmpty() && !isAnotherFollowUp) {
            includedAlreadyOnTargetDay = true;
            for (Exercise ex : publishedCatalog) {
                ScoredCandidate scored = scoreCandidate(
                        ex, criteria, snapshot, suggestedDay, targetDayExercises,
                        targetDayTextPatterns, undertrained, referenceSets, userDifficulty, profile, true, isVn
                );
                if (scored != null) {
                    ranked.add(scored);
                }
            }
        }

        ranked.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(sc -> sc.exercise().getName(), String.CASE_INSENSITIVE_ORDER));

        int limit = Math.min(criteria.requestedCount(), ranked.size());
        List<ExerciseRecommendationItemDto> items = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ScoredCandidate sc = ranked.get(i);
            items.add(toRecommendationItem(sc, targetDayExerciseIds.contains(sc.exercise().getId())));
        }

        List<String> planWarnings = buildRecommendationPlanWarnings(snapshot, suggestedDay, targetDayExercises, includedAlreadyOnTargetDay, isVn);

        ExerciseRecommendationsCardDto card = ExerciseRecommendationsCardDto.builder()
                .informationalOnly(false)
                .focusLabel(criteria.focusLabel())
                .criteriaKey(criteria.criteriaKey())
                .topicPrompt(effectiveTopicPrompt(prompt, history))
                .suggestedDayOfWeek(suggestedDow)
                .suggestedDayLabel(suggestedDay != null ? formatDayTitle(suggestedDay, isVn) : null)
                .targetDayExerciseCount(suggestedDay != null ? targetDayExercises.size() : null)
                .targetDayTotalSets(suggestedDay != null ? sumDaySets(targetDayExercises) : null)
                .undertrainedMuscles(undertrained)
                .availablePlanDays(buildPlanDayOptions(snapshot, isVn))
                .planWarnings(planWarnings)
                .recommendations(items)
                .build();

        Map<Long, Exercise> catalogById = publishedCatalog.stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e, (a, b) -> a));
        ExerciseRecommendationsCardDto validatedCard = validateAndSanitizeCard(card, catalogById);

        String markdown = formatRecommendationsMarkdown(validatedCard, snapshot, suggestedDay, isVn);
        return new RecommendationOutcome("EXERCISE_RECOMMENDATION", markdown, safeToJson(validatedCard), validatedCard);
    }

    @Transactional(readOnly = true)
    public ConsultationOutcome consultWorkoutPlan(
            Long userId,
            String prompt,
            WorkoutDayOfWeek selectedDayOfWeek,
            boolean isVn
    ) {
        PlanSnapshot snapshot = loadPlanSnapshot(userId);
        ClientProfile profile = userId != null ? clientProfileRepository.findByUserId(userId).orElse(null) : null;

        if (snapshot.plan() == null || snapshot.days().isEmpty()) {
            String noPlanMsg = isVn
                    ? "Bạn chưa có lịch tập nào đang hoạt động để phân tích. Hãy nói **\"Tạo lịch tập calisthenics\"** để tôi thiết kế lịch trình phù hợp cho bạn nhé!"
                    : "You don't currently have an active workout plan to evaluate. Ask me to **\"Create a calisthenics workout plan\"** or open the planner and I'll analyze it once set up.";
            WorkoutConsultationCardDto card = WorkoutConsultationCardDto.builder()
                    .focusArea("Workout Routine")
                    .balanceScore(null)
                    .findings(List.of(ConsultationFindingDto.builder()
                            .severity("INFO")
                            .title(isVn ? "Chưa có lịch tập" : "No Active Workout Plan")
                            .detail(noPlanMsg)
                            .build()))
                    .suggestedActionPrompt("Create a 4-day calisthenics workout plan")
                    .build();
            return new ConsultationOutcome("WORKOUT_CONSULTATION", noPlanMsg, safeToJson(card), card);
        }

        String lower = prompt != null ? prompt.toLowerCase(Locale.US) : "";
        WorkoutDayOfWeek explicitDay = UserAiWorkoutToolService.extractWeekday(prompt);
        WorkoutPlanDay focusDay = explicitDay != null
                ? findDay(snapshot.days(), explicitDay)
                : matchSemanticPlanDay(lower, snapshot);
        if (focusDay == null && selectedDayOfWeek != null && !lower.contains("weekly") && !lower.contains("routine") && !lower.contains("recovery")) {
            focusDay = findDay(snapshot.days(), selectedDayOfWeek);
        }

        List<ConsultationFindingDto> findings = new ArrayList<>();
        List<String> undertrained = new ArrayList<>();
        List<String> overloaded = new ArrayList<>();
        int score = 88;

        // 1. Analyze specific day balance (e.g., "Is my leg day balanced?")
        boolean asksLegBalance = lower.contains("leg") || lower.contains("lower") || lower.contains("hamstring") || lower.contains("quad") || lower.contains("glute") || lower.contains("calves");
        boolean asksRecovery = lower.contains("recovery") || lower.contains("rest day") || lower.contains("phục hồi") || lower.contains("nghỉ");
        boolean asksPlancheOrSkillVolume = lower.contains("planche") || lower.contains("front lever") || lower.contains("handstand") || lower.contains("volume") || lower.contains("quá tải");

        if (focusDay != null || asksLegBalance) {
            WorkoutPlanDay inspectedDay = focusDay != null ? focusDay : findLegDay(snapshot);
            if (inspectedDay != null) {
                Map<MuscleId, Double> daySets = snapshot.dayMuscleSets().getOrDefault(inspectedDay.getId(), Map.of());
                List<WorkoutPlanExercise> dayExs = snapshot.exercisesByDayId().getOrDefault(inspectedDay.getId(), List.of());
                double quadSets = daySets.getOrDefault(MuscleId.QUADRICEPS, 0.0);
                double hamSets = daySets.getOrDefault(MuscleId.HAMSTRINGS, 0.0);
                double gluteSets = daySets.getOrDefault(MuscleId.GLUTES, 0.0);
                double calfSets = daySets.getOrDefault(MuscleId.CALVES, 0.0);

                if (inspectedDay.getSessionType() == WorkoutSessionType.LEGS
                        || inspectedDay.getSessionType() == WorkoutSessionType.LOWER
                        || (inspectedDay.getName() != null && inspectedDay.getName().toLowerCase(Locale.US).contains("leg"))
                        || asksLegBalance) {
                    boolean directHamstrings = hasDirectPrimaryMuscle(dayExs, MuscleId.HAMSTRINGS);
                    boolean directCalves = hasDirectPrimaryMuscle(dayExs, MuscleId.CALVES);
                    if (quadSets > 0 && (!directHamstrings || hamSets <= quadSets * 0.6)) {
                        score -= 14;
                        undertrained.add("Hamstrings");
                        findings.add(ConsultationFindingDto.builder()
                                .severity("WARNING")
                                .title(isVn ? "Thiếu khối lượng cơ đùi sau (Hamstrings)" : "Low Hamstring Volume on " + formatDayTitle(inspectedDay, false))
                                .detail(String.format(Locale.US,
                                        "Quadriceps receive %.1f effective sets vs %.1f effective sets for Hamstrings%s. Adding a direct hip-hinge or curl movement (e.g., Romanian Deadlift or Nordic Curl) improves knee stability and balance.",
                                        quadSets, hamSets, directHamstrings ? "" : " (secondary stimulation only)"))
                                .build());
                    }
                    if (gluteSets < 2.0) {
                        score -= 8;
                        undertrained.add("Glutes");
                        findings.add(ConsultationFindingDto.builder()
                                .severity("WARNING")
                                .title("Glutes Could Use More Direct Stimulus")
                                .detail("Your leg session currently provides limited direct glute work. Single-leg or hip-extension movements would round this out.")
                                .build());
                    }
                    if (!directCalves && calfSets < 3.0) {
                        undertrained.add("Calves");
                        findings.add(ConsultationFindingDto.builder()
                                .severity("INFO")
                                .title("Direct Calf Work Is Minimal")
                                .detail("Adding Calf Raises at the end of the session only takes 5 minutes and supports ankle resilience for unilateral squats.")
                                .build());
                    }
                    if (dayExs.size() >= 6) {
                        score -= 10;
                        findings.add(ConsultationFindingDto.builder()
                                .severity("WARNING")
                                .title("High Exercise Count on " + formatDayTitle(inspectedDay, false))
                                .detail("You currently have " + dayExs.size() + " exercises scheduled on this day; monitor fatigue before adding more volume.")
                                .build());
                    }
                } else {
                    int totalDaySets = sumDaySets(dayExs);
                    findings.add(ConsultationFindingDto.builder()
                            .severity(totalDaySets > 24 ? "WARNING" : "INFO")
                            .title(formatDayTitle(inspectedDay, isVn) + " Volume Overview")
                            .detail(dayExs.size() + " exercises (" + totalDaySets + " total working sets).")
                            .build());
                    if (totalDaySets > 24) {
                        score -= 10;
                    }
                }
            }
        }

        // 2. Hamstring / specific muscle weekly check
        for (MuscleId requestedMuscle : MuscleId.resolveAliases(prompt)) {
            double weekly = snapshot.weeklyMuscleSets().getOrDefault(requestedMuscle, 0.0);
            if (weekly < 4.0) {
                score -= 10;
                if (!undertrained.contains(requestedMuscle.getDisplayName())) {
                    undertrained.add(requestedMuscle.getDisplayName());
                }
                findings.add(ConsultationFindingDto.builder()
                        .severity("WARNING")
                        .title("Low Weekly Volume for " + requestedMuscle.getDisplayName())
                        .detail(String.format(Locale.US,
                                "Current weekly effective volume for %s is %.1f sets (typically optimal is 6–12+ effective sets/week depending on recovery).",
                                requestedMuscle.getDisplayName(), weekly))
                        .build());
            } else if (weekly > HIGH_WEEKLY_MUSCLE_SETS_THRESHOLD) {
                score -= 10;
                overloaded.add(requestedMuscle.getDisplayName());
                findings.add(ConsultationFindingDto.builder()
                        .severity("WARNING")
                        .title("High Weekly Volume for " + requestedMuscle.getDisplayName())
                        .detail(String.format(Locale.US,
                                "Weekly effective volume for %s reaches %.1f sets. Ensure recovery days separate heavy sessions.",
                                requestedMuscle.getDisplayName(), weekly))
                        .build());
            } else {
                findings.add(ConsultationFindingDto.builder()
                        .severity("INFO")
                        .title(requestedMuscle.getDisplayName() + " Volume Looks Productive")
                        .detail(String.format(Locale.US,
                                "You accumulate %.1f effective weekly sets for %s across your current plan.",
                                weekly, requestedMuscle.getDisplayName()))
                        .build());
            }
        }

        // 3. Weekly recovery & consecutive stress check
        long restDaysCount = snapshot.days().stream().filter(WorkoutPlanDay::isRestDay).count();
        if (restDaysCount == 0) {
            score -= 18;
            findings.add(ConsultationFindingDto.builder()
                    .severity("WARNING")
                    .title(isVn ? "Không có ngày nghỉ phục hồi" : "No Scheduled Rest Days")
                    .detail("Your current weekly plan schedules training on all 7 days with 0 rest days. At least 1–2 recovery days per week helps tendon remodeling and nervous system recovery.")
                    .build());
        } else if (restDaysCount == 1 && asksRecovery) {
            score -= 8;
            findings.add(ConsultationFindingDto.builder()
                    .severity("INFO")
                    .title("Minimal Recovery Margin (1 Rest Day/Week)")
                    .detail("With 6 training days per week, recovery depends heavily on avoiding back-to-back overlapping muscle groups and managing sleep.")
                    .build());
        } else if (asksRecovery || restDaysCount >= 2) {
            findings.add(ConsultationFindingDto.builder()
                    .severity("INFO")
                    .title("Rest Day Distribution")
                    .detail("Your plan includes " + restDaysCount + " scheduled rest day(s) per week.")
                    .build());
        }

        // Consecutive day anterior deltoid / push / pull overlap check
        List<String> consecutiveOverlaps = detectConsecutiveMuscleOverlaps(snapshot);
        for (String overlap : consecutiveOverlaps) {
            score -= 8;
            findings.add(ConsultationFindingDto.builder()
                    .severity("WARNING")
                    .title("Consecutive-Day Muscle Overlap")
                    .detail(overlap)
                    .build());
        }

        // 4. Skill / Planche volume & tendon stress check
        if (asksPlancheOrSkillVolume) {
            int skillHoldSets = countSkillWeeklySets(snapshot, List.of("planche", "front lever", "back lever", "handstand", "flag"));
            double frontDeltWeekly = snapshot.weeklyMuscleSets().getOrDefault(MuscleId.FRONT_DELTOIDS, 0.0);
            int plancheDaysCount = countDaysMatchingKeyword(snapshot, "planche");
            if (lower.contains("planche") || skillHoldSets > 0) {
                boolean highPlanche = skillHoldSets >= 14 || frontDeltWeekly >= 16.0 || plancheDaysCount >= 4;
                if (highPlanche) {
                    score -= 12;
                    if (!overloaded.contains("Front Deltoids")) {
                        overloaded.add("Front Deltoids");
                    }
                    findings.add(ConsultationFindingDto.builder()
                            .severity("WARNING")
                            .title("High Straight-Arm / Planche Stress Detected")
                            .detail(String.format(Locale.US,
                                    "Your schedule contains %d weekly skill sets across %d day(s) alongside %.1f effective Front Deltoid sets/week. Straight-arm scapular & bicep-tendon work generally thrives on 2–3 non-consecutive days and ~8–12 quality sets/week.",
                                    skillHoldSets, plancheDaysCount, frontDeltWeekly))
                            .build());
                } else {
                    findings.add(ConsultationFindingDto.builder()
                            .severity("INFO")
                            .title("Skill Training Volume Assessment")
                            .detail(String.format(Locale.US,
                                    "You have %d weekly skill-specific working sets and %.1f effective Front Deltoid sets/week across %d day(s), which is manageable if soreness stays controlled.",
                                    skillHoldSets, frontDeltWeekly, plancheDaysCount))
                            .build());
                }
            }
        }

        // 5. General push/pull & upper/lower balance check if still few findings
        double pushSets = snapshot.weeklyMuscleSets().getOrDefault(MuscleId.CHEST, 0.0)
                + snapshot.weeklyMuscleSets().getOrDefault(MuscleId.FRONT_DELTOIDS, 0.0);
        double pullSets = snapshot.weeklyMuscleSets().getOrDefault(MuscleId.LATISSIMUS_DORSI, 0.0)
                + snapshot.weeklyMuscleSets().getOrDefault(MuscleId.RHOMBOIDS, 0.0);
        double totalLegSets = snapshot.weeklyMuscleSets().getOrDefault(MuscleId.QUADRICEPS, 0.0)
                + snapshot.weeklyMuscleSets().getOrDefault(MuscleId.HAMSTRINGS, 0.0)
                + snapshot.weeklyMuscleSets().getOrDefault(MuscleId.GLUTES, 0.0);

        if (pushSets > 0 && pullSets == 0) {
            score -= 12;
            undertrained.add("Latissimus Dorsi");
            findings.add(ConsultationFindingDto.builder()
                    .severity("WARNING")
                    .title("Missing Pulling Balance")
                    .detail("Your routine schedules pushing volume without pulling volume, which can strain anterior shoulders over time.")
                    .build());
        } else if (pullSets > 0 && pushSets == 0) {
            score -= 12;
            undertrained.add("Chest");
            findings.add(ConsultationFindingDto.builder()
                    .severity("WARNING")
                    .title("Missing Pushing Balance")
                    .detail("Your routine focuses on pulling movements without pushing balance.")
                    .build());
        }
        if (totalLegSets < 3.0) {
            undertrained.add("Quadriceps");
            undertrained.add("Hamstrings");
            findings.add(ConsultationFindingDto.builder()
                    .severity("WARNING")
                    .title("Lower-Body Volume Is Very Low")
                    .detail("Weekly lower-body volume is under 3 effective sets. Adding compound leg days or unilateral progressions will round out full-body strength.")
                    .build());
        }

        for (Map.Entry<MuscleId, Double> entry : snapshot.weeklyMuscleSets().entrySet()) {
            if (entry.getValue() > HIGH_WEEKLY_MUSCLE_SETS_THRESHOLD && !overloaded.contains(entry.getKey().getDisplayName())) {
                overloaded.add(entry.getKey().getDisplayName());
            }
        }

        int clampedScore = Math.max(35, Math.min(100, score));
        List<String> dedupUndertrained = undertrained.stream().distinct().limit(5).toList();
        List<String> dedupOverloaded = overloaded.stream().distinct().limit(5).toList();

        String suggestedFollowUp = !dedupUndertrained.isEmpty()
                ? "Recommend exercises for " + String.join(" and ", dedupUndertrained.subList(0, Math.min(2, dedupUndertrained.size())))
                : "What exercises am I missing in my current routine?";

        WorkoutConsultationCardDto card = WorkoutConsultationCardDto.builder()
                .focusArea(focusDay != null ? formatDayTitle(focusDay, isVn) : snapshot.plan().getName())
                .focusDayOfWeek(focusDay != null ? focusDay.getDayOfWeek() : null)
                .balanceScore(clampedScore)
                .findings(findings)
                .undertrainedMuscles(dedupUndertrained)
                .overloadedMuscles(dedupOverloaded)
                .suggestedActionPrompt(suggestedFollowUp)
                .build();

        String markdown = formatConsultationMarkdown(card, snapshot, focusDay, profile, isVn);
        return new ConsultationOutcome("WORKOUT_CONSULTATION", markdown, safeToJson(card), card);
    }

    /**
     * Validates and normalizes recommendation cards to guarantee no null collections,
     * no invented exercise IDs, and safe fallback values for optional fields.
     */
    public ExerciseRecommendationsCardDto validateAndSanitizeCard(
            ExerciseRecommendationsCardDto raw,
            Map<Long, Exercise> validCatalogById
    ) {
        if (raw == null) {
            return ExerciseRecommendationsCardDto.builder()
                    .recommendations(List.of())
                    .undertrainedMuscles(List.of())
                    .availablePlanDays(List.of())
                    .planWarnings(List.of())
                    .build();
        }
        List<ExerciseRecommendationItemDto> safeItems = new ArrayList<>();
        if (raw.getRecommendations() != null) {
            Set<Long> seenIds = new HashSet<>();
            for (ExerciseRecommendationItemDto item : raw.getRecommendations()) {
                if (item == null || item.getExerciseId() == null || item.getExerciseId() <= 0) {
                    continue;
                }
                Exercise dbEx = validCatalogById != null ? validCatalogById.get(item.getExerciseId()) : null;
                if (validCatalogById != null && dbEx == null) {
                    continue; // Strictly reject invented or non-published exercise IDs
                }
                if (!seenIds.add(item.getExerciseId())) {
                    continue;
                }
                String safeName = (item.getName() != null && !item.getName().isBlank())
                        ? item.getName().trim()
                        : (dbEx != null ? dbEx.getName() : null);
                if (safeName == null || safeName.isBlank()) {
                    continue;
                }

                List<String> primaryIds = item.getPrimaryMuscleIds() != null ? item.getPrimaryMuscleIds() : List.of();
                List<String> secondaryIds = item.getSecondaryMuscleIds() != null ? item.getSecondaryMuscleIds() : List.of();
                if ((primaryIds.isEmpty() && secondaryIds.isEmpty()) && dbEx != null) {
                    MuscleTaxonomyService.ResolvedMuscleTargets resolved = muscleTaxonomyService.resolveTargets(dbEx);
                    primaryIds = resolved.primaryMuscleIds();
                    secondaryIds = resolved.secondaryMuscleIds();
                }

                int sets = item.getRecommendedSets() != null && item.getRecommendedSets() > 0 ? item.getRecommendedSets() : 3;
                Integer reps = item.getRecommendedReps();
                Integer dur = item.getRecommendedDurationSeconds();
                if ((reps == null || reps <= 0) && (dur == null || dur <= 0)) {
                    reps = 10;
                    dur = null;
                }
                int rest = item.getRecommendedRestSeconds() != null && item.getRecommendedRestSeconds() >= 0
                        ? item.getRecommendedRestSeconds() : 90;
                String summary = (item.getPrescriptionSummary() != null && !item.getPrescriptionSummary().isBlank())
                        ? item.getPrescriptionSummary()
                        : formatPrescriptionSummary(sets, reps, dur, rest);

                safeItems.add(ExerciseRecommendationItemDto.builder()
                        .exerciseId(item.getExerciseId())
                        .name(safeName)
                        .code(item.getCode() != null ? item.getCode() : (dbEx != null ? dbEx.getCode() : null))
                        .category(item.getCategory() != null ? item.getCategory()
                                : (dbEx != null && dbEx.getCategory() != null ? dbEx.getCategory().name() : null))
                        .difficulty(item.getDifficulty() != null ? item.getDifficulty()
                                : (dbEx != null && dbEx.getDifficulty() != null ? dbEx.getDifficulty().name() : null))
                        .primaryMuscleIds(List.copyOf(primaryIds))
                        .secondaryMuscleIds(List.copyOf(secondaryIds))
                        .primaryMuscleNames(item.getPrimaryMuscleNames() != null ? List.copyOf(item.getPrimaryMuscleNames()) : List.of())
                        .secondaryMuscleNames(item.getSecondaryMuscleNames() != null ? List.copyOf(item.getSecondaryMuscleNames()) : List.of())
                        .recommendedSets(sets)
                        .recommendedReps(reps)
                        .recommendedDurationSeconds(dur)
                        .recommendedRestSeconds(rest)
                        .prescriptionSummary(summary)
                        .equipment(item.getEquipment())
                        .imageUrl(item.getImageUrl())
                        .rationale(item.getRationale())
                        .fillsUndertrainedMuscles(item.getFillsUndertrainedMuscles() != null ? List.copyOf(item.getFillsUndertrainedMuscles()) : List.of())
                        .alreadyInTargetDay(item.isAlreadyInTargetDay())
                        .alreadyInPlanDays(item.getAlreadyInPlanDays() != null ? List.copyOf(item.getAlreadyInPlanDays()) : List.of())
                        .volumeWarning(item.getVolumeWarning())
                        .build());
            }
        }

        return ExerciseRecommendationsCardDto.builder()
                .type("EXERCISE_RECOMMENDATIONS")
                .readOnly(true)
                .mutated(false)
                .informationalOnly(raw.isInformationalOnly())
                .focusLabel(raw.getFocusLabel())
                .criteriaKey(raw.getCriteriaKey())
                .topicPrompt(raw.getTopicPrompt())
                .suggestedDayOfWeek(raw.getSuggestedDayOfWeek())
                .suggestedDayLabel(raw.getSuggestedDayLabel())
                .targetDayExerciseCount(raw.getTargetDayExerciseCount())
                .targetDayTotalSets(raw.getTargetDayTotalSets())
                .undertrainedMuscles(raw.getUndertrainedMuscles() != null ? List.copyOf(raw.getUndertrainedMuscles()) : List.of())
                .availablePlanDays(raw.getAvailablePlanDays() != null ? List.copyOf(raw.getAvailablePlanDays()) : List.of())
                .planWarnings(raw.getPlanWarnings() != null ? List.copyOf(raw.getPlanWarnings()) : List.of())
                .recommendations(safeItems)
                .build();
    }

    public boolean planContextExplicitlyRequested(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.US);
        if (UserAiWorkoutToolService.extractWeekday(prompt) != null) {
            return true;
        }
        return lower.contains("add to")
                || lower.contains("to add")
                || lower.contains("should i add")
                || lower.contains("leg day")
                || lower.contains("legs day")
                || lower.contains("push day")
                || lower.contains("pull day")
                || lower.contains("upper day")
                || lower.contains("lower day")
                || lower.contains("planche day")
                || lower.contains("skill day")
                || lower.contains("in my routine")
                || lower.contains("current routine")
                || lower.contains("my current plan")
                || lower.contains("my workout plan")
                || lower.contains("missing in")
                || lower.contains("what am i missing")
                || lower.contains("what's missing")
                || lower.contains("vào lịch")
                || lower.contains("nên thêm")
                || lower.contains("thiếu trong lịch");
    }

    public String extractCriteriaKey(String prompt) {
        return parsePromptCriteria(prompt, null).criteriaKey();
    }

    public boolean hasExplicitExerciseCriteria(String prompt) {
        return parsePromptCriteria(prompt, null).hasExplicitFilter();
    }

    // ==================== PRIVATE SCORING & ANALYSIS HELPERS ====================

    private PromptCriteria parsePromptCriteria(String prompt, ClientProfile profile) {
        String lower = prompt != null ? prompt.toLowerCase(Locale.US).trim() : "";
        Set<MuscleId> muscles = new LinkedHashSet<>(MuscleId.resolveAliases(prompt));
        Set<ExerciseCategory> categories = new LinkedHashSet<>();
        List<String> skillKeywords = new ArrayList<>();

        Matcher skillMatcher = SKILL_KEYWORDS.matcher(lower);
        while (skillMatcher.find()) {
            String rawSkill = skillMatcher.group(1).toLowerCase(Locale.US).replace('-', ' ').trim();
            String canonicalSkill = (rawSkill.contains("handstand push") || rawSkill.equals("hspu"))
                    ? "handstand"
                    : rawSkill;
            if (!skillKeywords.contains(canonicalSkill)) {
                skillKeywords.add(canonicalSkill);
            }
        }

        boolean wholeGap = lower.contains("missing") || lower.contains("current routine")
                || lower.contains("my routine") || lower.contains("imbalance") || lower.contains("gap");
        boolean lowerBody = lower.contains("leg") || lower.contains("lower body") || lower.contains("squat")
                || muscles.contains(MuscleId.QUADRICEPS) || muscles.contains(MuscleId.HAMSTRINGS)
                || muscles.contains(MuscleId.GLUTES) || muscles.contains(MuscleId.CALVES) || muscles.contains(MuscleId.ADDUCTORS);
        boolean push = lower.contains("push") || lower.contains("chest") || lower.contains("tricep")
                || lower.contains("planche") || lower.contains("handstand") || lower.contains("hspu")
                || muscles.contains(MuscleId.CHEST) || muscles.contains(MuscleId.FRONT_DELTOIDS) || muscles.contains(MuscleId.TRICEPS);
        boolean pull = lower.contains("pull") || lower.contains("back") || lower.contains("lat") || lower.contains("bicep")
                || lower.contains("front lever") || lower.contains("muscle up")
                || muscles.contains(MuscleId.LATISSIMUS_DORSI) || muscles.contains(MuscleId.RHOMBOIDS) || muscles.contains(MuscleId.BICEPS);
        boolean core = lower.contains("core") || lower.contains("abs") || lower.contains("l sit") || lower.contains("l-sit")
                || lower.contains("dragon flag")
                || muscles.contains(MuscleId.RECTUS_ABDOMINIS) || muscles.contains(MuscleId.OBLIQUES);

        if (lower.contains("leg") || lower.contains("lower body")) {
            categories.add(ExerciseCategory.LEGS);
            categories.add(ExerciseCategory.GLUTES);
            muscles.add(MuscleId.QUADRICEPS);
            muscles.add(MuscleId.GLUTES);
            muscles.add(MuscleId.HAMSTRINGS);
            muscles.add(MuscleId.CALVES);
        }
        if (lower.contains("arm")) {
            categories.add(ExerciseCategory.BICEPS);
            categories.add(ExerciseCategory.TRICEPS);
            muscles.add(MuscleId.BICEPS);
            muscles.add(MuscleId.TRICEPS);
            muscles.add(MuscleId.FOREARMS);
        }
        if (lower.contains("push")) categories.add(ExerciseCategory.PUSH);
        if (lower.contains("pull")) categories.add(ExerciseCategory.PULL);
        if (lower.contains("core") || lower.contains("abs")) categories.add(ExerciseCategory.CORE);
        if (lower.contains("chest") || lower.contains("pec")) categories.add(ExerciseCategory.CHEST);
        if (lower.contains("back") || lower.contains("lat")) categories.add(ExerciseCategory.BACK);
        if (lower.contains("shoulder") || lower.contains("delt")) categories.add(ExerciseCategory.SHOULDERS);
        if (lower.contains("planche")) {
            categories.add(ExerciseCategory.SKILL);
            categories.add(ExerciseCategory.PUSH);
            categories.add(ExerciseCategory.ISOMETRIC);
            muscles.add(MuscleId.FRONT_DELTOIDS);
            muscles.add(MuscleId.CHEST);
            muscles.add(MuscleId.TRICEPS);
            muscles.add(MuscleId.RECTUS_ABDOMINIS);
        }
        if (lower.contains("front lever")) {
            categories.add(ExerciseCategory.SKILL);
            categories.add(ExerciseCategory.PULL);
            categories.add(ExerciseCategory.ISOMETRIC);
            muscles.add(MuscleId.LATISSIMUS_DORSI);
            muscles.add(MuscleId.TERES_MAJOR);
            muscles.add(MuscleId.RHOMBOIDS);
            muscles.add(MuscleId.REAR_DELTOIDS);
        }
        if (lower.contains("handstand") || lower.contains("hspu")) {
            categories.add(ExerciseCategory.SKILL);
            categories.add(ExerciseCategory.SHOULDERS);
            categories.add(ExerciseCategory.PUSH);
            muscles.add(MuscleId.FRONT_DELTOIDS);
            muscles.add(MuscleId.UPPER_TRAPEZIUS);
            muscles.add(MuscleId.TRICEPS);
        }

        boolean bodyweightOnly = lower.contains("no equipment") || lower.contains("without equipment")
                || lower.contains("bodyweight only") || lower.contains("at home") || lower.contains("just floor");
        boolean explicitlyGym = lower.contains("gym") || lower.contains("weight training")
                || lower.contains("barbell") || lower.contains("dumbbell") || lower.contains("cable")
                || lower.contains("machine") || lower.contains("smith")
                || lower.contains("bench press") || lower.contains("lat pulldown")
                || lower.contains("leg press") || lower.contains("leg extension")
                || lower.contains("leg curl") || lower.contains("romanian deadlift")
                || lower.contains("face pull") || lower.contains("calf raise");
        boolean explicitlyCalisthenics = bodyweightOnly || lower.contains("calisthenics") || lower.contains("bodyweight")
                || lower.contains("planche") || lower.contains("front lever") || lower.contains("back lever")
                || lower.contains("handstand") || lower.contains("hspu") || lower.contains("human flag")
                || lower.contains("muscle up") || lower.contains("muscle-up")
                || lower.contains("pistol squat") || lower.contains("shrimp squat")
                || lower.contains("l-sit") || lower.contains("l sit")
                || lower.contains("v-sit") || lower.contains("v sit")
                || lower.contains("dragon flag");
        ExerciseTrainingType requestedTrainingType = null;
        if (explicitlyGym && !explicitlyCalisthenics) {
            requestedTrainingType = ExerciseTrainingType.GYM;
        } else if (explicitlyCalisthenics && !explicitlyGym) {
            requestedTrainingType = ExerciseTrainingType.CALISTHENICS;
        }

        String eqFilter = null;
        for (String eq : List.of("parallettes", "rings", "gymnastic rings", "pull-up bar", "pull up bar", "resistance band", "bench", "box", "kettlebell", "dumbbell", "barbell", "cable", "machine")) {
            if (lower.contains(eq)) {
                eqFilter = eq;
                break;
            }
        }

        boolean hasExplicitFilter = !muscles.isEmpty() || !categories.isEmpty() || !skillKeywords.isEmpty()
                || wholeGap || bodyweightOnly || eqFilter != null || requestedTrainingType != null;

        // If nothing explicit matched and user mentioned general profile goals, draw subtle signal (only in plan-context mode)
        if (muscles.isEmpty() && categories.isEmpty() && !wholeGap && profile != null && profile.getPrimaryGoals() != null) {
            wholeGap = true;
        }

        int requestedCount = 5;
        boolean singularAnother = isSingularAnotherPhrase(lower);
        Matcher countMatcher = COUNT_PATTERN.matcher(lower);
        if (countMatcher.find()) {
            try {
                requestedCount = Math.max(1, Math.min(20, Integer.parseInt(countMatcher.group(1))));
            } catch (NumberFormatException ignored) {}
        } else if (singularAnother) {
            requestedCount = 1;
        }

        boolean isHspuQuery = lower.contains("handstand push") || lower.contains("hspu");
        String focusLabel;
        String listHeading;
        if (isHspuQuery) {
            focusLabel = "Handstand Push-Up Exercises";
            listHeading = "Handstand Push-Up Exercises";
        } else if (!skillKeywords.isEmpty()) {
            String capSkill = capitalizeWords(String.join(", ", skillKeywords));
            focusLabel = capSkill + " Progression";
            listHeading = capSkill + " Exercises";
        } else if (lowerBody && lower.contains("hamstring")) {
            focusLabel = "Hamstrings & Posterior Chain";
            listHeading = "Hamstring Exercises";
        } else if (lowerBody) {
            focusLabel = "Lower Body & Legs";
            listHeading = "Leg Exercises";
        } else if (wholeGap) {
            focusLabel = "Routine Balance & Missing Muscles";
            listHeading = "Recommended Exercises";
        } else if (!muscles.isEmpty()) {
            focusLabel = muscles.stream().limit(3).map(MuscleId::getDisplayName).collect(Collectors.joining(", "));
            listHeading = focusLabel + " Exercises";
        } else if (!categories.isEmpty()) {
            focusLabel = categories.stream().map(c -> capitalizeWords(c.name().replace('_', ' '))).collect(Collectors.joining(", "));
            listHeading = focusLabel + " Exercises";
        } else if (requestedTrainingType == ExerciseTrainingType.GYM) {
            focusLabel = "Gym / Weight Training Selection";
            listHeading = "Gym Exercises";
        } else {
            focusLabel = "Personalized Calisthenics Selection";
            listHeading = "Calisthenics Exercises";
        }

        String criteriaKey;
        if (isHspuQuery) {
            criteriaKey = "SKILL:HANDSTAND_PUSH_UP";
        } else if (!skillKeywords.isEmpty()) {
            criteriaKey = "SKILL:" + skillKeywords.stream().sorted().collect(Collectors.joining(","));
        } else if (wholeGap) {
            criteriaKey = "GAP_ANALYSIS";
        } else if (lowerBody && (lower.contains("leg") || lower.contains("lower body"))) {
            criteriaKey = "LEGS";
        } else if (!muscles.isEmpty()) {
            criteriaKey = "MUSCLES:" + muscles.stream().map(MuscleId::name).sorted().collect(Collectors.joining(","));
        } else if (!categories.isEmpty()) {
            criteriaKey = "CATS:" + categories.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        } else if (requestedTrainingType != null) {
            criteriaKey = "TYPE:" + requestedTrainingType.name();
        } else {
            criteriaKey = "GENERAL";
        }

        return new PromptCriteria(
                Set.copyOf(muscles),
                Set.copyOf(categories),
                List.copyOf(skillKeywords),
                wholeGap,
                lowerBody,
                push,
                pull,
                core,
                bodyweightOnly,
                requestedTrainingType,
                eqFilter,
                focusLabel,
                listHeading,
                criteriaKey,
                requestedCount,
                hasExplicitFilter
        );
    }

    private WorkoutPlanDay resolveSuggestedTargetDay(
            String prompt,
            WorkoutDayOfWeek selectedDayOfWeek,
            PromptCriteria criteria,
            PlanSnapshot snapshot
    ) {
        if (snapshot.days().isEmpty()) {
            return null;
        }
        WorkoutDayOfWeek explicit = UserAiWorkoutToolService.extractWeekday(prompt);
        if (explicit != null) {
            return findDay(snapshot.days(), explicit);
        }
        WorkoutPlanDay semanticMatch = matchSemanticPlanDay(prompt != null ? prompt.toLowerCase(Locale.US) : "", snapshot);
        if (semanticMatch != null) {
            return semanticMatch;
        }
        if (selectedDayOfWeek != null && !criteria.wholeRoutineGapAnalysis()) {
            return findDay(snapshot.days(), selectedDayOfWeek);
        }
        return null;
    }

    private WorkoutPlanDay matchSemanticPlanDay(String lower, PlanSnapshot snapshot) {
        if (lower == null || lower.isBlank() || snapshot.days().isEmpty()) {
            return null;
        }
        if (lower.contains("leg day") || lower.contains("legs day") || lower.contains("lower day") || lower.contains("lower body day")) {
            return findLegDay(snapshot);
        }
        if (lower.contains("push day")) {
            return findDayBySessionTypes(snapshot, List.of(WorkoutSessionType.PUSH), List.of("push"));
        }
        if (lower.contains("pull day")) {
            return findDayBySessionTypes(snapshot, List.of(WorkoutSessionType.PULL), List.of("pull"));
        }
        if (lower.contains("upper day") || lower.contains("upper body day")) {
            return findDayBySessionTypes(snapshot, List.of(WorkoutSessionType.UPPER), List.of("upper"));
        }
        if (lower.contains("planche day")) {
            return findDayBySessionTypes(snapshot, List.of(WorkoutSessionType.PLANCHE, WorkoutSessionType.SKILL_TRAINING), List.of("planche"));
        }
        if (lower.contains("skill day")) {
            return findDayBySessionTypes(snapshot, List.of(WorkoutSessionType.SKILL_TRAINING, WorkoutSessionType.PLANCHE, WorkoutSessionType.FRONT_LEVER), List.of("skill"));
        }
        return null;
    }

    private WorkoutPlanDay findLegDay(PlanSnapshot snapshot) {
        return findDayBySessionTypes(
                snapshot,
                List.of(WorkoutSessionType.LEGS, WorkoutSessionType.LOWER),
                List.of("leg", "lower", "squat", "glute")
        );
    }

    private WorkoutPlanDay findDayBySessionTypes(
            PlanSnapshot snapshot,
            List<WorkoutSessionType> types,
            List<String> titleKeywords
    ) {
        for (WorkoutPlanDay day : snapshot.days()) {
            if (!day.isRestDay() && day.getSessionType() != null && types.contains(day.getSessionType())) {
                return day;
            }
        }
        for (WorkoutPlanDay day : snapshot.days()) {
            if (!day.isRestDay() && day.getName() != null) {
                String n = day.getName().toLowerCase(Locale.US);
                for (String kw : titleKeywords) {
                    if (n.contains(kw)) {
                        return day;
                    }
                }
            }
        }
        return null;
    }

    private List<MuscleGapInsightDto> computeUndertrainedInsights(
            PromptCriteria criteria,
            PlanSnapshot snapshot,
            WorkoutPlanDay suggestedDay,
            boolean isVn
    ) {
        Set<MuscleId> candidateScope = new LinkedHashSet<>();
        if (!criteria.targetMuscles().isEmpty()) {
            candidateScope.addAll(criteria.targetMuscles());
        }
        if (criteria.wholeRoutineGapAnalysis() || candidateScope.isEmpty()) {
            candidateScope.addAll(List.of(
                    MuscleId.CHEST, MuscleId.FRONT_DELTOIDS, MuscleId.REAR_DELTOIDS,
                    MuscleId.LATISSIMUS_DORSI, MuscleId.RHOMBOIDS, MuscleId.BICEPS, MuscleId.TRICEPS,
                    MuscleId.QUADRICEPS, MuscleId.HAMSTRINGS, MuscleId.GLUTES, MuscleId.CALVES,
                    MuscleId.RECTUS_ABDOMINIS, MuscleId.ERECTOR_SPINAE
            ));
        }

        Map<MuscleId, Double> daySets = suggestedDay != null
                ? snapshot.dayMuscleSets().getOrDefault(suggestedDay.getId(), Map.of())
                : Map.of();

        List<MuscleGapInsightDto> insights = new ArrayList<>();
        for (MuscleId m : candidateScope) {
            double weekly = snapshot.weeklyMuscleSets().getOrDefault(m, 0.0);
            double onDay = daySets.getOrDefault(m, 0.0);
            boolean isGap = suggestedDay != null
                    ? (onDay < 3.0 || weekly < 5.0)
                    : weekly < 5.0;
            if (!isGap) {
                continue;
            }
            String reason;
            if (suggestedDay != null && onDay == 0.0 && weekly == 0.0) {
                reason = isVn ? "Chưa có bài tập nào tác động trong tuần" : "0 direct or secondary sets in your weekly plan";
            } else if (suggestedDay != null && onDay < 2.0) {
                reason = String.format(Locale.US, "%.1f effective sets on %s", onDay, formatDayLabel(suggestedDay.getDayOfWeek(), false));
            } else {
                reason = String.format(Locale.US, "Only %.1f effective weekly sets", weekly);
            }
            insights.add(MuscleGapInsightDto.builder()
                    .muscleId(m.getId())
                    .displayName(m.getDisplayName())
                    .weeklySets(round1(weekly))
                    .daySets(suggestedDay != null ? round1(onDay) : null)
                    .reason(reason)
                    .build());
        }

        insights.sort(Comparator.comparingDouble(g -> (g.getDaySets() != null ? g.getDaySets() * 2.0 : 0.0) + (g.getWeeklySets() != null ? g.getWeeklySets() : 0.0)));
        return insights.stream().limit(4).toList();
    }

    private ScoredCandidate scoreCandidate(
            Exercise ex,
            PromptCriteria criteria,
            PlanSnapshot snapshot,
            WorkoutPlanDay suggestedDay,
            List<WorkoutPlanExercise> targetDayExercises,
            Set<String> targetDayPatterns,
            List<MuscleGapInsightDto> undertrained,
            Map<MuscleId, Double> referenceSets,
            DifficultyLevel userDifficulty,
            ClientProfile profile,
            boolean includePlanContext,
            boolean isVn
    ) {
        MuscleTaxonomyService.ResolvedMuscleTargets resolved = muscleTaxonomyService.resolveTargets(ex);
        List<MuscleId> primary = toMuscleEnums(resolved.primaryMuscleIds());
        List<MuscleId> secondary = toMuscleEnums(resolved.secondaryMuscleIds());
        Set<MuscleId> undertrainedIds = undertrained.stream()
                .map(u -> MuscleId.fromId(u.getMuscleId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String exText = ((ex.getName() != null ? ex.getName() : "") + " "
                + (ex.getDescription() != null ? ex.getDescription() : "") + " "
                + (ex.getAliases() != null ? ex.getAliases() : "") + " "
                + (ex.getProgressionNotes() != null ? ex.getProgressionNotes() : "")).toLowerCase(Locale.US);

        double score = 0.0;
        boolean relevantToRequest = false;
        List<String> filledGaps = new ArrayList<>();

        // 0. Strict training type separation (prevent Gym movements from leaking into Calisthenics skills and vice versa)
        if (criteria.requestedTrainingType() != null
                && !SkillCatalogService.isExerciseCompatibleWithTrainingType(ex, criteria.requestedTrainingType())) {
            return null;
        }

        // 1. Direct target muscle / category / skill alignment
        if (!criteria.targetMuscles().isEmpty()) {
            for (MuscleId m : primary) {
                if (criteria.targetMuscles().contains(m)) {
                    score += 36.0;
                    relevantToRequest = true;
                }
            }
            for (MuscleId m : secondary) {
                if (criteria.targetMuscles().contains(m)) {
                    score += 14.0;
                    relevantToRequest = true;
                }
            }
        }
        if (ex.getCategory() != null && criteria.targetCategories().contains(ex.getCategory())) {
            score += 24.0;
            relevantToRequest = true;
        }
        for (String skill : criteria.skillKeywords()) {
            if (exText.contains(skill)) {
                score += 48.0;
                relevantToRequest = true;
            }
        }
        if (criteria.skillKeywords().contains("handstand") && (exText.contains("pike push") || exText.contains("handstand push"))) {
            score += 20.0;
            relevantToRequest = true;
        }
        if (criteria.lowerBodyFocus && isLowerBodyExercise(ex, primary, secondary)) {
            score += 20.0;
            relevantToRequest = true;
        }
        if (criteria.wholeRoutineGapAnalysis() || !criteria.hasExplicitFilter()) {
            relevantToRequest = true;
        }

        // Strict relevance gate when user asked for specific focus (e.g. leg exercises shouldn't return bicep curls)
        if (!relevantToRequest && (!criteria.targetMuscles().isEmpty() || !criteria.targetCategories().isEmpty() || !criteria.skillKeywords().isEmpty())) {
            return null;
        }

        // Equipment compatibility (applies in both informational & plan-context modes)
        String equipment = ex.getEquipment() != null ? ex.getEquipment().trim() : "";
        String eqLower = equipment.toLowerCase(Locale.US);
        boolean isMinimalEq = eqLower.isBlank() || eqLower.contains("none") || eqLower.contains("floor")
                || eqLower.contains("bodyweight") || eqLower.contains("wall");
        if (criteria.bodyweightOnly) {
            if (isMinimalEq) {
                score += 20.0;
            } else {
                score -= 45.0;
            }
        } else if (criteria.equipmentFilter() != null) {
            if (eqLower.contains(criteria.equipmentFilter())) {
                score += 25.0;
            }
        } else if (isMinimalEq) {
            score += 6.0;
        }

        if (!includePlanContext) {
            String briefRationale = buildInformationalRationale(ex, primary, isVn);
            return new ScoredCandidate(
                    ex, resolved, primary, secondary, List.of(), List.of(), score, null, briefRationale
            );
        }

        // 2. Boost exercises that directly fill undertrained muscle gaps
        for (MuscleId m : primary) {
            if (undertrainedIds.contains(m)) {
                double deficitBonus = 34.0 - Math.min(20.0, referenceSets.getOrDefault(m, 0.0) * 4.0);
                score += Math.max(12.0, deficitBonus);
                if (!filledGaps.contains(m.getDisplayName())) {
                    filledGaps.add(m.getDisplayName());
                }
            }
        }
        for (MuscleId m : secondary) {
            if (undertrainedIds.contains(m)) {
                score += 9.0;
                if (!filledGaps.contains(m.getDisplayName())) {
                    filledGaps.add(m.getDisplayName());
                }
            }
        }

        // 3. Movement pattern complementation (avoid recommending another squat when day already has squats & hamstrings are low)
        String exNameLower = ex.getName() != null ? ex.getName().toLowerCase(Locale.US) : "";
        boolean isHipHingeOrCurl = exNameLower.contains("deadlift") || exNameLower.contains("nordic")
                || exNameLower.contains("leg curl") || exNameLower.contains("bridge") || exNameLower.contains("hip thrust");
        boolean isSquatPattern = exNameLower.contains("squat") || exNameLower.contains("lunge");
        if (targetDayPatterns.contains("SQUAT") && isHipHingeOrCurl) {
            score += 26.0;
        } else if (targetDayPatterns.contains("SQUAT") && isSquatPattern && !targetDayPatterns.contains("HINGE")) {
            score -= 6.0;
        }

        // 5. Experience level / difficulty match
        if (userDifficulty != null && ex.getDifficulty() != null) {
            int diffGap = Math.abs(userDifficulty.ordinal() - ex.getDifficulty().ordinal());
            if (diffGap == 0) score += 14.0;
            else if (diffGap == 1) score += 6.0;
            else score -= 18.0;
        }

        // 6. User goal match from profile
        if (profile != null && profile.getPrimaryGoals() != null && !profile.getPrimaryGoals().isBlank()) {
            String goalsLower = profile.getPrimaryGoals().toLowerCase(Locale.US);
            for (String kw : List.of("planche", "front lever", "handstand", "muscle up", "legs", "strength", "hypertrophy", "mobility")) {
                if (goalsLower.contains(kw) && exText.contains(kw)) {
                    score += 10.0;
                }
            }
        }

        // 7. Deduplication across rest of plan
        List<String> alreadyInPlanDays = snapshot.existingPlanDaysByExerciseId()
                .getOrDefault(ex.getId(), Set.of())
                .stream()
                .toList();
        if (!alreadyInPlanDays.isEmpty()) {
            score -= 22.0; // Prefer fresh variety rather than duplicating across multiple days
        }

        // 8. Check volume warning if added
        int proposedSets = ex.getRecommendedSets() != null && ex.getRecommendedSets() > 0 ? ex.getRecommendedSets() : 3;
        StringBuilder warn = new StringBuilder();
        if (suggestedDay != null && targetDayExercises.size() >= HIGH_DAY_EXERCISE_THRESHOLD) {
            warn.append("Target day already has ").append(targetDayExercises.size()).append(" exercises.");
        }
        for (MuscleId pm : primary) {
            double currentWeekly = snapshot.weeklyMuscleSets().getOrDefault(pm, 0.0);
            if (currentWeekly + proposedSets > HIGH_WEEKLY_MUSCLE_SETS_THRESHOLD) {
                if (!warn.isEmpty()) warn.append(" ");
                warn.append(String.format(Locale.US, "%s weekly volume would reach %.0f+ sets.",
                        pm.getDisplayName(), currentWeekly + proposedSets));
                score -= 10.0;
                break;
            }
        }
        if (!alreadyInPlanDays.isEmpty()) {
            if (!warn.isEmpty()) warn.append(" ");
            warn.append("Already scheduled on ").append(String.join(", ", alreadyInPlanDays)).append(".");
        }

        String rationale = buildCandidateRationale(ex, primary, filledGaps, suggestedDay, userDifficulty, alreadyInPlanDays, isVn);
        return new ScoredCandidate(
                ex, resolved, primary, secondary, filledGaps, alreadyInPlanDays, score,
                warn.isEmpty() ? null : warn.toString(),
                rationale
        );
    }

    private ExerciseRecommendationItemDto toRecommendationItem(ScoredCandidate sc, boolean alreadyInTargetDay) {
        return toRecommendationItemInternal(sc, alreadyInTargetDay, true);
    }

    private ExerciseRecommendationItemDto toInformationalItem(ScoredCandidate sc, boolean includeRationale) {
        return toRecommendationItemInternal(sc, false, includeRationale);
    }

    private ExerciseRecommendationItemDto toRecommendationItemInternal(
            ScoredCandidate sc,
            boolean alreadyInTargetDay,
            boolean includeRationale
    ) {
        Exercise ex = sc.exercise();
        int sets = ex.getRecommendedSets() != null && ex.getRecommendedSets() > 0 ? ex.getRecommendedSets() : 3;
        int rest = ex.getRecommendedRestSeconds() != null && ex.getRecommendedRestSeconds() >= 0
                ? ex.getRecommendedRestSeconds() : 90;
        Integer reps = null;
        Integer durationSeconds = null;
        if (isStaticHoldExercise(ex)) {
            durationSeconds = 20;
        } else {
            reps = parseRecommendedReps(ex.getRecommendedReps()).orElse(10);
        }

        return ExerciseRecommendationItemDto.builder()
                .exerciseId(ex.getId())
                .name(ex.getName())
                .code(ex.getCode())
                .category(ex.getCategory() != null ? ex.getCategory().name() : null)
                .difficulty(ex.getDifficulty() != null ? ex.getDifficulty().name() : null)
                .primaryMuscleIds(sc.targets().primaryMuscleIds())
                .secondaryMuscleIds(sc.targets().secondaryMuscleIds())
                .primaryMuscleNames(sc.primaryMuscles().stream().map(MuscleId::getDisplayName).toList())
                .secondaryMuscleNames(sc.secondaryMuscles().stream().map(MuscleId::getDisplayName).toList())
                .recommendedSets(sets)
                .recommendedReps(reps)
                .recommendedDurationSeconds(durationSeconds)
                .recommendedRestSeconds(rest)
                .prescriptionSummary(formatPrescriptionSummary(sets, reps, durationSeconds, rest))
                .equipment(ex.getEquipment())
                .imageUrl(resolveBestImageUrl(ex))
                .rationale(includeRationale ? sc.rationale() : null)
                .fillsUndertrainedMuscles(sc.filledGaps())
                .alreadyInTargetDay(alreadyInTargetDay)
                .alreadyInPlanDays(sc.alreadyInPlanDays())
                .volumeWarning(sc.volumeWarning())
                .build();
    }

    private String buildInformationalRationale(Exercise ex, List<MuscleId> primary, boolean isVn) {
        if (ex.getDescription() != null && !ex.getDescription().isBlank()) {
            return ex.getDescription().trim();
        }
        String primaryNames = primary.isEmpty()
                ? (ex.getTargetMuscles() != null && !ex.getTargetMuscles().isBlank() ? ex.getTargetMuscles() : null)
                : primary.stream().map(MuscleId::getDisplayName).collect(Collectors.joining(" & "));
        if (primaryNames != null) {
            return isVn ? "Tác động vào " + primaryNames + "." : "Targets " + primaryNames + ".";
        }
        return null;
    }

    private String buildCandidateRationale(
            Exercise ex,
            List<MuscleId> primary,
            List<String> filledGaps,
            WorkoutPlanDay suggestedDay,
            DifficultyLevel userDifficulty,
            List<String> alreadyInPlanDays,
            boolean isVn
    ) {
        String primaryNames = primary.isEmpty()
                ? (ex.getTargetMuscles() != null ? ex.getTargetMuscles() : "key stabilizing muscles")
                : primary.stream().map(MuscleId::getDisplayName).collect(Collectors.joining(" & "));

        if (isVn) {
            if (!filledGaps.isEmpty()) {
                return "Tác động trực tiếp vào " + String.join(", ", filledGaps) + " đang còn thiếu trong lịch tập của bạn.";
            }
            return "Phát triển " + primaryNames + " phù hợp với mục tiêu hiện tại của bạn.";
        }

        StringBuilder sb = new StringBuilder();
        if (!filledGaps.isEmpty()) {
            sb.append("Directly targets ").append(String.join(" & ", filledGaps));
            if (suggestedDay != null) {
                sb.append(" to balance your ").append(formatDayLabel(suggestedDay.getDayOfWeek(), false)).append(" session");
            } else {
                sb.append(" which is currently undertrained in your weekly plan");
            }
            sb.append(".");
        } else {
            sb.append("Builds strength across ").append(primaryNames);
            if (ex.getEquipment() != null && !ex.getEquipment().isBlank()) {
                sb.append(" using ").append(ex.getEquipment());
            }
            sb.append(".");
        }
        if (userDifficulty != null && ex.getDifficulty() == userDifficulty) {
            sb.append(" Matches your ").append(capitalizeWords(userDifficulty.name())).append(" level.");
        }
        if (!alreadyInPlanDays.isEmpty()) {
            sb.append(" Note: already in your plan on ").append(String.join(", ", alreadyInPlanDays)).append(".");
        }
        return sb.toString();
    }

    private String formatRecommendationsMarkdown(
            ExerciseRecommendationsCardDto card,
            PlanSnapshot snapshot,
            WorkoutPlanDay suggestedDay,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🎯 ").append(card.getFocusLabel() != null ? card.getFocusLabel() : "Exercise Recommendations").append("\n\n");

        if (suggestedDay != null) {
            sb.append("**Target Session**: ").append(formatDayTitle(suggestedDay, isVn))
                    .append(" (").append(card.getTargetDayExerciseCount()).append(" exercises, ")
                    .append(card.getTargetDayTotalSets()).append(" sets)\n\n");
        } else if (snapshot.plan() != null) {
            sb.append("**Current Plan Analyzed**: ").append(snapshot.plan().getName()).append("\n\n");
        }

        if (!card.getUndertrainedMuscles().isEmpty()) {
            sb.append("**Gaps Detected**: ");
            sb.append(card.getUndertrainedMuscles().stream()
                    .map(g -> g.getDisplayName() + " (" + g.getReason() + ")")
                    .collect(Collectors.joining(" · ")));
            sb.append("\n\n");
        }

        if (!card.getRecommendations().isEmpty()) {
            sb.append("**Recommended Exercises** (select exercises below to preview adding to your workout):\n");
            for (ExerciseRecommendationItemDto item : card.getRecommendations()) {
                sb.append("- **").append(item.getName()).append("**");
                if (item.getPrescriptionSummary() != null) {
                    sb.append(" — ").append(item.getPrescriptionSummary());
                }
                if (item.getRationale() != null && !item.getRationale().isBlank()) {
                    sb.append("\n  _").append(item.getRationale()).append("_");
                }
                sb.append("\n");
            }
        }
        if (card.getSuggestedDayOfWeek() == null) {
            sb.append("\nSelect any exercises you'd like to include and choose a weekday below (or tell me e.g. _\"Add Bulgarian Split Squat to Wednesday\"_) to preview the update before saving.");
        } else {
            sb.append("\nSelect exercises below to generate a **Workout Plan Preview** for **")
                    .append(formatDayLabel(card.getSuggestedDayOfWeek(), isVn))
                    .append("** — nothing will be modified until you confirm.");
        }
        return sb.toString().trim();
    }

    private String formatConsultationMarkdown(
            WorkoutConsultationCardDto card,
            PlanSnapshot snapshot,
            WorkoutPlanDay focusDay,
            ClientProfile profile,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📊 Workout Consultation");
        if (card.getFocusArea() != null) {
            sb.append(": ").append(card.getFocusArea());
        }
        sb.append("\n\n");
        if (card.getBalanceScore() != null) {
            sb.append("**Routine Balance Score**: ").append(card.getBalanceScore()).append("/100");
            if (profile != null && profile.getExperienceLevel() != null) {
                sb.append(" · Level: ").append(capitalizeWords(profile.getExperienceLevel()));
            }
            sb.append("\n\n");
        }
        if (focusDay != null) {
            List<WorkoutPlanExercise> dayExs = snapshot.exercisesByDayId().getOrDefault(focusDay.getId(), List.of());
            sb.append("**Current ").append(formatDayTitle(focusDay, isVn)).append("**: ");
            if (dayExs.isEmpty()) {
                sb.append("Rest / Empty");
            } else {
                sb.append(dayExs.stream()
                        .map(pe -> pe.getExercise().getName() + " (" + (pe.getSets() != null ? pe.getSets() : 3) + " sets)")
                        .collect(Collectors.joining(", ")));
            }
            sb.append("\n\n");
        }
        for (ConsultationFindingDto finding : card.getFindings()) {
            String icon = "WARNING".equals(finding.getSeverity()) ? "⚠️" : "✅";
            sb.append(icon).append(" **").append(finding.getTitle()).append("** — ").append(finding.getDetail()).append("\n");
        }
        if (!card.getUndertrainedMuscles().isEmpty()) {
            sb.append("\n**Undertrained Muscle Groups**: ").append(String.join(", ", card.getUndertrainedMuscles())).append("\n");
        }
        if (!card.getOverloadedMuscles().isEmpty()) {
            sb.append("\n**High-Load Muscle Groups**: ").append(String.join(", ", card.getOverloadedMuscles())).append("\n");
        }
        sb.append("\n_No changes were made to your workout plan. Ask **\"")
                .append(card.getSuggestedActionPrompt())
                .append("\"** anytime to see targeted exercise options._");
        return sb.toString().trim();
    }

    // ==================== PLAN SNAPSHOT & ANALYTICS ====================

    private PlanSnapshot loadPlanSnapshot(Long userId) {
        if (userId == null) {
            return new PlanSnapshot(null, List.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Optional<WorkoutPlan> planOpt = resolveActiveWorkoutPlan(userId);
        if (planOpt.isEmpty()) {
            return new PlanSnapshot(null, List.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        WorkoutPlan plan = planOpt.get();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        Map<Long, List<WorkoutPlanExercise>> exByDay = new LinkedHashMap<>();
        Map<MuscleId, Double> weeklySets = new EnumMap<>(MuscleId.class);
        Map<Long, Map<MuscleId, Double>> dayMuscleSets = new LinkedHashMap<>();
        Map<Long, Set<String>> planDaysByExId = new LinkedHashMap<>();

        for (WorkoutPlanDay day : days) {
            List<WorkoutPlanExercise> dayExs = loadOrderedExercises(day);
            exByDay.put(day.getId(), dayExs);
            Map<MuscleId, Double> thisDayMuscles = new EnumMap<>(MuscleId.class);
            String dayLabel = formatDayLabel(day.getDayOfWeek(), false);

            for (WorkoutPlanExercise pe : dayExs) {
                Exercise ex = pe.getExercise();
                if (ex == null) continue;
                if (ex.getId() != null) {
                    planDaysByExId.computeIfAbsent(ex.getId(), k -> new LinkedHashSet<>()).add(dayLabel);
                }
                double sets = pe.getSets() != null && pe.getSets() > 0 ? pe.getSets() : 3.0;
                MuscleTaxonomyService.ResolvedMuscleTargets resolved = muscleTaxonomyService.resolveTargets(ex);
                for (String pId : resolved.primaryMuscleIds()) {
                    MuscleId.fromId(pId).ifPresent(m -> {
                        thisDayMuscles.merge(m, sets, Double::sum);
                        weeklySets.merge(m, sets, Double::sum);
                    });
                }
                for (String sId : resolved.secondaryMuscleIds()) {
                    MuscleId.fromId(sId).ifPresent(m -> {
                        thisDayMuscles.merge(m, sets * 0.5, Double::sum);
                        weeklySets.merge(m, sets * 0.5, Double::sum);
                    });
                }
            }
            dayMuscleSets.put(day.getId(), thisDayMuscles);
        }
        return new PlanSnapshot(plan, days, exByDay, weeklySets, dayMuscleSets, planDaysByExId);
    }

    private Optional<WorkoutPlan> resolveActiveWorkoutPlan(Long userId) {
        CoachClientRelationship activeCoach = coachClientRelationshipRepository
                .findFirstByClientIdAndStatusOrderByUpdatedAtDesc(userId, CoachClientRelationshipStatus.ACTIVE)
                .orElse(null);

        if (activeCoach != null) {
            List<WorkoutPlanAssignment> assignments = workoutPlanAssignmentRepository
                    .findByClientIdAndStatusOrderByCreatedAtDesc(userId, WorkoutPlanAssignmentStatus.ACTIVE);
            for (WorkoutPlanAssignment assignment : assignments) {
                if (assignment.getRelationship() != null
                        && Objects.equals(assignment.getRelationship().getId(), activeCoach.getId())
                        && assignment.getWorkoutPlan() != null) {
                    return Optional.of(workoutPlanRepository.findWithDetailsById(assignment.getWorkoutPlan().getId())
                            .orElse(assignment.getWorkoutPlan()));
                }
            }
        }

        Optional<WorkoutPlan> activePersonal = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
        if (activePersonal.isPresent()) {
            return Optional.of(workoutPlanRepository.findWithDetailsById(activePersonal.get().getId())
                    .orElse(activePersonal.get()));
        }
        List<WorkoutPlan> allPersonal = workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(userId);
        if (!allPersonal.isEmpty()) {
            WorkoutPlan first = allPersonal.get(0);
            return Optional.of(workoutPlanRepository.findWithDetailsById(first.getId()).orElse(first));
        }
        return Optional.empty();
    }

    private List<WorkoutPlanDay> loadOrderedDays(WorkoutPlan plan) {
        if (plan.getId() != null) {
            List<WorkoutPlanDay> repoDays = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(plan.getId());
            if (repoDays != null && !repoDays.isEmpty()) {
                return repoDays;
            }
        }
        if (plan.getDays() != null) {
            return plan.getDays().stream()
                    .sorted(Comparator.comparing(d -> d.getDayOfWeek() != null ? d.getDayOfWeek().getOrder() : 99))
                    .toList();
        }
        return List.of();
    }

    private List<WorkoutPlanExercise> loadOrderedExercises(WorkoutPlanDay day) {
        if (day == null) return List.of();
        if (day.getId() != null) {
            List<WorkoutPlanExercise> repoExs = workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
            if (repoExs != null && !repoExs.isEmpty()) {
                return repoExs;
            }
        }
        if (day.getExercises() != null) {
            return day.getExercises().stream()
                    .sorted(Comparator.comparing(WorkoutPlanExercise::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }
        return List.of();
    }

    private List<Exercise> loadPublishedCatalog() {
        List<Exercise> active = exerciseRepository.findByActiveTrueOrderByNameAsc();
        if (active == null) {
            active = exerciseRepository.findAll();
        }
        if (active == null) {
            return List.of();
        }
        return active.stream()
                .filter(e -> e != null && e.isActive() && e.getStatus() != ExerciseStatus.DRAFT)
                .toList();
    }

    private List<PlanDayOptionDto> buildPlanDayOptions(PlanSnapshot snapshot, boolean isVn) {
        return snapshot.days().stream()
                .map(d -> PlanDayOptionDto.builder()
                        .dayOfWeek(d.getDayOfWeek())
                        .dayTitle(formatDayTitle(d, isVn))
                        .sessionType(d.getSessionType() != null ? d.getSessionType().name() : null)
                        .restDay(d.isRestDay())
                        .exerciseCount(snapshot.exercisesByDayId().getOrDefault(d.getId(), List.of()).size())
                        .build())
                .toList();
    }

    private List<String> buildRecommendationPlanWarnings(
            PlanSnapshot snapshot,
            WorkoutPlanDay suggestedDay,
            List<WorkoutPlanExercise> targetDayExercises,
            boolean includedAlreadyOnTargetDay,
            boolean isVn
    ) {
        List<String> warnings = new ArrayList<>();
        if (includedAlreadyOnTargetDay) {
            warnings.add(isVn
                    ? "Một số bài tập gợi ý đã có trong ngày tập này."
                    : "Some matching exercises are already scheduled on the selected day.");
        }
        if (suggestedDay != null && targetDayExercises.size() >= HIGH_DAY_EXERCISE_THRESHOLD) {
            warnings.add("Your " + formatDayLabel(suggestedDay.getDayOfWeek(), false)
                    + " session already contains " + targetDayExercises.size() + " exercises — avoid adding too many at once.");
        }
        if (suggestedDay != null && suggestedDay.isRestDay()) {
            warnings.add(formatDayLabel(suggestedDay.getDayOfWeek(), false)
                    + " is currently marked as a Rest Day. Adding exercises will convert it into an active training day upon confirmation.");
        }
        return warnings;
    }

    private boolean hasDirectPrimaryMuscle(List<WorkoutPlanExercise> dayExercises, MuscleId muscleId) {
        if (dayExercises == null || muscleId == null) return false;
        for (WorkoutPlanExercise pe : dayExercises) {
            if (pe.getExercise() == null) continue;
            MuscleTaxonomyService.ResolvedMuscleTargets resolved = muscleTaxonomyService.resolveTargets(pe.getExercise());
            if (resolved.primaryMuscleIds().contains(muscleId.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<String> detectConsecutiveMuscleOverlaps(PlanSnapshot snapshot) {
        List<String> overlaps = new ArrayList<>();
        List<WorkoutPlanDay> days = snapshot.days();
        for (int i = 0; i + 1 < days.size(); i++) {
            WorkoutPlanDay d1 = days.get(i);
            WorkoutPlanDay d2 = days.get(i + 1);
            if (d1.isRestDay() || d2.isRestDay()) continue;
            Map<MuscleId, Double> m1 = snapshot.dayMuscleSets().getOrDefault(d1.getId(), Map.of());
            Map<MuscleId, Double> m2 = snapshot.dayMuscleSets().getOrDefault(d2.getId(), Map.of());
            for (MuscleId keyMuscle : List.of(MuscleId.FRONT_DELTOIDS, MuscleId.CHEST, MuscleId.LATISSIMUS_DORSI, MuscleId.QUADRICEPS, MuscleId.HAMSTRINGS)) {
                double s1 = m1.getOrDefault(keyMuscle, 0.0);
                double s2 = m2.getOrDefault(keyMuscle, 0.0);
                if (s1 >= 4.0 && s2 >= 4.0) {
                    overlaps.add(keyMuscle.getDisplayName() + " gets heavy work on consecutive days ("
                            + formatDayLabel(d1.getDayOfWeek(), false) + " & "
                            + formatDayLabel(d2.getDayOfWeek(), false) + ").");
                    break;
                }
            }
        }
        return overlaps;
    }

    private int countSkillWeeklySets(PlanSnapshot snapshot, List<String> keywords) {
        int total = 0;
        for (WorkoutPlanDay day : snapshot.days()) {
            for (WorkoutPlanExercise pe : snapshot.exercisesByDayId().getOrDefault(day.getId(), List.of())) {
                Exercise ex = pe.getExercise();
                if (ex == null || ex.getName() == null) continue;
                String name = ex.getName().toLowerCase(Locale.US);
                boolean matched = ex.getCategory() == ExerciseCategory.SKILL
                        || ex.getCategory() == ExerciseCategory.ISOMETRIC
                        || keywords.stream().anyMatch(name::contains);
                if (matched) {
                    total += pe.getSets() != null ? pe.getSets() : 3;
                }
            }
        }
        return total;
    }

    private int countDaysMatchingKeyword(PlanSnapshot snapshot, String keyword) {
        int count = 0;
        for (WorkoutPlanDay day : snapshot.days()) {
            boolean dayMatches = day.getName() != null && day.getName().toLowerCase(Locale.US).contains(keyword);
            if (!dayMatches) {
                for (WorkoutPlanExercise pe : snapshot.exercisesByDayId().getOrDefault(day.getId(), List.of())) {
                    if (pe.getExercise() != null && pe.getExercise().getName() != null
                            && pe.getExercise().getName().toLowerCase(Locale.US).contains(keyword)) {
                        dayMatches = true;
                        break;
                    }
                }
            }
            if (dayMatches) count++;
        }
        return count;
    }

    private DifficultyLevel resolveUserDifficulty(
            ClientProfile profile,
            List<WorkoutSession> recentSessions,
            List<PersonalRecord> recentPrs
    ) {
        if (profile != null && profile.getExperienceLevel() != null) {
            try {
                return DifficultyLevel.valueOf(profile.getExperienceLevel().trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException ignored) {}
        }
        if (recentSessions.size() >= 15 || recentPrs.size() >= 5) {
            return DifficultyLevel.INTERMEDIATE;
        }
        return DifficultyLevel.BEGINNER;
    }

    private Set<String> extractMovementPatterns(List<WorkoutPlanExercise> dayExercises) {
        Set<String> patterns = new HashSet<>();
        for (WorkoutPlanExercise pe : dayExercises) {
            if (pe.getExercise() == null || pe.getExercise().getName() == null) continue;
            String n = pe.getExercise().getName().toLowerCase(Locale.US);
            if (n.contains("squat") || n.contains("lunge")) patterns.add("SQUAT");
            if (n.contains("deadlift") || n.contains("nordic") || n.contains("leg curl") || n.contains("bridge")) {
                patterns.add("HINGE");
            }
        }
        return patterns;
    }

    private boolean isLowerBodyExercise(Exercise ex, List<MuscleId> primary, List<MuscleId> secondary) {
        if (ex.getCategory() == ExerciseCategory.LEGS || ex.getCategory() == ExerciseCategory.GLUTES) {
            return true;
        }
        return primary.stream().anyMatch(m -> m.getRegion() == MuscleRegion.LOWER_BODY)
                || secondary.stream().anyMatch(m -> m.getRegion() == MuscleRegion.LOWER_BODY);
    }

    private List<MuscleId> toMuscleEnums(List<String> ids) {
        if (ids == null) return List.of();
        return ids.stream()
                .map(id -> MuscleId.fromId(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private String resolveBestImageUrl(Exercise ex) {
        try {
            List<ExerciseMediaResponse> media = ExerciseMediaMapper.toResponses(ex);
            for (ExerciseMediaResponse item : media) {
                if (item.thumbnailUrl() != null && !item.thumbnailUrl().isBlank()) {
                    return item.thumbnailUrl();
                }
                if (item.url() != null && !item.url().isBlank()) {
                    return item.url();
                }
            }
        } catch (Exception ignored) {}
        return (ex.getImageUrl() != null && !ex.getImageUrl().isBlank()) ? ex.getImageUrl() : null;
    }

    private static boolean isStaticHoldExercise(Exercise ex) {
        if (ex == null) return false;
        if (ex.getCategory() == ExerciseCategory.ISOMETRIC || "STATIC_HOLD".equalsIgnoreCase(ex.getExerciseType())) {
            return true;
        }
        String lower = ex.getName() != null ? ex.getName().toLowerCase(Locale.US) : "";
        return (lower.contains("planche") || lower.contains("front lever") || lower.contains("back lever")
                || lower.contains("l-sit") || lower.equals("handstand") || lower.contains("plank"))
                && !lower.contains("raise") && !lower.contains("push") && !lower.contains("pull")
                && !lower.contains("row") && !lower.contains("press");
    }

    private static Optional<Integer> parseRecommendedReps(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(raw);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v >= 1 && v <= 200) return Optional.of(v);
            } catch (NumberFormatException ignored) {}
        }
        return Optional.empty();
    }

    private static String formatPrescriptionSummary(int sets, Integer reps, Integer durationSeconds, int rest) {
        StringBuilder sb = new StringBuilder();
        sb.append(sets).append(" sets");
        if (durationSeconds != null && durationSeconds > 0) {
            sb.append(" × ").append(durationSeconds).append("s hold");
        } else if (reps != null && reps > 0) {
            sb.append(" × ").append(reps).append(" reps");
        }
        if (rest > 0) {
            sb.append(" · Rest ").append(rest).append("s");
        }
        return sb.toString();
    }

    private WorkoutPlanDay findDay(List<WorkoutPlanDay> days, WorkoutDayOfWeek target) {
        if (days == null || target == null) return null;
        return days.stream().filter(d -> d.getDayOfWeek() == target).findFirst().orElse(null);
    }

    private static int sumDaySets(List<WorkoutPlanExercise> exercises) {
        return exercises.stream().mapToInt(pe -> pe.getSets() != null && pe.getSets() > 0 ? pe.getSets() : 3).sum();
    }

    private static String formatDayLabel(WorkoutDayOfWeek dow, boolean isVn) {
        if (dow == null) return "Day";
        if (isVn) {
            return switch (dow) {
                case MONDAY -> "Thứ Hai";
                case TUESDAY -> "Thứ Ba";
                case WEDNESDAY -> "Thứ Tư";
                case THURSDAY -> "Thứ Năm";
                case FRIDAY -> "Thứ Sáu";
                case SATURDAY -> "Thứ Bảy";
                case SUNDAY -> "Chủ Nhật";
            };
        }
        String n = dow.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.US);
    }

    private static String formatDayTitle(WorkoutPlanDay day, boolean isVn) {
        String label = formatDayLabel(day.getDayOfWeek(), isVn);
        if (day.getName() != null && !day.getName().isBlank()) {
            return label + " — " + day.getName();
        }
        if (day.getSessionType() != null) {
            return label + " — " + day.getSessionType().name();
        }
        return label;
    }

    private static String capitalizeWords(String input) {
        if (input == null || input.isBlank()) return "";
        String[] parts = input.toLowerCase(Locale.US).trim().split("\\s+");
        return Arrays.stream(parts)
                .filter(p -> !p.isBlank())
                .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private PlanSnapshot emptyPlanSnapshot() {
        return new PlanSnapshot(null, List.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private List<ScoredCandidate> rankCatalogCandidates(
            List<Exercise> publishedCatalog,
            PromptCriteria criteria,
            PlanSnapshot snapshot,
            WorkoutPlanDay suggestedDay,
            List<WorkoutPlanExercise> targetDayExercises,
            Set<String> targetDayPatterns,
            List<MuscleGapInsightDto> undertrained,
            Map<MuscleId, Double> referenceSets,
            DifficultyLevel userDifficulty,
            ClientProfile profile,
            boolean includePlanContext,
            boolean isVn
    ) {
        List<ScoredCandidate> ranked = new ArrayList<>();
        for (Exercise ex : publishedCatalog) {
            ScoredCandidate scored = scoreCandidate(
                    ex, criteria, snapshot, suggestedDay, targetDayExercises,
                    targetDayPatterns, undertrained, referenceSets, userDifficulty, profile,
                    includePlanContext, isVn
            );
            if (scored != null) {
                ranked.add(scored);
            }
        }
        ranked.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(sc -> sc.exercise().getName(), String.CASE_INSENSITIVE_ORDER));
        return ranked;
    }

    private ActiveTopicHistory extractActiveTopicHistory(
            List<AiMessage> priorHistory,
            List<Exercise> catalog,
            PromptCriteria currentCriteria,
            boolean isAnotherFollowUp
    ) {
        ExerciseDeduplicator.DeduplicationState exclusions = new ExerciseDeduplicator.DeduplicationState();
        if (priorHistory == null || priorHistory.isEmpty()) {
            return new ActiveTopicHistory(null, currentCriteria.criteriaKey(), null, false, exclusions);
        }

        // Only carry/accumulate exclusions across history for continuation queries or active topic refinements;
        // a brand-new standalone non-follow-up query with a different criteria starts fresh.
        String anchorTopicPrompt = null;
        String anchorCriteriaKey = null;
        String anchorIntent = null;
        boolean usedSupplementalAi = false;

        for (int i = priorHistory.size() - 1; i >= 0; i--) {
            AiMessage m = priorHistory.get(i);
            if (m == null || !"assistant".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            String mIntent = m.getIntent() != null ? m.getIntent().trim().toUpperCase(Locale.US) : "";
            if (!"LIST_EXERCISES".equals(mIntent) && !"EXERCISE_RECOMMENDATION".equals(mIntent)) {
                continue;
            }

            ParsedMessageTopic parsed = parseHistoryMessageTopic(m, catalog);
            String msgKey = parsed.criteriaKey() != null ? parsed.criteriaKey() : currentCriteria.criteriaKey();

            if (anchorCriteriaKey == null) {
                if (!isAnotherFollowUp) {
                    // Standalone non-follow-up request starts a fresh recommendation topic when user asks anew
                    return new ActiveTopicHistory(null, currentCriteria.criteriaKey(), null, false, exclusions);
                }
                if (currentCriteria.hasExplicitFilter() && !isCompatibleCriteriaKey(currentCriteria.criteriaKey(), msgKey)) {
                    return new ActiveTopicHistory(null, currentCriteria.criteriaKey(), null, false, exclusions);
                }
                anchorCriteriaKey = msgKey;
                anchorTopicPrompt = parsed.topicPrompt();
                anchorIntent = mIntent;
            } else if (!isCompatibleCriteriaKey(anchorCriteriaKey, msgKey)) {
                // Stop walking backward when hitting an earlier, genuinely different topic
                break;
            }

            if (parsed.usedSupplementalAi()) {
                usedSupplementalAi = true;
            }
            for (Long exId : parsed.exerciseIds()) {
                Exercise catEx = catalog.stream().filter(c -> Objects.equals(c.getId(), exId)).findFirst().orElse(null);
                if (catEx != null) {
                    exclusions.recordExcludedCatalogExercise(catEx);
                } else {
                    exclusions.recordExcluded(exId, null, null, null);
                }
            }
            for (String exName : parsed.exerciseNames()) {
                Exercise catEx = ExerciseDeduplicator.matchCatalogExercise(exName, catalog);
                if (catEx != null) {
                    exclusions.recordExcludedCatalogExercise(catEx);
                } else {
                    exclusions.recordExcluded(null, exName, null, null);
                }
            }
        }

        return new ActiveTopicHistory(anchorTopicPrompt, anchorCriteriaKey, anchorIntent, usedSupplementalAi, exclusions);
    }

    private record ParsedMessageTopic(
            String topicPrompt,
            String criteriaKey,
            boolean usedSupplementalAi,
            List<Long> exerciseIds,
            List<String> exerciseNames
    ) {}

    private ParsedMessageTopic parseHistoryMessageTopic(AiMessage m, List<Exercise> catalog) {
        String topicPrompt = null;
        String criteriaKey = null;
        boolean usedSupplementalAi = false;
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();

        if (m.getMetadata() != null && !m.getMetadata().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(m.getMetadata());
                topicPrompt = root.hasNonNull("topicPrompt") ? root.get("topicPrompt").asText() : null;
                criteriaKey = root.hasNonNull("criteriaKey") ? root.get("criteriaKey").asText() : null;
                usedSupplementalAi = root.path("usedSupplementalAi").asBoolean(false);

                JsonNode itemsNode = root.has("recommendations") && root.get("recommendations").isArray()
                        ? root.get("recommendations")
                        : (root.has("exercises") && root.get("exercises").isArray() ? root.get("exercises") : null);
                if (itemsNode != null) {
                    for (JsonNode item : itemsNode) {
                        if (item.hasNonNull("exerciseId")) {
                            long id = item.get("exerciseId").asLong(-1L);
                            if (id > 0) ids.add(id);
                        }
                        if (item.hasNonNull("name")) {
                            String n = item.get("name").asText();
                            if (n != null && !n.isBlank()) names.add(n);
                        }
                    }
                }
                JsonNode suppNode = root.get("supplementalNames");
                if (suppNode != null && suppNode.isArray()) {
                    for (JsonNode n : suppNode) {
                        if (n.isTextual() && !n.asText().isBlank()) {
                            names.add(n.asText());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (ids.isEmpty() && names.isEmpty() && m.getContent() != null) {
            for (ExerciseDeduplicator.ParsedExerciseLine line : ExerciseDeduplicator.parseExerciseLines(m.getContent())) {
                names.add(line.name());
            }
        }
        return new ParsedMessageTopic(topicPrompt, criteriaKey, usedSupplementalAi, ids, names);
    }

    private PromptCriteria resolveEffectiveCriteria(
            PromptCriteria rawCriteria,
            String rawPrompt,
            ActiveTopicHistory history,
            boolean isAnotherFollowUp
    ) {
        if (!isAnotherFollowUp || rawCriteria.hasExplicitFilter()) {
            return rawCriteria;
        }
        String fallbackTopic = history.topicPrompt();
        if ((fallbackTopic == null || fallbackTopic.isBlank()) && history.criteriaKey() != null) {
            fallbackTopic = topicPromptFromCriteriaKey(history.criteriaKey());
        }
        if (fallbackTopic == null || fallbackTopic.isBlank()) {
            return rawCriteria;
        }
        PromptCriteria inherited = parsePromptCriteria(fallbackTopic, null);
        return new PromptCriteria(
                inherited.targetMuscles(),
                inherited.targetCategories(),
                inherited.skillKeywords(),
                inherited.wholeRoutineGapAnalysis(),
                inherited.lowerBodyFocus(),
                inherited.upperPushFocus(),
                inherited.upperPullFocus(),
                inherited.coreFocus(),
                rawCriteria.bodyweightOnly() || inherited.bodyweightOnly(),
                rawCriteria.requestedTrainingType() != null ? rawCriteria.requestedTrainingType() : inherited.requestedTrainingType(),
                rawCriteria.equipmentFilter() != null ? rawCriteria.equipmentFilter() : inherited.equipmentFilter(),
                inherited.focusLabel(),
                inherited.listHeading(),
                inherited.criteriaKey(),
                rawCriteria.requestedCount(),
                inherited.hasExplicitFilter()
        );
    }

    private static String topicPromptFromCriteriaKey(String key) {
        if (key == null || key.isBlank()) return null;
        if ("LEGS".equals(key)) return "leg exercises";
        if (key.startsWith("SKILL:")) return key.substring("SKILL:".length()).replace('_', ' ').toLowerCase(Locale.US) + " exercises";
        if (key.startsWith("MUSCLES:")) return key.substring("MUSCLES:".length()).replace('_', ' ').toLowerCase(Locale.US) + " exercises";
        if (key.startsWith("CATS:")) return key.substring("CATS:".length()).replace('_', ' ').toLowerCase(Locale.US) + " exercises";
        return null;
    }

    private static boolean isCompatibleCriteriaKey(String currentKey, String historyKey) {
        if (currentKey == null || historyKey == null) return false;
        if (currentKey.equals(historyKey)) return true;
        if ("GENERAL".equals(currentKey) || "GENERAL".equals(historyKey)) return false;
        if ("LEGS".equals(historyKey) && currentKey.contains("HAMSTRINGS")) return true;
        if ("LEGS".equals(historyKey) && currentKey.contains("QUADRICEPS")) return true;
        if ("LEGS".equals(historyKey) && currentKey.contains("GLUTES")) return true;
        if ("LEGS".equals(historyKey) && currentKey.contains("CALVES")) return true;
        return false;
    }

    private static boolean isSingularAnotherPhrase(String lower) {
        if (lower == null) return false;
        boolean mentionsPluralExercises = lower.contains("more exercises") || lower.contains("other exercises")
                || lower.contains("different exercises") || lower.contains("anything else") || lower.contains("what else");
        if (mentionsPluralExercises) {
            return false;
        }
        return lower.contains("another") || lower.contains("different one")
                || lower.contains("different exercise") || lower.contains("bài khác")
                || lower.contains("bài tập khác");
    }

    private ExerciseDeduplicator.DeduplicationState copyDeduplicationState(ExerciseDeduplicator.DeduplicationState src) {
        ExerciseDeduplicator.DeduplicationState copy = new ExerciseDeduplicator.DeduplicationState();
        for (Long id : src.excludedIds()) {
            copy.recordExcluded(id, null, null, null);
        }
        for (String display : src.excludedDisplayNames()) {
            copy.recordExcluded(null, display, null, null);
        }
        for (String key : src.excludedNormalizedKeys()) {
            copy.addNormalizedKeyOnly(key);
        }
        return copy;
    }

    private List<SupplementalCandidate> fetchSupplementalFromGemini(
            String userPrompt,
            PromptCriteria criteria,
            ExerciseDeduplicator.DeduplicationState exclusions,
            int maxNeeded,
            List<Exercise> publishedCatalog,
            boolean includeBriefDescriptions,
            boolean isVn
    ) {
        if (geminiApiClient == null || maxNeeded <= 0) {
            return List.of();
        }
        try {
            StringBuilder sys = new StringBuilder();
            sys.append("You are an informational calisthenics and strength exercise directory.\n");
            sys.append("Target topic/focus: ").append(criteria.listHeading()).append(".\n");
            if (criteria.bodyweightOnly()) {
                sys.append("Equipment constraint: bodyweight / no equipment only.\n");
            } else if (criteria.equipmentFilter() != null) {
                sys.append("Equipment constraint: ").append(criteria.equipmentFilter()).append(".\n");
            }
            if (!exclusions.isEmpty()) {
                sys.append("ALREADY RECOMMENDED OR LISTED (EXCLUSION CONSTRAINTS - NEVER REPEAT, RENAME, OR REPHRASE THESE, INCLUDING ABBREVIATIONS LIKE DB/DUMBBELL OR RDL/ROMANIAN DEADLIFT):\n");
                if (!exclusions.excludedIds().isEmpty()) {
                    sys.append("- Excluded IDs: ").append(exclusions.excludedIds()).append("\n");
                }
                if (!exclusions.excludedDisplayNames().isEmpty()) {
                    sys.append("- Excluded Names: ").append(String.join(", ", exclusions.excludedDisplayNames())).append("\n");
                }
            }
            sys.append("STRICT OUTPUT RULES:\n");
            sys.append("- Return up to ").append(maxNeeded).append(" NEW relevant exercise(s) in a clean numbered Markdown list inside 'reply'.\n");
            if (includeBriefDescriptions) {
                sys.append("- Format each item strictly as: 1. **Exercise Name** — concise 1-sentence description.\n");
            } else {
                sys.append("- Format each item strictly as: 1. Exercise Name\n");
            }
            sys.append("- NEVER analyze any workout plan, detect muscle gaps, report weekly sets or volume, suggest adding exercises to weekdays, ask which day to train, or append follow-up questions.\n");
            sys.append("- If no suitable unseen exercises remain, set reply to 'NO_MORE_EXERCISES'.\n");
            if (isVn) {
                sys.append("- Respond in Vietnamese for headings/descriptions while keeping clear exercise names.\n");
            }

            List<GeminiRequestDto.Content> contents = List.of(GeminiRequestDto.Content.builder()
                    .role("user")
                    .parts(List.of(new GeminiRequestDto.Part(
                            userPrompt != null && !userPrompt.isBlank() ? userPrompt : criteria.listHeading())))
                    .build());

            Optional<GeminiAssistantOutput> outOpt = geminiApiClient.callAssistant(sys.toString(), contents);
            if (outOpt.isEmpty() || outOpt.get().getReply() == null || outOpt.get().getReply().isBlank()) {
                return List.of();
            }
            String reply = outOpt.get().getReply().trim();
            if (reply.contains("NO_MORE_EXERCISES")) {
                return List.of();
            }
            return filterSupplementalLines(
                    ExerciseDeduplicator.parseExerciseLines(reply),
                    exclusions,
                    publishedCatalog,
                    maxNeeded
            );
        } catch (Exception ex) {
            log.warn("Supplemental exercise retrieval from Gemini failed cleanly: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<String> filterUnseenExerciseNames(
            List<String> candidateNames,
            Set<Long> excludedIds,
            List<String> excludedNames,
            List<Exercise> catalog
    ) {
        ExerciseDeduplicator.DeduplicationState state = new ExerciseDeduplicator.DeduplicationState();
        if (excludedIds != null) {
            for (Long id : excludedIds) {
                state.recordExcluded(id, null, null, null);
            }
        }
        if (excludedNames != null) {
            for (String n : excludedNames) {
                Exercise match = ExerciseDeduplicator.matchCatalogExercise(n, catalog);
                if (match != null) {
                    state.recordExcludedCatalogExercise(match);
                } else {
                    state.recordExcluded(null, n, null, null);
                }
            }
        }
        List<String> result = new ArrayList<>();
        if (candidateNames == null) return result;
        for (String candidate : candidateNames) {
            if (candidate == null || candidate.isBlank()) continue;
            if (state.isExcludedName(candidate, catalog)) {
                continue;
            }
            Exercise match = ExerciseDeduplicator.matchCatalogExercise(candidate, catalog);
            if (match != null) {
                state.recordExcludedCatalogExercise(match);
            } else {
                state.recordExcluded(null, candidate, null, null);
            }
            result.add(candidate.trim());
        }
        return result;
    }

    private List<SupplementalCandidate> filterSupplementalLines(
            List<ExerciseDeduplicator.ParsedExerciseLine> lines,
            ExerciseDeduplicator.DeduplicationState exclusions,
            List<Exercise> catalog,
            int maxNeeded
    ) {
        if (lines == null || lines.isEmpty() || maxNeeded <= 0) {
            return List.of();
        }
        List<SupplementalCandidate> accepted = new ArrayList<>();
        for (ExerciseDeduplicator.ParsedExerciseLine line : lines) {
            if (line.name() == null || line.name().isBlank()) continue;
            if (exclusions.isExcludedName(line.name(), catalog)) {
                continue;
            }
            Exercise catalogMatch = ExerciseDeduplicator.matchCatalogExercise(line.name(), catalog);
            if (catalogMatch != null) {
                exclusions.recordExcludedCatalogExercise(catalogMatch);
            } else {
                exclusions.recordExcluded(null, line.name(), null, null);
            }
            accepted.add(new SupplementalCandidate(line.name(), line.description(), catalogMatch));
            if (accepted.size() >= maxNeeded) {
                break;
            }
        }
        return accepted;
    }

    private String formatListMarkdown(
            PromptCriteria criteria,
            List<ExerciseRecommendationItemDto> catalogItems,
            List<SupplementalCandidate> supplemental,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(criteria.listHeading()).append("**\n");
        int index = 1;
        for (ExerciseRecommendationItemDto item : catalogItems) {
            sb.append(index++).append(". ").append(item.getName()).append("\n");
        }
        for (SupplementalCandidate sup : supplemental) {
            sb.append(index++).append(". ").append(sup.name()).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatInformationalRecommendationsMarkdown(
            PromptCriteria criteria,
            List<ExerciseRecommendationItemDto> catalogItems,
            List<SupplementalCandidate> supplemental,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(criteria.listHeading()).append("**\n");
        int index = 1;
        for (ExerciseRecommendationItemDto item : catalogItems) {
            sb.append(index++).append(". **").append(item.getName()).append("**");
            if (item.getRationale() != null && !item.getRationale().isBlank()) {
                sb.append(" — ").append(item.getRationale());
            }
            sb.append("\n");
        }
        for (SupplementalCandidate sup : supplemental) {
            sb.append(index++).append(". **").append(sup.name()).append("**");
            if (sup.description() != null && !sup.description().isBlank()) {
                sb.append(" — ").append(sup.description());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildExerciseListMetadata(
            PromptCriteria criteria,
            String rawPrompt,
            ActiveTopicHistory history,
            List<ExerciseRecommendationItemDto> items,
            List<SupplementalCandidate> supplemental,
            boolean usedSupplementalAi,
            boolean exhausted
    ) {
        List<Map<String, Object>> trackedExercises = new ArrayList<>();
        List<String> suppNames = new ArrayList<>();
        for (ExerciseRecommendationItemDto item : items) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (item.getExerciseId() != null) entry.put("exerciseId", item.getExerciseId());
            entry.put("name", item.getName());
            trackedExercises.add(entry);
        }
        for (SupplementalCandidate sup : supplemental) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (sup.catalogMatch() != null && sup.catalogMatch().getId() != null) {
                entry.put("exerciseId", sup.catalogMatch().getId());
            }
            entry.put("name", sup.name());
            trackedExercises.add(entry);
            suppNames.add(sup.name());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "EXERCISE_LIST");
        meta.put("readOnly", true);
        meta.put("mutated", false);
        meta.put("focusLabel", criteria.listHeading());
        meta.put("criteriaKey", criteria.criteriaKey());
        meta.put("topicPrompt", effectiveTopicPrompt(rawPrompt, history));
        meta.put("usedSupplementalAi", usedSupplementalAi);
        meta.put("exhausted", exhausted);
        meta.put("exercises", trackedExercises);
        meta.put("supplementalNames", suppNames);
        return safeToJson(meta);
    }

    @SuppressWarnings("unchecked")
    private String injectExtraFieldsIntoJson(String json, boolean usedSupplementalAi, List<SupplementalCandidate> supplemental) {
        if (json == null) return null;
        if (!usedSupplementalAi && (supplemental == null || supplemental.isEmpty())) {
            return json;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            map.put("usedSupplementalAi", usedSupplementalAi);
            if (supplemental != null && !supplemental.isEmpty()) {
                map.put("supplementalNames", supplemental.stream().map(SupplementalCandidate::name).toList());
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return json;
        }
    }

    private static String effectiveTopicPrompt(String currentPrompt, ActiveTopicHistory history) {
        if (history != null && history.topicPrompt() != null && !history.topicPrompt().isBlank()) {
            return history.topicPrompt();
        }
        return currentPrompt;
    }

    private String buildNoPriorTopicMessage(boolean isVn) {
        return isVn
                ? "Hãy cho tôi biết bạn muốn tìm bài tập cho nhóm cơ hoặc kỹ năng nào trước nhé."
                : "Please specify which muscle group or skill you want exercises for first.";
    }

    private String buildNoMoreExercisesMessage(PromptCriteria criteria, boolean isVn) {
        if (isVn) {
            return "Hiện không còn bài tập phù hợp nào khác trong tiêu chí này ngoài các bài đã được gợi ý.";
        }
        String label = criteria != null && criteria.listHeading() != null && !"Calisthenics Exercises".equals(criteria.listHeading())
                ? criteria.listHeading().toLowerCase(Locale.US).replace("exercises", "").trim()
                : "";
        if (!label.isBlank()) {
            return "There are no more suitable " + label + " exercises available within the current criteria.";
        }
        return "There are no more suitable exercises available within the current criteria.";
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
