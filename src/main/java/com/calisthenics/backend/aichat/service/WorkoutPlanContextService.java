package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.muscle.MuscleId;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Retrieves and structures the user's authoritative active workout plan directly from the database,
 * resolves context-aware day references (e.g., "my pull day", "my Front Lever day") against real
 * weekday schedules and assigned exercises, and evaluates day suitability for proactive coaching.
 */
@Service
@RequiredArgsConstructor
public class WorkoutPlanContextService {

    private static final int HIGH_DAY_EXERCISE_THRESHOLD = 4;
    private static final int HIGH_DAY_SETS_THRESHOLD = 15;

    private static final Set<MuscleId> PULL_MUSCLES = Set.of(
            MuscleId.LATISSIMUS_DORSI, MuscleId.TERES_MAJOR, MuscleId.RHOMBOIDS,
            MuscleId.MIDDLE_TRAPEZIUS, MuscleId.LOWER_TRAPEZIUS, MuscleId.REAR_DELTOIDS, MuscleId.BICEPS
    );
    private static final Set<MuscleId> PUSH_MUSCLES = Set.of(
            MuscleId.CHEST, MuscleId.FRONT_DELTOIDS, MuscleId.TRICEPS
    );
    private static final Set<MuscleId> LEG_MUSCLES = Set.of(
            MuscleId.QUADRICEPS, MuscleId.HAMSTRINGS, MuscleId.GLUTES, MuscleId.CALVES, MuscleId.ADDUCTORS
    );
    private static final Set<MuscleId> CORE_MUSCLES = Set.of(
            MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS, MuscleId.OBLIQUES, MuscleId.ERECTOR_SPINAE
    );

    private static final Pattern SEMANTIC_DAY_EN_PATTERN = Pattern.compile(
            "(?i)\\b(?:my|the|a|an|that|this)?\\s*([a-z0-9][a-z0-9\\s+-]{1,40}?)\\s+(?:day|days|session|sessions|workout|workouts)\\b");
    private static final Pattern SEMANTIC_DAY_VN_PATTERN = Pattern.compile(
            "(?i)\\b(?:ngày|buổi)(?:\\s+tập)?\\s+([a-zA-Z0-9À-ỹ\\s+-]{2,40}?)(?:\\s+(?:của\\s+tôi|nhé|nha)|[,.!?]|$)");
    private static final Pattern LIGHT_HARD_SELECTOR_PATTERN = Pattern.compile(
            "(?i)\\b(?:the\\s+)?(light|lighter|hard|harder|heavy|heavier)(?:\\s+(?:one|day|session))?\\b");

    private static final Set<String> NON_FOCUS_DAY_WORDS = Set.of(
            "workout", "training", "current", "existing", "active", "weekly", "other", "another",
            "different", "next", "previous", "last", "first", "second", "same", "new", "one", "some", "any",
            "tập", "luyện", "này", "kia", "khác", "tiếp", "theo"
    );

    private static final Set<String> BOUNDARY_ANCHOR_TOKENS = Set.of(
            "add", "put", "move", "transfer", "remove", "delete", "show", "view", "check", "use", "pick", "choose",
            "to", "on", "in", "into", "onto", "for", "from", "with", "of", "at", "about",
            "my", "the", "a", "an", "your", "our", "this", "that", "is", "are", "was",
            "thêm", "vào", "cho", "của", "tôi", "là"
    );

    private static final Set<String> LEADING_FILLER_TOKENS = Set.of(
            "it", "them", "these", "those", "some", "more", "exercises", "exercise", "movements", "movement",
            "routine", "plan", "schedule", "current", "existing", "ngày", "buổi", "tập"
    );

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final MuscleTaxonomyService muscleTaxonomyService;

    public record ExerciseContext(
            Long workoutPlanExerciseId,
            Long exerciseId,
            String exerciseName,
            String code,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            ExerciseTrainingType trainingType,
            String exerciseType,
            List<String> primaryMuscles,
            List<String> secondaryMuscles,
            Set<MuscleId> primaryMuscleIds,
            Set<MuscleId> secondaryMuscleIds,
            Integer sets,
            Integer reps,
            Integer durationSeconds,
            Integer restSeconds,
            String notes
    ) {}

    public record DayContext(
            Long dayId,
            WorkoutDayOfWeek dayOfWeek,
            String dayLabel,
            boolean restDay,
            WorkoutSessionType sessionType,
            String dayName,
            String dayDescription,
            String focusSummary,
            Set<String> explicitFocusTags,
            Set<String> exerciseFocusTags,
            int exerciseCount,
            int totalSets,
            List<ExerciseContext> exercises
    ) {}

    public record ActiveWorkoutPlanContext(
            Long planId,
            String planName,
            String description,
            WorkoutSplitType splitType,
            boolean active,
            boolean coachAssigned,
            String coachName,
            int trainingDaysCount,
            int restDaysCount,
            List<DayContext> days
    ) {}

    public record DaySuitabilityEvaluation(
            DayContext day,
            double score,
            boolean focusAligned,
            boolean highVolume,
            boolean lightSession,
            boolean hardSession,
            boolean adjacentSameFocusConflict,
            List<String> alreadyScheduledMatchingExercises,
            List<String> availableCandidateExercises,
            String reasonSummary,
            String tradeoffNote
    ) {}

    public record DayRecommendationPlan(
            ActiveWorkoutPlanContext planContext,
            String requestedTopicLabel,
            List<DaySuitabilityEvaluation> rankedTrainingDays,
            DaySuitabilityEvaluation primaryRecommendation,
            List<DaySuitabilityEvaluation> alternatives
    ) {}

    @Transactional(readOnly = true)
    public Optional<ActiveWorkoutPlanContext> getActivePlanContext(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        if (relationshipRepository != null && assignmentRepository != null) {
            Optional<CoachClientRelationship> activeRel = relationshipRepository
                    .findFirstByClientIdAndStatusOrderByUpdatedAtDesc(userId, CoachClientRelationshipStatus.ACTIVE);
            if (activeRel.isPresent()) {
                List<WorkoutPlanAssignment> assignments = assignmentRepository
                        .findByClientIdAndStatusOrderByCreatedAtDesc(userId, WorkoutPlanAssignmentStatus.ACTIVE);
                for (WorkoutPlanAssignment assignment : assignments) {
                    if (assignment.getRelationship() != null
                            && Objects.equals(assignment.getRelationship().getId(), activeRel.get().getId())
                            && assignment.getWorkoutPlan() != null) {
                        WorkoutPlan plan = workoutPlanRepository
                                .findWithDetailsById(assignment.getWorkoutPlan().getId())
                                .orElse(assignment.getWorkoutPlan());
                        String coachName = assignment.getCoach() != null ? assignment.getCoach().getFullName() : null;
                        return Optional.of(buildPlanContext(plan, loadOrderedDays(plan), true, coachName));
                    }
                }
            }
        }

        return getOwnedPersonalPlanContext(userId);
    }

