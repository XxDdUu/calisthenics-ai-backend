package com.calisthenics.backend.workoutplan;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutDayExercisesBatchRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.UpdateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanDayRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkoutPlanControllerTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsAllSupportedSplitsAndCustomSchedules() throws Exception {
        User owner = createUser("splits@example.com");
        WorkoutSessionType[][] sessions = {
                {WorkoutSessionType.PUSH, WorkoutSessionType.PULL, WorkoutSessionType.LEGS,
                        WorkoutSessionType.PUSH, WorkoutSessionType.PULL, WorkoutSessionType.LEGS,
                        WorkoutSessionType.REST},
                {WorkoutSessionType.UPPER, WorkoutSessionType.LOWER, WorkoutSessionType.REST,
                        WorkoutSessionType.UPPER, WorkoutSessionType.LOWER, WorkoutSessionType.REST,
                        WorkoutSessionType.REST},
                {WorkoutSessionType.FULL_BODY, WorkoutSessionType.REST, WorkoutSessionType.FULL_BODY,
                        WorkoutSessionType.REST, WorkoutSessionType.FULL_BODY, WorkoutSessionType.REST,
                        WorkoutSessionType.REST},
                {WorkoutSessionType.REST, WorkoutSessionType.PUSH, WorkoutSessionType.REST,
                        WorkoutSessionType.UPPER, WorkoutSessionType.LEGS, WorkoutSessionType.PULL,
                        WorkoutSessionType.FULL_BODY}
        };
        WorkoutSplitType[] splits = WorkoutSplitType.values();

        for (int i = 0; i < sessions.length; i++) {
            CreateWorkoutPlanRequest request = planRequest("Plan " + i, splits[i % splits.length], sessions[i]);
            mockMvc.perform(post("/api/v1/workout-plans")
                            .with(authenticatedAs(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists())
                    .andExpect(jsonPath("$.days.length()").value(7));
        }

        mockMvc.perform(get("/api/v1/workout-plans")
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void trimsNameAndRejectsInvalidNamesAndSchedules() throws Exception {
        User owner = createUser("validation@example.com");
        WorkoutSessionType[] validSessions = defaultSessions();

        mockMvc.perform(post("/api/v1/workout-plans")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                planRequest("  Trimmed plan  ", WorkoutSplitType.PPL, validSessions))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Trimmed plan"));

        assertInvalidCreate(owner, planRequest("   ", WorkoutSplitType.PPL, validSessions));
        assertInvalidCreate(owner, planRequest("x".repeat(101), WorkoutSplitType.PPL, validSessions));

        CreateWorkoutPlanRequest noDays =
                planRequest("No days", WorkoutSplitType.PPL, validSessions);
        noDays.setDays(List.of());
        assertInvalidCreate(owner, noDays);

        CreateWorkoutPlanRequest tooManyDays =
                planRequest("Too many days", WorkoutSplitType.PPL, validSessions);
        tooManyDays.getDays().add(tooManyDays.getDays().get(0));
        assertInvalidCreate(owner, tooManyDays);

        CreateWorkoutPlanRequest duplicateDay =
                planRequest("Duplicate day", WorkoutSplitType.PPL, validSessions);
        duplicateDay.getDays().get(6).setDayOfWeek(WorkoutDayOfWeek.MONDAY);
        assertInvalidCreate(owner, duplicateDay);
    }

    @Test
    void retrievesDaysInMondayToSundayOrder() throws Exception {
        User owner = createUser("order@example.com");
        WorkoutPlan plan = createPlan(owner, "Ordered", WorkoutSplitType.PPL, defaultSessions());

        mockMvc.perform(get("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].id").isNumber())
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.days[1].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.days[2].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$.days[3].dayOfWeek").value("THURSDAY"))
                .andExpect(jsonPath("$.days[4].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.days[5].dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.days[6].dayOfWeek").value("SUNDAY"));
    }

    @Test
    void updatesTheCompleteScheduleAndRejectsInvalidUpdateWithoutPartialChanges() throws Exception {
        User owner = createUser("update@example.com");
        WorkoutPlan plan = createPlan(owner, "Before", WorkoutSplitType.PPL, defaultSessions());
        UpdateWorkoutPlanRequest validUpdate =
                updateRequest("After", WorkoutSplitType.UPPER_LOWER, upperLowerSessions());

        mockMvc.perform(put("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.splitType").value("UPPER_LOWER"))
                .andExpect(jsonPath("$.days[0].sessionType").value("UPPER"));

        UpdateWorkoutPlanRequest invalidUpdate =
                updateRequest("Must not persist", WorkoutSplitType.PPL, defaultSessions());
        invalidUpdate.getDays().get(6).setDayOfWeek(WorkoutDayOfWeek.MONDAY);
        mockMvc.perform(put("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdate)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After"))
                .andExpect(jsonPath("$.splitType").value("UPPER_LOWER"))
                .andExpect(jsonPath("$.days[0].sessionType").value("UPPER"));
    }

    @Test
    void deletingPlanAlsoDeletesItsDays() throws Exception {
        User owner = createUser("delete@example.com");
        WorkoutPlan plan = createPlan(owner, "Delete me", WorkoutSplitType.PPL, defaultSessions());

        mockMvc.perform(delete("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(workoutPlanRepository.findById(plan.getId())).isEmpty();
        Integer dayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_plan_days WHERE workout_plan_id = ?", Integer.class, plan.getId());
        org.assertj.core.api.Assertions.assertThat(dayCount).isZero();
    }

    @Test
    void listsOnlyOwnersPlansAndHidesOtherUsersPlans() throws Exception {
        User owner = createUser("owner@example.com");
        User otherUser = createUser("other@example.com");
        WorkoutPlan plan = createPlan(owner, "Private plan", WorkoutSplitType.PPL, defaultSessions());

        mockMvc.perform(get("/api/v1/workout-plans").with(authenticatedAs(otherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/workout-plans/{id}", plan.getId()).with(authenticatedAs(otherUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(otherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                planRequest("Stolen", WorkoutSplitType.PPL, defaultSessions()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(otherUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/workout-plans/{id}", plan.getId()).with(authenticatedAs(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedRequestsAndReturnsEditableTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/workout-plans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
        mockMvc.perform(get("/api/v1/workout-plans")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
        mockMvc.perform(post("/api/v1/workout-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                planRequest("Unauthenticated", WorkoutSplitType.PPL, defaultSessions()))))
                .andExpect(status().isUnauthorized());

        User owner = createUser("templates@example.com");
        mockMvc.perform(get("/api/v1/workout-plans/templates").with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].splitType").value("PPL"))
                .andExpect(jsonPath("$[0].days.length()").value(7))
                .andExpect(jsonPath("$[0].days[0].sessionType").value("PUSH"))
                .andExpect(jsonPath("$[0].days[6].sessionType").value("REST"))
                .andExpect(jsonPath("$[1].splitType").value("UPPER_LOWER"))
                .andExpect(jsonPath("$[1].days[2].sessionType").value("REST"))
                .andExpect(jsonPath("$[2].splitType").value("FULL_BODY"))
                .andExpect(jsonPath("$[2].days[0].sessionType").value("FULL_BODY"));
    }

    @Test
    void unmatchedApiRouteReturnsNotFoundInsteadOfInternalServerError() throws Exception {
        mockMvc.perform(get("/api/v1/not-a-real-route")
                        .with(authenticatedAs(createUser("route-check@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void addsListsUpdatesAndRemovesExistingCatalogExerciseAssignments() throws Exception {
        User owner = createUser("exercise-assignments@example.com");
        WorkoutPlan plan = createPlan(owner, "Exercise plan", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDay monday = plan.getDays().stream()
                .filter(day -> day.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst()
                .orElseThrow();
        Exercise dips = createExercise("Parallel Bar Dips", "DIPS");
        Exercise planche = createExercise("Tuck Planche", "TUCK_PLANCHE");

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(dips, 4, 8, null, 120, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exercise.id").value(dips.getId()))
                .andExpect(jsonPath("$.exercise.name").value("Parallel Bar Dips"))
                .andExpect(jsonPath("$.sets").value(4))
                .andExpect(jsonPath("$.reps").value(8))
                .andExpect(jsonPath("$.durationSeconds").doesNotExist())
                .andExpect(jsonPath("$.displayOrder").value(2));

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(planche, 3, null, 20, 90, 2))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(planche, 3, null, 20, 90, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSeconds").value(20));

        mockMvc.perform(get(exerciseAssignmentsUrl(plan, monday)).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].exercise.id").value(planche.getId()))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[1].exercise.id").value(dips.getId()))
                .andExpect(jsonPath("$[1].displayOrder").value(2));

        List<com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise> assignments =
                workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(monday.getId());
        Long dipAssignmentId = assignments.get(1).getId();
        mockMvc.perform(put(exerciseAssignmentsUrl(plan, monday) + "/{assignmentId}", dipAssignmentId)
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(dips, 5, 10, 30, 75, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets").value(5))
                .andExpect(jsonPath("$.reps").value(10))
                .andExpect(jsonPath("$.durationSeconds").value(30))
                .andExpect(jsonPath("$.restSeconds").value(75))
                .andExpect(jsonPath("$.displayOrder").value(3))
                .andExpect(jsonPath("$.notes").value("Keep good form"));

        mockMvc.perform(delete(exerciseAssignmentsUrl(plan, monday) + "/{assignmentId}", dipAssignmentId)
                        .with(authenticatedAs(owner)))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(exerciseRepository.findById(dips.getId())).isPresent();
        mockMvc.perform(get(exerciseAssignmentsUrl(plan, monday)).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].exercise.id").value(planche.getId()));
    }

    @Test
    void createsAndUpdatesMultipleExercisesWithinOneWorkoutDay() throws Exception {
        User owner = createUser("nested-exercises@example.com");
        Exercise dips = createExercise("Parallel Bar Dips", "NESTED_DIPS");
        Exercise handstandPushUp = createExercise("Handstand Push-up", "NESTED_HSPU");
        Exercise plancheHold = createExercise("Planche Hold", "NESTED_PLANCHE_HOLD");
        Exercise pseudoPlanchePushUp = createExercise("Pseudo Planche Push-up", "NESTED_PPPU");

        CreateWorkoutPlanRequest createRequest = planRequest("Push program", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDayRequest monday = createRequest.getDays().get(0);
        monday.setName("Push Day");
        monday.setExercises(List.of(
                exerciseRequest(dips, 4, 8, null, 120, 1),
                exerciseRequest(handstandPushUp, 3, 5, null, 180, 2),
                exerciseRequest(plancheHold, 4, null, 10, 90, 3)));

        mockMvc.perform(post("/api/v1/workout-plans")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].name").value("Push Day"))
                .andExpect(jsonPath("$.days[0].exercises.length()").value(3))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.id").value(dips.getId()))
                .andExpect(jsonPath("$.days[0].exercises[1].exercise.id").value(handstandPushUp.getId()))
                .andExpect(jsonPath("$.days[0].exercises[2].durationSeconds").value(10));

        WorkoutPlan plan = workoutPlanRepository.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        WorkoutPlanDay persistedMonday = plan.getDays().stream()
                .filter(day -> day.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst()
                .orElseThrow();
        List<com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise> originalAssignments =
                workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(persistedMonday.getId());
        Long dipsAssignmentId = originalAssignments.get(0).getId();
        Long handstandAssignmentId = originalAssignments.get(1).getId();
        Long plancheHoldAssignmentId = originalAssignments.get(2).getId();

        UpdateWorkoutPlanRequest updateRequest = updateRequest(
                "Push program v2", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDayRequest updatedMonday = updateRequest.getDays().get(0);
        updatedMonday.setName("Advanced Push Day");
        WorkoutPlanExerciseRequest retainedPlancheHold = exerciseRequest(plancheHold, 5, null, 12, 120, 1);
        retainedPlancheHold.setId(plancheHoldAssignmentId);
        WorkoutPlanExerciseRequest retainedDips = exerciseRequest(dips, 5, 6, null, 150, 2);
        retainedDips.setId(dipsAssignmentId);
        updatedMonday.setExercises(List.of(
                retainedPlancheHold,
                retainedDips,
                exerciseRequest(pseudoPlanchePushUp, 4, 10, null, 90, 3)));
        updateRequest.setDays(List.of(updatedMonday));

        mockMvc.perform(put("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Push program v2"))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].name").value("Advanced Push Day"))
                .andExpect(jsonPath("$.days[0].exercises.length()").value(3))
                .andExpect(jsonPath("$.days[0].exercises[0].id").value(plancheHoldAssignmentId))
                .andExpect(jsonPath("$.days[0].exercises[0].displayOrder").value(1))
                .andExpect(jsonPath("$.days[0].exercises[1].id").value(dipsAssignmentId))
                .andExpect(jsonPath("$.days[0].exercises[1].displayOrder").value(2))
                .andExpect(jsonPath("$.days[0].exercises[2].exercise.id").value(pseudoPlanchePushUp.getId()));

        List<com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise> updatedAssignments =
                workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(persistedMonday.getId());
        org.assertj.core.api.Assertions.assertThat(updatedAssignments)
                .extracting(com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise::getId)
                .containsExactly(plancheHoldAssignmentId, dipsAssignmentId, updatedAssignments.get(2).getId())
                .doesNotContain(handstandAssignmentId);
        org.assertj.core.api.Assertions.assertThat(updatedAssignments)
                .extracting(com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise::getDisplayOrder)
                .containsExactly(1, 2, 3);
        org.assertj.core.api.Assertions.assertThat(exerciseRepository.findById(handstandPushUp.getId())).isPresent();
    }

    @Test
    void rejectsInvalidNestedExerciseListsBeforeCreatingThePlan() throws Exception {
        User owner = createUser("nested-validation@example.com");
        Exercise dips = createExercise("Parallel Bar Dips", "INVALID_NESTED_DIPS");
        Exercise handstandPushUp = createExercise("Handstand Push-up", "INVALID_NESTED_HSPU");
        CreateWorkoutPlanRequest request = planRequest("Invalid nested plan", WorkoutSplitType.PPL, defaultSessions());
        request.getDays().get(0).setExercises(List.of(
                exerciseRequest(dips, 4, 8, null, 120, 1),
                exerciseRequest(handstandPushUp, 3, 5, null, 120, 1)));

        mockMvc.perform(post("/api/v1/workout-plans")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exercise display orders must be unique within a day"));

        org.assertj.core.api.Assertions.assertThat(
                workoutPlanRepository.findByUserIdOrderByCreatedAtDesc(owner.getId())).isEmpty();
    }

    @Test
    void fetchingMultiExercisePlanByIdNeverDuplicatesWeekdays() throws Exception {
        User owner = createUser("dedup-days@example.com");
        Exercise pushUp = createExercise("Push-up Dedup", "DEDUP_PUSH");
        Exercise pullUp = createExercise("Pull-up Dedup", "DEDUP_PULL");
        WorkoutPlan plan = createPlan(owner, "7 Day Dedup", WorkoutSplitType.CUSTOM, defaultSessions());
        for (WorkoutPlanDay day : plan.getDays()) {
            if (!day.isRestDay()) {
                mockMvc.perform(post(exerciseAssignmentsUrl(plan, day))
                                .with(authenticatedAs(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(exerciseRequest(pushUp, 3, 10, null, 60, 1))))
                        .andExpect(status().isCreated());
                mockMvc.perform(post(exerciseAssignmentsUrl(plan, day))
                                .with(authenticatedAs(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(exerciseRequest(pullUp, 3, 8, null, 90, 2))))
                        .andExpect(status().isCreated());
            }
        }
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/workout-plans/{id}", plan.getId()).with(authenticatedAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(7));
    }

    @Autowired
    private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;

    @Test
    void validatesAssignedExerciseInputAndReturnsNotFoundForMissingCatalogEntry() throws Exception {
        User owner = createUser("invalid-exercise-assignment@example.com");
        WorkoutPlan plan = createPlan(owner, "Exercise plan", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDay monday = plan.getDays().get(0);
        Exercise exercise = createExercise("Parallel Bar Dips", "DIPS");

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(exercise, 0, 8, null, 60, 1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(exercise, 3, 8, null, -1, 1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exerciseRequest(exercise, 3, null, 0, 60, 1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exerciseId\":999999,\"sets\":3,\"reps\":8,\"restSeconds\":60,\"displayOrder\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInactiveCatalogExercises() throws Exception {
        User owner = createUser("inactive-exercise@example.com");
        WorkoutPlan plan = createPlan(owner, "Exercise plan", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDay monday = plan.getDays().get(0);
        Exercise inactive = createExercise("Inactive Exercise", "INACTIVE_EXERCISE");
        inactive.setActive(false);
        exerciseRepository.saveAndFlush(inactive);

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                exerciseRequest(inactive, 3, 8, null, 60, 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void exerciseAssignmentsRequirePlanOwnershipAndMatchingDay() throws Exception {
        User owner = createUser("exercise-owner@example.com");
        User otherUser = createUser("exercise-other@example.com");
        WorkoutPlan plan = createPlan(owner, "Exercise plan", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlan otherPlan = createPlan(otherUser, "Other plan", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDay monday = plan.getDays().stream()
                .filter(day -> day.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst()
                .orElseThrow();
        WorkoutPlanDay otherMonday = otherPlan.getDays().stream()
                .filter(day -> day.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst()
                .orElseThrow();
        Exercise exercise = createExercise("Parallel Bar Dips", "DIPS");
        String payload = objectMapper.writeValueAsString(exerciseRequest(exercise, 3, 8, null, 60, 1));

        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(otherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(get(exerciseAssignmentsUrl(plan, otherMonday))
                        .with(authenticatedAs(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsPlanWithMultipleExercisesWithoutExplicitSplitOrSessionTypes() throws Exception {
        User owner = createUser("inferred-plan@example.com");
        Exercise pushup = createExercise("Planche Push-up", "INF_PUSHUP");
        Exercise hspu = createExercise("Handstand Push-up", "INF_HSPU");
        Exercise dips = createExercise("Weighted Dips", "INF_DIPS");

        String payload = """
                {
                  "name": "Push Pull Legs",
                  "days": [
                    {
                      "dayOfWeek": "MONDAY",
                      "name": "Push Day",
                      "exercises": [
                        {
                          "exerciseId": %d,
                          "sets": 4,
                          "reps": 8,
                          "durationSeconds": null,
                          "restSeconds": 120,
                          "displayOrder": 1,
                          "notes": "Target solid pause on lockout."
                        },
                        {
                          "exerciseId": %d,
                          "sets": 3,
                          "reps": 5,
                          "durationSeconds": null,
                          "restSeconds": 180,
                          "displayOrder": 2,
                          "notes": "Focus on controlled eccentric."
                        },
                        {
                          "exerciseId": %d,
                          "sets": 4,
                          "reps": null,
                          "durationSeconds": 10,
                          "restSeconds": 90,
                          "displayOrder": 3,
                          "notes": "Hold the position with proper form."
                        }
                      ]
                    }
                  ]
                }
                """.formatted(pushup.getId(), hspu.getId(), dips.getId());

        mockMvc.perform(post("/api/v1/workout-plans")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Push Pull Legs"))
                .andExpect(jsonPath("$.splitType").value("PPL"))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].sessionType").value("PUSH"))
                .andExpect(jsonPath("$.days[0].exercises.length()").value(3))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.name").value("Planche Push-up"))
                .andExpect(jsonPath("$.days[0].exercises[1].exercise.name").value("Handstand Push-up"))
                .andExpect(jsonPath("$.days[0].exercises[2].durationSeconds").value(10));
    }

    @Test
    void batchUpdatesAndReordersExercisesForDay() throws Exception {
        User owner = createUser("batch-exercises@example.com");
        Exercise dips = createExercise("Weighted Dips", "BATCH_DIPS");
        Exercise planche = createExercise("Planche Hold", "BATCH_PLANCHE");
        Exercise hspu = createExercise("Handstand Push-up", "BATCH_HSPU");

        WorkoutPlan plan = createPlan(owner, "Batch Plan", WorkoutSplitType.PPL, defaultSessions());
        WorkoutPlanDay monday = plan.getDays().stream()
                .filter(day -> day.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst()
                .orElseThrow();

        // 1. Add initial 2 exercises
        WorkoutPlanExerciseRequest dipsReq = exerciseRequest(dips, 3, 8, null, 60, 1);
        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dipsReq)))
                .andExpect(status().isCreated());

        WorkoutPlanExerciseRequest plancheReq = exerciseRequest(planche, 4, null, 15, 90, 2);
        mockMvc.perform(post(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(plancheReq)))
                .andExpect(status().isCreated());

        List<com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise> initialExercises =
                workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(monday.getId());
        Long dipsId = initialExercises.get(0).getId();
        Long plancheId = initialExercises.get(1).getId();

        // 2. Batch update: reorder planche to 1, dips to 2, and add hspu as 3
        String batchPayload = """
                {
                  "exercises": [
                    {
                      "id": %d,
                      "exerciseId": %d,
                      "sets": 4,
                      "durationSeconds": 20,
                      "restSeconds": 90,
                      "displayOrder": 1,
                      "notes": "Updated hold"
                    },
                    {
                      "id": %d,
                      "exerciseId": %d,
                      "sets": 5,
                      "reps": 10,
                      "restSeconds": 120,
                      "displayOrder": 2,
                      "notes": "Updated dips"
                    },
                    {
                      "exerciseId": %d,
                      "sets": 3,
                      "reps": 6,
                      "restSeconds": 150,
                      "displayOrder": 3,
                      "notes": "Added HSPU"
                    }
                  ]
                }
                """.formatted(plancheId, planche.getId(), dipsId, dips.getId(), hspu.getId());

        mockMvc.perform(put(exerciseAssignmentsUrl(plan, monday))
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(plancheId))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[0].durationSeconds").value(20))
                .andExpect(jsonPath("$[1].id").value(dipsId))
                .andExpect(jsonPath("$[1].displayOrder").value(2))
                .andExpect(jsonPath("$[1].sets").value(5))
                .andExpect(jsonPath("$[2].exercise.id").value(hspu.getId()))
                .andExpect(jsonPath("$[2].displayOrder").value(3));
    }

    @Test
    void planUpdatePreservesDayExercisesWhenExercisesFieldIsOmitted() throws Exception {
        User owner = createUser("preserve-exercises@example.com");
        Exercise dips = createExercise("Dips", "PRESERVE_DIPS");

        CreateWorkoutPlanRequest createRequest = planRequest("Preserve plan", WorkoutSplitType.PPL, defaultSessions());
        createRequest.getDays().get(0).setExercises(List.of(exerciseRequest(dips, 4, 8, null, 120, 1)));

        mockMvc.perform(post("/api/v1/workout-plans")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].exercises.length()").value(1));

        WorkoutPlan plan = workoutPlanRepository.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();

        // Update plan with days where exercises is null (omitted)
        UpdateWorkoutPlanRequest updateRequest = new UpdateWorkoutPlanRequest();
        updateRequest.setName("Renamed Preserve Plan");
        WorkoutPlanDayRequest mondayUpdate = new WorkoutPlanDayRequest();
        mondayUpdate.setDayOfWeek(WorkoutDayOfWeek.MONDAY);
        mondayUpdate.setSessionType(WorkoutSessionType.PUSH);
        mondayUpdate.setName("Monday Push");
        mondayUpdate.setExercises(null); // omitted
        updateRequest.setDays(List.of(mondayUpdate));

        mockMvc.perform(put("/api/v1/workout-plans/{id}", plan.getId())
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Preserve Plan"))
                .andExpect(jsonPath("$.days[0].name").value("Monday Push"))
                .andExpect(jsonPath("$.days[0].exercises.length()").value(1))
                .andExpect(jsonPath("$.days[0].exercises[0].exercise.id").value(dips.getId()));
    }

    private String exerciseAssignmentsUrl(WorkoutPlan plan, WorkoutPlanDay day) {
        return "/api/v1/workout-plans/" + plan.getId()
                + "/days/" + day.getId() + "/exercises";
    }

    private Exercise createExercise(String name, String code) {
        return exerciseRepository.saveAndFlush(Exercise.builder()
                .name(name)
                .code(code)
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .description("Catalog exercise")
                .build());
    }

    private WorkoutPlanExerciseRequest exerciseRequest(
            Exercise exercise, int sets, Integer reps, Integer duration, int rest, int order) {
        WorkoutPlanExerciseRequest request = new WorkoutPlanExerciseRequest();
        request.setExerciseId(exercise.getId());
        request.setSets(sets);
        request.setReps(reps);
        request.setDurationSeconds(duration);
        request.setRestSeconds(rest);
        request.setDisplayOrder(order);
        request.setNotes("Keep good form");
        return request;
    }

    private void assertInvalidCreate(User owner, CreateWorkoutPlanRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/workout-plans")
                        .with(authenticatedAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private WorkoutPlan createPlan(
            User owner, String name, WorkoutSplitType split, WorkoutSessionType[] sessions) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name(name)
                .splitType(split)
                .build();
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            plan.getDays().add(WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(day)
                    .sessionType(sessions[day.getOrder() - 1])
                    .build());
        }
        return workoutPlanRepository.saveAndFlush(plan);
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .fullName("Workout Plan Test User")
                .role(Role.ROLE_USER)
                .build());
    }

    private RequestPostProcessor authenticatedAs(User user) {
        return user(UserPrincipal.create(user));
    }

    private CreateWorkoutPlanRequest planRequest(
            String name, WorkoutSplitType split, WorkoutSessionType[] sessions) {
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest();
        setRequestValues(request, name, split, sessions);
        return request;
    }

    private UpdateWorkoutPlanRequest updateRequest(
            String name, WorkoutSplitType split, WorkoutSessionType[] sessions) {
        UpdateWorkoutPlanRequest request = new UpdateWorkoutPlanRequest();
        setRequestValues(request, name, split, sessions);
        return request;
    }

    private void setRequestValues(
            WorkoutPlanRequest request,
            String name, WorkoutSplitType split, WorkoutSessionType[] sessions) {
        request.setName(name);
        request.setSplitType(split);
        List<WorkoutPlanDayRequest> days = new ArrayList<>();
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            WorkoutPlanDayRequest dayRequest = new WorkoutPlanDayRequest();
            dayRequest.setDayOfWeek(day);
            dayRequest.setSessionType(sessions[day.getOrder() - 1]);
            days.add(dayRequest);
        }
        request.setDays(days);
    }

    private WorkoutSessionType[] defaultSessions() {
        return new WorkoutSessionType[]{
                WorkoutSessionType.PUSH, WorkoutSessionType.PULL, WorkoutSessionType.LEGS,
                WorkoutSessionType.PUSH, WorkoutSessionType.PULL, WorkoutSessionType.LEGS,
                WorkoutSessionType.REST
        };
    }

    private WorkoutSessionType[] upperLowerSessions() {
        return new WorkoutSessionType[]{
                WorkoutSessionType.UPPER, WorkoutSessionType.LOWER, WorkoutSessionType.REST,
                WorkoutSessionType.UPPER, WorkoutSessionType.LOWER, WorkoutSessionType.REST,
                WorkoutSessionType.REST
        };
    }
}
