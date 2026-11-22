package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.preview.*;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.entity.AiWorkoutPlanChangePreview;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aichat.repository.AiWorkoutPlanChangePreviewRepository;
import com.calisthenics.backend.aichat.type.WorkoutPlanOperationType;
import com.calisthenics.backend.aichat.type.WorkoutPlanPreviewStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages the full lifecycle of AI Workout Plan Change Previews:
 * proposal planning & validation (zero DB workout mutations), follow-up revisions,
 * stale plan fingerprint detection, atomic confirmation with post-mutation verification,
 * cancellation, and expiration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutPlanChangePreviewService {

    public static final String TOOL_UPDATE_EXERCISE_IN_WORKOUT = "update_exercise_in_workout";
    public static final String TOOL_REORDER_EXERCISES_IN_WORKOUT = "reorder_exercises_in_workout";
    public static final String METADATA_TYPE_PREVIEW = "WORKOUT_PLAN_CHANGE_PREVIEW";
    public static final String METADATA_TYPE_UPDATED = "WORKOUT_PLAN_UPDATED";
    public static final String METADATA_TYPE_CANCELLED = "WORKOUT_PLAN_PREVIEW_CANCELLED";
    public static final String METADATA_TYPE_CLARIFICATION = "WORKOUT_PLAN_CLARIFICATION";
    public static final Duration DEFAULT_PREVIEW_TTL = Duration.ofMinutes(30);

    private static final Pattern GENERIC_COUNT_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s+(?:[a-zÀ-ỹ-]+\\s+){0,2}(?:exercises?|bài\\s*tập)\\b");
    private static final Pattern SETS_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})\\s*(?:sets?|hiệp)\\b");
    private static final Pattern SETS_OF_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})\\s*[x×]\\s*\\d{1,3}");
    private static final Pattern REPS_PATTERN = Pattern.compile("(?i)\\b(\\d{1,3})\\s*(?:reps?|lần)\\b");
    private static final Pattern TIMES_REPS_PATTERN = Pattern.compile(
            "(?i)\\b\\d{1,2}\\s*[x×]\\s*(\\d{1,3})(?!\\s*(?:s|sec|secs|second|seconds|giây))\\b");
    private static final Pattern REST_AFTER_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*(?:s|sec|secs|seconds?|giây)?\\s*(?:rest|nghỉ)\\b");
    private static final Pattern REST_BEFORE_PATTERN = Pattern.compile(
            "(?i)\\b(?:rest|nghỉ)\\s*[:=]?\\s*(?:of\\s*|to\\s*)?(\\d{1,3})\\s*(?:s|sec|secs|seconds?|giây)?\\b");
    private static final Pattern HOLD_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,3})\\s*(?:s|sec|secs|seconds?|giây)(?![\\p{L}\\p{N}])(?:\\s*(?:hold|giữ)(?![\\p{L}\\p{N}]))?(?!\\s*(?:rest|nghỉ)(?![\\p{L}\\p{N}]))");
    private static final Pattern HOLD_PREFIX_PATTERN = Pattern.compile(
            "(?i)\\b(?:hold|giữ)\\s*(?:for\\s*|to\\s*)?(\\d{1,3})\\s*(?:s|sec|secs|seconds?|giây)(?![\\p{L}\\p{N}])");
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "(?i)(?:position|pos|slot|order|#)\\s*(\\d{1,2})\\b");
    private static final Pattern COMPOUND_SPLIT_PATTERN = Pattern.compile(
            "(?i)(?:\\s*;\\s*|\\s+(?:and|then)\\s+(?=(?:add|insert|remove|delete|drop|move|transfer|shift|reorder|thêm|xóa|chuyển)\\b))");

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
    private static final MuscleTaxonomyService MUSCLE_TAXONOMY_SERVICE = new MuscleTaxonomyService();

    private final AiWorkoutPlanChangePreviewRepository previewRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanAtomicMutationExecutor atomicMutationExecutor;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private WorkoutPlanContextService workoutPlanContextService;

    private WorkoutPlanContextService getPlanContextService() {
        if (workoutPlanContextService != null) {
            return workoutPlanContextService;
        }
        return new WorkoutPlanContextService(
                workoutPlanRepository,
                workoutPlanDayRepository,
                workoutPlanExerciseRepository,
                null,
                null,
                MUSCLE_TAXONOMY_SERVICE
        );
    }

    public record PrescriptionInput(Integer sets, Integer reps, Integer durationSeconds, Integer restSeconds) {
        public boolean hasAny() {
            return sets != null || reps != null || durationSeconds != null || restSeconds != null;
        }
    }

    public record PreviewActionOutcome(
            HttpStatus httpStatus,
            WorkoutPlanChangePreviewActionResponse response
    ) {}

    private record PlannedBatch(
            boolean success,
            List<WorkoutPlanChangeOperation> operations,
            String explanation,
            String nonPreviewReply,
            String nonPreviewMetadataJson
    ) {}

    // ==================== PREVIEW GENERATION (ZERO WORKOUT MUTATIONS) ====================

    @Transactional
    public UserAiWorkoutToolService.WorkoutToolResult proposeChanges(
            Long userId,
            Long conversationId,
            String toolName,
            WorkoutDayOfWeek explicitSourceDay,
            WorkoutDayOfWeek explicitTargetDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        Optional<WorkoutPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân đang hoạt động để chỉnh sửa (lịch tập do huấn luyện viên giao chỉ có huấn luyện viên mới chỉnh sửa được)."
                    : "You don't currently have an active personal workout plan to modify (coach-assigned plans can only be edited by your coach).";
            return buildNonMutatingResult(toolName, msg, null, "NO_PERSONAL_PLAN");
        }

        WorkoutPlan plan = planOpt.get();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        PlannedBatch planned = planOperations(
                plan, days, toolName, explicitSourceDay, explicitTargetDay, selectedDayOfWeek,
                prompt, explicitExerciseName, history, isVn
        );

        if (!planned.success()) {
            return new UserAiWorkoutToolService.WorkoutToolResult(
                    toolName,
                    false,
                    "WORKOUT_PLAN_UPDATE",
                    false,
                    planned.nonPreviewReply(),
                    planned.nonPreviewMetadataJson()
            );
        }

        String supersededId = supersedeActivePendingPreviews(
                userId, conversationId, "Superseded by newer workout plan preview");
        AiWorkoutPlanChangePreview saved = persistPendingPreview(
                userId,
                conversationId,
                plan,
                days,
                planned.operations(),
                planned.explanation(),
                prompt,
                1,
                supersededId,
                isVn
        );

        WorkoutPlanChangePreviewResponse previewDto = toPreviewResponse(saved, plan.getName(), supersededId, false);
        String markdown = formatPreviewMarkdown(previewDto, isVn);
        String metadataJson = buildPreviewMetadataJson(previewDto, toolName);

        return new UserAiWorkoutToolService.WorkoutToolResult(
                toolName,
                false,
                "WORKOUT_PLAN_PREVIEW",
                true,
                markdown,
                metadataJson
        );
    }

    @Transactional
    public UserAiWorkoutToolService.WorkoutToolResult proposeAddExercisesBatch(
            Long userId,
            Long conversationId,
            List<Long> exerciseIds,
            WorkoutDayOfWeek targetDayOfWeek,
            PrescriptionInput overridePrescription,
            String sourcePrompt,
            boolean isVn
    ) {
        if (exerciseIds == null || exerciseIds.isEmpty()) {
            String msg = isVn ? "Vui lòng chọn ít nhất một bài tập để thêm vào lịch tập."
                    : "Please select at least one exercise to add to your workout plan.";
            return buildNonMutatingResult(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, targetDayOfWeek, "EMPTY_SELECTION");
        }
        Optional<WorkoutPlan> planOpt = resolveOwnedPersonalPlan(userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Bạn chưa có lịch tập cá nhân đang hoạt động để chỉnh sửa (lịch tập do huấn luyện viên giao chỉ có huấn luyện viên mới chỉnh sửa được)."
                    : "You don't currently have an active personal workout plan to modify (coach-assigned plans can only be edited by your coach).";
            return buildNonMutatingResult(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, targetDayOfWeek, "NO_PERSONAL_PLAN");
        }

        WorkoutPlan plan = planOpt.get();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        WorkoutDayOfWeek resolvedDayOfWeek = targetDayOfWeek;
        if (resolvedDayOfWeek == null) {
            WorkoutPlanContextService ctxSvc = getPlanContextService();
            WorkoutPlanContextService.ActiveWorkoutPlanContext planCtx = ctxSvc.buildPlanContext(plan, days, false, null);
            List<Exercise> selectedCatalog = exerciseRepository.findAllById(exerciseIds);
            String semanticDay = ctxSvc.extractSemanticDayQueryWithPlanFallback(planCtx, sourcePrompt);
            if (semanticDay != null) {
                List<WorkoutPlanContextService.DayContext> matches = ctxSvc.findMatchingDays(planCtx, semanticDay);
                if (matches.size() == 1) {
                    resolvedDayOfWeek = matches.get(0).dayOfWeek();
                } else if (matches.size() > 1) {
                    String subjectLabel = selectedCatalog.stream().map(Exercise::getName).collect(Collectors.joining(", "));
                    String msg = ctxSvc.formatMultiDayMatchClarification(
                            planCtx, semanticDay, matches, subjectLabel, selectedCatalog, isVn);
                    return buildClarificationResult(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                            "ADD_MULTI", null, null, msg, exerciseIds, selectedCatalog, null,
                            matches.stream().map(WorkoutPlanContextService.DayContext::dayOfWeek).toList());
                } else {
                    String subjectLabel = selectedCatalog.stream().map(Exercise::getName).collect(Collectors.joining(", "));
                    String msg = ctxSvc.formatNoMatchingDayClarification(planCtx, semanticDay, subjectLabel, isVn);
                    return buildClarificationResult(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                            "ADD_MULTI", null, null, msg, exerciseIds, selectedCatalog, null, List.of());
                }
            } else {
                String subjectLabel = selectedCatalog.stream().map(Exercise::getName).collect(Collectors.joining(", "));
                WorkoutPlanContextService.DayRecommendationPlan evalPlan =
                        ctxSvc.evaluateDaysForAddition(planCtx, subjectLabel, selectedCatalog);
                String msg = ctxSvc.formatProactiveRecommendationClarification(evalPlan, subjectLabel, isVn);
                WorkoutDayOfWeek recDow = evalPlan.primaryRecommendation() != null
                        ? evalPlan.primaryRecommendation().day().dayOfWeek() : null;
                List<WorkoutDayOfWeek> candidateDows = new ArrayList<>();
                if (recDow != null) candidateDows.add(recDow);
                evalPlan.alternatives().forEach(a -> candidateDows.add(a.day().dayOfWeek()));
                return buildClarificationResult(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                        "ADD_MULTI", null, null, msg, exerciseIds, selectedCatalog, recDow, candidateDows);
            }
        }

        WorkoutPlanDay destDay = findDay(days, resolvedDayOfWeek);
        targetDayOfWeek = resolvedDayOfWeek;
        if (destDay == null) {
            return buildNonMutatingResult(
                    UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                    isVn ? "Không tìm thấy ngày tập đã chọn trong lịch tập của bạn." : "Could not locate that workout day in your plan.",
                    targetDayOfWeek,
                    "DAY_NOT_FOUND"
            );
        }

        List<WorkoutPlanExercise> destDayExercises = loadOrderedExercises(destDay);
        Set<Long> destExistingIds = destDayExercises.stream()
                .map(pe -> pe.getExercise() != null ? pe.getExercise().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, List<String>> existingOtherDaysByExId = new LinkedHashMap<>();
        Map<MuscleId, Double> weeklyMuscleSets = new EnumMap<>(MuscleId.class);
        for (WorkoutPlanDay d : days) {
            for (WorkoutPlanExercise pe : loadOrderedExercises(d)) {
                if (pe.getExercise() == null) continue;
                Long exId = pe.getExercise().getId();
                if (!Objects.equals(d.getId(), destDay.getId()) && exId != null) {
                    existingOtherDaysByExId.computeIfAbsent(exId, k -> new ArrayList<>())
                            .add(formatDayLabel(d.getDayOfWeek(), isVn));
                }
                double sets = pe.getSets() != null && pe.getSets() > 0 ? pe.getSets() : 3.0;
                MuscleTaxonomyService.ResolvedMuscleTargets resolved = MUSCLE_TAXONOMY_SERVICE.resolveTargets(pe.getExercise());
                for (String pId : resolved.primaryMuscleIds()) {
                    MuscleId.fromId(pId).ifPresent(m -> weeklyMuscleSets.merge(m, sets, Double::sum));
                }
                for (String sId : resolved.secondaryMuscleIds()) {
                    MuscleId.fromId(sId).ifPresent(m -> weeklyMuscleSets.merge(m, sets * 0.5, Double::sum));
                }
            }
        }

        Map<Long, Exercise> catalogById = loadPublishedCatalog().stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e, (a, b) -> a));
        PrescriptionInput effectiveOverride = overridePrescription != null
                ? overridePrescription : new PrescriptionInput(null, null, null, null);

        List<WorkoutPlanChangeOperation> ops = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingOrInvalid = new ArrayList<>();
        Set<Long> dedupSelectedIds = new LinkedHashSet<>(exerciseIds);

        for (Long exId : dedupSelectedIds) {
            Exercise ex = exId != null ? catalogById.get(exId) : null;
            if (ex == null) {
                missingOrInvalid.add(String.valueOf(exId));
                continue;
            }
            if (destExistingIds.contains(ex.getId())) {
                warnings.add(ex.getName() + " is already scheduled on " + formatDayLabel(targetDayOfWeek, false) + " (skipped duplicate).");
                continue;
            }
            List<String> otherDays = existingOtherDaysByExId.getOrDefault(ex.getId(), List.of());
            if (!otherDays.isEmpty()) {
                warnings.add(ex.getName() + " is already trained on " + String.join(", ", otherDays) + ".");
            }
            WorkoutPlanChangeOperation op = buildAddExerciseOp(destDay, ex, effectiveOverride, isVn);
            int addedSets = op.getProposedValues() != null && op.getProposedValues().getSets() != null
                    ? op.getProposedValues().getSets() : 3;
            MuscleTaxonomyService.ResolvedMuscleTargets targets = MUSCLE_TAXONOMY_SERVICE.resolveTargets(ex);
            for (String pId : targets.primaryMuscleIds()) {
                Optional<MuscleId> mid = MuscleId.fromId(pId);
                if (mid.isPresent()) {
                    double nextTotal = weeklyMuscleSets.merge(mid.get(), (double) addedSets, Double::sum);
                    if (nextTotal > 18.0) {
                        warnings.add("High weekly volume warning: " + mid.get().getDisplayName()
                                + " would reach " + Math.round(nextTotal) + " effective sets/week.");
                        break;
                    }
                }
            }
            ops.add(op);
        }

        if (ops.isEmpty()) {
            String msg;
            String code;
            if (!warnings.isEmpty()) {
                msg = String.join(" ", warnings) + " No duplicate exercises were added.";
                code = "ALREADY_EXISTS";
            } else {
                msg = "None of the selected exercises were found in the published exercise catalog (" + String.join(", ", missingOrInvalid) + ").";
                code = "EXERCISE_NOT_FOUND";
            }
            return buildNonMutatingResult(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, targetDayOfWeek, code);
        }

        int resultingDayCount = destDayExercises.size() + ops.size();
        if (resultingDayCount > 6) {
            warnings.add(formatDayLabel(targetDayOfWeek, false) + " would have " + resultingDayCount
                    + " exercises after this update — consider splitting volume if fatigue accumulates.");
        }
        if (destDay.isRestDay()) {
            warnings.add(formatDayLabel(targetDayOfWeek, false) + " is currently a Rest Day and will become active if confirmed.");
        }

        StringBuilder explanationSb = new StringBuilder(buildBatchExplanation(ops, isVn));
        if (!warnings.isEmpty()) {
            List<String> distinctWarnings = warnings.stream().distinct().toList();
            explanationSb.append("\n\n⚠️ **Heads-up**: ").append(String.join(" ", distinctWarnings));
        }

        String supersededId = supersedeActivePendingPreviews(
                userId, conversationId, "Superseded by newer workout plan preview");
        AiWorkoutPlanChangePreview saved = persistPendingPreview(
                userId,
                conversationId,
                plan,
                days,
                ops,
                explanationSb.toString(),
                sourcePrompt != null ? sourcePrompt : ("Add selected exercises to " + targetDayOfWeek.name()),
                1,
                supersededId,
                isVn
        );

        WorkoutPlanChangePreviewResponse previewDto = toPreviewResponse(saved, plan.getName(), supersededId, false);
        String markdown = formatPreviewMarkdown(previewDto, isVn);
        String metadataJson = buildPreviewMetadataJson(previewDto, UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT);

        return new UserAiWorkoutToolService.WorkoutToolResult(
                UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                false,
                "WORKOUT_PLAN_PREVIEW",
                true,
                markdown,
                metadataJson
        );
    }

    // ==================== FOLLOW-UP PREVIEW REVISION ====================

    @Transactional
    public UserAiWorkoutToolService.WorkoutToolResult revisePendingPreview(
            Long userId,
            Long conversationId,
            AiWorkoutPlanChangePreview existingPreview,
            String followUpPrompt,
            boolean isVn
    ) {
        if (isExpired(existingPreview)) {
            markExpiredInternal(existingPreview);
            String msg = isVn
                    ? "Bản xem trước trước đó đã hết hạn. Vui lòng gửi lại yêu cầu thay đổi lịch tập để tôi tạo bản xem trước mới."
                    : "Your previous workout plan preview has expired. Please state the change you'd like to make and I'll generate a fresh preview.";
            return buildNonMutatingResult("revise_workout_preview", msg, null, "PREVIEW_EXPIRED");
        }

        Optional<WorkoutPlan> planOpt = workoutPlanRepository.findByIdAndUserId(existingPreview.getWorkoutPlanId(), userId);
        if (planOpt.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy lịch tập liên quan đến bản xem trước này."
                    : "Could not find the workout plan associated with this preview.";
            return buildNonMutatingResult("revise_workout_preview", msg, null, "PLAN_NOT_FOUND");
        }

        WorkoutPlan plan = planOpt.get();
        List<WorkoutPlanDay> days = loadOrderedDays(plan);
        List<WorkoutPlanChangeOperation> existingOps = deserializeOperations(existingPreview.getOperationsJson());
        if (existingOps.isEmpty()) {
            String msg = isVn
                    ? "Bản xem trước hiện tại không có thao tác nào để điều chỉnh."
                    : "The pending preview has no operations to revise.";
            return buildNonMutatingResult("revise_workout_preview", msg, null, "EMPTY_PREVIEW");
        }

        PrescriptionInput prescriptionOverride = parseExercisePrescription(followUpPrompt);
        WorkoutDayOfWeek newTargetDow = UserAiWorkoutToolService.extractWeekday(followUpPrompt);
        if (newTargetDow == null) {
            WorkoutPlanContextService ctxSvc = getPlanContextService();
            WorkoutPlanContextService.ActiveWorkoutPlanContext planCtx = ctxSvc.buildPlanContext(plan, days, false, null);
            String semanticDay = ctxSvc.extractSemanticDayQueryWithPlanFallback(planCtx, followUpPrompt);
            if (semanticDay != null) {
                List<WorkoutPlanContextService.DayContext> matches = ctxSvc.findMatchingDays(planCtx, semanticDay);
                if (matches.size() == 1) {
                    newTargetDow = matches.get(0).dayOfWeek();
                } else if (matches.size() > 1) {
                    String msg = ctxSvc.formatMultiDayMatchClarification(
                            planCtx, semanticDay, matches, "these changes", List.of(), isVn);
                    return buildClarificationResult("revise_workout_preview", "REVISE", null, null, msg);
                } else {
                    String msg = ctxSvc.formatNoMatchingDayClarification(planCtx, semanticDay, "these changes", isVn);
                    return buildNonMutatingResult("revise_workout_preview", msg, null, "DAY_NOT_FOUND");
                }
            }
        }

        // Identify which operation in the pending preview this follow-up modifies
        List<Integer> matchingIndices = findMatchingOperationIndices(existingOps, followUpPrompt);
        if (matchingIndices.isEmpty() && existingOps.size() > 1 && newTargetDow != null && !prescriptionOverride.hasAny()
                && existingOps.stream().allMatch(op -> op.getOperationType() == WorkoutPlanOperationType.ADD_EXERCISE)) {
            WorkoutPlanDay newDestDay = findDay(days, newTargetDow);
            if (newDestDay == null) {
                String msg = isVn
                        ? "Không tìm thấy " + formatDayLabel(newTargetDow, true) + " trong lịch tập của bạn."
                        : "Could not find " + formatDayLabel(newTargetDow, false) + " in your workout plan.";
                return buildNonMutatingResult("revise_workout_preview", msg, newTargetDow, "DAY_NOT_FOUND");
            }
            Set<Long> existingDestIds = loadOrderedExercises(newDestDay).stream()
                    .map(WorkoutPlanExercise::getExercise)
                    .filter(Objects::nonNull)
                    .map(Exercise::getId)
                    .collect(Collectors.toSet());
            List<WorkoutPlanChangeOperation> revisedOps = new ArrayList<>();
            List<String> skippedDups = new ArrayList<>();
            for (WorkoutPlanChangeOperation op : existingOps) {
                if (existingDestIds.contains(op.getExerciseId())) {
                    skippedDups.add(op.getExerciseName());
                    continue;
                }
                WorkoutPlanChangeOperation copy = deepCopyOperation(op);
                copy.setDestinationDayId(newDestDay.getId());
                copy.setDestinationDayOfWeek(newDestDay.getDayOfWeek());
                copy.setDestinationDayLabel(formatDayTitle(newDestDay, isVn));
                copy.setSummary(buildOperationSummary(copy, isVn));
                revisedOps.add(copy);
            }
            if (revisedOps.isEmpty()) {
                String msg = "**" + String.join("**, **", skippedDups) + "** are already scheduled on **"
                        + formatDayLabel(newTargetDow, false) + "**. No duplicates were added.";
                return buildNonMutatingResult("revise_workout_preview", msg, newTargetDow, "ALREADY_EXISTS");
            }
            List<String> validationErrors = validateOperationsAgainstCurrentPlan(plan, days, revisedOps);
            if (!validationErrors.isEmpty()) {
                return buildNonMutatingResult("revise_workout_preview", String.join(" ", validationErrors), null, "VALIDATION_FAILED");
            }
            String oldPreviewId = existingPreview.getPreviewId();
            existingPreview.setStatus(WorkoutPlanPreviewStatus.CANCELLED);
            existingPreview.setResolvedAt(ZonedDateTime.now());
            existingPreview.setFailureReason("Superseded by revised preview");
            previewRepository.save(existingPreview);
            syncAssistantMessageStatus(existingPreview, false);
            int nextRevision = (existingPreview.getRevision() != null ? existingPreview.getRevision() : 1) + 1;
            String combinedSourcePrompt = existingPreview.getSourcePrompt() != null
                    ? existingPreview.getSourcePrompt() + " | Follow-up: " + followUpPrompt
                    : followUpPrompt;
            WorkoutPlanContextService.ActiveWorkoutPlanContext planCtx =
                    getPlanContextService().buildPlanContext(plan, days, false, null);
            boolean destIsRest = newDestDay.isRestDay() || newDestDay.getSessionType() == WorkoutSessionType.REST;
            List<WorkoutPlanExercise> destExs = loadOrderedExercises(newDestDay);
            int destTotalSets = destExs.stream().mapToInt(pe -> pe.getSets() != null ? pe.getSets() : 0).sum();
            String explanation = buildAdditionExplanationWithContext(
                    planCtx, newDestDay, destIsRest, revisedOps, skippedDups, destExs.size(), destTotalSets, isVn);
            AiWorkoutPlanChangePreview saved = persistPendingPreview(
                    userId, conversationId, plan, days, revisedOps, explanation, combinedSourcePrompt, nextRevision, oldPreviewId, isVn);
            WorkoutPlanChangePreviewResponse previewDto = toPreviewResponse(saved, plan.getName(), oldPreviewId, false);
            return new UserAiWorkoutToolService.WorkoutToolResult(
                    "revise_workout_preview", false, "WORKOUT_PLAN_PREVIEW", true,
                    formatPreviewMarkdown(previewDto, isVn), buildPreviewMetadataJson(previewDto, "revise_workout_preview"));
        }

        int targetIndex;
        if (matchingIndices.size() == 1) {
            targetIndex = matchingIndices.get(0);
        } else if (matchingIndices.size() > 1) {
            String names = matchingIndices.stream()
                    .map(i -> existingOps.get(i).getExerciseName())
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            String msg = isVn
                    ? "Bản xem trước đang chờ có nhiều bài tập phù hợp (" + names + "). Bạn muốn điều chỉnh bài tập nào?"
                    : "Your pending preview affects multiple exercises (" + names + "). Which exercise would you like me to adjust?";
            return buildClarificationResult("revise_workout_preview", "REVISE", null, null, msg);
        } else if (existingOps.size() == 1) {
            targetIndex = 0;
        } else {
            String names = existingOps.stream()
                    .map(WorkoutPlanChangeOperation::getExerciseName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            String msg = isVn
                    ? "Bản xem trước đang chờ bao gồm nhiều bài tập (" + names + "). Vui lòng nêu rõ tên bài tập bạn muốn điều chỉnh."
                    : "Your pending preview includes multiple exercises (" + names + "). Please specify which exercise you'd like to modify.";
            return buildClarificationResult("revise_workout_preview", "REVISE", null, null, msg);
        }

        WorkoutPlanChangeOperation targetOp = deepCopyOperation(existingOps.get(targetIndex));
        boolean changed = false;

        if (prescriptionOverride.hasAny()) {
            if (targetOp.getOperationType() == WorkoutPlanOperationType.REMOVE_EXERCISE
                    || targetOp.getOperationType() == WorkoutPlanOperationType.REORDER_EXERCISES
                    || targetOp.getOperationType() == WorkoutPlanOperationType.UPDATE_DAY) {
                String msg = isVn
                        ? "Không thể thay đổi số hiệp/lần cho thao tác này trong bản xem trước."
                        : "Sets, reps, hold, or rest changes cannot be applied to that operation type.";
                return buildNonMutatingResult("revise_workout_preview", msg, null, "UNSUPPORTED_REVISION");
            }
            ExercisePrescriptionValues base = targetOp.getProposedValues() != null
                    ? copyValues(targetOp.getProposedValues())
                    : (targetOp.getPreviousValues() != null ? copyValues(targetOp.getPreviousValues()) : new ExercisePrescriptionValues());
            applyPrescriptionOverride(base, prescriptionOverride);
            targetOp.setProposedValues(base);
            changed = true;
        }

        if (newTargetDow != null
                && (targetOp.getOperationType() == WorkoutPlanOperationType.MOVE_EXERCISE
                || targetOp.getOperationType() == WorkoutPlanOperationType.ADD_EXERCISE)) {
            WorkoutPlanDay newDestDay = findDay(days, newTargetDow);
            if (newDestDay == null) {
                String msg = isVn
                        ? "Không tìm thấy " + formatDayLabel(newTargetDow, true) + " trong lịch tập của bạn."
                        : "Could not find " + formatDayLabel(newTargetDow, false) + " in your workout plan.";
                return buildNonMutatingResult("revise_workout_preview", msg, newTargetDow, "DAY_NOT_FOUND");
            }
            if (targetOp.getOperationType() == WorkoutPlanOperationType.MOVE_EXERCISE
                    && Objects.equals(targetOp.getSourceDayId(), newDestDay.getId())) {
                String msg = isVn
                        ? "Ngày nguồn và ngày đích giống nhau (" + formatDayLabel(newTargetDow, true) + ")."
                        : "Source and destination days are both " + formatDayLabel(newTargetDow, false) + ".";
                return buildNonMutatingResult("revise_workout_preview", msg, newTargetDow, "SAME_DAY");
            }
            boolean alreadyOnDest = loadOrderedExercises(newDestDay).stream()
                    .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), targetOp.getExerciseId()));
            if (alreadyOnDest) {
                String msg = isVn
                        ? "**" + targetOp.getExerciseName() + "** đã có trong **" + formatDayLabel(newTargetDow, true)
                                + "**. Không thể tạo bài tập trùng lặp."
                        : "**" + targetOp.getExerciseName() + "** is already scheduled on **" + formatDayLabel(newTargetDow, false)
                                + "**. Cannot create a duplicate exercise.";
                return buildNonMutatingResult("revise_workout_preview", msg, newTargetDow, "ALREADY_EXISTS");
            }
            targetOp.setDestinationDayId(newDestDay.getId());
            targetOp.setDestinationDayOfWeek(newDestDay.getDayOfWeek());
            targetOp.setDestinationDayLabel(formatDayTitle(newDestDay, isVn));
            changed = true;
        }

        if (!changed) {
            String msg = isVn
                    ? "Tôi chưa thấy thay đổi mới nào cho bản xem trước hiện tại. Bạn có thể yêu cầu đổi số hiệp, số lần, thời gian giữ, thời gian nghỉ hoặc ngày đích, hoặc nhắn \"Xác nhận\" / \"Hủy\"."
                    : "I didn't detect a new adjustment for the pending preview. You can change sets, reps, hold time, rest time, or destination weekday, or say \"Confirm\" or \"Cancel\".";
            return buildNonMutatingResult("revise_workout_preview", msg, null, "NO_CHANGES");
        }

        targetOp.setSummary(buildOperationSummary(targetOp, isVn));

        List<WorkoutPlanChangeOperation> revisedOps = new ArrayList<>(existingOps);
        revisedOps.set(targetIndex, targetOp);

        List<String> validationErrors = validateOperationsAgainstCurrentPlan(plan, days, revisedOps);
        if (!validationErrors.isEmpty()) {
            return buildNonMutatingResult("revise_workout_preview", String.join(" ", validationErrors), null, "VALIDATION_FAILED");
        }

        String oldPreviewId = existingPreview.getPreviewId();
        existingPreview.setStatus(WorkoutPlanPreviewStatus.CANCELLED);
        existingPreview.setResolvedAt(ZonedDateTime.now());
        existingPreview.setFailureReason("Superseded by revised preview");
        previewRepository.save(existingPreview);
        syncAssistantMessageStatus(existingPreview, false);

        int nextRevision = (existingPreview.getRevision() != null ? existingPreview.getRevision() : 1) + 1;
        String combinedSourcePrompt = existingPreview.getSourcePrompt() != null
                ? existingPreview.getSourcePrompt() + " | Follow-up: " + followUpPrompt
                : followUpPrompt;
        String explanation = buildBatchExplanation(revisedOps, isVn);
        if (newTargetDow != null) {
            WorkoutPlanDay revisedDest = findDay(days, newTargetDow);
            if (revisedDest != null && (revisedDest.isRestDay() || revisedDest.getSessionType() == WorkoutSessionType.REST)) {
                explanation += isVn
                        ? "\n\n⚠️ **Lưu ý Ngày nghỉ**: **" + formatDayLabel(newTargetDow, true)
                                + "** hiện là Ngày nghỉ (Rest Day) và sẽ chuyển thành ngày tập nếu bạn xác nhận."
                        : "\n\n⚠️ **Rest Day Notice**: **" + formatDayLabel(newTargetDow, false)
                                + "** is currently a **Rest Day** in your workout plan and will become an active training day if confirmed.";
            }
        }

        AiWorkoutPlanChangePreview saved = persistPendingPreview(
                userId,
                conversationId,
                plan,
                days,
                revisedOps,
                explanation,
                combinedSourcePrompt,
                nextRevision,
                oldPreviewId,
                isVn
        );

        WorkoutPlanChangePreviewResponse previewDto = toPreviewResponse(saved, plan.getName(), oldPreviewId, false);
        String markdown = formatPreviewMarkdown(previewDto, isVn);
        String metadataJson = buildPreviewMetadataJson(previewDto, "revise_workout_preview");

        return new UserAiWorkoutToolService.WorkoutToolResult(
                "revise_workout_preview",
                false,
                "WORKOUT_PLAN_PREVIEW",
                true,
                markdown,
                metadataJson
        );
    }

    // ==================== GET ACTIVE PENDING PREVIEW ====================

    @Transactional
    public Optional<AiWorkoutPlanChangePreview> findActivePendingPreview(Long userId, Long conversationId) {
        Optional<AiWorkoutPlanChangePreview> opt = previewRepository
                .findFirstByUserIdAndConversationIdAndStatusOrderByCreatedAtDesc(
                        userId, conversationId, WorkoutPlanPreviewStatus.PENDING);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        AiWorkoutPlanChangePreview preview = opt.get();
        if (isExpired(preview)) {
            markExpiredInternal(preview);
            return Optional.empty();
        }
        return Optional.of(preview);
    }

    @Transactional
    public WorkoutPlanChangePreviewResponse getPendingPreviewResponse(Long userId, Long conversationId) {
        aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));
        return findActivePendingPreview(userId, conversationId)
                .map(p -> {
                    String planName = workoutPlanRepository.findById(p.getWorkoutPlanId())
                            .map(WorkoutPlan::getName)
                            .orElse("Workout Plan");
                    return toPreviewResponse(p, planName, null, false);
                })
                .orElse(null);
    }

    // ==================== CONFIRMATION (ATOMIC + STALE CHECK + VERIFICATION) ====================

    public PreviewActionOutcome confirmPreview(
            Long userId,
            Long conversationId,
            String previewId,
            boolean isVn,
            boolean persistAssistantMessage
    ) {
        try {
            return executeInTransaction(() ->
                    confirmPreviewTransactional(userId, conversationId, previewId, isVn, persistAssistantMessage));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Atomic confirmation failed for previewId={}, userId={}: {}", previewId, userId, ex.getMessage(), ex);
            return executeInTransaction(() ->
                    recordExecutionFailure(userId, conversationId, previewId, ex.getMessage(), isVn, persistAssistantMessage));
        }
    }

    private <T> T executeInTransaction(Supplier<T> work) {
        if (transactionManager == null) {
            return work.get();
        }
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        return txTemplate.execute(status -> work.get());
    }

    protected PreviewActionOutcome confirmPreviewTransactional(
            Long userId,
            Long conversationId,
            String previewId,
            boolean isVn,
            boolean persistAssistantMessage
    ) {
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));

        AiWorkoutPlanChangePreview preview = previewRepository
                .findByPreviewIdAndUserIdAndConversationIdForUpdate(previewId, userId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout plan preview not found"));

        String planName = workoutPlanRepository.findById(preview.getWorkoutPlanId())
                .map(WorkoutPlan::getName)
                .orElse("Workout Plan");

        // Prevent replay & double execution
        if (preview.getStatus() != WorkoutPlanPreviewStatus.PENDING) {
            String msg = switch (preview.getStatus()) {
                case CONFIRMED -> isVn
                        ? "Thay đổi này đã được xác nhận và áp dụng trước đó."
                        : "These workout plan changes have already been confirmed and applied.";
                case CANCELLED -> isVn
                        ? "Bản xem trước này đã bị hủy hoặc được thay thế bởi bản mới hơn."
                        : "This workout plan preview was cancelled or superseded by a newer revision.";
                case EXPIRED -> isVn
                        ? "Bản xem trước này đã hết hạn. Vui lòng tạo lại bản xem trước mới."
                        : "This workout plan preview has expired. Please request a fresh preview.";
                case FAILED -> isVn
                        ? "Bản xem trước này không còn hợp lệ. Vui lòng xem bản mới hoặc gửi lại yêu cầu."
                        : "This workout plan preview is no longer valid. Please review any refreshed preview or try again.";
                default -> "Preview is no longer pending.";
            };
            WorkoutPlanChangePreviewResponse dto = toPreviewResponse(preview, planName, null, false);
            return new PreviewActionOutcome(
                    HttpStatus.CONFLICT,
                    WorkoutPlanChangePreviewActionResponse.builder()
                            .success(false)
                            .mutated(false)
                            .verified(false)
                            .stale(false)
                            .code("PREVIEW_ALREADY_RESOLVED")
                            .message(msg)
                            .preview(dto)
                            .build()
            );
        }

        // Check expiration TTL
        if (isExpired(preview)) {
            markExpiredInternal(preview);
            String msg = isVn
                    ? "Bản xem trước đã hết hạn trước khi xác nhận. Không có thay đổi nào được áp dụng."
                    : "This workout plan preview expired before confirmation. No changes were applied.";
            AiMessageResponse assistantMsg = persistAssistantMessage
                    ? saveSystemAssistantMessage(conversation, msg, "WORKOUT_PLAN_PREVIEW", buildCancelledOrFailedMetadata(preview, "PREVIEW_EXPIRED"))
                    : null;
            return new PreviewActionOutcome(
                    HttpStatus.CONFLICT,
                    WorkoutPlanChangePreviewActionResponse.builder()
                            .success(false)
                            .mutated(false)
                            .verified(false)
                            .stale(false)
                            .code("PREVIEW_EXPIRED")
                            .message(msg)
                            .preview(toPreviewResponse(preview, planName, null, false))
                            .assistantMessage(assistantMsg)
                            .build()
            );
        }

        // Load live plan and check ownership + staleness + operation revalidation
        Optional<WorkoutPlan> livePlanOpt = workoutPlanRepository.findByIdAndUserId(preview.getWorkoutPlanId(), userId);
        if (livePlanOpt.isEmpty()) {
            preview.setStatus(WorkoutPlanPreviewStatus.FAILED);
            preview.setResolvedAt(ZonedDateTime.now());
            preview.setFailureReason("Target workout plan no longer exists.");
            previewRepository.save(preview);
            syncAssistantMessageStatus(preview, true);

            String msg = isVn
                    ? "Không tìm thấy lịch tập mục tiêu nữa. Không có thay đổi nào được thực hiện."
                    : "The target workout plan no longer exists. Confirmation was rejected with no changes made.";
            AiMessageResponse assistantMsg = persistAssistantMessage
                    ? saveSystemAssistantMessage(conversation, msg, "WORKOUT_PLAN_PREVIEW", buildCancelledOrFailedMetadata(preview, "PLAN_NOT_FOUND"))
                    : null;
            return new PreviewActionOutcome(
                    HttpStatus.CONFLICT,
                    WorkoutPlanChangePreviewActionResponse.builder()
                            .success(false)
                            .mutated(false)
                            .verified(false)
                            .stale(true)
                            .code("STALE_PREVIEW")
                            .message(msg)
                            .preview(toPreviewResponse(preview, planName, null, true))
                            .assistantMessage(assistantMsg)
                            .build()
            );
        }

        WorkoutPlan livePlan = livePlanOpt.get();
        List<WorkoutPlanDay> liveDays = loadOrderedDays(livePlan);
        List<WorkoutPlanChangeOperation> operations = deserializeOperations(preview.getOperationsJson());
        String currentFingerprint = computePlanFingerprint(livePlan, liveDays);
        List<String> revalidationErrors = validateOperationsAgainstCurrentPlan(livePlan, liveDays, operations);

        boolean isStale = !Objects.equals(currentFingerprint, preview.getPlanFingerprint()) || !revalidationErrors.isEmpty();
        if (isStale) {
            return handleStalePreview(conversation, preview, livePlan, liveDays, operations, revalidationErrors, isVn, persistAssistantMessage);
        }

        // Atomically claim PENDING -> CONFIRMED inside the same DB transaction to prevent concurrent double execution
        ZonedDateTime resolvedNow = ZonedDateTime.now();
        Long targetPlanId = livePlan.getId();
        int claimed = previewRepository.transitionStatusAtomically(
                preview.getPreviewId(),
                userId,
                conversationId,
                WorkoutPlanPreviewStatus.PENDING,
                WorkoutPlanPreviewStatus.CONFIRMED,
                resolvedNow
        );
        if (claimed == 0) {
            return new PreviewActionOutcome(
                    HttpStatus.CONFLICT,
                    WorkoutPlanChangePreviewActionResponse.builder()
                            .success(false)
                            .mutated(false)
                            .verified(false)
                            .stale(false)
                            .code("PREVIEW_ALREADY_RESOLVED")
                            .message(isVn
                                    ? "Thay đổi này đã được xử lý bởi một yêu cầu khác."
                                    : "This workout plan preview was already processed.")
                            .preview(toPreviewResponse(preview, planName, null, false))
                            .build()
            );
        }

        // Execute all approved operations atomically and verify DB persistence
        WorkoutPlanAtomicMutationExecutor.ExecutionVerificationResult verificationResult =
                atomicMutationExecutor.executeAtomicallyAndVerify(userId, targetPlanId, operations);

        AiWorkoutPlanChangePreview confirmedPreview = previewRepository
                .findByPreviewIdAndUserIdAndConversationId(previewId, userId, conversationId)
                .orElse(preview);
        confirmedPreview.setStatus(WorkoutPlanPreviewStatus.CONFIRMED);
        confirmedPreview.setResolvedAt(resolvedNow);
        syncAssistantMessageStatus(confirmedPreview, false);

        WorkoutPlanChangePreviewResponse confirmedDto = toPreviewResponse(
                confirmedPreview, verificationResult.reloadedPlan().getName(), null, false);
        String successMarkdown = formatConfirmedSuccessMarkdown(confirmedDto, verificationResult, isVn);
        String successMeta = buildConfirmedMetadataJson(confirmedDto, operations);

        AiMessageResponse assistantMsg = null;
        if (persistAssistantMessage) {
            AiConversation managedConv = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElse(conversation);
            assistantMsg = saveSystemAssistantMessage(managedConv, successMarkdown, "WORKOUT_PLAN_UPDATE", successMeta);
        }

        return new PreviewActionOutcome(
                HttpStatus.OK,
                WorkoutPlanChangePreviewActionResponse.builder()
                        .success(true)
                        .mutated(true)
                        .verified(true)
                        .stale(false)
                        .code("CONFIRMED")
                        .message(successMarkdown)
                        .preview(confirmedDto)
                        .assistantMessage(assistantMsg)
                        .build()
        );
    }

    private PreviewActionOutcome handleStalePreview(
            AiConversation conversation,
            AiWorkoutPlanChangePreview stalePreview,
            WorkoutPlan livePlan,
            List<WorkoutPlanDay> liveDays,
            List<WorkoutPlanChangeOperation> originalOps,
            List<String> revalidationErrors,
            boolean isVn,
            boolean persistAssistantMessage
    ) {
        String reason = revalidationErrors.isEmpty()
                ? "Your workout plan changed after this preview was generated."
                : String.join(" ", revalidationErrors);
        stalePreview.setStatus(WorkoutPlanPreviewStatus.FAILED);
        stalePreview.setResolvedAt(ZonedDateTime.now());
        stalePreview.setFailureReason("STALE_PREVIEW: " + reason);
        previewRepository.save(stalePreview);
        syncAssistantMessageStatus(stalePreview, true);

        WorkoutPlanChangePreviewResponse staleDto = toPreviewResponse(stalePreview, livePlan.getName(), null, true);

        // Scenario E: Refresh workout plan & attempt to regenerate a fresh preview from original intent
        WorkoutPlanChangePreviewResponse refreshedDto = null;
        AiWorkoutPlanChangePreview regeneratedEntity = null;
        List<WorkoutPlanChangeOperation> replannedOps = attemptReplanOnFreshPlan(livePlan, liveDays, originalOps, isVn);
        if (!replannedOps.isEmpty()) {
            String explanation = buildBatchExplanation(replannedOps, isVn);
            regeneratedEntity = persistPendingPreview(
                    stalePreview.getUserId(),
                    stalePreview.getConversationId(),
                    livePlan,
                    liveDays,
                    replannedOps,
                    explanation,
                    stalePreview.getSourcePrompt(),
                    (stalePreview.getRevision() != null ? stalePreview.getRevision() : 1) + 1,
                    stalePreview.getPreviewId(),
                    isVn
            );
            refreshedDto = toPreviewResponse(regeneratedEntity, livePlan.getName(), stalePreview.getPreviewId(), false);
        }

        StringBuilder msgSb = new StringBuilder();
        if (isVn) {
            msgSb.append("⚠️ **Bản xem trước đã cũ — đã từ chối áp dụng**\n\n")
                    .append("Lịch tập của bạn đã thay đổi sau khi bản xem trước này được tạo nên không có thay đổi cũ nào được ghi đè.\n\n");
            if (refreshedDto != null) {
                msgSb.append("Tôi đã tải lại lịch tập mới nhất và tạo bản xem trước cập nhật bên dưới để bạn xác nhận:\n\n")
                        .append(formatPreviewMarkdown(refreshedDto, true));
            } else {
                msgSb.append("Tôi đã tải lại lịch tập mới nhất nhưng yêu cầu trước đó không còn áp dụng được nữa (")
                        .append(reason).append(").");
            }
        } else {
            msgSb.append("⚠️ **Preview Outdated — Confirmation Rejected**\n\n")
                    .append("Your workout plan changed after this preview was generated, so the stale changes were not applied.\n\n");
            if (refreshedDto != null) {
                msgSb.append("I've refreshed your latest workout plan and generated a new preview below for your review:\n\n")
                        .append(formatPreviewMarkdown(refreshedDto, false));
            } else {
                msgSb.append("I've refreshed your latest workout plan, and the original change can no longer be applied cleanly (")
                        .append(reason).append(").");
            }
        }

        String metadataJson = refreshedDto != null
                ? buildPreviewMetadataJson(refreshedDto, "refresh_stale_preview")
                : buildCancelledOrFailedMetadata(stalePreview, "STALE_PREVIEW");

        AiMessageResponse assistantMsg = null;
        if (persistAssistantMessage) {
            assistantMsg = saveSystemAssistantMessage(
                    conversation,
                    msgSb.toString().trim(),
                    "WORKOUT_PLAN_PREVIEW",
                    metadataJson
            );
            if (regeneratedEntity != null && assistantMsg != null && assistantMsg.getId() != null) {
                regeneratedEntity.setAssistantMessageId(assistantMsg.getId());
                previewRepository.save(regeneratedEntity);
            }
        }

        return new PreviewActionOutcome(
                HttpStatus.CONFLICT,
                WorkoutPlanChangePreviewActionResponse.builder()
                        .success(false)
                        .mutated(false)
                        .verified(false)
                        .stale(true)
                        .code("STALE_PREVIEW")
                        .message(msgSb.toString().trim())
                        .preview(staleDto)
                        .refreshedPreview(refreshedDto)
                        .assistantMessage(assistantMsg)
                        .build()
        );
    }

    protected PreviewActionOutcome recordExecutionFailure(
            Long userId,
            Long conversationId,
            String previewId,
            String errorMessage,
            boolean isVn,
            boolean persistAssistantMessage
    ) {
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId).orElse(null);
        AiWorkoutPlanChangePreview preview = previewRepository
                .findByPreviewIdAndUserIdAndConversationId(previewId, userId, conversationId)
                .orElse(null);

        WorkoutPlanChangePreviewResponse dto = null;
        if (preview != null && preview.getStatus() == WorkoutPlanPreviewStatus.PENDING) {
            preview.setStatus(WorkoutPlanPreviewStatus.FAILED);
            preview.setResolvedAt(ZonedDateTime.now());
            preview.setFailureReason(errorMessage != null ? errorMessage : "Failed to apply workout plan changes");
            previewRepository.save(preview);
            syncAssistantMessageStatus(preview, false);
            String planName = workoutPlanRepository.findById(preview.getWorkoutPlanId())
                    .map(WorkoutPlan::getName)
                    .orElse("Workout Plan");
            dto = toPreviewResponse(preview, planName, null, false);
        }

        String msg = isVn
                ? "Không thể áp dụng các thay đổi vào lịch tập (" + (errorMessage != null ? errorMessage : "lỗi hệ thống")
                        + "). Toàn bộ giao dịch đã được hoàn tác nên lịch tập gốc vẫn giữ nguyên."
                : "Could not apply the proposed workout plan changes (" + (errorMessage != null ? errorMessage : "unexpected error")
                        + "). All changes were rolled back so your original workout plan remains unchanged.";

        AiMessageResponse assistantMsg = (persistAssistantMessage && conversation != null && preview != null)
                ? saveSystemAssistantMessage(conversation, msg, "WORKOUT_PLAN_UPDATE", buildCancelledOrFailedMetadata(preview, "EXECUTION_FAILED"))
                : null;

        return new PreviewActionOutcome(
                HttpStatus.CONFLICT,
                WorkoutPlanChangePreviewActionResponse.builder()
                        .success(false)
                        .mutated(false)
                        .verified(false)
                        .stale(false)
                        .code("EXECUTION_FAILED")
                        .message(msg)
                        .preview(dto)
                        .assistantMessage(assistantMsg)
                        .build()
        );
    }

    // ==================== CANCELLATION ====================

    @Transactional
    public PreviewActionOutcome cancelPreview(
            Long userId,
            Long conversationId,
            String previewId,
            boolean isVn,
            boolean persistAssistantMessage
    ) {
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));

        AiWorkoutPlanChangePreview preview = previewRepository
                .findByPreviewIdAndUserIdAndConversationIdForUpdate(previewId, userId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout plan preview not found"));

        String planName = workoutPlanRepository.findById(preview.getWorkoutPlanId())
                .map(WorkoutPlan::getName)
                .orElse("Workout Plan");

        if (preview.getStatus() != WorkoutPlanPreviewStatus.PENDING) {
            String msg = isVn
                    ? "Bản xem trước này đã được xử lý trước đó (" + preview.getStatus().name() + ")."
                    : "This workout plan preview was already resolved (" + preview.getStatus().name() + ").";
            return new PreviewActionOutcome(
                    HttpStatus.CONFLICT,
                    WorkoutPlanChangePreviewActionResponse.builder()
                            .success(false)
                            .mutated(false)
                            .verified(false)
                            .stale(false)
                            .code("PREVIEW_ALREADY_RESOLVED")
                            .message(msg)
                            .preview(toPreviewResponse(preview, planName, null, false))
                            .build()
            );
        }

        preview.setStatus(WorkoutPlanPreviewStatus.CANCELLED);
        preview.setResolvedAt(ZonedDateTime.now());
        preview.setFailureReason(null);
        previewRepository.save(preview);
        syncAssistantMessageStatus(preview, false);

        String msg = isVn
                ? "Đã hủy các thay đổi đề xuất. Lịch tập hiện tại của bạn vẫn được giữ nguyên."
                : "Cancelled the proposed workout plan changes. Your workout plan remains unchanged.";

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_CANCELLED);
        meta.put("previewId", preview.getPreviewId());
        meta.put("status", WorkoutPlanPreviewStatus.CANCELLED.name());
        meta.put("mutated", false);

        AiMessageResponse assistantMsg = persistAssistantMessage
                ? saveSystemAssistantMessage(conversation, msg, "WORKOUT_PLAN_PREVIEW", safeToJson(meta))
                : null;

        return new PreviewActionOutcome(
                HttpStatus.OK,
                WorkoutPlanChangePreviewActionResponse.builder()
                        .success(true)
                        .mutated(false)
                        .verified(false)
                        .stale(false)
                        .code("CANCELLED")
                        .message(msg)
                        .preview(toPreviewResponse(preview, planName, null, false))
                        .assistantMessage(assistantMsg)
                        .build()
        );
    }

    public UserAiWorkoutToolService.WorkoutToolResult toConversationalToolResult(PreviewActionOutcome outcome) {
        WorkoutPlanChangePreviewActionResponse resp = outcome.response();
        if (resp == null) {
            return buildNonMutatingResult("workout_preview_action", "Unable to process workout preview action.", null, "UNKNOWN");
        }
        if (resp.isSuccess() && resp.isMutated() && resp.getPreview() != null) {
            String meta = buildConfirmedMetadataJson(resp.getPreview(),
                    resp.getPreview().getOperations() != null ? resp.getPreview().getOperations() : List.of());
            return new UserAiWorkoutToolService.WorkoutToolResult(
                    "confirm_workout_preview",
                    false,
                    "WORKOUT_PLAN_UPDATE",
                    true,
                    resp.getMessage(),
                    meta
            );
        }
        if (resp.isStale() && resp.getRefreshedPreview() != null) {
            String meta = buildPreviewMetadataJson(resp.getRefreshedPreview(), "refresh_stale_preview");
            return new UserAiWorkoutToolService.WorkoutToolResult(
                    "refresh_stale_preview",
                    false,
                    "WORKOUT_PLAN_PREVIEW",
                    true,
                    resp.getMessage(),
                    meta
            );
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_CANCELLED);
        if (resp.getPreview() != null) {
            meta.put("previewId", resp.getPreview().getPreviewId());
            meta.put("status", resp.getPreview().getStatus() != null ? resp.getPreview().getStatus().name() : null);
        }
        meta.put("code", resp.getCode());
        meta.put("mutated", false);
        return new UserAiWorkoutToolService.WorkoutToolResult(
                "cancel_workout_preview",
                false,
                "WORKOUT_PLAN_PREVIEW",
                resp.isSuccess(),
                resp.getMessage(),
                safeToJson(meta)
        );
    }

    // ==================== MESSAGE LINKING & HYDRATION ====================

    @Transactional
    public void attachAssistantMessageId(String previewId, Long assistantMessageId) {
        if (previewId == null || assistantMessageId == null) {
            return;
        }
        previewRepository.findAll().stream()
                .filter(p -> previewId.equals(p.getPreviewId()))
                .findFirst()
                .ifPresent(p -> {
                    p.setAssistantMessageId(assistantMessageId);
                    previewRepository.save(p);
                });
    }

    @Transactional
    public void attachAssistantMessageId(Long userId, String previewId, Long assistantMessageId) {
        if (userId == null || previewId == null || assistantMessageId == null) {
            return;
        }
        previewRepository.findByPreviewIdAndUserId(previewId, userId).ifPresent(p -> {
            p.setAssistantMessageId(assistantMessageId);
            previewRepository.save(p);
        });
    }

    @Transactional
    public String hydrateMessageMetadata(Long userId, String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank() || !rawMetadata.contains(METADATA_TYPE_PREVIEW)) {
            return rawMetadata;
        }
        try {
            JsonNode root = objectMapper.readTree(rawMetadata);
            if (!METADATA_TYPE_PREVIEW.equals(root.path("type").asText())) {
                return rawMetadata;
            }
            JsonNode previewNode = root.path("preview");
            String previewId = previewNode.path("previewId").asText(null);
            if (previewId == null || previewId.isBlank()) {
                return rawMetadata;
            }
            Optional<AiWorkoutPlanChangePreview> liveOpt = previewRepository.findByPreviewIdAndUserId(previewId, userId);
            if (liveOpt.isEmpty()) {
                return rawMetadata;
            }
            AiWorkoutPlanChangePreview live = liveOpt.get();
            if (live.getStatus() == WorkoutPlanPreviewStatus.PENDING && isExpired(live)) {
                markExpiredInternal(live);
            }
            if (root instanceof ObjectNode rootObj && previewNode instanceof ObjectNode previewObj) {
                previewObj.put("status", live.getStatus().name());
                if (live.getResolvedAt() != null) {
                    previewObj.put("resolvedAt", live.getResolvedAt().toString());
                }
                if (live.getFailureReason() != null) {
                    previewObj.put("failureReason", live.getFailureReason());
                    previewObj.put("stale", live.getFailureReason().startsWith("STALE_PREVIEW"));
                }
                rootObj.put("status", live.getStatus().name());
                return objectMapper.writeValueAsString(rootObj);
            }
        } catch (Exception ignored) {
        }
        return rawMetadata;
    }

    @Transactional
    public void deleteConversationPreviews(Long conversationId) {
        previewRepository.deleteByConversationId(conversationId);
    }

    // ==================== PLAN FINGERPRINTING ====================

    public String computePlanFingerprint(WorkoutPlan plan, List<WorkoutPlanDay> orderedDays) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("plan:")
                .append(plan.getId()).append('|')
                .append(nullSafe(plan.getName())).append('|')
                .append(plan.getSplitType() != null ? plan.getSplitType().name() : "").append('|')
                .append(plan.isActive()).append('\n');

        List<WorkoutPlanDay> sortedDays = new ArrayList<>(orderedDays);
        sortedDays.sort(Comparator
                .comparingInt((WorkoutPlanDay d) -> d.getDayOfWeek() != null ? d.getDayOfWeek().getOrder() : 99)
                .thenComparing(d -> d.getId() != null ? d.getId() : 0L));

        for (WorkoutPlanDay day : sortedDays) {
            canonical.append("day:")
                    .append(day.getId()).append('|')
                    .append(day.getDayOfWeek() != null ? day.getDayOfWeek().name() : "").append('|')
                    .append(nullSafe(day.getName())).append('|')
                    .append(day.getSessionType() != null ? day.getSessionType().name() : "").append('|')
                    .append(day.isRestDay()).append('|')
                    .append(day.getDisplayOrder()).append('\n');

            List<WorkoutPlanExercise> exercises = loadOrderedExercises(day);
            for (WorkoutPlanExercise pe : exercises) {
                canonical.append("ex:")
                        .append(pe.getId()).append('|')
                        .append(pe.getExercise() != null ? pe.getExercise().getId() : "").append('|')
                        .append(pe.getSets()).append('|')
                        .append(pe.getReps()).append('|')
                        .append(pe.getDurationSeconds()).append('|')
                        .append(pe.getRestSeconds()).append('|')
                        .append(pe.getDisplayOrder()).append('|')
                        .append(nullSafe(pe.getNotes())).append('\n');
            }
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute plan fingerprint", ex);
        }
    }

    // ==================== OPERATION PLANNING ====================

    private PlannedBatch planOperations(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            String toolName,
            WorkoutDayOfWeek explicitSourceDay,
            WorkoutDayOfWeek explicitTargetDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        // Check if prompt is a compound multi-operation request (e.g., "Remove X from Monday and add Y to Monday")
        List<String> clauses = splitCompoundClauses(prompt);
        if (clauses.size() > 1) {
            return planCompoundClauses(plan, days, clauses, selectedDayOfWeek, history, isVn);
        }

        return switch (toolName) {
            case UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS ->
                    planMoveOperation(plan, days, explicitSourceDay, explicitTargetDay, selectedDayOfWeek,
                            prompt, explicitExerciseName, history, isVn);
            case UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT ->
                    planAddOperation(plan, days, explicitTargetDay, selectedDayOfWeek,
                            prompt, explicitExerciseName, history, isVn);
            case UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT ->
                    planRemoveOperation(plan, days, explicitTargetDay, selectedDayOfWeek,
                            prompt, explicitExerciseName, history, isVn);
            case TOOL_UPDATE_EXERCISE_IN_WORKOUT ->
                    planUpdateExerciseOperation(plan, days, explicitTargetDay, selectedDayOfWeek,
                            prompt, explicitExerciseName, history, isVn);
            case TOOL_REORDER_EXERCISES_IN_WORKOUT ->
                    planReorderOperation(plan, days, explicitTargetDay, selectedDayOfWeek,
                            prompt, explicitExerciseName, history, isVn);
            case UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN ->
                    planUpdateDayOrExerciseOperation(plan, days, explicitTargetDay, selectedDayOfWeek,
                            prompt, explicitExerciseName, history, isVn);
            default -> new PlannedBatch(false, List.of(), null,
                    isVn ? "Yêu cầu chỉnh sửa lịch tập không được hỗ trợ." : "Unsupported workout modification request.",
                    null);
        };
    }

    private List<String> splitCompoundClauses(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }
        String[] parts = COMPOUND_SPLIT_PATTERN.split(prompt.trim());
        if (parts.length <= 1) {
            return List.of(prompt.trim());
        }
        List<String> valid = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                valid.add(trimmed);
            }
        }
        return valid.size() > 1 ? valid : List.of(prompt.trim());
    }

    private PlannedBatch planCompoundClauses(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            List<String> clauses,
            WorkoutDayOfWeek selectedDayOfWeek,
            List<AiMessage> history,
            boolean isVn
    ) {
        List<WorkoutPlanChangeOperation> combined = new ArrayList<>();
        for (String clause : clauses) {
            String clauseTool = inferClauseTool(clause);
            UserAiWorkoutToolService.MoveDays moveDays = UserAiWorkoutToolService.extractMoveDays(clause);
            WorkoutDayOfWeek weekday = UserAiWorkoutToolService.extractWeekday(clause);
            PlannedBatch sub = planOperations(
                    plan,
                    days,
                    clauseTool,
                    moveDays.sourceDay(),
                    moveDays.targetDay() != null ? moveDays.targetDay() : weekday,
                    selectedDayOfWeek,
                    clause,
                    null,
                    history,
                    isVn
            );
            if (!sub.success()) {
                return sub;
            }
            combined.addAll(sub.operations());
        }
        if (combined.isEmpty()) {
            return new PlannedBatch(false, List.of(), null,
                    isVn ? "Không có thay đổi hợp lệ nào được tìm thấy." : "No valid workout changes were found.",
                    null);
        }
        return new PlannedBatch(true, combined, buildBatchExplanation(combined, isVn), null, null);
    }

    private String inferClauseTool(String clause) {
        String lower = clause.toLowerCase(Locale.US).trim();
        if (startsWithVerb(lower, "move", "transfer", "shift", "chuyển", "dời")) {
            return UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS;
        }
        if (startsWithVerb(lower, "remove", "delete", "drop", "xóa", "bỏ")) {
            return UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT;
        }
        if (startsWithVerb(lower, "add", "insert", "thêm")) {
            return UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT;
        }
        if (startsWithVerb(lower, "reorder", "swap")) {
            return TOOL_REORDER_EXERCISES_IN_WORKOUT;
        }
        return TOOL_UPDATE_EXERCISE_IN_WORKOUT;
    }

    private boolean startsWithVerb(String lower, String... verbs) {
        for (String v : verbs) {
            if (lower.startsWith(v + " ") || lower.equals(v)) {
                return true;
            }
        }
        return false;
    }

    private PlannedBatch planMoveOperation(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            WorkoutDayOfWeek explicitSourceDay,
            WorkoutDayOfWeek explicitTargetDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        UserAiWorkoutToolService.MoveDays fromPrompt = UserAiWorkoutToolService.extractMoveDays(prompt);
        WorkoutDayOfWeek targetDow = fromPrompt.targetDay() != null
                ? fromPrompt.targetDay()
                : (explicitTargetDay != null ? explicitTargetDay : UserAiWorkoutToolService.extractWeekday(prompt));
        WorkoutDayOfWeek sourceDow = fromPrompt.sourceDay() != null
                ? fromPrompt.sourceDay()
                : explicitSourceDay;

        PrescriptionInput parsedOverride = parseExercisePrescription(prompt);
        String effectiveQuery = resolveEffectiveExerciseQuery(prompt, explicitExerciseName, history);

        if (targetDow == null) {
            String msg = isVn
                    ? "Bạn muốn chuyển bài tập sang thứ mấy trong tuần (ví dụ: Thứ Ba)?"
                    : "Which destination weekday would you like to move this exercise to (for example: Tuesday)?";
            return clarificationBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS, "MOVE",
                    explicitExerciseName, null, msg);
        }

        WorkoutPlanDay destDayEntity = findDay(days, targetDow);
        if (destDayEntity == null) {
            return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                    isVn ? "Không tìm thấy ngày tập đích trong lịch tập của bạn." : "Could not locate the destination workout day in your plan.",
                    targetDow, "DAY_NOT_FOUND");
        }

        WorkoutPlanDay sourceDayEntity = null;
        WorkoutPlanExercise sourceEx = null;

        if (sourceDow != null) {
            if (sourceDow == targetDow) {
                return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                        isVn ? "Ngày nguồn và ngày đích giống nhau (**" + formatDayLabel(sourceDow, true) + "**), nên không có thay đổi nào được thực hiện."
                                : "Source and destination days are both **" + formatDayLabel(sourceDow, false) + "**, so no changes were proposed.",
                        targetDow, "SAME_DAY");
            }
            sourceDayEntity = findDay(days, sourceDow);
            if (sourceDayEntity == null) {
                return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                        isVn ? "Không tìm thấy ngày tập nguồn." : "Could not locate the source workout day.",
                        sourceDow, "DAY_NOT_FOUND");
            }
            Optional<WorkoutPlanExercise> matchOpt = findBestMatchingPlanExercise(loadOrderedExercises(sourceDayEntity), effectiveQuery);
            if (matchOpt.isEmpty()) {
                return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                        isVn ? "Không tìm thấy bài tập cần chuyển trong **" + formatDayLabel(sourceDow, true) + "**. Không có thay đổi nào được thực hiện."
                                : "That exercise was not found on **" + formatDayLabel(sourceDow, false) + "**. No changes were made to your workout plan.",
                        sourceDow, "EXERCISE_NOT_FOUND");
            }
            sourceEx = matchOpt.get();
        } else {
            // Implicit source day resolution: search across all workout days in DB!
            List<WorkoutPlanExercise> nonTargetMatches = new ArrayList<>();
            WorkoutPlanExercise onTargetMatch = null;
            for (WorkoutPlanDay d : days) {
                Optional<WorkoutPlanExercise> m = findBestMatchingPlanExercise(loadOrderedExercises(d), effectiveQuery);
                if (m.isPresent()) {
                    if (d.getDayOfWeek() == targetDow) {
                        onTargetMatch = m.get();
                    } else {
                        nonTargetMatches.add(m.get());
                    }
                }
            }

            // If user has a selectedDayOfWeek in UI and it matches one of multiple source days, disambiguate only if needed
            if (nonTargetMatches.isEmpty()) {
                if (onTargetMatch != null) {
                    Exercise ex = onTargetMatch.getExercise();
                    if (parsedOverride.hasAny() && wouldChangePrescription(onTargetMatch, parsedOverride)) {
                        // Already on destination day, user asked to update attributes -> propose UPDATE_EXERCISE
                        WorkoutPlanChangeOperation updateOp = buildUpdateExerciseOp(destDayEntity, onTargetMatch, parsedOverride, isVn);
                        String explanation = isVn
                                ? "**" + ex.getName() + "** đã có sẵn trong " + formatDayLabel(targetDow, true)
                                        + "; đề xuất cập nhật thông số bài tập."
                                : "**" + ex.getName() + "** is already on " + formatDayLabel(targetDow, false)
                                        + "; proposing an update to its target prescription.";
                        return new PlannedBatch(true, List.of(updateOp), explanation, null, null);
                    }
                    return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                            isVn ? "**" + ex.getName() + "** đã có sẵn trong **" + formatDayLabel(targetDow, true) + "** rồi."
                                    : "**" + ex.getName() + "** is already scheduled on **" + formatDayLabel(targetDow, false) + "**. No changes needed.",
                            targetDow, "ALREADY_ON_TARGET_DAY");
                }
                return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                        isVn ? "Không tìm thấy bài tập cần chuyển trong lịch tập hiện tại của bạn. Không có thay đổi nào được thực hiện."
                                : "I couldn't find that exercise in your current workout plan. Please verify the exercise name.",
                        targetDow, "EXERCISE_NOT_FOUND");
            }

            if (nonTargetMatches.size() > 1) {
                String matchedDays = nonTargetMatches.stream()
                        .map(pe -> formatDayLabel(pe.getWorkoutPlanDay().getDayOfWeek(), isVn))
                        .distinct()
                        .collect(Collectors.joining(", "));
                String exName = nonTargetMatches.get(0).getExercise().getName();
                String msg = isVn
                        ? "Bài tập **" + exName + "** xuất hiện ở nhiều ngày (" + matchedDays
                                + "). Bạn muốn chuyển từ ngày nào sang **" + formatDayLabel(targetDow, true) + "**?"
                        : "**" + exName + "** appears on multiple days (" + matchedDays
                                + "). Which day would you like to move it from to **" + formatDayLabel(targetDow, false) + "**?";
                return clarificationBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS, "MOVE",
                        exName, targetDow, msg);
            }

            sourceEx = nonTargetMatches.get(0);
            sourceDayEntity = findDay(days, sourceEx.getWorkoutPlanDay().getDayOfWeek());
            sourceDow = sourceDayEntity.getDayOfWeek();
        }

        Exercise exercise = sourceEx.getExercise();

        // Duplicate prevention on destination day
        Optional<WorkoutPlanExercise> existingOnDest = loadOrderedExercises(destDayEntity).stream()
                .filter(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), exercise.getId()))
                .findFirst();

        if (existingOnDest.isPresent()) {
            if (parsedOverride.hasAny() && wouldChangePrescription(existingOnDest.get(), parsedOverride)) {
                WorkoutPlanChangeOperation removeOp = buildRemoveExerciseOp(sourceDayEntity, sourceEx, isVn);
                WorkoutPlanChangeOperation updateOp = buildUpdateExerciseOp(destDayEntity, existingOnDest.get(), parsedOverride, isVn);
                List<WorkoutPlanChangeOperation> ops = List.of(removeOp, updateOp);
                String explanation = isVn
                        ? "**" + exercise.getName() + "** đã có trong " + formatDayLabel(targetDow, true)
                                + ". Đề xuất xóa khỏi " + formatDayLabel(sourceDow, true) + " và cập nhật bài tập sẵn có ở "
                                + formatDayLabel(targetDow, true) + " để tránh trùng lặp."
                        : "**" + exercise.getName() + "** is already on " + formatDayLabel(targetDow, false)
                                + ". Proposing to remove it from " + formatDayLabel(sourceDow, false)
                                + " and update the existing entry on " + formatDayLabel(targetDow, false) + " without creating a duplicate.";
                return new PlannedBatch(true, ops, explanation, null, null);
            }
            return errorBatch(UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                    isVn ? "**" + exercise.getName() + "** đã có sẵn trong **" + formatDayLabel(targetDow, true)
                            + "**. Không thể tạo bài tập trùng lặp. Bạn có thể yêu cầu xóa ở " + formatDayLabel(sourceDow, true)
                            + " hoặc cập nhật thông số ở " + formatDayLabel(targetDow, true) + "."
                            : "**" + exercise.getName() + "** is already present on **" + formatDayLabel(targetDow, false)
                            + "**. To prevent duplicates, no duplicate move was proposed. Let me know if you want to remove it from "
                            + formatDayLabel(sourceDow, false) + " or update its prescription on " + formatDayLabel(targetDow, false) + ".",
                    targetDow, "ALREADY_EXISTS");
        }

        ExercisePrescriptionValues previousValues = toPrescriptionValues(sourceEx);
        ExercisePrescriptionValues proposedValues = copyValues(previousValues);
        proposedValues.setDisplayOrder(null);
        applyPrescriptionOverride(proposedValues, parsedOverride);

        WorkoutPlanChangeOperation moveOp = WorkoutPlanChangeOperation.builder()
                .operationType(WorkoutPlanOperationType.MOVE_EXERCISE)
                .sourceDayId(sourceDayEntity.getId())
                .sourceDayOfWeek(sourceDayEntity.getDayOfWeek())
                .sourceDayLabel(formatDayTitle(sourceDayEntity, isVn))
                .destinationDayId(destDayEntity.getId())
                .destinationDayOfWeek(destDayEntity.getDayOfWeek())
                .destinationDayLabel(formatDayTitle(destDayEntity, isVn))
                .workoutPlanExerciseId(sourceEx.getId())
                .exerciseId(exercise.getId())
                .exerciseName(exercise.getName())
                .previousValues(previousValues)
                .proposedValues(proposedValues)
                .build();
        moveOp.setSummary(buildOperationSummary(moveOp, isVn));

        return new PlannedBatch(true, List.of(moveOp), moveOp.getSummary(), null, null);
    }

    private record ClarificationContext(
            String pendingAction,
            String exerciseName,
            String exerciseQuery,
            boolean multiExerciseRequest,
            WorkoutDayOfWeek recommendedDayOfWeek
    ) {}

    private PlannedBatch planAddOperation(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        List<Exercise> publishedCatalog = loadPublishedCatalog();
        WorkoutDayOfWeek fromPrompt = UserAiWorkoutToolService.extractWeekday(prompt);
        WorkoutDayOfWeek dayToModify = resolveTargetDay(fromPrompt, explicitDay, selectedDayOfWeek);

        ClarificationContext clarCtx = extractLatestClarificationContext(history);
        List<Exercise> multiListMatches = (explicitExerciseName == null || explicitExerciseName.isBlank())
                ? extractMultipleExercisesFromList(publishedCatalog, prompt)
                : List.of();

        Optional<Exercise> matchedExercise = findRequestedExercise(publishedCatalog, prompt, explicitExerciseName, history);
        ExerciseCategory detectedCategory = detectCategory(prompt);
        if (detectedCategory == null && clarCtx != null && clarCtx.exerciseQuery() != null) {
            detectedCategory = detectCategory(clarCtx.exerciseQuery());
        }
        final ExerciseCategory requestedCategory = detectedCategory;

        boolean matchedDirectlyInPrompt = findBestMatchingExercise(publishedCatalog, prompt).isPresent();
        boolean pluralOrTopicRequest = multiListMatches.size() <= 1
                && isTopicOrPluralExerciseRequest(prompt, explicitExerciseName, matchedExercise, matchedDirectlyInPrompt, clarCtx);
        String topicQuery = pluralOrTopicRequest ? resolveTopicQuery(prompt, matchedExercise, matchedDirectlyInPrompt, clarCtx) : null;
        List<Exercise> topicCandidates = pluralOrTopicRequest
                ? findTopicMatchingExercises(publishedCatalog, topicQuery, requestedCategory)
                : List.of();
        if (pluralOrTopicRequest && !topicCandidates.isEmpty()) {
            matchedExercise = Optional.empty();
        }

        boolean genericCategoryRequest = matchedExercise.isEmpty()
                && !pluralOrTopicRequest
                && isGenericCategoryExerciseRequest(prompt, requestedCategory);

        WorkoutPlanContextService ctxSvc = getPlanContextService();
        WorkoutPlanContextService.ActiveWorkoutPlanContext planCtx = ctxSvc.buildPlanContext(plan, days, false, null);

        if (dayToModify == null) {
            String subjectLabel = resolveRequestedSubjectLabel(
                    matchedExercise.map(Exercise::getName).orElse(null),
                    topicQuery,
                    requestedCategory,
                    clarCtx
            );
            String evalQuery = topicQuery != null ? topicQuery
                    : (matchedExercise.isPresent() ? matchedExercise.get().getName()
                    : (requestedCategory != null ? requestedCategory.name() : ""));
            List<Exercise> evalCandidates = !topicCandidates.isEmpty() ? topicCandidates
                    : (matchedExercise.isPresent() ? List.of(matchedExercise.get())
                    : (requestedCategory != null
                        ? publishedCatalog.stream().filter(e -> e.getCategory() == requestedCategory).toList()
                        : List.of()));

            String semanticDay = ctxSvc.extractSemanticDayQueryWithPlanFallback(planCtx, prompt);
            if (semanticDay != null) {
                List<WorkoutPlanContextService.DayContext> matches = ctxSvc.findMatchingDays(planCtx, semanticDay);
                if (matches.size() == 1) {
                    dayToModify = matches.get(0).dayOfWeek();
                } else if (matches.size() > 1) {
                    String msg = ctxSvc.formatMultiDayMatchClarification(
                            planCtx, semanticDay, matches, subjectLabel, evalCandidates, isVn);
                    return clarificationBatch(
                            UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                            "ADD",
                            matchedExercise.map(Exercise::getName).orElse(null),
                            topicQuery,
                            pluralOrTopicRequest,
                            null,
                            null,
                            matches.stream().map(WorkoutPlanContextService.DayContext::dayOfWeek).toList(),
                            null,
                            null,
                            msg
                    );
                } else {
                    String msg = ctxSvc.formatNoMatchingDayClarification(planCtx, semanticDay, subjectLabel, isVn);
                    return clarificationBatch(
                            UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                            "ADD",
                            matchedExercise.map(Exercise::getName).orElse(null),
                            topicQuery,
                            pluralOrTopicRequest,
                            null,
                            null,
                            List.of(),
                            null,
                            null,
                            msg
                    );
                }
            } else if (clarCtx != null && clarCtx.recommendedDayOfWeek() != null && isAffirmativeAcceptance(prompt)) {
                dayToModify = clarCtx.recommendedDayOfWeek();
            } else {
                WorkoutPlanContextService.DayRecommendationPlan evalPlan =
                        ctxSvc.evaluateDaysForAddition(planCtx, evalQuery, evalCandidates);
                String msg = ctxSvc.formatProactiveRecommendationClarification(evalPlan, subjectLabel, isVn);
                WorkoutDayOfWeek recDow = evalPlan.primaryRecommendation() != null
                        ? evalPlan.primaryRecommendation().day().dayOfWeek() : null;
                List<WorkoutDayOfWeek> candidateDows = new ArrayList<>();
                if (recDow != null) {
                    candidateDows.add(recDow);
                }
                evalPlan.alternatives().forEach(a -> candidateDows.add(a.day().dayOfWeek()));
                return clarificationBatch(
                        UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                        "ADD",
                        matchedExercise.map(Exercise::getName).orElse(null),
                        topicQuery,
                        pluralOrTopicRequest,
                        null,
                        recDow,
                        candidateDows,
                        null,
                        null,
                        msg
                );
            }
        }

        WorkoutPlanDay destDay = findDay(days, dayToModify);
        if (destDay == null) {
            return errorBatch(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                    isVn ? "Không tìm thấy ngày tập." : "Could not find the target workout day.",
                    dayToModify, "DAY_NOT_FOUND");
        }

        List<WorkoutPlanExercise> existingDayExercises = loadOrderedExercises(destDay);
        Map<Long, WorkoutPlanExercise> existingByExerciseId = new LinkedHashMap<>();
        int existingDayTotalSets = 0;
        for (WorkoutPlanExercise pe : existingDayExercises) {
            if (pe.getExercise() != null) {
                existingByExerciseId.put(pe.getExercise().getId(), pe);
            }
            if (pe.getSets() != null) {
                existingDayTotalSets += pe.getSets();
            }
        }

        boolean destIsRestDay = destDay.isRestDay() || destDay.getSessionType() == WorkoutSessionType.REST;
        PrescriptionInput parsedPrescription = parseExercisePrescription(prompt);

        if (multiListMatches.size() > 1) {
            List<WorkoutPlanChangeOperation> ops = new ArrayList<>();
            List<String> skippedDupNames = new ArrayList<>();
            for (Exercise ex : multiListMatches) {
                if (existingByExerciseId.containsKey(ex.getId())) {
                    skippedDupNames.add(ex.getName());
                } else {
                    ops.add(buildAddExerciseOp(destDay, ex, parsedPrescription, isVn));
                }
            }
            if (ops.isEmpty()) {
                String msg = "**" + String.join("**, **", skippedDupNames) + "** are already scheduled on **"
                        + formatDayLabel(dayToModify, false) + "**. No duplicates were added.";
                return errorBatch(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "ALREADY_EXISTS");
            }
            String explanation = buildAdditionExplanationWithContext(
                    planCtx, destDay, destIsRestDay, ops, skippedDupNames, existingDayExercises.size(), existingDayTotalSets, isVn);
            return new PlannedBatch(true, ops, explanation, null, null);
        }

        if (pluralOrTopicRequest) {
            if (topicCandidates.isEmpty()) {
                String msg = isVn
                        ? "Không tìm thấy bài tập phù hợp trong danh mục công khai để thêm vào **"
                                + formatDayLabel(dayToModify, true) + "**. Không có thay đổi nào được thực hiện."
                        : "I couldn't find matching published exercises in the catalog for that request. No changes were made to **"
                                + formatDayLabel(dayToModify, false) + "**.";
                return errorBatch(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "EXERCISE_NOT_FOUND");
            }
            int explicitCount = parseRequestedGenericCount(prompt, -1);
            int targetCount = explicitCount > 0
                    ? explicitCount
                    : ((existingDayExercises.size() >= 4 || existingDayTotalSets >= 14) ? 1 : 2);

            List<Exercise> nonDupCandidates = prioritizeComplementaryCandidates(
                    topicCandidates.stream()
                            .filter(e -> !existingByExerciseId.containsKey(e.getId()))
                            .toList(),
                    existingDayExercises
            );
            List<String> skippedDupNames = topicCandidates.stream()
                    .filter(e -> existingByExerciseId.containsKey(e.getId()))
                    .map(Exercise::getName)
                    .toList();

            if (nonDupCandidates.isEmpty()) {
                String alreadyNames = skippedDupNames.stream().collect(Collectors.joining(", "));
                String msg = isVn
                        ? "Tất cả bài tập phù hợp (**" + alreadyNames + "**) đã có sẵn trong **"
                                + formatDayLabel(dayToModify, true) + "**. Không thêm bài trùng lặp."
                        : "All matching catalog exercises (**" + alreadyNames + "**) are already scheduled on **"
                                + formatDayLabel(dayToModify, false) + "**. No duplicate exercises were added.";
                return errorBatch(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "ALREADY_EXISTS");
            }

            List<WorkoutPlanChangeOperation> ops = nonDupCandidates.stream()
                    .limit(targetCount)
                    .map(ex -> buildAddExerciseOp(destDay, ex, parsedPrescription, isVn))
                    .toList();
            String explanation = buildAdditionExplanationWithContext(
                    planCtx, destDay, destIsRestDay, ops, skippedDupNames, existingDayExercises.size(), existingDayTotalSets, isVn);
            return new PlannedBatch(true, ops, explanation, null, null);
        }

        // Scenario D: Duplicate prevention (offer UPDATE only when user requested changes!)
        if (matchedExercise.isPresent()) {
            Exercise exact = matchedExercise.get();
            WorkoutPlanExercise existing = existingByExerciseId.get(exact.getId());
            if (existing != null) {
                if (parsedPrescription.hasAny() && wouldChangePrescription(existing, parsedPrescription)) {
                    WorkoutPlanChangeOperation updateOp = buildUpdateExerciseOp(destDay, existing, parsedPrescription, isVn);
                    String explanation = isVn
                            ? "**" + exact.getName() + "** đã có trong " + formatDayLabel(dayToModify, true)
                                    + ". Thay vì tạo bài trùng lặp, tôi đề xuất cập nhật thông số hiện có:"
                            : "**" + exact.getName() + "** is already present on " + formatDayLabel(dayToModify, false)
                                    + ". Instead of creating a duplicate, here is a preview to update its settings:";
                    return new PlannedBatch(true, List.of(updateOp), explanation, null, null);
                }
                String currentSummary = formatPrescriptionSummary(toPrescriptionValues(existing), isVn);
                String msg = isVn
                        ? "Bài tập **" + exact.getName() + "** đã có trong lịch tập **" + formatDayLabel(dayToModify, true)
                                + "** (" + currentSummary + ") rồi. Không thêm trùng hoặc tự ý thay thế bằng bài tập khác."
                        : "**" + exact.getName() + "** is already scheduled on **" + formatDayLabel(dayToModify, false)
                                + "** (" + currentSummary + "). No duplicate or substitute exercise was added.";
                return errorBatch(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "ALREADY_EXISTS");
            }

            WorkoutPlanChangeOperation addOp = buildAddExerciseOp(destDay, exact, parsedPrescription, isVn);
            String explanation = (destIsRestDay || existingDayExercises.size() >= 4 || existingDayTotalSets >= 14)
                    ? buildAdditionExplanationWithContext(
                            planCtx, destDay, destIsRestDay, List.of(addOp), List.of(), existingDayExercises.size(), existingDayTotalSets, isVn)
                    : addOp.getSummary();
            return new PlannedBatch(true, List.of(addOp), explanation, null, null);
        }

        if (genericCategoryRequest) {
            int requestedCount = parseRequestedGenericCount(prompt, 1);
            List<Exercise> candidates = publishedCatalog.stream()
                    .filter(e -> !existingByExerciseId.containsKey(e.getId()))
                    .filter(e -> e.getCategory() == requestedCategory)
                    .limit(requestedCount)
                    .toList();
            if (!candidates.isEmpty()) {
                List<WorkoutPlanChangeOperation> ops = candidates.stream()
                        .map(ex -> buildAddExerciseOp(destDay, ex, parsedPrescription, isVn))
                        .toList();
                String explanation = (destIsRestDay || existingDayExercises.size() >= 4 || existingDayTotalSets >= 14)
                        ? buildAdditionExplanationWithContext(
                                planCtx, destDay, destIsRestDay, ops, List.of(), existingDayExercises.size(), existingDayTotalSets, isVn)
                        : buildBatchExplanation(ops, isVn);
                return new PlannedBatch(true, ops, explanation, null, null);
            }
        }

        String msg = isVn
                ? "Không tìm thấy bài tập khớp chính xác trong danh mục công khai để thêm vào **"
                        + formatDayLabel(dayToModify, true) + "**. Không có thay đổi nào được thực hiện."
                : "I couldn't find a matching published exercise in the catalog for that request. Please specify the exact exercise name; no changes were made to **"
                        + formatDayLabel(dayToModify, false) + "**.";
        return errorBatch(UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, msg, dayToModify, "EXERCISE_NOT_FOUND");
    }

    private PlannedBatch planRemoveOperation(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        WorkoutDayOfWeek dayFilter = resolveTargetDay(UserAiWorkoutToolService.extractWeekday(prompt), explicitDay, selectedDayOfWeek);
        String effectiveQuery = resolveEffectiveExerciseQuery(prompt, explicitExerciseName, history);

        if (dayFilter == null) {
            List<WorkoutPlanExercise> matchesAcrossDays = new ArrayList<>();
            for (WorkoutPlanDay d : days) {
                findBestMatchingPlanExercise(loadOrderedExercises(d), effectiveQuery).ifPresent(matchesAcrossDays::add);
            }
            if (matchesAcrossDays.isEmpty()) {
                String msg = isVn
                        ? "Không tìm thấy bài tập cần xóa trong lịch tập của bạn. Vui lòng nêu rõ tên bài tập và ngày tập."
                        : "I couldn't find that exercise in your current workout plan. Please specify the exact exercise name and weekday.";
                return errorBatch(UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT, msg, null, "EXERCISE_NOT_FOUND");
            }
            if (matchesAcrossDays.size() > 1) {
                String matchedDays = matchesAcrossDays.stream()
                        .map(pe -> formatDayLabel(pe.getWorkoutPlanDay().getDayOfWeek(), isVn))
                        .distinct()
                        .collect(Collectors.joining(", "));
                String exName = matchesAcrossDays.get(0).getExercise().getName();
                String msg = isVn
                        ? "Bài tập **" + exName + "** xuất hiện ở nhiều ngày (" + matchedDays + "). Bạn muốn xóa ở ngày nào?"
                        : "**" + exName + "** appears on multiple days (" + matchedDays + "). Which weekday would you like to remove it from?";
                return clarificationBatch(UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT, "REMOVE", exName, null, msg);
            }
            dayFilter = matchesAcrossDays.get(0).getWorkoutPlanDay().getDayOfWeek();
        }

        WorkoutPlanDay sourceDay = findDay(days, dayFilter);
        if (sourceDay == null) {
            return errorBatch(UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT,
                    isVn ? "Không tìm thấy ngày tập cần xóa." : "Could not find the specified workout day.",
                    dayFilter, "DAY_NOT_FOUND");
        }

        Optional<WorkoutPlanExercise> matchOpt = findBestMatchingPlanExercise(loadOrderedExercises(sourceDay), effectiveQuery);
        if (matchOpt.isEmpty()) {
            String msg = isVn
                    ? "Không tìm thấy bài tập phù hợp trong **" + formatDayLabel(dayFilter, true) + "**. Không có thay đổi nào được thực hiện ở các ngày khác."
                    : "That exercise was not found on **" + formatDayLabel(dayFilter, false) + "**. No changes were made to any other day.";
            return errorBatch(UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT, msg, dayFilter, "EXERCISE_NOT_FOUND");
        }

        WorkoutPlanChangeOperation removeOp = buildRemoveExerciseOp(sourceDay, matchOpt.get(), isVn);
        return new PlannedBatch(true, List.of(removeOp), removeOp.getSummary(), null, null);
    }

    private PlannedBatch planUpdateExerciseOperation(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        WorkoutDayOfWeek dayFilter = resolveTargetDay(UserAiWorkoutToolService.extractWeekday(prompt), explicitDay, selectedDayOfWeek);
        String effectiveQuery = resolveEffectiveExerciseQuery(prompt, explicitExerciseName, history);
        PrescriptionInput parsedOverride = parseExercisePrescription(prompt);

        if (!parsedOverride.hasAny()) {
            String msg = isVn
                    ? "Vui lòng cho biết giá trị bạn muốn cập nhật (ví dụ: số hiệp, số lần, thời gian giữ hoặc thời gian nghỉ)."
                    : "Please specify what you'd like to change (for example: sets, reps, hold duration, or rest time).";
            return errorBatch(TOOL_UPDATE_EXERCISE_IN_WORKOUT, msg, dayFilter, "MISSING_UPDATE_VALUES");
        }

        WorkoutPlanDay targetDay = null;
        WorkoutPlanExercise targetEx = null;

        if (dayFilter != null) {
            targetDay = findDay(days, dayFilter);
            if (targetDay == null) {
                return errorBatch(TOOL_UPDATE_EXERCISE_IN_WORKOUT,
                        isVn ? "Không tìm thấy ngày tập." : "Could not locate that workout day.",
                        dayFilter, "DAY_NOT_FOUND");
            }
            Optional<WorkoutPlanExercise> m = findBestMatchingPlanExercise(loadOrderedExercises(targetDay), effectiveQuery);
            if (m.isEmpty()) {
                return errorBatch(TOOL_UPDATE_EXERCISE_IN_WORKOUT,
                        isVn ? "Không tìm thấy bài tập phù hợp trong **" + formatDayLabel(dayFilter, true) + "**."
                                : "Could not find that exercise on **" + formatDayLabel(dayFilter, false) + "**.",
                        dayFilter, "EXERCISE_NOT_FOUND");
            }
            targetEx = m.get();
        } else {
            List<WorkoutPlanExercise> matches = new ArrayList<>();
            for (WorkoutPlanDay d : days) {
                findBestMatchingPlanExercise(loadOrderedExercises(d), effectiveQuery).ifPresent(matches::add);
            }
            if (matches.isEmpty()) {
                return errorBatch(TOOL_UPDATE_EXERCISE_IN_WORKOUT,
                        isVn ? "Không tìm thấy bài tập cần cập nhật trong lịch tập hiện tại của bạn."
                                : "I couldn't find that exercise in your current workout plan.",
                        null, "EXERCISE_NOT_FOUND");
            }
            if (matches.size() > 1) {
                String matchedDays = matches.stream()
                        .map(pe -> formatDayLabel(pe.getWorkoutPlanDay().getDayOfWeek(), isVn))
                        .distinct()
                        .collect(Collectors.joining(", "));
                String exName = matches.get(0).getExercise().getName();
                String msg = isVn
                        ? "Bài tập **" + exName + "** xuất hiện ở nhiều ngày (" + matchedDays + "). Bạn muốn cập nhật ở ngày nào?"
                        : "**" + exName + "** appears on multiple days (" + matchedDays + "). Which weekday would you like to update?";
                return clarificationBatch(TOOL_UPDATE_EXERCISE_IN_WORKOUT, "UPDATE", exName, null, msg);
            }
            targetEx = matches.get(0);
            targetDay = findDay(days, targetEx.getWorkoutPlanDay().getDayOfWeek());
        }

        if (!wouldChangePrescription(targetEx, parsedOverride)) {
            String msg = isVn
                    ? "**" + targetEx.getExercise().getName() + "** trên **" + formatDayLabel(targetDay.getDayOfWeek(), true)
                            + "** đã có sẵn thông số đó rồi."
                    : "**" + targetEx.getExercise().getName() + "** on **" + formatDayLabel(targetDay.getDayOfWeek(), false)
                            + "** already has those exact settings.";
            return errorBatch(TOOL_UPDATE_EXERCISE_IN_WORKOUT, msg, targetDay.getDayOfWeek(), "NO_CHANGES");
        }

        WorkoutPlanChangeOperation op = buildUpdateExerciseOp(targetDay, targetEx, parsedOverride, isVn);
        return new PlannedBatch(true, List.of(op), op.getSummary(), null, null);
    }

    private PlannedBatch planReorderOperation(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        WorkoutDayOfWeek dayFilter = resolveTargetDay(UserAiWorkoutToolService.extractWeekday(prompt), explicitDay, selectedDayOfWeek);
        String effectiveQuery = resolveEffectiveExerciseQuery(prompt, explicitExerciseName, history);

        WorkoutPlanDay targetDay = dayFilter != null ? findDay(days, dayFilter) : null;
        if (targetDay == null) {
            List<WorkoutPlanDay> matchedDays = new ArrayList<>();
            for (WorkoutPlanDay d : days) {
                if (findBestMatchingPlanExercise(loadOrderedExercises(d), effectiveQuery).isPresent()) {
                    matchedDays.add(d);
                }
            }
            if (matchedDays.size() == 1) {
                targetDay = matchedDays.get(0);
            } else if (matchedDays.size() > 1) {
                String labels = matchedDays.stream().map(d -> formatDayLabel(d.getDayOfWeek(), isVn)).collect(Collectors.joining(", "));
                return clarificationBatch(TOOL_REORDER_EXERCISES_IN_WORKOUT, "REORDER", null, null,
                        isVn ? "Bạn muốn sắp xếp lại thứ tự bài tập của ngày nào (" + labels + ")?"
                                : "Which weekday's exercises would you like to reorder (" + labels + ")?");
            }
        }
        if (targetDay == null) {
            return errorBatch(TOOL_REORDER_EXERCISES_IN_WORKOUT,
                    isVn ? "Vui lòng nêu rõ ngày tập bạn muốn sắp xếp lại thứ tự bài tập."
                            : "Please specify which workout day you'd like to reorder.",
                    dayFilter, "DAY_NOT_FOUND");
        }

        List<WorkoutPlanExercise> current = new ArrayList<>(loadOrderedExercises(targetDay));
        if (current.size() < 2) {
            return errorBatch(TOOL_REORDER_EXERCISES_IN_WORKOUT,
                    isVn ? formatDayLabel(targetDay.getDayOfWeek(), true) + " cần ít nhất 2 bài tập để thay đổi thứ tự."
                            : formatDayLabel(targetDay.getDayOfWeek(), false) + " needs at least 2 exercises to reorder.",
                    targetDay.getDayOfWeek(), "NOT_ENOUGH_EXERCISES");
        }

        List<WorkoutPlanExercise> reordered = computeReorderedList(current, prompt, effectiveQuery);
        List<Long> oldIds = current.stream().map(WorkoutPlanExercise::getId).toList();
        List<Long> newIds = reordered.stream().map(WorkoutPlanExercise::getId).toList();
        if (oldIds.equals(newIds)) {
            return errorBatch(TOOL_REORDER_EXERCISES_IN_WORKOUT,
                    isVn ? "Thứ tự bài tập trên **" + formatDayLabel(targetDay.getDayOfWeek(), true) + "** không thay đổi."
                            : "The exercise order on **" + formatDayLabel(targetDay.getDayOfWeek(), false) + "** is already in that order.",
                    targetDay.getDayOfWeek(), "NO_CHANGES");
        }

        List<String> oldNames = current.stream().map(pe -> pe.getExercise().getName()).toList();
        List<String> newNames = reordered.stream().map(pe -> pe.getExercise().getName()).toList();

        WorkoutPlanChangeOperation op = WorkoutPlanChangeOperation.builder()
                .operationType(WorkoutPlanOperationType.REORDER_EXERCISES)
                .sourceDayId(targetDay.getId())
                .sourceDayOfWeek(targetDay.getDayOfWeek())
                .sourceDayLabel(formatDayTitle(targetDay, isVn))
                .destinationDayId(targetDay.getId())
                .destinationDayOfWeek(targetDay.getDayOfWeek())
                .destinationDayLabel(formatDayTitle(targetDay, isVn))
                .exerciseName("Exercise Order")
                .previousValues(ExercisePrescriptionValues.builder()
                        .orderedAssignmentIds(oldIds)
                        .orderedExerciseNames(oldNames)
                        .build())
                .proposedValues(ExercisePrescriptionValues.builder()
                        .orderedAssignmentIds(newIds)
                        .orderedExerciseNames(newNames)
                        .build())
                .build();
        op.setSummary(buildOperationSummary(op, isVn));
        return new PlannedBatch(true, List.of(op), op.getSummary(), null, null);
    }

    private PlannedBatch planUpdateDayOrExerciseOperation(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek,
            String prompt,
            String explicitExerciseName,
            List<AiMessage> history,
            boolean isVn
    ) {
        // If user asked to update a specific exercise's sets/reps/hold/rest, route to UPDATE_EXERCISE!
        PrescriptionInput prescription = parseExercisePrescription(prompt);
        if (prescription.hasAny()) {
            PlannedBatch exerciseUpdate = planUpdateExerciseOperation(
                    plan, days, explicitDay, selectedDayOfWeek, prompt, explicitExerciseName, history, isVn);
            if (exerciseUpdate.success() || (exerciseUpdate.nonPreviewMetadataJson() != null
                    && exerciseUpdate.nonPreviewMetadataJson().contains(METADATA_TYPE_CLARIFICATION))) {
                return exerciseUpdate;
            }
        }

        WorkoutDayOfWeek dayToUpdate = resolveTargetDay(UserAiWorkoutToolService.extractWeekday(prompt), explicitDay, selectedDayOfWeek);
        if (dayToUpdate == null) {
            return errorBatch(UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN,
                    isVn ? "Vui lòng nêu rõ ngày tập bạn muốn chỉnh sửa (ví dụ: \"Đổi Thứ Hai thành Push Day\")."
                            : "Please specify which workout day you'd like to update (for example: \"Change Monday's workout to Push Day\").",
                    null, "MISSING_WEEKDAY");
        }

        WorkoutPlanDay day = findDay(days, dayToUpdate);
        if (day == null) {
            return errorBatch(UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN,
                    isVn ? "Không tìm thấy ngày tập tương ứng trong lịch tập của bạn."
                            : "Could not locate that training day in your workout plan.",
                    dayToUpdate, "DAY_NOT_FOUND");
        }

        String requestedTitle = extractRequestedDayTitle(prompt);
        if (requestedTitle == null || requestedTitle.isBlank()) {
            return errorBatch(UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN,
                    isVn ? "Vui lòng nêu rõ tên buổi tập mới cho " + formatDayLabel(dayToUpdate, true) + "."
                            : "Please specify what you'd like to change " + formatDayLabel(dayToUpdate, false) + "'s workout to.",
                    dayToUpdate, "MISSING_DAY_TITLE");
        }

        boolean nextRest = requestedTitle.toLowerCase(Locale.US).contains("rest");
        WorkoutSessionType nextSessionType = nextRest ? WorkoutSessionType.REST : inferSessionType(requestedTitle);
        if (nextSessionType == null) {
            nextSessionType = day.getSessionType() != null && day.getSessionType() != WorkoutSessionType.REST
                    ? day.getSessionType()
                    : WorkoutSessionType.CUSTOM;
        }

        ExercisePrescriptionValues prev = ExercisePrescriptionValues.builder()
                .dayName(day.getName())
                .sessionType(day.getSessionType() != null ? day.getSessionType().name() : null)
                .restDay(day.isRestDay())
                .build();
        ExercisePrescriptionValues proposed = ExercisePrescriptionValues.builder()
                .dayName(requestedTitle)
                .sessionType(nextSessionType.name())
                .restDay(nextRest)
                .build();

        WorkoutPlanChangeOperation op = WorkoutPlanChangeOperation.builder()
                .operationType(WorkoutPlanOperationType.UPDATE_DAY)
                .sourceDayId(day.getId())
                .sourceDayOfWeek(day.getDayOfWeek())
                .sourceDayLabel(formatDayTitle(day, isVn))
                .destinationDayId(day.getId())
                .destinationDayOfWeek(day.getDayOfWeek())
                .destinationDayLabel(formatDayTitle(day, isVn))
                .exerciseName("Workout Day Focus")
                .previousValues(prev)
                .proposedValues(proposed)
                .build();
        op.setSummary(buildOperationSummary(op, isVn));
        return new PlannedBatch(true, List.of(op), op.getSummary(), null, null);
    }

    // ==================== REVALIDATION & STALE RE-PLANNING ====================

    private List<String> validateOperationsAgainstCurrentPlan(
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            List<WorkoutPlanChangeOperation> operations
    ) {
        List<String> errors = new ArrayList<>();
        Map<Long, WorkoutPlanDay> daysById = new HashMap<>();
        Map<Long, List<WorkoutPlanExercise>> exercisesByDayId = new HashMap<>();
        for (WorkoutPlanDay d : days) {
            daysById.put(d.getId(), d);
            exercisesByDayId.put(d.getId(), new ArrayList<>(loadOrderedExercises(d)));
        }

        for (WorkoutPlanChangeOperation op : operations) {
            switch (op.getOperationType()) {
                case ADD_EXERCISE -> {
                    WorkoutPlanDay dest = daysById.get(op.getDestinationDayId());
                    if (dest == null) {
                        errors.add("Destination workout day no longer exists.");
                        continue;
                    }
                    if (op.getExerciseId() == null || exerciseRepository.findByIdAndActiveTrue(op.getExerciseId()).isEmpty()) {
                        errors.add("Exercise '" + op.getExerciseName() + "' is no longer available.");
                        continue;
                    }
                    boolean dup = exercisesByDayId.get(dest.getId()).stream()
                            .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), op.getExerciseId()));
                    if (dup) {
                        errors.add(op.getExerciseName() + " is already scheduled on " + dest.getDayOfWeek() + ".");
                    }
                    validatePrescriptionBounds(op.getProposedValues(), errors);
                }
                case REMOVE_EXERCISE -> {
                    WorkoutPlanDay src = daysById.get(op.getSourceDayId());
                    if (src == null) {
                        errors.add("Source workout day no longer exists.");
                        continue;
                    }
                    Optional<WorkoutPlanExercise> found = exercisesByDayId.get(src.getId()).stream()
                            .filter(pe -> Objects.equals(pe.getId(), op.getWorkoutPlanExerciseId()))
                            .findFirst();
                    if (found.isEmpty()) {
                        errors.add(op.getExerciseName() + " is no longer on " + src.getDayOfWeek() + ".");
                    } else {
                        exercisesByDayId.get(src.getId()).remove(found.get());
                    }
                }
                case UPDATE_EXERCISE -> {
                    Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
                    WorkoutPlanDay day = daysById.get(dayId);
                    if (day == null) {
                        errors.add("Target workout day no longer exists.");
                        continue;
                    }
                    boolean exists = exercisesByDayId.get(day.getId()).stream()
                            .anyMatch(pe -> Objects.equals(pe.getId(), op.getWorkoutPlanExerciseId()));
                    if (!exists) {
                        errors.add(op.getExerciseName() + " is no longer present on " + day.getDayOfWeek() + ".");
                    }
                    validatePrescriptionBounds(op.getProposedValues(), errors);
                }
                case MOVE_EXERCISE -> {
                    WorkoutPlanDay src = daysById.get(op.getSourceDayId());
                    WorkoutPlanDay dest = daysById.get(op.getDestinationDayId());
                    if (src == null || dest == null) {
                        errors.add("Source or destination workout day no longer exists.");
                        continue;
                    }
                    if (Objects.equals(src.getId(), dest.getId())) {
                        errors.add("Source and destination days cannot be the same.");
                        continue;
                    }
                    Optional<WorkoutPlanExercise> foundSrc = exercisesByDayId.get(src.getId()).stream()
                            .filter(pe -> Objects.equals(pe.getId(), op.getWorkoutPlanExerciseId()))
                            .findFirst();
                    if (foundSrc.isEmpty()) {
                        errors.add(op.getExerciseName() + " is no longer on " + src.getDayOfWeek() + ".");
                        continue;
                    }
                    boolean dupDest = exercisesByDayId.get(dest.getId()).stream()
                            .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), op.getExerciseId()));
                    if (dupDest) {
                        errors.add(op.getExerciseName() + " already exists on " + dest.getDayOfWeek() + ".");
                    }
                    validatePrescriptionBounds(op.getProposedValues(), errors);
                }
                case REORDER_EXERCISES -> {
                    Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
                    WorkoutPlanDay day = daysById.get(dayId);
                    if (day == null) {
                        errors.add("Target workout day no longer exists.");
                        continue;
                    }
                    Set<Long> currentIds = exercisesByDayId.get(day.getId()).stream()
                            .map(WorkoutPlanExercise::getId)
                            .collect(Collectors.toSet());
                    List<Long> requestedIds = op.getProposedValues() != null
                            ? op.getProposedValues().getOrderedAssignmentIds() : List.of();
                    if (requestedIds == null || requestedIds.size() != currentIds.size() || !currentIds.containsAll(requestedIds)) {
                        errors.add("Exercises on " + day.getDayOfWeek() + " changed since preview generation.");
                    }
                }
                case UPDATE_DAY -> {
                    Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
                    if (!daysById.containsKey(dayId)) {
                        errors.add("Target workout day no longer exists.");
                    }
                }
            }
        }
        return errors;
    }

    private void validatePrescriptionBounds(ExercisePrescriptionValues vals, List<String> errors) {
        if (vals == null) {
            errors.add("Missing proposed prescription values.");
            return;
        }
        if (vals.getSets() != null && (vals.getSets() < 1 || vals.getSets() > 20)) {
            errors.add("Sets must be between 1 and 20.");
        }
        if (vals.getReps() != null && (vals.getReps() < 1 || vals.getReps() > 200)) {
            errors.add("Reps must be between 1 and 200.");
        }
        if (vals.getDurationSeconds() != null && (vals.getDurationSeconds() < 1 || vals.getDurationSeconds() > 600)) {
            errors.add("Hold duration must be between 1 and 600 seconds.");
        }
        if (vals.getRestSeconds() != null && (vals.getRestSeconds() < 0 || vals.getRestSeconds() > 600)) {
            errors.add("Rest time must be between 0 and 600 seconds.");
        }
    }

    private List<WorkoutPlanChangeOperation> attemptReplanOnFreshPlan(
            WorkoutPlan livePlan,
            List<WorkoutPlanDay> liveDays,
            List<WorkoutPlanChangeOperation> oldOps,
            boolean isVn
    ) {
        List<WorkoutPlanChangeOperation> refreshed = new ArrayList<>();
        for (WorkoutPlanChangeOperation old : oldOps) {
            switch (old.getOperationType()) {
                case MOVE_EXERCISE -> {
                    WorkoutPlanDay srcDay = findDay(liveDays, old.getSourceDayOfWeek());
                    WorkoutPlanDay destDay = findDay(liveDays, old.getDestinationDayOfWeek());
                    if (srcDay == null || destDay == null) {
                        return List.of();
                    }
                    Optional<WorkoutPlanExercise> freshSrc = loadOrderedExercises(srcDay).stream()
                            .filter(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), old.getExerciseId()))
                            .findFirst();
                    boolean destHas = loadOrderedExercises(destDay).stream()
                            .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), old.getExerciseId()));
                    if (freshSrc.isEmpty() || destHas) {
                        return List.of();
                    }
                    ExercisePrescriptionValues freshPrev = toPrescriptionValues(freshSrc.get());
                    ExercisePrescriptionValues freshProposed = mergeDeltasOntoFresh(freshPrev, old.getPreviousValues(), old.getProposedValues());
                    WorkoutPlanChangeOperation next = WorkoutPlanChangeOperation.builder()
                            .operationType(WorkoutPlanOperationType.MOVE_EXERCISE)
                            .sourceDayId(srcDay.getId())
                            .sourceDayOfWeek(srcDay.getDayOfWeek())
                            .sourceDayLabel(formatDayTitle(srcDay, isVn))
                            .destinationDayId(destDay.getId())
                            .destinationDayOfWeek(destDay.getDayOfWeek())
                            .destinationDayLabel(formatDayTitle(destDay, isVn))
                            .workoutPlanExerciseId(freshSrc.get().getId())
                            .exerciseId(old.getExerciseId())
                            .exerciseName(freshSrc.get().getExercise().getName())
                            .previousValues(freshPrev)
                            .proposedValues(freshProposed)
                            .build();
                    next.setSummary(buildOperationSummary(next, isVn));
                    refreshed.add(next);
                }
                case ADD_EXERCISE -> {
                    WorkoutPlanDay destDay = findDay(liveDays, old.getDestinationDayOfWeek());
                    if (destDay == null) {
                        return List.of();
                    }
                    boolean destHas = loadOrderedExercises(destDay).stream()
                            .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), old.getExerciseId()));
                    if (destHas) {
                        return List.of();
                    }
                    WorkoutPlanChangeOperation next = deepCopyOperation(old);
                    next.setDestinationDayId(destDay.getId());
                    next.setDestinationDayLabel(formatDayTitle(destDay, isVn));
                    next.setSummary(buildOperationSummary(next, isVn));
                    refreshed.add(next);
                }
                case REMOVE_EXERCISE -> {
                    WorkoutPlanDay srcDay = findDay(liveDays, old.getSourceDayOfWeek());
                    if (srcDay == null) {
                        return List.of();
                    }
                    Optional<WorkoutPlanExercise> freshSrc = loadOrderedExercises(srcDay).stream()
                            .filter(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), old.getExerciseId()))
                            .findFirst();
                    if (freshSrc.isEmpty()) {
                        return List.of();
                    }
                    refreshed.add(buildRemoveExerciseOp(srcDay, freshSrc.get(), isVn));
                }
                case UPDATE_EXERCISE -> {
                    WorkoutPlanDay targetDay = findDay(liveDays, old.getDestinationDayOfWeek());
                    if (targetDay == null) {
                        return List.of();
                    }
                    Optional<WorkoutPlanExercise> freshEx = loadOrderedExercises(targetDay).stream()
                            .filter(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), old.getExerciseId()))
                            .findFirst();
                    if (freshEx.isEmpty()) {
                        return List.of();
                    }
                    ExercisePrescriptionValues freshPrev = toPrescriptionValues(freshEx.get());
                    ExercisePrescriptionValues freshProposed = mergeDeltasOntoFresh(freshPrev, old.getPreviousValues(), old.getProposedValues());
                    WorkoutPlanChangeOperation next = WorkoutPlanChangeOperation.builder()
                            .operationType(WorkoutPlanOperationType.UPDATE_EXERCISE)
                            .sourceDayId(targetDay.getId())
                            .sourceDayOfWeek(targetDay.getDayOfWeek())
                            .sourceDayLabel(formatDayTitle(targetDay, isVn))
                            .destinationDayId(targetDay.getId())
                            .destinationDayOfWeek(targetDay.getDayOfWeek())
                            .destinationDayLabel(formatDayTitle(targetDay, isVn))
                            .workoutPlanExerciseId(freshEx.get().getId())
                            .exerciseId(old.getExerciseId())
                            .exerciseName(freshEx.get().getExercise().getName())
                            .previousValues(freshPrev)
                            .proposedValues(freshProposed)
                            .build();
                    next.setSummary(buildOperationSummary(next, isVn));
                    refreshed.add(next);
                }
                case UPDATE_DAY -> {
                    WorkoutPlanDay targetDay = findDay(liveDays, old.getDestinationDayOfWeek());
                    if (targetDay == null) {
                        return List.of();
                    }
                    WorkoutPlanChangeOperation next = deepCopyOperation(old);
                    next.setSourceDayId(targetDay.getId());
                    next.setDestinationDayId(targetDay.getId());
                    next.setPreviousValues(ExercisePrescriptionValues.builder()
                            .dayName(targetDay.getName())
                            .sessionType(targetDay.getSessionType() != null ? targetDay.getSessionType().name() : null)
                            .restDay(targetDay.isRestDay())
                            .build());
                    next.setSummary(buildOperationSummary(next, isVn));
                    refreshed.add(next);
                }
                case REORDER_EXERCISES -> {
                    return List.of();
                }
            }
        }
        return validateOperationsAgainstCurrentPlan(livePlan, liveDays, refreshed).isEmpty() ? refreshed : List.of();
    }

    private ExercisePrescriptionValues mergeDeltasOntoFresh(
            ExercisePrescriptionValues freshPrev,
            ExercisePrescriptionValues oldPrev,
            ExercisePrescriptionValues oldProposed
    ) {
        ExercisePrescriptionValues result = copyValues(freshPrev);
        result.setDisplayOrder(null);
        if (oldProposed == null) {
            return result;
        }
        if (oldPrev == null || !Objects.equals(oldPrev.getSets(), oldProposed.getSets())) {
            if (oldProposed.getSets() != null) result.setSets(oldProposed.getSets());
        }
        if (oldPrev == null || !Objects.equals(oldPrev.getReps(), oldProposed.getReps())) {
            result.setReps(oldProposed.getReps());
        }
        if (oldPrev == null || !Objects.equals(oldPrev.getDurationSeconds(), oldProposed.getDurationSeconds())) {
            result.setDurationSeconds(oldProposed.getDurationSeconds());
        }
        if (oldPrev == null || !Objects.equals(oldPrev.getRestSeconds(), oldProposed.getRestSeconds())) {
            if (oldProposed.getRestSeconds() != null) result.setRestSeconds(oldProposed.getRestSeconds());
        }
        return result;
    }

    // ==================== PERSISTENCE & FORMATTING HELPERS ====================

    private String supersedeActivePendingPreviews(Long userId, Long conversationId, String reason) {
        List<AiWorkoutPlanChangePreview> actives = previewRepository.findByUserIdAndConversationIdAndStatus(
                userId, conversationId, WorkoutPlanPreviewStatus.PENDING);
        String latestId = null;
        ZonedDateTime now = ZonedDateTime.now();
        for (AiWorkoutPlanChangePreview active : actives) {
            latestId = active.getPreviewId();
            active.setStatus(WorkoutPlanPreviewStatus.CANCELLED);
            active.setResolvedAt(now);
            active.setFailureReason(reason);
            previewRepository.save(active);
            syncAssistantMessageStatus(active, false);
        }
        return latestId;
    }

    private AiWorkoutPlanChangePreview persistPendingPreview(
            Long userId,
            Long conversationId,
            WorkoutPlan plan,
            List<WorkoutPlanDay> days,
            List<WorkoutPlanChangeOperation> operations,
            String explanation,
            String sourcePrompt,
            int revision,
            String supersedesPreviewId,
            boolean isVn
    ) {
        String previewId = UUID.randomUUID().toString();
        String fingerprint = computePlanFingerprint(plan, days);
        List<WorkoutPlanPreviewDayDiff> affectedDays = buildAffectedDayDiffs(days, operations, isVn);
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiresAt = now.plus(DEFAULT_PREVIEW_TTL);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("explanation", explanation);
        payload.put("affectedDays", affectedDays);
        if (supersedesPreviewId != null) {
            payload.put("supersedesPreviewId", supersedesPreviewId);
        }

        AiWorkoutPlanChangePreview entity = AiWorkoutPlanChangePreview.builder()
                .previewId(previewId)
                .userId(userId)
                .conversationId(conversationId)
                .workoutPlanId(plan.getId())
                .status(WorkoutPlanPreviewStatus.PENDING)
                .revision(revision)
                .summary(explanation)
                .sourcePrompt(sourcePrompt)
                .planFingerprint(fingerprint)
                .operationsJson(safeToJson(operations))
                .previewPayloadJson(safeToJson(payload))
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(expiresAt)
                .build();
        return previewRepository.save(entity);
    }

    private List<WorkoutPlanPreviewDayDiff> buildAffectedDayDiffs(
            List<WorkoutPlanDay> allDays,
            List<WorkoutPlanChangeOperation> operations,
            boolean isVn
    ) {
        Map<Long, WorkoutPlanDay> daysById = new HashMap<>();
        for (WorkoutPlanDay d : allDays) {
            daysById.put(d.getId(), d);
        }

        Map<Long, WorkoutPlanPreviewDayDiff> diffsByDayId = new LinkedHashMap<>();

        for (WorkoutPlanChangeOperation op : operations) {
            switch (op.getOperationType()) {
                case MOVE_EXERCISE -> {
                    WorkoutPlanPreviewDayDiff srcDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            op.getSourceDayId(), op.getSourceDayOfWeek(), op.getSourceDayLabel(), isVn);
                    srcDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("REMOVE")
                            .operationType(WorkoutPlanOperationType.MOVE_EXERCISE)
                            .exerciseId(op.getExerciseId())
                            .exerciseName(op.getExerciseName())
                            .previousSummary(formatPrescriptionSummary(op.getPreviousValues(), isVn))
                            .previousValues(op.getPreviousValues())
                            .detailText(isVn
                                    ? "Chuyển sang " + formatDayLabel(op.getDestinationDayOfWeek(), true)
                                    : "Moved to " + formatDayLabel(op.getDestinationDayOfWeek(), false))
                            .build());

                    WorkoutPlanPreviewDayDiff destDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            op.getDestinationDayId(), op.getDestinationDayOfWeek(), op.getDestinationDayLabel(), isVn);
                    String prevSum = formatPrescriptionSummary(op.getPreviousValues(), isVn);
                    String propSum = formatPrescriptionSummary(op.getProposedValues(), isVn);
                    destDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("ADD")
                            .operationType(WorkoutPlanOperationType.MOVE_EXERCISE)
                            .exerciseId(op.getExerciseId())
                            .exerciseName(op.getExerciseName())
                            .previousSummary(!Objects.equals(prevSum, propSum) ? prevSum : null)
                            .proposedSummary(propSum)
                            .previousValues(op.getPreviousValues())
                            .proposedValues(op.getProposedValues())
                            .detailText(isVn
                                    ? "Chuyển từ " + formatDayLabel(op.getSourceDayOfWeek(), true)
                                    : "Moved from " + formatDayLabel(op.getSourceDayOfWeek(), false))
                            .build());
                }
                case ADD_EXERCISE -> {
                    WorkoutPlanPreviewDayDiff destDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            op.getDestinationDayId(), op.getDestinationDayOfWeek(), op.getDestinationDayLabel(), isVn);
                    destDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("ADD")
                            .operationType(WorkoutPlanOperationType.ADD_EXERCISE)
                            .exerciseId(op.getExerciseId())
                            .exerciseName(op.getExerciseName())
                            .proposedSummary(formatPrescriptionSummary(op.getProposedValues(), isVn))
                            .proposedValues(op.getProposedValues())
                            .build());
                }
                case REMOVE_EXERCISE -> {
                    WorkoutPlanPreviewDayDiff srcDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            op.getSourceDayId(), op.getSourceDayOfWeek(), op.getSourceDayLabel(), isVn);
                    srcDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("REMOVE")
                            .operationType(WorkoutPlanOperationType.REMOVE_EXERCISE)
                            .exerciseId(op.getExerciseId())
                            .exerciseName(op.getExerciseName())
                            .previousSummary(formatPrescriptionSummary(op.getPreviousValues(), isVn))
                            .previousValues(op.getPreviousValues())
                            .build());
                }
                case UPDATE_EXERCISE -> {
                    Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
                    WorkoutPlanPreviewDayDiff dayDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            dayId, op.getDestinationDayOfWeek(), op.getDestinationDayLabel(), isVn);
                    dayDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("UPDATE")
                            .operationType(WorkoutPlanOperationType.UPDATE_EXERCISE)
                            .exerciseId(op.getExerciseId())
                            .exerciseName(op.getExerciseName())
                            .previousSummary(formatPrescriptionSummary(op.getPreviousValues(), isVn))
                            .proposedSummary(formatPrescriptionSummary(op.getProposedValues(), isVn))
                            .previousValues(op.getPreviousValues())
                            .proposedValues(op.getProposedValues())
                            .build());
                }
                case REORDER_EXERCISES -> {
                    Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
                    WorkoutPlanPreviewDayDiff dayDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            dayId, op.getDestinationDayOfWeek(), op.getDestinationDayLabel(), isVn);
                    String prevOrder = op.getPreviousValues() != null && op.getPreviousValues().getOrderedExerciseNames() != null
                            ? String.join(" → ", op.getPreviousValues().getOrderedExerciseNames()) : "";
                    String nextOrder = op.getProposedValues() != null && op.getProposedValues().getOrderedExerciseNames() != null
                            ? String.join(" → ", op.getProposedValues().getOrderedExerciseNames()) : "";
                    dayDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("MOVE")
                            .operationType(WorkoutPlanOperationType.REORDER_EXERCISES)
                            .exerciseName(isVn ? "Thứ tự bài tập" : "Exercise Order")
                            .previousSummary(prevOrder)
                            .proposedSummary(nextOrder)
                            .previousValues(op.getPreviousValues())
                            .proposedValues(op.getProposedValues())
                            .detailText(isVn ? "Sắp xếp lại thứ tự" : "Reordered exercises")
                            .build());
                }
                case UPDATE_DAY -> {
                    Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
                    WorkoutPlanPreviewDayDiff dayDiff = getOrCreateDayDiff(diffsByDayId, daysById,
                            dayId, op.getDestinationDayOfWeek(), op.getDestinationDayLabel(), isVn);
                    String prevDay = op.getPreviousValues() != null ? formatDayFocus(op.getPreviousValues()) : "";
                    String nextDay = op.getProposedValues() != null ? formatDayFocus(op.getProposedValues()) : "";
                    dayDiff.getChanges().add(WorkoutPlanPreviewItem.builder()
                            .badge("UPDATE")
                            .operationType(WorkoutPlanOperationType.UPDATE_DAY)
                            .exerciseName(isVn ? "Tên buổi tập" : "Workout Day")
                            .previousSummary(prevDay)
                            .proposedSummary(nextDay)
                            .previousValues(op.getPreviousValues())
                            .proposedValues(op.getProposedValues())
                            .build());
                }
            }
        }

        return diffsByDayId.values().stream()
                .sorted(Comparator.comparingInt(d -> d.getDayOfWeek() != null ? d.getDayOfWeek().getOrder() : 99))
                .toList();
    }

    private WorkoutPlanPreviewDayDiff getOrCreateDayDiff(
            Map<Long, WorkoutPlanPreviewDayDiff> map,
            Map<Long, WorkoutPlanDay> daysById,
            Long dayId,
            WorkoutDayOfWeek dow,
            String fallbackTitle,
            boolean isVn
    ) {
        return map.computeIfAbsent(dayId, id -> {
            WorkoutPlanDay d = daysById.get(id);
            String title = d != null ? formatDayTitle(d, isVn)
                    : (fallbackTitle != null ? fallbackTitle : formatDayLabel(dow, isVn));
            return WorkoutPlanPreviewDayDiff.builder()
                    .dayId(id)
                    .dayOfWeek(d != null ? d.getDayOfWeek() : dow)
                    .dayTitle(title)
                    .changes(new ArrayList<>())
                    .build();
        });
    }

    private String formatPreviewMarkdown(WorkoutPlanChangePreviewResponse preview, boolean isVn) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(isVn ? "Xem Trước Thay Đổi Lịch Tập" : "Workout Plan Preview");
        if (preview.getRevision() != null && preview.getRevision() > 1) {
            sb.append(" (Rev ").append(preview.getRevision()).append(")");
        }
        sb.append("\n\n");
        if (preview.getExplanation() != null && !preview.getExplanation().isBlank()) {
            sb.append(preview.getExplanation()).append("\n\n");
        }

        for (WorkoutPlanPreviewDayDiff dayDiff : preview.getAffectedDays()) {
            sb.append("**").append(dayDiff.getDayTitle()).append("**\n");
            for (WorkoutPlanPreviewItem item : dayDiff.getChanges()) {
                sb.append("- ").append(item.getBadge()).append(": ").append(item.getExerciseName());
                if ("UPDATE".equals(item.getBadge()) || "MOVE".equals(item.getBadge())) {
                    if (item.getPreviousSummary() != null && !item.getPreviousSummary().isBlank()) {
                        sb.append(" — ").append(item.getPreviousSummary()).append(" → ").append(item.getProposedSummary());
                    } else if (item.getProposedSummary() != null && !item.getProposedSummary().isBlank()) {
                        sb.append(" — ").append(item.getProposedSummary());
                    }
                } else if ("REMOVE".equals(item.getBadge())) {
                    if (item.getPreviousSummary() != null && !item.getPreviousSummary().isBlank()) {
                        sb.append(" — ").append(item.getPreviousSummary());
                    }
                } else {
                    if (item.getProposedSummary() != null && !item.getProposedSummary().isBlank()) {
                        sb.append(" — ").append(item.getProposedSummary());
                    }
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append(isVn
                ? "_Chưa có thay đổi nào được lưu vào lịch tập. Nhấn **Xác nhận thay đổi** (hoặc nhắn \"Xác nhận\") để áp dụng, nhắn điều chỉnh thêm, hoặc **Hủy**._"
                : "_No changes have been saved yet. Click **Confirm Changes** (or reply \"Confirm\") to apply, request adjustments, or **Cancel**._");
        return sb.toString().trim();
    }

    private String formatConfirmedSuccessMarkdown(
            WorkoutPlanChangePreviewResponse confirmed,
            WorkoutPlanAtomicMutationExecutor.ExecutionVerificationResult verified,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(isVn ? "✅ **Đã áp dụng thay đổi vào lịch tập!**\n\n" : "✅ **Workout Plan Updated & Verified!**\n\n");
        if (confirmed.getExplanation() != null && !confirmed.getExplanation().isBlank()) {
            sb.append(confirmed.getExplanation()).append("\n\n");
        }
        for (WorkoutPlanPreviewDayDiff dayDiff : confirmed.getAffectedDays()) {
            sb.append("**").append(dayDiff.getDayTitle()).append("**\n");
            for (WorkoutPlanPreviewItem item : dayDiff.getChanges()) {
                sb.append("- ").append(item.getBadge()).append(": ").append(item.getExerciseName());
                String summary = "REMOVE".equals(item.getBadge())
                        ? item.getPreviousSummary()
                        : item.getProposedSummary();
                if (summary != null && !summary.isBlank()) {
                    sb.append(" — ").append(summary);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildPreviewMetadataJson(WorkoutPlanChangePreviewResponse previewDto, String toolName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_PREVIEW);
        meta.put("toolName", toolName);
        meta.put("readOnly", false);
        meta.put("mutated", false);
        meta.put("previewId", previewDto.getPreviewId());
        meta.put("status", previewDto.getStatus().name());
        if (previewDto.getSupersedesPreviewId() != null) {
            meta.put("supersedesPreviewId", previewDto.getSupersedesPreviewId());
        }
        meta.put("preview", previewDto);
        return safeToJson(meta);
    }

    private String buildConfirmedMetadataJson(
            WorkoutPlanChangePreviewResponse confirmedDto,
            List<WorkoutPlanChangeOperation> operations
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_UPDATED);
        meta.put("readOnly", false);
        meta.put("mutated", true);
        meta.put("verified", true);
        meta.put("previewId", confirmedDto.getPreviewId());
        meta.put("status", WorkoutPlanPreviewStatus.CONFIRMED.name());
        meta.put("workoutPlanId", confirmedDto.getWorkoutPlanId());
        if (!operations.isEmpty()) {
            WorkoutPlanChangeOperation first = operations.get(0);
            if (first.getDestinationDayOfWeek() != null) {
                meta.put("dayOfWeek", first.getDestinationDayOfWeek().name());
            } else if (first.getSourceDayOfWeek() != null) {
                meta.put("dayOfWeek", first.getSourceDayOfWeek().name());
            }
            if (first.getSourceDayOfWeek() != null && first.getOperationType() == WorkoutPlanOperationType.MOVE_EXERCISE) {
                meta.put("sourceDayOfWeek", first.getSourceDayOfWeek().name());
                meta.put("movedExercise", first.getExerciseName());
            }
        }
        List<String> addedNames = operations.stream()
                .filter(op -> op.getOperationType() == WorkoutPlanOperationType.ADD_EXERCISE)
                .map(WorkoutPlanChangeOperation::getExerciseName)
                .filter(Objects::nonNull)
                .toList();
        if (!addedNames.isEmpty()) {
            meta.put("addedExercises", addedNames);
        }
        operations.stream()
                .filter(op -> op.getOperationType() == WorkoutPlanOperationType.REMOVE_EXERCISE)
                .map(WorkoutPlanChangeOperation::getExerciseName)
                .findFirst()
                .ifPresent(name -> meta.put("removedExercise", name));
        meta.put("preview", confirmedDto);
        return safeToJson(meta);
    }

    private String buildCancelledOrFailedMetadata(AiWorkoutPlanChangePreview preview, String code) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_CANCELLED);
        meta.put("previewId", preview.getPreviewId());
        meta.put("status", preview.getStatus().name());
        meta.put("code", code);
        meta.put("mutated", false);
        return safeToJson(meta);
    }

    private AiMessageResponse saveSystemAssistantMessage(
            AiConversation conversation,
            String content,
            String intent,
            String metadataJson
    ) {
        AiMessage msg = AiMessage.builder()
                .conversation(conversation)
                .role("assistant")
                .content(content)
                .intent(intent)
                .metadata(metadataJson)
                .build();
        AiMessage saved = aiMessageRepository.save(msg);
        conversation.setUpdatedAt(ZonedDateTime.now());
        aiConversationRepository.save(conversation);
        return AiMessageResponse.builder()
                .id(saved.getId())
                .conversationId(conversation.getId())
                .role(saved.getRole())
                .content(saved.getContent())
                .intent(saved.getIntent())
                .metadata(saved.getMetadata())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private void syncAssistantMessageStatus(AiWorkoutPlanChangePreview preview, boolean stale) {
        if (preview.getAssistantMessageId() == null) {
            return;
        }
        aiMessageRepository.findById(preview.getAssistantMessageId()).ifPresent(msg -> {
            if (msg.getMetadata() == null || msg.getMetadata().isBlank()) {
                return;
            }
            try {
                JsonNode root = objectMapper.readTree(msg.getMetadata());
                if (root instanceof ObjectNode rootObj) {
                    rootObj.put("status", preview.getStatus().name());
                    JsonNode pNode = rootObj.get("preview");
                    if (pNode instanceof ObjectNode previewObj) {
                        previewObj.put("status", preview.getStatus().name());
                        if (preview.getResolvedAt() != null) {
                            previewObj.put("resolvedAt", preview.getResolvedAt().toString());
                        }
                        if (preview.getFailureReason() != null) {
                            previewObj.put("failureReason", preview.getFailureReason());
                        }
                        previewObj.put("stale", stale);
                    }
                    msg.setMetadata(objectMapper.writeValueAsString(rootObj));
                    aiMessageRepository.save(msg);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void markExpiredInternal(AiWorkoutPlanChangePreview preview) {
        preview.setStatus(WorkoutPlanPreviewStatus.EXPIRED);
        preview.setResolvedAt(ZonedDateTime.now());
        if (preview.getFailureReason() == null) {
            preview.setFailureReason("Preview expired before confirmation.");
        }
        previewRepository.save(preview);
        syncAssistantMessageStatus(preview, false);
    }

    private boolean isExpired(AiWorkoutPlanChangePreview preview) {
        return preview.getExpiresAt() != null && preview.getExpiresAt().isBefore(ZonedDateTime.now());
    }

    private WorkoutPlanChangePreviewResponse toPreviewResponse(
            AiWorkoutPlanChangePreview entity,
            String planName,
            String supersedesPreviewId,
            boolean stale
    ) {
        List<WorkoutPlanChangeOperation> ops = deserializeOperations(entity.getOperationsJson());
        List<WorkoutPlanPreviewDayDiff> affectedDays = new ArrayList<>();
        String explanation = entity.getSummary();
        String storedSupersedes = supersedesPreviewId;

        if (entity.getPreviewPayloadJson() != null && !entity.getPreviewPayloadJson().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(entity.getPreviewPayloadJson());
                if (node.hasNonNull("explanation")) {
                    explanation = node.get("explanation").asText();
                }
                if (storedSupersedes == null && node.hasNonNull("supersedesPreviewId")) {
                    storedSupersedes = node.get("supersedesPreviewId").asText();
                }
                if (node.has("affectedDays") && node.get("affectedDays").isArray()) {
                    affectedDays = objectMapper.convertValue(
                            node.get("affectedDays"),
                            new TypeReference<List<WorkoutPlanPreviewDayDiff>>() {}
                    );
                }
            } catch (Exception ignored) {
            }
        }

        return WorkoutPlanChangePreviewResponse.builder()
                .previewId(entity.getPreviewId())
                .conversationId(entity.getConversationId())
                .workoutPlanId(entity.getWorkoutPlanId())
                .workoutPlanName(planName)
                .status(entity.getStatus())
                .revision(entity.getRevision())
                .explanation(explanation)
                .supersedesPreviewId(storedSupersedes)
                .operations(ops)
                .affectedDays(affectedDays)
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .resolvedAt(entity.getResolvedAt())
                .failureReason(entity.getFailureReason())
                .stale(stale || (entity.getFailureReason() != null && entity.getFailureReason().startsWith("STALE_PREVIEW")))
                .build();
    }

    // ==================== OPERATION BUILDERS & PRESCRIPTION PARSING ====================

    private WorkoutPlanChangeOperation buildAddExerciseOp(
            WorkoutPlanDay destDay,
            Exercise ex,
            PrescriptionInput parsed,
            boolean isVn
    ) {
        ExercisePrescriptionValues proposed = buildDefaultPrescription(ex, parsed);
        WorkoutPlanChangeOperation op = WorkoutPlanChangeOperation.builder()
                .operationType(WorkoutPlanOperationType.ADD_EXERCISE)
                .destinationDayId(destDay.getId())
                .destinationDayOfWeek(destDay.getDayOfWeek())
                .destinationDayLabel(formatDayTitle(destDay, isVn))
                .exerciseId(ex.getId())
                .exerciseName(ex.getName())
                .proposedValues(proposed)
                .build();
        op.setSummary(buildOperationSummary(op, isVn));
        return op;
    }

    private WorkoutPlanChangeOperation buildRemoveExerciseOp(
            WorkoutPlanDay sourceDay,
            WorkoutPlanExercise sourceEx,
            boolean isVn
    ) {
        WorkoutPlanChangeOperation op = WorkoutPlanChangeOperation.builder()
                .operationType(WorkoutPlanOperationType.REMOVE_EXERCISE)
                .sourceDayId(sourceDay.getId())
                .sourceDayOfWeek(sourceDay.getDayOfWeek())
                .sourceDayLabel(formatDayTitle(sourceDay, isVn))
                .workoutPlanExerciseId(sourceEx.getId())
                .exerciseId(sourceEx.getExercise().getId())
                .exerciseName(sourceEx.getExercise().getName())
                .previousValues(toPrescriptionValues(sourceEx))
                .build();
        op.setSummary(buildOperationSummary(op, isVn));
        return op;
    }

    private WorkoutPlanChangeOperation buildUpdateExerciseOp(
            WorkoutPlanDay day,
            WorkoutPlanExercise existing,
            PrescriptionInput override,
            boolean isVn
    ) {
        ExercisePrescriptionValues prev = toPrescriptionValues(existing);
        ExercisePrescriptionValues proposed = copyValues(prev);
        applyPrescriptionOverride(proposed, override);

        WorkoutPlanChangeOperation op = WorkoutPlanChangeOperation.builder()
                .operationType(WorkoutPlanOperationType.UPDATE_EXERCISE)
                .sourceDayId(day.getId())
                .sourceDayOfWeek(day.getDayOfWeek())
                .sourceDayLabel(formatDayTitle(day, isVn))
                .destinationDayId(day.getId())
                .destinationDayOfWeek(day.getDayOfWeek())
                .destinationDayLabel(formatDayTitle(day, isVn))
                .workoutPlanExerciseId(existing.getId())
                .exerciseId(existing.getExercise().getId())
                .exerciseName(existing.getExercise().getName())
                .previousValues(prev)
                .proposedValues(proposed)
                .build();
        op.setSummary(buildOperationSummary(op, isVn));
        return op;
    }

    private String buildOperationSummary(WorkoutPlanChangeOperation op, boolean isVn) {
        return switch (op.getOperationType()) {
            case MOVE_EXERCISE -> {
                String src = formatDayLabel(op.getSourceDayOfWeek(), isVn);
                String dest = formatDayLabel(op.getDestinationDayOfWeek(), isVn);
                String prevSum = formatPrescriptionSummary(op.getPreviousValues(), isVn);
                String propSum = formatPrescriptionSummary(op.getProposedValues(), isVn);
                boolean changed = !Objects.equals(prevSum, propSum);
                if (isVn) {
                    yield "Chuyển **" + op.getExerciseName() + "** từ " + src + " sang " + dest
                            + (changed ? " và cập nhật thành " + propSum : "") + ".";
                }
                yield "Move **" + op.getExerciseName() + "** from " + src + " to " + dest
                        + (changed ? " and update to " + propSum : "") + ".";
            }
            case ADD_EXERCISE -> {
                String dest = formatDayLabel(op.getDestinationDayOfWeek(), isVn);
                String propSum = formatPrescriptionSummary(op.getProposedValues(), isVn);
                yield isVn
                        ? "Thêm **" + op.getExerciseName() + "** (" + propSum + ") vào " + dest + "."
                        : "Add **" + op.getExerciseName() + "** (" + propSum + ") to " + dest + ".";
            }
            case REMOVE_EXERCISE -> {
                String src = formatDayLabel(op.getSourceDayOfWeek(), isVn);
                yield isVn
                        ? "Xóa **" + op.getExerciseName() + "** khỏi " + src + "."
                        : "Remove **" + op.getExerciseName() + "** from " + src + ".";
            }
            case UPDATE_EXERCISE -> {
                String day = formatDayLabel(op.getDestinationDayOfWeek(), isVn);
                String propSum = formatPrescriptionSummary(op.getProposedValues(), isVn);
                yield isVn
                        ? "Cập nhật **" + op.getExerciseName() + "** trong " + day + " thành " + propSum + "."
                        : "Update **" + op.getExerciseName() + "** on " + day + " to " + propSum + ".";
            }
            case REORDER_EXERCISES -> {
                String day = formatDayLabel(op.getDestinationDayOfWeek(), isVn);
                yield isVn
                        ? "Sắp xếp lại thứ tự bài tập trong " + day + "."
                        : "Reorder exercises on " + day + ".";
            }
            case UPDATE_DAY -> {
                String day = formatDayLabel(op.getDestinationDayOfWeek(), isVn);
                String nextName = op.getProposedValues() != null && op.getProposedValues().getDayName() != null
                        ? op.getProposedValues().getDayName() : "";
                yield isVn
                        ? "Cập nhật buổi tập " + day + " thành **" + nextName + "**."
                        : "Update " + day + "'s workout focus to **" + nextName + "**.";
            }
        };
    }

    private String buildBatchExplanation(List<WorkoutPlanChangeOperation> ops, boolean isVn) {
        if (ops.size() == 1) {
            return ops.get(0).getSummary();
        }
        return ops.stream()
                .map(WorkoutPlanChangeOperation::getSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    public static PrescriptionInput parseExercisePrescription(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new PrescriptionInput(null, null, null, null);
        }
        Integer sets = parseIntMatch(SETS_PATTERN, prompt, 1, 20);
        if (sets == null) {
            sets = parseIntMatch(SETS_OF_PATTERN, prompt, 1, 20);
        }
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
        return new PrescriptionInput(sets, reps, durationSeconds, restSeconds);
    }

    private static void applyPrescriptionOverride(ExercisePrescriptionValues target, PrescriptionInput override) {
        if (override == null) {
            return;
        }
        if (override.sets() != null) {
            target.setSets(override.sets());
        }
        if (override.reps() != null) {
            target.setReps(override.reps());
            target.setDurationSeconds(null);
        } else if (override.durationSeconds() != null) {
            target.setDurationSeconds(override.durationSeconds());
            target.setReps(null);
        }
        if (override.restSeconds() != null) {
            target.setRestSeconds(override.restSeconds());
        }
    }

    private boolean wouldChangePrescription(WorkoutPlanExercise existing, PrescriptionInput override) {
        if (override == null || !override.hasAny()) {
            return false;
        }
        if (override.sets() != null && !Objects.equals(override.sets(), existing.getSets())) {
            return true;
        }
        if (override.reps() != null && !Objects.equals(override.reps(), existing.getReps())) {
            return true;
        }
        if (override.durationSeconds() != null && !Objects.equals(override.durationSeconds(), existing.getDurationSeconds())) {
            return true;
        }
        return override.restSeconds() != null && !Objects.equals(override.restSeconds(), existing.getRestSeconds());
    }

    private ExercisePrescriptionValues buildDefaultPrescription(Exercise ex, PrescriptionInput parsed) {
        int sets = parsed.sets() != null ? parsed.sets()
                : (ex.getRecommendedSets() != null && ex.getRecommendedSets() > 0 ? ex.getRecommendedSets() : 3);
        int rest = parsed.restSeconds() != null ? parsed.restSeconds()
                : (ex.getRecommendedRestSeconds() != null && ex.getRecommendedRestSeconds() >= 0 ? ex.getRecommendedRestSeconds() : 90);
        Integer reps = null;
        Integer durationSeconds = null;
        if (parsed.reps() != null) {
            reps = parsed.reps();
        } else if (parsed.durationSeconds() != null) {
            durationSeconds = parsed.durationSeconds();
        } else if (isStaticHoldExercise(ex)) {
            durationSeconds = 20;
        } else {
            reps = parseRecommendedReps(ex.getRecommendedReps()).orElse(10);
        }
        return ExercisePrescriptionValues.builder()
                .sets(sets)
                .reps(reps)
                .durationSeconds(durationSeconds)
                .restSeconds(rest)
                .notes("Added via AI Assistant")
                .build();
    }

    private Optional<Integer> parseRecommendedReps(String recommendedReps) {
        if (recommendedReps == null || recommendedReps.isBlank()) {
            return Optional.empty();
        }
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(recommendedReps);
        if (m.find()) {
            return Optional.ofNullable(parseIntValue(m.group(1), 1, 200));
        }
        return Optional.empty();
    }

    private List<WorkoutPlanExercise> computeReorderedList(
            List<WorkoutPlanExercise> current,
            String prompt,
            String effectiveQuery
    ) {
        List<WorkoutPlanExercise> result = new ArrayList<>(current);
        String lower = prompt.toLowerCase(Locale.US);

        if (lower.contains("reverse")) {
            Collections.reverse(result);
            return result;
        }

        // Find all matching exercises referenced in prompt with their position in prompt
        record PromptHit(WorkoutPlanExercise exercise, int promptStart) {}
        List<PromptHit> hits = new ArrayList<>();
        List<String> promptTokens = tokenizeNormalized(prompt);
        for (WorkoutPlanExercise pe : current) {
            if (pe.getExercise() == null || pe.getExercise().getName() == null) continue;
            List<String> exTokens = tokenizeNormalized(pe.getExercise().getName());
            int idx = containsSublist(promptTokens, exTokens);
            if (idx >= 0) {
                hits.add(new PromptHit(pe, idx));
            }
        }
        hits.sort(Comparator.comparingInt(PromptHit::promptStart));

        if (hits.size() >= 2 && (lower.contains("swap") || lower.contains("đổi chỗ"))) {
            int i1 = result.indexOf(hits.get(0).exercise());
            int i2 = result.indexOf(hits.get(1).exercise());
            if (i1 >= 0 && i2 >= 0) {
                Collections.swap(result, i1, i2);
                return result;
            }
        }

        if (hits.size() >= 2 && (lower.contains("before") || lower.contains("trước"))) {
            WorkoutPlanExercise moving = hits.get(0).exercise();
            WorkoutPlanExercise anchor = hits.get(1).exercise();
            result.remove(moving);
            int anchorIdx = result.indexOf(anchor);
            result.add(Math.max(0, anchorIdx), moving);
            return result;
        }

        if (hits.size() >= 2 && (lower.contains("after") || lower.contains("sau"))) {
            WorkoutPlanExercise moving = hits.get(0).exercise();
            WorkoutPlanExercise anchor = hits.get(1).exercise();
            result.remove(moving);
            int anchorIdx = result.indexOf(anchor);
            result.add(Math.min(result.size(), anchorIdx + 1), moving);
            return result;
        }

        WorkoutPlanExercise primary = !hits.isEmpty()
                ? hits.get(0).exercise()
                : findBestMatchingPlanExercise(current, effectiveQuery).orElse(null);

        if (primary != null) {
            if (lower.contains("first") || lower.contains("top") || lower.contains("đầu tiên") || lower.contains("lên đầu")) {
                result.remove(primary);
                result.add(0, primary);
                return result;
            }
            if (lower.contains("last") || lower.contains("bottom") || lower.contains("end") || lower.contains("cuối cùng")) {
                result.remove(primary);
                result.add(primary);
                return result;
            }
            Matcher posMatcher = POSITION_PATTERN.matcher(prompt);
            if (posMatcher.find()) {
                Integer pos = parseIntValue(posMatcher.group(1), 1, result.size());
                if (pos != null) {
                    result.remove(primary);
                    result.add(Math.min(result.size(), pos - 1), primary);
                    return result;
                }
            }
        }
        return result;
    }

    private List<Integer> findMatchingOperationIndices(List<WorkoutPlanChangeOperation> ops, String prompt) {
        List<String> queryTokens = tokenizeNormalized(prompt);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        List<Integer> matched = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            String exName = ops.get(i).getExerciseName();
            if (exName != null && scoreExerciseMatch(exName, queryTokens) > Integer.MIN_VALUE) {
                matched.add(i);
            }
        }
        return matched;
    }

    // ==================== SHARED QUERY & FORMATTING UTILITIES ====================

    private Optional<WorkoutPlan> resolveOwnedPersonalPlan(Long userId) {
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

    private List<Exercise> loadPublishedCatalog() {
        return exerciseRepository.findAll().stream()
                .filter(e -> e.isActive() && e.getStatus() != ExerciseStatus.DRAFT)
                .toList();
    }

    private WorkoutPlanDay findDay(List<WorkoutPlanDay> days, WorkoutDayOfWeek targetDow) {
        if (days == null || targetDow == null) return null;
        return days.stream().filter(d -> d.getDayOfWeek() == targetDow).findFirst().orElse(null);
    }

    private WorkoutDayOfWeek resolveTargetDay(
            WorkoutDayOfWeek explicitFromPrompt,
            WorkoutDayOfWeek explicitDay,
            WorkoutDayOfWeek selectedDayOfWeek
    ) {
        if (explicitFromPrompt != null) return explicitFromPrompt;
        if (explicitDay != null) return explicitDay;
        return selectedDayOfWeek;
    }

    private ExercisePrescriptionValues toPrescriptionValues(WorkoutPlanExercise pe) {
        return ExercisePrescriptionValues.builder()
                .sets(pe.getSets())
                .reps(pe.getReps())
                .durationSeconds(pe.getDurationSeconds())
                .restSeconds(pe.getRestSeconds())
                .displayOrder(pe.getDisplayOrder())
                .notes(pe.getNotes())
                .build();
    }

    private ExercisePrescriptionValues copyValues(ExercisePrescriptionValues src) {
        if (src == null) return new ExercisePrescriptionValues();
        return ExercisePrescriptionValues.builder()
                .sets(src.getSets())
                .reps(src.getReps())
                .durationSeconds(src.getDurationSeconds())
                .restSeconds(src.getRestSeconds())
                .displayOrder(src.getDisplayOrder())
                .notes(src.getNotes())
                .dayName(src.getDayName())
                .sessionType(src.getSessionType())
                .restDay(src.getRestDay())
                .orderedAssignmentIds(src.getOrderedAssignmentIds() != null ? new ArrayList<>(src.getOrderedAssignmentIds()) : null)
                .orderedExerciseNames(src.getOrderedExerciseNames() != null ? new ArrayList<>(src.getOrderedExerciseNames()) : null)
                .build();
    }

    private WorkoutPlanChangeOperation deepCopyOperation(WorkoutPlanChangeOperation op) {
        return WorkoutPlanChangeOperation.builder()
                .operationType(op.getOperationType())
                .sourceDayId(op.getSourceDayId())
                .sourceDayOfWeek(op.getSourceDayOfWeek())
                .sourceDayLabel(op.getSourceDayLabel())
                .destinationDayId(op.getDestinationDayId())
                .destinationDayOfWeek(op.getDestinationDayOfWeek())
                .destinationDayLabel(op.getDestinationDayLabel())
                .workoutPlanExerciseId(op.getWorkoutPlanExerciseId())
                .exerciseId(op.getExerciseId())
                .exerciseName(op.getExerciseName())
                .previousValues(op.getPreviousValues() != null ? copyValues(op.getPreviousValues()) : null)
                .proposedValues(op.getProposedValues() != null ? copyValues(op.getProposedValues()) : null)
                .summary(op.getSummary())
                .build();
    }

    public static String formatPrescriptionSummary(ExercisePrescriptionValues vals, boolean isVn) {
        if (vals == null) return "";
        List<String> parts = new ArrayList<>();
        if (vals.getSets() != null) {
            parts.add(vals.getSets() + (isVn ? " hiệp" : " sets"));
        }
        if (vals.getReps() != null) {
            parts.add(vals.getReps() + (isVn ? " lần" : " reps"));
        }
        if (vals.getDurationSeconds() != null) {
            parts.add(vals.getDurationSeconds() + (isVn ? " giây giữ" : "s hold"));
        }
        String volume = String.join(" × ", parts);
        if (vals.getRestSeconds() != null) {
            volume += (volume.isEmpty() ? "" : " · ") + (isVn ? "Nghỉ " : "Rest ") + vals.getRestSeconds() + "s";
        }
        return volume;
    }

    private String formatDayFocus(ExercisePrescriptionValues vals) {
        if (Boolean.TRUE.equals(vals.getRestDay())) {
            return "Rest Day";
        }
        if (vals.getDayName() != null && !vals.getDayName().isBlank()) {
            return vals.getDayName();
        }
        return vals.getSessionType() != null ? vals.getSessionType().replace('_', ' ') : "Workout";
    }

    private String formatDayTitle(WorkoutPlanDay day, boolean isVn) {
        String dayLabel = formatDayLabel(day.getDayOfWeek(), isVn);
        String focus;
        if (day.isRestDay()) {
            focus = isVn ? "Ngày nghỉ (REST)" : "REST";
        } else if (day.getName() != null && !day.getName().isBlank()) {
            focus = day.getName().trim();
        } else if (day.getSessionType() != null) {
            focus = day.getSessionType().name().replace('_', ' ');
        } else {
            focus = isVn ? "Buổi tập" : "WORKOUT";
        }
        return dayLabel + " — " + focus;
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
        if (prompt == null || prompt.isBlank()) return true;
        List<String> queryTokens = tokenizeNormalized(prompt);
        List<String> candidateTokens = tokenizeNormalized(candidate.getName());
        if (containsSublist(queryTokens, candidateTokens) >= 0) return true;
        return isFollowUpReference(prompt);
    }

    private String resolveEffectiveExerciseQuery(String prompt, String explicitExerciseName, List<AiMessage> history) {
        if (explicitExerciseName != null && !explicitExerciseName.isBlank()) {
            return explicitExerciseName;
        }
        if (isFollowUpReference(prompt) && history != null) {
            String fromHistory = extractRecentExerciseNameFromHistory(exerciseRepository.findAll(), prompt, history);
            if (fromHistory != null) {
                return fromHistory;
            }
        }
        return prompt;
    }

    private Optional<Exercise> findBestMatchingExercise(List<Exercise> catalog, String query) {
        if (catalog == null || catalog.isEmpty() || query == null || query.isBlank()) return Optional.empty();
        List<String> queryTokens = tokenizeNormalized(query);
        if (queryTokens.isEmpty()) return Optional.empty();
        Exercise best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Exercise ex : catalog) {
            if (ex.getName() == null || ex.getName().isBlank()) continue;
            int score = scoreExerciseMatch(ex.getName(), queryTokens);
            if (score > bestScore) {
                bestScore = score;
                best = ex;
            }
        }
        return best != null ? Optional.of(best) : Optional.empty();
    }

    private List<Exercise> extractMultipleExercisesFromList(List<Exercise> catalog, String prompt) {
        if (catalog == null || catalog.isEmpty() || prompt == null || prompt.isBlank()) {
            return List.of();
        }
        if (!prompt.contains(",") && !prompt.toLowerCase(Locale.US).contains(" and ")
                && !prompt.toLowerCase(Locale.US).contains(" và ")) {
            return List.of();
        }
        String[] parts = prompt.split("(?i)(?:\\s*,\\s*|\\s+and\\s+|\\s+và\\s+)");
        if (parts.length < 2) {
            return List.of();
        }
        List<Exercise> matches = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (String part : parts) {
            findBestMatchingExercise(catalog, part.trim()).ifPresent(ex -> {
                if (seenIds.add(ex.getId())) {
                    matches.add(ex);
                }
            });
        }
        return matches.size() > 1 ? matches : List.of();
    }

    private Optional<WorkoutPlanExercise> findBestMatchingPlanExercise(List<WorkoutPlanExercise> dayExercises, String query) {
        if (dayExercises == null || dayExercises.isEmpty() || query == null || query.isBlank()) return Optional.empty();
        List<String> queryTokens = tokenizeNormalized(query);
        if (queryTokens.isEmpty()) return Optional.empty();
        WorkoutPlanExercise best = null;
        int bestScore = Integer.MIN_VALUE;
        for (WorkoutPlanExercise pe : dayExercises) {
            if (pe.getExercise() == null || pe.getExercise().getName() == null) continue;
            int score = scoreExerciseMatch(pe.getExercise().getName(), queryTokens);
            if (score > bestScore) {
                bestScore = score;
                best = pe;
            }
        }
        return best != null ? Optional.of(best) : Optional.empty();
    }

    private int scoreExerciseMatch(String exerciseName, List<String> queryTokens) {
        List<String> exTokens = tokenizeNormalized(exerciseName);
        if (exTokens.isEmpty()) return Integer.MIN_VALUE;
        int matchStart = containsSublist(queryTokens, exTokens);
        if (matchStart < 0) return Integer.MIN_VALUE;
        int afterIdx = matchStart + exTokens.size();
        if (afterIdx < queryTokens.size()) {
            String nextToken = queryTokens.get(afterIdx);
            if (MOVEMENT_VARIANT_MODIFIERS.contains(nextToken) && !exTokens.contains(nextToken)) {
                return Integer.MIN_VALUE;
            }
        }
        int exactBonus = queryTokens.size() == exTokens.size() ? 10_000 : 0;
        return exactBonus + (exTokens.size() * 1000) + exerciseName.length();
    }

    private static int containsSublist(List<String> haystack, List<String> needle) {
        if (needle.isEmpty() || haystack.size() < needle.size()) return -1;
        for (int i = 0; i <= haystack.size() - needle.size(); i++) {
            boolean allMatch = true;
            for (int j = 0; j < needle.size(); j++) {
                if (!haystack.get(i + j).equals(needle.get(j))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return i;
        }
        return -1;
    }

    private static List<String> tokenizeNormalized(String text) {
        if (text == null || text.isBlank()) return List.of();
        String cleaned = text.toLowerCase(Locale.US).replaceAll("[-_/(),.:;!?]+", " ").trim();
        if (cleaned.isEmpty()) return List.of();
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
        if (token.length() <= 3) return token;
        if (token.endsWith("ies") && token.length() > 4) return token.substring(0, token.length() - 3) + "y";
        if (token.endsWith("ches") || token.endsWith("shes") || token.endsWith("xes")
                || token.endsWith("sses") || token.endsWith("zes")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("s") && !token.endsWith("ss")) return token.substring(0, token.length() - 1);
        return token;
    }

    private boolean isFollowUpReference(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String lower = prompt.toLowerCase(Locale.US).trim();
        if (lower.contains("this exercise") || lower.contains("that exercise")
                || lower.contains("bài tập này") || lower.contains("bài này")
                || lower.matches(".*\\b(it|that|them)\\b.*")) {
            return true;
        }
        boolean hasWeekday = UserAiWorkoutToolService.extractWeekday(prompt) != null;
        boolean hasNoSpecificWords = tokenizeNormalized(prompt).stream()
                .allMatch(WorkoutPlanChangePreviewService::isFillerOrDayOrActionToken);
        return hasWeekday && hasNoSpecificWords;
    }

    private static boolean isFillerOrDayOrActionToken(String tok) {
        return Set.of(
                "no", "yes", "ok", "okay", "please", "add", "put", "remove", "delete", "move",
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
            if (prev == null) continue;
            if ("assistant".equalsIgnoreCase(prev.getRole()) && prev.getMetadata() != null) {
                try {
                    JsonNode node = objectMapper.readTree(prev.getMetadata());
                    if (node.hasNonNull("exerciseName")) return node.get("exerciseName").asText();
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
                if (matched.isPresent()) return matched.get().getName();
            }
        }
        return null;
    }

    private boolean isStaticHoldExercise(Exercise ex) {
        if (ex == null) return false;
        if (ex.getCategory() == ExerciseCategory.ISOMETRIC) return true;
        List<String> nameTokens = tokenizeNormalized(ex.getName());
        if (nameTokens.stream().anyMatch(DYNAMIC_MOVEMENT_WORDS::contains)) return false;
        if (ex.getCategory() == ExerciseCategory.SKILL) return true;
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

    private boolean isGenericCategoryExerciseRequest(String prompt, ExerciseCategory requestedCategory) {
        if (prompt == null || requestedCategory == null) return false;
        String lower = prompt.toLowerCase(Locale.US);
        return lower.contains("exercise") || lower.contains("bài tập");
    }

    private int parseRequestedGenericCount(String prompt, int fallback) {
        if (prompt == null) return fallback;
        Matcher m = GENERIC_COUNT_PATTERN.matcher(prompt);
        if (m.find()) {
            Integer val = parseIntValue(m.group(1), 1, 10);
            if (val != null) return val;
        }
        return fallback;
    }

    private String extractRequestedDayTitle(String prompt) {
        if (prompt == null) return null;
        Pattern p = Pattern.compile("(?i)(?:to|thành)\\s+([A-Za-z0-9À-ỹ\\s/+-]{2,60}?)(?:\\.|!|$)");
        Matcher m = p.matcher(prompt);
        if (m.find()) {
            String title = m.group(1).trim();
            if (!title.isBlank()) return title;
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

    private PlannedBatch errorBatch(String toolName, String message, WorkoutDayOfWeek day, String errorCode) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_UPDATED);
        meta.put("toolName", toolName);
        meta.put("readOnly", false);
        meta.put("mutated", false);
        meta.put("success", false);
        if (day != null) meta.put("dayOfWeek", day.name());
        if (errorCode != null) meta.put("error", errorCode);
        return new PlannedBatch(false, List.of(), null, message, safeToJson(meta));
    }

    private PlannedBatch clarificationBatch(
            String toolName,
            String pendingAction,
            String exerciseName,
            WorkoutDayOfWeek targetDay,
            String message
    ) {
        return clarificationBatch(toolName, pendingAction, exerciseName, null, false,
                targetDay, null, null, null, null, message);
    }

    private PlannedBatch clarificationBatch(
            String toolName,
            String pendingAction,
            String exerciseName,
            String exerciseQuery,
            boolean multiExerciseRequest,
            WorkoutDayOfWeek targetDay,
            WorkoutDayOfWeek recommendedDay,
            List<WorkoutDayOfWeek> candidateDays,
            List<Long> selectedExerciseIds,
            List<String> selectedExerciseNames,
            String message
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", METADATA_TYPE_CLARIFICATION);
        meta.put("toolName", toolName);
        meta.put("readOnly", false);
        meta.put("mutated", false);
        meta.put("pendingAction", pendingAction);
        if (exerciseName != null) meta.put("exerciseName", exerciseName);
        if (exerciseQuery != null) meta.put("exerciseQuery", exerciseQuery);
        if (multiExerciseRequest) meta.put("multiExerciseRequest", true);
        if (targetDay != null) meta.put("targetDayOfWeek", targetDay.name());
        if (recommendedDay != null) meta.put("recommendedDayOfWeek", recommendedDay.name());
        if (candidateDays != null && !candidateDays.isEmpty()) {
            meta.put("candidateDays", candidateDays.stream().map(WorkoutDayOfWeek::name).toList());
        }
        if (selectedExerciseIds != null && !selectedExerciseIds.isEmpty()) {
            meta.put("selectedExerciseIds", selectedExerciseIds);
        }
        if (selectedExerciseNames != null && !selectedExerciseNames.isEmpty()) {
            meta.put("selectedExerciseNames", selectedExerciseNames);
        }
        return new PlannedBatch(false, List.of(), null, message, safeToJson(meta));
    }

    private UserAiWorkoutToolService.WorkoutToolResult buildNonMutatingResult(
            String toolName, String message, WorkoutDayOfWeek day, String errorCode
    ) {
        PlannedBatch err = errorBatch(toolName, message, day, errorCode);
        return new UserAiWorkoutToolService.WorkoutToolResult(
                toolName, false, "WORKOUT_PLAN_UPDATE", false, err.nonPreviewReply(), err.nonPreviewMetadataJson());
    }

    private UserAiWorkoutToolService.WorkoutToolResult buildClarificationResult(
            String toolName, String pendingAction, String exerciseName, WorkoutDayOfWeek targetDay, String message
    ) {
        PlannedBatch clar = clarificationBatch(toolName, pendingAction, exerciseName, targetDay, message);
        return new UserAiWorkoutToolService.WorkoutToolResult(
                toolName, false, "WORKOUT_PLAN_UPDATE", false, clar.nonPreviewReply(), clar.nonPreviewMetadataJson());
    }

    private UserAiWorkoutToolService.WorkoutToolResult buildClarificationResult(
            String toolName,
            String pendingAction,
            String exerciseName,
            WorkoutDayOfWeek targetDay,
            String message,
            List<Long> selectedExerciseIds,
            List<Exercise> selectedExercises,
            WorkoutDayOfWeek recommendedDay,
            List<WorkoutDayOfWeek> candidateDays
    ) {
        List<String> selectedNames = selectedExercises != null
                ? selectedExercises.stream().map(Exercise::getName).toList() : null;
        PlannedBatch clar = clarificationBatch(
                toolName, pendingAction, exerciseName, null, true,
                targetDay, recommendedDay, candidateDays, selectedExerciseIds, selectedNames, message);
        return new UserAiWorkoutToolService.WorkoutToolResult(
                toolName, false, "WORKOUT_PLAN_UPDATE", false, clar.nonPreviewReply(), clar.nonPreviewMetadataJson());
    }

    private ClarificationContext extractLatestClarificationContext(List<AiMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage m = history.get(i);
            if (m == null || !"assistant".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            if (m.getMetadata() == null || m.getMetadata().isBlank()) {
                return null;
            }
            try {
                JsonNode root = objectMapper.readTree(m.getMetadata());
                if (METADATA_TYPE_CLARIFICATION.equals(root.path("type").asText())) {
                    String action = root.path("pendingAction").asText(null);
                    String exName = root.hasNonNull("exerciseName") ? root.get("exerciseName").asText() : null;
                    String exQuery = root.hasNonNull("exerciseQuery") ? root.get("exerciseQuery").asText() : null;
                    boolean multi = root.path("multiExerciseRequest").asBoolean(false);
                    WorkoutDayOfWeek recDay = null;
                    if (root.hasNonNull("recommendedDayOfWeek")) {
                        try {
                            recDay = WorkoutDayOfWeek.valueOf(root.get("recommendedDayOfWeek").asText().trim().toUpperCase(Locale.US));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    return new ClarificationContext(action, exName, exQuery, multi, recDay);
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        return null;
    }

    private boolean isTopicOrPluralExerciseRequest(
            String prompt,
            String explicitExerciseName,
            Optional<Exercise> matchedExercise,
            boolean matchedDirectlyInPrompt,
            ClarificationContext clarCtx
    ) {
        if (explicitExerciseName != null && !explicitExerciseName.isBlank()) {
            return false;
        }
        String lower = prompt != null ? prompt.toLowerCase(Locale.US) : "";
        boolean hasPluralNoun = lower.contains("exercises") || lower.contains("movements") || lower.contains("bài tập");
        if (hasPluralNoun) {
            return true;
        }
        if (clarCtx != null && clarCtx.multiExerciseRequest() && !matchedDirectlyInPrompt) {
            return true;
        }
        return matchedExercise.isEmpty() && extractSkillTopicFromText(prompt) != null;
    }

    private String resolveTopicQuery(
            String prompt,
            Optional<Exercise> matchedExercise,
            boolean matchedDirectlyInPrompt,
            ClarificationContext clarCtx
    ) {
        String fromPrompt = extractSkillTopicFromText(prompt);
        if (fromPrompt != null) {
            return fromPrompt;
        }
        if (clarCtx != null && clarCtx.exerciseQuery() != null && !clarCtx.exerciseQuery().isBlank() && !matchedDirectlyInPrompt) {
            return clarCtx.exerciseQuery();
        }
        if (matchedExercise.isPresent()) {
            return matchedExercise.get().getName();
        }
        if (clarCtx != null && clarCtx.exerciseQuery() != null && !clarCtx.exerciseQuery().isBlank()) {
            return clarCtx.exerciseQuery();
        }
        if (clarCtx != null && clarCtx.exerciseName() != null && !clarCtx.exerciseName().isBlank()) {
            return clarCtx.exerciseName();
        }
        return null;
    }

    private static String extractSkillTopicFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.US);
        List<String> knownSkills = List.of(
                "front lever", "back lever", "planche", "handstand push-up", "handstand pushup",
                "hspu", "handstand", "muscle-up", "muscle up", "l-sit", "human flag",
                "pistol squat", "dragon flag", "nordic", "iron cross"
        );
        for (String skill : knownSkills) {
            if (lower.contains(skill)) {
                return skill.replace('-', ' ');
            }
        }
        return null;
    }

    private List<Exercise> findTopicMatchingExercises(
            List<Exercise> catalog,
            String topicQuery,
            ExerciseCategory fallbackCategory
    ) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        if (topicQuery != null && !topicQuery.isBlank()) {
            List<String> topicTokens = tokenizeNormalized(topicQuery);
            if (!topicTokens.isEmpty()) {
                List<Exercise> direct = catalog.stream()
                        .filter(ex -> {
                            List<String> nameTokens = tokenizeNormalized(ex.getName());
                            if (containsSublist(nameTokens, topicTokens) >= 0) {
                                return true;
                            }
                            if (ex.getAliases() != null && !ex.getAliases().isBlank()) {
                                List<String> aliasTokens = tokenizeNormalized(ex.getAliases());
                                if (containsSublist(aliasTokens, topicTokens) >= 0) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .toList();
                if (!direct.isEmpty()) {
                    return direct;
                }
            }
        }
        if (fallbackCategory != null) {
            return catalog.stream()
                    .filter(e -> e.getCategory() == fallbackCategory)
                    .toList();
        }
        return List.of();
    }

    private List<Exercise> prioritizeComplementaryCandidates(
            List<Exercise> candidates,
            List<WorkoutPlanExercise> existingDayExercises
    ) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        boolean dayAlreadyHasStaticSkillHold = existingDayExercises.stream()
                .map(WorkoutPlanExercise::getExercise)
                .filter(Objects::nonNull)
                .anyMatch(this::isStaticHoldExercise);

        return candidates.stream()
                .sorted(Comparator
                        .comparingInt((Exercise ex) -> {
                            // Prefer dynamic pulling variations if day already has a static hold, or foundational variations first
                            boolean isStatic = isStaticHoldExercise(ex);
                            if (dayAlreadyHasStaticSkillHold) {
                                return isStatic ? 1 : 0;
                            }
                            return ex.getCategory() == ExerciseCategory.PULL ? 0 : 1;
                        })
                        .thenComparingInt(ex -> ex.getDifficulty() != null ? ex.getDifficulty().ordinal() : 9)
                        .thenComparing(Exercise::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static String resolveRequestedSubjectLabel(
            String singleExerciseName,
            String topicQuery,
            ExerciseCategory requestedCategory,
            ClarificationContext clarCtx
    ) {
        if (singleExerciseName != null && !singleExerciseName.isBlank()) {
            return singleExerciseName;
        }
        if (topicQuery != null && !topicQuery.isBlank()) {
            return Arrays.stream(topicQuery.split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                    .collect(Collectors.joining(" ")) + " exercises";
        }
        if (requestedCategory != null) {
            return requestedCategory.name().toLowerCase(Locale.US) + " exercises";
        }
        if (clarCtx != null && clarCtx.exerciseName() != null) {
            return clarCtx.exerciseName();
        }
        return null;
    }

    private static boolean isAffirmativeAcceptance(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String norm = prompt.trim().toLowerCase(Locale.US).replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        return Set.of(
                "yes", "yeah", "yep", "sure", "ok", "okay", "sounds good", "go ahead", "do it",
                "your recommendation", "the recommended day", "recommend", "the first one", "first option",
                "có", "đồng ý", "được", "ok bạn", "theo đề xuất của bạn"
        ).contains(norm);
    }

    private String buildAdditionExplanationWithContext(
            WorkoutPlanContextService.ActiveWorkoutPlanContext planCtx,
            WorkoutPlanDay destDay,
            boolean destIsRestDay,
            List<WorkoutPlanChangeOperation> ops,
            List<String> skippedDupNames,
            int existingDayExerciseCount,
            int existingDayTotalSets,
            boolean isVn
    ) {
        StringBuilder sb = new StringBuilder(buildBatchExplanation(ops, isVn));
        if (destIsRestDay) {
            List<String> activeAlternatives = planCtx.days().stream()
                    .filter(d -> !d.restDay())
                    .limit(2)
                    .map(d -> formatDayLabel(d.dayOfWeek(), isVn) + " (" + d.focusSummary() + ")")
                    .toList();
            String altHint = activeAlternatives.isEmpty() ? ""
                    : (isVn ? " Nếu muốn giữ ngày nghỉ này, bạn có thể chọn ngày tập khác như " + String.join(", ", activeAlternatives) + "."
                            : " If you'd prefer to keep this Rest Day for recovery, reply with another training day (e.g., "
                            + String.join(" or ", activeAlternatives) + ") or click Cancel.");
            if (isVn) {
                sb.append("\n\n⚠️ **Lưu ý Ngày nghỉ**: **").append(formatDayLabel(destDay.getDayOfWeek(), true))
                        .append("** hiện đang là **Ngày nghỉ (Rest Day)** trong lịch tập (không phải buổi tập kéo). Nếu xác nhận, ngày này sẽ chuyển thành buổi tập.").append(altHint);
            } else {
                sb.append("\n\n⚠️ **Rest Day Notice**: **").append(formatDayLabel(destDay.getDayOfWeek(), false))
                        .append("** is currently a **Rest Day** in your workout plan (not a Pull/training day). Confirming will convert it into an active training day.").append(altHint);
            }
        }
        if (skippedDupNames != null && !skippedDupNames.isEmpty()) {
            if (isVn) {
                sb.append("\n\n⚠️ **Tránh trùng lặp**: Đã bỏ qua ").append(String.join(", ", skippedDupNames))
                        .append(" vì đã có sẵn trong ").append(formatDayLabel(destDay.getDayOfWeek(), true)).append(".");
            } else {
                sb.append("\n\n⚠️ **Duplicate Check**: Skipped ").append(String.join(", ", skippedDupNames))
                        .append(" because it is already scheduled on ").append(formatDayLabel(destDay.getDayOfWeek(), false)).append(".");
            }
        }
        if (!destIsRestDay && (existingDayExerciseCount >= 4 || existingDayTotalSets >= 14)) {
            if (isVn) {
                sb.append("\n\n📊 **Khối lượng tập**: Ngày này hiện đã có ").append(existingDayExerciseCount)
                        .append(" bài tập (").append(existingDayTotalSets).append(" hiệp) — hãy theo dõi độ mỏi khi thêm bài mới.");
            } else {
                sb.append("\n\n📊 **Volume Check**: ").append(formatDayLabel(destDay.getDayOfWeek(), false))
                        .append(" already includes ").append(existingDayExerciseCount).append(" exercise(s) (")
                        .append(existingDayTotalSets).append(" sets); monitor recovery and straight-arm fatigue.");
            }
        }
        return sb.toString();
    }

    private List<WorkoutPlanChangeOperation> deserializeOperations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkoutPlanChangeOperation>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize preview operations JSON: {}", ex.getMessage());
            return List.of();
        }
    }

    private static Integer parseIntMatch(Pattern pattern, String text, int min, int max) {
        Matcher m = pattern.matcher(text);
        if (m.find()) return parseIntValue(m.group(1), min, max);
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

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
