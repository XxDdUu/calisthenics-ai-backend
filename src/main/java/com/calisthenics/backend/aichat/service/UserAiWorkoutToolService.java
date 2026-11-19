package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanExerciseService;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Separated READ-ONLY and WRITE tool operations for the User AI Assistant.
 * Read-only tools strictly query existing plans/exercises and never mutate DB state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAiWorkoutToolService {

    // READ-ONLY tools
    public static final String TOOL_GET_MY_WORKOUT_PLAN = "get_my_workout_plan";
    public static final String TOOL_GET_WEEKLY_WORKOUT_SCHEDULE = "get_weekly_workout_schedule";
    public static final String TOOL_GET_WORKOUT_DAY_DETAILS = "get_workout_day_details";
    public static final String TOOL_GET_EXERCISE_DETAILS = "get_exercise_details";

    // WRITE tools
    public static final String TOOL_CREATE_WORKOUT_PLAN = "create_workout_plan";
    public static final String TOOL_UPDATE_WORKOUT_PLAN = "update_workout_plan";
    public static final String TOOL_ASSIGN_WORKOUT_PLAN = "assign_workout_plan";
    public static final String TOOL_ADD_EXERCISE_TO_WORKOUT = "add_exercise_to_workout";
    public static final String TOOL_REMOVE_EXERCISE_FROM_WORKOUT = "remove_exercise_from_workout";
    public static final String TOOL_MOVE_EXERCISE_BETWEEN_DAYS = "move_exercise_between_days";
    public static final String TOOL_REGENERATE_WORKOUT_PLAN = "regenerate_workout_plan";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);
    private static final Pattern GENERIC_COUNT_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s+(?:[a-zÀ-ỹ-]+\\s+){0,2}(?:exercises?|bài\\s*tập)\\b");
    private static final Pattern SETS_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})\\s*sets?\\b");
    private static final Pattern REPS_PATTERN = Pattern.compile("(?i)\\b(\\d{1,3})\\s*reps?\\b");
    private static final Pattern TIMES_REPS_PATTERN = Pattern.compile(
            "(?i)\\b\\d{1,2}\\s*[x×]\\s*(\\d{1,3})(?!\\s*(?:s|sec|secs|second|seconds))\\b");
    private static final Pattern REST_AFTER_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*(?:s|sec|secs|seconds?)?\\s*rest\\b");
    private static final Pattern REST_BEFORE_PATTERN = Pattern.compile(
            "(?i)\\brest\\s*[:=]?\\s*(?:of\\s*)?(\\d{1,3})\\s*(?:s|sec|secs|seconds?)?\\b");
    private static final Pattern HOLD_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*(?:s|sec|secs|seconds?)\\s*(?:hold\\b)?(?!\\s*rest\\b)");
    private static final Pattern HOLD_PREFIX_PATTERN = Pattern.compile(
            "(?i)\\bhold\\s*(?:for\\s*)?(\\d{1,3})\\s*(?:s|sec|secs|seconds?)\\b");
    private static final Pattern WEEKDAY_TOKEN_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(" +
                    "monday|mon|" +
                    "tuesday|tues|tue|" +
                    "wednesday|wed|" +
                    "thursday|thurs|thur|thu|" +
                    "friday|fri|" +
                    "saturday|sat|" +
                    "sunday|sun|" +
                    "thứ\\s*(?:2|hai|3|ba|4|tư|5|năm|6|sáu|7|bảy)|" +
                    "chủ\\s*nhật|chu\\s*nhat|" +
                    "t[2-7]|cn" +
                    ")(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NEGATED_WEEKDAY_PREFIX_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\s,;:()])(?:not|no|never|don't|do\\s+not|instead\\s+of|rather\\s+than|excluding|except|không\\s+phải|đừng|thay\\s+vì)(?:\\s+(?:on|to|in|into|for|at|vào|cho))?\\s+$");
    private static final Pattern SOURCE_WEEKDAY_PREFIX_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\s,;:()])(?:from|off|out\\s+of|từ|khỏi)\\s+$");
    private static final Pattern TARGET_WEEKDAY_PREFIX_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\s,;:()])(?:to|into|onto|on|for|in|vào|sang|qua|cho|tới)\\s+$");
    private static final Set<String> MOVEMENT_VARIANT_MODIFIERS = Set.of(
            "raise", "row", "pull", "push", "press", "curl", "dip", "fly",
            "extension", "negative", "eccentric", "lean", "kick", "squat",
            "lunge", "crunch", "plank", "jump", "walk", "rotation", "bridge",
            "hang", "muscle", "handstand", "planche", "lever", "flag", "sit"
    );
    private static final Set<String> DYNAMIC_MOVEMENT_WORDS = Set.of(
            "raise", "row", "pull", "push", "press", "curl", "dip", "fly",
            "squat", "lunge", "crunch", "jump", "walk", "kick", "muscle", "extension"
    );

    public record MoveDays(WorkoutDayOfWeek sourceDay, WorkoutDayOfWeek targetDay) {}

    private record WeekdayMatch(
            WorkoutDayOfWeek day,
            int start,
            int end,
            boolean negated,
            boolean sourcePrep,
            boolean targetPrep
    ) {}

    private record ExercisePrescription(Integer sets, Integer reps, Integer durationSeconds, Integer restSeconds) {}

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanService workoutPlanService;
    private final WorkoutPlanExerciseService workoutPlanExerciseService;
    private final AiWorkoutPlanService aiWorkoutPlanService;
    private final UserTimeResolver userTimeResolver;
    private final ObjectMapper objectMapper;

    public record WorkoutToolResult(
            String toolName,
            boolean readOnly,
            String intent,
            boolean success,
            String reply,
            String metadataJson
    ) {}

    private record ResolvedPlan(
            WorkoutPlan plan,
            WorkoutPlanAssignment assignment,
            String coachName
    ) {}

    public static boolean isReadOnlyTool(String toolName) {
        return TOOL_GET_MY_WORKOUT_PLAN.equals(toolName)
                || TOOL_GET_WEEKLY_WORKOUT_SCHEDULE.equals(toolName)
                || TOOL_GET_WORKOUT_DAY_DETAILS.equals(toolName)
                || TOOL_GET_EXERCISE_DETAILS.equals(toolName);
    }

    // ==================== READ-ONLY TOOLS ====================

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public WorkoutToolResult getMyWorkoutPlan(Long userId, boolean detailed, boolean isVn) {
        return retrievePlan(userId, TOOL_GET_MY_WORKOUT_PLAN, detailed, isVn);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public WorkoutToolResult getWeeklyWorkoutSchedule(Long userId, boolean detailed, boolean isVn) {
        return retrievePlan(userId, TOOL_GET_WEEKLY_WORKOUT_SCHEDULE, detailed, isVn);
    }

    private WorkoutToolResult retrievePlan(Long userId, String toolName, boolean detailed, boolean isVn) {
        try {
            Optional<ResolvedPlan> planOpt = resolveCurrentWorkoutPlan(userId);
            if (planOpt.isEmpty()) {
                return buildNoPlanResult(toolName, isVn);
            }
            ResolvedPlan resolved = planOpt.get();
            List<WorkoutPlanDay> days = loadOrderedDays(resolved.plan());
            String markdown = formatWeeklyPlanMarkdown(resolved, days, detailed, isVn);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "WORKOUT_PLAN_VIEW");
            meta.put("toolName", toolName);
            meta.put("readOnly", true);
            meta.put("found", true);
            meta.put("detailed", detailed);
            meta.put("planName", resolved.plan().getName());

            return new WorkoutToolResult(
                    toolName,
                    true,
                    "WORKOUT_PLAN_VIEW",
                    true,
                    markdown,
                    safeToJson(meta)
            );
        } catch (Exception ex) {
            log.error("Failed to retrieve workout plan for userId={}: {}", userId, ex.getMessage(), ex);
            return buildRetrievalErrorResult(toolName, "WORKOUT_PLAN_VIEW", isVn);
        }
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public WorkoutToolResult getWorkoutDayDetails(
            Long userId,
            WorkoutDayOfWeek explicitDay,
            boolean isToday,
            boolean nextSession,
            String timezone,
            boolean detailed,
            boolean isVn
    ) {
        try {
            Optional<ResolvedPlan> planOpt = resolveCurrentWorkoutPlan(userId);
            if (planOpt.isEmpty()) {
                return buildNoPlanResult(TOOL_GET_WORKOUT_DAY_DETAILS, isVn);
            }
            ResolvedPlan resolved = planOpt.get();
            List<WorkoutPlanDay> days = loadOrderedDays(resolved.plan());

            LocalDate todayDate = userTimeResolver != null ? userTimeResolver.today(timezone) : LocalDate.now();
            WorkoutDayOfWeek todayDow = toWorkoutDayOfWeek(todayDate.getDayOfWeek());

            WorkoutDayOfWeek targetDow = explicitDay;
            WorkoutPlanDay selectedDay = null;

            if (targetDow == null && nextSession) {
                selectedDay = findNextTrainingDay(days, todayDow).orElse(null);
                targetDow = selectedDay != null ? selectedDay.getDayOfWeek() : todayDow;
            } else {
                if (targetDow == null) {
                    targetDow = todayDow;
                }
                WorkoutDayOfWeek lookupDow = targetDow;
                selectedDay = days.stream()
                        .filter(d -> d.getDayOfWeek() == lookupDow)
                        .findFirst()
                        .orElse(null);
            }

            String markdown = formatSingleDayMarkdown(resolved, targetDow, selectedDay, isToday, nextSession, isVn);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "WORKOUT_DAY_VIEW");
            meta.put("toolName", TOOL_GET_WORKOUT_DAY_DETAILS);
            meta.put("readOnly", true);
            meta.put("found", true);
            meta.put("detailed", true);
            meta.put("dayOfWeek", targetDow.name());
            meta.put("planName", resolved.plan().getName());

            return new WorkoutToolResult(
                    TOOL_GET_WORKOUT_DAY_DETAILS,
                    true,
                    "WORKOUT_DAY_VIEW",
                    true,
                    markdown,
                    safeToJson(meta)
            );
        } catch (Exception ex) {
            log.error("Failed to retrieve workout day details for userId={}: {}", userId, ex.getMessage(), ex);
            return buildRetrievalErrorResult(TOOL_GET_WORKOUT_DAY_DETAILS, "WORKOUT_DAY_VIEW", isVn);
        }
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public WorkoutToolResult getExerciseDetails(Long userId, String prompt, boolean isVn) {
        try {
            List<Exercise> publishedCatalog = exerciseRepository.findAll().stream()
                    .filter(e -> e.isActive() && e.getStatus() != ExerciseStatus.DRAFT)
                    .toList();

            Optional<Exercise> matchedExercise = findBestMatchingExercise(publishedCatalog, prompt);

            // Also inspect how the exercise is prescribed in the user's current plan if one exists
            List<String> planPrescriptions = new ArrayList<>();
            Optional<ResolvedPlan> planOpt = resolveCurrentWorkoutPlan(userId);
            if (matchedExercise.isPresent() && planOpt.isPresent()) {
                Long exId = matchedExercise.get().getId();
                for (WorkoutPlanDay day : loadOrderedDays(planOpt.get().plan())) {
                    List<WorkoutPlanExercise> dayExs = loadOrderedExercises(day);
                    for (WorkoutPlanExercise pe : dayExs) {
                        if (pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), exId)) {
                            planPrescriptions.add(formatDayLabel(day.getDayOfWeek(), isVn) + ": "
                                    + formatPrescriptionSummary(pe, isVn));
                        }
                    }
                }
            }

            if (matchedExercise.isEmpty()) {
                String msg = isVn
                        ? "Tôi không tìm thấy bài tập phù hợp trong danh mục bài tập công khai. Bạn có thể nêu rõ tên bài tập muốn xem hướng dẫn không?"
                        : "I couldn't find a matching published exercise in the catalog. Please share the exact exercise name you'd like details for.";
                Map<String, Object> meta = Map.of(
                        "type", "EXERCISE_DETAILS_VIEW",
                        "toolName", TOOL_GET_EXERCISE_DETAILS,
                        "readOnly", true,
                        "found", false
                );
                return new WorkoutToolResult(TOOL_GET_EXERCISE_DETAILS, true, "EXERCISE_DETAILS_VIEW", true, msg, safeToJson(meta));
            }

            Exercise ex = matchedExercise.get();
            StringBuilder sb = new StringBuilder();
            sb.append("### ").append(ex.getName()).append("\n\n");
            sb.append("- **").append(isVn ? "Nhóm" : "Category").append("**: ").append(ex.getCategory())
                    .append(" · **").append(isVn ? "Độ khó" : "Difficulty").append("**: ").append(ex.getDifficulty()).append("\n");
            if (ex.getTargetMuscles() != null && !ex.getTargetMuscles().isBlank()) {
                sb.append("- **").append(isVn ? "Nhóm cơ tác động" : "Target Muscles").append("**: ").append(ex.getTargetMuscles()).append("\n");
            }
            if (ex.getEquipment() != null && !ex.getEquipment().isBlank()) {
                sb.append("- **").append(isVn ? "Dụng cụ" : "Equipment").append("**: ").append(ex.getEquipment()).append("\n");
            }
            if (ex.getDescription() != null && !ex.getDescription().isBlank()) {
                sb.append("\n**").append(isVn ? "Mô tả" : "Description").append("**: ").append(ex.getDescription()).append("\n");
            }
            if (ex.getInstructions() != null && !ex.getInstructions().isBlank()) {
                sb.append("\n**").append(isVn ? "Hướng dẫn kỹ thuật" : "Instructions").append("**:\n")
                        .append(ex.getInstructions()).append("\n");
            }
            if (!planPrescriptions.isEmpty()) {
                sb.append("\n**").append(isVn ? "Trong lịch tập hiện tại của bạn" : "In Your Current Plan").append("**:\n");
                for (String item : planPrescriptions) {
                    sb.append("- ").append(item).append("\n");
                }
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "EXERCISE_DETAILS_VIEW");
            meta.put("toolName", TOOL_GET_EXERCISE_DETAILS);
            meta.put("readOnly", true);
            meta.put("found", true);
            meta.put("exerciseName", ex.getName());

            return new WorkoutToolResult(TOOL_GET_EXERCISE_DETAILS, true, "EXERCISE_DETAILS_VIEW", true, sb.toString(), safeToJson(meta));
        } catch (Exception ex) {
            log.error("Failed to retrieve exercise details for userId={}: {}", userId, ex.getMessage(), ex);
            return buildRetrievalErrorResult(TOOL_GET_EXERCISE_DETAILS, "EXERCISE_DETAILS_VIEW", isVn);
        }
    }

    // ==================== WRITE TOOLS ====================

    @Transactional
    public WorkoutToolResult createWorkoutPlan(
            Long userId,
            String prompt,
            String fitnessLevel,
            Integer durationWeeks,
            String timezone,
            boolean isVn,
            boolean regenerate
    ) {
        String toolName = regenerate ? TOOL_REGENERATE_WORKOUT_PLAN : TOOL_CREATE_WORKOUT_PLAN;
        try {
            GenerateAiWorkoutPlanRequest req = GenerateAiWorkoutPlanRequest.builder()
                    .prompt(prompt)
                    .fitnessLevel(fitnessLevel != null && !fitnessLevel.isBlank() ? fitnessLevel : "INTERMEDIATE")
                    .durationWeeks(durationWeeks != null && durationWeeks > 0 ? Math.min(12, durationWeeks) : 4)
                    .build();

            AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(userId, req, timezone);

            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "WORKOUT_PLAN_PREVIEW");
            cardData.put("toolName", toolName);
            cardData.put("readOnly", false);
            cardData.put("plan", preview);
            String metadataJson = objectMapper.writeValueAsString(cardData);

            String header = regenerate
                    ? (isVn ? "📋 **Đã tạo lịch tập mới để thay thế!**" : "📋 **New Replacement Workout Plan Generated!**")
                    : (isVn ? "📋 **Đã tạo lịch tập AI!**" : "📋 **AI Workout Plan Generated!**");
            String reply = String.format("%s\n\n**%s** (%d-week %s)\n\n%s\n\n%s",
                    header,
                    preview.getPlanName(),
                    preview.getDurationWeeks(),
                    preview.getSplitType() != null ? preview.getSplitType().name().replace('_', ' ') : "Calisthenics",
                    preview.getDescription() != null ? preview.getDescription() : "",
                    isVn
                            ? "Bạn có thể xem chi tiết từng buổi tập bên dưới và xác nhận lưu vào lịch tập!"
                            : "Review each day's sessions below and commit it directly to your training calendar!");

            return new WorkoutToolResult(toolName, false, "WORKOUT_PLAN", true, reply, metadataJson);
        } catch (Exception ex) {
            log.warn("Failed to {} workout plan for userId={}: {}", toolName, userId, ex.getMessage());
            String errorReply = isVn
                    ? "Không thể tạo lịch tập mới ngay lúc này: " + ex.getMessage()
                    : "Unable to create a workout plan right now: " + ex.getMessage();
            return new WorkoutToolResult(toolName, false, "WORKOUT_PLAN", false, errorReply, null);
        }
    }

    @Transactional
    public WorkoutToolResult updateWorkoutPlan(
            Long userId,
            WorkoutDayOfWeek targetDay,
            String prompt,
            String timezone,
            boolean isVn
    ) {
        return updateWorkoutPlan(userId, targetDay, null, prompt, timezone, isVn);
    }

    @Transactional
    public WorkoutToolResult updateWorkoutPlan(
            Long userId,
            WorkoutDayOfWeek targetDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String timezone,
            boolean isVn
    ) {
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân đang hoạt động để chỉnh sửa (hoặc lịch tập hiện tại được huấn luyện viên giao). Hãy tạo lịch tập cá nhân trước hoặc nhờ huấn luyện viên điều chỉnh nhé."
                    : "You don't currently have an active personal workout plan to modify (coach-assigned plans can only be edited by your coach).";
            return new WorkoutToolResult(TOOL_UPDATE_WORKOUT_PLAN, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        WorkoutPlan plan = planOpt.get().plan();
        WorkoutDayOfWeek dayToUpdate = resolveTargetDay(extractWeekday(prompt), targetDay, selectedDayOfWeek);
        if (dayToUpdate == null) {
            return createWorkoutPlan(userId, prompt, null, 4, timezone, isVn, true);
        }

        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        Map<WorkoutDayOfWeek, List<Long>> preSnapshot = captureDayExerciseIdsSnapshot(days);
        WorkoutPlanDay day = findDay(days, dayToUpdate);

        if (day == null) {
            String msg = isVn
                    ? "Không tìm thấy ngày tập tương ứng trong lịch tập của bạn."
                    : "Could not locate that training day in your workout plan.";
            return new WorkoutToolResult(TOOL_UPDATE_WORKOUT_PLAN, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        String requestedTitle = extractRequestedDayTitle(prompt);
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            day.setName(requestedTitle);
            if (requestedTitle.toLowerCase(Locale.US).contains("rest")) {
                day.setRestDay(true);
                day.setSessionType(WorkoutSessionType.REST);
            } else {
                day.setRestDay(false);
                day.setSessionType(inferSessionType(requestedTitle));
            }
            workoutPlanDayRepository.save(day);
        }

        Map<WorkoutDayOfWeek, WorkoutPlanDay> verifiedDays = reloadAndVerifyMutation(
                plan.getId(), preSnapshot, Set.of(dayToUpdate), Map.of(), Map.of());
        WorkoutPlan reloaded = workoutPlanRepository.findWithDetailsById(plan.getId()).orElse(plan);
        WorkoutPlanDay verifiedDay = verifiedDays.getOrDefault(dayToUpdate, day);

        String dayFormatted = formatSingleDayMarkdown(
                new ResolvedPlan(reloaded, null, null), dayToUpdate, verifiedDay, false, false, isVn);
        String header = isVn
                ? "✅ **Đã cập nhật lịch tập cho " + formatDayLabel(dayToUpdate, true) + "!**\n\n"
                : "✅ **Updated " + formatDayLabel(dayToUpdate, false) + "'s workout!**\n\n";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_UPDATED");
        meta.put("toolName", TOOL_UPDATE_WORKOUT_PLAN);
        meta.put("readOnly", false);
        meta.put("mutated", true);
        meta.put("dayOfWeek", dayToUpdate.name());
        return new WorkoutToolResult(TOOL_UPDATE_WORKOUT_PLAN, false, "WORKOUT_PLAN_UPDATE", true, header + dayFormatted, safeToJson(meta));
    }

    @Transactional
    public WorkoutToolResult addExerciseToWorkout(
            Long userId,
            WorkoutDayOfWeek explicitDay,
            String prompt,
            String timezone,
            boolean isVn
    ) {
        return addExerciseToWorkout(userId, explicitDay, null, prompt, null, List.of(), timezone, isVn);
    }

    @Transactional
    public WorkoutToolResult addExerciseToWorkout(
            Long userId,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            String timezone,
            boolean isVn
    ) {
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân để thêm bài tập. Hãy tạo lịch tập trước khi thêm bài mới."
                    : "You don't have an active personal workout plan to add exercises to yet.";
            return buildNonMutatingFailureResult(TOOL_ADD_EXERCISE_TO_WORKOUT, msg, null, null);
        }

        List<Exercise> publishedCatalog = loadPublishedCatalog();
        WorkoutDayOfWeek fromPrompt = extractWeekday(prompt);
        WorkoutDayOfWeek dayToModify = resolveTargetDay(fromPrompt, explicitDay, selectedDayOfWeek);

        // Identify the requested exercise(s) strictly before modifying any state.
        Optional<Exercise> matchedExercise = findRequestedExercise(
                publishedCatalog, prompt, explicitExerciseName, history);
        ExerciseCategory requestedCategory = detectCategory(prompt);
        boolean genericCategoryRequest = matchedExercise.isEmpty() && isGenericCategoryExerciseRequest(prompt, requestedCategory);

        if (dayToModify == null) {
            String pendingEx = matchedExercise.map(Exercise::getName).orElse(null);
            String msg;
            if (pendingEx != null) {
                msg = isVn
                        ? "Bạn muốn thêm **" + pendingEx + "** vào thứ mấy trong tuần (ví dụ: Thứ Hai, Thứ Ba, ...)?"
                        : "Which weekday would you like to add **" + pendingEx + "** to (for example: Monday, Tuesday, ...)?";
            } else {
                msg = isVn
                        ? "Vui lòng cho biết tên bài tập và thứ bạn muốn thêm vào lịch tập (ví dụ: \"Thêm Front Lever Raises vào Thứ Ba\")."
                        : "Please specify both the exercise name and the weekday you want to add it to (for example: \"Add Front Lever Raises to Tuesday\").";
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "WORKOUT_PLAN_CLARIFICATION");
            meta.put("toolName", TOOL_ADD_EXERCISE_TO_WORKOUT);
            meta.put("readOnly", false);
            meta.put("mutated", false);
            meta.put("pendingAction", "ADD");
            if (pendingEx != null) {
                meta.put("exerciseName", pendingEx);
            }
            return new WorkoutToolResult(TOOL_ADD_EXERCISE_TO_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false, msg, safeToJson(meta));
        }

        WorkoutPlan plan = planOpt.get().plan();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        WorkoutPlanDay day = findDay(days, dayToModify);
        if (day == null) {
            return buildNonMutatingFailureResult(TOOL_ADD_EXERCISE_TO_WORKOUT,
                    isVn ? "Không tìm thấy ngày tập." : "Could not find the target workout day.",
                    dayToModify, null);
        }

        Set<Long> existingExerciseIds = new HashSet<>();
        for (WorkoutPlanExercise pe : loadOrderedExercises(day)) {
            if (pe.getExercise() != null) {
                existingExerciseIds.add(pe.getExercise().getId());
            }
        }

        List<Exercise> toAdd = new ArrayList<>();
        if (matchedExercise.isPresent()) {
            Exercise exact = matchedExercise.get();
            if (existingExerciseIds.contains(exact.getId())) {
                String msg = isVn
                        ? "Bài tập **" + exact.getName() + "** đã có trong lịch tập **" + formatDayLabel(dayToModify, true)
                                + "** rồi. Không thêm trùng hoặc tự ý thay thế bằng bài tập khác."
                        : "**" + exact.getName() + "** is already scheduled on **" + formatDayLabel(dayToModify, false)
                                + "**. No duplicate or substitute exercise was added.";
                return buildNonMutatingFailureResult(TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "ALREADY_EXISTS");
            }
            toAdd.add(exact);
        } else if (genericCategoryRequest) {
            int requestedCount = parseRequestedGenericCount(prompt, 1);
            publishedCatalog.stream()
                    .filter(e -> !existingExerciseIds.contains(e.getId()))
                    .filter(e -> e.getCategory() == requestedCategory)
                    .limit(requestedCount)
                    .forEach(toAdd::add);
        }

        if (toAdd.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy bài tập khớp chính xác trong danh mục công khai để thêm vào **"
                            + formatDayLabel(dayToModify, true) + "**. Không có thay đổi nào được thực hiện."
                    : "I couldn't find a matching published exercise in the catalog for that request. Please specify the exact exercise name; no changes were made to **"
                            + formatDayLabel(dayToModify, false) + "**.";
            return buildNonMutatingFailureResult(TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "EXERCISE_NOT_FOUND");
        }

        Map<WorkoutDayOfWeek, List<Long>> preSnapshot = captureDayExerciseIdsSnapshot(days);

        if (day.isRestDay()) {
            day.setRestDay(false);
            if (day.getName() == null || day.getName().isBlank() || day.getName().toLowerCase(Locale.US).contains("rest")) {
                day.setName(requestedCategory != null ? requestedCategory.name() + " Day" : "Custom Training");
            }
            if (day.getSessionType() == null || day.getSessionType() == WorkoutSessionType.REST) {
                day.setSessionType(requestedCategory != null ? inferSessionTypeFromCategory(requestedCategory) : WorkoutSessionType.CUSTOM);
            }
            workoutPlanDayRepository.save(day);
        }

        ExercisePrescription parsedPrescription = parseExercisePrescription(prompt);
        List<Long> expectedAddedAssignmentIds = new ArrayList<>();
        for (Exercise ex : toAdd) {
            WorkoutPlanExerciseRequest addReq = buildAddExerciseRequest(ex, parsedPrescription);
            var created = workoutPlanExerciseService.add(userId, plan.getId(), day.getId(), addReq);
            if (created != null && created.getId() != null) {
                expectedAddedAssignmentIds.add(created.getId());
            }
        }

        Map<WorkoutDayOfWeek, WorkoutPlanDay> verifiedDays = reloadAndVerifyMutation(
                plan.getId(),
                preSnapshot,
                Set.of(dayToModify),
                Map.of(dayToModify, expectedAddedAssignmentIds),
                Map.of()
        );

        WorkoutPlan reloaded = workoutPlanRepository.findWithDetailsById(plan.getId()).orElse(plan);
        WorkoutPlanDay verifiedDay = verifiedDays.getOrDefault(dayToModify, day);
        List<WorkoutPlanExercise> verifiedDayExercises = loadOrderedExercises(verifiedDay);
        List<String> persistedAddedNames = verifiedDayExercises.stream()
                .filter(pe -> expectedAddedAssignmentIds.contains(pe.getId()))
                .map(pe -> pe.getExercise().getName())
                .toList();

        String dayMarkdown = formatSingleDayMarkdown(new ResolvedPlan(reloaded, null, null), dayToModify, verifiedDay, false, false, isVn);
        String header = isVn
                ? "✅ **Đã thêm " + persistedAddedNames.size() + " bài tập vào " + formatDayLabel(dayToModify, true) + "**: "
                        + String.join(", ", persistedAddedNames) + "\n\n"
                : "✅ **Added " + persistedAddedNames.size() + " exercise(s) to " + formatDayLabel(dayToModify, false) + "**: "
                        + String.join(", ", persistedAddedNames) + "\n\n";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_UPDATED");
        meta.put("toolName", TOOL_ADD_EXERCISE_TO_WORKOUT);
        meta.put("readOnly", false);
        meta.put("mutated", true);
        meta.put("addedExercises", persistedAddedNames);
        meta.put("dayOfWeek", dayToModify.name());
        return new WorkoutToolResult(TOOL_ADD_EXERCISE_TO_WORKOUT, false, "WORKOUT_PLAN_UPDATE", true, header + dayMarkdown, safeToJson(meta));
    }

    @Transactional
    public WorkoutToolResult removeExerciseFromWorkout(
            Long userId,
            WorkoutDayOfWeek explicitDay,
            String prompt,
            List<AiMessage> history,
            boolean isVn
    ) {
        return removeExerciseFromWorkout(userId, explicitDay, null, prompt, null, history, isVn);
    }

    @Transactional
    public WorkoutToolResult removeExerciseFromWorkout(
            Long userId,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân để xóa bài tập."
                    : "You don't have an active personal workout plan to remove exercises from.";
            return buildNonMutatingFailureResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, msg, null, null);
        }

        WorkoutPlan plan = planOpt.get().plan();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        WorkoutDayOfWeek dayFilter = resolveTargetDay(extractWeekday(prompt), explicitDay, selectedDayOfWeek);

        String effectiveQuery = resolveEffectiveExerciseQuery(prompt, explicitExerciseName, history);
        if (dayFilter == null) {
            List<WorkoutPlanExercise> matchesAcrossDays = new ArrayList<>();
            for (WorkoutPlanDay d : days) {
                findBestMatchingPlanExercise(loadOrderedExercises(d), effectiveQuery)
                        .ifPresent(matchesAcrossDays::add);
            }
            if (matchesAcrossDays.isEmpty()) {
                String msg = isVn
                        ? "Không tìm thấy bài tập cần xóa trong lịch tập của bạn. Vui lòng nêu rõ tên bài tập và ngày tập."
                        : "I couldn't find that exercise in your current workout plan. Please specify the exact exercise name and weekday.";
                return buildNonMutatingFailureResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, msg, null, "EXERCISE_NOT_FOUND");
            }
            if (matchesAcrossDays.size() > 1) {
                String matchedDays = matchesAcrossDays.stream()
                        .map(pe -> formatDayLabel(pe.getWorkoutPlanDay().getDayOfWeek(), isVn))
                        .distinct()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                String exName = matchesAcrossDays.get(0).getExercise().getName();
                String msg = isVn
                        ? "Bài tập **" + exName + "** xuất hiện ở nhiều ngày (" + matchedDays + "). Bạn muốn xóa ở ngày nào?"
                        : "**" + exName + "** appears on multiple days (" + matchedDays + "). Which weekday would you like to remove it from?";
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("type", "WORKOUT_PLAN_CLARIFICATION");
                meta.put("toolName", TOOL_REMOVE_EXERCISE_FROM_WORKOUT);
                meta.put("readOnly", false);
                meta.put("mutated", false);
                meta.put("pendingAction", "REMOVE");
                meta.put("exerciseName", exName);
                return new WorkoutToolResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false, msg, safeToJson(meta));
            }
            dayFilter = matchesAcrossDays.get(0).getWorkoutPlanDay().getDayOfWeek();
        }

        WorkoutPlanDay targetDayEntity = findDay(days, dayFilter);
        if (targetDayEntity == null) {
            return buildNonMutatingFailureResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT,
                    isVn ? "Không tìm thấy ngày tập cần xóa." : "Could not find the specified workout day.",
                    dayFilter, "DAY_NOT_FOUND");
        }

        Optional<WorkoutPlanExercise> matchOpt = findBestMatchingPlanExercise(
                loadOrderedExercises(targetDayEntity), effectiveQuery);
        if (matchOpt.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy bài tập phù hợp trong **" + formatDayLabel(dayFilter, true) + "**. Không có thay đổi nào được thực hiện ở các ngày khác."
                    : "That exercise was not found on **" + formatDayLabel(dayFilter, false) + "**. No changes were made to any other day.";
            return buildNonMutatingFailureResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, msg, dayFilter, "EXERCISE_NOT_FOUND");
        }

        Map<WorkoutDayOfWeek, List<Long>> preSnapshot = captureDayExerciseIdsSnapshot(days);

        WorkoutPlanExercise toRemove = matchOpt.get();
        Long removedId = toRemove.getId();
        String removedName = toRemove.getExercise().getName();
        workoutPlanExerciseService.remove(userId, plan.getId(), targetDayEntity.getId(), removedId);
        resequenceDayExercises(targetDayEntity.getId());

        Map<WorkoutDayOfWeek, WorkoutPlanDay> verifiedDays = reloadAndVerifyMutation(
                plan.getId(),
                preSnapshot,
                Set.of(dayFilter),
                Map.of(),
                Map.of(dayFilter, List.of(removedId))
        );

        WorkoutPlan reloaded = workoutPlanRepository.findWithDetailsById(plan.getId()).orElse(plan);
        WorkoutPlanDay verifiedDay = verifiedDays.getOrDefault(dayFilter, targetDayEntity);
        String dayMarkdown = formatSingleDayMarkdown(new ResolvedPlan(reloaded, null, null), dayFilter, verifiedDay, false, false, isVn);

        String header = isVn
                ? "✅ Đã xóa **" + removedName + "** khỏi **" + formatDayLabel(dayFilter, true) + "** trong lịch tập của bạn.\n\n"
                : "✅ Removed **" + removedName + "** from **" + formatDayLabel(dayFilter, false) + "** in your workout plan.\n\n";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_UPDATED");
        meta.put("toolName", TOOL_REMOVE_EXERCISE_FROM_WORKOUT);
        meta.put("readOnly", false);
        meta.put("mutated", true);
        meta.put("removedExercise", removedName);
        meta.put("dayOfWeek", dayFilter.name());
        return new WorkoutToolResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, "WORKOUT_PLAN_UPDATE", true, header + dayMarkdown, safeToJson(meta));
    }

    @Transactional
    public WorkoutToolResult moveExerciseBetweenDays(
            Long userId,
            WorkoutDayOfWeek explicitSourceDay,
            WorkoutDayOfWeek explicitTargetDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân để di chuyển bài tập."
                    : "You don't have an active personal workout plan to move exercises in.";
            return buildNonMutatingFailureResult(TOOL_MOVE_EXERCISE_BETWEEN_DAYS, msg, null, null);
        }

        MoveDays fromPrompt = extractMoveDays(prompt);
        WorkoutDayOfWeek sourceDay = fromPrompt.sourceDay() != null
                ? fromPrompt.sourceDay()
                : (explicitSourceDay != null ? explicitSourceDay : selectedDayOfWeek);
        WorkoutDayOfWeek targetDay = fromPrompt.targetDay() != null
                ? fromPrompt.targetDay()
                : explicitTargetDay;

        if (sourceDay == null || targetDay == null) {
            String msg = isVn
                    ? "Vui lòng nêu rõ cả ngày nguồn và ngày đích khi di chuyển bài tập (ví dụ: \"Chuyển Front Lever Raises từ Thứ Hai sang Thứ Ba\")."
                    : "Please specify both the source day and destination day when moving an exercise (for example: \"Move Front Lever Raises from Monday to Tuesday\").";
            return buildNonMutatingFailureResult(TOOL_MOVE_EXERCISE_BETWEEN_DAYS, msg, targetDay, "MISSING_WEEKDAY");
        }

        if (sourceDay == targetDay) {
            String msg = isVn
                    ? "Ngày nguồn và ngày đích giống nhau (**" + formatDayLabel(sourceDay, true) + "**), nên không có thay đổi nào được thực hiện."
                    : "Source and destination days are both **" + formatDayLabel(sourceDay, false) + "**, so no changes were made.";
            return buildNonMutatingFailureResult(TOOL_MOVE_EXERCISE_BETWEEN_DAYS, msg, targetDay, "SAME_DAY");
        }

        WorkoutPlan plan = planOpt.get().plan();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        WorkoutPlanDay sourceDayEntity = findDay(days, sourceDay);
        WorkoutPlanDay targetDayEntity = findDay(days, targetDay);
        if (sourceDayEntity == null || targetDayEntity == null) {
            return buildNonMutatingFailureResult(TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                    isVn ? "Không tìm thấy ngày tập nguồn hoặc đích." : "Could not locate the source or destination workout day.",
                    targetDay, "DAY_NOT_FOUND");
        }

        String effectiveQuery = resolveEffectiveExerciseQuery(prompt, explicitExerciseName, history);
        Optional<WorkoutPlanExercise> sourceMatchOpt = findBestMatchingPlanExercise(
                loadOrderedExercises(sourceDayEntity), effectiveQuery);
        if (sourceMatchOpt.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy bài tập cần chuyển trong **" + formatDayLabel(sourceDay, true) + "**. Không có thay đổi nào được thực hiện."
                    : "That exercise was not found on **" + formatDayLabel(sourceDay, false) + "**. No changes were made to your workout plan.";
            return buildNonMutatingFailureResult(TOOL_MOVE_EXERCISE_BETWEEN_DAYS, msg, sourceDay, "EXERCISE_NOT_FOUND");
        }

        WorkoutPlanExercise sourceEx = sourceMatchOpt.get();
        Exercise exercise = sourceEx.getExercise();
        Long removedSourceId = sourceEx.getId();

        Map<WorkoutDayOfWeek, List<Long>> preSnapshot = captureDayExerciseIdsSnapshot(days);

        boolean targetAlreadyHas = loadOrderedExercises(targetDayEntity).stream()
                .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), exercise.getId()));

        WorkoutPlanExerciseRequest moveAddReq = null;
        if (!targetAlreadyHas) {
            moveAddReq = new WorkoutPlanExerciseRequest();
            moveAddReq.setExerciseId(exercise.getId());
            moveAddReq.setSets(sourceEx.getSets() != null ? sourceEx.getSets() : 3);
            moveAddReq.setReps(sourceEx.getReps());
            moveAddReq.setDurationSeconds(sourceEx.getDurationSeconds());
            moveAddReq.setRestSeconds(sourceEx.getRestSeconds() != null ? sourceEx.getRestSeconds() : 90);
            moveAddReq.setNotes(sourceEx.getNotes());
        }

        workoutPlanExerciseService.remove(userId, plan.getId(), sourceDayEntity.getId(), removedSourceId);
        resequenceDayExercises(sourceDayEntity.getId());

        List<Long> expectedTargetAddedIds = new ArrayList<>();
        if (moveAddReq != null) {
            if (targetDayEntity.isRestDay()) {
                targetDayEntity.setRestDay(false);
                if (targetDayEntity.getName() == null || targetDayEntity.getName().isBlank()
                        || targetDayEntity.getName().toLowerCase(Locale.US).contains("rest")) {
                    targetDayEntity.setName(exercise.getCategory() != null
                            ? exercise.getCategory().name() + " Day" : "Custom Training");
                }
                if (targetDayEntity.getSessionType() == null || targetDayEntity.getSessionType() == WorkoutSessionType.REST) {
                    targetDayEntity.setSessionType(inferSessionTypeFromCategory(exercise.getCategory()));
                }
                workoutPlanDayRepository.save(targetDayEntity);
            }
            var created = workoutPlanExerciseService.add(userId, plan.getId(), targetDayEntity.getId(), moveAddReq);
            if (created != null && created.getId() != null) {
                expectedTargetAddedIds.add(created.getId());
            }
        }

        Map<WorkoutDayOfWeek, WorkoutPlanDay> verifiedDays = reloadAndVerifyMutation(
                plan.getId(),
                preSnapshot,
                Set.of(sourceDay, targetDay),
                expectedTargetAddedIds.isEmpty() ? Map.of() : Map.of(targetDay, expectedTargetAddedIds),
                Map.of(sourceDay, List.of(removedSourceId))
        );

        WorkoutPlan reloaded = workoutPlanRepository.findWithDetailsById(plan.getId()).orElse(plan);
        WorkoutPlanDay verifiedSource = verifiedDays.getOrDefault(sourceDay, sourceDayEntity);
        WorkoutPlanDay verifiedTarget = verifiedDays.getOrDefault(targetDay, targetDayEntity);

        StringBuilder sb = new StringBuilder();
        if (isVn) {
            sb.append("✅ Đã chuyển **").append(exercise.getName()).append("** từ **")
                    .append(formatDayLabel(sourceDay, true)).append("** sang **")
                    .append(formatDayLabel(targetDay, true)).append("**");
            if (targetAlreadyHas) {
                sb.append(" (bài tập đã có sẵn ở ").append(formatDayLabel(targetDay, true)).append(")");
            }
            sb.append(".\n\n");
        } else {
            sb.append("✅ Moved **").append(exercise.getName()).append("** from **")
                    .append(formatDayLabel(sourceDay, false)).append("** to **")
                    .append(formatDayLabel(targetDay, false)).append("**");
            if (targetAlreadyHas) {
                sb.append(" (kept existing entry on ").append(formatDayLabel(targetDay, false)).append(")");
            }
            sb.append(".\n\n");
        }
        appendDaySection(sb, sourceDay, verifiedSource, false, isVn);
        appendDaySection(sb, targetDay, verifiedTarget, false, isVn);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_UPDATED");
        meta.put("toolName", TOOL_MOVE_EXERCISE_BETWEEN_DAYS);
        meta.put("readOnly", false);
        meta.put("mutated", true);
        meta.put("movedExercise", exercise.getName());
        meta.put("sourceDayOfWeek", sourceDay.name());
        meta.put("dayOfWeek", targetDay.name());
        return new WorkoutToolResult(TOOL_MOVE_EXERCISE_BETWEEN_DAYS, false, "WORKOUT_PLAN_UPDATE", true, sb.toString().trim(), safeToJson(meta));
    }

    @Transactional
    public WorkoutToolResult assignWorkoutPlan(Long userId, Long explicitPlanId, String prompt, boolean isVn) {
        List<WorkoutPlan> plans = workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(userId);
        Optional<WorkoutPlan> target = Optional.empty();
        if (explicitPlanId != null) {
            target = plans.stream().filter(p -> Objects.equals(p.getId(), explicitPlanId)).findFirst();
        }
        if (target.isEmpty() && prompt != null && !prompt.isBlank()) {
            String lower = prompt.toLowerCase(Locale.US);
            target = plans.stream()
                    .filter(p -> p.getName() != null && lower.contains(p.getName().toLowerCase(Locale.US)))
                    .findFirst();
        }
        if (target.isEmpty() && !plans.isEmpty()) {
            target = Optional.of(plans.get(0));
        }

        if (target.isEmpty()) {
            return buildNoPlanResult(TOOL_ASSIGN_WORKOUT_PLAN, isVn);
        }

        workoutPlanService.activate(userId, target.get().getId());
        String msg = isVn
                ? "✅ Đã kích hoạt lịch tập **" + target.get().getName() + "** làm lịch tập hiện tại của bạn."
                : "✅ Activated **" + target.get().getName() + "** as your current workout plan.";
        Map<String, Object> meta = Map.of(
                "type", "WORKOUT_PLAN_UPDATED",
                "toolName", TOOL_ASSIGN_WORKOUT_PLAN,
                "readOnly", false,
                "planName", target.get().getName()
        );
        return new WorkoutToolResult(TOOL_ASSIGN_WORKOUT_PLAN, false, "WORKOUT_PLAN_UPDATE", true, msg, safeToJson(meta));
    }

    // ==================== RESOLUTION & FORMATTING HELPERS ====================

    private Optional<ResolvedPlan> resolveCurrentWorkoutPlan(Long userId) {
        CoachClientRelationship activeCoach = relationshipRepository
                .findFirstByClientIdAndStatusOrderByUpdatedAtDesc(userId, CoachClientRelationshipStatus.ACTIVE)
                .orElse(null);

        if (activeCoach != null) {
            List<WorkoutPlanAssignment> assignments = assignmentRepository.findByClientIdAndStatusOrderByCreatedAtDesc(
                    userId, WorkoutPlanAssignmentStatus.ACTIVE);
            for (WorkoutPlanAssignment assignment : assignments) {
                if (assignment.getRelationship() != null
                        && Objects.equals(assignment.getRelationship().getId(), activeCoach.getId())
                        && assignment.getWorkoutPlan() != null) {
                    WorkoutPlan detailedPlan = workoutPlanRepository
                            .findWithDetailsById(assignment.getWorkoutPlan().getId())
                            .orElse(assignment.getWorkoutPlan());
                    String coachName = assignment.getCoach() != null ? assignment.getCoach().getFullName() : null;
                    return Optional.of(new ResolvedPlan(detailedPlan, assignment, coachName));
                }
            }
        }

        Optional<WorkoutPlan> activePersonal = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
        if (activePersonal.isPresent()) {
            WorkoutPlan loaded = workoutPlanRepository.findWithDetailsById(activePersonal.get().getId())
                    .orElse(activePersonal.get());
            return Optional.of(new ResolvedPlan(loaded, null, null));
        }

        List<WorkoutPlan> allPersonal = workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(userId);
        if (!allPersonal.isEmpty()) {
            WorkoutPlan first = allPersonal.get(0);
            WorkoutPlan loaded = workoutPlanRepository.findWithDetailsById(first.getId()).orElse(first);
            return Optional.of(new ResolvedPlan(loaded, null, null));
        }

        return Optional.empty();
    }

    private Optional<ResolvedPlan> resolveOwnedPersonalPlan(Long userId) {
        Optional<WorkoutPlan> activePersonal = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId);
        if (activePersonal.isPresent()) {
            WorkoutPlan loaded = workoutPlanRepository.findWithDetailsById(activePersonal.get().getId())
                    .orElse(activePersonal.get());
            return Optional.of(new ResolvedPlan(loaded, null, null));
        }
        List<WorkoutPlan> allPersonal = workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(userId);
        if (!allPersonal.isEmpty()) {
            WorkoutPlan loaded = workoutPlanRepository.findWithDetailsById(allPersonal.get(0).getId())
                    .orElse(allPersonal.get(0));
            return Optional.of(new ResolvedPlan(loaded, null, null));
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
        if (day == null) {
            return List.of();
        }
        if (day.getId() != null) {
            List<WorkoutPlanExercise> repoExercises = workoutPlanExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
            if (repoExercises != null && !repoExercises.isEmpty()) {
                return repoExercises;
            }
        }
        if (day.getExercises() != null) {
            return day.getExercises().stream()
                    .sorted(Comparator.comparing(WorkoutPlanExercise::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }
        return List.of();
    }

    private String formatWeeklyPlanMarkdown(
            ResolvedPlan resolved,
            List<WorkoutPlanDay> existingDays,
            boolean detailed,
            boolean isVn
    ) {
        WorkoutPlan plan = resolved.plan();
        Map<WorkoutDayOfWeek, WorkoutPlanDay> dayMap = new EnumMap<>(WorkoutDayOfWeek.class);
        for (WorkoutPlanDay d : existingDays) {
            if (d.getDayOfWeek() != null) {
                dayMap.put(d.getDayOfWeek(), d);
            }
        }

        long trainingDays = Arrays.stream(WorkoutDayOfWeek.values())
                .filter(dow -> {
                    WorkoutPlanDay d = dayMap.get(dow);
                    return d != null && !d.isRestDay();
                })
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(plan.getName() != null ? plan.getName() : (isVn ? "Lịch Tập Hiện Tại" : "Current Workout Plan")).append("\n\n");

        if (plan.getDescription() != null && !plan.getDescription().isBlank()) {
            sb.append(plan.getDescription()).append("\n\n");
        }

        sb.append("- **").append(isVn ? "Phân bổ lịch tập (Split)" : "Training Split").append("**: ")
                .append(plan.getSplitType() != null ? plan.getSplitType().name().replace('_', ' ') : "CUSTOM").append("\n");
        sb.append("- **").append(isVn ? "Tần suất tập luyện" : "Training Frequency").append("**: ")
                .append(trainingDays).append(isVn ? " buổi / tuần (Lịch 7 ngày từ Thứ Hai đến Chủ Nhật)" : " training day(s)/week (Monday–Sunday)").append("\n");

        if (resolved.coachName() != null && !resolved.coachName().isBlank()) {
            sb.append("- **").append(isVn ? "Huấn luyện viên phụ trách" : "Assigned Coach").append("**: ")
                    .append(resolved.coachName()).append("\n");
            if (resolved.assignment() != null && resolved.assignment().getNotes() != null && !resolved.assignment().getNotes().isBlank()) {
                sb.append("- **").append(isVn ? "Ghi chú của HLV" : "Coach Notes").append("**: ")
                        .append(resolved.assignment().getNotes()).append("\n");
            }
        }
        if (plan.getUpdatedAt() != null) {
            sb.append("- **").append(isVn ? "Cập nhật lần cuối" : "Last Updated").append("**: ")
                    .append(DATE_FORMAT.format(plan.getUpdatedAt())).append("\n");
        }

        sb.append("\n---\n\n");

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            WorkoutPlanDay day = dayMap.get(dow);
            appendDaySection(sb, dow, day, detailed, isVn);
        }

        return sb.toString().trim();
    }

    private String formatSingleDayMarkdown(
            ResolvedPlan resolved,
            WorkoutDayOfWeek targetDow,
            WorkoutPlanDay day,
            boolean isToday,
            boolean nextSession,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder();
        String dayName = formatDayLabel(targetDow, isVn);
        String contextPrefix = isToday
                ? (isVn ? "Lịch tập hôm nay — " : "Today's Workout — ")
                : (nextSession ? (isVn ? "Buổi tập tiếp theo — " : "Next Training Session — ") : "");

        sb.append("### ").append(contextPrefix).append(dayName).append("\n\n");
        sb.append("**").append(isVn ? "Kế hoạch" : "Plan").append("**: ").append(resolved.plan().getName());
        if (resolved.coachName() != null && !resolved.coachName().isBlank()) {
            sb.append(" · **").append(isVn ? "HLV" : "Coach").append("**: ").append(resolved.coachName());
        }
        if (resolved.plan().getUpdatedAt() != null) {
            sb.append(" · **").append(isVn ? "Cập nhật" : "Updated").append("**: ")
                    .append(DATE_FORMAT.format(resolved.plan().getUpdatedAt()));
        }
        sb.append("\n\n");

        appendDaySection(sb, targetDow, day, true, isVn);
        return sb.toString().trim();
    }

    private void appendDaySection(
            StringBuilder sb,
            WorkoutDayOfWeek dow,
            WorkoutPlanDay day,
            boolean includeInstructions,
            boolean isVn
    ) {
        String dayLabel = formatDayLabel(dow, isVn);
        boolean isRest = day == null || day.isRestDay();
        if (isRest) {
            String restLabel = isVn ? "Ngày nghỉ & phục hồi (Rest Day)" : "Rest Day";
            sb.append("#### ").append(dayLabel).append(" — ").append(restLabel).append("\n");
            if (day != null && day.getDescription() != null && !day.getDescription().isBlank()) {
                sb.append("- _").append(day.getDescription()).append("_\n");
            } else {
                sb.append("- ").append(isVn
                        ? "Nghỉ ngơi, giãn cơ nhẹ và phục hồi hệ thần kinh - cơ bắp."
                        : "Active recovery, mobility work, and rest.")
                        .append("\n");
            }
            sb.append("\n");
            return;
        }

        String focus = (day.getName() != null && !day.getName().isBlank())
                ? day.getName()
                : (day.getSessionType() != null ? day.getSessionType().name().replace('_', ' ') : (isVn ? "Buổi tập" : "Training Session"));
        sb.append("#### ").append(dayLabel).append(" — ").append(focus);
        if (day.getSessionType() != null && !focus.toUpperCase(Locale.US).contains(day.getSessionType().name().replace('_', ' '))) {
            sb.append(" (").append(day.getSessionType().name().replace('_', ' ')).append(")");
        }
        sb.append("\n");

        if (day.getDescription() != null && !day.getDescription().isBlank()) {
            sb.append("_").append(day.getDescription()).append("_\n");
        }

        List<WorkoutPlanExercise> exercises = loadOrderedExercises(day);
        if (exercises.isEmpty()) {
            sb.append("- ").append(isVn ? "Chưa có bài tập nào được thêm vào ngày này." : "No exercises scheduled for this day yet.").append("\n\n");
            return;
        }

        int index = 1;
        for (WorkoutPlanExercise pe : exercises) {
            Exercise ex = pe.getExercise();
            String exName = ex != null ? ex.getName() : "Exercise";
            sb.append(index++).append(". **").append(exName).append("** — ")
                    .append(formatPrescriptionSummary(pe, isVn)).append("\n");
            if (pe.getNotes() != null && !pe.getNotes().isBlank()) {
                sb.append("   - **").append(isVn ? "Ghi chú" : "Notes").append("**: ").append(pe.getNotes()).append("\n");
            }
            if (includeInstructions && ex != null && ex.getInstructions() != null && !ex.getInstructions().isBlank()) {
                sb.append("   - **").append(isVn ? "Hướng dẫn" : "Instructions").append("**: ").append(ex.getInstructions()).append("\n");
            }
        }
        sb.append("\n");
    }

    private String formatPrescriptionSummary(WorkoutPlanExercise pe, boolean isVn) {
        List<String> parts = new ArrayList<>();
        if (pe.getSets() != null) {
            parts.add(pe.getSets() + (isVn ? " hiệp" : " sets"));
        }
        if (pe.getReps() != null) {
            parts.add(pe.getReps() + (isVn ? " lần" : " reps"));
        }
        if (pe.getDurationSeconds() != null) {
            parts.add(pe.getDurationSeconds() + (isVn ? " giây giữ" : "s hold"));
        }
        String volume = String.join(" × ", parts);
        if (pe.getRestSeconds() != null) {
            volume += (isVn ? " · Nghỉ " : " · Rest ") + pe.getRestSeconds() + "s";
        }
        return volume;
    }

    private Optional<WorkoutPlanDay> findNextTrainingDay(List<WorkoutPlanDay> days, WorkoutDayOfWeek todayDow) {
        if (days == null || days.isEmpty()) {
            return Optional.empty();
        }
        Map<WorkoutDayOfWeek, WorkoutPlanDay> map = new EnumMap<>(WorkoutDayOfWeek.class);
        for (WorkoutPlanDay d : days) {
            if (d.getDayOfWeek() != null) {
                map.put(d.getDayOfWeek(), d);
            }
        }
        int startOrder = todayDow.getOrder();
        for (int offset = 0; offset < 7; offset++) {
            int order = ((startOrder - 1 + offset) % 7) + 1;
            WorkoutDayOfWeek candidateDow = fromOrder(order);
            WorkoutPlanDay candidate = map.get(candidateDow);
            if (candidate != null && !candidate.isRestDay() && !loadOrderedExercises(candidate).isEmpty()) {
                return Optional.of(candidate);
            }
        }
        return days.stream().filter(d -> !d.isRestDay()).findFirst();
    }

    private WorkoutToolResult buildNoPlanResult(String toolName, boolean isVn) {
        String msg = isVn
                ? "Bạn hiện chưa có lịch tập nào được gán hoặc đang kích hoạt. Nếu bạn muốn, hãy yêu cầu tôi tạo một lịch tập mới phù hợp với trình độ và mục tiêu của bạn!"
                : "You currently have no assigned or active workout plan. Whenever you're ready, ask me to create a new workout plan tailored to your goals!";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_VIEW");
        meta.put("toolName", toolName);
        meta.put("readOnly", true);
        meta.put("found", false);
        return new WorkoutToolResult(toolName, true, "WORKOUT_PLAN_VIEW", true, msg, safeToJson(meta));
    }

    private WorkoutToolResult buildRetrievalErrorResult(String toolName, String intent, boolean isVn) {
        String msg = isVn
                ? "Tôi không thể truy xuất lịch tập hiện tại do lỗi hệ thống. Dữ liệu lịch tập của bạn không bị thay đổi. Vui lòng thử lại sau."
                : "I encountered an error while retrieving your current workout plan. No changes were made to your plans. Please try again shortly.";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", intent);
        meta.put("toolName", toolName);
        meta.put("readOnly", true);
        meta.put("error", true);
        return new WorkoutToolResult(toolName, true, intent, false, msg, safeToJson(meta));
    }

    private List<Exercise> loadPublishedCatalog() {
        return exerciseRepository.findAll().stream()
                .filter(e -> e.isActive() && e.getStatus() != ExerciseStatus.DRAFT)
                .toList();
    }

    private WorkoutPlanDay findDay(List<WorkoutPlanDay> days, WorkoutDayOfWeek targetDay) {
        if (days == null || targetDay == null) {
            return null;
        }
        return days.stream()
                .filter(d -> d.getDayOfWeek() == targetDay)
                .findFirst()
                .orElse(null);
    }

    private WorkoutDayOfWeek resolveTargetDay(
            WorkoutDayOfWeek explicitFromPrompt,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek
    ) {
        // Explicit weekday in the user's message ALWAYS takes priority over everything else.
        if (explicitFromPrompt != null) {
            return explicitFromPrompt;
        }
        if (explicitDay != null) {
            return explicitDay;
        }
        return selectedDayOfWeek;
    }

    private WorkoutToolResult buildNonMutatingFailureResult(
            String toolName,
            String message,
            WorkoutDayOfWeek dayOfWeek,
            String errorCode
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_UPDATED");
        meta.put("toolName", toolName);
        meta.put("readOnly", false);
        meta.put("mutated", false);
        meta.put("success", false);
        if (dayOfWeek != null) {
            meta.put("dayOfWeek", dayOfWeek.name());
        }
        if (errorCode != null) {
            meta.put("error", errorCode);
        }
        return new WorkoutToolResult(toolName, false, "WORKOUT_PLAN_UPDATE", false, message, safeToJson(meta));
    }

    private Map<WorkoutDayOfWeek, List<Long>> captureDayExerciseIdsSnapshot(List<WorkoutPlanDay> days) {
        Map<WorkoutDayOfWeek, List<Long>> snapshot = new EnumMap<>(WorkoutDayOfWeek.class);
        if (days == null) {
            return snapshot;
        }
        for (WorkoutPlanDay d : days) {
            if (d.getDayOfWeek() != null) {
                List<Long> ids = loadOrderedExercises(d).stream()
                        .map(WorkoutPlanExercise::getId)
                        .toList();
                snapshot.put(d.getDayOfWeek(), ids);
            }
        }
        return snapshot;
    }

    private void resequenceDayExercises(Long dayId) {
        workoutPlanExerciseRepository.flush();
        List<WorkoutPlanExercise> remaining = workoutPlanExerciseRepository
                .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(dayId);
        if (!remaining.isEmpty()) {
            int maxOrder = remaining.stream().mapToInt(WorkoutPlanExercise::getDisplayOrder).max().orElse(0);
            int offset = maxOrder + remaining.size() + 1;
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).setDisplayOrder(offset + i);
            }
            workoutPlanExerciseRepository.saveAllAndFlush(remaining);
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).setDisplayOrder(i + 1);
            }
            workoutPlanExerciseRepository.saveAllAndFlush(remaining);
        }
    }

    private Map<WorkoutDayOfWeek, WorkoutPlanDay> reloadAndVerifyMutation(
            Long planId,
            Map<WorkoutDayOfWeek, List<Long>> preSnapshot,
            Set<WorkoutDayOfWeek> allowedModifiedDays,
            Map<WorkoutDayOfWeek, List<Long>> expectedPresentAssignmentIds,
            Map<WorkoutDayOfWeek, List<Long>> expectedAbsentAssignmentIds
    ) {
        workoutPlanExerciseRepository.flush();
        workoutPlanDayRepository.flush();

        List<WorkoutPlanDay> reloadedDays = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(planId);
        Map<WorkoutDayOfWeek, WorkoutPlanDay> byDow = new EnumMap<>(WorkoutDayOfWeek.class);
        for (WorkoutPlanDay d : reloadedDays) {
            if (d.getDayOfWeek() == null) {
                continue;
            }
            byDow.put(d.getDayOfWeek(), d);
            List<Long> persistedIds = workoutPlanExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(d.getId())
                    .stream()
                    .map(WorkoutPlanExercise::getId)
                    .toList();

            if (!allowedModifiedDays.contains(d.getDayOfWeek())) {
                List<Long> beforeIds = preSnapshot.getOrDefault(d.getDayOfWeek(), List.of());
                if (!Objects.equals(beforeIds, persistedIds)) {
                    throw new IllegalStateException("Unexpected mutation detected on non-target day " + d.getDayOfWeek());
                }
            }

            List<Long> mustBePresent = expectedPresentAssignmentIds.getOrDefault(d.getDayOfWeek(), List.of());
            for (Long expectedId : mustBePresent) {
                if (!persistedIds.contains(expectedId)) {
                    throw new IllegalStateException("Expected persisted assignment " + expectedId + " missing on " + d.getDayOfWeek());
                }
            }

            List<Long> mustBeAbsent = expectedAbsentAssignmentIds.getOrDefault(d.getDayOfWeek(), List.of());
            for (Long absentId : mustBeAbsent) {
                if (persistedIds.contains(absentId)) {
                    throw new IllegalStateException("Expected removed assignment " + absentId + " still persisted on " + d.getDayOfWeek());
                }
            }
        }
        return byDow;
    }

    private Optional<Exercise> findRequestedExercise(
            List<Exercise> catalog,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history
    ) {
        Optional<Exercise> directFromPrompt = findBestMatchingExercise(catalog, prompt);
        if (directFromPrompt.isPresent()) {
            return directFromPrompt;
        }
        if (explicitExerciseName != null && !explicitExerciseName.isBlank()) {
            Optional<Exercise> fromExplicit = findBestMatchingExercise(catalog, explicitExerciseName);
            if (fromExplicit.isPresent() && isFollowUpOrExplicitCompatible(prompt, fromExplicit.get())) {
                return fromExplicit;
            }
        }
        if (isFollowUpReference(prompt) && history != null && !history.isEmpty()) {
            String fromHistoryName = extractRecentExerciseNameFromHistory(catalog, prompt, history);
            if (fromHistoryName != null) {
                return findBestMatchingExercise(catalog, fromHistoryName);
            }
        }
        return Optional.empty();
    }

    private boolean isFollowUpOrExplicitCompatible(String prompt, Exercise candidate) {
        if (prompt == null || prompt.isBlank()) {
            return true;
        }
        List<String> queryTokens = tokenizeNormalized(prompt);
        List<String> candidateTokens = tokenizeNormalized(candidate.getName());
        if (containsSublist(queryTokens, candidateTokens) >= 0) {
            return true;
        }
        return isFollowUpReference(prompt);
    }

    private String resolveEffectiveExerciseQuery(String prompt, String explicitExerciseName, List<AiMessage> history) {
        if (explicitExerciseName != null && !explicitExerciseName.isBlank()) {
            return explicitExerciseName;
        }
        return resolveExerciseQueryWithContext(prompt, history);
    }

    private Optional<Exercise> findBestMatchingExercise(List<Exercise> catalog, String query) {
        return findBestMatchByName(catalog, Exercise::getName, query);
    }

    private Optional<WorkoutPlanExercise> findBestMatchingPlanExercise(List<WorkoutPlanExercise> dayExercises, String query) {
        return findBestMatchByName(dayExercises, pe -> pe.getExercise() != null ? pe.getExercise().getName() : null, query);
    }

    private <T> Optional<T> findBestMatchByName(
            List<T> candidates,
            java.util.function.Function<T, String> nameExtractor,
            String query
    ) {
        if (candidates == null || candidates.isEmpty() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        List<String> queryTokens = tokenizeNormalized(query);
        if (queryTokens.isEmpty()) {
            return Optional.empty();
        }
        T best = null;
        int bestScore = Integer.MIN_VALUE;
        for (T candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String name = nameExtractor.apply(candidate);
            if (name == null || name.isBlank()) {
                continue;
            }
            int score = scoreExerciseMatch(name, queryTokens);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private int scoreExerciseMatch(String exerciseName, List<String> queryTokens) {
        List<String> exTokens = tokenizeNormalized(exerciseName);
        if (exTokens.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        int matchStart = containsSublist(queryTokens, exTokens);
        if (matchStart < 0) {
            return Integer.MIN_VALUE;
        }
        int afterIdx = matchStart + exTokens.size();
        if (afterIdx < queryTokens.size()) {
            String nextToken = queryTokens.get(afterIdx);
            if (MOVEMENT_VARIANT_MODIFIERS.contains(nextToken) && !exTokens.contains(nextToken)) {
                // Reject partial prefix matches such as "Front Lever" when the user asked for "Front Lever Raises".
                return Integer.MIN_VALUE;
            }
        }
        int exactBonus = queryTokens.size() == exTokens.size() ? 10_000 : 0;
        return exactBonus + (exTokens.size() * 1000) + exerciseName.length();
    }

    private static int containsSublist(List<String> haystack, List<String> needle) {
        if (needle.isEmpty() || haystack.size() < needle.size()) {
            return -1;
        }
        for (int i = 0; i <= haystack.size() - needle.size(); i++) {
            boolean allMatch = true;
            for (int j = 0; j < needle.size(); j++) {
                if (!haystack.get(i + j).equals(needle.get(j))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> tokenizeNormalized(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = text.toLowerCase(Locale.US)
                .replaceAll("[-_/(),.:;!?]+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        String[] rawTokens = cleaned.split("\\s+");
        List<String> normalized = new ArrayList<>(rawTokens.length);
        for (String tok : rawTokens) {
            if (!tok.isBlank()) {
                normalized.add(singularizeToken(tok));
            }
        }
        return normalized;
    }

    private static String singularizeToken(String token) {
        if (token.length() <= 3) {
            return token;
        }
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("ches") || token.endsWith("shes") || token.endsWith("xes")
                || token.endsWith("sses") || token.endsWith("zes")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private String resolveExerciseQueryWithContext(String prompt, List<AiMessage> history) {
        if (isFollowUpReference(prompt) && history != null) {
            String fromHistory = extractRecentExerciseNameFromHistory(exerciseRepository.findAll(), prompt, history);
            if (fromHistory != null) {
                return fromHistory;
            }
        }
        return prompt;
    }

    private boolean isFollowUpReference(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.US).trim();
        if (lower.contains("this exercise") || lower.contains("that exercise")
                || lower.contains("bài tập này") || lower.contains("bài này")
                || lower.matches(".*\\b(it|that|them)\\b.*")) {
            return true;
        }
        // Day-only or correction follow-ups (e.g. "no, add to Tuesday not monday", "Tuesday")
        boolean hasWeekday = extractWeekday(prompt) != null;
        boolean hasNoSpecificWords = tokenizeNormalized(prompt).stream()
                .allMatch(tok -> isFillerOrDayOrActionToken(tok));
        return hasWeekday && hasNoSpecificWords;
    }

    private static boolean isFillerOrDayOrActionToken(String tok) {
        return Set.of(
                "no", "yes", "ok", "okay", "please", "add", "put", "remove", "delete",
                "to", "on", "in", "into", "for", "from", "not", "instead", "of", "rather", "than",
                "monday", "mon", "tuesday", "tue", "wednesday", "wed", "thursday", "thu",
                "friday", "fri", "saturday", "sat", "sunday", "sun",
                "thứ", "hai", "ba", "tư", "năm", "sáu", "bảy", "chủ", "nhật", "vào", "không", "phải",
                "thêm", "xóa", "bỏ", "chuyển", "sang", "từ", "2", "3", "4", "5", "6", "7"
        ).contains(tok);
    }

    private String extractRecentExerciseNameFromHistory(List<Exercise> catalog, String currentPrompt, List<AiMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage prev = history.get(i);
            if (prev == null) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(prev.getRole()) && prev.getMetadata() != null) {
                try {
                    var node = objectMapper.readTree(prev.getMetadata());
                    if (node.hasNonNull("exerciseName")) {
                        return node.get("exerciseName").asText();
                    }
                    if (node.has("addedExercises") && node.get("addedExercises").isArray()
                            && node.get("addedExercises").size() == 1) {
                        return node.get("addedExercises").get(0).asText();
                    }
                } catch (Exception ignored) {
                }
            }
            if ("user".equalsIgnoreCase(prev.getRole()) && prev.getContent() != null
                    && !Objects.equals(prev.getContent(), currentPrompt)) {
                Optional<Exercise> matched = findBestMatchingExercise(catalog, prev.getContent());
                if (matched.isPresent()) {
                    return matched.get().getName();
                }
            }
        }
        return null;
    }

    public static WorkoutDayOfWeek extractWeekday(String prompt) {
        List<WeekdayMatch> matches = findWeekdayMatches(prompt);
        if (matches.isEmpty()) {
            return null;
        }
        List<WeekdayMatch> nonNegated = matches.stream()
                .filter(m -> !m.negated())
                .toList();
        if (nonNegated.isEmpty()) {
            return null;
        }
        // Prefer the last explicit target preposition match (e.g. "no, add to Tuesday not monday")
        for (int i = nonNegated.size() - 1; i >= 0; i--) {
            if (nonNegated.get(i).targetPrep()) {
                return nonNegated.get(i).day();
            }
        }
        return nonNegated.get(0).day();
    }

    public static MoveDays extractMoveDays(String prompt) {
        List<WeekdayMatch> matches = findWeekdayMatches(prompt);
        if (matches.isEmpty()) {
            return new MoveDays(null, null);
        }
        List<WeekdayMatch> nonNegated = matches.stream()
                .filter(m -> !m.negated())
                .toList();
        WorkoutDayOfWeek source = null;
        WorkoutDayOfWeek target = null;
        for (WeekdayMatch m : nonNegated) {
            if (m.sourcePrep() && source == null) {
                source = m.day();
            } else if (m.targetPrep()) {
                target = m.day();
            }
        }
        if (source == null && target != null && nonNegated.size() >= 2) {
            for (WeekdayMatch m : nonNegated) {
                if (m.day() != target) {
                    source = m.day();
                    break;
                }
            }
        }
        if (source != null && target == null && nonNegated.size() >= 2) {
            for (WeekdayMatch m : nonNegated) {
                if (m.day() != source) {
                    target = m.day();
                    break;
                }
            }
        }
        return new MoveDays(source, target);
    }

    private static List<WeekdayMatch> findWeekdayMatches(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }
        List<WeekdayMatch> matches = new ArrayList<>();
        Matcher m = WEEKDAY_TOKEN_PATTERN.matcher(prompt);
        while (m.find()) {
            String raw = m.group(1);
            int start = m.start(1);
            int end = m.end(1);
            String rawLower = raw.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
            String prefix = prompt.substring(Math.max(0, start - 35), start);
            boolean negated = NEGATED_WEEKDAY_PREFIX_PATTERN.matcher(prefix).find();
            boolean sourcePrep = SOURCE_WEEKDAY_PREFIX_PATTERN.matcher(prefix).find();
            boolean targetPrep = TARGET_WEEKDAY_PREFIX_PATTERN.matcher(prefix).find();
            if (!targetPrep && end < prompt.length() && prompt.substring(end).toLowerCase(Locale.US).startsWith("'s")) {
                targetPrep = true;
            }

            // Guard lowercase English words "sat" and "sun" unless capitalized or in day-preposition context.
            if (("sat".equals(rawLower) || "sun".equals(rawLower))
                    && Character.isLowerCase(raw.charAt(0))
                    && !sourcePrep && !targetPrep && !negated) {
                continue;
            }

            WorkoutDayOfWeek dow = parseWeekdayToken(rawLower);
            if (dow != null) {
                matches.add(new WeekdayMatch(dow, start, end, negated, sourcePrep, targetPrep));
            }
        }
        return matches;
    }

    private static WorkoutDayOfWeek parseWeekdayToken(String rawLower) {
        if (rawLower.startsWith("mon") || rawLower.equals("thứ 2") || rawLower.equals("thứ hai") || rawLower.equals("t2")) {
            return WorkoutDayOfWeek.MONDAY;
        }
        if (rawLower.startsWith("tue") || rawLower.equals("thứ 3") || rawLower.equals("thứ ba") || rawLower.equals("t3")) {
            return WorkoutDayOfWeek.TUESDAY;
        }
        if (rawLower.startsWith("wed") || rawLower.equals("thứ 4") || rawLower.equals("thứ tư") || rawLower.equals("t4")) {
            return WorkoutDayOfWeek.WEDNESDAY;
        }
        if (rawLower.startsWith("thu") || rawLower.equals("thứ 5") || rawLower.equals("thứ năm") || rawLower.equals("t5")) {
            return WorkoutDayOfWeek.THURSDAY;
        }
        if (rawLower.startsWith("fri") || rawLower.equals("thứ 6") || rawLower.equals("thứ sáu") || rawLower.equals("t6")) {
            return WorkoutDayOfWeek.FRIDAY;
        }
        if (rawLower.startsWith("sat") || rawLower.equals("thứ 7") || rawLower.equals("thứ bảy") || rawLower.equals("t7")) {
            return WorkoutDayOfWeek.SATURDAY;
        }
        if (rawLower.startsWith("sun") || rawLower.equals("chủ nhật") || rawLower.equals("chu nhat") || rawLower.equals("cn")) {
            return WorkoutDayOfWeek.SUNDAY;
        }
        return null;
    }

    private static WorkoutDayOfWeek toWorkoutDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> WorkoutDayOfWeek.MONDAY;
            case TUESDAY -> WorkoutDayOfWeek.TUESDAY;
            case WEDNESDAY -> WorkoutDayOfWeek.WEDNESDAY;
            case THURSDAY -> WorkoutDayOfWeek.THURSDAY;
            case FRIDAY -> WorkoutDayOfWeek.FRIDAY;
            case SATURDAY -> WorkoutDayOfWeek.SATURDAY;
            case SUNDAY -> WorkoutDayOfWeek.SUNDAY;
        };
    }

    private static WorkoutDayOfWeek fromOrder(int order) {
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            if (dow.getOrder() == order) {
                return dow;
            }
        }
        return WorkoutDayOfWeek.MONDAY;
    }

    private String formatDayLabel(WorkoutDayOfWeek dow, boolean isVn) {
        if (dow == null) return "";
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

    private String extractRequestedDayTitle(String prompt) {
        if (prompt == null) return null;
        Pattern p = Pattern.compile("(?i)(?:to|thành)\\s+([A-Za-z0-9À-ỹ\\s/+-]{2,60}?)(?:\\.|!|$)");
        Matcher m = p.matcher(prompt);
        if (m.find()) {
            String title = m.group(1).trim();
            if (!title.isBlank()) {
                return title;
            }
        }
        return null;
    }

    private WorkoutSessionType inferSessionType(String title) {
        String lower = title.toLowerCase(Locale.US);
        if (lower.contains("push")) return WorkoutSessionType.PUSH;
        if (lower.contains("pull")) return WorkoutSessionType.PULL;
        if (lower.contains("leg")) return WorkoutSessionType.LEGS;
        if (lower.contains("upper")) return WorkoutSessionType.UPPER;
        if (lower.contains("lower")) return WorkoutSessionType.LOWER;
        if (lower.contains("full")) return WorkoutSessionType.FULL_BODY;
        if (lower.contains("handstand")) return WorkoutSessionType.HANDSTAND;
        if (lower.contains("planche")) return WorkoutSessionType.PLANCHE;
        if (lower.contains("lever")) return WorkoutSessionType.FRONT_LEVER;
        if (lower.contains("mobility")) return WorkoutSessionType.MOBILITY;
        return null;
    }

    private WorkoutSessionType inferSessionTypeFromCategory(ExerciseCategory category) {
        if (category == null) {
            return WorkoutSessionType.CUSTOM;
        }
        return switch (category) {
            case PUSH -> WorkoutSessionType.PUSH;
            case PULL -> WorkoutSessionType.PULL;
            case LEGS -> WorkoutSessionType.LEGS;
            case SKILL -> WorkoutSessionType.SKILL_TRAINING;
            case FLEXIBILITY -> WorkoutSessionType.MOBILITY;
            default -> WorkoutSessionType.CUSTOM;
        };
    }

    private boolean isGenericCategoryExerciseRequest(String prompt, ExerciseCategory requestedCategory) {
        if (prompt == null || requestedCategory == null) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.US);
        return lower.contains("exercise") || lower.contains("bài tập");
    }

    private int parseRequestedGenericCount(String prompt, int fallback) {
        if (prompt == null) return fallback;
        Matcher m = GENERIC_COUNT_PATTERN.matcher(prompt);
        if (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                if (val >= 1 && val <= 10) return val;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private ExercisePrescription parseExercisePrescription(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new ExercisePrescription(null, null, null, null);
        }
        Integer sets = parseIntMatch(SETS_PATTERN, prompt, 1, 20);
        Integer reps = parseIntMatch(REPS_PATTERN, prompt, 1, 200);
        if (reps == null) {
            reps = parseIntMatch(TIMES_REPS_PATTERN, prompt, 1, 200);
        }
        Integer restSeconds = parseIntMatch(REST_AFTER_PATTERN, prompt, 0, 600);
        if (restSeconds == null) {
            restSeconds = parseIntMatch(REST_BEFORE_PATTERN, prompt, 0, 600);
        }
        Integer durationSeconds = null;
        if (reps == null) {
            durationSeconds = parseIntMatch(HOLD_PREFIX_PATTERN, prompt, 1, 600);
            if (durationSeconds == null) {
                Matcher holdMatcher = HOLD_PATTERN.matcher(prompt);
                while (holdMatcher.find()) {
                    Integer candidate = parseIntValue(holdMatcher.group(1), 1, 600);
                    if (candidate != null && !Objects.equals(candidate, restSeconds)) {
                        durationSeconds = candidate;
                        break;
                    }
                }
            }
        }
        return new ExercisePrescription(sets, reps, durationSeconds, restSeconds);
    }

    private static Integer parseIntMatch(Pattern pattern, String text, int min, int max) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return parseIntValue(m.group(1), min, max);
        }
        return null;
    }

    private static Integer parseIntValue(String raw, int min, int max) {
        try {
            int val = Integer.parseInt(raw);
            return (val >= min && val <= max) ? val : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private WorkoutPlanExerciseRequest buildAddExerciseRequest(Exercise ex, ExercisePrescription parsed) {
        WorkoutPlanExerciseRequest addReq = new WorkoutPlanExerciseRequest();
        addReq.setExerciseId(ex.getId());
        addReq.setSets(parsed.sets() != null ? parsed.sets() : 3);
        addReq.setRestSeconds(parsed.restSeconds() != null ? parsed.restSeconds() : 90);
        if (parsed.reps() != null) {
            addReq.setReps(parsed.reps());
        } else if (parsed.durationSeconds() != null) {
            addReq.setDurationSeconds(parsed.durationSeconds());
        } else if (isStaticHoldExercise(ex)) {
            addReq.setDurationSeconds(20);
        } else {
            addReq.setReps(10);
        }
        addReq.setNotes("Added via AI Assistant");
        return addReq;
    }

    private boolean isStaticHoldExercise(Exercise ex) {
        if (ex == null) return false;
        if (ex.getCategory() == ExerciseCategory.ISOMETRIC) {
            return true;
        }
        List<String> nameTokens = tokenizeNormalized(ex.getName());
        boolean hasDynamicWord = nameTokens.stream().anyMatch(DYNAMIC_MOVEMENT_WORDS::contains);
        if (hasDynamicWord) {
            return false;
        }
        if (ex.getCategory() == ExerciseCategory.SKILL) {
            return true;
        }
        return nameTokens.contains("hold") || nameTokens.contains("plank") || nameTokens.contains("handstand");
    }

    private ExerciseCategory detectCategory(String prompt) {
        if (prompt == null) return null;
        String lower = prompt.toLowerCase(Locale.US);
        if (lower.contains("leg") || lower.contains("squat") || lower.contains("chân")) return ExerciseCategory.LEGS;
        if (lower.contains("push") || lower.contains("chest") || lower.contains("đẩy")) return ExerciseCategory.PUSH;
        if (lower.contains("pull") || lower.contains("back") || lower.contains("kéo")) return ExerciseCategory.PULL;
        if (lower.contains("core") || lower.contains("abs") || lower.contains("bụng")) return ExerciseCategory.CORE;
        if (lower.contains("isometric") || lower.contains("hold")) return ExerciseCategory.ISOMETRIC;
        if (lower.contains("skill") || lower.contains("planche") || lower.contains("lever") || lower.contains("handstand")) {
            return ExerciseCategory.SKILL;
        }
        return null;
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
