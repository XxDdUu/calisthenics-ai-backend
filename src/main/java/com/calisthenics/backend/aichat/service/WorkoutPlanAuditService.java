package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto;
import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto.AthleteSnapshotDto;
import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto.ConsultationFindingDto;
import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto.MovementPatternAuditDto;
import com.calisthenics.backend.aichat.dto.recommendation.WorkoutConsultationCardDto.MuscleCoverageDto;
import com.calisthenics.backend.aichat.service.WorkoutPlanContextService.ActiveWorkoutPlanContext;
import com.calisthenics.backend.aichat.service.WorkoutPlanContextService.DayContext;
import com.calisthenics.backend.aichat.service.WorkoutPlanContextService.ExerciseContext;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.muscle.MuscleId;
import com.calisthenics.backend.exercise.muscle.MuscleRegion;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Performs comprehensive, data-driven athlete and active-workout-plan audits using deterministic
 * database retrieval and volume calculations (direct vs. indirect stimulus, movement patterns,
 * skill-specific progression vs. PRs, program priority inference, and recovery spacing).
 */
@Service
@RequiredArgsConstructor
public class WorkoutPlanAuditService {

    private static final double HIGH_WEEKLY_MUSCLE_SETS_THRESHOLD = 18.0;

    private static final List<MuscleId> AUDITED_MUSCLES = List.of(
            MuscleId.CHEST,
            MuscleId.FRONT_DELTOIDS,
            MuscleId.LATERAL_DELTOIDS,
            MuscleId.REAR_DELTOIDS,
            MuscleId.LATISSIMUS_DORSI,
            MuscleId.RHOMBOIDS,
            MuscleId.BICEPS,
            MuscleId.TRICEPS,
            MuscleId.FOREARMS,
            MuscleId.RECTUS_ABDOMINIS,
            MuscleId.ERECTOR_SPINAE,
            MuscleId.QUADRICEPS,
            MuscleId.HAMSTRINGS,
            MuscleId.GLUTES,
            MuscleId.CALVES
    );

    private final WorkoutPlanContextService workoutPlanContextService;
    private final ClientProfileRepository clientProfileRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final WorkoutSessionRepository workoutSessionRepository;

    @Autowired(required = false)
    private ClientBodyMeasurementRepository measurementRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    public enum CoverageStatus {
        MISSING,
        UNDERDEVELOPED,
        ADEQUATELY_COVERED,
        OVERLOADED
    }

    public record KeyPrSnapshot(
            String categoryKey,
            String metricLabel,
            String exerciseName,
            PersonalRecordType recordType,
            BigDecimal recordValue,
            String unit,
            BigDecimal secondaryValue,
            BigDecimal previousValue,
            BigDecimal improvementPct,
            ZonedDateTime achievedAt,
            String formattedSummary
    ) {}

    public record AthleteProfileSnapshot(
            Long userId,
            String fullName,
            String gender,
            Double weightKg,
            ZonedDateTime weightMeasuredAt,
            Double weightTrendDeltaKg,
            Double heightCm,
            String experienceLevel,
            String trainingExperience,
            String primaryGoals,
            String trainingInterests,
            String preferredFrequency,
            List<KeyPrSnapshot> keyPrs,
            List<PersonalRecord> allPrs,
            int completedSessionsCount,
            int completedLast30DaysCount,
            int incompleteSessionsCount,
            LocalDate lastCompletedDate,
            Set<String> inferredEquipment,
            List<String> missingDataNotes
    ) {}

    public record ProgramPriorityProfile(
            boolean specializedProgram,
            List<String> primarySkills,
            List<String> trainingModalities,
            boolean emphasizesUpperBodySpecialization,
            String summaryLabel
    ) {}

    public record MuscleAuditItem(
            MuscleId muscle,
            String displayName,
            double directSets,
            double indirectSets,
            double effectiveSets,
            CoverageStatus status,
            boolean priorityTarget,
            List<String> contributingExercises
    ) {}

    public record MovementPatternAuditItem(
            String patternCode,
            String displayName,
            double weeklySets,
            int exerciseCount,
            CoverageStatus status,
            List<String> existingExercises
    ) {}

    public record SkillAuditItem(
            String skillKey,
            String skillName,
            int weeklySkillSets,
            int staticSets,
            int dynamicSets,
            List<WorkoutDayOfWeek> scheduledDays,
            double supportingStrengthSets,
            String bestPrSummary,
            boolean strongPrRecorded,
            boolean strongPrButWeakSupport,
            String assessment
    ) {}

    public record AuditFinding(
            int priorityTier,
            String severity,
            CoverageStatus coverageStatus,
            String title,
            String whatIsInsufficient,
            String whyItMatters,
            String existingCoverage,
            String recommendedAction,
            WorkoutDayOfWeek suggestedDay,
            String suggestedDayLabel
    ) {}

    public record ComprehensiveWorkoutAudit(
            AthleteProfileSnapshot athlete,
            ActiveWorkoutPlanContext planContext,
            ProgramPriorityProfile priorities,
            List<MuscleAuditItem> muscleAudit,
            List<MovementPatternAuditItem> movementPatterns,
            List<SkillAuditItem> skillAudits,
            List<String> recoveryConflicts,
            List<AuditFinding> findings,
            int balanceScore,
            List<String> undertrainedMuscles,
            List<String> overloadedMuscles,
            List<String> adequatelyCoveredMuscles,
            String suggestedActionPrompt
    ) {}

    @Transactional(readOnly = true)
    public AthleteProfileSnapshot loadAthleteProfileSnapshot(Long userId, ActiveWorkoutPlanContext planContext) {
        User user = (userId != null && userRepository != null) ? userRepository.findById(userId).orElse(null) : null;
        ClientProfile profile = (userId != null && clientProfileRepository != null)
                ? clientProfileRepository.findByUserId(userId).orElse(null) : null;

        Double latestWeight = null;
        ZonedDateTime weightMeasuredAt = null;
        Double weightTrendDelta = null;
        if (userId != null && measurementRepository != null) {
            List<ClientBodyMeasurement> measurements = measurementRepository.findByClientIdOrderByMeasuredAtAsc(userId);
            if (measurements != null && !measurements.isEmpty()) {
                ClientBodyMeasurement latest = measurements.get(measurements.size() - 1);
                if (latest.getWeightKg() != null) {
                    latestWeight = latest.getWeightKg().doubleValue();
                    weightMeasuredAt = latest.getMeasuredAt();
                }
                if (measurements.size() >= 2) {
                    ClientBodyMeasurement prev = measurements.get(measurements.size() - 2);
                    if (latest.getWeightKg() != null && prev.getWeightKg() != null) {
                        weightTrendDelta = round1(latest.getWeightKg().doubleValue() - prev.getWeightKg().doubleValue());
                    }
                }
            } else {
                Optional<ClientBodyMeasurement> single = measurementRepository.findFirstByClientIdOrderByMeasuredAtDesc(userId);
                if (single.isPresent() && single.get().getWeightKg() != null) {
                    latestWeight = single.get().getWeightKg().doubleValue();
                    weightMeasuredAt = single.get().getMeasuredAt();
                }
            }
        }

        Double heightCm = (profile != null && profile.getHeightCm() != null)
                ? profile.getHeightCm().doubleValue() : null;

        List<PersonalRecord> prs = (userId != null && personalRecordRepository != null)
                ? personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId) : List.of();
        if (prs == null) {
            prs = List.of();
        }
        List<KeyPrSnapshot> keyPrs = extractKeyPrSnapshots(prs);

