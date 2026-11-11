package com.calisthenics.backend.coachclient;

import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.service.CoachClientRelationshipService;
import com.calisthenics.backend.coachclient.service.WorkoutPlanAssignmentService;
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
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionExerciseStatus;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workout.dto.FinishWorkoutSessionRequest;
import com.calisthenics.backend.workout.dto.StartWorkoutSessionRequest;
import com.calisthenics.backend.workout.dto.UpdateWorkoutSessionSetRequest;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachClientAssignmentCancellationLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private WorkoutPlanAssignmentRepository assignmentRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private PersonalRecordRepository personalRecordRepository;

    @Autowired
    private CoachClientRelationshipService relationshipService;

    @Autowired
    private WorkoutPlanAssignmentService assignmentService;

    @Test
    void fullRelationshipTerminationSoftCancelsAssignmentsPreservesHistoryAndDoesNotReactivateOnReconnect()
            throws Exception {
        User coach = createUser("lifecycle-coach");
        User client = createUser("lifecycle-client");
        User otherClient = createUser("lifecycle-other-client");
        Exercise pushUp = createExercise("Lifecycle Push-up");

        WorkoutPlan coachPlanA = createFullWeekPlan(coach, "Coach Plan A", pushUp);
        WorkoutPlan coachPlanB = createFullWeekPlan(coach, "Coach Plan B", pushUp);
        WorkoutPlan coachPlanForOtherClient = createFullWeekPlan(coach, "Coach Other Client Plan", pushUp);
        WorkoutPlan personalPlan = createFullWeekPlan(client, "Client Personal Plan", pushUp);

        CoachClientRelationship relationship = connect(coach, client);
        CoachClientRelationship otherRelationship = connect(coach, otherClient);

        // 1. Coach assigns multiple plans to client (one stays ACTIVE, one manually INACTIVE)
        //    plus assigns a plan to another client.
        WorkoutPlanAssignment activeAssignment = createAssignment(coach, relationship, coachPlanA);
        WorkoutPlanAssignment manuallyInactiveAssignment = createAssignment(coach, relationship, coachPlanB);
        mockMvc.perform(patch("/api/v1/coach/assignments/{id}/status", manuallyInactiveAssignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        WorkoutPlanAssignment otherClientAssignment =
                createAssignment(coach, otherRelationship, coachPlanForOtherClient);

        // Client has historical COMPLETED session + PR tied to Coach Plan A,
        // plus an IN_PROGRESS session for today and a future SCHEDULED upcoming session.
        LocalDate today = LocalDate.now();
        WorkoutSession completedCoachSession = persistCompletedSessionWithPr(
                client, coachPlanA, activeAssignment, pushUp, today.minusDays(2));

        WorkoutPlanDay todayDay = coachPlanA.getDays().stream()
                .filter(d -> d.getDayOfWeek().getOrder() == today.getDayOfWeek().getValue())
                .findFirst()
                .orElseThrow();
        WorkoutSession inProgressCoachSession = persistSession(
                client, coachPlanA, todayDay, activeAssignment, today, WorkoutSessionStatus.IN_PROGRESS, pushUp);
        WorkoutSession upcomingCoachSession = persistSession(
                client, coachPlanA, todayDay, activeAssignment, today.plusDays(2), WorkoutSessionStatus.SCHEDULED, pushUp);

        // Sanity check before termination: Coach Plan A resolves Today Workout for client.
        mockMvc.perform(get("/api/v1/workout-plans/today")
                        .with(authenticatedAs(client))
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCoach").value(true))
                .andExpect(jsonPath("$.source").value("COACH"))
                .andExpect(jsonPath("$.candidates[0].workoutPlan.id").value(coachPlanA.getId()));

        // 2. Terminate Coach-Client relationship
        mockMvc.perform(post("/api/v1/coach-client-relationships/{id}/terminate", relationship.getId())
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        // 3. All assignments created by that coach for that client become CANCELLED (not deleted)
        WorkoutPlanAssignment reloadedActive = assignmentRepository.findById(activeAssignment.getId()).orElseThrow();
        WorkoutPlanAssignment reloadedManualInactive =
                assignmentRepository.findById(manuallyInactiveAssignment.getId()).orElseThrow();
        assertThat(reloadedActive.getStatus()).isEqualTo(WorkoutPlanAssignmentStatus.CANCELLED);
        assertThat(reloadedActive.getEndDate()).isNotNull();
        assertThat(reloadedManualInactive.getStatus()).isEqualTo(WorkoutPlanAssignmentStatus.CANCELLED);

        // Cannot manually mutate or reactivate a CANCELLED assignment
        mockMvc.perform(put("/api/v1/coach-client-relationships/{relId}/assignments/{id}",
                        relationship.getId(), activeAssignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());

        // Uncompleted sessions tied to canceled assignments are soft-canceled, COMPLETED is preserved
        assertThat(workoutSessionRepository.findById(inProgressCoachSession.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutSessionStatus.CANCELLED);
        assertThat(workoutSessionRepository.findById(upcomingCoachSession.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutSessionStatus.CANCELLED);
        assertThat(workoutSessionRepository.findById(completedCoachSession.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutSessionStatus.COMPLETED);

        // Client cannot continue/update canceled in-progress coach session
        WorkoutSessionExercise cancelledExercise = inProgressCoachSession.getExercises().getFirst();
        WorkoutSessionSet cancelledSet = cancelledExercise.getSets().getFirst();
        mockMvc.perform(put("/api/v1/workout-sessions/{sid}/exercises/{eid}/sets/{setId}",
                        inProgressCoachSession.getId(), cancelledExercise.getId(), cancelledSet.getId())
                        .with(authenticatedAs(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateWorkoutSessionSetRequest(10, null, BigDecimal.ZERO, null, true, null, 0L))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/workout-sessions/{sid}/finish", inProgressCoachSession.getId())
                        .with(authenticatedAs(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishWorkoutSessionRequest(true))))
                .andExpect(status().isConflict());

        // Cannot start a new session against Coach Plan A as client
        mockMvc.perform(post("/api/v1/workout-sessions")
                        .with(authenticatedAs(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartWorkoutSessionRequest(coachPlanA.getId(), todayDay.getId(), today, activeAssignment.getId()))))
                .andExpect(status().isNotFound());

        // 4. Client and Coach active endpoints exclude CANCELLED assignments & sessions
        mockMvc.perform(get("/api/v1/clients/me/workout-plan-assignments")
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/coach-client-relationships/{id}/assignments", relationship.getId())
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/coach/me/workout-plan-assignments")
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(otherClientAssignment.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(activeAssignment.getId().intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(manuallyInactiveAssignment.getId().intValue()))));

        mockMvc.perform(get("/api/v1/workout-plans/{id}", coachPlanA.getId())
                        .with(authenticatedAs(client)))
                .andExpect(status().isNotFound());

        // Today Workout immediately falls back to client's personal plan
        mockMvc.perform(get("/api/v1/workout-plans/today")
                        .with(authenticatedAs(client))
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCoach").value(false))
                .andExpect(jsonPath("$.source").value("PERSONAL"))
                .andExpect(jsonPath("$.candidates[0].workoutPlan.id").value(personalPlan.getId()));

        // Active & Upcoming session endpoints exclude canceled coach sessions
        mockMvc.perform(get("/api/v1/workout-sessions/active")
                        .with(authenticatedAs(client))
                        .param("localDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/workout-sessions/upcoming")
                        .with(authenticatedAs(client))
                        .param("localDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 5. Historical endpoints & completed workout history + PRs remain intact and retrievable
        mockMvc.perform(get("/api/v1/clients/me/workout-plan-assignments/history")
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", hasItem("CANCELLED")));

        mockMvc.perform(get("/api/v1/coach-client-relationships/{id}/assignments", relationship.getId())
                        .with(authenticatedAs(coach))
                        .param("includeCancelled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/workout-sessions")
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(completedCoachSession.getId().intValue())));

        mockMvc.perform(get("/api/v1/workout-sessions/{id}", completedCoachSession.getId())
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/prs")
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recordValue").value(15));
        assertThat(personalRecordRepository.findByWorkoutSessionId(completedCoachSession.getId())).hasSize(1);

        // 6. Other client's assignment and client's personal plan remain unaffected;
        //    coach's manual INACTIVE toggle on active relationship remains INACTIVE (distinct from CANCELLED).
        assertThat(assignmentRepository.findById(otherClientAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);
        mockMvc.perform(patch("/api/v1/coach/assignments/{id}/status", otherClientAssignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertThat(workoutPlanRepository.findById(personalPlan.getId())).isPresent();

        // 7. Reconnecting the same coach and client does NOT automatically reactivate old CANCELLED assignments
        CoachClientRelationship reconnected = connect(coach, client);
        assertThat(reconnected.getId()).isNotEqualTo(relationship.getId());
        assertThat(assignmentRepository.findById(activeAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.CANCELLED);
        assertThat(assignmentRepository.findById(manuallyInactiveAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.CANCELLED);

        mockMvc.perform(get("/api/v1/clients/me/workout-plan-assignments")
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/workout-plans/today")
                        .with(authenticatedAs(client))
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCoach").value(true))
                .andExpect(jsonPath("$.source").value("PERSONAL"))
                .andExpect(jsonPath("$.candidates[0].workoutPlan.id").value(personalPlan.getId()));

        // Re-assigning the same plan on the reconnected relationship creates a NEW ACTIVE row
        // while keeping the old CANCELLED assignment untouched in history.
        WorkoutPlanAssignment reassigned = createAssignment(coach, reconnected, coachPlanA);
        assertThat(reassigned.getId()).isNotEqualTo(activeAssignment.getId());
        assertThat(reassigned.getStatus()).isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);
        assertThat(assignmentRepository.findById(activeAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.CANCELLED);
    }

    private WorkoutSession persistCompletedSessionWithPr(
            User client, WorkoutPlan plan, WorkoutPlanAssignment assignment, Exercise exercise, LocalDate date) {
        WorkoutPlanDay day = plan.getDays().iterator().next();
        WorkoutSession session = persistSession(
                client, plan, day, assignment, date, WorkoutSessionStatus.COMPLETED, exercise);
        session.setCompletedAt(ZonedDateTime.now().minusDays(2));
        WorkoutSession saved = workoutSessionRepository.saveAndFlush(session);
        personalRecordRepository.saveAndFlush(PersonalRecord.builder()
                .user(client)
                .exercise(exercise)
                .workoutSession(saved)
                .recordType(PersonalRecordType.MAX_REPS)
                .recordValue(BigDecimal.valueOf(15))
                .unit("reps")
                .achievedAt(ZonedDateTime.now().minusDays(2))
                .build());
        return saved;
    }

    private WorkoutSession persistSession(
            User client,
            WorkoutPlan plan,
            WorkoutPlanDay day,
            WorkoutPlanAssignment assignment,
            LocalDate date,
            WorkoutSessionStatus status,
            Exercise exercise) {
        WorkoutSession session = WorkoutSession.builder()
                .user(client)
                .workoutPlan(plan)
                .workoutPlanDay(day)
                .workoutPlanAssignment(assignment)
                .source(WorkoutSessionSource.COACH)
                .title(plan.getName())
                .workoutDate(date)
                .status(status)
                .startedAt(status == WorkoutSessionStatus.SCHEDULED ? null : ZonedDateTime.now())
                .build();
        WorkoutSessionExercise sessionExercise = WorkoutSessionExercise.builder()
                .workoutSession(session)
                .exercise(exercise)
                .exerciseNameSnapshot(exercise.getName())
                .orderIndex(0)
                .targetSets(1)
                .targetReps(10)
                .restSeconds(60)
                .status(status == WorkoutSessionStatus.COMPLETED
                        ? WorkoutSessionExerciseStatus.COMPLETED
                        : WorkoutSessionExerciseStatus.IN_PROGRESS)
                .build();
        WorkoutSessionSet set = WorkoutSessionSet.builder()
                .workoutSessionExercise(sessionExercise)
                .setNumber(1)
                .reps(10)
                .completed(status == WorkoutSessionStatus.COMPLETED)
                .build();
        sessionExercise.getSets().add(set);
        session.getExercises().add(sessionExercise);
        return workoutSessionRepository.saveAndFlush(session);
    }

    private WorkoutPlanAssignment createAssignment(
            User coach, CoachClientRelationship relationship, WorkoutPlan plan) {
        CreateWorkoutPlanAssignmentRequest request = new CreateWorkoutPlanAssignmentRequest();
        request.setWorkoutPlanId(plan.getId());
        WorkoutPlanAssignmentResponse response =
                assignmentService.assign(coach.getId(), relationship.getId(), request);
        return assignmentRepository.findById(response.getId()).orElseThrow();
    }

    private CoachClientRelationship connect(User coach, User client) {
        CreateCoachClientRequest request = new CreateCoachClientRequest();
        request.setClientId(client.getId());
        Long relationshipId = relationshipService.request(coach.getId(), request).getId();
        relationshipService.transition(client.getId(), relationshipId, CoachClientRelationshipStatus.ACTIVE);
        return relationshipRepository.findById(relationshipId).orElseThrow();
    }

    private Exercise createExercise(String name) {
        return exerciseRepository.saveAndFlush(Exercise.builder()
                .name(name)
                .code("CODE_" + UUID.randomUUID())
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .build());
    }

    private WorkoutPlan createFullWeekPlan(User owner, String name, Exercise exercise) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name(name)
                .splitType(WorkoutSplitType.FULL_BODY)
                .build();
        for (WorkoutDayOfWeek dayOfWeek : WorkoutDayOfWeek.values()) {
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dayOfWeek)
                    .sessionType(WorkoutSessionType.FULL_BODY)
                    .build();
            day.getExercises().add(WorkoutPlanExercise.builder()
                    .workoutPlanDay(day)
                    .exercise(exercise)
                    .sets(3)
                    .reps(10)
                    .restSeconds(60)
                    .displayOrder(0)
                    .build());
            plan.getDays().add(day);
        }
        return workoutPlanRepository.saveAndFlush(plan);
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .fullName(prefix)
                .role(Role.ROLE_USER)
                .build());
    }

    private RequestPostProcessor authenticatedAs(User user) {
        return user(UserPrincipal.create(user));
    }
}
