package com.calisthenics.backend.aiworkout.validation;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.exception.AiWorkoutPlanValidationException;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Component
@RequiredArgsConstructor
public class AiWorkoutPlanValidator {

    private static final int DAYS_IN_WEEK = 7;
    private static final int HARD_MAX_EXERCISES_PER_SESSION = 10;
    private static final int MAX_SETS = 10;
    private static final int MAX_REPS = 100;
    private static final int MAX_HOLD_SECONDS = 300;
    private static final int MAX_REST_SECONDS = 600;

    private final AppProperties appProperties;
    private final AiWorkoutPlanPromptAnalyzer promptAnalyzer;

    public record ValidationResult(
            AiWorkoutPlanPreviewResponse preview,
            List<String> violations
    ) {
        public boolean isValid() {
            return violations.isEmpty() && preview != null;
        }
    }

    public ValidationResult validateAndNormalizeGeneratedPlan(
            GeminiStructuredWorkoutPlan plan,
            List<Exercise> catalog,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints,
            LocalDate defaultStartDate
    ) {
        List<String> violations = new ArrayList<>();
        if (plan == null) {
            violations.add("Workout plan response is empty.");
            return new ValidationResult(null, violations);
        }

        CatalogIndex catalogIndex = CatalogIndex.from(catalog);
        List<GeminiStructuredWorkoutPlan.GeminiSession> rawSessions = plan.getSessions();
        if (rawSessions == null || rawSessions.isEmpty()) {
            violations.add("Workout plan must contain at least 1 scheduled day.");
            return new ValidationResult(null, violations);
        }
        if (rawSessions.size() > DAYS_IN_WEEK) {
            violations.add("Weekly plan cannot contain more than 7 day entries (found " + rawSessions.size() + ").");
        }

        Map<WorkoutDayOfWeek, GeminiStructuredWorkoutPlan.GeminiSession> byDay = new EnumMap<>(WorkoutDayOfWeek.class);
        for (int i = 0; i < rawSessions.size(); i++) {
            GeminiStructuredWorkoutPlan.GeminiSession s = rawSessions.get(i);
            if (s == null || s.getDayOfWeek() == null || s.getDayOfWeek().isBlank()) {
                violations.add("Session at index " + i + " is missing day_of_week.");
                continue;
            }
            WorkoutDayOfWeek dow;
            try {
                dow = WorkoutDayOfWeek.valueOf(s.getDayOfWeek().trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException ex) {
                violations.add("Invalid day_of_week '" + s.getDayOfWeek() + "'. Must be MONDAY through SUNDAY.");
                continue;
            }
            if (byDay.putIfAbsent(dow, s) != null) {
                violations.add("Duplicate weekday '" + dow.name() + "' found in weekly schedule.");
            }
        }

        List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> sessionPreviews = new ArrayList<>(DAYS_IN_WEEK);
        int trainingDays = 0;

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            GeminiStructuredWorkoutPlan.GeminiSession rawSession = byDay.get(dow);
            if (rawSession == null || Boolean.TRUE.equals(rawSession.getRestDay())) {
                if (rawSession != null && rawSession.getExercises() != null && !rawSession.getExercises().isEmpty()) {
                    violations.add(dow.name() + " is marked as rest day but contains exercises.");
                }
                String restTitle = (rawSession != null && rawSession.getTitle() != null && !rawSession.getTitle().isBlank())
                        ? rawSession.getTitle().trim()
                        : "Rest Day";
                sessionPreviews.add(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                        .dayOfWeek(dow)
                        .title(sanitizeDayTitle(restTitle, "Rest Day"))
                        .restDay(true)
                        .exercises(Collections.emptyList())
                        .build());
                continue;
            }

            trainingDays++;
            String dayTitle = sanitizeDayTitle(rawSession.getTitle(), dow.name() + " Workout");
            if (looksLikeRestTitle(dayTitle) && (rawSession.getExercises() == null || rawSession.getExercises().isEmpty())) {
                violations.add(dow.name() + " has a rest title but is not marked as is_rest_day=true.");
            }

            List<GeminiStructuredWorkoutPlan.GeminiExercise> rawExercises = rawSession.getExercises();
            if (rawExercises == null || rawExercises.isEmpty()) {
                violations.add("Training day " + dow.name() + " must include at least 1 exercise.");
                sessionPreviews.add(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                        .dayOfWeek(dow)
                        .title(dayTitle)
                        .restDay(false)
                        .exercises(Collections.emptyList())
                        .build());
                continue;
            }

            int maxAllowed = Math.min(
                    HARD_MAX_EXERCISES_PER_SESSION,
                    promptAnalyzer.maxExercisesForSession(constraints, dayTitle)
            );
            if (rawExercises.size() > maxAllowed) {
                violations.add(dow.name() + " contains " + rawExercises.size()
                        + " exercises, exceeding maximum of " + maxAllowed + " per session.");
            }

            List<AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview> exercisePreviews = new ArrayList<>();
            Set<Long> seenExerciseIds = new HashSet<>();

            for (int idx = 0; idx < rawExercises.size(); idx++) {
                GeminiStructuredWorkoutPlan.GeminiExercise rawEx = rawExercises.get(idx);
                if (rawEx == null) {
                    violations.add(dow.name() + " exercise #" + (idx + 1) + " is null.");
                    continue;
                }
                Exercise resolved = resolveExactCatalogExercise(rawEx.getExerciseId(), rawEx.getExerciseName(), catalogIndex);
                if (resolved == null) {
                    violations.add(dow.name() + " exercise #" + (idx + 1)
                            + " ('" + rawEx.getExerciseName() + "', id=" + rawEx.getExerciseId()
                            + ") does not match an active catalog exercise.");
                    continue;
                }
                if (!seenExerciseIds.add(resolved.getId())) {
                    violations.add("Duplicate exercise '" + resolved.getName() + "' (id=" + resolved.getId()
                            + ") repeated within " + dow.name() + " session.");
                    continue;
                }
                if (resolved.getCategory() == null) {
                    violations.add("Catalog exercise '" + resolved.getName() + "' is missing category/muscle group.");
                    continue;
                }

                AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview validatedEx =
                        validateAndBuildExercisePreview(dow, idx + 1, resolved, rawEx.getSets(),
                                rawEx.getReps(), rawEx.getHoldSeconds(), rawEx.getRestSeconds(),
                                rawEx.getNotes(), violations);
                if (validatedEx != null) {
                    exercisePreviews.add(validatedEx);
                }
            }

            sessionPreviews.add(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                    .dayOfWeek(dow)
                    .title(dayTitle)
                    .restDay(false)
                    .exercises(exercisePreviews)
                    .build());
        }

        validateWeeklyFrequency(trainingDays, constraints, violations);
        validateCrossDayRepetition(sessionPreviews, catalogIndex, violations);

        int durationWeeks = resolveAndValidateDurationWeeks(plan.getDurationWeeks(), constraints, violations);
        LocalDate startDate = resolveStartDate(plan.getSuggestedStartDate(), defaultStartDate, violations);
        WorkoutSplitType splitType = resolveSplitType(plan.getSplitType());

        AiWorkoutPlanPreviewResponse preview = AiWorkoutPlanPreviewResponse.builder()
                .planName(sanitizePlanName(plan.getPlanName()))
                .description(sanitizeDescription(plan.getDescription()))
                .durationWeeks(durationWeeks)
                .startDate(startDate)
                .splitType(splitType)
                .sessions(sessionPreviews)
                .metadata(AiWorkoutPlanPreviewResponse.AiWorkoutMetadata.builder().build())
                .build();

        return new ValidationResult(preview, List.copyOf(violations));
    }

