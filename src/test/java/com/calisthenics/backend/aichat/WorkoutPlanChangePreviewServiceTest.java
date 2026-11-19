package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.preview.ExercisePrescriptionValues;
import com.calisthenics.backend.aichat.dto.preview.WorkoutPlanChangeOperation;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiWorkoutPlanChangePreview;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aichat.repository.AiWorkoutPlanChangePreviewRepository;
import com.calisthenics.backend.aichat.service.UserAiWorkoutToolService;
import com.calisthenics.backend.aichat.service.WorkoutPlanAtomicMutationExecutor;
import com.calisthenics.backend.aichat.service.WorkoutPlanChangePreviewService;
import com.calisthenics.backend.aichat.type.WorkoutPlanOperationType;
import com.calisthenics.backend.aichat.type.WorkoutPlanPreviewStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutPlanChangePreviewServiceTest {

    @Mock private AiWorkoutPlanChangePreviewRepository previewRepository;
    @Mock private AiConversationRepository aiConversationRepository;
    @Mock private AiMessageRepository aiMessageRepository;
    @Mock private WorkoutPlanRepository workoutPlanRepository;
    @Mock private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Mock private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private WorkoutPlanAtomicMutationExecutor atomicMutationExecutor;

    private ObjectMapper objectMapper;
    private WorkoutPlanChangePreviewService previewService;

    private User user;
    private AiConversation conversation;
    private WorkoutPlan plan;
    private WorkoutPlanDay monday;
    private WorkoutPlanDay tuesday;
    private Exercise frontLeverRaises;
    private Exercise pullUp;
    private WorkoutPlanExercise mondayFlr;
    private WorkoutPlanExercise tuesdayPullUp;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        previewService = new WorkoutPlanChangePreviewService(
                previewRepository,
                aiConversationRepository,
                aiMessageRepository,
                workoutPlanRepository,
                workoutPlanDayRepository,
                workoutPlanExerciseRepository,
                exerciseRepository,
                atomicMutationExecutor,
                null,
                objectMapper
        );

        user = User.builder().id(1L).email("athlete@test.com").fullName("Athlete").role(Role.ROLE_USER).build();
        conversation = AiConversation.builder().id(10L).user(user).title("Plan Edit").build();

        frontLeverRaises = Exercise.builder()
                .id(100L).name("Front Lever Raises").code("FRONT_LEVER_RAISES")
                .category(ExerciseCategory.PULL).difficulty(DifficultyLevel.INTERMEDIATE)
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
        pullUp = Exercise.builder()
                .id(101L).name("Pull-Up").code("PULL_UP")
                .category(ExerciseCategory.PULL).difficulty(DifficultyLevel.INTERMEDIATE)
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();

        plan = WorkoutPlan.builder()
                .id(50L).user(user).name("PPL Skill Focus").splitType(WorkoutSplitType.PPL).active(true).build();
        monday = WorkoutPlanDay.builder()
                .id(501L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.MONDAY)
                .sessionType(WorkoutSessionType.PUSH).displayOrder(1).restDay(false).build();
        tuesday = WorkoutPlanDay.builder()
                .id(502L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.TUESDAY)
                .sessionType(WorkoutSessionType.PULL).displayOrder(2).restDay(false).build();

        mondayFlr = WorkoutPlanExercise.builder()
                .id(901L).workoutPlanDay(monday).exercise(frontLeverRaises)
                .sets(3).reps(10).restSeconds(90).displayOrder(1).notes("Strict tempo").build();
        tuesdayPullUp = WorkoutPlanExercise.builder()
                .id(902L).workoutPlanDay(tuesday).exercise(pullUp)
                .sets(4).reps(8).restSeconds(120).displayOrder(1).build();
    }

    @Test
    @DisplayName("computePlanFingerprint is deterministic and detects prescription or order changes")
    void computePlanFingerprint_detectsMutations() {
        when(workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(501L)).thenReturn(List.of(mondayFlr));
        when(workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(502L)).thenReturn(List.of(tuesdayPullUp));

        String fp1 = previewService.computePlanFingerprint(plan, List.of(monday, tuesday));
        String fp2 = previewService.computePlanFingerprint(plan, List.of(tuesday, monday));
        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64);

        mondayFlr.setReps(12);
        String fpAfterRepEdit = previewService.computePlanFingerprint(plan, List.of(monday, tuesday));
        assertThat(fpAfterRepEdit).isNotEqualTo(fp1);
    }

    @Test
    @DisplayName("proposeChanges for move with implicit source day preserves unspecified attributes and never mutates workout DB")
    void proposeChanges_moveImplicitSourceAndSetOverride_preservesAttributes() {
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(plan));
        when(workoutPlanRepository.findWithDetailsById(50L)).thenReturn(Optional.of(plan));
        when(workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(50L)).thenReturn(List.of(monday, tuesday));
        when(workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(501L)).thenReturn(List.of(mondayFlr));
        when(workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(502L)).thenReturn(List.of(tuesdayPullUp));
        when(previewRepository.findByUserIdAndConversationIdAndStatus(1L, 10L, WorkoutPlanPreviewStatus.PENDING))
                .thenReturn(List.of());
        when(previewRepository.save(any(AiWorkoutPlanChangePreview.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAiWorkoutToolService.WorkoutToolResult result = previewService.proposeChanges(
                1L, 10L,
                UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS,
                null, WorkoutDayOfWeek.TUESDAY, null,
                "Move Front Lever Raises to Tuesday and make it 4 sets.",
                null, List.of(), false
        );

        assertThat(result.success()).isTrue();
        assertThat(result.intent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(result.reply()).contains("Workout Plan Preview");
        assertThat(result.reply()).contains("Monday — PUSH");
        assertThat(result.reply()).contains("REMOVE: Front Lever Raises — 3 sets × 10 reps · Rest 90s");
        assertThat(result.reply()).contains("Tuesday — PULL");
        assertThat(result.reply()).contains("ADD: Front Lever Raises — 4 sets × 10 reps · Rest 90s");

        // Verify saved structured operation uses actual DB day IDs and preserves reps=10, rest=90, notes="Strict tempo"
        ArgumentCaptor<AiWorkoutPlanChangePreview> captor = ArgumentCaptor.forClass(AiWorkoutPlanChangePreview.class);
        verify(previewRepository).save(captor.capture());
        AiWorkoutPlanChangePreview saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(WorkoutPlanPreviewStatus.PENDING);
        assertThat(saved.getOperationsJson()).contains("\"sourceDayId\":501");
        assertThat(saved.getOperationsJson()).contains("\"destinationDayId\":502");
        assertThat(saved.getOperationsJson()).contains("\"sets\":4");
        assertThat(saved.getOperationsJson()).contains("\"reps\":10");
        assertThat(saved.getOperationsJson()).contains("\"restSeconds\":90");
        assertThat(saved.getOperationsJson()).contains("Strict tempo");

        // Zero workout mutations during preview generation
        verifyNoInteractions(atomicMutationExecutor);
    }

    @Test
    @DisplayName("proposeChanges prevents duplicate add when no changes requested, and offers UPDATE when changes requested")
    void proposeChanges_duplicatePreventionAndConditionalUpdate() {
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.of(plan));
        when(workoutPlanRepository.findWithDetailsById(50L)).thenReturn(Optional.of(plan));
        when(workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(50L)).thenReturn(List.of(monday, tuesday));
        when(workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(501L)).thenReturn(List.of(mondayFlr));
        when(workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(502L)).thenReturn(List.of(tuesdayPullUp));
        when(exerciseRepository.findAll()).thenReturn(List.of(frontLeverRaises, pullUp));

        // Duplicate without requested change -> reject cleanly with no preview saved
        UserAiWorkoutToolService.WorkoutToolResult dupNoChange = previewService.proposeChanges(
                1L, 10L, UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                null, WorkoutDayOfWeek.MONDAY, null,
                "Add Front Lever Raises to Monday.", null, List.of(), false
        );
        assertThat(dupNoChange.success()).isFalse();
        assertThat(dupNoChange.reply()).contains("already scheduled on **Monday**");
        verify(previewRepository, never()).save(any());

        // Duplicate WITH requested change (5 sets) -> offer UPDATE_EXERCISE preview instead of duplicate ADD
        when(previewRepository.findByUserIdAndConversationIdAndStatus(1L, 10L, WorkoutPlanPreviewStatus.PENDING))
                .thenReturn(List.of());
        when(previewRepository.save(any(AiWorkoutPlanChangePreview.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAiWorkoutToolService.WorkoutToolResult dupWithUpdate = previewService.proposeChanges(
                1L, 10L, UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT,
                null, WorkoutDayOfWeek.MONDAY, null,
                "Add Front Lever Raises 5 sets to Monday.", null, List.of(), false
        );
        assertThat(dupWithUpdate.success()).isTrue();
        assertThat(dupWithUpdate.intent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(dupWithUpdate.metadataJson()).contains("UPDATE_EXERCISE");
        assertThat(dupWithUpdate.reply()).contains("UPDATE: Front Lever Raises");
        assertThat(dupWithUpdate.reply()).contains("5 sets");
    }

    @Test
    @DisplayName("revisePendingPreview asks clarification when multiple exercises match an ambiguous follow-up")
    void revisePendingPreview_ambiguousMultiExercise_asksClarification() throws Exception {
        List<WorkoutPlanChangeOperation> multiOps = List.of(
                WorkoutPlanChangeOperation.builder()
                        .operationType(WorkoutPlanOperationType.ADD_EXERCISE)
                        .destinationDayId(502L).destinationDayOfWeek(WorkoutDayOfWeek.TUESDAY)
                        .exerciseId(100L).exerciseName("Front Lever Raises")
                        .proposedValues(ExercisePrescriptionValues.builder().sets(3).reps(10).restSeconds(90).build())
                        .build(),
                WorkoutPlanChangeOperation.builder()
                        .operationType(WorkoutPlanOperationType.ADD_EXERCISE)
                        .destinationDayId(502L).destinationDayOfWeek(WorkoutDayOfWeek.TUESDAY)
                        .exerciseId(101L).exerciseName("Pull-Up")
                        .proposedValues(ExercisePrescriptionValues.builder().sets(3).reps(10).restSeconds(90).build())
                        .build()
        );
        AiWorkoutPlanChangePreview pending = AiWorkoutPlanChangePreview.builder()
                .previewId("prev-multi")
                .userId(1L)
                .conversationId(10L)
                .workoutPlanId(50L)
                .status(WorkoutPlanPreviewStatus.PENDING)
                .revision(1)
                .operationsJson(objectMapper.writeValueAsString(multiOps))
                .previewPayloadJson("{}")
                .expiresAt(ZonedDateTime.now().plusMinutes(20))
                .build();

        when(workoutPlanRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(plan));
        when(workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(50L)).thenReturn(List.of(monday, tuesday));

        UserAiWorkoutToolService.WorkoutToolResult res = previewService.revisePendingPreview(
                1L, 10L, pending, "Make it 5 sets instead.", false);

        assertThat(res.success()).isFalse();
        assertThat(res.metadataJson()).contains("WORKOUT_PLAN_CLARIFICATION");
        assertThat(res.reply()).contains("multiple exercises");
        assertThat(res.reply()).contains("Front Lever Raises");
        assertThat(res.reply()).contains("Pull-Up");
        verify(previewRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmPreview rejects expired previews, marks EXPIRED, and prevents execution")
    void confirmPreview_expiredPreview_marksExpiredAndRejects() {
        AiWorkoutPlanChangePreview expired = AiWorkoutPlanChangePreview.builder()
                .previewId("prev-exp")
                .userId(1L)
                .conversationId(10L)
                .workoutPlanId(50L)
                .status(WorkoutPlanPreviewStatus.PENDING)
                .operationsJson("[]")
                .previewPayloadJson("{}")
                .expiresAt(ZonedDateTime.now().minusMinutes(5))
                .build();

        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(conversation));
        when(previewRepository.findByPreviewIdAndUserIdAndConversationIdForUpdate("prev-exp", 1L, 10L))
                .thenReturn(Optional.of(expired));
        when(workoutPlanRepository.findById(50L)).thenReturn(Optional.of(plan));
        when(previewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkoutPlanChangePreviewService.PreviewActionOutcome outcome =
                previewService.confirmPreview(1L, 10L, "prev-exp", false, false);

        assertThat(outcome.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(outcome.response().getCode()).isEqualTo("PREVIEW_EXPIRED");
        assertThat(outcome.response().isMutated()).isFalse();
        assertThat(expired.getStatus()).isEqualTo(WorkoutPlanPreviewStatus.EXPIRED);
        verifyNoInteractions(atomicMutationExecutor);
    }

    @Test
    @DisplayName("confirmPreview blocks replay on already CONFIRMED preview and enforces user ownership")
    void confirmPreview_replayBlockedAndOwnershipEnforced() {
        AiWorkoutPlanChangePreview alreadyConfirmed = AiWorkoutPlanChangePreview.builder()
                .previewId("prev-done")
                .userId(1L)
                .conversationId(10L)
                .workoutPlanId(50L)
                .status(WorkoutPlanPreviewStatus.CONFIRMED)
                .operationsJson("[]")
                .previewPayloadJson("{}")
                .expiresAt(ZonedDateTime.now().plusMinutes(20))
                .build();

        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(conversation));
        when(previewRepository.findByPreviewIdAndUserIdAndConversationIdForUpdate("prev-done", 1L, 10L))
                .thenReturn(Optional.of(alreadyConfirmed));
        when(workoutPlanRepository.findById(50L)).thenReturn(Optional.of(plan));

        WorkoutPlanChangePreviewService.PreviewActionOutcome replay =
                previewService.confirmPreview(1L, 10L, "prev-done", false, false);

        assertThat(replay.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(replay.response().getCode()).isEqualTo("PREVIEW_ALREADY_RESOLVED");
        assertThat(replay.response().isMutated()).isFalse();
        verifyNoInteractions(atomicMutationExecutor);

        // Unauthorized user -> 404 NOT_FOUND
        when(aiConversationRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> previewService.confirmPreview(2L, 10L, "prev-done", false, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
