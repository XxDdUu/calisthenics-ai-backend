package com.calisthenics.backend.coachclient;

import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.exception.CoachClientConflictException;
import com.calisthenics.backend.coachclient.exception.CoachClientForbiddenException;
import com.calisthenics.backend.coachclient.exception.CoachClientRelationshipNotFoundException;
import com.calisthenics.backend.coachclient.exception.WorkoutPlanAssignmentConflictException;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachClientIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void regularAuthenticatedUsersCanCreateAndListRelationshipRequests() throws Exception {
        User coach = createUser();
        User client = createUser();

        String response = mockMvc.perform(post("/api/v1/coach-client-relationships")
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":" + client.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.coachId").value(coach.getId()))
                .andExpect(jsonPath("$.clientId").value(client.getId()))
                .andReturn().getResponse().getContentAsString();

        Long relationshipId = objectMapper.readTree(response).get("id").asLong();
        mockMvc.perform(get("/api/v1/coach-client-relationships").with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(relationshipId));
        assertThat(coach.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(client.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    void clientCanDiscoverCoachesRequestConnectionAndCoachCanAcceptOrReject() throws Exception {
        User client = createUser();
        User coach = createUser();
        User secondClient = createUser();

        mockMvc.perform(get("/api/v1/coaches").with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + client.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + coach.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.email)]").doesNotExist());
        mockMvc.perform(get("/api/v1/coaches"))
                .andExpect(status().isUnauthorized());

        String created = mockMvc.perform(post("/api/v1/coach-client-relationships/requests")
                        .with(authenticatedAs(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":" + coach.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.coachId").value(coach.getId()))
                .andExpect(jsonPath("$.clientId").value(client.getId()))
                .andExpect(jsonPath("$.requestedBy").value(client.getId()))
                .andReturn().getResponse().getContentAsString();

        Long relationshipId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/v1/coach-client-relationships/" + relationshipId + "/accept")
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String secondCreated = mockMvc.perform(post("/api/v1/coach-client-relationships/requests")
                        .with(authenticatedAs(secondClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":" + coach.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long secondRelationshipId = objectMapper.readTree(secondCreated).get("id").asLong();
        mockMvc.perform(post("/api/v1/coach-client-relationships/" + secondRelationshipId + "/reject")
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        User cancelingClient = createUser();
        String cancellationRequest = mockMvc.perform(
                        post("/api/v1/coach-client-relationships/requests")
                                .with(authenticatedAs(cancelingClient))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"coachId\":" + coach.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cancellationId = objectMapper.readTree(cancellationRequest).get("id").asLong();
        mockMvc.perform(post("/api/v1/coach-client-relationships/" + cancellationId + "/cancel")
                        .with(authenticatedAs(cancelingClient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/coach-client-relationships"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void coachCanManageMultipleClientsAndClientCanHaveOnlyOneActiveCoach() {
        User coach = createUser();
        User clientOne = createUser();
        User clientTwo = createUser();
        User otherCoach = createUser();

        CoachClientRelationship first = request(coach, clientOne);
        CoachClientRelationship secondClient = request(coach, clientTwo);
        accept(clientOne, first);
        accept(clientTwo, secondClient);
        assertThat(relationshipRepository.findByCoachIdOrClientIdOrderByCreatedAtDesc(
                coach.getId(), coach.getId()).stream()
                .filter(row -> row.getStatus() == CoachClientRelationshipStatus.ACTIVE))
                .hasSize(2);

        CoachClientRelationship secondCoachRequest = request(otherCoach, clientOne);
        assertThatThrownBy(() -> accept(clientOne, secondCoachRequest))
                .isInstanceOf(CoachClientConflictException.class);
        assertThat(relationshipRepository.existsByClientIdAndStatus(
                clientOne.getId(), CoachClientRelationshipStatus.ACTIVE)).isTrue();

        relationshipService.transition(
                clientOne.getId(), first.getId(), CoachClientRelationshipStatus.CANCELLED);
        accept(clientOne, secondCoachRequest);
        assertThat(relationshipRepository.findById(secondCoachRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(CoachClientRelationshipStatus.ACTIVE);
    }

    @Test
    void enforcesRelationshipTransitionsAndPreventsDuplicatePendingRequests() {
        User coach = createUser();
        User client = createUser();
        User stranger = createUser();
        CoachClientRelationship relationship = request(coach, client);

        assertThatThrownBy(() -> relationshipService.request(coach.getId(), requestBody(client)))
                .isInstanceOf(CoachClientConflictException.class);
        assertThatThrownBy(() -> relationshipService.transition(
                coach.getId(), relationship.getId(), CoachClientRelationshipStatus.ACTIVE))
                .isInstanceOf(CoachClientForbiddenException.class);
        assertThatThrownBy(() -> relationshipService.transition(
                stranger.getId(), relationship.getId(), CoachClientRelationshipStatus.REJECTED))
                .isInstanceOf(CoachClientRelationshipNotFoundException.class);
        assertThatThrownBy(() -> relationshipService.transition(
                client.getId(), relationship.getId(), CoachClientRelationshipStatus.CANCELLED))
                .isInstanceOf(CoachClientForbiddenException.class);

        relationshipService.transition(
                coach.getId(), relationship.getId(), CoachClientRelationshipStatus.CANCELLED);
        assertThat(relationshipRepository.findById(relationship.getId()).orElseThrow().getStatus())
                .isEqualTo(CoachClientRelationshipStatus.CANCELLED);

        CoachClientRelationship retry = request(coach, client);
        assertThat(retry.getStatus()).isEqualTo(CoachClientRelationshipStatus.PENDING);
        relationshipService.transition(
                client.getId(), retry.getId(), CoachClientRelationshipStatus.REJECTED);
        assertThat(relationshipRepository.findById(retry.getId()).orElseThrow().getStatus())
                .isEqualTo(CoachClientRelationshipStatus.REJECTED);
    }

    @Test
    void concurrentAcceptanceCannotGiveClientTwoActiveCoaches() throws Exception {
        User client = createUser();
        CoachClientRelationship first = request(createUser(), client);
        CoachClientRelationship second = request(createUser(), client);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> acceptFirst = executor.submit(
                    () -> attemptAccept(client.getId(), first.getId(), ready, start));
            Future<Boolean> acceptSecond = executor.submit(
                    () -> attemptAccept(client.getId(), second.getId(), ready, start));
            ready.await();
            start.countDown();
            int succeeded = (acceptFirst.get() ? 1 : 0) + (acceptSecond.get() ? 1 : 0);
            assertThat(succeeded).isEqualTo(1);
            assertThat(relationshipRepository.findByCoachIdOrClientIdOrderByCreatedAtDesc(
                    client.getId(), client.getId()).stream()
                    .filter(row -> row.getStatus() == CoachClientRelationshipStatus.ACTIVE))
                    .hasSize(1);
        }
    }

    @Test
    void onlyActiveRelationshipAndCoachOwnedPlanCanBeAssignedAndHistorySurvivesTermination() {
        User coach = createUser();
        User client = createUser();
        User unrelated = createUser();
        WorkoutPlan plan = createPlan(coach);
        WorkoutPlanAssignmentRequestBody assignmentRequest = new WorkoutPlanAssignmentRequestBody(plan.getId());
        CoachClientRelationship relationship = request(coach, client);

        assertThatThrownBy(() -> assignmentService.assign(
                coach.getId(), relationship.getId(), assignmentRequest.toRequest()))
                .isInstanceOf(WorkoutPlanAssignmentConflictException.class);

        accept(client, relationship);
        WorkoutPlanAssignmentResponse assignment = assignmentService.assign(
                coach.getId(), relationship.getId(), assignmentRequest.toRequest());

        assertThat(assignmentService.listForClient(client.getId()))
                .extracting(WorkoutPlanAssignmentResponse::getId)
                .containsExactly(assignment.getId());
        assertThat(assignmentService.listForClient(unrelated.getId())).isEmpty();
        assertThatThrownBy(() -> assignmentService.listForRelationship(unrelated.getId(), relationship.getId()))
                .isInstanceOf(CoachClientRelationshipNotFoundException.class);

        relationshipService.transition(
                client.getId(), relationship.getId(), CoachClientRelationshipStatus.CANCELLED);
        assertThat(assignmentRepository.findById(assignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.ENDED);
        assertThat(assignmentService.listForClient(client.getId()))
                .extracting(WorkoutPlanAssignmentResponse::getId)
                .containsExactly(assignment.getId());
    }

    @Test
    void coachCannotAssignAnotherUsersPlan() {
        User coach = createUser();
        User client = createUser();
        User otherUser = createUser();
        CoachClientRelationship relationship = request(coach, client);
        accept(client, relationship);
        WorkoutPlan privatePlan = createPlan(otherUser);
        CreateWorkoutPlanAssignmentRequest request = new CreateWorkoutPlanAssignmentRequest();
        request.setWorkoutPlanId(privatePlan.getId());

        assertThatThrownBy(() -> assignmentService.assign(coach.getId(), relationship.getId(), request))
                .isInstanceOf(com.calisthenics.backend.workoutplan.exception.WorkoutPlanNotFoundException.class);
    }

    private boolean attemptAccept(
            Long clientId, Long relationshipId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            relationshipService.transition(clientId, relationshipId, CoachClientRelationshipStatus.ACTIVE);
            return true;
        } catch (CoachClientConflictException ex) {
            return false;
        }
    }

    private CoachClientRelationship request(User coach, User client) {
        return relationshipRepository.findById(relationshipService.request(
                coach.getId(), requestBody(client)).getId()).orElseThrow();
    }

    private void accept(User client, CoachClientRelationship relationship) {
        relationshipService.transition(
                client.getId(), relationship.getId(), CoachClientRelationshipStatus.ACTIVE);
    }

    private CreateCoachClientRequest requestBody(User client) {
        CreateCoachClientRequest request = new CreateCoachClientRequest();
        request.setClientId(client.getId());
        return request;
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .email(suffix + "@example.com")
                .fullName("Test user")
                .role(Role.ROLE_USER)
                .build());
    }

    private WorkoutPlan createPlan(User owner) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name("Shared weekly plan")
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

    private RequestPostProcessor authenticatedAs(User user) {
        return user(UserPrincipal.create(user));
    }

    private record WorkoutPlanAssignmentRequestBody(Long planId) {
        private CreateWorkoutPlanAssignmentRequest toRequest() {
            CreateWorkoutPlanAssignmentRequest request = new CreateWorkoutPlanAssignmentRequest();
            request.setWorkoutPlanId(planId);
            request.setNotes("Assigned for training");
            return request;
        }
    }
}
