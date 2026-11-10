package com.calisthenics.backend.coachclient;

import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.response.CoachClientRelationshipResponse;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.service.CoachClientRelationshipService;
import com.calisthenics.backend.coachclient.service.WorkoutPlanAssignmentService;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanAssignmentStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private WorkoutPlanAssignmentRepository assignmentRepository;

    @Autowired
    private CoachClientRelationshipService relationshipService;

    @Autowired
    private WorkoutPlanAssignmentService assignmentService;

    @Test
    void coachCanDeactivateAndReactivateTheSameAssignment() throws Exception {
        User coach = createUser("status-coach");
        User athlete = createUser("status-athlete");
        WorkoutPlan plan = createPlan(coach);
        CoachClientRelationship relationship = connect(coach, athlete);
        WorkoutPlanAssignment assignment = createAssignment(coach, relationship, plan);

        mockMvc.perform(patch("/api/v1/coach/assignments/{assignmentId}/status", assignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignment.getId()))
                .andExpect(jsonPath("$.workoutPlanId").value(plan.getId()))
                .andExpect(jsonPath("$.clientId").value(athlete.getId()))
                .andExpect(jsonPath("$.athleteId").value(athlete.getId()))
                .andExpect(jsonPath("$.coachId").value(coach.getId()))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(patch("/api/v1/coach/assignments/{assignmentId}/status", assignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignment.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(assignmentRepository.findByRelationshipIdOrderByCreatedAtDesc(relationship.getId()))
                .hasSize(1);
        assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);
    }

    @Test
    void assigningAnInactivePlanReusesTheExistingAssignmentRow() throws Exception {
        User coach = createUser("reuse-coach");
        User athlete = createUser("reuse-athlete");
        WorkoutPlan plan = createPlan(coach);
        CoachClientRelationship relationship = connect(coach, athlete);
        WorkoutPlanAssignment assignment = createAssignment(coach, relationship, plan);

        mockMvc.perform(patch("/api/v1/coach/assignments/{assignmentId}/status", assignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/coach-client-relationships/{relationshipId}/assignments", relationship.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutPlanId\":" + plan.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(assignment.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(assignmentRepository.findByRelationshipIdOrderByCreatedAtDesc(relationship.getId()))
                .hasSize(1);
    }

    @Test
    void onlyOwningCoachCanChangeStatus() throws Exception {
        User coach = createUser("owner-coach");
        User otherCoach = createUser("other-coach");
        User athlete = createUser("permission-athlete");
        WorkoutPlan plan = createPlan(coach);
        CoachClientRelationship relationship = connect(coach, athlete);
        WorkoutPlanAssignment assignment = createAssignment(coach, relationship, plan);

        for (User unauthorized : new User[]{otherCoach, athlete}) {
            mockMvc.perform(patch("/api/v1/coach/assignments/{assignmentId}/status", assignment.getId())
                            .with(authenticatedAs(unauthorized))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"INACTIVE\"}"))
                    .andExpect(status().isNotFound());
        }

        assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);
    }

    @Test
    void statusEndpointRejectsNonToggleStatuses() throws Exception {
        User coach = createUser("invalid-status-coach");
        User athlete = createUser("invalid-status-athlete");
        WorkoutPlan plan = createPlan(coach);
        CoachClientRelationship relationship = connect(coach, athlete);
        WorkoutPlanAssignment assignment = createAssignment(coach, relationship, plan);

        mockMvc.perform(patch("/api/v1/coach/assignments/{assignmentId}/status", assignment.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());
    }

    private WorkoutPlanAssignment createAssignment(User coach, CoachClientRelationship relationship, WorkoutPlan plan) {
        CreateWorkoutPlanAssignmentRequest request = new CreateWorkoutPlanAssignmentRequest();
        request.setWorkoutPlanId(plan.getId());
        WorkoutPlanAssignmentResponse response = assignmentService.assign(coach.getId(), relationship.getId(), request);
        return assignmentRepository.findById(response.getId()).orElseThrow();
    }

    private CoachClientRelationship connect(User coach, User athlete) {
        CreateCoachClientRequest request = new CreateCoachClientRequest();
        request.setClientId(athlete.getId());
        CoachClientRelationshipResponse response = relationshipService.request(coach.getId(), request);
        relationshipService.transition(athlete.getId(), response.getId(), CoachClientRelationshipStatus.ACTIVE);
        return relationshipRepository.findById(response.getId()).orElseThrow();
    }

    private WorkoutPlan createPlan(User owner) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name("Status test plan")
                .splitType(WorkoutSplitType.PPL)
                .build();
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            plan.getDays().add(WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(day)
                    .sessionType(WorkoutSessionType.REST)
                    .build());
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