    @Transactional(readOnly = true)
    public Optional<ActiveWorkoutPlanContext> getOwnedPersonalPlanContext(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        Optional<WorkoutPlan> activePersonal = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
        if (activePersonal.isPresent()) {
            WorkoutPlan loaded = workoutPlanRepository.findWithDetailsById(activePersonal.get().getId())
                    .orElse(activePersonal.get());
            return Optional.of(buildPlanContext(loaded, loadOrderedDays(loaded), false, null));
        }

        List<WorkoutPlan> allPersonal = workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(userId);
        if (!allPersonal.isEmpty()) {
            WorkoutPlan first = allPersonal.get(0);
            WorkoutPlan loaded = workoutPlanRepository.findWithDetailsById(first.getId()).orElse(first);
            return Optional.of(buildPlanContext(loaded, loadOrderedDays(loaded), false, null));
        }
        return Optional.empty();
    }

    public ActiveWorkoutPlanContext buildPlanContext(
            WorkoutPlan plan,
            List<WorkoutPlanDay> orderedDays,
            boolean coachAssigned,
            String coachName
    ) {
        Map<WorkoutDayOfWeek, WorkoutPlanDay> dayMap = new EnumMap<>(WorkoutDayOfWeek.class);
        if (orderedDays != null) {
            for (WorkoutPlanDay d : orderedDays) {
                if (d != null && d.getDayOfWeek() != null) {
                    dayMap.put(d.getDayOfWeek(), d);
                }
            }
        }

        List<DayContext> dayContexts = new ArrayList<>();
        int trainingDays = 0;
        int restDays = 0;

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            WorkoutPlanDay day = dayMap.get(dow);
            DayContext dc = buildDayContext(dow, day);
            dayContexts.add(dc);
            if (dc.restDay()) {
                restDays++;
            } else {
                trainingDays++;
            }
        }

