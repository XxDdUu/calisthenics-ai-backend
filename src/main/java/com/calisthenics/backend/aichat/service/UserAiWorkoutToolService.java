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
    public static final String TOOL_REGENERATE_WORKOUT_PLAN = "regenerate_workout_plan";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d+)\\b");

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

    @Transactional(readOnly = true)
    public WorkoutToolResult getMyWorkoutPlan(Long userId, boolean detailed, boolean isVn) {
        return retrievePlan(userId, TOOL_GET_MY_WORKOUT_PLAN, detailed, isVn);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân đang hoạt động để chỉnh sửa (hoặc lịch tập hiện tại được huấn luyện viên giao). Hãy tạo lịch tập cá nhân trước hoặc nhờ huấn luyện viên điều chỉnh nhé."
                    : "You don't currently have an active personal workout plan to modify (coach-assigned plans can only be edited by your coach).";
            return new WorkoutToolResult(TOOL_UPDATE_WORKOUT_PLAN, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        WorkoutPlan plan = planOpt.get().plan();
        WorkoutDayOfWeek dayToUpdate = targetDay != null ? targetDay : extractWeekday(prompt);
        if (dayToUpdate == null) {
            return createWorkoutPlan(userId, prompt, null, 4, timezone, isVn, true);
        }

        WorkoutPlanDay day = loadOrderedDays(plan).stream()
                .filter(d -> d.getDayOfWeek() == dayToUpdate)
                .findFirst()
                .orElse(null);

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

        String dayFormatted = formatSingleDayMarkdown(
                new ResolvedPlan(plan, null, null), dayToUpdate, day, false, false, isVn);
        String header = isVn
                ? "✅ **Đã cập nhật lịch tập cho " + formatDayLabel(dayToUpdate, true) + "!**\n\n"
                : "✅ **Updated " + formatDayLabel(dayToUpdate, false) + "'s workout!**\n\n";
        Map<String, Object> meta = Map.of(
                "type", "WORKOUT_PLAN_UPDATED",
                "toolName", TOOL_UPDATE_WORKOUT_PLAN,
                "readOnly", false,
                "dayOfWeek", dayToUpdate.name()
        );
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
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân để thêm bài tập. Hãy tạo lịch tập trước khi thêm bài mới."
                    : "You don't have an active personal workout plan to add exercises to yet.";
            return new WorkoutToolResult(TOOL_ADD_EXERCISE_TO_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        WorkoutPlan plan = planOpt.get().plan();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        WorkoutDayOfWeek dayToModify = explicitDay != null ? explicitDay : extractWeekday(prompt);
        if (dayToModify == null) {
            dayToModify = days.stream()
                    .filter(d -> !d.isRestDay())
                    .map(WorkoutPlanDay::getDayOfWeek)
                    .findFirst()
                    .orElse(WorkoutDayOfWeek.MONDAY);
        }
        WorkoutDayOfWeek finalDayDow = dayToModify;
        WorkoutPlanDay day = days.stream()
                .filter(d -> d.getDayOfWeek() == finalDayDow)
                .findFirst()
                .orElse(null);
        if (day == null) {
            return new WorkoutToolResult(TOOL_ADD_EXERCISE_TO_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false,
                    isVn ? "Không tìm thấy ngày tập." : "Could not find the target workout day.", null);
        }

        List<Exercise> publishedCatalog = exerciseRepository.findAll().stream()
                .filter(e -> e.isActive() && e.getStatus() != ExerciseStatus.DRAFT)
                .toList();

        Set<Long> existingExerciseIds = new HashSet<>();
        for (WorkoutPlanExercise pe : loadOrderedExercises(day)) {
            if (pe.getExercise() != null) {
                existingExerciseIds.add(pe.getExercise().getId());
            }
        }

        int requestedCount = parseCount(prompt, 1);
        ExerciseCategory requestedCategory = detectCategory(prompt);

        List<Exercise> toAdd = new ArrayList<>();
        // First try direct exercise name match
        Optional<Exercise> directMatch = findBestMatchingExercise(publishedCatalog, prompt);
        if (directMatch.isPresent() && !existingExerciseIds.contains(directMatch.get().getId())) {
            toAdd.add(directMatch.get());
        }

        if (toAdd.size() < requestedCount) {
            publishedCatalog.stream()
                    .filter(e -> !existingExerciseIds.contains(e.getId()))
                    .filter(e -> toAdd.stream().noneMatch(added -> Objects.equals(added.getId(), e.getId())))
                    .filter(e -> requestedCategory == null || e.getCategory() == requestedCategory)
                    .limit(requestedCount - toAdd.size())
                    .forEach(toAdd::add);
        }

        if (toAdd.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy bài tập phù hợp mới trong danh mục công khai để thêm vào ngày tập này."
                    : "No matching new published exercises were found in the catalog to add to that day.";
            return new WorkoutToolResult(TOOL_ADD_EXERCISE_TO_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        if (day.isRestDay()) {
            day.setRestDay(false);
            if (day.getName() == null || day.getName().toLowerCase(Locale.US).contains("rest")) {
                day.setName(requestedCategory != null ? requestedCategory.name() + " Day" : "Custom Training");
            }
            workoutPlanDayRepository.save(day);
        }

        List<String> addedNames = new ArrayList<>();
        for (Exercise ex : toAdd) {
            WorkoutPlanExerciseRequest addReq = new WorkoutPlanExerciseRequest();
            addReq.setExerciseId(ex.getId());
            addReq.setSets(3);
            boolean isometric = ex.getCategory() == ExerciseCategory.ISOMETRIC
                    || (ex.getName() != null && ex.getName().toLowerCase(Locale.US).contains("hold"))
                    || (ex.getName() != null && ex.getName().toLowerCase(Locale.US).contains("plank"))
                    || (ex.getName() != null && ex.getName().toLowerCase(Locale.US).contains("handstand"));
            if (isometric) {
                addReq.setDurationSeconds(20);
            } else {
                addReq.setReps(10);
            }
            addReq.setRestSeconds(90);
            addReq.setNotes("Added via AI Assistant");
            workoutPlanExerciseService.add(userId, plan.getId(), day.getId(), addReq);
            addedNames.add(ex.getName());
        }

        WorkoutPlan reloaded = workoutPlanRepository.findWithDetailsById(plan.getId()).orElse(plan);
        WorkoutPlanDay reloadedDay = loadOrderedDays(reloaded).stream()
                .filter(d -> d.getDayOfWeek() == finalDayDow)
                .findFirst()
                .orElse(day);
        String dayMarkdown = formatSingleDayMarkdown(new ResolvedPlan(reloaded, null, null), finalDayDow, reloadedDay, false, false, isVn);

        String header = isVn
                ? "✅ **Đã thêm " + addedNames.size() + " bài tập vào " + formatDayLabel(finalDayDow, true) + "**: "
                        + String.join(", ", addedNames) + "\n\n"
                : "✅ **Added " + addedNames.size() + " exercise(s) to " + formatDayLabel(finalDayDow, false) + "**: "
                        + String.join(", ", addedNames) + "\n\n";
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", "WORKOUT_PLAN_UPDATED");
        meta.put("toolName", TOOL_ADD_EXERCISE_TO_WORKOUT);
        meta.put("readOnly", false);
        meta.put("addedExercises", addedNames);
        meta.put("dayOfWeek", finalDayDow.name());
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
        Optional<ResolvedPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân để xóa bài tập."
                    : "You don't have an active personal workout plan to remove exercises from.";
            return new WorkoutToolResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        WorkoutPlan plan = planOpt.get().plan();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        WorkoutDayOfWeek dayFilter = explicitDay != null ? explicitDay : extractWeekday(prompt);

        String effectiveQuery = resolveExerciseQueryWithContext(prompt, history);
        List<WorkoutPlanExercise> candidateMatches = new ArrayList<>();
        for (WorkoutPlanDay d : days) {
            if (dayFilter != null && d.getDayOfWeek() != dayFilter) {
                continue;
            }
            for (WorkoutPlanExercise pe : loadOrderedExercises(d)) {
                if (pe.getExercise() != null && matchesExerciseQuery(pe.getExercise().getName(), effectiveQuery)) {
                    candidateMatches.add(pe);
                }
            }
        }

        if (candidateMatches.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy bài tập cần xóa trong lịch tập của bạn. Vui lòng nêu rõ tên bài tập và ngày tập."
                    : "I couldn't find which exercise to remove in your current workout plan. Please specify the exact exercise name.";
            return new WorkoutToolResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, "WORKOUT_PLAN_UPDATE", false, msg, null);
        }

        WorkoutPlanExercise toRemove = candidateMatches.get(0);
        WorkoutPlanDay parentDay = toRemove.getWorkoutPlanDay();
        String removedName = toRemove.getExercise().getName();
        workoutPlanExerciseService.remove(userId, plan.getId(), parentDay.getId(), toRemove.getId());

        // Resequence display orders via WorkoutPlanExerciseService-safe order updates if needed
        List<WorkoutPlanExercise> remaining = workoutPlanExerciseRepository
                .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(parentDay.getId());
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

        String msg = isVn
                ? "✅ Đã xóa **" + removedName + "** khỏi **" + formatDayLabel(parentDay.getDayOfWeek(), true) + "** trong lịch tập của bạn."
                : "✅ Removed **" + removedName + "** from **" + formatDayLabel(parentDay.getDayOfWeek(), false) + "** in your workout plan.";
        Map<String, Object> meta = Map.of(
                "type", "WORKOUT_PLAN_UPDATED",
                "toolName", TOOL_REMOVE_EXERCISE_FROM_WORKOUT,
                "readOnly", false,
                "removedExercise", removedName,
                "dayOfWeek", parentDay.getDayOfWeek().name()
        );
        return new WorkoutToolResult(TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, "WORKOUT_PLAN_UPDATE", true, msg, safeToJson(meta));
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

    private Optional<Exercise> findBestMatchingExercise(List<Exercise> catalog, String query) {
        if (catalog == null || catalog.isEmpty() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        String lower = query.toLowerCase(Locale.US);
        // Prefer longest matching exercise name first (e.g. "Pike Push-Up" over "Push-Up")
        return catalog.stream()
                .filter(e -> e.getName() != null && lower.contains(e.getName().toLowerCase(Locale.US)))
                .max(Comparator.comparingInt(e -> e.getName().length()));
    }

    private String resolveExerciseQueryWithContext(String prompt, List<AiMessage> history) {
        String lower = prompt != null ? prompt.toLowerCase(Locale.US) : "";
        if ((lower.contains("this exercise") || lower.contains("bài tập này") || lower.contains("that exercise"))
                && history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                AiMessage prev = history.get(i);
                if (prev.getContent() != null && !Objects.equals(prev.getContent(), prompt)) {
                    Optional<Exercise> fromHistory = findBestMatchingExercise(exerciseRepository.findAll(), prev.getContent());
                    if (fromHistory.isPresent()) {
                        return fromHistory.get().getName();
                    }
                }
            }
        }
        return prompt;
    }

    private boolean matchesExerciseQuery(String exerciseName, String query) {
        if (exerciseName == null || query == null || query.isBlank()) {
            return false;
        }
        String nameLower = exerciseName.toLowerCase(Locale.US);
        String queryLower = query.toLowerCase(Locale.US);
        if (queryLower.contains(nameLower) || nameLower.equals(queryLower)) {
            return true;
        }
        // Handle plural vs singular (e.g. "push ups" vs "Push-Up")
        String normName = nameLower.replaceAll("[^a-z0-9]", "");
        String normQuery = queryLower.replaceAll("[^a-z0-9]", "");
        return normQuery.contains(normName);
    }

    public static WorkoutDayOfWeek extractWeekday(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lower = prompt.toLowerCase(Locale.US);
        if (lower.contains("monday") || lower.contains("mon ") || lower.contains("mon's") || lower.contains("thứ 2") || lower.contains("thứ hai")) {
            return WorkoutDayOfWeek.MONDAY;
        }
        if (lower.contains("tuesday") || lower.contains("tue ") || lower.contains("tues") || lower.contains("thứ 3") || lower.contains("thứ ba")) {
            return WorkoutDayOfWeek.TUESDAY;
        }
        if (lower.contains("wednesday") || lower.contains("wed ") || lower.contains("thứ 4") || lower.contains("thứ tư")) {
            return WorkoutDayOfWeek.WEDNESDAY;
        }
        if (lower.contains("thursday") || lower.contains("thu ") || lower.contains("thurs") || lower.contains("thứ 5") || lower.contains("thứ năm")) {
            return WorkoutDayOfWeek.THURSDAY;
        }
        if (lower.contains("friday") || lower.contains("fri ") || lower.contains("thứ 6") || lower.contains("thứ sáu")) {
            return WorkoutDayOfWeek.FRIDAY;
        }
        if (lower.contains("saturday") || lower.contains("sat ") || lower.contains("thứ 7") || lower.contains("thứ bảy")) {
            return WorkoutDayOfWeek.SATURDAY;
        }
        if (lower.contains("sunday") || lower.contains("sun ") || lower.contains("chủ nhật") || lower.contains("chu nhat")) {
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

    private int parseCount(String prompt, int fallback) {
        if (prompt == null) return fallback;
        Matcher m = NUMBER_PATTERN.matcher(prompt);
        if (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                if (val >= 1 && val <= 10) return val;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
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
