package com.calisthenics.backend.bodymetrics;

import com.calisthenics.backend.bodymetrics.dto.RecordBodyMeasurementRequest;
import com.calisthenics.backend.bodymetrics.dto.UpdateHeightRequest;
import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSessionExerciseRepository;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodyMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private ClientBodyMeasurementRepository measurementRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private WorkoutPlanAssignmentRepository assignmentRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private WorkoutSessionExerciseRepository workoutSessionExerciseRepository;

    private User clientUser;
    private User assignedCoach;
    private User unassignedCoach;

    @BeforeEach
    void setUp() {
        measurementRepository.deleteAll();
        workoutSessionExerciseRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        assignmentRepository.deleteAll();
        relationshipRepository.deleteAll();
        clientProfileRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        clientUser = userRepository.save(User.builder()
                .email("client_" + suffix + "@test.com")
                .fullName("Test Client " + suffix)
                .role(Role.ROLE_USER)
                .build());

        clientProfileRepository.save(ClientProfile.builder()
                .user(clientUser)
                .displayName("Client Display " + suffix)
                .heightCm(BigDecimal.valueOf(178.5))
                .build());

        assignedCoach = userRepository.save(User.builder()
                .email("coach_assigned_" + suffix + "@test.com")
                .fullName("Assigned Coach " + suffix)
                .role(Role.ROLE_USER)
                .build());

        unassignedCoach = userRepository.save(User.builder()
                .email("coach_unassigned_" + suffix + "@test.com")
                .fullName("Unassigned Coach " + suffix)
                .role(Role.ROLE_USER)
                .build());

        relationshipRepository.save(CoachClientRelationship.builder()
                .client(clientUser)
                .coach(assignedCoach)
                .requestedBy(clientUser)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .version(0L)
                .build());
    }

    private RequestPostProcessor asUser(User u) {
        return user(UserPrincipal.create(u));
    }

    @Test
    void clientCanRecordBodyMeasurement() throws Exception {
        RecordBodyMeasurementRequest request = RecordBodyMeasurementRequest.builder()
                .weightKg(75.5)
                .bodyFatPercentage(15.2)
                .waistCm(82.0)
                .chestCm(98.5)
                .armCm(36.0)
                .thighCm(56.0)
                .measuredAt(ZonedDateTime.now().minusHours(1))
                .build();

        mockMvc.perform(post("/api/v1/clients/me/body-metrics")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.clientId").value(clientUser.getId()))
                .andExpect(jsonPath("$.weightKg").value(75.5))
                .andExpect(jsonPath("$.bodyFatPercentage").value(15.2))
                .andExpect(jsonPath("$.waistCm").value(82.0))
                .andExpect(jsonPath("$.chestCm").value(98.5))
                .andExpect(jsonPath("$.armCm").value(36.0))
                .andExpect(jsonPath("$.thighCm").value(56.0));

        assertThat(measurementRepository.countByClientId(clientUser.getId())).isEqualTo(1);
    }

    @Test
    void clientCanGetTheirCurrentBodyMetrics() throws Exception {
        measurementRepository.save(ClientBodyMeasurement.builder()
                .client(clientUser)
                .weightKg(BigDecimal.valueOf(74.0))
                .bodyFatPercentage(BigDecimal.valueOf(16.0))
                .waistCm(BigDecimal.valueOf(83.0))
                .chestCm(BigDecimal.valueOf(97.0))
                .armCm(BigDecimal.valueOf(35.5))
                .thighCm(BigDecimal.valueOf(55.0))
                .measuredAt(ZonedDateTime.now().minusDays(1))
                .build());

        measurementRepository.save(ClientBodyMeasurement.builder()
                .client(clientUser)
                .weightKg(BigDecimal.valueOf(75.2))
                .bodyFatPercentage(BigDecimal.valueOf(15.5))
                .waistCm(BigDecimal.valueOf(82.5))
                .chestCm(BigDecimal.valueOf(98.0))
                .armCm(BigDecimal.valueOf(36.0))
                .thighCm(BigDecimal.valueOf(55.5))
                .measuredAt(ZonedDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/clients/me/body-metrics/current")
                        .with(asUser(clientUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(178.5))
                .andExpect(jsonPath("$.weightKg").value(75.2))
                .andExpect(jsonPath("$.bodyFatPercentage").value(15.5))
                .andExpect(jsonPath("$.waistCm").value(82.5))
                .andExpect(jsonPath("$.chestCm").value(98.0))
                .andExpect(jsonPath("$.armCm").value(36.0))
                .andExpect(jsonPath("$.thighCm").value(55.5))
                .andExpect(jsonPath("$.totalMeasurementsCount").value(2));
    }

    @Test
    void clientCanUpdateHeightWithoutOverwritingWeightHistory() throws Exception {
        measurementRepository.save(ClientBodyMeasurement.builder()
                .client(clientUser)
                .weightKg(BigDecimal.valueOf(76.0))
                .measuredAt(ZonedDateTime.now().minusDays(2))
                .build());

        UpdateHeightRequest heightRequest = UpdateHeightRequest.builder()
                .heightCm(180.0)
                .build();

        mockMvc.perform(put("/api/v1/clients/me/body-metrics/height")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(heightRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(180.0))
                .andExpect(jsonPath("$.weightKg").value(76.0));

        // Historical measurement count must remain 1 and value preserved
        assertThat(measurementRepository.countByClientId(clientUser.getId())).isEqualTo(1);
        ClientBodyMeasurement history = measurementRepository.findFirstByClientIdOrderByMeasuredAtDesc(clientUser.getId()).orElseThrow();
        assertThat(history.getWeightKg().doubleValue()).isEqualTo(76.0);
    }

    @Test
    void historicalRecordsArePreservedAfterNewMeasurement() throws Exception {
        RecordBodyMeasurementRequest req1 = RecordBodyMeasurementRequest.builder()
                .weightKg(77.0)
                .measuredAt(ZonedDateTime.now().minusDays(3))
                .build();

        RecordBodyMeasurementRequest req2 = RecordBodyMeasurementRequest.builder()
                .weightKg(76.5)
                .measuredAt(ZonedDateTime.now().minusDays(1))
                .build();

        mockMvc.perform(post("/api/v1/clients/me/body-metrics")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clients/me/body-metrics")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        assertThat(measurementRepository.countByClientId(clientUser.getId())).isEqualTo(2);

        mockMvc.perform(get("/api/v1/clients/me/body-metrics/history")
                        .with(asUser(clientUser))
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].weightKg").value(77.0))
                .andExpect(jsonPath("$.content[1].weightKg").value(76.5));
    }

    @Test
    void assignedCoachCanViewClientBodyMetrics() throws Exception {
        measurementRepository.save(ClientBodyMeasurement.builder()
                .client(clientUser)
                .weightKg(BigDecimal.valueOf(74.5))
                .bodyFatPercentage(BigDecimal.valueOf(14.8))
                .measuredAt(ZonedDateTime.now().minusDays(1))
                .build());

        mockMvc.perform(get("/api/v1/coach/clients/" + clientUser.getId() + "/body-metrics")
                        .with(asUser(assignedCoach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientUser.getId()))
                .andExpect(jsonPath("$.currentHeightCm").value(178.5))
                .andExpect(jsonPath("$.currentMetrics.weightKg").value(74.5))
                .andExpect(jsonPath("$.measurements", hasSize(1)));
    }

    @Test
    void unassignedCoachCannotViewClientBodyMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/coach/clients/" + clientUser.getId() + "/body-metrics")
                        .with(asUser(unassignedCoach)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidMeasurementValuesAreRejected() throws Exception {
        // 1. Negative or too small weight
        RecordBodyMeasurementRequest invalidWeight = RecordBodyMeasurementRequest.builder()
                .weightKg(10.0) // min is 20.0
                .measuredAt(ZonedDateTime.now())
                .build();

        mockMvc.perform(post("/api/v1/clients/me/body-metrics")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidWeight)))
                .andExpect(status().isBadRequest());

        // 2. Body fat > 100%
        RecordBodyMeasurementRequest invalidBodyFat = RecordBodyMeasurementRequest.builder()
                .weightKg(70.0)
                .bodyFatPercentage(105.0)
                .measuredAt(ZonedDateTime.now())
                .build();

        mockMvc.perform(post("/api/v1/clients/me/body-metrics")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBodyFat)))
                .andExpect(status().isBadRequest());

        // 3. Future-dated measurement (> 5 min future)
        RecordBodyMeasurementRequest futureDate = RecordBodyMeasurementRequest.builder()
                .weightKg(70.0)
                .measuredAt(ZonedDateTime.now().plusDays(2))
                .build();

        mockMvc.perform(post("/api/v1/clients/me/body-metrics")
                        .with(asUser(clientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(futureDate)))
                .andExpect(status().isBadRequest());
    }
}
