package com.calisthenics.backend.aiworkout.service.impl;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.ModifyAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.entity.AiWorkoutPlanLog;
import com.calisthenics.backend.aiworkout.exception.AiWorkoutPlanValidationException;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.aiworkout.repository.AiWorkoutPlanLogRepository;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanPromptAnalyzer;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanValidator;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.skill.dto.SkillReferenceDto;
import com.calisthenics.backend.skill.service.SkillCatalogService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkoutPlanServiceImpl implements AiWorkoutPlanService {

    private static final List<WorkoutSessionStatus> ACTIVE_CALENDAR_STATUSES = List.of(
            WorkoutSessionStatus.SCHEDULED,
            WorkoutSessionStatus.IN_PROGRESS,
            WorkoutSessionStatus.COMPLETED
    );

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
    private final AiWorkoutPlanPromptAnalyzer promptAnalyzer;
    private final AiWorkoutPlanValidator planValidator;
    private final UserTimeResolver userTimeResolver;
    private final AppProperties appProperties;

    @Autowired(required = false)
    private SkillCatalogService skillCatalogService;

    @Override
    @Transactional
    public AiWorkoutPlanPreviewResponse generatePlan(Long userId, GenerateAiWorkoutPlanRequest request) {
        return generatePlan(userId, request, null);
    }

    @Override
    @Transactional
    public AiWorkoutPlanPreviewResponse generatePlan(Long userId, GenerateAiWorkoutPlanRequest request, String timezone) {
        Long targetUserId = resolveTargetUserId(userId, request.getClientId());
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints = promptAnalyzer.analyze(
                request.getPrompt(),
                request.getFitnessLevel(),
                request.getDurationWeeks()
        );

        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        if (catalog.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Exercise catalog is empty");
        }

        LocalDate today = userTimeResolver.today(timezone);
        LocalDate defaultStartDate = request.getStartDate() != null
                ? request.getStartDate()
                : today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        String systemPrompt = buildSystemPrompt(catalog, constraints);
        String userPrompt = buildUserPrompt(request, constraints);

        return executeAndValidateWithCorrection(
                user,
                request.getPrompt(),
                systemPrompt,
                userPrompt,
                catalog,
                constraints,
                defaultStartDate,
                targetUserId,
                "SUCCESS"
        );
    }

    @Override
    @Transactional
    public AiWorkoutPlanPreviewResponse modifyPlan(Long userId, ModifyAiWorkoutPlanRequest request) {
        return modifyPlan(userId, request, null);
    }

    @Override
    @Transactional
    public AiWorkoutPlanPreviewResponse modifyPlan(Long userId, ModifyAiWorkoutPlanRequest request, String timezone) {
        Long targetUserId = resolveTargetUserId(userId, request.getClientId());
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        if (catalog.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Exercise catalog is empty");
        }

        Integer existingDuration = request.getCurrentPlan() != null ? request.getCurrentPlan().getDurationWeeks() : null;
        AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints = promptAnalyzer.analyze(
                request.getPrompt(),
                null,
                existingDuration
        );

        LocalDate today = userTimeResolver.today(timezone);
        LocalDate defaultStartDate = (request.getCurrentPlan() != null && request.getCurrentPlan().getStartDate() != null)
                ? request.getCurrentPlan().getStartDate()
                : today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        String systemPrompt = buildSystemPrompt(catalog, constraints);
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

                Apply the requested changes while obeying every rule:
                - Cover all 7 unique days MONDAY through SUNDAY cleanly (training days vs rest days).
                - Stay within %d to %d exercises per training day (strict maximum %d).
                - Do not duplicate exercises within the same session.
                - Use exact exercise_id and exercise_name values from the catalog.
                Return the complete updated structured workout plan JSON.
                """,
                currentPlanJson,
                request.getPrompt(),
                constraints.recommendedMinExercises(),
                constraints.recommendedMaxExercises(),
                constraints.maxExercisesPerSession());

        return executeAndValidateWithCorrection(
                user,
                request.getPrompt(),
                systemPrompt,
                userPrompt,
                catalog,
                constraints,
                defaultStartDate,
                targetUserId,
                "MODIFIED"
        );
    }

    private AiWorkoutPlanPreviewResponse executeAndValidateWithCorrection(
            User user,
            String promptForLog,
            String systemPrompt,
            String userPrompt,
            List<Exercise> catalog,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints,
            LocalDate defaultStartDate,
            Long targetUserId,
            String successLogStatus
    ) {
        GeminiApiClient.GeminiCallResult result = geminiApiClient.generateWorkoutPlan(systemPrompt, userPrompt);
        int totalInputTokens = result.inputTokens();
        int totalOutputTokens = result.outputTokens();
        long totalLatencyMs = result.latencyMs();

        AiWorkoutPlanValidator.ValidationResult validation = planValidator.validateAndNormalizeGeneratedPlan(
                result.plan(), catalog, constraints, defaultStartDate);

        int maxCorrections = Math.max(0, appProperties.getWorkoutPlanner().getCorrectionRetries());
        int attempt = 0;
        while (!validation.isValid() && attempt < maxCorrections) {
            attempt++;
            log.warn("AI workout plan failed validation for user={} (attempt {}): {}",
                    targetUserId, attempt, validation.violations());
            String correctionPrompt = buildCorrectionPrompt(userPrompt, result.rawJson(), validation.violations(), constraints);
            result = geminiApiClient.generateWorkoutPlan(systemPrompt, correctionPrompt);
            totalInputTokens += result.inputTokens();
            totalOutputTokens += result.outputTokens();
            totalLatencyMs += result.latencyMs();
            validation = planValidator.validateAndNormalizeGeneratedPlan(
                    result.plan(), catalog, constraints, defaultStartDate);
        }

        if (!validation.isValid()) {
            String errorSummary = String.join("; ", validation.violations());
            log.warn("AI workout plan rejected after correction attempt for user={}: {}", targetUserId, errorSummary);
            saveLog(user, promptForLog, result.modelUsed(), totalInputTokens, totalOutputTokens,
                    totalLatencyMs, "VALIDATION_FAILED", errorSummary, null);
            throw new AiWorkoutPlanValidationException(
                    "Unable to build a valid workout plan: " + errorSummary,
                    validation.violations()
            );
        }

        AiWorkoutPlanPreviewResponse preview = validation.preview();
        Optional<WorkoutPlan> existingActive = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(targetUserId);
        preview.setMetadata(AiWorkoutPlanPreviewResponse.AiWorkoutMetadata.builder()
                .modelUsed(result.modelUsed())
                .inputTokens(totalInputTokens)
                .outputTokens(totalOutputTokens)
                .latencyMs(totalLatencyMs)
                .hasConflicts(existingActive.isPresent())
                .existingActivePlanName(existingActive.map(WorkoutPlan::getName).orElse(null))
                .build());

        saveLog(user, promptForLog, result.modelUsed(), totalInputTokens, totalOutputTokens,
                totalLatencyMs, successLogStatus, null, null);

        return preview;
    }

    @Override
    @Transactional
    public WorkoutPlanResponse commitPlan(Long userId, CommitAiWorkoutPlanRequest request) {
        return commitPlan(userId, request, null);
    }

    @Override
    @Transactional
    public WorkoutPlanResponse commitPlan(Long userId, CommitAiWorkoutPlanRequest request, String timezone) {
        Long targetUserId = resolveTargetUserId(userId, request.getClientId());
        User user = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

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

        LocalDate today = userTimeResolver.today(timezone);
        LocalDate defaultStartDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        planValidator.validatePreviewForCommit(planPreview, catalog, defaultStartDate);

        Map<Long, Exercise> catalogById = new HashMap<>();
        for (Exercise ex : catalog) {
            catalogById.put(ex.getId(), ex);
        }

        boolean shouldReplaceAndActivate = replaceFlag == null || Boolean.TRUE.equals(replaceFlag);

        CreateWorkoutPlanRequest createRequest = new CreateWorkoutPlanRequest();
        createRequest.setName(sanitizePlanName(planPreview.getPlanName()));
        createRequest.setDescription(sanitizeDescription(planPreview.getDescription()));
        createRequest.setSplitType(planPreview.getSplitType() != null ? planPreview.getSplitType() : WorkoutSplitType.CUSTOM);
        createRequest.setActive(shouldReplaceAndActivate);

        Map<WorkoutDayOfWeek, AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview> sessionMap = new EnumMap<>(WorkoutDayOfWeek.class);
        for (AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview session : planPreview.getSessions()) {
            sessionMap.put(session.getDayOfWeek(), session);
        }

        List<WorkoutPlanDayRequest> dayRequests = new ArrayList<>();
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
                for (AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview ex : session.getExercises()) {
                    Exercise catalogEntry = catalogById.get(ex.getExerciseId());
                    if (catalogEntry == null) {
                        continue;
                    }
                    boolean staticHold = AiWorkoutPlanValidator.isStaticHoldExercise(catalogEntry);
                    NormalizedPrescription rx = normalizePrescription(
                            ex.getSets(), ex.getReps(), ex.getHoldSeconds(), ex.getRestSeconds(), staticHold);

                    WorkoutPlanExerciseRequest exReq = new WorkoutPlanExerciseRequest();
                    exReq.setExerciseId(catalogEntry.getId());
                    exReq.setSets(rx.sets());
                    exReq.setReps(rx.reps());
                    exReq.setDurationSeconds(rx.holdSeconds());
                    exReq.setRestSeconds(rx.restSeconds());
                    exReq.setDisplayOrder(order++);
                    exReq.setNotes(ex.getNotes());
                    exRequests.add(exReq);
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
            cancelFutureUnstartedScheduledSessions(targetUserId, today);
        }

        WorkoutPlanResponse createdPlan = workoutPlanService.create(targetUserId, createRequest);

        workoutPlanRepository.findById(createdPlan.getId()).ifPresent(planEntity -> {
            planEntity.setAiGenerated(true);
            planEntity.setAiPrompt(createRequest.getDescription());
            workoutPlanRepository.save(planEntity);
        });

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate()
                : (planPreview.getStartDate() != null ? planPreview.getStartDate() : defaultStartDate);

        if (shouldReplaceAndActivate || currentActivePlan.isEmpty()) {
            scheduleInitialWorkoutSessions(targetUserId, createdPlan.getId(), startDate, planPreview.getDurationWeeks());
        }

        String loggedModel = (planPreview.getMetadata() != null && planPreview.getMetadata().getModelUsed() != null)
                ? planPreview.getMetadata().getModelUsed()
                : appProperties.getGemini().getModel();
        saveLog(user, createRequest.getName(), loggedModel, 0, 0, 0L,
                "COMMITTED", null, request.getIdempotencyKey());

        log.info("Successfully committed AI workout plan '{}' (id: {}, active: {}) for user {}",
                createdPlan.getName(), createdPlan.getId(), createdPlan.isActive(), targetUserId);

        return createdPlan;
    }

    private void cancelFutureUnstartedScheduledSessions(Long userId, LocalDate today) {
        List<WorkoutSession> futureUnstarted = sessionRepository
                .findByUserIdAndStatusAndStartedAtIsNullAndWorkoutDateAfter(userId, WorkoutSessionStatus.SCHEDULED, today);
        if (futureUnstarted != null && !futureUnstarted.isEmpty()) {
            futureUnstarted.forEach(s -> s.setStatus(WorkoutSessionStatus.CANCELLED));
            sessionRepository.saveAll(futureUnstarted);
        }
    }

    private void scheduleInitialWorkoutSessions(Long userId, Long planId, LocalDate startDate, Integer durationWeeks) {
        int maxWeeks = appProperties.getWorkoutPlanner().getMaxDurationWeeks();
        int weeks = (durationWeeks != null && durationWeeks > 0) ? Math.min(durationWeeks, maxWeeks) : 4;
        User user = userRepository.getReferenceById(userId);
        WorkoutPlan plan = workoutPlanRepository.getReferenceById(planId);
        List<WorkoutPlanDay> days = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(planId);

        Set<LocalDate> scheduledDates = new HashSet<>();
        for (WorkoutPlanDay day : days) {
            if (day.isRestDay() || day.getExercises().isEmpty()) {
                continue;
            }

            DayOfWeek targetDow = DayOfWeek.of(day.getDayOfWeek().getOrder());
            LocalDate firstDate = startDate.with(TemporalAdjusters.nextOrSame(targetDow));

            for (int w = 0; w < weeks; w++) {
                LocalDate sessionDate = firstDate.plusWeeks(w);
                if (!scheduledDates.add(sessionDate)) {
                    continue;
                }
                boolean exists = sessionRepository.existsByUserIdAndWorkoutDateAndStatusIn(
                        userId, sessionDate, ACTIVE_CALENDAR_STATUSES);

                if (!exists) {
                    WorkoutSession session = WorkoutSession.builder()
                            .user(user)
                            .workoutPlan(plan)
                            .workoutPlanDay(day)
                            .workoutDate(sessionDate)
                            .title(day.getName() != null ? day.getName() : day.getDayOfWeek().name() + " Workout")
                            .status(WorkoutSessionStatus.SCHEDULED)
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
        boolean isAuthorized = relationshipRepository.findActiveRelationshipForUsers(currentUserId, clientId).isPresent();
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to manage workouts for client ID: " + clientId);
        }
        return clientId;
    }

    private String buildSystemPrompt(
            List<Exercise> catalog,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an elite calisthenics coach, gymnastics strength training expert, and biomechanics programmer.\n");
        sb.append("Design a balanced, structured weekly calisthenics workout plan based on the user's instructions.\n\n");

        sb.append("CRITICAL REQUIREMENT - EXERCISE CATALOG USAGE:\n");
        sb.append("Select exercises EXCLUSIVELY from the catalog below. Copy BOTH 'exercise_id' and 'exercise_name' verbatim.\n");
        sb.append("NEVER invent new exercises or IDs.\n\n");
        sb.append("AVAILABLE EXERCISE CATALOG:\n");

        Map<Long, List<SkillReferenceDto>> skillRefsByExercise = skillCatalogService != null
                ? skillCatalogService.getSkillReferencesByExerciseIds(catalog.stream().map(Exercise::getId).toList())
                : Map.of();

        for (Exercise ex : catalog) {
            ExerciseTrainingType resolvedType = SkillCatalogService.resolveEffectiveTrainingType(ex);
            String linkedSkills = skillRefsByExercise.getOrDefault(ex.getId(), List.of()).stream()
                    .map(SkillReferenceDto::getSkillName)
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (linkedSkills.isBlank()) {
                sb.append(String.format("- ID: %d | Name: \"%s\" | Code: %s | TrainingType: %s | Category: %s | Difficulty: %s\n",
                        ex.getId(), ex.getName(), ex.getCode(), resolvedType.name(), ex.getCategory().name(), ex.getDifficulty().name()));
            } else {
                sb.append(String.format("- ID: %d | Name: \"%s\" | Code: %s | TrainingType: %s | Category: %s | Difficulty: %s | Skills: %s\n",
                        ex.getId(), ex.getName(), ex.getCode(), resolvedType.name(), ex.getCategory().name(), ex.getDifficulty().name(), linkedSkills));
            }
        }

        sb.append("\nSTRICT STRUCTURE & VOLUME RULES:\n");
        sb.append("1. Output EXACTLY 7 entries in 'sessions', one for each unique weekday MONDAY through SUNDAY (never duplicate weekdays and never output multi-week session arrays > 7 items).\n");
        sb.append("2. Distinguish 'duration_weeks' (program length in weeks, 1-12) from weekly training frequency (1-7 days per week).\n");
        if (constraints.requestedTrainingDaysPerWeek().isPresent()) {
            int reqDays = constraints.requestedTrainingDaysPerWeek().getAsInt();
            sb.append(String.format("3. The user requested %d training days/week: generate EXACTLY %d training days ('is_rest_day': false) and %d rest days ('is_rest_day': true).\n",
                    reqDays, reqDays, 7 - reqDays));
        } else {
            sb.append("3. Schedule 1 to 7 training days ('is_rest_day': false) matching the user's request, and mark remaining days as rest days ('is_rest_day': true).\n");
        }
        sb.append("4. Rest days MUST have 'is_rest_day': true and an empty 'exercises' array ([]). Rest days never count as training days.\n");
        sb.append(String.format("5. Volume per training session: target %d-%d exercises based on fitness level (%s) and workout focus; NEVER exceed %d exercises in any session.\n",
                constraints.recommendedMinExercises(), constraints.recommendedMaxExercises(),
                constraints.fitnessLevel(), constraints.maxExercisesPerSession()));
        sb.append("6. NEVER repeat the same 'exercise_id' within the same session.\n");
        sb.append("7. Avoid repeating the same dynamic compound strength exercise (PUSH/PULL/LEGS) across consecutive training days; intentional skill (SKILL) or core (CORE) practice may repeat when appropriate.\n");
        sb.append("8. STRICT MODALITY ISOLATION: When the user asks for Calisthenics / bodyweight / calisthenics skills, pick exercises with TrainingType CALISTHENICS (or BOTH) and NEVER include GYM machine/barbell movements. When the user asks for Gym / weights, pick GYM (or BOTH) exercises and NEVER substitute Calisthenics-only isometric skills unless explicitly requested.\n");
        sb.append("8. For static holds (e.g. Planche progressions, Front Lever, Handstand, L-Sit): set 'hold_seconds' (5-60) and 'reps' to null.\n");
        sb.append("9. For dynamic rep-based exercises (Push-Ups, Pull-Ups, Dips, Squats, Pike Push-Up, Muscle-Up, Planche push up, Front lever pull up): set 'reps' (3-25) and 'hold_seconds' to null.\n");
        sb.append("10. Sets: 2-6 (typically 3-5). Rest: 60-180 seconds. Do NOT include any AI provider brand names in titles or descriptions.\n");

        return sb.toString();
    }

    private String buildUserPrompt(
            GenerateAiWorkoutPlanRequest request,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getPrompt()).append("\n\n");
        sb.append("Target athlete fitness level: ").append(constraints.fitnessLevel()).append("\n");
        sb.append("Target program duration: ").append(constraints.targetDurationWeeks()).append(" weeks\n");
        if (constraints.requestedTrainingDaysPerWeek().isPresent()) {
            sb.append("Required training days per week: ").append(constraints.requestedTrainingDaysPerWeek().getAsInt()).append("\n");
        }
        if (request.getPreferredSplit() != null) {
            sb.append("Preferred split structure: ").append(request.getPreferredSplit()).append("\n");
        }
        if (request.getStartDate() != null) {
            sb.append("Target starting date: ").append(request.getStartDate()).append("\n");
        }
        return sb.toString();
    }

    private String buildCorrectionPrompt(
            String originalUserPrompt,
            String previousRawJson,
            List<String> violations,
            AiWorkoutPlanPromptAnalyzer.PromptConstraints constraints
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your previous workout plan output violated the following backend validation rules:\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append(i + 1).append(". ").append(violations.get(i)).append("\n");
        }
        sb.append("\nOriginal user request:\n").append(originalUserPrompt).append("\n");
        sb.append(String.format("Fix ALL violations listed above while staying within %d-%d exercises per session (hard max %d), ",
                constraints.recommendedMinExercises(), constraints.recommendedMaxExercises(), constraints.maxExercisesPerSession()));
        sb.append("exactly 7 unique days MONDAY-SUNDAY, valid active catalog IDs/names only, no duplicate exercises within a session, ");
        sb.append("and strict rest-day separation.\n");
        if (previousRawJson != null && !previousRawJson.isBlank()) {
            sb.append("\nPrevious invalid JSON for reference:\n").append(previousRawJson).append("\n");
        }
        return sb.toString();
    }

    private record NormalizedPrescription(int sets, Integer reps, Integer holdSeconds, int restSeconds) {}

    private NormalizedPrescription normalizePrescription(
            Integer rawSets,
            Integer rawReps,
            Integer rawHoldSeconds,
            Integer rawRestSeconds,
            boolean staticHold
    ) {
        int sets = (rawSets != null && rawSets > 0) ? Math.min(rawSets, 10) : 3;
        int rest = (rawRestSeconds != null && rawRestSeconds >= 0) ? Math.min(rawRestSeconds, 600) : 90;

        Integer reps = (rawReps != null && rawReps > 0) ? Math.min(rawReps, 100) : null;
        Integer hold = (rawHoldSeconds != null && rawHoldSeconds > 0) ? Math.min(rawHoldSeconds, 300) : null;

        if (staticHold) {
            reps = null;
            if (hold == null) {
                hold = 15;
            }
        } else {
            hold = null;
            if (reps == null) {
                reps = 10;
            }
        }
        return new NormalizedPrescription(sets, reps, hold, rest);
    }

    private String sanitizePlanName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "Structured Calisthenics Plan";
        }
        String trimmed = rawName.trim();
        return trimmed.length() <= 100 ? trimmed : trimmed.substring(0, 100);
    }

    private String sanitizeDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return "Personalized structured calisthenics workout plan.";
        }
        return rawDescription.replaceAll("(?i)\\b(google\\s+)?gemini(\\s+ai)?\\b", "AI").trim();
    }

    private String sanitizeDayName(String rawName, String fallback) {
        String value = (rawName == null || rawName.trim().isEmpty()) ? fallback : rawName.trim();
        return value.length() <= 100 ? value : value.substring(0, 100);
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
        if (lower.contains("handstand")) return WorkoutSessionType.HANDSTAND;
        if (lower.contains("planche")) return WorkoutSessionType.PLANCHE;
        if (lower.contains("front lever") || lower.contains("lever")) return WorkoutSessionType.FRONT_LEVER;
        if (lower.contains("mobility") || lower.contains("stretch")) return WorkoutSessionType.MOBILITY;
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