    public void validatePreviewForCommit(
            AiWorkoutPlanPreviewResponse preview,
            List<Exercise> catalog,
            LocalDate fallbackStartDate
    ) {
        List<String> violations = new ArrayList<>();
        if (preview == null) {
            throw new AiWorkoutPlanValidationException("Workout plan preview is required.", List.of("Plan cannot be null."));
        }

        CatalogIndex catalogIndex = CatalogIndex.from(catalog);
        AppProperties.WorkoutPlanner cfg = appProperties.getWorkoutPlanner();
        int hardMax = Math.min(HARD_MAX_EXERCISES_PER_SESSION, Math.max(1, cfg.getMaxExercisesPerSession()));

        List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> sessions = preview.getSessions();
        if (sessions == null || sessions.isEmpty()) {
            throw new AiWorkoutPlanValidationException(
                    "Workout plan must include at least one day.",
                    List.of("Sessions list is empty.")
            );
        }
        if (sessions.size() > DAYS_IN_WEEK) {
            violations.add("Weekly plan cannot contain more than 7 days (found " + sessions.size() + ").");
        }

        Set<WorkoutDayOfWeek> seenDays = EnumSet.noneOf(WorkoutDayOfWeek.class);
        int trainingDays = 0;

        for (AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview session : sessions) {
            if (session == null || session.getDayOfWeek() == null) {
                violations.add("Every workout session must specify a valid weekday.");
                continue;
            }
            if (!seenDays.add(session.getDayOfWeek())) {
                violations.add("Duplicate weekday '" + session.getDayOfWeek().name() + "' in workout plan.");
            }
            if (session.isRestDay()) {
                if (session.getExercises() != null && !session.getExercises().isEmpty()) {
                    violations.add(session.getDayOfWeek().name() + " is a rest day and must not contain exercises.");
                }
                continue;
            }
            trainingDays++;
            List<AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview> exercises = session.getExercises();
            if (exercises == null || exercises.isEmpty()) {
                violations.add(session.getDayOfWeek().name() + " must include at least 1 exercise or be marked as a rest day.");
                continue;
            }
            if (exercises.size() > hardMax) {
                violations.add(session.getDayOfWeek().name() + " contains " + exercises.size()
                        + " exercises, exceeding maximum of " + hardMax + " per session.");
            }
            Set<Long> seenIds = new HashSet<>();
            for (int i = 0; i < exercises.size(); i++) {
                AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview ex = exercises.get(i);
                if (ex == null || ex.getExerciseId() == null) {
                    violations.add(session.getDayOfWeek().name() + " exercise #" + (i + 1) + " is missing exerciseId.");
                    continue;
                }
                Exercise catalogEntry = catalogIndex.byId.get(ex.getExerciseId());
                if (catalogEntry == null) {
                    violations.add(session.getDayOfWeek().name() + " references invalid or inactive exerciseId=" + ex.getExerciseId() + ".");
                    continue;
                }
                if (!seenIds.add(catalogEntry.getId())) {
                    violations.add("Duplicate exercise '" + catalogEntry.getName() + "' (id=" + catalogEntry.getId()
                            + ") repeated within " + session.getDayOfWeek().name() + " session.");
                    continue;
                }
                validateAndBuildExercisePreview(
                        session.getDayOfWeek(),
                        i + 1,
                        catalogEntry,
                        ex.getSets(),
                        ex.getReps(),
                        ex.getHoldSeconds(),
                        ex.getRestSeconds(),
                        ex.getNotes(),
                        violations
                );
            }
        }

        if (trainingDays < cfg.getMinTrainingDaysPerWeek() || trainingDays > cfg.getMaxTrainingDaysPerWeek()) {
            violations.add("Weekly training days (" + trainingDays + ") must be between "
                    + cfg.getMinTrainingDaysPerWeek() + " and " + cfg.getMaxTrainingDaysPerWeek() + ".");
        }

        int durationWeeks = preview.getDurationWeeks() != null ? preview.getDurationWeeks() : 4;
        if (durationWeeks < cfg.getMinDurationWeeks() || durationWeeks > cfg.getMaxDurationWeeks()) {
            violations.add("Plan duration (" + durationWeeks + " weeks) must be between "
                    + cfg.getMinDurationWeeks() + " and " + cfg.getMaxDurationWeeks() + ".");
        }

        LocalDate startDate = preview.getStartDate() != null ? preview.getStartDate() : fallbackStartDate;
        if (startDate == null) {
            violations.add("A valid workout plan startDate is required.");
        }

        if (!violations.isEmpty()) {
            throw new AiWorkoutPlanValidationException(String.join(" ", violations), violations);
        }
    }

