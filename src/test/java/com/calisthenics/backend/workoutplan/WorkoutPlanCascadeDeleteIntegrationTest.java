package com.calisthenics.backend.workoutplan;

import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.*;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanCascadeDeleteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkoutPlanService workoutPlanService;
    @Autowired private UserRepository userRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;
    @Autowired private PersonalRecordRepository personalRecordRepository;
    @Autowired private CoachClientRelationshipRepository relationshipRepository;
    @Autowired private WorkoutPlanAssignmentRepository assignmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Deleting workout plan removes all linked sessions & child records while keeping other plans, PRs, catalog & users intact")
    void deletingPlanDeletesAllAssociatedSessionsAndPreservesOtherData() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = createUser("cascade-owner-" + suffix + "@test.com");
        User otherUser = createUser("cascade-other-" + suffix + "@test.com");
        Exercise pushUp = createExercise("Push-Up " + suffix, "PUSH_UP_" + suffix);

        WorkoutPlan targetPlan = createPlanWithDayExercise(owner, "Target Plan " + suffix, pushUp);
        WorkoutPlan otherOwnerPlan = createPlanWithDayExercise(owner, "Keep Owner Plan " + suffix, pushUp);
        WorkoutPlan otherUserPlan = createPlanWithDayExercise(otherUser, "Keep Other User Plan " + suffix, pushUp);

        // Create sessions across all lifecycle statuses tied to targetPlan
        WorkoutSession scheduledSession = createSessionWithChildren(owner, targetPlan, pushUp, WorkoutSessionStatus.SCHEDULED, LocalDate.of(2026, 12, 1));
        WorkoutSession inProgressSession = createSessionWithChildren(owner, targetPlan, pushUp, WorkoutSessionStatus.IN_PROGRESS, LocalDate.of(2026, 11, 10));
        WorkoutSession completedSession = createSessionWithChildren(owner, targetPlan, pushUp, WorkoutSessionStatus.COMPLETED, LocalDate.of(2026, 11, 8));
        WorkoutSession cancelledSession = createSessionWithChildren(owner, targetPlan, pushUp, WorkoutSessionStatus.CANCELLED, LocalDate.of(2026, 11, 7));

        // Personal record earned in completedSession must survive plan/session deletion (detached)
        WorkoutSessionSet completedSet = completedSession.getExercises().get(0).getSets().get(0);
        PersonalRecord sessionPr = personalRecordRepository.saveAndFlush(PersonalRecord.builder()
                .user(owner)
                .exercise(pushUp)
                .workoutSession(completedSession)
                .workoutSessionSet(completedSet)
                .recordType(PersonalRecordType.MAX_REPS)
                .recordValue(BigDecimal.valueOf(25))
                .unit("reps")
                .achievedAt(ZonedDateTime.now().minusDays(2))
                .build());

        // Sessions belonging to other plans & standalone session (workout_plan_id IS NULL)
        WorkoutSession otherPlanSession = createSessionWithChildren(owner, otherOwnerPlan, pushUp, WorkoutSessionStatus.IN_PROGRESS, LocalDate.of(2026, 11, 9));
        WorkoutSession otherUserSession = createSessionWithChildren(otherUser, otherUserPlan, pushUp, WorkoutSessionStatus.SCHEDULED, LocalDate.of(2026, 12, 2));
        WorkoutSession standaloneSession = createSessionWithChildren(owner, null, pushUp, WorkoutSessionStatus.COMPLETED, LocalDate.of(2026, 11, 1));

        mockMvc.perform(delete("/api/v1/workout-plans/{id}", targetPlan.getId())
                        .with(user(UserPrincipal.create(owner))))
                .andExpect(status().isNoContent());

        // Verify target plan and all its sessions/children are deleted
        assertThat(workoutPlanRepository.findById(targetPlan.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_sessions WHERE workout_plan_id = ?",
                Integer.class, targetPlan.getId())).isZero();
        assertThat(workoutSessionRepository.findById(scheduledSession.getId())).isEmpty();
        assertThat(workoutSessionRepository.findById(inProgressSession.getId())).isEmpty();
        assertThat(workoutSessionRepository.findById(completedSession.getId())).isEmpty();
        assertThat(workoutSessionRepository.findById(cancelledSession.getId())).isEmpty();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_session_exercises WHERE workout_session_id IN (?, ?, ?, ?)",
                Integer.class, scheduledSession.getId(), inProgressSession.getId(), completedSession.getId(), cancelledSession.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_logs WHERE workout_session_id IN (?, ?, ?, ?)",
                Integer.class, scheduledSession.getId(), inProgressSession.getId(), completedSession.getId(), cancelledSession.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_plan_days WHERE workout_plan_id = ?",
                Integer.class, targetPlan.getId())).isZero();

        // Verify personal record is preserved and cleanly detached
        PersonalRecord reloadedPr = personalRecordRepository.findById(sessionPr.getId()).orElseThrow();
        assertThat(reloadedPr.getWorkoutSession()).isNull();
        assertThat(reloadedPr.getWorkoutSessionSet()).isNull();

        // Verify other plans, their sessions, standalone sessions, catalog exercises, and users remain intact
        assertThat(workoutPlanRepository.findById(otherOwnerPlan.getId())).isPresent();
        assertThat(workoutPlanRepository.findById(otherUserPlan.getId())).isPresent();
        assertThat(workoutSessionRepository.findById(otherPlanSession.getId())).isPresent();
        assertThat(workoutSessionRepository.findById(otherUserSession.getId())).isPresent();
        assertThat(workoutSessionRepository.findById(standaloneSession.getId())).isPresent();
        assertThat(exerciseRepository.findById(pushUp.getId())).isPresent();
        assertThat(userRepository.findById(owner.getId())).isPresent();
        assertThat(userRepository.findById(otherUser.getId())).isPresent();
    }

    @Test
    @DisplayName("Unauthorized user cannot delete another user's workout plan and its sessions remain intact")
    void unauthorizedUserCannotDeletePlanAndSessionsRemainUntouched() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = createUser("auth-owner-" + suffix + "@test.com");
        User attacker = createUser("auth-attacker-" + suffix + "@test.com");
        Exercise dips = createExercise("Dips " + suffix, "DIPS_" + suffix);

        WorkoutPlan ownerPlan = createPlanWithDayExercise(owner, "Protected Plan " + suffix, dips);
        WorkoutSession session = createSessionWithChildren(owner, ownerPlan, dips, WorkoutSessionStatus.IN_PROGRESS, LocalDate.of(2026, 11, 10));

        mockMvc.perform(delete("/api/v1/workout-plans/{id}", ownerPlan.getId())
                        .with(user(UserPrincipal.create(attacker))))
                .andExpect(status().isNotFound());

        assertThat(workoutPlanRepository.findById(ownerPlan.getId())).isPresent();
        assertThat(workoutSessionRepository.findById(session.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_sessions WHERE workout_plan_id = ?",
                Integer.class, ownerPlan.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Transaction rolls back child session deletions atomically if deleting parent workout plan fails")
    void rollbackRestoresChildSessionsWhenPlanDeletionFails() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User coach = createUser("rb-coach-" + suffix + "@test.com");
        User client = createUser("rb-client-" + suffix + "@test.com");
        Exercise pullUp = createExercise("Pull-Up " + suffix, "PULL_UP_" + suffix);

        WorkoutPlan assignedPlan = createPlanWithDayExercise(coach, "Assigned Plan " + suffix, pullUp);
        WorkoutSession s1 = createSessionWithChildren(client, assignedPlan, pullUp, WorkoutSessionStatus.IN_PROGRESS, LocalDate.of(2026, 11, 10));
        WorkoutSession s2 = createSessionWithChildren(client, assignedPlan, pullUp, WorkoutSessionStatus.SCHEDULED, LocalDate.of(2026, 12, 3));

        WorkoutSessionSet set1 = s1.getExercises().get(0).getSets().get(0);
        PersonalRecord pr = personalRecordRepository.saveAndFlush(PersonalRecord.builder()
                .user(client)
                .exercise(pullUp)
                .workoutSession(s1)
                .workoutSessionSet(set1)
                .recordType(PersonalRecordType.MAX_REPS)
                .recordValue(BigDecimal.TEN)
                .unit("reps")
                .achievedAt(ZonedDateTime.now())
                .build());

        CoachClientRelationship relationship = relationshipRepository.saveAndFlush(CoachClientRelationship.builder()
                .coach(coach)
                .client(client)
                .requestedBy(coach)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        assignmentRepository.saveAndFlush(WorkoutPlanAssignment.builder()
                .relationship(relationship)
                .workoutPlan(assignedPlan)
                .coach(coach)
                .client(client)
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 11, 1))
                .build());

        assertThrows(DataIntegrityViolationException.class,
                () -> workoutPlanService.delete(coach.getId(), assignedPlan.getId()));

        // Parent plan, child sessions, child exercises/sets, and PR link must all be rolled back cleanly
        assertThat(workoutPlanRepository.findById(assignedPlan.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_sessions WHERE workout_plan_id = ?",
                Integer.class, assignedPlan.getId())).isEqualTo(2);
        assertThat(workoutSessionRepository.findById(s1.getId())).isPresent();
        assertThat(workoutSessionRepository.findById(s2.getId())).isPresent();
        PersonalRecord intactPr = personalRecordRepository.findById(pr.getId()).orElseThrow();
        assertThat(intactPr.getWorkoutSession()).isNotNull();
        assertThat(intactPr.getWorkoutSession().getId()).isEqualTo(s1.getId());
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .fullName("Cascade Test User")
                .role(Role.ROLE_USER)
                .build());
    }

    private Exercise createExercise(String name, String code) {
        return exerciseRepository.saveAndFlush(Exercise.builder()
                .name(name)
                .code(code)
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .active(true)
                .build());
    }

    private WorkoutPlan createPlanWithDayExercise(User owner, String name, Exercise exercise) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name(name)
                .splitType(WorkoutSplitType.CUSTOM)
                .active(false)
                .build();
        WorkoutPlanDay monday = WorkoutPlanDay.builder()
                .workoutPlan(plan)
                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                .displayOrder(1)
                .sessionType(WorkoutSessionType.PUSH)
                .restDay(false)
                .build();
        monday.getExercises().add(WorkoutPlanExercise.builder()
                .workoutPlanDay(monday)
                .exercise(exercise)
                .sets(3)
                .reps(10)
                .restSeconds(90)
                .displayOrder(1)
                .build());
        plan.getDays().add(monday);
        return workoutPlanRepository.saveAndFlush(plan);
    }

    private WorkoutSession createSessionWithChildren(
            User user,
            WorkoutPlan plan,
            Exercise exercise,
            WorkoutSessionStatus status,
            LocalDate workoutDate
    ) {
        WorkoutSession session = WorkoutSession.builder()
                .user(user)
                .workoutPlan(plan)
                .workoutPlanDay(plan != null ? plan.getDays().get(0) : null)
                .title(exercise.getName() + " Session")
                .source(WorkoutSessionSource.PERSONAL)
                .status(status)
                .workoutDate(workoutDate)
                .startedAt(status == WorkoutSessionStatus.SCHEDULED ? null : ZonedDateTime.now().minusMinutes(30))
                .completedAt(status == WorkoutSessionStatus.COMPLETED ? ZonedDateTime.now() : null)
                .build();

        WorkoutSessionExercise ex = WorkoutSessionExercise.builder()
                .workoutSession(session)
                .exercise(exercise)
                .exerciseNameSnapshot(exercise.getName())
                .orderIndex(1)
                .targetSets(3)
                .targetReps(10)
                .restSeconds(90)
                .build();
        WorkoutSessionSet set = WorkoutSessionSet.builder()
                .workoutSessionExercise(ex)
                .setNumber(1)
                .reps(10)
                .completed(status == WorkoutSessionStatus.COMPLETED)
                .build();
        ex.getSets().add(set);
        session.getExercises().add(ex);

        WorkoutLog log = WorkoutLog.builder()
                .workoutSession(session)
                .exercise(exercise)
                .sets(1)
                .reps(10)
                .build();
        session.getLogs().add(log);

        return workoutSessionRepository.saveAndFlush(session);
    }
}