        List<WorkoutSession> completedSessions = (userId != null && workoutSessionRepository != null)
                ? workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(userId, WorkoutSessionStatus.COMPLETED)
                : List.of();
        if (completedSessions == null) {
            completedSessions = List.of();
        }
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        int completedLast30 = (int) completedSessions.stream()
                .filter(s -> s.getWorkoutDate() != null && !s.getWorkoutDate().isBefore(thirtyDaysAgo))
                .count();
        LocalDate lastCompletedDate = completedSessions.stream()
                .map(WorkoutSession::getWorkoutDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        int incompleteCount = 0;
        if (userId != null && workoutSessionRepository != null) {
            long totalCount = workoutSessionRepository.countByUserId(userId);
            if (totalCount > completedSessions.size()) {
                incompleteCount = (int) (totalCount - completedSessions.size());
            } else {
                List<WorkoutSession> allSessions = workoutSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
                if (allSessions != null) {
                    incompleteCount = (int) allSessions.stream()
                            .filter(s -> s.getStatus() != WorkoutSessionStatus.COMPLETED)
                            .count();
                }
            }
        }

        Set<String> equipment = new LinkedHashSet<>();
        if (planContext != null) {
            for (DayContext dc : planContext.days()) {
                for (ExerciseContext ex : dc.exercises()) {
                    if (ex.exerciseType() != null && !ex.exerciseType().isBlank()) {
                        // Equipment tracked separately if needed; keep clean
                    }
                }
            }
        }

        List<String> missingNotes = new ArrayList<>();
        if (latestWeight == null) missingNotes.add("Body weight not recorded");
        if (heightCm == null) missingNotes.add("Height not recorded");
        if (profile == null || isBlank(profile.getExperienceLevel())) missingNotes.add("Experience level not specified");
        if (profile == null || isBlank(profile.getPrimaryGoals())) missingNotes.add("Primary training goals not set");
        if (prs.isEmpty()) missingNotes.add("No personal records (PRs) logged yet");
        if (completedSessions.isEmpty()) missingNotes.add("No completed workout sessions logged yet");

        return new AthleteProfileSnapshot(
                userId,
                user != null ? user.getFullName() : null,
                user != null && user.getGender() != null ? user.getGender().name() : null,
                latestWeight,
                weightMeasuredAt,
                weightTrendDelta,
                heightCm,
                profile != null ? profile.getExperienceLevel() : null,
                profile != null ? profile.getTrainingExperience() : null,
                profile != null ? profile.getPrimaryGoals() : null,
                profile != null ? profile.getTrainingInterests() : null,
                profile != null ? profile.getPreferredFrequency() : null,
                List.copyOf(keyPrs),
                List.copyOf(prs),
                completedSessions.size(),
                completedLast30,
                incompleteCount,
                lastCompletedDate,
                Set.copyOf(equipment),
                List.copyOf(missingNotes)
        );
    }