    private void validateWeeklyFrequency(
            int trainingDays,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints,
            List<String> violations
    ) {
        AppProperties.WorkoutPlanner cfg = appProperties.getWorkoutPlanner();
        if (trainingDays < cfg.getMinTrainingDaysPerWeek() || trainingDays > cfg.getMaxTrainingDaysPerWeek()) {
            violations.add("Weekly training days (" + trainingDays + ") must be between "
                    + cfg.getMinTrainingDaysPerWeek() + " and " + cfg.getMaxTrainingDaysPerWeek() + ".");
        }
        if (constraints.requestedTrainingDaysPerWeek().isPresent()) {
            int expected = constraints.requestedTrainingDaysPerWeek().getAsInt();
            if (trainingDays != expected) {
                violations.add("Requested exactly " + expected + " training days per week, but plan contains "
                        + trainingDays + " training days.");
            }
        }
    }

    private void validateCrossDayRepetition(
            List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> sessions,
            CatalogIndex catalogIndex,
            List<String> violations
    ) {
        List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> trainingDays = sessions.stream()
                .filter(s -> !s.isRestDay() && !s.getExercises().isEmpty())
                .toList();
        if (trainingDays.size() < 2) {
            return;
        }
        for (int i = 0; i < trainingDays.size() - 1; i++) {
            AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview current = trainingDays.get(i);
            AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview next = trainingDays.get(i + 1);
            if (Math.abs(next.getDayOfWeek().getOrder() - current.getDayOfWeek().getOrder()) != 1) {
                continue;
            }
            Set<Long> currentIds = new HashSet<>();
            for (var ex : current.getExercises()) {
                currentIds.add(ex.getExerciseId());
            }
            int identicalCount = 0;
            for (var ex : next.getExercises()) {
                if (currentIds.contains(ex.getExerciseId())) {
                    identicalCount++;
                    Exercise catalogEntry = catalogIndex.byId.get(ex.getExerciseId());
                    if (catalogEntry != null && !isIntentionalRepeatAllowed(catalogEntry)) {
                        violations.add("Consecutive training days " + current.getDayOfWeek().name() + " and "
                                + next.getDayOfWeek().name() + " unnecessarily repeat compound exercise '"
                                + catalogEntry.getName() + "'.");
                    }
                }
            }
            if (identicalCount > 0 && identicalCount == next.getExercises().size() && identicalCount == current.getExercises().size()) {
                violations.add("Consecutive training days " + current.getDayOfWeek().name() + " and "
                        + next.getDayOfWeek().name() + " are completely identical.");
            }
        }
    }

