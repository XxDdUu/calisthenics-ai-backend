package com.calisthenics.backend.workout;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workout.dto.StartWorkoutSessionRequest;
import com.calisthenics.backend.workout.dto.UpdateWorkoutSessionSetRequest;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentWorkoutSessionServiceTest {

    @Mock
    private WorkoutSessionRepository sessionRepository;
    @Mock
    private WorkoutPlanDayRepository dayRepository;
    @Mock
    private WorkoutPlanExerciseRepository planExerciseRepository;
    @Mock
    private TodayWorkoutResolver todayWorkoutResolver;
    @Mock
    private PersonalRecordService personalRecordService;

    private PersistentWorkoutSessionService service;

    @BeforeEach
    void setUp() {
        service = new PersistentWorkoutSessionService(
                sessionRepository, dayRepository, planExerciseRepository, todayWorkoutResolver, personalRecordService);
    }

    @Test
    void startCreatesSnapshotAndPrescribedSetRows() {
        Long userId = 4L;
        Long planId = 12L;
        Long dayId = 30L;
        LocalDate date = LocalDate.of(2026, 10, 26);
        User user = User.builder().id(userId).email("client@example.test").fullName("Client").build();
        WorkoutPlan plan = WorkoutPlan.builder().id(planId).user(user).name("Strength").build();
        WorkoutPlanDay day = WorkoutPlanDay.builder()
                .id(dayId).workoutPlan(plan)
                .dayOfWeek(com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek.MONDAY)
                .sessionType(com.calisthenics.backend.workoutplan.type.WorkoutSessionType.PUSH)
                .build();
        Exercise exercise = Exercise.builder().id(8L).name("Push-up").code("PUSH_UP")
                .category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER)
                .instructions("Keep a straight body line").build();
        WorkoutPlanExercise assignment = WorkoutPlanExercise.builder()
                .id(90L).workoutPlanDay(day).exercise(exercise).sets(2).reps(10)
                .restSeconds(45).displayOrder(0).notes("Controlled tempo").build();
        when(dayRepository.findByIdAndWorkoutPlanId(dayId, planId)).thenReturn(Optional.of(day));
        when(todayWorkoutResolver.resolveCandidates(userId, date)).thenReturn(
                new TodayWorkoutResolver.ResolutionCandidates(
                        false, WorkoutSessionSource.PERSONAL,
                        List.of(new TodayWorkoutResolver.ResolvedWorkout(plan, day, null))));
        when(planExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(dayId))
                .thenReturn(List.of(assignment));
        when(sessionRepository.saveAndFlush(any(WorkoutSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.startOrResume(userId, new StartWorkoutSessionRequest(planId, dayId, date, null));

        assertEquals(WorkoutSessionStatus.IN_PROGRESS, result.status());
        assertEquals(WorkoutSessionSource.PERSONAL, result.source());
        assertEquals(planId, result.workoutPlanId());
        assertEquals(dayId, result.workoutPlanDayId());
        assertEquals(1, result.exercises().size());
        assertEquals("Push-up", result.exercises().getFirst().exerciseName());
        assertEquals(2, result.exercises().getFirst().sets().size());
        assertEquals(10, result.exercises().getFirst().targetReps());
        verify(sessionRepository).saveAndFlush(any(WorkoutSession.class));
    }

    @Test
    void startResumesExistingSessionWithoutCreatingAnother() {
        Long userId = 4L;
        Long planId = 12L;
        Long dayId = 30L;
        LocalDate date = LocalDate.of(2026, 10, 26);
        User user = User.builder().id(userId).email("client@example.test").fullName("Client").build();
        WorkoutPlan plan = WorkoutPlan.builder().id(planId).user(user).name("Strength").build();
        WorkoutPlanDay day = WorkoutPlanDay.builder().id(dayId).workoutPlan(plan)
                .dayOfWeek(com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek.MONDAY)
                .sessionType(com.calisthenics.backend.workoutplan.type.WorkoutSessionType.PUSH).build();
        WorkoutSession existing = WorkoutSession.builder()
                .id(77L).user(user).workoutPlan(plan).workoutPlanDay(day)
                .workoutDate(date).title("Existing").status(WorkoutSessionStatus.IN_PROGRESS)
                .startedAt(java.time.ZonedDateTime.now()).build();
        when(sessionRepository.findByUserIdAndWorkoutDateAndStatus(
                userId, date, WorkoutSessionStatus.IN_PROGRESS)).thenReturn(List.of(existing));

        var result = service.startOrResume(userId, new StartWorkoutSessionRequest(planId, dayId, date, null));

        assertEquals(77L, result.id());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void startRejectsPlanNotOwnedOrAssignedToUser() {
        Long userId = 4L;
        WorkoutPlan plan = WorkoutPlan.builder()
                .id(12L)
                .user(User.builder().id(8L).email("coach@example.test").fullName("Coach").build())
                .name("Assigned").build();
        WorkoutPlanDay day = WorkoutPlanDay.builder().id(30L).workoutPlan(plan)
                .dayOfWeek(com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek.MONDAY)
                .sessionType(com.calisthenics.backend.workoutplan.type.WorkoutSessionType.PUSH).build();
        when(dayRepository.findByIdAndWorkoutPlanId(30L, 12L)).thenReturn(Optional.of(day));
        when(todayWorkoutResolver.resolveCandidates(userId, LocalDate.of(2026, 10, 26))).thenReturn(
                new TodayWorkoutResolver.ResolutionCandidates(
                        false, WorkoutSessionSource.NONE, List.of()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.startOrResume(userId,
                        new StartWorkoutSessionRequest(12L, 30L, LocalDate.of(2026, 10, 26), null)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void startResumesExistingSessionEvenWhenCurrentPlanAndSourceChanged() {
        Long userId = 4L;
        LocalDate date = LocalDate.of(2026, 10, 26);
        WorkoutSession existing = WorkoutSession.builder()
                .id(91L)
                .user(User.builder().id(userId).email("client@example.test").fullName("Client").build())
                .title("Original coach workout")
                .workoutDate(date)
                .source(WorkoutSessionSource.COACH)
                .status(WorkoutSessionStatus.IN_PROGRESS)
                .startedAt(java.time.ZonedDateTime.now())
                .build();
        when(sessionRepository.findByUserIdAndWorkoutDateAndStatus(
                userId, date, WorkoutSessionStatus.IN_PROGRESS)).thenReturn(List.of(existing));

        var result = service.startOrResume(userId, new StartWorkoutSessionRequest(800L, 900L, date, null));

        assertEquals(91L, result.id());
        assertEquals(WorkoutSessionSource.COACH, result.source());
        verify(todayWorkoutResolver, never()).resolveCandidates(any(), any());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void coachSessionPersistsAssignmentAndSource() {
        Long userId = 4L;
        Long planId = 22L;
        Long dayId = 33L;
        Long assignmentId = 44L;
        LocalDate date = LocalDate.of(2026, 10, 26);
        User client = User.builder().id(userId).email("client@example.test").fullName("Client").build();
        User coach = User.builder().id(8L).email("coach@example.test").fullName("Coach").build();
        WorkoutPlan plan = WorkoutPlan.builder().id(planId).user(coach).name("Coach plan").build();
        WorkoutPlanDay day = WorkoutPlanDay.builder()
                .id(dayId).workoutPlan(plan)
                .dayOfWeek(com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek.MONDAY)
                .sessionType(com.calisthenics.backend.workoutplan.type.WorkoutSessionType.PUSH)
                .build();
        CoachClientRelationship relationship = CoachClientRelationship.builder()
                .id(55L).coach(coach).client(client).status(CoachClientRelationshipStatus.ACTIVE).build();
        WorkoutPlanAssignment coachAssignment = WorkoutPlanAssignment.builder()
                .id(assignmentId).relationship(relationship).workoutPlan(plan).coach(coach).client(client)
                .status(WorkoutPlanAssignmentStatus.ACTIVE).startDate(date.minusDays(1)).build();
        Exercise exercise = Exercise.builder().id(8L).name("Push-up").code("PUSH_UP")
                .category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER).build();
        WorkoutPlanExercise planned = WorkoutPlanExercise.builder()
                .workoutPlanDay(day).exercise(exercise).sets(1).reps(8).restSeconds(45).displayOrder(0).build();
        when(dayRepository.findByIdAndWorkoutPlanId(dayId, planId)).thenReturn(Optional.of(day));
        when(todayWorkoutResolver.resolveCandidates(userId, date)).thenReturn(
                new TodayWorkoutResolver.ResolutionCandidates(
                        true, WorkoutSessionSource.COACH,
                        List.of(new TodayWorkoutResolver.ResolvedWorkout(plan, day, coachAssignment))));
        when(planExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(dayId))
                .thenReturn(List.of(planned));
        when(sessionRepository.saveAndFlush(any(WorkoutSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.startOrResume(
                userId, new StartWorkoutSessionRequest(planId, dayId, date, assignmentId));

        assertEquals(WorkoutSessionSource.COACH, result.source());
        assertEquals(assignmentId, result.assignmentId());
        assertEquals("Coach plan - PUSH", result.title());
    }

    @Test
    void inProgressCoachSessionKeepsItsSnapshotWhenAssignmentChanges() {
        Long userId = 4L;
        User client = User.builder().id(userId).email("client@example.test").fullName("Client").build();
        User coach = User.builder().id(8L).email("coach@example.test").fullName("Coach").build();
        WorkoutPlan plan = WorkoutPlan.builder().id(12L).user(coach).name("Original coach plan").build();
        WorkoutPlanDay day = WorkoutPlanDay.builder().id(30L).workoutPlan(plan).build();
        CoachClientRelationship relationship = CoachClientRelationship.builder()
                .id(55L).coach(coach).client(client).status(CoachClientRelationshipStatus.ACTIVE).build();
        WorkoutPlanAssignment originalAssignment = WorkoutPlanAssignment.builder()
                .id(70L).relationship(relationship).workoutPlan(plan).coach(coach).client(client)
                .status(WorkoutPlanAssignmentStatus.ENDED).startDate(LocalDate.of(2026, 1, 1)).build();
        WorkoutSessionSet set = WorkoutSessionSet.builder()
                .id(20L).setNumber(1).completed(false).build();
        WorkoutSessionExercise exercise = WorkoutSessionExercise.builder()
                .id(10L).workoutSession(null).exerciseNameSnapshot("Original push-up")
                .orderIndex(0).targetSets(1).targetReps(8).restSeconds(45)
                .status(WorkoutSessionExerciseStatus.IN_PROGRESS).build();
        set.setWorkoutSessionExercise(exercise);
        exercise.getSets().add(set);
        WorkoutSession session = WorkoutSession.builder()
                .id(77L).user(client).workoutPlan(plan).workoutPlanDay(day)
                .workoutPlanAssignment(originalAssignment).source(WorkoutSessionSource.COACH)
                .title("Original workout").workoutDate(LocalDate.of(2026, 10, 26))
                .status(WorkoutSessionStatus.IN_PROGRESS).exercises(List.of(exercise))
                .startedAt(java.time.ZonedDateTime.now()).build();
        when(sessionRepository.findByIdAndUserId(77L, userId)).thenReturn(Optional.of(session));

        var result = service.updateSet(
                userId, 77L, 10L, 20L,
                new UpdateWorkoutSessionSetRequest(10, null, BigDecimal.ZERO, null, true, null, 0L));

        assertEquals(WorkoutSessionSource.COACH, result.source());
        assertEquals(70L, result.assignmentId());
        assertEquals("Original push-up", result.exercises().getFirst().exerciseName());
        assertEquals(8, result.exercises().getFirst().targetReps());
        assertEquals(10, result.exercises().getFirst().sets().getFirst().reps());
        verify(todayWorkoutResolver, never()).resolveCandidates(any(), any());
    }

    @Test
    void completedSessionCannotBeChanged() {
        User user = User.builder().id(4L).email("client@example.test").fullName("Client").build();
        WorkoutSession completed = WorkoutSession.builder()
                .id(77L).user(user).title("Finished").status(WorkoutSessionStatus.COMPLETED).build();
        when(sessionRepository.findByIdAndUserId(77L, 4L)).thenReturn(Optional.of(completed));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.updateSet(4L, 77L, 10L, 20L,
                        new UpdateWorkoutSessionSetRequest(8, null, null, null, true, null, 0L)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void getActiveExcludesFutureSessionsWhileGetUpcomingIncludesOnlyFutureSessions() {
        Long userId = 4L;
        LocalDate nov17 = LocalDate.of(2026, 11, 17);
        User user = User.builder().id(userId).email("client@example.test").fullName("Client").build();

        WorkoutSession nov17Unfinished = WorkoutSession.builder()
                .id(1L).user(user).title("Push Nov 17").workoutDate(LocalDate.of(2026, 11, 17))
                .status(WorkoutSessionStatus.IN_PROGRESS).build();
        WorkoutSession nov18FutureLegacyInProgress = WorkoutSession.builder()
                .id(2L).user(user).title("Pull Nov 18").workoutDate(LocalDate.of(2026, 11, 18))
                .status(WorkoutSessionStatus.IN_PROGRESS).build();
        WorkoutSession nov25Scheduled = WorkoutSession.builder()
                .id(3L).user(user).title("Legs Nov 25").workoutDate(LocalDate.of(2026, 11, 25))
                .status(WorkoutSessionStatus.SCHEDULED).build();

        when(sessionRepository.findUnfinishedSessionsAsOf(userId, nov17))
                .thenReturn(List.of(nov17Unfinished, nov18FutureLegacyInProgress));
        when(sessionRepository.findUpcomingSessionsAfter(userId, nov17))
                .thenReturn(List.of(nov17Unfinished, nov18FutureLegacyInProgress, nov25Scheduled));

        var unfinished = service.getActive(userId, nov17, "Asia/Ho_Chi_Minh");
        var upcoming = service.getUpcoming(userId, nov17, "Asia/Ho_Chi_Minh");

        assertEquals(1, unfinished.size());
        assertEquals(1L, unfinished.getFirst().id());
        assertEquals(WorkoutSessionClassification.UNFINISHED, unfinished.getFirst().classification());

        assertEquals(2, upcoming.size());
        assertEquals(List.of(2L, 3L), upcoming.stream().map(r -> r.id()).toList());
        assertTrue(upcoming.stream().allMatch(r -> r.classification() == WorkoutSessionClassification.UPCOMING));
    }

    @Test
    void startOrResumePromotesScheduledPlaceholderAndHydratesExercises() {
        Long userId = 4L;
        Long planId = 12L;
        Long dayId = 30L;
        LocalDate date = LocalDate.of(2026, 11, 17);
        User user = User.builder().id(userId).email("client@example.test").fullName("Client").build();
        WorkoutPlan plan = WorkoutPlan.builder().id(planId).user(user).name("Strength").build();
        WorkoutPlanDay day = WorkoutPlanDay.builder()
                .id(dayId).workoutPlan(plan)
                .dayOfWeek(com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek.TUESDAY)
                .sessionType(com.calisthenics.backend.workoutplan.type.WorkoutSessionType.PUSH)
                .build();
        Exercise pushUp = Exercise.builder().id(8L).name("Push-up").code("PUSH_UP")
                .category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER).build();
        WorkoutPlanExercise planned = WorkoutPlanExercise.builder()
                .id(90L).workoutPlanDay(day).exercise(pushUp).sets(3).reps(10)
                .restSeconds(60).displayOrder(1).build();
        WorkoutSession scheduled = WorkoutSession.builder()
                .id(55L).user(user).workoutPlan(plan).workoutPlanDay(day)
                .title("Push Day").workoutDate(date).status(WorkoutSessionStatus.SCHEDULED)
                .build();

        when(sessionRepository.findByUserIdAndWorkoutDateAndStatus(userId, date, WorkoutSessionStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(sessionRepository.findByUserIdAndWorkoutDateAndStatus(userId, date, WorkoutSessionStatus.SCHEDULED))
                .thenReturn(List.of(scheduled));
        when(planExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(dayId))
                .thenReturn(List.of(planned));
        when(sessionRepository.saveAndFlush(any(WorkoutSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var res = service.startOrResume(userId, new StartWorkoutSessionRequest(planId, dayId, date, null));

        assertEquals(WorkoutSessionStatus.IN_PROGRESS, res.status());
        assertNotNull(res.startedAt());
        assertEquals(1, res.exercises().size());
        assertEquals(3, res.exercises().getFirst().sets().size());
    }
}