    @Transactional(readOnly = true)
    public ComprehensiveWorkoutAudit auditActiveWorkoutPlan(Long userId) {
        ActiveWorkoutPlanContext planCtx = workoutPlanContextService.getActivePlanContext(userId).orElse(null);
        AthleteProfileSnapshot athlete = loadAthleteProfileSnapshot(userId, planCtx);
        if (planCtx == null) {
            return null;
        }

        ProgramPriorityProfile priorities = inferProgramPriorities(planCtx, athlete);

        // 1. Aggregate direct & indirect weekly sets per muscle group across ALL 7 weekdays
        Map<MuscleId, Double> directSetsMap = new EnumMap<>(MuscleId.class);
        Map<MuscleId, Double> indirectSetsMap = new EnumMap<>(MuscleId.class);
        Map<MuscleId, Set<String>> contributorsMap = new EnumMap<>(MuscleId.class);

        for (DayContext day : planCtx.days()) {
            if (day.restDay()) continue;
            for (ExerciseContext ex : day.exercises()) {
                double sets = ex.sets() != null && ex.sets() > 0 ? ex.sets() : 3.0;
                for (MuscleId p : ex.primaryMuscleIds()) {
                    directSetsMap.merge(p, sets, Double::sum);
                    contributorsMap.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(ex.exerciseName());
                }
                for (MuscleId s : ex.secondaryMuscleIds()) {
                    indirectSetsMap.merge(s, sets, Double::sum);
                    contributorsMap.computeIfAbsent(s, k -> new LinkedHashSet<>()).add(ex.exerciseName() + " (secondary)");
                }
            }
        }

        List<MuscleAuditItem> muscleAudit = evaluateMuscleGroups(
                priorities, directSetsMap, indirectSetsMap, contributorsMap);
        List<MovementPatternAuditItem> movementPatterns = evaluateMovementPatterns(planCtx, priorities);
        List<SkillAuditItem> skillAudits = evaluateSkillsAgainstPrs(planCtx, athlete, priorities, movementPatterns, muscleAudit);
        List<String> recoveryConflicts = evaluateRecoveryAndSpacing(planCtx, priorities);

        // 2. Build prioritized findings (Tier 1 -> Tier 4)
        List<AuditFinding> findings = new ArrayList<>();
        int score = 92;

        // Tier 1: Primary skill & goal alignment / PR vs supporting training checks
        for (SkillAuditItem skill : skillAudits) {
            boolean isPrimary = priorities.primarySkills().contains(skill.skillName())
                    || skill.strongPrButWeakSupport()
                    || skill.weeklySkillSets() >= 14;
            if (!isPrimary) {
                continue;
            }
            if (skill.strongPrButWeakSupport()) {
                score -= 10;
                WorkoutDayOfWeek targetDay = pickBestDayForFocus(planCtx,
                        "Planche".equals(skill.skillName()) ? "push" : "pull");
                findings.add(new AuditFinding(
                        1,
                        "WARNING",
                        CoverageStatus.UNDERDEVELOPED,
                        skill.skillName() + ": Strong PR but Supporting Strength Gap",
                        skill.assessment(),
                        "Your " + skill.bestPrSummary() + " shows strong static hold capacity; continuing to add only static holds raises tendon fatigue without addressing the supporting bent-arm / dynamic strength bottleneck.",
                        formatScheduledDaysAndSets(skill),
                        "Planche".equals(skill.skillName())
                                ? "Prioritize supporting dynamic pressing (e.g., Pseudo Planche Push-Ups, Weighted Dips, or HSPU) and scapular/core stability rather than adding more static Planche holds."
                                : "Prioritize supporting dynamic pulling (e.g., Weighted Pull-Ups, Front Lever Raises, or Rows) and scapular depression/core anti-extension rather than more static holds.",
                        targetDay,
                        formatDayLabel(targetDay)
                ));
            } else if (skill.weeklySkillSets() == 0) {
                score -= 14;
                WorkoutDayOfWeek targetDay = pickBestDayForFocus(planCtx,
                        "Planche".equals(skill.skillName()) ? "push" : "pull");
                findings.add(new AuditFinding(
                        1,
                        "WARNING",
                        CoverageStatus.MISSING,
                        skill.skillName() + ": Missing Skill-Specific Volume",
                        "No dedicated " + skill.skillName() + " progressions are scheduled in your active week despite it being a primary goal/plan focus.",
                        "Specialized skill adaptations (straight-arm connective tissue and joint-angle strength) require specific exposure 2–3 times per week.",
                        "0 weekly sets currently assigned",
                        "Integrate 1–2 progressive " + skill.skillName() + " variations at the start of a fresh session.",
                        targetDay,
                        formatDayLabel(targetDay)
                ));
            } else if (skill.weeklySkillSets() >= 15 || skill.scheduledDays().size() >= 4) {
                score -= 10;
                findings.add(new AuditFinding(
                        1,
                        "WARNING",
                        CoverageStatus.OVERLOADED,
                        skill.skillName() + ": High Weekly Skill Volume / Frequency",
                        skill.assessment(),
                        "Straight-arm tendons recover slower than muscle tissue; excessive weekly skill sets increase anterior shoulder or distal bicep tendon strain.",
                        formatScheduledDaysAndSets(skill),
                        "Consolidate into 2–3 well-spaced sessions (~8–12 quality sets/week) with clear Light vs. Hard intensity separation.",
                        null,
                        null
                ));
            } else {
                findings.add(new AuditFinding(
                        1,
                        "INFO",
                        CoverageStatus.ADEQUATELY_COVERED,
                        skill.skillName() + ": Well-Represented Priority Volume",
                        skill.assessment(),
                        "Matches your stated priority without excessive straight-arm overload.",
                        formatScheduledDaysAndSets(skill),
                        "Keep progressing hold quality or leverage gradually before adding extra sets.",
                        null,
                        null
                ));
            }
        }

        // Tier 1 / 2: Recovery & Rest Day check
        if (planCtx.restDaysCount() == 0) {
            score -= 16;
            findings.add(new AuditFinding(
                    1,
                    "WARNING",
                    CoverageStatus.OVERLOADED,
                    "Recovery: No Scheduled Rest Days",
                    "All 7 days of your active plan schedule training (0 rest days).",
                    "Tendons and the central nervous system require recovery days to consolidate strength gains, especially on demanding skill blocks.",
                    "7 training days / 0 rest days",
                    "Convert at least 1–2 days (ideally after your heaviest skill sessions) into Rest or active mobility days.",
                    null,
                    null
            ));
        }
        for (String conflict : recoveryConflicts) {
            score -= 6;
            findings.add(new AuditFinding(
                    2,
                    "WARNING",
                    CoverageStatus.OVERLOADED,
                    "Recovery Spacing: Consecutive-Day Overlap",
                    conflict,
                    "Back-to-back high-load work on the same prime movers or straight-arm joints limits quality and recovery.",
                    "Adjacent training days overlap",
                    "Separate demanding same-pattern sessions with a rest day or lower-body/light session in between.",
                    null,
                    null
            ));
        }

        // Tier 2: Movement pattern & supporting muscle coverage (distinguishing MISSING vs UNDERDEVELOPED vs ADEQUATELY_COVERED)
        for (MovementPatternAuditItem pattern : movementPatterns) {
            if (pattern.status() == CoverageStatus.MISSING || pattern.status() == CoverageStatus.UNDERDEVELOPED) {
                boolean isLegPattern = pattern.patternCode().contains("LEGS") || "CALVES".equals(pattern.patternCode());
                int tier = (isLegPattern && priorities.emphasizesUpperBodySpecialization()) ? 3 : 2;
                score -= (tier == 2 ? 7 : 4);
                WorkoutDayOfWeek suggested = pickBestDayForPattern(planCtx, pattern.patternCode());
                String why = (isLegPattern && priorities.emphasizesUpperBodySpecialization())
                        ? "Even in an upper-skill specialization block (" + priorities.summaryLabel()
                                + "), minimal maintenance work preserves lower-body resilience without hurting recovery."
                        : "Supports balanced joint health, strength transfer, and long-term progression.";
                String rec = (isLegPattern && priorities.emphasizesUpperBodySpecialization())
                        ? "Keep maintenance low-fatigue (e.g., 1–2 concise unilateral/hinge exercises) so it does not compromise your primary skill recovery."
                        : "Add 1–2 targeted exercises covering " + pattern.displayName() + " with controlled weekly volume.";
                findings.add(new AuditFinding(
                        tier,
                        "WARNING",
                        pattern.status(),
                        pattern.displayName() + ": " + formatStatusLabel(pattern.status()),
                        String.format(Locale.US, "%s currently has %.1f weekly sets (%d exercise(s)).",
                                pattern.displayName(), pattern.weeklySets(), pattern.exerciseCount()),
                        why,
                        pattern.existingExercises().isEmpty()
                                ? "None scheduled"
                                : String.join(", ", pattern.existingExercises()),
                        rec,
                        suggested,
                        formatDayLabel(suggested)
                ));
            }
        }

        // Tier 2 / 3: Muscle group gaps (using effective weekly sets = direct + 0.5*indirect)
        List<String> undertrained = new ArrayList<>();
        List<String> overloaded = new ArrayList<>();
        List<String> adequate = new ArrayList<>();

        for (MuscleAuditItem m : muscleAudit) {
            if (m.status() == CoverageStatus.MISSING || m.status() == CoverageStatus.UNDERDEVELOPED) {
                undertrained.add(m.displayName());
                boolean isLower = m.muscle().getRegion() == MuscleRegion.LOWER_BODY;
                int tier = (isLower && priorities.emphasizesUpperBodySpecialization()) ? 3 : 2;
                // Avoid duplicating findings if already covered by a movement pattern finding for lower body
                boolean alreadyFlaggedByPattern = isLower && findings.stream()
                        .anyMatch(f -> f.title().contains("Leg") || f.title().contains("Hinge") || f.title().contains("Calf"));
                if (!alreadyFlaggedByPattern || m.muscle() == MuscleId.HAMSTRINGS) {
                    if (m.muscle() == MuscleId.HAMSTRINGS || m.priorityTarget()) {
                        score -= 5;
                    }
                    WorkoutDayOfWeek suggested = pickBestDayForMuscle(planCtx, m.muscle());
                    findings.add(new AuditFinding(
                            tier,
                            "WARNING",
                            m.status(),
                            m.displayName() + ": " + formatStatusLabel(m.status()),
                            String.format(Locale.US, "%.1f effective weekly sets (%.1f direct, %.1f secondary).",
                                    m.effectiveSets(), m.directSets(), m.indirectSets()),
                            m.priorityTarget()
                                    ? "Directly supports your primary training focus and joint stability."
                                    : (priorities.emphasizesUpperBodySpecialization() && isLower
                                        ? "Needs light maintenance volume rather than high hypertrophy volume during your specialized skill focus."
                                        : "Balances overall muscular development and injury resilience."),
                            m.contributingExercises().isEmpty()
                                    ? "No direct or indirect exercises"
                                    : String.join(", ", m.contributingExercises()),
                            priorities.emphasizesUpperBodySpecialization() && isLower
                                    ? "Add 1 concise maintenance movement (3–4 sets/week) without adding excessive systemic fatigue."
                                    : "Add 1–2 compound or targeted sets for " + m.displayName() + ".",
                            suggested,
                            formatDayLabel(suggested)
                    ));
                }
            } else if (m.status() == CoverageStatus.OVERLOADED) {
                overloaded.add(m.displayName());
                score -= 7;
                findings.add(new AuditFinding(
                        2,
                        "WARNING",
                        CoverageStatus.OVERLOADED,
                        m.displayName() + ": High Weekly Load",
                        String.format(Locale.US, "%.1f effective weekly sets (%.1f direct, %.1f secondary).",
                                m.effectiveSets(), m.directSets(), m.indirectSets()),
                        "Excessive weekly load can limit recovery and carry over fatigue into skill sessions.",
                        String.join(", ", m.contributingExercises()),
                        "Trim redundant sets or spread sessions further apart.",
                        null,
                        null
                ));
            } else {
                adequate.add(m.displayName());
            }
        }

        // Tier 4: Compound-covered highlights so caller/UI sees adequately trained muscles were NOT mislabeled missing
        List<String> compoundCoveredHighlights = muscleAudit.stream()
                .filter(m -> m.status() == CoverageStatus.ADEQUATELY_COVERED && !m.contributingExercises().isEmpty())
                .filter(m -> m.muscle() == MuscleId.TRICEPS || m.muscle() == MuscleId.BICEPS
                        || m.muscle() == MuscleId.REAR_DELTOIDS || m.muscle() == MuscleId.FRONT_DELTOIDS
                        || m.muscle() == MuscleId.LATISSIMUS_DORSI)
                .limit(3)
                .map(m -> m.displayName() + " (" + round1(m.effectiveSets()) + " eff. sets via "
                        + String.join(", ", m.contributingExercises().stream().limit(2).toList()) + ")")
                .toList();
        if (!compoundCoveredHighlights.isEmpty()) {
            findings.add(new AuditFinding(
                    4,
                    "INFO",
                    CoverageStatus.ADEQUATELY_COVERED,
                    "Adequately Covered via Compound Movements",
                    String.join("; ", compoundCoveredHighlights),
                    "Compound movements already supply productive effective stimulus, so isolated exercises are not mandatory.",
                    String.join("; ", compoundCoveredHighlights),
                    "No extra isolation volume needed unless targeting a specific weak link.",
                    null,
                    null
            ));
        }

        findings.sort(Comparator.comparingInt(AuditFinding::priorityTier)
                .thenComparing(f -> "WARNING".equals(f.severity()) ? 0 : 1));

        int clampedScore = Math.max(35, Math.min(100, score));
        List<String> dedupUndertrained = undertrained.stream().distinct().limit(6).toList();
        List<String> dedupOverloaded = overloaded.stream().distinct().limit(5).toList();

        String suggestedFollowUp = !dedupUndertrained.isEmpty()
                ? "Recommend exercises for " + String.join(" and ", dedupUndertrained.subList(0, Math.min(2, dedupUndertrained.size())))
                : "What exercises should I add to improve my routine?";

        return new ComprehensiveWorkoutAudit(
                athlete,
                planCtx,
                priorities,
                List.copyOf(muscleAudit),
                List.copyOf(movementPatterns),
                List.copyOf(skillAudits),
                List.copyOf(recoveryConflicts),
                List.copyOf(findings),
                clampedScore,
                dedupUndertrained,
                dedupOverloaded,
                List.copyOf(adequate),
                suggestedFollowUp
        );
    }