    private boolean isIntentionalRepeatAllowed(Exercise exercise) {
        return exercise.getCategory() == ExerciseCategory.SKILL
                || exercise.getCategory() == ExerciseCategory.CORE
                || exercise.getCategory() == ExerciseCategory.ISOMETRIC;
    }

    private AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview validateAndBuildExercisePreview(
            WorkoutDayOfWeek day,
            int orderIndex,
            Exercise catalogEntry,
            Integer rawSets,
            Integer rawReps,
            Integer rawHoldSeconds,
            Integer rawRestSeconds,
            String notes,
            List<String> violations
    ) {
        if (rawSets == null || rawSets < 1 || rawSets > MAX_SETS) {
            violations.add(day.name() + " exercise #" + orderIndex + " ('" + catalogEntry.getName()
                    + "') has invalid sets=" + rawSets + " (allowed 1-" + MAX_SETS + ").");
            return null;
        }
        int rest = rawRestSeconds != null ? rawRestSeconds : 90;
        if (rest < 0 || rest > MAX_REST_SECONDS) {
            violations.add(day.name() + " exercise #" + orderIndex + " ('" + catalogEntry.getName()
                    + "') has invalid rest_seconds=" + rest + " (allowed 0-" + MAX_REST_SECONDS + ").");
            return null;
        }

        boolean staticHold = isStaticHoldExercise(catalogEntry);
        Integer normalizedReps = (rawReps != null && rawReps > 0) ? rawReps : null;
        Integer normalizedHold = (rawHoldSeconds != null && rawHoldSeconds > 0) ? rawHoldSeconds : null;

        if (staticHold) {
            // Static hold exercises cannot persist positive reps alongside hold duration.
            normalizedReps = null;
            if (normalizedHold == null) {
                violations.add(day.name() + " static hold exercise #" + orderIndex + " ('" + catalogEntry.getName()
                        + "') must specify hold_seconds (1-" + MAX_HOLD_SECONDS + ").");
                return null;
            }
            if (normalizedHold > MAX_HOLD_SECONDS) {
                violations.add(day.name() + " exercise #" + orderIndex + " ('" + catalogEntry.getName()
                        + "') hold_seconds=" + normalizedHold + " exceeds max " + MAX_HOLD_SECONDS + ".");
                return null;
            }
        } else {
            normalizedHold = null;
            if (normalizedReps == null) {
                violations.add(day.name() + " rep-based exercise #" + orderIndex + " ('" + catalogEntry.getName()
                        + "') must specify reps (1-" + MAX_REPS + ").");
                return null;
            }
            if (normalizedReps > MAX_REPS) {
                violations.add(day.name() + " exercise #" + orderIndex + " ('" + catalogEntry.getName()
                        + "') reps=" + normalizedReps + " exceeds max " + MAX_REPS + ".");
                return null;
            }
        }

        return AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview.builder()
                .exerciseId(catalogEntry.getId())
                .exerciseName(catalogEntry.getName())
                .exerciseCode(catalogEntry.getCode())
                .category(catalogEntry.getCategory().name())
                .sets(rawSets)
                .reps(normalizedReps)
                .holdSeconds(normalizedHold)
                .restSeconds(rest)
                .notes(notes)
                .requiresReview(false)
                .build();
    }