        return new ActiveWorkoutPlanContext(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getSplitType(),
                plan.isActive(),
                coachAssigned,
                coachName,
                trainingDays,
                restDays,
                List.copyOf(dayContexts)
        );
    }

    private DayContext buildDayContext(WorkoutDayOfWeek dow, WorkoutPlanDay day) {
        String label = formatDayLabel(dow, false);
        if (day == null || day.isRestDay() || day.getSessionType() == WorkoutSessionType.REST) {
            String dayName = (day != null && day.getName() != null && !day.getName().isBlank())
                    ? day.getName().trim() : "Rest Day";
            return new DayContext(
                    day != null ? day.getId() : null,
                    dow,
                    label,
                    true,
                    WorkoutSessionType.REST,
                    dayName,
                    day != null ? day.getDescription() : null,
                    "Rest Day",
                    Set.of("rest"),
                    Set.of(),
                    0,
                    0,
                    List.of()
            );
        }

        List<WorkoutPlanExercise> planExercises = loadOrderedExercises(day);
        List<ExerciseContext> exerciseContexts = new ArrayList<>(planExercises.size());
        Set<String> explicitTags = new LinkedHashSet<>();
        Set<String> exerciseTags = new LinkedHashSet<>();
        int totalSets = 0;

        addSessionTypeTags(day.getSessionType(), explicitTags);
        addTextFocusTags(day.getName(), explicitTags);
        addTextFocusTags(day.getDescription(), explicitTags);

        for (WorkoutPlanExercise pe : planExercises) {
            Exercise ex = pe.getExercise();
            if (ex == null) {
                continue;
            }
            if (pe.getSets() != null) {
                totalSets += pe.getSets();
            }

            MuscleTaxonomyService.ResolvedMuscleTargets resolved = muscleTaxonomyService != null
                    ? muscleTaxonomyService.resolveTargets(ex)
                    : MuscleTaxonomyService.ResolvedMuscleTargets.empty();

            Set<MuscleId> primaryIds = new LinkedHashSet<>();
            List<String> primaryNames = new ArrayList<>();
            for (String id : resolved.primaryMuscleIds()) {
                MuscleId.fromId(id).ifPresent(m -> {
                    primaryIds.add(m);
                    primaryNames.add(m.getDisplayName());
                });
            }
            Set<MuscleId> secondaryIds = new LinkedHashSet<>();
            List<String> secondaryNames = new ArrayList<>();
            for (String id : resolved.secondaryMuscleIds()) {
                MuscleId.fromId(id).ifPresent(m -> {
                    secondaryIds.add(m);
                    secondaryNames.add(m.getDisplayName());
                });
            }

            addExerciseDerivedTags(ex, primaryIds, exerciseTags);

            exerciseContexts.add(new ExerciseContext(
                    pe.getId(),
                    ex.getId(),
                    ex.getName(),
                    ex.getCode(),
                    ex.getCategory(),
                    ex.getDifficulty(),
                    ex.getTrainingType(),
                    ex.getExerciseType(),
                    List.copyOf(primaryNames),
                    List.copyOf(secondaryNames),
                    Set.copyOf(primaryIds),
                    Set.copyOf(secondaryIds),
                    pe.getSets(),
                    pe.getReps(),
                    pe.getDurationSeconds(),
                    pe.getRestSeconds(),
                    pe.getNotes()
            ));
        }

        String focusSummary = deriveFocusSummary(day, exerciseContexts);
        return new DayContext(
                day.getId(),
                dow,
                label,
                false,
                day.getSessionType(),
                day.getName(),
                day.getDescription(),
                focusSummary,
                Set.copyOf(explicitTags),
                Set.copyOf(exerciseTags),
                exerciseContexts.size(),
                totalSets,
                List.copyOf(exerciseContexts)
        );
    }

    /**
     * Renders the user's real active workout plan into a structured summary for the AI system prompt
     * so the model always sees exact weekdays, rest days, focuses, exercises, and muscle/category classifications.
     */
    public String formatStructuredContextForPrompt(ActiveWorkoutPlanContext ctx) {
        if (ctx == null) {
            return "Active Workout Plan: NONE (user has no active workout plan in the database).\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Active Workout Plan (Authoritative Database Schedule — NEVER override or invent days):\n");
        sb.append("- Plan Name: ").append(ctx.planName() != null ? ctx.planName() : "Unnamed Plan");
        if (ctx.splitType() != null) {
            sb.append(" | Split: ").append(ctx.splitType().name());
        }
        sb.append(" | Source: ").append(ctx.coachAssigned() ? "Coach-Assigned" : "Personal");
        sb.append(" | Training Days: ").append(ctx.trainingDaysCount()).append("/7")
                .append(" | Rest Days: ").append(ctx.restDaysCount()).append("/7\n");

        for (DayContext day : ctx.days()) {
            if (day.restDay()) {
                sb.append("  * ").append(day.dayOfWeek().name()).append(": [REST DAY] (")
                        .append(day.focusSummary())
                        .append(" — NEVER recommend or treat as Pull/Push/Legs/Skill day unless user explicitly asks to convert Rest Day)\n");
                continue;
            }
            sb.append("  * ").append(day.dayOfWeek().name()).append(": [TRAINING] Focus=\"")
                    .append(day.focusSummary()).append("\"");
            if (day.sessionType() != null) {
                sb.append(" (SessionType=").append(day.sessionType().name()).append(")");
            }
            sb.append(" | Volume: ").append(day.exerciseCount()).append(" exercise(s), ")
                    .append(day.totalSets()).append(" total sets");
            if (!day.exercises().isEmpty()) {
                String exSummary = day.exercises().stream()
                        .map(ex -> {
                            String cat = ex.category() != null ? ex.category().name() : "UNKNOWN";
                            String muscles = !ex.primaryMuscles().isEmpty()
                                    ? String.join("/", ex.primaryMuscles()) : "Unspecified";
                            String rx = formatShortPrescription(ex);
                            return ex.exerciseName() + " [" + cat + "; Primary=" + muscles + "; " + rx + "]";
                        })
                        .collect(Collectors.joining(", "));
                sb.append(" | Exercises: ").append(exSummary);
            } else {
                sb.append(" | Exercises: none");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Extracts a semantic training-day descriptor (e.g., "pull", "push", "front lever", "planche", "leg", "rest")
     * from a user message. Returns null for generic routine/plan phrases or unrelated messages.
     */
    public static String extractSemanticDayQuery(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();

        Matcher en = SEMANTIC_DAY_EN_PATTERN.matcher(trimmed);
        while (en.find()) {
            String candidate = cleanFocusDescriptor(en.group(1));
            if (candidate != null) {
                return candidate;
            }
        }

        Matcher vn = SEMANTIC_DAY_VN_PATTERN.matcher(trimmed);
        while (vn.find()) {
            String candidate = cleanFocusDescriptor(vn.group(1));
            if (candidate != null) {
                return candidate;
            }
        }

        Matcher lh = LIGHT_HARD_SELECTOR_PATTERN.matcher(trimmed);
        if (lh.find() && trimmed.split("\\s+").length <= 6) {
            return lh.group(1).toLowerCase(Locale.US);
        }

        return null;
    }

    /**
     * Extracts a semantic day query when the user replies briefly to a pending clarification
     * (e.g., "Pull", "Legs", "Front Lever + Pull", "the light one").
     */
    public String extractSemanticDayQueryWithPlanFallback(ActiveWorkoutPlanContext ctx, String text) {
        String extracted = extractSemanticDayQuery(text);
        if (extracted != null) {
            return extracted;
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.trim().toLowerCase(Locale.US).replaceAll("[.!?]+$", "").trim();
        if (lower.isEmpty() || lower.split("\\s+").length > 6) {
            return null;
        }
        if (Set.of("pull", "pulling", "push", "pushing", "leg", "legs", "lower", "upper",
                "core", "skill", "rest", "kéo", "đẩy", "chân", "nghỉ").contains(lower)) {
            return normalizeFocusKeyword(lower);
        }
        if (ctx != null) {
            for (DayContext day : ctx.days()) {
                if (day.dayName() != null && !day.dayName().isBlank()
                        && day.dayName().trim().toLowerCase(Locale.US).equals(lower)) {
                    return lower;
                }
            }
        }
        return null;
    }

    /**
     * Dynamically searches the active workout plan to find all days matching the user's semantic
     * day description based on sessionType, day title/description, and actual assigned exercises.
     * Rest Days NEVER match training focus queries unless the user explicitly asks for "rest".
     */
    public List<DayContext> findMatchingDays(ActiveWorkoutPlanContext ctx, String rawQuery) {
        if (ctx == null || ctx.days().isEmpty() || rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }

        String normalized = normalizeFocusKeyword(rawQuery);
        if (normalized.isBlank()) {
            return List.of();
        }

        if ("rest".equals(normalized) || "recovery".equals(normalized)) {
            return ctx.days().stream().filter(DayContext::restDay).toList();
        }

        boolean isSkillOrMovementQuery = isSkillOrNamedMovementQuery(normalized);
        List<DayContext> tier1Matches = new ArrayList<>();
        List<DayContext> tier2Matches = new ArrayList<>();

        for (DayContext day : ctx.days()) {
            if (day.restDay()) {
                continue;
            }
            if (matchesExplicitDayFocus(day, normalized)
                    || (isSkillOrMovementQuery && matchesAssignedExerciseName(day, normalized))) {
                tier1Matches.add(day);
            } else if (matchesDerivedExerciseFocus(day, normalized)) {
                tier2Matches.add(day);
            }
        }

        if (!tier1Matches.isEmpty()) {
            return List.copyOf(tier1Matches);
        }
        return List.copyOf(tier2Matches);
    }

    /**
     * Evaluates every non-rest day in the active workout plan for suitability when adding a requested
     * exercise or skill focus, considering focus alignment, existing exercises, volume, and recovery.
     */
    public DayRecommendationPlan evaluateDaysForAddition(
            ActiveWorkoutPlanContext ctx,
            String topicOrExerciseQuery,
            List<Exercise> candidateExercises
    ) {
        if (ctx == null) {
            return null;
        }
        String queryLower = topicOrExerciseQuery != null ? topicOrExerciseQuery.toLowerCase(Locale.US) : "";
        boolean wantsPull = queryLower.contains("front lever") || queryLower.contains("pull")
                || queryLower.contains("row") || queryLower.contains("back") || queryLower.contains("chin");
        boolean wantsPush = queryLower.contains("planche") || queryLower.contains("push")
                || queryLower.contains("dip") || queryLower.contains("handstand") || queryLower.contains("hspu");
        boolean wantsLegs = queryLower.contains("leg") || queryLower.contains("squat")
                || queryLower.contains("deadlift") || queryLower.contains("rdl")
                || queryLower.contains("nordic") || queryLower.contains("calf") || queryLower.contains("lunge");
        boolean wantsCore = queryLower.contains("core") || queryLower.contains("abs")
                || queryLower.contains("l-sit") || queryLower.contains("dragon flag") || queryLower.contains("plank");

        Set<ExerciseCategory> desiredCategories = new LinkedHashSet<>();
        Set<MuscleId> desiredMuscles = new LinkedHashSet<>();
        if (candidateExercises != null) {
            for (Exercise ex : candidateExercises) {
                if (ex.getCategory() != null) {
                    desiredCategories.add(ex.getCategory());
                }
                if (muscleTaxonomyService != null) {
                    MuscleTaxonomyService.ResolvedMuscleTargets targets = muscleTaxonomyService.resolveTargets(ex);
                    targets.primaryMuscleIds().forEach(id -> MuscleId.fromId(id).ifPresent(desiredMuscles::add));
                }
            }
        }
        if (desiredMuscles.isEmpty()) {
            if (wantsPull) desiredMuscles.addAll(PULL_MUSCLES);
            if (wantsPush) desiredMuscles.addAll(PUSH_MUSCLES);
            if (wantsLegs) desiredMuscles.addAll(LEG_MUSCLES);
            if (wantsCore) desiredMuscles.addAll(CORE_MUSCLES);
        }

        Map<WorkoutDayOfWeek, DayContext> dayByDow = new EnumMap<>(WorkoutDayOfWeek.class);
        for (DayContext dc : ctx.days()) {
            dayByDow.put(dc.dayOfWeek(), dc);
        }

        List<DaySuitabilityEvaluation> evaluations = new ArrayList<>();
        for (DayContext day : ctx.days()) {
            if (day.restDay()) {
                continue;
            }

            double score = 0.0;
            boolean focusAligned = false;

            boolean hasPullFocus = dayHasFocus(day, "pull") || dayHasFocus(day, "front lever")
                    || dayHasCategory(day, ExerciseCategory.PULL) || dayHasMuscles(day, PULL_MUSCLES);
            boolean hasPushFocus = dayHasFocus(day, "push") || dayHasFocus(day, "planche")
                    || dayHasFocus(day, "handstand") || dayHasFocus(day, "hspu")
                    || dayHasCategory(day, ExerciseCategory.PUSH) || dayHasMuscles(day, PUSH_MUSCLES);
            boolean hasLegFocus = dayHasFocus(day, "leg") || dayHasFocus(day, "lower")
                    || dayHasCategory(day, ExerciseCategory.LEGS) || dayHasMuscles(day, LEG_MUSCLES);
            boolean hasCoreFocus = dayHasFocus(day, "core")
                    || dayHasCategory(day, ExerciseCategory.CORE) || dayHasMuscles(day, CORE_MUSCLES);

            if (wantsPull) {
                if (dayHasExplicitTag(day, "pull")) {
                    score += 35.0;
                    focusAligned = true;
                } else if (hasPullFocus) {
                    score += 20.0;
                    focusAligned = true;
                }
                if (!queryLower.isBlank() && (dayHasExplicitTag(day, normalizeFocusKeyword(queryLower))
                        || matchesAssignedExerciseName(day, normalizeFocusKeyword(queryLower)))) {
                    score += 25.0;
                    focusAligned = true;
                }
                if (hasLegFocus && !hasPullFocus) {
                    score -= 25.0;
                } else if (hasPushFocus && !hasPullFocus && !dayHasFocus(day, "upper") && !dayHasFocus(day, "full body")) {
                    score -= 15.0;
                }
            } else if (wantsPush) {
                if (dayHasExplicitTag(day, "push") || dayHasExplicitTag(day, "planche") || dayHasExplicitTag(day, "handstand")) {
                    score += 35.0;
                    focusAligned = true;
                } else if (hasPushFocus) {
                    score += 20.0;
                    focusAligned = true;
                }
                if (hasLegFocus && !hasPushFocus) {
                    score -= 25.0;
                } else if (hasPullFocus && !hasPushFocus && !dayHasFocus(day, "upper") && !dayHasFocus(day, "full body")) {
                    score -= 15.0;
                }
            } else if (wantsLegs) {
                if (dayHasExplicitTag(day, "leg") || dayHasExplicitTag(day, "lower")) {
                    score += 45.0;
                    focusAligned = true;
                } else if (hasLegFocus) {
                    score += 25.0;
                    focusAligned = true;
                } else {
                    score -= 20.0;
                }
            } else if (wantsCore) {
                if (dayHasExplicitTag(day, "core") || hasCoreFocus) {
                    score += 30.0;
                    focusAligned = true;
                }
            } else {
                // Generic muscle/category alignment
                if (desiredCategories.stream().anyMatch(cat -> dayHasCategory(day, cat))) {
                    score += 20.0;
                    focusAligned = true;
                }
                if (dayHasMuscles(day, desiredMuscles)) {
                    score += 15.0;
                    focusAligned = true;
                }
            }

            if (day.sessionType() == WorkoutSessionType.UPPER || day.sessionType() == WorkoutSessionType.FULL_BODY) {
                if (wantsPull || wantsPush) {
                    score += 10.0;
                    focusAligned = true;
                }
            }

            String focusLower = (nullSafe(day.dayName()) + " " + nullSafe(day.dayDescription())).toLowerCase(Locale.US);
            boolean lightSession = focusLower.contains("light") || focusLower.contains("technique") || focusLower.contains("nhẹ");
            boolean hardSession = focusLower.contains("hard") || focusLower.contains("heavy") || focusLower.contains("intense") || focusLower.contains("nặng");
            boolean highVolume = day.exerciseCount() >= HIGH_DAY_EXERCISE_THRESHOLD || day.totalSets() >= HIGH_DAY_SETS_THRESHOLD;

            if (lightSession) {
                score += 10.0;
            }
            if (hardSession) {
                score -= 12.0;
            }
            if (highVolume) {
                score -= 15.0;
            } else if (day.exerciseCount() <= 3 && day.totalSets() <= 12) {
                score += 10.0;
            }

            // Duplicate / candidate availability check
            Set<Long> scheduledIds = day.exercises().stream()
                    .map(ExerciseContext::exerciseId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<String> alreadyScheduledMatching = new ArrayList<>();
            List<String> availableCandidates = new ArrayList<>();
            if (candidateExercises != null) {
                for (Exercise ex : candidateExercises) {
                    if (scheduledIds.contains(ex.getId())) {
                        alreadyScheduledMatching.add(ex.getName());
                    } else {
                        availableCandidates.add(ex.getName());
                    }
                }
                if (!candidateExercises.isEmpty() && availableCandidates.isEmpty()) {
                    score -= 60.0;
                }
            }

            // Recovery / adjacency check
            boolean adjacentConflict = hasAdjacentSameFocusConflict(dayByDow, day.dayOfWeek(), wantsPull, wantsPush, wantsLegs);
            if (adjacentConflict) {
                score -= 6.0;
            }

            String reason = buildSuitabilityReason(day, focusAligned, lightSession, hardSession, highVolume,
                    adjacentConflict, alreadyScheduledMatching, availableCandidates);
            String tradeoff = buildSuitabilityTradeoff(day, lightSession, hardSession, highVolume,
                    adjacentConflict, alreadyScheduledMatching, availableCandidates);

            evaluations.add(new DaySuitabilityEvaluation(
                    day,
                    score,
                    focusAligned,
                    highVolume,
                    lightSession,
                    hardSession,
                    adjacentConflict,
                    List.copyOf(alreadyScheduledMatching),
                    List.copyOf(availableCandidates),
                    reason,
                    tradeoff
            ));
        }

        evaluations.sort(Comparator
                .comparingDouble(DaySuitabilityEvaluation::score).reversed()
                .thenComparingInt(e -> e.day().dayOfWeek().getOrder()));

        DaySuitabilityEvaluation primary = evaluations.isEmpty() ? null : evaluations.get(0);
        List<DaySuitabilityEvaluation> alternatives = new ArrayList<>();
        for (int i = 1; i < evaluations.size() && alternatives.size() < 2; i++) {
            DaySuitabilityEvaluation cand = evaluations.get(i);
            if (cand.focusAligned() || cand.score() > 0 || alternatives.isEmpty()) {
                alternatives.add(cand);
            }
        }

        return new DayRecommendationPlan(
                ctx,
                topicOrExerciseQuery,
                List.copyOf(evaluations),
                primary,
                List.copyOf(alternatives)
        );
    }

    public String formatProactiveRecommendationClarification(
            DayRecommendationPlan evalPlan,
            String requestedSubjectLabel,
            boolean isVn
    ) {
        ActiveWorkoutPlanContext ctx = evalPlan.planContext();
        String planTitle = ctx.planName() != null ? ctx.planName() : (isVn ? "Lịch tập hiện tại" : "your current workout plan");
        String subject = requestedSubjectLabel != null && !requestedSubjectLabel.isBlank()
                ? requestedSubjectLabel : (isVn ? "bài tập này" : "these exercises");

        if (evalPlan.primaryRecommendation() == null) {
            if (isVn) {
                return "Tôi đã kiểm tra lịch tập hiện tại (**" + planTitle + "**), nhưng hiện tất cả các ngày trong tuần đều là Ngày nghỉ (Rest Day). "
                        + "Bạn muốn chuyển thứ mấy thành buổi tập để thêm **" + subject + "**?";
            }
            return "I checked your current workout plan (**" + planTitle + "**), and all 7 days are currently set as Rest Days. "
                    + "Which weekday would you like to convert into a training day for **" + subject + "**?";
        }

        DaySuitabilityEvaluation primary = evalPlan.primaryRecommendation();
        List<DaySuitabilityEvaluation> alts = evalPlan.alternatives();

        StringBuilder sb = new StringBuilder();
        if (isVn) {
            sb.append("Tôi đã kiểm tra lịch tập hiện tại của bạn (**").append(planTitle).append("**).\n\n");
            sb.append("Tôi đề xuất thêm **").append(subject).append("** vào **")
                    .append(primary.day().dayLabel()).append(" (").append(primary.day().focusSummary()).append(")**, vì ")
                    .append(primary.reasonSummary()).append(".\n\n");
            sb.append("Bạn muốn chọn phương án nào?\n");
            sb.append("- Thêm vào **").append(primary.day().dayLabel()).append("** (")
                    .append(primary.day().focusSummary()).append(") — đề xuất phù hợp nhất\n");
            for (DaySuitabilityEvaluation alt : alts) {
                sb.append("- Thêm vào **").append(alt.day().dayLabel()).append("** (")
                        .append(alt.day().focusSummary()).append(")");
                if (alt.tradeoffNote() != null && !alt.tradeoffNote().isBlank()) {
                    sb.append(" — ").append(alt.tradeoffNote());
                }
                sb.append("\n");
            }
            sb.append("- Hoặc chọn một ngày tập khác trong tuần?\n\n");
            sb.append("Bạn thấy phương án nào phù hợp hơn?");
        } else {
            sb.append("I checked your current workout plan (**").append(planTitle).append("**).\n\n");
            sb.append("I recommend adding **").append(subject).append("** to **")
                    .append(primary.day().dayLabel()).append(" (").append(primary.day().focusSummary()).append(")**, since ")
                    .append(primary.reasonSummary()).append(".\n\n");
            sb.append("Would you prefer to:\n");
            sb.append("- Add to **").append(primary.day().dayLabel()).append("** (")
                    .append(primary.day().focusSummary()).append(") — my recommendation\n");
            for (DaySuitabilityEvaluation alt : alts) {
                sb.append("- Add to **").append(alt.day().dayLabel()).append("** (")
                        .append(alt.day().focusSummary()).append(")");
                if (alt.tradeoffNote() != null && !alt.tradeoffNote().isBlank()) {
                    sb.append(" — ").append(alt.tradeoffNote());
                }
                sb.append("\n");
            }
            sb.append("- Add to another training day?\n\n");
            sb.append("What do you think?");
        }
        return sb.toString();
    }

    public String formatMultiDayMatchClarification(
            ActiveWorkoutPlanContext ctx,
            String rawQuery,
            List<DayContext> matches,
            String requestedSubjectLabel,
            List<Exercise> candidateExercises,
            boolean isVn
    ) {
        String subject = requestedSubjectLabel != null && !requestedSubjectLabel.isBlank()
                ? requestedSubjectLabel : (isVn ? "bài tập này" : "these exercises");
        StringBuilder sb = new StringBuilder();
        if (isVn) {
            sb.append("Tôi đã kiểm tra lịch tập **").append(ctx.planName()).append("** và thấy **")
                    .append(matches.size()).append(" ngày** phù hợp với \"").append(rawQuery).append("\":\n\n");
        } else {
            sb.append("I checked your current workout plan (**").append(ctx.planName()).append("**) and found **")
                    .append(matches.size()).append(" matching days** for **").append(rawQuery).append("**:\n\n");
        }

        for (DayContext d : matches) {
            String exNames = d.exercises().isEmpty()
                    ? (isVn ? "chưa có bài tập" : "no exercises yet")
                    : d.exercises().stream().map(ExerciseContext::exerciseName).collect(Collectors.joining(", "));
            sb.append("- **").append(d.dayLabel()).append("** (").append(d.focusSummary()).append("): ")
                    .append(d.exerciseCount()).append(isVn ? " bài tập, " : " exercise(s), ")
                    .append(d.totalSets()).append(isVn ? " hiệp" : " sets")
                    .append(" — ").append(exNames);

            if (candidateExercises != null && !candidateExercises.isEmpty()) {
                Set<Long> scheduledIds = d.exercises().stream()
                        .map(ExerciseContext::exerciseId).filter(Objects::nonNull).collect(Collectors.toSet());
                List<String> alreadyOnDay = candidateExercises.stream()
                        .filter(e -> scheduledIds.contains(e.getId()))
                        .map(Exercise::getName)
                        .toList();
                if (!alreadyOnDay.isEmpty()) {
                    sb.append(isVn ? " _(đã có: " : " _(already includes: ")
                            .append(String.join(", ", alreadyOnDay)).append(")_");
                }
            }
            sb.append("\n");
        }

        String dayList = matches.stream().map(DayContext::dayLabel).collect(Collectors.joining(isVn ? " hay " : " or "));
        if (isVn) {
            sb.append("\nBạn muốn thêm **").append(subject).append("** vào ngày nào (").append(dayList).append(")?");
        } else {
            sb.append("\nWhich day would you like to add **").append(subject).append("** to (").append(dayList).append(")?");
        }
        return sb.toString();
    }

    public String formatNoMatchingDayClarification(
            ActiveWorkoutPlanContext ctx,
            String rawQuery,
            String requestedSubjectLabel,
            boolean isVn
    ) {
        String subject = requestedSubjectLabel != null && !requestedSubjectLabel.isBlank()
                ? requestedSubjectLabel : (isVn ? "bài tập này" : "these exercises");
        StringBuilder sb = new StringBuilder();
        if (isVn) {
            sb.append("Tôi đã kiểm tra lịch tập hiện tại (**").append(ctx.planName())
                    .append("**), nhưng không có ngày nào khớp với **").append(rawQuery).append("**.\n\n");
            sb.append("Các ngày tập hiện có trong lịch của bạn:\n");
        } else {
            sb.append("I checked your current workout plan (**").append(ctx.planName())
                    .append("**), and it doesn't currently contain a matching **").append(rawQuery).append("** day.\n\n");
            sb.append("Your current training days are:\n");
        }
        boolean hasTrainingDay = false;
        for (DayContext d : ctx.days()) {
            if (!d.restDay()) {
                hasTrainingDay = true;
                sb.append("- **").append(d.dayLabel()).append("**: ").append(d.focusSummary())
                        .append(" (").append(d.exerciseCount()).append(isVn ? " bài tập)\n" : " exercise(s))\n");
            }
        }
        if (!hasTrainingDay) {
            sb.append(isVn ? "- (Chưa có ngày tập nào — tất cả đều là Ngày nghỉ)\n" : "- (No active training days — all 7 days are Rest Days)\n");
        }
        if (isVn) {
            sb.append("\nBạn muốn thêm **").append(subject).append("** vào ngày nào trong lịch trên, hay muốn giữ nguyên lịch hiện tại?");
        } else {
            sb.append("\nWhich existing day would you like to add **").append(subject).append("** to, or how would you like to proceed?");
        }
        return sb.toString();
    }

    private boolean matchesExplicitDayFocus(DayContext day, String query) {
        if (day.explicitFocusTags().contains(query)) {
            return true;
        }
        if (day.dayName() != null && day.dayName().toLowerCase(Locale.US).contains(query)) {
            return true;
        }
        if (day.dayDescription() != null && day.dayDescription().toLowerCase(Locale.US).contains(query)) {
            return true;
        }
        if ("leg".equals(query) && (day.explicitFocusTags().contains("lower") || day.explicitFocusTags().contains("legs"))) {
            return true;
        }
        if (("handstand".equals(query) || "hspu".equals(query))
                && (day.explicitFocusTags().contains("handstand") || day.explicitFocusTags().contains("hspu"))) {
            return true;
        }
        return false;
    }

    private boolean matchesAssignedExerciseName(DayContext day, String query) {
        for (ExerciseContext ex : day.exercises()) {
            if (ex.exerciseName() != null && ex.exerciseName().toLowerCase(Locale.US).contains(query)) {
                return true;
            }
            if (ex.code() != null && ex.code().toLowerCase(Locale.US).replace('_', ' ').contains(query)) {
                return true;
            }
            if (("handstand".equals(query) || "hspu".equals(query)) && ex.exerciseName() != null) {
                String n = ex.exerciseName().toLowerCase(Locale.US);
                if (n.contains("handstand") || n.contains("hspu")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesDerivedExerciseFocus(DayContext day, String query) {
        if (day.exerciseFocusTags().contains(query)) {
            return true;
        }
        if ("leg".equals(query) && (day.exerciseFocusTags().contains("lower") || day.exerciseFocusTags().contains("legs"))) {
            return true;
        }
        return false;
    }

    private static boolean isSkillOrNamedMovementQuery(String normalized) {
        return normalized.contains("lever")
                || normalized.contains("planche")
                || normalized.contains("handstand")
                || normalized.contains("hspu")
                || normalized.contains("muscle up")
                || normalized.contains("l-sit")
                || normalized.contains("flag")
                || normalized.contains("squat")
                || normalized.contains("dip");
    }

    private static String cleanFocusDescriptor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] tokens = raw.toLowerCase(Locale.US).trim().replaceAll("[^a-z0-9À-ỹ+\\s-]", " ").split("\\s+");
        int startIndex = 0;
        for (int i = 0; i < tokens.length; i++) {
            if (BOUNDARY_ANCHOR_TOKENS.contains(tokens[i])) {
                startIndex = i + 1;
            }
        }
        List<String> kept = new ArrayList<>();
        for (int i = startIndex; i < tokens.length; i++) {
            String tok = tokens[i];
            if (!tok.isBlank() && !LEADING_FILLER_TOKENS.contains(tok) && !BOUNDARY_ANCHOR_TOKENS.contains(tok)) {
                kept.add(tok);
            }
        }
        if (kept.isEmpty()) {
            return null;
        }
        String joined = String.join(" ", kept).trim();
        if (NON_FOCUS_DAY_WORDS.contains(joined)) {
            return null;
        }
        if (UserAiWorkoutToolService.extractWeekday(joined) != null) {
            return null;
        }
        return normalizeFocusKeyword(joined);
    }

    public static String normalizeFocusKeyword(String raw) {
        if (raw == null) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.US).trim().replaceAll("\\s+", " ");
        return switch (lower) {
            case "pulling", "kéo" -> "pull";
            case "pushing", "đẩy" -> "push";
            case "legs", "lower body", "chân" -> "leg";
            case "upper body" -> "upper";
            case "abs", "abdominal", "bụng" -> "core";
            case "nghỉ", "recovery" -> "rest";
            case "handstand push up", "handstand push-up", "handstand pushups", "handstand push-ups" -> "hspu";
            default -> lower;
        };
    }

    private static void addSessionTypeTags(WorkoutSessionType type, Set<String> tags) {
        if (type == null || type == WorkoutSessionType.REST) {
            return;
        }
        switch (type) {
            case PULL -> tags.add("pull");
            case PUSH -> tags.add("push");
            case LEGS, LOWER -> {
                tags.add("leg");
                tags.add("lower");
            }
            case UPPER -> tags.add("upper");
            case FULL_BODY -> tags.add("full body");
            case FRONT_LEVER -> {
                tags.add("front lever");
                tags.add("pull");
                tags.add("skill");
            }
            case PLANCHE -> {
                tags.add("planche");
                tags.add("push");
                tags.add("skill");
            }
            case HANDSTAND -> {
                tags.add("handstand");
                tags.add("hspu");
                tags.add("push");
                tags.add("skill");
            }
            case SKILL_TRAINING -> tags.add("skill");
            case STRAIGHT_ARM -> tags.add("straight arm");
            case BENT_ARM -> tags.add("bent arm");
            case STRENGTH -> tags.add("strength");
            case HYPERTROPHY -> tags.add("hypertrophy");
            case MOBILITY -> tags.add("mobility");
            case CONDITIONING -> tags.add("conditioning");
            default -> {
            }
        }
    }

    private static void addTextFocusTags(String text, Set<String> tags) {
        if (text == null || text.isBlank()) {
            return;
        }
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("pull")) tags.add("pull");
        if (lower.contains("push")) tags.add("push");
        if (lower.contains("leg") || lower.contains("lower")) {
            tags.add("leg");
            tags.add("lower");
        }
        if (lower.contains("upper")) tags.add("upper");
        if (lower.contains("full body")) tags.add("full body");
        if (lower.contains("front lever")) tags.add("front lever");
        if (lower.contains("back lever")) tags.add("back lever");
        if (lower.contains("planche")) tags.add("planche");
        if (lower.contains("handstand")) tags.add("handstand");
        if (lower.contains("hspu")) {
            tags.add("hspu");
            tags.add("handstand");
        }
        if (lower.contains("core") || lower.contains("abs")) tags.add("core");
        if (lower.contains("skill")) tags.add("skill");
        if (lower.contains("light")) tags.add("light");
        if (lower.contains("hard") || lower.contains("heavy")) tags.add("hard");
    }

    private static void addExerciseDerivedTags(Exercise ex, Set<MuscleId> primaryMuscles, Set<String> tags) {
        if (ex.getName() != null) {
            addTextFocusTags(ex.getName(), tags);
        }
        if (ex.getAliases() != null) {
            addTextFocusTags(ex.getAliases(), tags);
        }
        if (ex.getCategory() != null) {
            switch (ex.getCategory()) {
                case PULL, BACK, BICEPS -> tags.add("pull");
                case PUSH, CHEST, SHOULDERS, TRICEPS -> tags.add("push");
                case LEGS, GLUTES -> {
                    tags.add("leg");
                    tags.add("lower");
                }
                case CORE -> tags.add("core");
                case SKILL, ISOMETRIC -> tags.add("skill");
                default -> {
                }
            }
        }
        if (primaryMuscles.stream().anyMatch(PULL_MUSCLES::contains)) {
            tags.add("pull");
        }
        if (primaryMuscles.stream().anyMatch(PUSH_MUSCLES::contains)) {
            tags.add("push");
        }
        if (primaryMuscles.stream().anyMatch(LEG_MUSCLES::contains)) {
            tags.add("leg");
            tags.add("lower");
        }
        if (primaryMuscles.stream().anyMatch(CORE_MUSCLES::contains)) {
            tags.add("core");
        }
    }

    private static String deriveFocusSummary(WorkoutPlanDay day, List<ExerciseContext> exercises) {
        if (day.getName() != null && !day.getName().isBlank()) {
            return day.getName().trim();
        }
        if (day.getSessionType() != null && day.getSessionType() != WorkoutSessionType.CUSTOM) {
            return formatSessionTypeLabel(day.getSessionType());
        }
        if (!exercises.isEmpty()) {
            return exercises.stream()
                    .map(ExerciseContext::exerciseName)
                    .limit(2)
                    .collect(Collectors.joining(" + "));
        }
        return "Training Day";
    }

    private static String formatSessionTypeLabel(WorkoutSessionType type) {
        return switch (type) {
            case PUSH -> "Push Day";
            case PULL -> "Pull Day";
            case LEGS -> "Leg Day";
            case UPPER -> "Upper Body";
            case LOWER -> "Lower Body";
            case FULL_BODY -> "Full Body";
            case FRONT_LEVER -> "Front Lever";
            case PLANCHE -> "Planche";
            case HANDSTAND -> "Handstand";
            case SKILL_TRAINING -> "Skill Training";
            default -> type.name().replace('_', ' ');
        };
    }

    private static boolean dayHasExplicitTag(DayContext day, String tag) {
        return day.explicitFocusTags().contains(tag);
    }

    private static boolean dayHasFocus(DayContext day, String tag) {
        return day.explicitFocusTags().contains(tag) || day.exerciseFocusTags().contains(tag);
    }

    private static boolean dayHasCategory(DayContext day, ExerciseCategory category) {
        return day.exercises().stream().anyMatch(e -> e.category() == category);
    }

    private static boolean dayHasMuscles(DayContext day, Set<MuscleId> muscles) {
        return day.exercises().stream().anyMatch(e ->
                e.primaryMuscleIds().stream().anyMatch(muscles::contains));
    }

    private static boolean hasAdjacentSameFocusConflict(
            Map<WorkoutDayOfWeek, DayContext> byDow,
            WorkoutDayOfWeek current,
            boolean wantsPull,
            boolean wantsPush,
            boolean wantsLegs
    ) {
        int order = current.getOrder();
        int prevOrder = order == 1 ? 7 : order - 1;
        int nextOrder = order == 7 ? 1 : order + 1;
        for (WorkoutDayOfWeek neighborDow : WorkoutDayOfWeek.values()) {
            if (neighborDow.getOrder() != prevOrder && neighborDow.getOrder() != nextOrder) {
                continue;
            }
            DayContext neighbor = byDow.get(neighborDow);
            if (neighbor == null || neighbor.restDay()) {
                continue;
            }
            if (wantsPull && (dayHasFocus(neighbor, "pull") || dayHasFocus(neighbor, "front lever"))) {
                return true;
            }
            if (wantsPush && (dayHasFocus(neighbor, "push") || dayHasFocus(neighbor, "planche"))) {
                return true;
            }
            if (wantsLegs && (dayHasFocus(neighbor, "leg") || dayHasFocus(neighbor, "lower"))) {
                return true;
            }
        }
        return false;
    }

    private static String buildSuitabilityReason(
            DayContext day,
            boolean focusAligned,
            boolean lightSession,
            boolean hardSession,
            boolean highVolume,
            boolean adjacentConflict,
            List<String> alreadyScheduledMatching,
            List<String> availableCandidates
    ) {
        List<String> reasons = new ArrayList<>();
        if (focusAligned) {
            reasons.add("it already aligns with that training focus (" + day.focusSummary() + ")");
        } else {
            reasons.add("it is an active training day (" + day.focusSummary() + ")");
        }
        if (lightSession) {
            reasons.add("it is a lighter session with room for skill work");
        } else if (!highVolume) {
            reasons.add("its current volume is manageable (" + day.exerciseCount() + " exercise(s), " + day.totalSets() + " sets)");
        }
        if (!alreadyScheduledMatching.isEmpty() && !availableCandidates.isEmpty()) {
            reasons.add("we can complement your existing " + String.join(", ", alreadyScheduledMatching)
                    + " without duplicating it");
        }
        if (!adjacentConflict) {
            reasons.add("adjacent days allow good recovery");
        }
        if (hardSession || highVolume) {
            reasons.add("though note this day already carries substantial volume");
        }
        return String.join(" and ", reasons.subList(0, Math.min(2, reasons.size())));
    }

    private static String buildSuitabilityTradeoff(
            DayContext day,
            boolean lightSession,
            boolean hardSession,
            boolean highVolume,
            boolean adjacentConflict,
            List<String> alreadyScheduledMatching,
            List<String> availableCandidates
    ) {
        if (!availableCandidates.isEmpty() && !alreadyScheduledMatching.isEmpty()) {
            return "already includes " + String.join(", ", alreadyScheduledMatching)
                    + "; can add complementary variation (" + day.exerciseCount() + " ex, " + day.totalSets() + " sets)";
        }
        if (lightSession) {
            return "lighter skill volume (" + day.exerciseCount() + " ex, " + day.totalSets() + " sets)";
        }
        if (hardSession || highVolume) {
            return "higher existing volume/intensity (" + day.exerciseCount() + " ex, " + day.totalSets() + " sets — watch fatigue)";
        }
        if (adjacentConflict) {
            return "back-to-back with a similar session (" + day.exerciseCount() + " ex, " + day.totalSets() + " sets)";
        }
        return day.exerciseCount() + " exercise(s), " + day.totalSets() + " sets";
    }

    private List<WorkoutPlanDay> loadOrderedDays(WorkoutPlan plan) {
        if (plan.getId() != null && workoutPlanDayRepository != null) {
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
        if (day == null) {
            return List.of();
        }
        if (day.getId() != null && workoutPlanExerciseRepository != null) {
            List<WorkoutPlanExercise> repoExs = workoutPlanExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
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

    private static String formatShortPrescription(ExerciseContext ex) {
        StringBuilder sb = new StringBuilder();
        if (ex.sets() != null) {
            sb.append(ex.sets()).append("x");
        }
        if (ex.reps() != null) {
            sb.append(ex.reps()).append(" reps");
        } else if (ex.durationSeconds() != null) {
            sb.append(ex.durationSeconds()).append("s hold");
        }
        if (ex.restSeconds() != null) {
            sb.append(" (").append(ex.restSeconds()).append("s rest)");
        }
        return sb.toString();
    }

    private static String formatDayLabel(WorkoutDayOfWeek dow, boolean isVn) {
        if (dow == null) {
            return "";
        }
        if (isVn) {
            return switch (dow) {
                case MONDAY -> "Thứ Hai (Monday)";
                case TUESDAY -> "Thứ Ba (Tuesday)";
                case WEDNESDAY -> "Thứ Tư (Wednesday)";
                case THURSDAY -> "Thứ Năm (Thursday)";
                case FRIDAY -> "Thứ Sáu (Friday)";
                case SATURDAY -> "Thứ Bảy (Saturday)";
                case SUNDAY -> "Chủ Nhật (Sunday)";
            };
        }
        String raw = dow.name();
        return raw.charAt(0) + raw.substring(1).toLowerCase(Locale.US);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