    public WorkoutConsultationCardDto toConsultationCard(ComprehensiveWorkoutAudit audit) {
        AthleteProfileSnapshot athlete = audit.athlete();
        AthleteSnapshotDto athleteDto = AthleteSnapshotDto.builder()
                .weightKg(athlete.weightKg())
                .heightCm(athlete.heightCm())
                .experienceLevel(athlete.experienceLevel())
                .primaryGoals(athlete.primaryGoals())
                .preferredFrequency(athlete.preferredFrequency())
                .completedSessionsCount(athlete.completedSessionsCount())
                .incompleteSessionsCount(athlete.incompleteSessionsCount())
                .keyPrs(athlete.keyPrs().stream().map(KeyPrSnapshot::formattedSummary).toList())
                .missingData(athlete.missingDataNotes())
                .build();

        List<ConsultationFindingDto> findingDtos = audit.findings().stream()
                .map(f -> ConsultationFindingDto.builder()
                        .severity(f.severity())
                        .title(f.title())
                        .detail(f.whatIsInsufficient() + " " + f.recommendedAction())
                        .priorityTier(f.priorityTier())
                        .coverageStatus(f.coverageStatus() != null ? f.coverageStatus().name() : null)
                        .recommendedDay(f.suggestedDay())
                        .build())
                .toList();

        List<MuscleCoverageDto> muscleDtos = audit.muscleAudit().stream()
                .map(m -> MuscleCoverageDto.builder()
                        .muscleName(m.displayName())
                        .directSets(round1(m.directSets()))
                        .indirectSets(round1(m.indirectSets()))
                        .effectiveSets(round1(m.effectiveSets()))
                        .status(m.status().name())
                        .contributingExercises(m.contributingExercises())
                        .build())
                .toList();

        List<MovementPatternAuditDto> patternDtos = audit.movementPatterns().stream()
                .map(p -> MovementPatternAuditDto.builder()
                        .patternName(p.displayName())
                        .weeklySets(round1(p.weeklySets()))
                        .exerciseCount(p.exerciseCount())
                        .status(p.status().name())
                        .existingExercises(p.existingExercises())
                        .build())
                .toList();

        return WorkoutConsultationCardDto.builder()
                .focusArea(audit.planContext().planName())
                .balanceScore(audit.balanceScore())
                .findings(findingDtos)
                .undertrainedMuscles(audit.undertrainedMuscles())
                .overloadedMuscles(audit.overloadedMuscles())
                .adequatelyCoveredMuscles(audit.adequatelyCoveredMuscles())
                .programPriorities(audit.priorities().primarySkills())
                .specializedProgram(audit.priorities().specializedProgram())
                .athleteSnapshot(athleteDto)
                .muscleCoverage(muscleDtos)
                .movementPatterns(patternDtos)
                .suggestedActionPrompt(audit.suggestedActionPrompt())
                .build();
    }

    public String formatComprehensiveAuditMarkdown(ComprehensiveWorkoutAudit audit, boolean isVn) {
        AthleteProfileSnapshot a = audit.athlete();
        ActiveWorkoutPlanContext plan = audit.planContext();
        StringBuilder sb = new StringBuilder();

        sb.append("### 📊 ").append(isVn ? "Đánh Giá Toàn Diện Lịch Tập: " : "Workout Plan Audit: ")
                .append(plan.planName()).append("\n\n");

        if (audit.priorities().specializedProgram()) {
            sb.append("**").append(isVn ? "Định hướng chương trình" : "Program Focus").append("**: ")
                    .append(audit.priorities().summaryLabel())
                    .append(" _(specialized program — maintenance volume is evaluated without compromising your primary skill goals)_.\n\n");
        } else {
            sb.append("**").append(isVn ? "Định hướng chương trình" : "Program Focus").append("**: ")
                    .append(audit.priorities().summaryLabel()).append("\n\n");
        }

        // Athlete profile & PR snapshot (never fabricating missing values)
        sb.append("**").append(isVn ? "Hồ sơ & Thành tích (DB)" : "Athlete Snapshot").append("**\n");
        sb.append("- **").append(isVn ? "Cân nặng / Chiều cao" : "Body Metrics").append("**: ")
                .append(a.weightKg() != null ? a.weightKg() + " kg" : "Weight not recorded")
                .append(" · ")
                .append(a.heightCm() != null ? a.heightCm() + " cm" : "Height not recorded");
        if (a.weightTrendDeltaKg() != null && Math.abs(a.weightTrendDeltaKg()) >= 0.1) {
            sb.append(String.format(Locale.US, " (latest change: %+.1f kg)", a.weightTrendDeltaKg()));
        }
        sb.append("\n");

        sb.append("- **").append(isVn ? "Trình độ & Mục tiêu" : "Level & Goals").append("**: ")
                .append(a.experienceLevel() != null ? a.experienceLevel() : "Level not specified")
                .append(" · ")
                .append(a.primaryGoals() != null ? a.primaryGoals() : "Goals not specified")
                .append("\n");

        sb.append("- **").append(isVn ? "Lịch sử buổi tập" : "Training History").append("**: ")
                .append(a.completedSessionsCount()).append(" completed session(s)")
                .append(a.completedLast30DaysCount() > 0 ? " (" + a.completedLast30DaysCount() + " in last 30d)" : "");
        if (a.incompleteSessionsCount() > 0) {
            sb.append(" · ").append(a.incompleteSessionsCount())
                    .append(" incomplete/scheduled session(s) excluded from achievements");
        }
        sb.append("\n");

        sb.append("- **").append(isVn ? "Tần suất lịch tập" : "Schedule Frequency").append("**: ")
                .append(plan.trainingDaysCount()).append(" training day(s) / ")
                .append(plan.restDaysCount()).append(" rest day(s) per week\n");

        if (a.keyPrs().isEmpty()) {
            sb.append("- **Personal Records (PRs)**: None recorded yet.\n");
        } else {
            String prList = a.keyPrs().stream()
                    .limit(6)
                    .map(KeyPrSnapshot::formattedSummary)
                    .collect(Collectors.joining(" · "));
            sb.append("- **Personal Records (PRs)**: ").append(prList).append("\n");
        }

        if (!a.missingDataNotes().isEmpty()) {
            sb.append("- **Data Gaps Acknowledged**: ").append(String.join("; ", a.missingDataNotes())).append("\n");
        }

        sb.append("\n**Prioritized Findings** (Score: ").append(audit.balanceScore()).append("/100)\n\n");

        int idx = 1;
        for (AuditFinding f : audit.findings()) {
            String icon = "WARNING".equals(f.severity()) ? "⚠️" : "✅";
            sb.append(idx++).append(". ").append(icon).append(" **").append(f.title()).append("** [")
                    .append(formatStatusLabel(f.coverageStatus())).append("]\n");
            sb.append("   - **Finding**: ").append(f.whatIsInsufficient()).append("\n");
            sb.append("   - **Why it matters**: ").append(f.whyItMatters()).append("\n");
            sb.append("   - **Current coverage**: ").append(f.existingCoverage()).append("\n");
            sb.append("   - **Recommendation**: ").append(f.recommendedAction());
            if (f.suggestedDayLabel() != null && !f.suggestedDayLabel().isBlank()) {
                sb.append(" (Best fit: **").append(f.suggestedDayLabel()).append("**)");
            }
            sb.append("\n");
        }

        sb.append("\n_No changes were made to your workout plan._ ")
                .append(isVn
                        ? "Bạn có muốn tôi gợi ý bài tập cụ thể và đề xuất ngày phù hợp để thêm vào lịch không?"
                        : "Would you like me to suggest specific exercises and where to integrate them?");
        return sb.toString().trim();
    }