    /**
     * Exact catalog resolution:
     * - Prevents merging distinct exercises with similar names (e.g. Tuck Planche vs Straddle Planche vs Planche).
     * - Never silently falls back to an unrelated exercise like catalog.getFirst().
     */
    private Exercise resolveExactCatalogExercise(Long id, String rawName, CatalogIndex index) {
        Exercise byId = id != null ? index.byId.get(id) : null;
        Exercise byExactName = resolveByNormalizedKey(rawName, index);

        if (byId != null && byExactName != null) {
            // If ID and exact catalog name disagree, trust exact catalog name when present.
            return byId.getId().equals(byExactName.getId()) ? byId : byExactName;
        }
        if (byId != null) {
            return (rawName == null || rawName.isBlank()) ? byId : null;
        }
        return byExactName;
    }

    private Exercise resolveByNormalizedKey(String rawName, CatalogIndex index) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String lower = rawName.toLowerCase(Locale.US).trim();
        Exercise exact = index.byExactNameOrCode.get(lower);
        if (exact != null) {
            return exact;
        }
        String compact = normalizeToken(lower);
        return index.byCompactToken.get(compact);
    }

    public static boolean isStaticHoldExercise(Exercise ex) {
        if (ex == null) {
            return false;
        }
        return isStaticHoldExercise(
                ex.getCategory() != null ? ex.getCategory().name() : null,
                ex.getCode(),
                ex.getName()
        );
    }

    public static boolean isStaticHoldExercise(String category, String code, String name) {
        String lowerNameCode = ((code != null ? code : "") + " " + (name != null ? name : "")).toLowerCase(Locale.US);
        // Dynamic compounds using planche/lever positions remain rep-based when categorized PUSH/PULL.
        if ((ExerciseCategory.PUSH.name().equalsIgnoreCase(category) || ExerciseCategory.PULL.name().equalsIgnoreCase(category))
                && (lowerNameCode.contains("push") || lowerNameCode.contains("pull") || lowerNameCode.contains("dip"))) {
            return false;
        }
        if (ExerciseCategory.SKILL.name().equalsIgnoreCase(category)
                || ExerciseCategory.ISOMETRIC.name().equalsIgnoreCase(category)) {
            return true;
        }
        return lowerNameCode.contains("planche")
                || lowerNameCode.contains("front lever")
                || lowerNameCode.contains("front_lever")
                || lowerNameCode.contains("back lever")
                || lowerNameCode.contains("handstand")
                || lowerNameCode.contains("l-sit")
                || lowerNameCode.contains("l_sit")
                || lowerNameCode.contains("hold")
                || lowerNameCode.contains("plank")
                || lowerNameCode.contains("flag");
    }

    private int resolveAndValidateDurationWeeks(
            Integer rawDurationWeeks,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints,
            List<String> violations
    ) {
        AppProperties.WorkoutPlanner cfg = appProperties.getWorkoutPlanner();
        int weeks = rawDurationWeeks != null && rawDurationWeeks > 0
                ? rawDurationWeeks
                : constraints.targetDurationWeeks();
        if (weeks < cfg.getMinDurationWeeks() || weeks > cfg.getMaxDurationWeeks()) {
            violations.add("Plan duration (" + weeks + " weeks) must be between "
                    + cfg.getMinDurationWeeks() + " and " + cfg.getMaxDurationWeeks() + " weeks.");
        }
        return weeks;
    }

    private LocalDate resolveStartDate(String rawDate, LocalDate fallbackStartDate, List<String> violations) {
        if (rawDate != null && !rawDate.isBlank()) {
            try {
                return LocalDate.parse(rawDate.trim());
            } catch (Exception ex) {
                violations.add("Invalid suggested_start_date '" + rawDate + "'. Expected YYYY-MM-DD.");
            }
        }
        return fallbackStartDate != null
                ? fallbackStartDate
                : LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private WorkoutSplitType resolveSplitType(String rawSplit) {
        if (rawSplit == null || rawSplit.isBlank()) {
            return WorkoutSplitType.CUSTOM;
        }
        try {
            return WorkoutSplitType.valueOf(rawSplit.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return WorkoutSplitType.CUSTOM;
        }
    }

    private boolean looksLikeRestTitle(String title) {
        String lower = title.toLowerCase(Locale.US);
        return lower.equals("rest") || lower.equals("rest day") || lower.contains("rest & recovery");
    }

    private String sanitizePlanName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "Structured Calisthenics Plan";
        }
        String trimmed = rawName.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private String sanitizeDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "Personalized structured calisthenics workout plan.";
        }
        String cleaned = rawDescription.replaceAll("(?i)\\b(google\\s+)?gemini(\\s+ai)?\\b", "AI").trim();
        return cleaned.isBlank() ? "Personalized structured calisthenics workout plan." : cleaned;
    }

    private String sanitizeDayTitle(String rawTitle, String fallback) {
        String value = (rawTitle == null || rawTitle.isBlank()) ? fallback : rawTitle.trim();
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private static String normalizeToken(String text) {
        return text.toLowerCase(Locale.US)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final class CatalogIndex {
        private final Map<Long, Exercise> byId;
        private final Map<String, Exercise> byExactNameOrCode;
        private final Map<String, Exercise> byCompactToken;

        private CatalogIndex(
                Map<Long, Exercise> byId,
                Map<String, Exercise> byExactNameOrCode,
                Map<String, Exercise> byCompactToken
        ) {
            this.byId = byId;
            this.byExactNameOrCode = byExactNameOrCode;
            this.byCompactToken = byCompactToken;
        }

        private static CatalogIndex from(List<Exercise> catalog) {
            Map<Long, Exercise> byId = new HashMap<>();
            Map<String, Exercise> byExact = new HashMap<>();
            Map<String, Exercise> byCompact = new HashMap<>();
            if (catalog != null) {
                for (Exercise ex : catalog) {
                    if (ex == null || !ex.isActive() || ex.getId() == null) {
                        continue;
                    }
                    byId.put(ex.getId(), ex);
                    if (ex.getName() != null) {
                        String cleanName = ex.getName().toLowerCase(Locale.US).trim();
                        byExact.put(cleanName, ex);
                        byCompact.putIfAbsent(normalizeToken(cleanName), ex);
                    }
                    if (ex.getCode() != null) {
                        String cleanCode = ex.getCode().toLowerCase(Locale.US).trim();
                        byExact.putIfAbsent(cleanCode, ex);
                        byCompact.putIfAbsent(normalizeToken(cleanCode), ex);
                    }
                }
            }
            return new CatalogIndex(byId, byExact, byCompact);
        }
    }
}
