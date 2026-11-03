package com.calisthenics.backend.workout;

import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodayWorkoutResolverTest {

    private static final Long CLIENT_ID = 4L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 26);

    @Mock
    private CoachClientRelationshipRepository relationshipRepository;
    @Mock
    private WorkoutPlanAssignmentRepository assignmentRepository;
    @Mock
    private WorkoutPlanRepository planRepository;

    private TodayWorkoutResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TodayWorkoutResolver(
                relationshipRepository, assignmentRepository, planRepository, new WorkoutPlanMapper());
    }

    @Test
    void activeCoachAssignmentTakesPriorityOverPersonalPlan() {
        CoachClientRelationship activeRelationship = relationship(10L, CoachClientRelationshipStatus.ACTIVE);
        WorkoutPlan coachPlan = plan(20L, 8L, "Coach plan");
        WorkoutPlanAssignment assignment = assignment(30L, activeRelationship, coachPlan, DATE);
        when(relationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                CLIENT_ID, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.of(activeRelationship));
        when(assignmentRepository.findByClientIdOrderByCreatedAtDesc(CLIENT_ID)).thenReturn(List.of(assignment));
        lenient().when(planRepository.findByUserIdOrderByCreatedAtDesc(CLIENT_ID))
                .thenReturn(List.of(plan(40L, CLIENT_ID, "Personal plan")));

        var result = resolver.resolveCandidates(CLIENT_ID, DATE);

        assertTrue(result.activeCoach());
        assertEquals(WorkoutSessionSource.COACH, result.source());
        assertEquals(List.of(20L), result.candidates().stream().map(item -> item.plan().getId()).toList());
        verify(planRepository, never()).findByUserIdOrderByCreatedAtDesc(CLIENT_ID);
    }

    @Test
    void fallsBackToPersonalPlanWhenActiveCoachHasNoApplicableAssignment() {
        CoachClientRelationship activeRelationship = relationship(10L, CoachClientRelationshipStatus.ACTIVE);
        when(relationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                CLIENT_ID, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.of(activeRelationship));
        when(assignmentRepository.findByClientIdOrderByCreatedAtDesc(CLIENT_ID)).thenReturn(List.of());
        when(planRepository.findByUserIdOrderByCreatedAtDesc(CLIENT_ID))
                .thenReturn(List.of(plan(40L, CLIENT_ID, "Personal plan")));

        var result = resolver.resolveCandidates(CLIENT_ID, DATE);

        assertTrue(result.activeCoach());
        assertEquals(WorkoutSessionSource.PERSONAL, result.source());
        assertEquals(List.of(40L), result.candidates().stream().map(item -> item.plan().getId()).toList());
    }

    @Test
    void pendingCoachRequestDoesNotSuppressPersonalPlan() {
        when(relationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                CLIENT_ID, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(planRepository.findByUserIdOrderByCreatedAtDesc(CLIENT_ID))
                .thenReturn(List.of(plan(40L, CLIENT_ID, "Personal plan")));

        var result = resolver.resolveCandidates(CLIENT_ID, DATE);

        assertFalse(result.activeCoach());
        assertEquals(WorkoutSessionSource.PERSONAL, result.source());
        assertEquals(1, result.candidates().size());
    }

    @Test
    void noApplicableSourceProducesEmptyResolution() {
        when(relationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                CLIENT_ID, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(planRepository.findByUserIdOrderByCreatedAtDesc(CLIENT_ID)).thenReturn(List.of());

        var result = resolver.resolveCandidates(CLIENT_ID, DATE);

        assertEquals(WorkoutSessionSource.NONE, result.source());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void multipleApplicableCoachPlansRequireExplicitSelection() {
        CoachClientRelationship activeRelationship = relationship(10L, CoachClientRelationshipStatus.ACTIVE);
        WorkoutPlan first = plan(20L, 8L, "Coach plan one");
        WorkoutPlan second = plan(21L, 8L, "Coach plan two");
        WorkoutPlanAssignment newestFirstPlanAssignment =
                assignment(30L, activeRelationship, first, DATE);
        WorkoutPlanAssignment olderDuplicateAssignment =
                assignment(29L, activeRelationship, first, DATE);
        when(relationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                CLIENT_ID, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.of(activeRelationship));
        when(assignmentRepository.findByClientIdOrderByCreatedAtDesc(CLIENT_ID)).thenReturn(List.of(
                newestFirstPlanAssignment,
                olderDuplicateAssignment,
                assignment(31L, activeRelationship, second, DATE)));

        var result = resolver.resolve(CLIENT_ID, DATE, null);

        assertEquals(WorkoutSessionSource.COACH, result.source());
        assertTrue(result.selectionRequired());
        assertNull(result.selectedPlanId());
        assertEquals(2, result.candidates().size());
        assertEquals(30L, result.candidates().getFirst().assignmentId());
    }

    private CoachClientRelationship relationship(Long id, CoachClientRelationshipStatus status) {
        return CoachClientRelationship.builder().id(id).status(status).build();
    }

    private WorkoutPlan plan(Long id, Long ownerId, String name) {
        User owner = User.builder().id(ownerId).email("user" + ownerId + "@example.test").fullName(name).build();
        WorkoutPlan plan = WorkoutPlan.builder()
                .id(id).user(owner).name(name).splitType(WorkoutSplitType.FULL_BODY).build();
        plan.getDays().add(WorkoutPlanDay.builder()
                .id(id * 10)
                .workoutPlan(plan)
                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                .sessionType(WorkoutSessionType.FULL_BODY)
                .build());
        return plan;
    }

    private WorkoutPlanAssignment assignment(
            Long id, CoachClientRelationship relationship, WorkoutPlan plan, LocalDate startDate) {
        User coach = User.builder().id(8L).email("coach@example.test").fullName("Coach").build();
        User client = User.builder().id(CLIENT_ID).email("client@example.test").fullName("Client").build();
        return WorkoutPlanAssignment.builder()
                .id(id)
                .relationship(relationship)
                .workoutPlan(plan)
                .coach(coach)
                .client(client)
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(startDate.minusDays(1))
                .build();
    }
}
