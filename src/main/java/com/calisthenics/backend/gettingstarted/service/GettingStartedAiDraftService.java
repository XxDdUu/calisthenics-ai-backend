package com.calisthenics.backend.gettingstarted.service;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.ModifyAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanValidator;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedDayRequest;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedExerciseRequest;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedDayResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedExerciseOperationRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedExerciseResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.service.impl.GettingStartedCampaignHelper;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates and edits 3-day Getting Started programs by reusing the existing
 * {@link AiWorkoutPlanService}, {@link GeminiApiClient}, {@link AiWorkoutPlanValidator},
 * and active {@link ExerciseRepository} catalog, with deterministic catalog-grounded
 * fallback when Gemini is not configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GettingStartedAiDraftService {

    private static final Pattern DAY_NUMBER_PATTERN = Pattern.compile("(?i)\\bday\\s*([123])\\b");
    private static final Pattern SETS_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})\\s*sets?\\b");
    private static final Pattern REPS_PATTERN = Pattern.compile("(?i)\\b(\\d{1,3})\\s*reps?\\b");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(?:hold|duration|for)\\s*(?:of\\s*)?(\\d{1,3})\\s*(?:s|sec|secs|seconds?)\\b");
    private static final Pattern REST_PATTERN = Pattern.compile("(?i)rest\\s*(?:time\\s*)?(?:of|to|=|:)?\\s*(\\d{1,3})\\s*(?:s|sec|secs|seconds?)\\b");
    private static final Pattern INSTRUCTIONS_PATTERN = Pattern.compile("(?i)instructions?\\s*(?:to|:|=|with)\\s*['\"]?([^'\".\\n]+)['\"]?");

    private final AiWorkoutPlanService aiWorkoutPlanService;
    private final GeminiApiClient geminiApiClient;
    private final ExerciseRepository exerciseRepository;
    private final GettingStartedCampaignHelper campaignHelper;
    private final AppProperties appProperties;

    private boolean isGeminiConfigured() {
        return geminiApiClient != null
                && appProperties != null
                && appProperties.getGemini() != null
                && appProperties.getGemini().getApiKey() != null
                && !appProperties.getGemini().getApiKey().isBlank();
    }

    /**
     * Generates a beginner-friendly 3-day full-body Getting Started program draft.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminGettingStartedProgramRequest generateDraftRequest(
            Long adminId,
            String instruction,
            String preferredTitle,
            String preferredDescription,
            TargetAudience targetAudience
    ) {
        String effectiveTitle = (preferredTitle != null && !preferredTitle.isBlank())
                ? preferredTitle.trim()
                : "Getting Started – 3 Days";
        String effectiveDescription = (preferredDescription != null && !preferredDescription.isBlank())
                ? preferredDescription.trim()
                : "Beginner-friendly 3-day full-body calisthenics onboarding program building foundational pushing, pulling, lower-body, and core control.";
        TargetAudience resolvedAudience = targetAudience != null ? targetAudience : TargetAudience.ALL_ATHLETES;

        List<Exercise> catalog = exerciseRepository.findByActiveTrue();

        if (isGeminiConfigured() && !catalog.isEmpty()) {
            try {
                String plannerPrompt = buildPlannerGenerationPrompt(instruction);
                GenerateAiWorkoutPlanRequest planReq = new GenerateAiWorkoutPlanRequest();
                planReq.setPrompt(plannerPrompt);
                planReq.setFitnessLevel("BEGINNER");
                planReq.setDurationWeeks(1);
                planReq.setPreferredSplit("FULL_BODY");

                AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(adminId, planReq);
                List<AdminGettingStartedDayRequest> mappedDays = extractThreeDaysFromPreview(preview, catalog);
                if (mappedDays.size() == 3) {
                    return AdminGettingStartedProgramRequest.builder()
                            .title(effectiveTitle)
                            .description(effectiveDescription)
                            .targetAudience(resolvedAudience)
                            .days(mappedDays)
                            .build();
                }
            } catch (Exception ex) {
                log.warn("Reused AI Workout Planner fallback for Getting Started draft generation: {}", ex.getMessage());
            }
        }

        return buildDeterministicBeginnerProgram(effectiveTitle, effectiveDescription, resolvedAudience, catalog, instruction);
    }

    /**
     * Applies natural language edit instructions against an existing Getting Started program,
     * reusing {@link AiWorkoutPlanService#modifyPlan} when Gemini is configured or deterministic
     * catalog transformations when offline.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminGettingStartedProgramRequest applyNaturalLanguageEdit(
            Long adminId,
            GettingStartedProgram existing,
            GettingStartedProgramResponse existingResponse,
            String instruction
    ) {
        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        String lower = instruction != null ? instruction.toLowerCase(Locale.US) : "";

        // Apply deterministic structural/focus updates directly if clearly expressed or when Gemini is off
        Optional<AdminGettingStartedProgramRequest> deterministic =
                tryDeterministicNaturalLanguageEdit(existing, instruction, lower, catalog);

        if (deterministic.isPresent() && (!isGeminiConfigured() || isFocusedDeterministicEdit(lower))) {
            return deterministic.get();
        }

        if (isGeminiConfigured() && !catalog.isEmpty()) {
            try {
                AiWorkoutPlanPreviewResponse currentPreview = toWeeklyPreviewForPlanner(existingResponse, catalog);
                ModifyAiWorkoutPlanRequest modifyReq = new ModifyAiWorkoutPlanRequest();
                modifyReq.setCurrentPlan(currentPreview);
                modifyReq.setPrompt("Keep exactly 3 training days (MONDAY=Day 1, WEDNESDAY=Day 2, FRIDAY=Day 3) and 4 rest days. Apply this beginner Getting Started edit: " + instruction);

                AiWorkoutPlanPreviewResponse modified = aiWorkoutPlanService.modifyPlan(adminId, modifyReq);
                List<AdminGettingStartedDayRequest> updatedDays = extractThreeDaysFromPreview(modified, catalog);
                if (updatedDays.size() == 3) {
                    AdminGettingStartedProgramRequest req = campaignHelper.toAdminRequest(existing);
                    req.setDays(updatedDays);
                    return req;
                }
            } catch (Exception ex) {
                log.warn("AI Workout Planner modify fallback for Getting Started edit: {}", ex.getMessage());
            }
        }

        if (deterministic.isPresent()) {
            return deterministic.get();
        }

        throw new IllegalArgumentException(
                "Could not interpret program modification instruction: '" + instruction
                        + "'. Specify Day 1/2/3 focus (push, pull, legs, core), exercise add/remove/reorder, or sets/reps/duration/rest."
        );
    }

    private boolean isFocusedDeterministicEdit(String lower) {
        return lower.contains("focus") || lower.contains("add ") || lower.contains("remove ")
                || lower.contains("delete ") || lower.contains("reorder") || lower.contains("swap")
                || lower.contains("move ") || REPS_PATTERN.matcher(lower).find()
                || SETS_PATTERN.matcher(lower).find() || DURATION_PATTERN.matcher(lower).find()
                || REST_PATTERN.matcher(lower).find();
    }

    private Optional<AdminGettingStartedProgramRequest> tryDeterministicNaturalLanguageEdit(
            GettingStartedProgram existing,
            String instruction,
            String lower,
            List<Exercise> catalog
    ) {
        if (instruction == null || instruction.isBlank()) {
            return Optional.empty();
        }
        AdminGettingStartedProgramRequest draft = campaignHelper.toAdminRequest(existing);
        Integer dayNum = extractDayNumber(lower).orElse(1);
        AdminGettingStartedDayRequest targetDay = draft.getDays().stream()
                .filter(d -> Objects.equals(d.getDayNumber(), dayNum))
                .findFirst()
                .orElse(null);
        if (targetDay == null) {
            return Optional.empty();
        }

        // 1. Day focus change e.g. "Make Day 1 focused on pushing movements"
        if (lower.contains("focus") || lower.contains("pushing") || lower.contains("push movement")
                || lower.contains("pulling") || lower.contains("pull movement")
                || lower.contains("leg") || lower.contains("lower body") || lower.contains("core")) {
            ExerciseCategory focusCategory = detectFocusCategory(lower);
            if (focusCategory != null) {
                List<AdminGettingStartedExerciseRequest> focusedExercises = buildExercisesForCategory(focusCategory, catalog, dayNum);
                targetDay.setTitle(dayFocusTitle(dayNum, focusCategory));
                targetDay.setDescription("Day " + dayNum + " focus: " + focusCategory.name().toLowerCase(Locale.US) + " beginner movements.");
                targetDay.setExercises(focusedExercises);
                return Optional.of(draft);
            }
        }

        // 2. Remove exercise from Day X
        if (lower.contains("remove ") || lower.contains("delete ")) {
            Optional<AdminGettingStartedExerciseRequest> match = findMentionedExistingExercise(targetDay.getExercises(), lower);
            if (match.isPresent()) {
                GettingStartedExerciseOperationRequest op = GettingStartedExerciseOperationRequest.builder()
                        .operation("REMOVE_EXERCISE")
                        .dayNumber(dayNum)
                        .exerciseName(match.get().getExerciseName())
                        .build();
                return Optional.of(campaignHelper.applyOperations(existing, List.of(op), exerciseRepository, null, null, null));
            }
        }

        // 3. Add exercise to Day X
        if (lower.contains("add ")) {
            Exercise catalogMatch = findMentionedCatalogExercise(catalog, lower).orElse(null);
            String exerciseName = catalogMatch != null ? catalogMatch.getName() : extractAddedExerciseName(instruction);
            if (exerciseName != null && !exerciseName.isBlank()) {
                GettingStartedExerciseOperationRequest op = GettingStartedExerciseOperationRequest.builder()
                        .operation("ADD_EXERCISE")
                        .dayNumber(dayNum)
                        .exerciseId(catalogMatch != null ? catalogMatch.getId() : null)
                        .exerciseName(exerciseName)
                        .sets(extractInt(SETS_PATTERN, instruction))
                        .reps(extractInt(REPS_PATTERN, instruction))
                        .durationSeconds(extractInt(DURATION_PATTERN, instruction))
                        .restSeconds(extractInt(REST_PATTERN, instruction))
                        .instructions(extractInstructions(instruction))
                        .build();
                return Optional.of(campaignHelper.applyOperations(existing, List.of(op), exerciseRepository, null, null, null));
            }
        }

        // 4. Reorder / move exercise first or reverse
        if (lower.contains("reorder") || lower.contains("reverse") || lower.contains("swap") || lower.contains("move ")) {
            List<AdminGettingStartedExerciseRequest> current = targetDay.getExercises();
            Optional<AdminGettingStartedExerciseRequest> match = findMentionedExistingExercise(current, lower);
            if (lower.contains("reverse") && current.size() > 1) {
                Collections.reverse(current);
                reindex(current);
                return Optional.of(draft);
            }
            if (match.isPresent()) {
                int targetPos = (lower.contains("first") || lower.contains("top") || lower.contains("start") || lower.contains("before")) ? 0 : current.size() - 1;
                GettingStartedExerciseOperationRequest op = GettingStartedExerciseOperationRequest.builder()
                        .operation("REORDER_EXERCISES")
                        .dayNumber(dayNum)
                        .exerciseName(match.get().getExerciseName())
                        .newOrder(targetPos)
                        .build();
                return Optional.of(campaignHelper.applyOperations(existing, List.of(op), exerciseRepository, null, null, null));
            }
        }

        // 5. Modify sets / reps / duration / rest / instructions
        Integer sets = extractInt(SETS_PATTERN, instruction);
        Integer reps = extractInt(REPS_PATTERN, instruction);
        Integer duration = extractInt(DURATION_PATTERN, instruction);
        Integer rest = extractInt(REST_PATTERN, instruction);
        String notes = extractInstructions(instruction);
        if (sets != null || reps != null || duration != null || rest != null || notes != null) {
            Optional<AdminGettingStartedExerciseRequest> match = findMentionedExistingExercise(targetDay.getExercises(), lower);
            AdminGettingStartedExerciseRequest targetEx = match.orElse(
                    targetDay.getExercises().isEmpty() ? null : targetDay.getExercises().get(0));
            if (targetEx != null) {
                GettingStartedExerciseOperationRequest op = GettingStartedExerciseOperationRequest.builder()
                        .operation("UPDATE_EXERCISE")
                        .dayNumber(dayNum)
                        .exerciseName(targetEx.getExerciseName())
                        .sets(sets)
                        .reps(reps)
                        .durationSeconds(duration)
                        .restSeconds(rest)
                        .instructions(notes)
                        .build();
                return Optional.of(campaignHelper.applyOperations(existing, List.of(op), exerciseRepository, null, null, null));
            }
        }

        return Optional.empty();
    }

    private ExerciseCategory detectFocusCategory(String lower) {
        if (lower.contains("push")) return ExerciseCategory.PUSH;
        if (lower.contains("pull")) return ExerciseCategory.PULL;
        if (lower.contains("leg") || lower.contains("lower")) return ExerciseCategory.LEGS;
        if (lower.contains("core") || lower.contains("ab")) return ExerciseCategory.CORE;
        return null;
    }

    private String dayFocusTitle(int dayNum, ExerciseCategory category) {
        String focus = switch (category) {
            case PUSH -> "Day " + dayNum + ": Pushing Movements Foundation";
            case PULL -> "Day " + dayNum + ": Pulling Strength & Posture";
            case LEGS -> "Day " + dayNum + ": Lower Body Power & Stability";
            case CORE -> "Day " + dayNum + ": Core Stability & Control";
            default -> "Day " + dayNum + ": Full-Body Movement Practice";
        };
        return focus;
    }

    private List<AdminGettingStartedExerciseRequest> buildExercisesForCategory(
            ExerciseCategory category,
            List<Exercise> catalog,
            int dayNum
    ) {
        List<Exercise> matched = catalog.stream()
                .filter(ex -> ex.getCategory() == category)
                .sorted(Comparator.comparingInt(this::beginnerSortKey))
                .toList();

        List<AdminGettingStartedExerciseRequest> list = new ArrayList<>();
        if (!matched.isEmpty()) {
            int limit = Math.min(3, matched.size());
            for (int i = 0; i < limit; i++) {
                list.add(toExerciseRequest(matched.get(i), i, "Focus on controlled tempo for " + category.name().toLowerCase(Locale.US) + " technique."));
            }
            if (list.size() < 2) {
                Exercise extraCore = pickByCategories(catalog, Set.of(ExerciseCategory.CORE, category),
                        Set.of(matched.get(0).getId()));
                if (extraCore != null) {
                    list.add(toExerciseRequest(extraCore, list.size(), "Brace core throughout each rep."));
                }
            }
            return list;
        }

        return defaultFallbackExercisesForCategory(category, dayNum);
    }

    private AdminGettingStartedProgramRequest buildDeterministicBeginnerProgram(
            String title,
            String description,
            TargetAudience audience,
            List<Exercise> catalog,
            String instruction
    ) {
        String lower = instruction != null ? instruction.toLowerCase(Locale.US) : "";
        boolean day1Push = lower.contains("day 1") && lower.contains("push");

        List<AdminGettingStartedDayRequest> days = new ArrayList<>();
        if (day1Push) {
            days.add(AdminGettingStartedDayRequest.builder()
                    .dayNumber(1)
                    .title(dayFocusTitle(1, ExerciseCategory.PUSH))
                    .description("Day 1 emphasizes beginner-friendly pushing mechanics.")
                    .exercises(buildExercisesForCategory(ExerciseCategory.PUSH, catalog, 1))
                    .build());
        } else {
            days.add(buildFullBodyDay(1, "Day 1: Full-Body Foundation",
                    "Learn fundamental push, leg, and core movement patterns.",
                    List.of(ExerciseCategory.PUSH, ExerciseCategory.LEGS, ExerciseCategory.CORE), catalog,
                    defaultFallbackExercisesForDay(1)));
        }

        days.add(buildFullBodyDay(2, "Day 2: Pulling & Posterior Chain",
                "Build balanced upper-back pulling strength and hip stability.",
                List.of(ExerciseCategory.PULL, ExerciseCategory.LEGS, ExerciseCategory.CORE), catalog,
                defaultFallbackExercisesForDay(2)));

        days.add(buildFullBodyDay(3, "Day 3: Full-Body Control & Endurance",
                "Combine push, pull, squat, and core control with steady breathing.",
                List.of(ExerciseCategory.PUSH, ExerciseCategory.PULL, ExerciseCategory.CORE), catalog,
                defaultFallbackExercisesForDay(3)));

        return AdminGettingStartedProgramRequest.builder()
                .title(title)
                .description(description)
                .targetAudience(audience)
                .days(days)
                .build();
    }

    private AdminGettingStartedDayRequest buildFullBodyDay(
            int dayNumber,
            String title,
            String description,
            List<ExerciseCategory> categories,
            List<Exercise> catalog,
            List<AdminGettingStartedExerciseRequest> fallback
    ) {
        if (catalog == null || catalog.isEmpty()) {
            return AdminGettingStartedDayRequest.builder()
                    .dayNumber(dayNumber)
                    .title(title)
                    .description(description)
                    .exercises(fallback)
                    .build();
        }
        Set<Long> usedIds = new HashSet<>();
        List<AdminGettingStartedExerciseRequest> exercises = new ArrayList<>();
        for (ExerciseCategory category : categories) {
            Exercise picked = pickByCategories(catalog, Set.of(category), usedIds);
            if (picked != null) {
                usedIds.add(picked.getId());
                exercises.add(toExerciseRequest(picked, exercises.size(), "Move with smooth, full range of motion."));
            }
        }
        if (exercises.isEmpty()) {
            Exercise anyFirst = catalog.stream().min(Comparator.comparingInt(this::beginnerSortKey)).orElse(null);
            if (anyFirst != null) {
                exercises.add(toExerciseRequest(anyFirst, 0, "Focus on beginner-friendly tempo and clean posture."));
            } else {
                exercises.addAll(fallback);
            }
        }
        reindex(exercises);
        return AdminGettingStartedDayRequest.builder()
                .dayNumber(dayNumber)
                .title(title)
                .description(description)
                .exercises(exercises)
                .build();
    }

    private Exercise pickByCategories(List<Exercise> catalog, Set<ExerciseCategory> categories, Set<Long> excludeIds) {
        return catalog.stream()
                .filter(ex -> !excludeIds.contains(ex.getId()) && categories.contains(ex.getCategory()))
                .min(Comparator.comparingInt(this::beginnerSortKey))
                .orElse(catalog.stream()
                        .filter(ex -> !excludeIds.contains(ex.getId()))
                        .min(Comparator.comparingInt(this::beginnerSortKey))
                        .orElse(null));
    }

    private int beginnerSortKey(Exercise ex) {
        if (ex.getDifficulty() == null) return 2;
        return switch (ex.getDifficulty()) {
            case BEGINNER -> 0;
            case INTERMEDIATE -> 1;
            case ADVANCED -> 2;
            case ELITE -> 3;
        };
    }

    private AdminGettingStartedExerciseRequest toExerciseRequest(Exercise ex, int order, String instructions) {
        boolean staticHold = AiWorkoutPlanValidator.isStaticHoldExercise(ex);
        return AdminGettingStartedExerciseRequest.builder()
                .exerciseId(ex.getId())
                .exerciseName(ex.getName())
                .sets(3)
                .reps(staticHold ? null : 10)
                .durationSeconds(staticHold ? 25 : null)
                .restSeconds(60)
                .instructions(instructions)
                .displayOrder(order)
                .build();
    }

    private List<AdminGettingStartedExerciseRequest> defaultFallbackExercisesForCategory(ExerciseCategory category, int dayNum) {
        return switch (category) {
            case PUSH -> List.of(
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Incline Push-Ups").sets(3).reps(10).restSeconds(60).instructions("Hands elevated if needed; lower chest under control.").displayOrder(0).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Knee Push-Ups").sets(3).reps(8).restSeconds(60).instructions("Keep ribs down and elbows at ~45 degrees.").displayOrder(1).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Pike Shoulder Tap Hold").sets(3).durationSeconds(25).restSeconds(60).instructions("Push the floor away with straight arms.").displayOrder(2).build()
            );
            case PULL -> List.of(
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Inverted Rows").sets(3).reps(8).restSeconds(75).instructions("Pull chest to bar/table edge and pause 1 second.").displayOrder(0).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Scapular Pull-Ups").sets(3).reps(8).restSeconds(60).instructions("Depress shoulder blades smoothly without bending elbows.").displayOrder(1).build()
            );
            case LEGS -> List.of(
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Bodyweight Squats").sets(3).reps(12).restSeconds(60).instructions("Sit hips back and keep heels planted.").displayOrder(0).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Glute Bridges").sets(3).reps(12).restSeconds(45).instructions("Squeeze glutes cleanly at top for 1 second.").displayOrder(1).build()
            );
            default -> defaultFallbackExercisesForDay(dayNum);
        };
    }

    private List<AdminGettingStartedExerciseRequest> defaultFallbackExercisesForDay(int dayNumber) {
        return switch (dayNumber) {
            case 1 -> List.of(
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Incline Push-Ups").sets(3).reps(10).restSeconds(60).instructions("Keep body straight and elbows ~45 degrees.").displayOrder(0).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Bodyweight Squats").sets(3).reps(12).restSeconds(60).instructions("Descend with control, chest lifted.").displayOrder(1).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Forearm Plank").sets(3).durationSeconds(30).restSeconds(45).instructions("Brace glutes and abs without sagging hips.").displayOrder(2).build()
            );
            case 2 -> List.of(
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Inverted Rows").sets(3).reps(8).restSeconds(75).instructions("Squeeze shoulder blades together at top.").displayOrder(0).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Glute Bridges").sets(3).reps(12).restSeconds(60).instructions("Drive through heels and control the lowering phase.").displayOrder(1).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Dead Bug").sets(3).reps(10).restSeconds(45).instructions("Keep lower back pressed softly against floor.").displayOrder(2).build()
            );
            default -> List.of(
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Push-Ups").sets(3).reps(8).restSeconds(60).instructions("Scale on knees if form breaks.").displayOrder(0).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Reverse Lunges").sets(3).reps(10).restSeconds(60).instructions("Step gently and push back through front heel.").displayOrder(1).build(),
                    AdminGettingStartedExerciseRequest.builder().exerciseName("Hollow Body Hold").sets(3).durationSeconds(25).restSeconds(60).instructions("Keep ribs tucked and lower back flat.").displayOrder(2).build()
            );
        };
    }

    private String buildPlannerGenerationPrompt(String instruction) {
        String extra = (instruction != null && !instruction.isBlank()) ? " Special instructions: " + instruction.trim() : "";
        return "Create a beginner-friendly 3-day full-body calisthenics onboarding workout program (3 training days/week)."
                + extra
                + " Focus on accessible foundational exercises, safe sets (2-3), reps (8-12) or holds (20-35s), and 60s rest.";
    }

    private List<AdminGettingStartedDayRequest> extractThreeDaysFromPreview(
            AiWorkoutPlanPreviewResponse preview,
            List<Exercise> catalog
    ) {
        if (preview == null || preview.getSessions() == null) {
            return Collections.emptyList();
        }
        List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> activeSessions = preview.getSessions().stream()
                .filter(s -> s != null && !s.isRestDay() && s.getExercises() != null && !s.getExercises().isEmpty())
                .limit(3)
                .toList();
        if (activeSessions.size() != 3) {
            return Collections.emptyList();
        }
        Map<Long, Exercise> byId = new HashMap<>();
        for (Exercise ex : catalog) {
            byId.put(ex.getId(), ex);
        }

        List<AdminGettingStartedDayRequest> days = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview session = activeSessions.get(i);
            List<AdminGettingStartedExerciseRequest> exReqs = new ArrayList<>();
            int order = 0;
            for (AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview ex : session.getExercises()) {
                Exercise catalogEx = ex.getExerciseId() != null ? byId.get(ex.getExerciseId()) : null;
                boolean staticHold = (catalogEx != null && AiWorkoutPlanValidator.isStaticHoldExercise(catalogEx))
                        || (ex.getReps() == null && ex.getHoldSeconds() != null);
                exReqs.add(AdminGettingStartedExerciseRequest.builder()
                        .exerciseId(ex.getExerciseId())
                        .exerciseName(ex.getExerciseName())
                        .sets(ex.getSets() != null ? ex.getSets() : 3)
                        .reps(staticHold ? null : (ex.getReps() != null ? ex.getReps() : 10))
                        .durationSeconds(staticHold ? (ex.getHoldSeconds() != null ? ex.getHoldSeconds() : 25) : null)
                        .restSeconds(ex.getRestSeconds() != null ? ex.getRestSeconds() : 60)
                        .instructions(ex.getNotes())
                        .displayOrder(order++)
                        .build());
            }
            days.add(AdminGettingStartedDayRequest.builder()
                    .dayNumber(i + 1)
                    .title(session.getTitle() != null && !session.getTitle().isBlank() ? session.getTitle() : "Day " + (i + 1))
                    .description("AI-crafted beginner session for Day " + (i + 1))
                    .exercises(exReqs)
                    .build());
        }
        return days;
    }

    private AiWorkoutPlanPreviewResponse toWeeklyPreviewForPlanner(
            GettingStartedProgramResponse existing,
            List<Exercise> catalog
    ) {
        Map<Long, Exercise> byId = new HashMap<>();
        for (Exercise ex : catalog) {
            byId.put(ex.getId(), ex);
        }
        List<WorkoutDayOfWeek> trainingWeekdays = List.of(WorkoutDayOfWeek.MONDAY, WorkoutDayOfWeek.WEDNESDAY, WorkoutDayOfWeek.FRIDAY);
        Map<WorkoutDayOfWeek, AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> mapped = new EnumMap<>(WorkoutDayOfWeek.class);

        List<GettingStartedDayResponse> days = existing.getDays() != null ? existing.getDays() : Collections.emptyList();
        for (int i = 0; i < Math.min(3, days.size()); i++) {
            GettingStartedDayResponse d = days.get(i);
            WorkoutDayOfWeek dow = trainingWeekdays.get(i);
            List<AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview> exList = new ArrayList<>();
            if (d.getExercises() != null) {
                for (GettingStartedExerciseResponse ex : d.getExercises()) {
                    Exercise cat = ex.getExerciseId() != null ? byId.get(ex.getExerciseId()) : null;
                    if (cat == null && !catalog.isEmpty()) {
                        cat = catalog.get(0);
                    }
                    if (cat == null) continue;
                    exList.add(AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview.builder()
                            .exerciseId(cat.getId())
                            .exerciseName(cat.getName())
                            .exerciseCode(cat.getCode())
                            .category(cat.getCategory() != null ? cat.getCategory().name() : null)
                            .sets(ex.getSets())
                            .reps(ex.getReps())
                            .holdSeconds(ex.getDurationSeconds())
                            .restSeconds(ex.getRestSeconds())
                            .notes(ex.getInstructions())
                            .build());
                }
            }
            mapped.put(dow, AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                    .dayOfWeek(dow)
                    .title(d.getTitle())
                    .restDay(false)
                    .exercises(exList)
                    .build());
        }

        List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> allWeek = new ArrayList<>();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            allWeek.add(mapped.getOrDefault(dow, AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                    .dayOfWeek(dow)
                    .title("Rest Day")
                    .restDay(true)
                    .exercises(Collections.emptyList())
                    .build()));
        }

        return AiWorkoutPlanPreviewResponse.builder()
                .planName(existing.getTitle())
                .description(existing.getDescription())
                .durationWeeks(1)
                .splitType(WorkoutSplitType.FULL_BODY)
                .sessions(allWeek)
                .build();
    }

    private OptionalInt extractDayNumber(String lower) {
        Matcher m = DAY_NUMBER_PATTERN.matcher(lower);
        if (m.find()) {
            return OptionalInt.of(Integer.parseInt(m.group(1)));
        }
        return OptionalInt.empty();
    }

    private Integer extractInt(Pattern pattern, String input) {
        if (input == null) return null;
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private String extractInstructions(String input) {
        if (input == null) return null;
        Matcher m = INSTRUCTIONS_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private Optional<AdminGettingStartedExerciseRequest> findMentionedExistingExercise(
            List<AdminGettingStartedExerciseRequest> exercises,
            String lowerPrompt
    ) {
        if (exercises == null) return Optional.empty();
        return exercises.stream()
                .filter(ex -> ex.getExerciseName() != null && lowerPrompt.contains(ex.getExerciseName().toLowerCase(Locale.US)))
                .findFirst();
    }

    private Optional<Exercise> findMentionedCatalogExercise(List<Exercise> catalog, String lowerPrompt) {
        if (catalog == null) return Optional.empty();
        return catalog.stream()
                .filter(ex -> ex.getName() != null && lowerPrompt.contains(ex.getName().toLowerCase(Locale.US)))
                .findFirst();
    }

    private String extractAddedExerciseName(String instruction) {
        Pattern p = Pattern.compile("(?i)add\\s+(?:exercise\\s+)?([a-zA-Z][a-zA-Z0-9\\s-]{2,40}?)(?:\\s+to\\s+day|\\s+for\\s+|\\s+with\\s+|$)");
        Matcher m = p.matcher(instruction);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private void reindex(List<AdminGettingStartedExerciseRequest> exercises) {
        for (int i = 0; i < exercises.size(); i++) {
            exercises.get(i).setDisplayOrder(i);
        }
    }
}
