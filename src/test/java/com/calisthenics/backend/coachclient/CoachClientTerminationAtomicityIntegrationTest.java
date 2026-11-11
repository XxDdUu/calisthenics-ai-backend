package com.calisthenics.backend.coachclient;

import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.service.CoachClientRelationshipService;
import com.calisthenics.backend.coachclient.service.WorkoutPlanAssignmentService;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class CoachClientTerminationAtomicityIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private WorkoutPlanAssignmentRepository assignmentRepository;

    @SpyBean
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private CoachClientRelationshipService relationshipService;

    @Autowired
    private WorkoutPlanAssignmentService assignmentService;

    @Test
    void terminateRollsBackRelationshipAndAssignmentStatusWhenSessionDeactivationFails() {
        User coach = createUser("atomic-coach");
        User client = createUser("atomic-client");
        WorkoutPlan plan = createPlan(coach);

        CreateCoachClientRequest relReq = new CreateCoachClientRequest();
        relReq.setClientId(client.getId());
        Long relationshipId = relationshipService.request(coach.getId(), relReq).getId();
        relationshipService.transition(client.getId(), relationshipId, CoachClientRelationshipStatus.ACTIVE);

        CreateWorkoutPlanAssignmentRequest assignReq = new CreateWorkoutPlanAssignmentRequest();
        assignReq.setWorkoutPlanId(plan.getId());
        Long assignmentId = assignmentService.assign(coach.getId(), relationshipId, assignReq).getId();

        WorkoutPlanAssignment assignment = assignmentRepository.findById(assignmentId).orElseThrow();
        WorkoutSession session = workoutSessionRepository.saveAndFlush(WorkoutSession.builder()
                .user(client)
                .workoutPlan(plan)
                .workoutPlanAssignment(assignment)
                .source(WorkoutSessionSource.COACH)
                .title("Atomic coach session")
                .workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.IN_PROGRESS)
                .build());

        doThrow(new RuntimeException("Simulated persistence failure"))
                .when(workoutSessionRepository).saveAllAndFlush(anyList());

        assertThatThrownBy(() -> relationshipService.terminate(coach.getId(), relationshipId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated persistence failure");

        CoachClientRelationship persistedRelationship = relationshipRepository.findById(relationshipId).orElseThrow();
        WorkoutPlanAssignment persistedAssignment = assignmentRepository.findById(assignmentId).orElseThrow();
        WorkoutSession persistedSession = workoutSessionRepository.findById(session.getId()).orElseThrow();

        assertThat(persistedRelationship.getStatus()).isEqualTo(CoachClientRelationshipStatus.ACTIVE);
        assertThat(persistedAssignment.getStatus()).isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);
        assertThat(persistedSession.getStatus()).isEqualTo(WorkoutSessionStatus.IN_PROGRESS);
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .fullName(prefix)
                .role(Role.ROLE_USER)
                .build());
    }

    private WorkoutPlan createPlan(User owner) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name("Atomic Plan")
                .splitType(WorkoutSplitType.FULL_BODY)
                .build();
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            plan.getDays().add(WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(day)
                    .sessionType(WorkoutSessionType.FULL_BODY)
                    .build());
        }
        return workoutPlanRepository.saveAndFlush(plan);
    }
}
