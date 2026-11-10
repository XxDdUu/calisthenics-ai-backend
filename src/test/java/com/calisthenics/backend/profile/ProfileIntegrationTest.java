package com.calisthenics.backend.profile;

import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.profile.dto.request.ClientProfileRequest;
import com.calisthenics.backend.profile.dto.request.CoachProfileRequest;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private WorkoutPlanAssignmentRepository assignmentRepository;

    @Autowired
    private ClientBodyMeasurementRepository measurementRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateAndGetCoachProfile_Success() throws Exception {
        User coach = createUser("Coach Alpha");

        CoachProfileRequest request = CoachProfileRequest.builder()
                .displayName("Coach Alpha Master")
                .bio("Specialist in planche and front lever")
                .specialties("Planche, Front Lever, Handstand")
                .yearsExperience(6)
                .trainingPhilosophy("Quality over quantity and strict form")
                .coachingDescription("Personalized 1-on-1 calisthenics coaching")
                .languages("English, Spanish")
                .certifications("NASM-CPT, WSWCF Level 2")
                .socialLinks("https://instagram.com/coachalpha")
                .isPublic(true)
                .isAcceptingClients(true)
                .build();

        mockMvc.perform(put("/api/v1/coaches/me")
                        .with(principal(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Coach Alpha Master"))
                .andExpect(jsonPath("$.bio").value("Specialist in planche and front lever"))
                .andExpect(jsonPath("$.specialties").value("Planche, Front Lever, Handstand"))
                .andExpect(jsonPath("$.yearsExperience").value(6))
                .andExpect(jsonPath("$.activeClientsCount").value(0));

        mockMvc.perform(get("/api/v1/coaches/me")
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Coach Alpha Master"))
                .andExpect(jsonPath("$.yearsExperience").value(6));
    }

    @Test
    void testPublicCoachProfile_WithRealStatistics() throws Exception {
        User coach = createUser("Coach Stats");
        User client1 = createUser("Client One");
        User client2 = createUser("Client Two");

        // Active connection with client1
        CoachClientRelationship activeRel = relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(client1)
                .requestedBy(client1)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        // Cancelled connection with client2
        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(client2)
                .requestedBy(coach)
                .status(CoachClientRelationshipStatus.CANCELLED)
                .build());

        // Create a workout plan and assign to client1
        WorkoutPlan plan = createWorkoutPlan(coach, "Plan A");
        assignmentRepository.save(WorkoutPlanAssignment.builder()
                .relationship(activeRel)
                .workoutPlan(plan)
                .coach(coach)
                .client(client1)
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(LocalDate.now())
                .build());

        // Query public coach profile as client1
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId())
                        .with(principal(client1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(coach.getId()))
                .andExpect(jsonPath("$.activeClientsCount").value(1))
                .andExpect(jsonPath("$.completedClientsCount").value(1))
                .andExpect(jsonPath("$.assignedPlansCount").value(1))
                .andExpect(jsonPath("$.connectionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.relationshipId").value(activeRel.getId()));
    }

    @Test
    void testCreateAndGetClientProfile_Success() throws Exception {
        User client = createUser("Athlete Delta");

        ClientProfileRequest request = ClientProfileRequest.builder()
                .displayName("Delta Calisthenics")
                .bio("Road to full planche")
                .experienceLevel("INTERMEDIATE")
                .trainingExperience("2 years bodyweight training")
                .primaryGoals("Full Planche, Muscle-Up 10 reps")
                .trainingInterests("Statics, Weighted Calisthenics")
                .preferredFrequency("4 days per week")
                .coachingLanguage("English")
                .build();

        mockMvc.perform(put("/api/v1/clients/me")
                        .with(principal(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Delta Calisthenics"))
                .andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.primaryGoals").value("Full Planche, Muscle-Up 10 reps"))
                .andExpect(jsonPath("$.preferredFrequency").value("4 days per week"));

        mockMvc.perform(get("/api/v1/clients/me")
                        .with(principal(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Delta Calisthenics"))
                .andExpect(jsonPath("$.experienceLevel").value("INTERMEDIATE"));
    }

    @Test
    void testClientTrainingOverview_Success() throws Exception {
        User client = createUser("Athlete Overview");

        // Create a workout plan for client
        WorkoutPlan plan = createWorkoutPlan(client, "Strength Plan");

        // Create completed workout session
        workoutSessionRepository.save(WorkoutSession.builder()
                .user(client)
                .title("Chest & Triceps")
                .startedAt(ZonedDateTime.now().minusHours(2))
                .completedAt(ZonedDateTime.now().minusHours(1))
                .build());

        mockMvc.perform(get("/api/v1/clients/me/overview")
                        .with(principal(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(client.getId()))
                .andExpect(jsonPath("$.activePlansCount").value(1))
                .andExpect(jsonPath("$.completedSessionsCount").value(1))
                .andExpect(jsonPath("$.currentPlanName").value("Strength Plan"))
                .andExpect(jsonPath("$.recentWorkouts[0].title").value("Chest & Triceps"));
    }

    @Test
    void testAuthorizedCoach_CanAccessConnectedClientProfileAndOverview() throws Exception {
        User coach = createUser("Coach Authorized");
        User client = createUser("Client Supervised");

        // Active connection
        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(client)
                .requestedBy(client)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        // Coach can view client profile
        mockMvc.perform(get("/api/v1/clients/" + client.getId())
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(client.getId()));

        // Coach can view client overview
        mockMvc.perform(get("/api/v1/clients/" + client.getId() + "/overview")
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(client.getId()));
    }

    @Test
    void testUnauthorizedUserOrCoach_CannotAccessClientProfile_ReturnsForbidden() throws Exception {
        User stranger = createUser("Stranger Danger");
        User client = createUser("Private Client");

        // Stranger has no active relationship with client
        mockMvc.perform(get("/api/v1/clients/" + client.getId())
                        .with(principal(stranger)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/clients/" + client.getId() + "/overview")
                        .with(principal(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAuthorizedCoach_CanViewAssignedAthleteHeightAndWeight_Success() throws Exception {
        User coach = createUser("Coach BodyMetrics");
        User athlete = createUser("Athlete Fit");

        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(athlete)
                .requestedBy(athlete)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        // Athlete sets their height and weight
        ClientProfileRequest request = ClientProfileRequest.builder()
                .displayName("Athlete Fit")
                .heightCm(177.0)
                .weightKg(76.0)
                .build();

        mockMvc.perform(put("/api/v1/clients/me")
                        .with(principal(athlete))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(177.0))
                .andExpect(jsonPath("$.weightKg").value(76.0));

        // Authorized coach views the athlete's profile
        mockMvc.perform(get("/api/v1/clients/" + athlete.getId())
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(athlete.getId()))
                .andExpect(jsonPath("$.heightCm").value(177.0))
                .andExpect(jsonPath("$.weightKg").value(76.0));
    }

    @Test
    void testAuthorizedCoach_AthleteHasNoHeightOrWeight_ReturnsNullMetrics() throws Exception {
        User coach = createUser("Coach EmptyMetrics");
        User athlete = createUser("Athlete Incomplete");

        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(athlete)
                .requestedBy(athlete)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        // Coach views athlete profile before any metrics are entered
        mockMvc.perform(get("/api/v1/clients/" + athlete.getId())
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(athlete.getId()))
                .andExpect(jsonPath("$.heightCm").value(nullValue()))
                .andExpect(jsonPath("$.weightKg").value(nullValue()));
    }

    @Test
    void testAthleteUpdatesMetrics_AppearsCorrectlyInCoachView() throws Exception {
        User coach = createUser("Coach Dynamic");
        User athlete = createUser("Athlete Growing");

        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(athlete)
                .requestedBy(athlete)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        // Initial metrics
        ClientProfileRequest initialRequest = ClientProfileRequest.builder()
                .heightCm(170.0)
                .weightKg(68.0)
                .build();
        mockMvc.perform(put("/api/v1/clients/me")
                        .with(principal(athlete))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isOk());

        // Verify coach sees initial metrics
        mockMvc.perform(get("/api/v1/clients/" + athlete.getId())
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(170.0))
                .andExpect(jsonPath("$.weightKg").value(68.0));

        // Athlete updates their height and weight
        ClientProfileRequest updatedRequest = ClientProfileRequest.builder()
                .heightCm(172.5)
                .weightKg(71.5)
                .build();
        mockMvc.perform(put("/api/v1/clients/me")
                        .with(principal(athlete))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(172.5))
                .andExpect(jsonPath("$.weightKg").value(71.5));

        // Coach views again - receives updated metrics
        mockMvc.perform(get("/api/v1/clients/" + athlete.getId())
                        .with(principal(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(172.5))
                .andExpect(jsonPath("$.weightKg").value(71.5));
    }

    @Test
    void testCoachProfileValidation_YearsExperienceCannotBeNegative() throws Exception {
        User coach = createUser("Invalid Coach");

        CoachProfileRequest request = CoachProfileRequest.builder()
                .yearsExperience(-2)
                .build();

        mockMvc.perform(put("/api/v1/coaches/me")
                        .with(principal(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEmptyProfileState_ReturnsDefaultValues() throws Exception {
        User user = createUser("Fresh User");

        // When getting profile before explicitly saving one, returns valid defaults based on User entity
        mockMvc.perform(get("/api/v1/clients/me")
                        .with(principal(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Fresh User"))
                .andExpect(jsonPath("$.activePlansCount").value(0))
                .andExpect(jsonPath("$.completedSessionsCount").value(0));

        mockMvc.perform(get("/api/v1/coaches/me")
                        .with(principal(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Fresh User"))
                .andExpect(jsonPath("$.activeClientsCount").value(0));
    }

    private User createUser(String name) {
        return userRepository.save(User.builder()
                .email(UUID.randomUUID() + "@example.com")
                .fullName(name)
                .passwordHash("hashed")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build());
    }

    private WorkoutPlan createWorkoutPlan(User user, String name) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(user)
                .name(name)
                .splitType(WorkoutSplitType.PPL)
                .build();

        List<WorkoutPlanDay> days = List.of(
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.MONDAY).sessionType(WorkoutSessionType.PUSH).build(),
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.TUESDAY).sessionType(WorkoutSessionType.PULL).build(),
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.WEDNESDAY).sessionType(WorkoutSessionType.LEGS).build(),
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.THURSDAY).sessionType(WorkoutSessionType.PUSH).build(),
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.FRIDAY).sessionType(WorkoutSessionType.PULL).build(),
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.SATURDAY).sessionType(WorkoutSessionType.LEGS).build(),
                WorkoutPlanDay.builder().workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.SUNDAY).sessionType(WorkoutSessionType.REST).build()
        );
        plan.setDays(days);
        return workoutPlanRepository.save(plan);
    }

    private RequestPostProcessor principal(User user) {
        return user(UserPrincipal.create(user));
    }
}
