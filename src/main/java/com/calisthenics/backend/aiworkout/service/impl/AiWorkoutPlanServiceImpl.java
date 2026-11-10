package com.calisthenics.backend.aiworkout.service.impl;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.ModifyAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.entity.AiWorkoutPlanLog;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.aiworkout.repository.AiWorkoutPlanLogRepository;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanDayRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkoutPlanServiceImpl implements AiWorkoutPlanService {

    private final GeminiApiClient geminiApiClient;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanService workoutPlanService;
    private final UserRepository userRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final AiWorkoutPlanLogRepository aiWorkoutPlanLogRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AiWorkoutPlanPreviewResponse generatePlan(Long userId, GenerateAiWorkoutPlanRequest request) {
        Long targetUserId = resolveTargetUserId(userId, request.getClientId());
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        if (catalog.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Exercise catalog is empty");
        }

        String systemPrompt = buildSystemPrompt(catalog, request.getFitnessLevel());
        String userPrompt = buildUserPrompt(request);

        GeminiApiClient.GeminiCallResult result = geminiApiClient.generateWorkoutPlan(systemPrompt, userPrompt);
        AiWorkoutPlanPreviewResponse preview = mapAndValidateStructuredPlan(result.plan(), catalog, targetUserId);

        preview.getMetadata().setModelUsed(result.modelUsed());
        preview.getMetadata().setInputTokens(result.inputTokens());
        preview.getMetadata().setOutputTokens(result.outputTokens());
        preview.getMetadata().setLatencyMs(result.latencyMs());

        // Log the AI generation
        saveLog(user, request.getPrompt(), result.modelUsed(), result.inputTokens(), result.outputTokens(),
                result.latencyMs(), "SUCCESS", null, null);

        return preview;
    }

    @Override
    @Transactional
    public AiWorkoutPlanPreviewResponse modifyPlan(Long userId, ModifyAiWorkoutPlanRequest request) {
        Long targetUserId = resolveTargetUserId(userId, request.getClientId());
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        String systemPrompt = buildSystemPrompt(catalog, null);

        String currentPlanJson;
        try {
            currentPlanJson = objectMapper.writeValueAsString(request.getCurrentPlan());
        } catch (Exception ex) {
            currentPlanJson = "{}";
        }

        String userPrompt = String.format("""
                Here is the current workout plan in structured JSON format:
                %s

                User instruction for modifying this workout plan:
                "%s"

                Apply the requested changes to the plan while preserving the rest of the routine.
                Ensure all exercises use valid exercise_id values from the catalog.
                Return the complete updated structured workout plan JSON.
                """, currentPlanJson, request.getPrompt());

        GeminiApiClient.GeminiCallResult result = geminiApiClient.generateWorkoutPlan(systemPrompt, userPrompt);
        AiWorkoutPlanPreviewResponse preview = mapAndValidateStructuredPlan(result.plan(), catalog, targetUserId);

        preview.getMetadata().setModelUsed(result.modelUsed());
        preview.getMetadata().setInputTokens(result.inputTokens());
        preview.getMetadata().setOutputTokens(result.outputTokens());
        preview.getMetadata().setLatencyMs(result.latencyMs());

        saveLog(user, request.getPrompt(), result.modelUsed(), result.inputTokens(), result.outputTokens(),
                result.latencyMs(), "MODIFIED", null, null);

        return preview;
    }

    @Override
    @Transactional
    public WorkoutPlanResponse commitPlan(Long userId, CommitAiWorkoutPlanRequest request) {
        Long targetUserId = resolveTargetUserId(userId, request.getClientId());
        // Acquire user row lock BEFORE reading/modifying workout plans to preserve global lock order (User -> WorkoutPlan)
        User user = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // User-scoped idempotency check: if key was already processed for this user, return the committed plan
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<AiWorkoutPlanLog> existingLog = aiWorkoutPlanLogRepository
                    .findFirstByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(targetUserId, request.getIdempotencyKey());
            if (existingLog.isPresent() && "COMMITTED".equals(existingLog.get().getStatus())) {
                log.info("Idempotent commit request with key: {} for user {}. Returning existing plan.", request.getIdempotencyKey(), targetUserId);
                WorkoutPlan existingPlan = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(targetUserId)
                        .or(() -> workoutPlanRepository.findFirstByUserIdOrderByUpdatedAtDesc(targetUserId))
                        .orElse(null);
                if (existingPlan != null) {
                    return workoutPlanService.get(targetUserId, existingPlan.getId());
                }
            }
        }

        AiWorkoutPlanPreviewResponse planPreview = request.getPlan();
        if (planPreview == null || planPreview.getSessions() == null || planPreview.getSessions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan preview must have at least one session");
        }

        List<WorkoutPlan> lockedPlans = workoutPlanRepository.findByUserIdForUpdate(targetUserId);
        Optional<WorkoutPlan> currentActivePlan = lockedPlans.stream()
                .filter(WorkoutPlan::isActive)
                .findFirst();

        Boolean replaceFlag = request.getReplaceExisting();
        if (replaceFlag == null) {
            if (currentActivePlan.isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Active workout plan '" + currentActivePlan.get().getName()
                                + "' already exists. Confirm replacing it or save without activating."
                );
            }
        } else if (Boolean.TRUE.equals(replaceFlag) && request.getExpectedActivePlanId() != null) {
            long expectedId = request.getExpectedActivePlanId();
            if (expectedId <= 0L && currentActivePlan.isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An active workout plan ('" + currentActivePlan.get().getName()
                                + "') is currently active. Please review and confirm replacing it."
                );
            }
            if (expectedId > 0L && currentActivePlan.isPresent() && !currentActivePlan.get().getId().equals(expectedId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Your active workout plan changed to '" + currentActivePlan.get().getName()
                                + "'. Refresh and confirm replacement."
                );
            }
        }

        boolean shouldReplaceAndActivate = replaceFlag == null || Boolean.TRUE.equals(replaceFlag);

        // Convert to CreateWorkoutPlanRequest with sanitized DB-safe constraints
        CreateWorkoutPlanRequest createRequest = new CreateWorkoutPlanRequest();
        createRequest.setName(sanitizePlanName(planPreview.getPlanName()));
        createRequest.setDescription(planPreview.getDescription());
        createRequest.setSplitType(planPreview.getSplitType() != null ? planPreview.getSplitType() : WorkoutSplitType.CUSTOM);
        createRequest.setActive(shouldReplaceAndActivate);

        List<WorkoutPlanDayRequest> dayRequests = new ArrayList<>();
        Map<WorkoutDayOfWeek, AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> sessionMap = new EnumMap<>(WorkoutDayOfWeek.class);
        for (AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview session : planPreview.getSessions()) {
            if (session.getDayOfWeek() != null) {
                sessionMap.put(session.getDayOfWeek(), session);
            }
        }

        // Guarantee all 7 days of the week are present with canonical display order 1..7
        for (WorkoutDayOfWeek dayOfWeek : WorkoutDayOfWeek.values()) {
            WorkoutPlanDayRequest dayReq = new WorkoutPlanDayRequest();
            dayReq.setDayOfWeek(dayOfWeek);
            dayReq.setDisplayOrder(dayOfWeek.getOrder());

            AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview session = sessionMap.get(dayOfWeek);
            if (session != null && !session.isRestDay()) {
                dayReq.setRestDay(false);
                dayReq.setName(sanitizeDayName(session.getTitle(), dayOfWeek.name() + " Workout"));
                dayReq.setSessionType(resolveSessionType(session.getTitle()));

                List<WorkoutPlanExerciseRequest> exRequests = new ArrayList<>();
                int order = 1;
                if (session.getExercises() != null) {
                    for (AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview ex : session.getExercises()) {
                        if (ex.getExerciseId() != null && exerciseRepository.existsById(ex.getExerciseId())) {
                            boolean staticHold = isStaticHoldExercise(ex.getCategory(), ex.getExerciseCode(), ex.getExerciseName());
                            NormalizedPrescription rx = normalizePrescription(ex.getSets(), ex.getReps(), ex.getHoldSeconds(), ex.getRestSeconds(), staticHold);

                            WorkoutPlanExerciseRequest exReq = new WorkoutPlanExerciseRequest();
                            exReq.setExerciseId(ex.getExerciseId());
                            exReq.setSets(rx.sets());
                            exReq.setReps(rx.reps());
                            exReq.setDurationSeconds(rx.holdSeconds());
                            exReq.setRestSeconds(rx.restSeconds());
                            exReq.setDisplayOrder(order++);
                            exReq.setNotes(ex.getNotes());
                            exRequests.add(exReq);
                        }
                    }
                }
                dayReq.setExercises(exRequests);
            } else {
                dayReq.setRestDay(true);
                dayReq.setName("Rest Day");
                dayReq.setSessionType(WorkoutSessionType.REST);
                dayReq.setExercises(Collections.emptyList());
            }
            dayRequests.add(dayReq);
        }

        createRequest.setDays(dayRequests);

        if (shouldReplaceAndActivate) {
            for (WorkoutPlan plan : lockedPlans) {
                if (plan.isActive()) {
                    plan.setActive(false);
                    workoutPlanRepository.saveAndFlush(plan);
                }
            }
        }

        // Transactional plan creation and optional activation
        WorkoutPlanResponse createdPlan = workoutPlanService.create(targetUserId, createRequest);

        // Tag the plan as AI generated
        workoutPlanRepository.findById(createdPlan.getId()).ifPresent(planEntity -> {
            planEntity.setAiGenerated(true);
            planEntity.setAiPrompt(planPreview.getDescription());
            workoutPlanRepository.save(planEntity);
        });

        // Schedule initial calendar sessions if a start date is specified
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate()
                : (planPreview.getStartDate() != null ? planPreview.getStartDate() : LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));

        scheduleInitialWorkoutSessions(targetUserId, createdPlan.getId(), startDate, planPreview.getDurationWeeks());

        String loggedModel = (planPreview.getMetadata() != null && planPreview.getMetadata().getModelUsed() != null)
                ? planPreview.getMetadata().getModelUsed()
                : "gemini-flash-lite-latest";
        saveLog(user, createRequest.getName(), loggedModel, 0, 0, 0L,
                "COMMITTED", null, request.getIdempotencyKey());

        log.info("Successfully committed AI workout plan '{}' (id: {}, active: {}) for user {}",
                createdPlan.getName(), createdPlan.getId(), createdPlan.isActive(), targetUserId);

        return createdPlan;
    }

    private void scheduleInitialWorkoutSessions(Long userId, Long planId, LocalDate startDate, Integer durationWeeks) {
        int weeks = (durationWeeks != null && durationWeeks > 0) ? Math.min(durationWeeks, 8) : 4;
        User user = userRepository.getReferenceById(userId);
        WorkoutPlan plan = workoutPlanRepository.getReferenceById(planId);
        List<WorkoutPlanDay> days = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(planId);

        for (WorkoutPlanDay day : days) {
            if (day.isRestDay() || day.getExercises().isEmpty()) continue;

            DayOfWeek targetDow = DayOfWeek.of(day.getDayOfWeek().getOrder());
            LocalDate firstDate = startDate.with(TemporalAdjusters.nextOrSame(targetDow));

            for (int w = 0; w < weeks; w++) {
                LocalDate sessionDate = firstDate.plusWeeks(w);
                boolean exists = sessionRepository.existsByUserIdAndWorkoutDateAndStatus(
                        userId, sessionDate, WorkoutSessionStatus.IN_PROGRESS);

                if (!exists) {
                    WorkoutSession session = WorkoutSession.builder()
                            .user(user)
                            .workoutPlan(plan)
                            .workoutPlanDay(day)
                            .workoutDate(sessionDate)
                            .title(day.getName() != null ? day.getName() : day.getDayOfWeek().name() + " Workout")
                            .status(WorkoutSessionStatus.IN_PROGRESS)
                            .build();
                    sessionRepository.save(session);
                }
            }
        }
    }

    private Long resolveTargetUserId(Long currentUserId, Long clientId) {
        if (clientId == null || clientId.equals(currentUserId)) {
            return currentUserId;
        }
        // Verify coach-client authorization
        boolean isAuthorized = relationshipRepository.findActiveRelationshipForUsers(currentUserId, clientId).isPresent();
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to manage workouts for client ID: " + clientId);
        }
        return clientId;
    }

    private String buildSystemPrompt(List<Exercise> catalog, String fitnessLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an elite calisthenics coach, gymnastics strength training expert, and biomechanics programmer.\n");
        sb.append("Your task is to design a high-quality, scientifically sound weekly calisthenics workout plan based on the user's instructions.\n\n");

        sb.append("CRITICAL REQUIREMENT - EXERCISE CATALOG USAGE:\n");
        sb.append("You MUST select exercises exclusively from the following verified catalog.\n");
        sb.append("For every exercise you include, you MUST set 'exercise_id' to the EXACT numeric ID from this catalog, and 'exercise_name' to the EXACT name.\n");
        sb.append("DO NOT INVENT EXERCISE IDs OR NAMES.\n\n");
        sb.append("AVAILABLE EXERCISE CATALOG:\n");

        for (Exercise ex : catalog) {
            sb.append(String.format("- ID: %d | Name: \"%s\" | Code: %s | Category: %s | Difficulty: %s\n",
                    ex.getId(), ex.getName(), ex.getCode(), ex.getCategory().name(), ex.getDifficulty().name()));
        }

        sb.append("\nPROGRAMMING RULES:\n");
        sb.append("1. For static holds (e.g. Planche, Front Lever, Handstand, L-Sit): set 'hold_seconds' (e.g. 8 to 25 seconds) and 'reps' to null.\n");
        sb.append("2. For dynamic exercises (e.g. Push-Ups, Pull-Ups, Dips, Squats): set 'reps' (e.g. 5 to 15) and 'hold_seconds' to null.\n");
        sb.append("3. Sets should typically range between 3 and 5.\n");
        sb.append("4. Rest between sets: 90-180 seconds for strength/skills, 60-90 seconds for hypertrophy/endurance.\n");
        sb.append("5. Every week MUST cover all 7 days (MONDAY through SUNDAY). Mark non-training days with 'is_rest_day': true and an empty 'exercises' array.\n");
        sb.append("6. If the user specifies a starting date (e.g. 'start next Monday', 'start tomorrow'), set 'suggested_start_date' formatted as YYYY-MM-DD.\n");

        if (fitnessLevel != null && !fitnessLevel.isBlank()) {
            sb.append("\nTarget Athlete Fitness Level: ").append(fitnessLevel).append("\n");
        }

        return sb.toString();
    }

    private String buildUserPrompt(GenerateAiWorkoutPlanRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getPrompt()).append("\n\n");
        if (request.getPreferredSplit() != null) {
            sb.append("Preferred split structure: ").append(request.getPreferredSplit()).append("\n");
        }
        if (request.getStartDate() != null) {
            sb.append("Target starting date: ").append(request.getStartDate()).append("\n");
        }
        if (request.getDurationWeeks() != null) {
            sb.append("Target program duration in weeks: ").append(request.getDurationWeeks()).append("\n");
        }
        return sb.toString();
    }

    private AiWorkoutPlanPreviewResponse mapAndValidateStructuredPlan(
            GeminiStructuredWorkoutPlan plan,
            List<Exercise> catalog,
            Long targetUserId
    ) {
        Map<Long, Exercise> idMap = new HashMap<>();
        Map<String, Exercise> nameMap = new HashMap<>();
        for (Exercise ex : catalog) {
            idMap.put(ex.getId(), ex);
            nameMap.put(ex.getName().toLowerCase().trim(), ex);
            nameMap.put(ex.getCode().toLowerCase().trim(), ex);
        }

        List<AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> sessionPreviews = new ArrayList<>();
        Map<String, GeminiStructuredWorkoutPlan.GeminiSession> returnedSessions = new HashMap<>();
        if (plan.getSessions() != null) {
            for (GeminiStructuredWorkoutPlan.GeminiSession s : plan.getSessions()) {
                if (s.getDayOfWeek() != null) {
                    returnedSessions.put(s.getDayOfWeek().toUpperCase(), s);
                }
            }
        }

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            GeminiStructuredWorkoutPlan.GeminiSession session = returnedSessions.get(dow.name());
            if (session != null && !Boolean.TRUE.equals(session.getRestDay())) {
                List<AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview> exercisePreviews = new ArrayList<>();
                if (session.getExercises() != null) {
                    for (GeminiStructuredWorkoutPlan.GeminiExercise ex : session.getExercises()) {
                        Exercise matched = matchExercise(ex.getExerciseId(), ex.getExerciseName(), idMap, nameMap, catalog);
                        if (matched != null) {
                            boolean staticHold = isStaticHoldExercise(
                                    matched.getCategory() != null ? matched.getCategory().name() : null,
                                    matched.getCode(),
                                    matched.getName()
                            );
                            NormalizedPrescription rx = normalizePrescription(
                                    ex.getSets(),
                                    ex.getReps(),
                                    ex.getHoldSeconds(),
                                    ex.getRestSeconds(),
                                    staticHold
                            );
                            exercisePreviews.add(AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview.builder()
                                    .exerciseId(matched.getId())
                                    .exerciseName(matched.getName())
                                    .exerciseCode(matched.getCode())
                                    .category(matched.getCategory().name())
                                    .sets(rx.sets())
                                    .reps(rx.reps())
                                    .holdSeconds(rx.holdSeconds())
                                    .restSeconds(rx.restSeconds())
                                    .notes(ex.getNotes())
                                    .requiresReview(false)
                                    .build());
                        }
                    }
                }

                sessionPreviews.add(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                        .dayOfWeek(dow)
                        .title(session.getTitle() != null ? session.getTitle() : dow.name() + " Workout")
                        .restDay(false)
                        .exercises(exercisePreviews)
                        .build());
            } else {
                sessionPreviews.add(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                        .dayOfWeek(dow)
                        .title(session != null && session.getTitle() != null ? session.getTitle() : "Rest Day")
                        .restDay(true)
                        .exercises(Collections.emptyList())
                        .build());
            }
        }

        // Check for conflicts with existing active plans
        Optional<WorkoutPlan> existingActive = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(targetUserId);

        LocalDate startDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (plan.getSuggestedStartDate() != null) {
            try {
                startDate = LocalDate.parse(plan.getSuggestedStartDate().trim());
            } catch (Exception ignored) {}
        }

        WorkoutSplitType splitType = WorkoutSplitType.CUSTOM;
        if (plan.getSplitType() != null) {
            try {
                splitType = WorkoutSplitType.valueOf(plan.getSplitType().toUpperCase().trim());
            } catch (Exception ignored) {}
        }

        return AiWorkoutPlanPreviewResponse.builder()
                .planName(sanitizePlanName(plan.getPlanName()))
                .description(plan.getDescription() != null ? plan.getDescription() : "Custom workout plan generated by Gemini AI")
                .durationWeeks(plan.getDurationWeeks() != null ? plan.getDurationWeeks() : 4)
                .startDate(startDate)
                .splitType(splitType)
                .sessions(sessionPreviews)
                .metadata(AiWorkoutPlanPreviewResponse.AiWorkoutMetadata.builder()
                        .hasConflicts(existingActive.isPresent())
                        .existingActivePlanName(existingActive.map(WorkoutPlan::getName).orElse(null))
                        .build())
                .build();
    }

    private record NormalizedPrescription(int sets, Integer reps, Integer holdSeconds, int restSeconds) {}

    private NormalizedPrescription normalizePrescription(
            Integer rawSets,
            Integer rawReps,
            Integer rawHoldSeconds,
            Integer rawRestSeconds,
            boolean staticHold
    ) {
        int sets = (rawSets != null && rawSets > 0) ? rawSets : 3;
        int rest = (rawRestSeconds != null && rawRestSeconds >= 0) ? rawRestSeconds : 90;

        // DB constraint workout_plan_exercises_reps_check requires reps IS NULL OR reps > 0;
        // workout_plan_exercises_duration_seconds_check requires duration_seconds IS NULL OR duration_seconds > 0.
        Integer reps = (rawReps != null && rawReps > 0) ? rawReps : null;
        Integer hold = (rawHoldSeconds != null && rawHoldSeconds > 0) ? rawHoldSeconds : null;

        if (reps == null && hold == null) {
            if (staticHold) {
                hold = 15;
            } else {
                reps = 10;
            }
        }
        return new NormalizedPrescription(sets, reps, hold, rest);
    }

    private boolean isStaticHoldExercise(String category, String code, String name) {
        if (ExerciseCategory.SKILL.name().equalsIgnoreCase(category)
                || ExerciseCategory.ISOMETRIC.name().equalsIgnoreCase(category)) {
            return true;
        }
        String joined = ((code != null ? code : "") + " " + (name != null ? name : "")).toLowerCase(Locale.US);
        return joined.contains("planche")
                || joined.contains("front lever")
                || joined.contains("front_lever")
                || joined.contains("back lever")
                || joined.contains("handstand")
                || joined.contains("l-sit")
                || joined.contains("l_sit")
                || joined.contains("hold")
                || joined.contains("plank")
                || joined.contains("flag");
    }

    private String sanitizePlanName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "AI Calisthenics Plan";
        }
        String trimmed = rawName.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private String sanitizeDayName(String rawName, String fallback) {
        String value = (rawName == null || rawName.trim().isEmpty()) ? fallback : rawName.trim();
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private Exercise matchExercise(
            Long id,
            String name,
            Map<Long, Exercise> idMap,
            Map<String, Exercise> nameMap,
            List<Exercise> catalog
    ) {
        if (id != null && idMap.containsKey(id)) {
            return idMap.get(id);
        }
        if (name != null) {
            String clean = name.toLowerCase().trim();
            if (nameMap.containsKey(clean)) {
                return nameMap.get(clean);
            }
            // Substring search
            for (Exercise ex : catalog) {
                if (clean.contains(ex.getName().toLowerCase()) || ex.getName().toLowerCase().contains(clean)) {
                    return ex;
                }
            }
        }
        // Fallback: default to push-up if catalog is not empty
        return catalog.isEmpty() ? null : catalog.getFirst();
    }

    private WorkoutSessionType resolveSessionType(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase();
        if (lower.contains("push")) return WorkoutSessionType.PUSH;
        if (lower.contains("pull")) return WorkoutSessionType.PULL;
        if (lower.contains("leg")) return WorkoutSessionType.LEGS;
        if (lower.contains("upper")) return WorkoutSessionType.UPPER;
        if (lower.contains("lower")) return WorkoutSessionType.LOWER;
        if (lower.contains("full")) return WorkoutSessionType.FULL_BODY;
        return null;
    }

    private void saveLog(User user, String prompt, String model, int inTokens, int outTokens,
                         long latency, String status, String error, String idempotencyKey) {
        try {
            AiWorkoutPlanLog logEntity = AiWorkoutPlanLog.builder()
                    .user(user)
                    .prompt(prompt)
                    .model(model)
                    .inputTokens(inTokens)
                    .outputTokens(outTokens)
                    .latencyMs(latency)
                    .status(status)
                    .errorMessage(error)
                    .idempotencyKey(idempotencyKey)
                    .build();
            aiWorkoutPlanLogRepository.save(logEntity);
        } catch (Exception ex) {
            log.warn("Failed to persist AI workout plan log: {}", ex.getMessage());
        }
    }
}