    public String formatAthleteSnapshotForSystemPrompt(AthleteProfileSnapshot a) {
        if (a == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Authoritative Athlete Metrics & PRs (Use ONLY these values; NEVER fabricate missing numbers):\n");
        sb.append("- Body Weight: ").append(a.weightKg() != null ? a.weightKg() + " kg" : "Not recorded").append("\n");
        sb.append("- Height: ").append(a.heightCm() != null ? a.heightCm() + " cm" : "Not recorded").append("\n");
        sb.append("- Experience Level: ").append(a.experienceLevel() != null ? a.experienceLevel() : "Not recorded").append("\n");
        sb.append("- Primary Goals: ").append(a.primaryGoals() != null ? a.primaryGoals() : "Not recorded").append("\n");
        sb.append("- Completed Sessions: ").append(a.completedSessionsCount())
                .append(" (Incomplete/Scheduled excluded: ").append(a.incompleteSessionsCount()).append(")\n");
        if (a.keyPrs().isEmpty()) {
            sb.append("- Personal Records: None recorded\n");
        } else {
            sb.append("- Personal Records: ")
                    .append(a.keyPrs().stream().limit(8).map(KeyPrSnapshot::formattedSummary).collect(Collectors.joining("; ")))
                    .append("\n");
        }
        return sb.toString();
    }

    private ProgramPriorityProfile inferProgramPriorities(
            ActiveWorkoutPlanContext planCtx,
            AthleteProfileSnapshot athlete
    ) {
        StringBuilder corpus = new StringBuilder();
        if (planCtx.planName() != null) corpus.append(planCtx.planName()).append(" ");
        if (planCtx.description() != null) corpus.append(planCtx.description()).append(" ");
        if (athlete.primaryGoals() != null) corpus.append(athlete.primaryGoals()).append(" ");
        if (athlete.trainingInterests() != null) corpus.append(athlete.trainingInterests()).append(" ");
        String lower = corpus.toString().toLowerCase(Locale.US);

        List<String> skills = new ArrayList<>();
        if (lower.contains("planche")) skills.add("Planche");
        if (lower.contains("front lever")) skills.add("Front Lever");
        if (lower.contains("back lever")) skills.add("Back Lever");
        if (lower.contains("handstand") || lower.contains("hspu")) skills.add("Handstand / HSPU");
        if (lower.contains("muscle-up") || lower.contains("muscle up")) skills.add("Muscle-Up");
        if (lower.contains("human flag")) skills.add("Human Flag");
        if (lower.contains("l-sit")) skills.add("L-Sit");

        List<String> modalities = new ArrayList<>();
        if (lower.contains("strength")) modalities.add("Strength");
        if (lower.contains("hypertrophy") || lower.contains("muscle")) modalities.add("Hypertrophy");
        if (lower.contains("endurance")) modalities.add("Endurance");
        if (lower.contains("general") || (skills.isEmpty() && modalities.isEmpty())) {
            modalities.add("General Calisthenics");
        }

        boolean explicitFocusCue = lower.contains("focus") || lower.contains("special") || lower.contains("mastery");
        boolean specialized = !skills.isEmpty() && (explicitFocusCue || skills.size() <= 2);
        boolean upperSpec = specialized && skills.stream()
                .allMatch(s -> Set.of("Planche", "Front Lever", "Back Lever", "Handstand / HSPU", "Muscle-Up", "Human Flag", "L-Sit").contains(s));

        List<String> labelParts = new ArrayList<>();
        labelParts.addAll(skills);
        if (!specialized || modalities.stream().anyMatch(m -> !"General Calisthenics".equals(m))) {
            labelParts.addAll(modalities);
        }
        String summaryLabel = labelParts.isEmpty() ? "General Calisthenics" : String.join(" + ", labelParts);

        return new ProgramPriorityProfile(
                specialized,
                List.copyOf(skills),
                List.copyOf(modalities),
                upperSpec,
                summaryLabel
        );
    }

    private List<MuscleAuditItem> evaluateMuscleGroups(
            ProgramPriorityProfile priorities,
            Map<MuscleId, Double> directMap,
            Map<MuscleId, Double> indirectMap,
            Map<MuscleId, Set<String>> contributorsMap
    ) {
        Set<MuscleId> priorityMuscles = EnumSet.noneOf(MuscleId.class);
        for (String skill : priorities.primarySkills()) {
            switch (skill) {
                case "Planche" -> priorityMuscles.addAll(List.of(
                        MuscleId.FRONT_DELTOIDS, MuscleId.CHEST, MuscleId.TRICEPS, MuscleId.RECTUS_ABDOMINIS));
                case "Front Lever" -> priorityMuscles.addAll(List.of(
                        MuscleId.LATISSIMUS_DORSI, MuscleId.RHOMBOIDS, MuscleId.REAR_DELTOIDS, MuscleId.RECTUS_ABDOMINIS));
                case "Handstand / HSPU" -> priorityMuscles.addAll(List.of(
                        MuscleId.FRONT_DELTOIDS, MuscleId.TRICEPS));
                case "Muscle-Up" -> priorityMuscles.addAll(List.of(
                        MuscleId.LATISSIMUS_DORSI, MuscleId.CHEST, MuscleId.TRICEPS));
                default -> {}
            }
        }

        List<MuscleAuditItem> results = new ArrayList<>();
        for (MuscleId m : AUDITED_MUSCLES) {
            double direct = directMap.getOrDefault(m, 0.0);
            double indirect = indirectMap.getOrDefault(m, 0.0);
            double effective = direct + (indirect * 0.5);
            boolean isPriority = priorityMuscles.contains(m);
            boolean isLower = m.getRegion() == MuscleRegion.LOWER_BODY;

            double minAdequate;
            if (priorities.emphasizesUpperBodySpecialization() && isLower) {
                minAdequate = 3.0; // maintenance expectation during specialized upper skill block
            } else if (m == MuscleId.LATERAL_DELTOIDS || m == MuscleId.FOREARMS || m == MuscleId.CALVES) {
                minAdequate = 2.5;
            } else if (isPriority) {
                minAdequate = 5.0;
            } else {
                minAdequate = 4.0;
            }

            CoverageStatus status;
            if (effective <= 0.0) {
                status = CoverageStatus.MISSING;
            } else if (effective < minAdequate) {
                status = CoverageStatus.UNDERDEVELOPED;
            } else if (effective > HIGH_WEEKLY_MUSCLE_SETS_THRESHOLD) {
                status = CoverageStatus.OVERLOADED;
            } else {
                status = CoverageStatus.ADEQUATELY_COVERED;
            }

            List<String> contributors = contributorsMap.getOrDefault(m, Set.of()).stream().limit(5).toList();
            results.add(new MuscleAuditItem(
                    m, m.getDisplayName(), round1(direct), round1(indirect), round1(effective),
                    status, isPriority, contributors));
        }
        return results;
    }

    private List<MovementPatternAuditItem> evaluateMovementPatterns(
            ActiveWorkoutPlanContext planCtx,
            ProgramPriorityProfile priorities
    ) {
        Map<String, Double> patternSets = new LinkedHashMap<>();
        Map<String, Integer> patternExCount = new LinkedHashMap<>();
        Map<String, Set<String>> patternExercises = new LinkedHashMap<>();

        List<String[]> defs = List.of(
                new String[]{"HORIZONTAL_PUSH", "Horizontal Push"},
                new String[]{"VERTICAL_PUSH", "Vertical Push"},
                new String[]{"HORIZONTAL_PULL", "Horizontal Pull"},
                new String[]{"VERTICAL_PULL", "Vertical Pull"},
                new String[]{"KNEE_DOMINANT_LEGS", "Knee-Dominant Legs (Squat/Lunge)"},
                new String[]{"HIP_HINGE_LEGS", "Hip-Hinge / Posterior Chain (Hamstrings/Glutes)"},
                new String[]{"CALVES", "Calves"},
                new String[]{"CORE", "Core / Anti-Extension"}
        );
        for (String[] def : defs) {
            patternSets.put(def[0], 0.0);
            patternExCount.put(def[0], 0);
            patternExercises.put(def[0], new LinkedHashSet<>());
        }

        for (DayContext day : planCtx.days()) {
            if (day.restDay()) continue;
            for (ExerciseContext ex : day.exercises()) {
                double sets = ex.sets() != null && ex.sets() > 0 ? ex.sets() : 3.0;
                String pattern = classifyMovementPattern(ex);
                if (pattern != null && patternSets.containsKey(pattern)) {
                    patternSets.merge(pattern, sets, Double::sum);
                    patternExCount.merge(pattern, 1, Integer::sum);
                    patternExercises.get(pattern).add(ex.exerciseName());
                }
                // Secondary core attribution for demanding holds (Planche / Front Lever / L-Sit)
                if (!"CORE".equals(pattern) && ex.secondaryMuscleIds().contains(MuscleId.RECTUS_ABDOMINIS)) {
                    patternSets.merge("CORE", sets * 0.5, Double::sum);
                    patternExercises.get("CORE").add(ex.exerciseName() + " (secondary)");
                }
            }
        }

        List<MovementPatternAuditItem> items = new ArrayList<>();
        for (String[] def : defs) {
            String code = def[0];
            String label = def[1];
            double sets = patternSets.get(code);
            int count = patternExCount.get(code);
            boolean isLeg = code.contains("LEGS") || "CALVES".equals(code);
            double minSets = (isLeg && priorities.emphasizesUpperBodySpecialization()) ? 2.0 : 3.0;
            CoverageStatus status;
            if (sets <= 0.0) {
                status = CoverageStatus.MISSING;
            } else if (sets < minSets) {
                status = CoverageStatus.UNDERDEVELOPED;
            } else if (sets > 20.0) {
                status = CoverageStatus.OVERLOADED;
            } else {
                status = CoverageStatus.ADEQUATELY_COVERED;
            }
            items.add(new MovementPatternAuditItem(
                    code, label, round1(sets), count, status,
                    patternExercises.get(code).stream().limit(5).toList()));
        }
        return items;
    }

    private List<SkillAuditItem> evaluateSkillsAgainstPrs(
            ActiveWorkoutPlanContext planCtx,
            AthleteProfileSnapshot athlete,
            ProgramPriorityProfile priorities,
            List<MovementPatternAuditItem> patterns,
            List<MuscleAuditItem> muscles
    ) {
        List<SkillAuditItem> audits = new ArrayList<>();
        Map<String, MovementPatternAuditItem> patternByCode = patterns.stream()
                .collect(Collectors.toMap(MovementPatternAuditItem::patternCode, p -> p));
        Map<MuscleId, MuscleAuditItem> muscleById = muscles.stream()
                .collect(Collectors.toMap(MuscleAuditItem::muscle, m -> m));

        Set<String> skillsToAudit = new LinkedHashSet<>(priorities.primarySkills());
        for (DayContext d : planCtx.days()) {
            for (ExerciseContext ex : d.exercises()) {
                String n = ex.exerciseName().toLowerCase(Locale.US);
                if (n.contains("planche")) skillsToAudit.add("Planche");
                if (n.contains("front lever")) skillsToAudit.add("Front Lever");
                if (n.contains("handstand") || n.contains("hspu")) skillsToAudit.add("Handstand / HSPU");
                if (n.contains("muscle-up") || n.contains("muscle up")) skillsToAudit.add("Muscle-Up");
            }
        }

        for (String skill : skillsToAudit) {
            String keyLower = switch (skill) {
                case "Planche" -> "planche";
                case "Front Lever" -> "front lever";
                case "Handstand / HSPU" -> "handstand";
                case "Muscle-Up" -> "muscle";
                default -> skill.toLowerCase(Locale.US);
            };
            int staticSets = 0;
            int dynamicSets = 0;
            Set<WorkoutDayOfWeek> days = new LinkedHashSet<>();

            for (DayContext d : planCtx.days()) {
                if (d.restDay()) continue;
                for (ExerciseContext ex : d.exercises()) {
                    String n = ex.exerciseName().toLowerCase(Locale.US);
                    boolean matches = n.contains(keyLower)
                            || ("Handstand / HSPU".equals(skill) && n.contains("hspu"));
                    if (!matches) continue;
                    days.add(d.dayOfWeek());
                    int sets = ex.sets() != null && ex.sets() > 0 ? ex.sets() : 3;
                    if (isStaticHold(ex)) {
                        staticSets += sets;
                    } else {
                        dynamicSets += sets;
                    }
                }
            }

            int totalSkillSets = staticSets + dynamicSets;
            double supportingSets = 0.0;
            if ("Planche".equals(skill)) {
                double vPush = patternByCode.get("VERTICAL_PUSH").weeklySets();
                double hPush = patternByCode.get("HORIZONTAL_PUSH").weeklySets();
                double dynamicPush = Math.max(0.0, (vPush + hPush) - staticSets);
                supportingSets = dynamicPush;
            } else if ("Front Lever".equals(skill)) {
                double vPull = patternByCode.get("VERTICAL_PULL").weeklySets();
                double hPull = patternByCode.get("HORIZONTAL_PULL").weeklySets();
                double dynamicPull = Math.max(0.0, (vPull + hPull) - staticSets);
                supportingSets = dynamicPull;
            }

            KeyPrSnapshot pr = findBestPrForSkill(athlete.keyPrs(), skill);
            boolean strongPr = isStrongSkillPr(skill, pr);
            boolean weakSupport = false;
            String assessment;

            if ("Planche".equals(skill)) {
                double coreSets = muscleById.get(MuscleId.RECTUS_ABDOMINIS).effectiveSets();
                double chestSets = muscleById.get(MuscleId.CHEST).effectiveSets();
                double tricepsSets = muscleById.get(MuscleId.TRICEPS).effectiveSets();
                boolean insufficientSupporting = supportingSets < 6.0 || coreSets < 4.0 || (chestSets < 4.0 && tricepsSets < 4.0);
                weakSupport = strongPr && staticSets > 0 && insufficientSupporting;
                if (weakSupport) {
                    assessment = String.format(Locale.US,
                            "You have a strong Planche PR (%s) and %d weekly static Planche sets, but supporting bent-arm pressing (%.1f sets), chest/triceps, or core stability (%.1f eff. sets) is underdeveloped.",
                            pr.formattedSummary(), staticSets, supportingSets, coreSets);
                } else {
                    assessment = String.format(Locale.US,
                            "%d weekly Planche sets (%d static, %d dynamic) across %d day(s)%s.",
                            totalSkillSets, staticSets, dynamicSets, days.size(),
                            pr != null ? " alongside PR: " + pr.formattedSummary() : "");
                }
            } else if ("Front Lever".equals(skill)) {
                double coreSets = muscleById.get(MuscleId.RECTUS_ABDOMINIS).effectiveSets();
                double rhomboidSets = muscleById.get(MuscleId.RHOMBOIDS).effectiveSets();
                boolean insufficientSupporting = supportingSets < 6.0 || coreSets < 4.0 || rhomboidSets < 4.0;
                weakSupport = strongPr && staticSets > 0 && insufficientSupporting;
                if (weakSupport) {
                    assessment = String.format(Locale.US,
                            "You have a strong Front Lever PR (%s) and %d static sets, but supporting dynamic pulling (%.1f sets), scapular retraction (%.1f eff. sets), or core work (%.1f eff. sets) is light.",
                            pr.formattedSummary(), staticSets, supportingSets, rhomboidSets, coreSets);
                } else {
                    assessment = String.format(Locale.US,
                            "%d weekly Front Lever sets (%d static, %d dynamic) across %d day(s)%s.",
                            totalSkillSets, staticSets, dynamicSets, days.size(),
                            pr != null ? " alongside PR: " + pr.formattedSummary() : "");
                }
            } else {
                assessment = String.format(Locale.US,
                        "%d weekly sets across %d day(s)%s.",
                        totalSkillSets, days.size(),
                        pr != null ? " (PR: " + pr.formattedSummary() + ")" : "");
            }

            audits.add(new SkillAuditItem(
                    keyLower, skill, totalSkillSets, staticSets, dynamicSets,
                    List.copyOf(days), round1(supportingSets),
                    pr != null ? pr.formattedSummary() : null,
                    strongPr, weakSupport, assessment));
        }
        return audits;
    }

    private List<String> evaluateRecoveryAndSpacing(
            ActiveWorkoutPlanContext planCtx,
            ProgramPriorityProfile priorities
    ) {
        List<String> conflicts = new ArrayList<>();
        List<DayContext> days = planCtx.days();
        for (int i = 0; i < days.size(); i++) {
            DayContext d1 = days.get(i);
            DayContext d2 = days.get((i + 1) % days.size()); // includes Sunday -> Monday wrap
            if (d1.restDay() || d2.restDay() || d1.dayOfWeek() == d2.dayOfWeek()) {
                continue;
            }
            boolean d1HeavyPush = dayEffectiveSetsFor(d1, MuscleId.FRONT_DELTOIDS) >= 5.0
                    || dayEffectiveSetsFor(d1, MuscleId.CHEST) >= 5.0;
            boolean d2HeavyPush = dayEffectiveSetsFor(d2, MuscleId.FRONT_DELTOIDS) >= 5.0
                    || dayEffectiveSetsFor(d2, MuscleId.CHEST) >= 5.0;
            if (d1HeavyPush && d2HeavyPush) {
                conflicts.add("Front Deltoids / heavy pushing scheduled on consecutive days ("
                        + d1.dayLabel() + " & " + d2.dayLabel() + ").");
            }
            boolean d1HeavyPull = dayEffectiveSetsFor(d1, MuscleId.LATISSIMUS_DORSI) >= 5.0;
            boolean d2HeavyPull = dayEffectiveSetsFor(d2, MuscleId.LATISSIMUS_DORSI) >= 5.0;
            if (d1HeavyPull && d2HeavyPull) {
                conflicts.add("Heavy pulling / Lats scheduled on consecutive days ("
                        + d1.dayLabel() + " & " + d2.dayLabel() + ").");
            }
        }
        return conflicts.stream().distinct().toList();
    }

    private static double dayEffectiveSetsFor(DayContext day, MuscleId target) {
        double total = 0.0;
        for (ExerciseContext ex : day.exercises()) {
            double sets = ex.sets() != null && ex.sets() > 0 ? ex.sets() : 3.0;
            if (ex.primaryMuscleIds().contains(target)) total += sets;
            else if (ex.secondaryMuscleIds().contains(target)) total += sets * 0.5;
        }
        return total;
    }

    private List<KeyPrSnapshot> extractKeyPrSnapshots(List<PersonalRecord> prs) {
        Map<String, KeyPrSnapshot> bestByKey = new LinkedHashMap<>();
        for (PersonalRecord pr : prs) {
            String exName = pr.getEffectiveExerciseName();
            String lower = exName.toLowerCase(Locale.US);
            String key;
            String label;
            if (lower.contains("planche")) {
                key = "PLANCHE";
                label = "Planche";
            } else if (lower.contains("front lever")) {
                key = "FRONT_LEVER";
                label = "Front Lever";
            } else if (lower.contains("handstand push") || lower.contains("hspu")) {
                key = "HSPU";
                label = "HSPU";
            } else if (lower.contains("pull") && (pr.getRecordType() == PersonalRecordType.MAX_WEIGHT
                    || lower.contains("weighted"))) {
                key = "WEIGHTED_PULL_UP";
                label = "Weighted Pull-Up";
            } else if (lower.contains("muscle-up") || lower.contains("muscle up")) {
                key = "MUSCLE_UP";
                label = "Muscle-Up";
            } else if (lower.contains("pull-up") || lower.contains("pull up")) {
                key = "PULL_UP";
                label = "Pull-Up";
            } else {
                key = "OTHER_" + exName.toUpperCase(Locale.US);
                label = exName;
            }

            String formatted = formatPrSummary(exName, pr);
            KeyPrSnapshot snap = new KeyPrSnapshot(
                    key, label, exName, pr.getRecordType(),
                    pr.getRecordValue(), pr.getUnit(), pr.getSecondaryValue(),
                    pr.getPreviousRecordValue(), pr.getImprovementPercentage(),
                    pr.getAchievedAt(), formatted
            );
            bestByKey.merge(key, snap, (existing, candidate) ->
                    isBetterPr(candidate, existing) ? candidate : existing);
        }
        return new ArrayList<>(bestByKey.values());
    }

    private static boolean isBetterPr(KeyPrSnapshot candidate, KeyPrSnapshot existing) {
        int candTier = skillVariantRank(candidate.exerciseName());
        int existTier = skillVariantRank(existing.exerciseName());
        if (candTier != existTier) {
            return candTier > existTier;
        }
        BigDecimal cVal = candidate.recordValue() != null ? candidate.recordValue() : BigDecimal.ZERO;
        BigDecimal eVal = existing.recordValue() != null ? existing.recordValue() : BigDecimal.ZERO;
        return cVal.compareTo(eVal) > 0;
    }

    private static int skillVariantRank(String name) {
        if (name == null) return 0;
        String l = name.toLowerCase(Locale.US);
        if (l.contains("full")) return 4;
        if (l.contains("straddle") || l.contains("half")) return 3;
        if (l.contains("adv")) return 2;
        if (l.contains("tuck")) return 1;
        return 3;
    }

    private static KeyPrSnapshot findBestPrForSkill(List<KeyPrSnapshot> keyPrs, String skillName) {
        String targetKey = switch (skillName) {
            case "Planche" -> "PLANCHE";
            case "Front Lever" -> "FRONT_LEVER";
            case "Handstand / HSPU" -> "HSPU";
            case "Muscle-Up" -> "MUSCLE_UP";
            default -> "";
        };
        return keyPrs.stream().filter(k -> targetKey.equals(k.categoryKey())).findFirst().orElse(null);
    }

    private static boolean isStrongSkillPr(String skillName, KeyPrSnapshot pr) {
        if (pr == null || pr.recordValue() == null) {
            return false;
        }
        double val = pr.recordValue().doubleValue();
        String exLower = pr.exerciseName() != null ? pr.exerciseName().toLowerCase(Locale.US) : "";
        if ("Planche".equals(skillName) || "Front Lever".equals(skillName)) {
            if (exLower.contains("tuck") && !exLower.contains("adv")) {
                return val >= 15.0;
            }
            if (exLower.contains("adv")) {
                return val >= 10.0;
            }
            return val >= 5.0;
        }
        return false;
    }

    private static String classifyMovementPattern(ExerciseContext ex) {
        String n = ex.exerciseName() != null ? ex.exerciseName().toLowerCase(Locale.US) : "";
        if (n.contains("calf") || n.contains("calves")) return "CALVES";
        if (n.contains("deadlift") || n.contains("rdl") || n.contains("nordic")
                || n.contains("leg curl") || n.contains("hamstring") || n.contains("hip thrust") || n.contains("glute bridge")) {
            return "HIP_HINGE_LEGS";
        }
        if (n.contains("squat") || n.contains("lunge") || n.contains("step-up") || n.contains("leg extension")
                || ex.category() == ExerciseCategory.LEGS || ex.category() == ExerciseCategory.GLUTES) {
            return "KNEE_DOMINANT_LEGS";
        }
        if (n.contains("l-sit") || n.contains("dragon flag") || n.contains("plank")
                || n.contains("hollow") || n.contains("leg raise") || n.contains("crunch")
                || ex.category() == ExerciseCategory.CORE) {
            return "CORE";
        }
        if (n.contains("hspu") || n.contains("handstand") || n.contains("pike push")
                || n.contains("overhead") || n.contains("dip")) {
            return "VERTICAL_PUSH";
        }
        if (n.contains("planche") || n.contains("push-up") || n.contains("push up") || n.contains("pushup")
                || n.contains("bench") || ex.category() == ExerciseCategory.PUSH || ex.category() == ExerciseCategory.CHEST) {
            return "HORIZONTAL_PUSH";
        }
        if (n.contains("front lever") || n.contains("row") || n.contains("australian") || n.contains("face pull")) {
            return "HORIZONTAL_PULL";
        }
        if (n.contains("pull-up") || n.contains("pull up") || n.contains("chin-up") || n.contains("muscle-up")
                || n.contains("muscle up") || n.contains("pulldown") || ex.category() == ExerciseCategory.PULL
                || ex.category() == ExerciseCategory.BACK) {
            return "VERTICAL_PULL";
        }
        if (ex.primaryMuscleIds().stream().anyMatch(m -> m == MuscleId.CHEST || m == MuscleId.FRONT_DELTOIDS)) {
            return "HORIZONTAL_PUSH";
        }
        if (ex.primaryMuscleIds().stream().anyMatch(m -> m == MuscleId.LATISSIMUS_DORSI || m == MuscleId.RHOMBOIDS)) {
            return "VERTICAL_PULL";
        }
        return null;
    }

    private static boolean isStaticHold(ExerciseContext ex) {
        if (ex.category() == ExerciseCategory.ISOMETRIC || "STATIC_HOLD".equalsIgnoreCase(ex.exerciseType())) {
            return true;
        }
        if (ex.durationSeconds() != null && ex.durationSeconds() > 0 && (ex.reps() == null || ex.reps() == 0)) {
            return true;
        }
        String lower = ex.exerciseName() != null ? ex.exerciseName().toLowerCase(Locale.US) : "";
        return (lower.contains("planche") || lower.contains("front lever") || lower.contains("back lever")
                || lower.contains("l-sit") || lower.equals("handstand"))
                && !lower.contains("raise") && !lower.contains("push") && !lower.contains("pull")
                && !lower.contains("row") && !lower.contains("press");
    }

    private WorkoutDayOfWeek pickBestDayForFocus(ActiveWorkoutPlanContext ctx, String focusKeyword) {
        List<DayContext> matches = workoutPlanContextService.findMatchingDays(ctx, focusKeyword);
        if (!matches.isEmpty()) {
            return matches.stream()
                    .min(Comparator.comparingInt(DayContext::exerciseCount).thenComparingInt(d -> d.dayOfWeek().getOrder()))
                    .map(DayContext::dayOfWeek)
                    .orElse(null);
        }
        return ctx.days().stream()
                .filter(d -> !d.restDay())
                .min(Comparator.comparingInt(DayContext::exerciseCount).thenComparingInt(d -> d.dayOfWeek().getOrder()))
                .map(DayContext::dayOfWeek)
                .orElse(null);
    }

    private WorkoutDayOfWeek pickBestDayForPattern(ActiveWorkoutPlanContext ctx, String patternCode) {
        if (patternCode.contains("LEGS") || "CALVES".equals(patternCode)) {
            return pickBestDayForFocus(ctx, "leg");
        }
        if (patternCode.contains("PUSH")) {
            return pickBestDayForFocus(ctx, "push");
        }
        if (patternCode.contains("PULL")) {
            return pickBestDayForFocus(ctx, "pull");
        }
        return pickBestDayForFocus(ctx, "core");
    }

    private WorkoutDayOfWeek pickBestDayForMuscle(ActiveWorkoutPlanContext ctx, MuscleId muscle) {
        if (muscle.getRegion() == MuscleRegion.LOWER_BODY) {
            return pickBestDayForFocus(ctx, "leg");
        }
        if (muscle == MuscleId.CHEST || muscle == MuscleId.FRONT_DELTOIDS || muscle == MuscleId.TRICEPS) {
            return pickBestDayForFocus(ctx, "push");
        }
        if (muscle == MuscleId.LATISSIMUS_DORSI || muscle == MuscleId.RHOMBOIDS || muscle == MuscleId.REAR_DELTOIDS || muscle == MuscleId.BICEPS) {
            return pickBestDayForFocus(ctx, "pull");
        }
        return pickBestDayForFocus(ctx, "core");
    }

    private static String formatPrSummary(String exName, PersonalRecord pr) {
        StringBuilder sb = new StringBuilder(exName).append(": ");
        if (pr.getRecordValue() != null) {
            sb.append(pr.getRecordValue().stripTrailingZeros().toPlainString());
        }
        if (pr.getUnit() != null && !pr.getUnit().isBlank()) {
            sb.append(pr.getUnit().equals("s") ? "s" : " " + pr.getUnit());
        }
        if (pr.getSecondaryValue() != null) {
            sb.append(" × ").append(pr.getSecondaryValue().stripTrailingZeros().toPlainString()).append(" reps");
        }
        if (pr.getImprovementPercentage() != null && pr.getImprovementPercentage().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" (+").append(pr.getImprovementPercentage().stripTrailingZeros().toPlainString()).append("%)");
        }
        return sb.toString();
    }

    private static String formatScheduledDaysAndSets(SkillAuditItem skill) {
        if (skill.scheduledDays().isEmpty()) {
            return "0 weekly sets";
        }
        String dayLabels = skill.scheduledDays().stream()
                .map(WorkoutPlanAuditService::formatDayLabel)
                .collect(Collectors.joining(", "));
        return skill.weeklySkillSets() + " weekly sets on " + dayLabels;
    }

    private static String formatStatusLabel(CoverageStatus status) {
        if (status == null) return "Reviewed";
        return switch (status) {
            case MISSING -> "Missing";
            case UNDERDEVELOPED -> "Underdeveloped";
            case ADEQUATELY_COVERED -> "Adequately Covered";
            case OVERLOADED -> "Overloaded";
        };
    }

    private static String formatDayLabel(WorkoutDayOfWeek dow) {
        if (dow == null) return null;
        String n = dow.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.US);
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
