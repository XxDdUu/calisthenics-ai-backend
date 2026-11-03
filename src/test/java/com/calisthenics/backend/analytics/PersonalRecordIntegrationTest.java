package com.calisthenics.backend.analytics;

import com.calisthenics.backend.analytics.dto.CreatePersonalRecordRequest;
import com.calisthenics.backend.analytics.dto.UpdatePersonalRecordRequest;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PersonalRecordIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private PersonalRecordRepository prRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User clientUser;
    private User otherUser;
    private User coachUser;
    private User unassignedCoachUser;
    private Exercise pullUp;
    private Exercise muscleUp;
    private String clientToken;
    private String otherUserToken;
    private String coachToken;
    private String unassignedCoachToken;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        clientUser = userRepository.save(User.builder()
                .email("pr_client_" + ts + "@calisthenics.ai")
                .fullName("PR Client")
                .role(Role.ROLE_USER)
                .build());

        otherUser = userRepository.save(User.builder()
                .email("pr_other_" + ts + "@calisthenics.ai")
                .fullName("Other User")
                .role(Role.ROLE_USER)
                .build());

        coachUser = userRepository.save(User.builder()
                .email("pr_coach_" + ts + "@calisthenics.ai")
                .fullName("Assigned Coach")
                .role(Role.ROLE_USER)
                .build());

        unassignedCoachUser = userRepository.save(User.builder()
                .email("pr_unassigned_coach_" + ts + "@calisthenics.ai")
                .fullName("Unassigned Coach")
                .role(Role.ROLE_USER)
                .build());

        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coachUser)
                .client(clientUser)
                .requestedBy(clientUser)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .version(0L)
                .build());

        pullUp = exerciseRepository.save(Exercise.builder()
                .name("Weighted Pull-up")
                .code("weighted-pull-up-pr-" + ts)
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .build());

        muscleUp = exerciseRepository.save(Exercise.builder()
                .name("Bar Muscle-up")
                .code("bar-muscle-up-pr-" + ts)
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.ADVANCED)
                .build());

        clientToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(clientUser));
        otherUserToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(otherUser));
        coachToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(coachUser));
        unassignedCoachToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(unassignedCoachUser));
    }

    @Test
    void createPR_Success() throws Exception {
        CreatePersonalRecordRequest request = CreatePersonalRecordRequest.builder()
                .exerciseId(pullUp.getId())
                .prType(PersonalRecordType.MAX_WEIGHT)
                .value(BigDecimal.valueOf(40.0))
                .unit("kg")
                .achievedAt(ZonedDateTime.now())
                .notes("Weighted pull-up PR with strict form")
                .build();

        mockMvc.perform(post("/api/analytics/pr")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.exerciseId").value(pullUp.getId()))
                .andExpect(jsonPath("$.exerciseName").value("Weighted Pull-up"))
                .andExpect(jsonPath("$.recordType").value("MAX_WEIGHT"))
                .andExpect(jsonPath("$.recordValue").value(40.0))
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.notes").value("Weighted pull-up PR with strict form"));

        assertThat(prRepository.countByUserId(clientUser.getId())).isEqualTo(1);
    }

    @Test
    void createPR_WithCustomExerciseName_Success() throws Exception {
        CreatePersonalRecordRequest request = CreatePersonalRecordRequest.builder()
                .exerciseName("Full Planche Hold")
                .prType(PersonalRecordType.MAX_HOLD)
                .value(BigDecimal.valueOf(8.0))
                .unit("seconds")
                .achievedAt(ZonedDateTime.now())
                .notes("Clean form, no pike")
                .build();

        mockMvc.perform(post("/api/analytics/pr")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseName").value("Full Planche Hold"))
                .andExpect(jsonPath("$.recordType").value("MAX_HOLD"))
                .andExpect(jsonPath("$.recordValue").value(8.0))
                .andExpect(jsonPath("$.unit").value("seconds"));
    }

    @Test
    void createPR_DuplicatePrevention_ReturnsConflict() throws Exception {
        ZonedDateTime achievedAt = ZonedDateTime.now().minusDays(1);
        CreatePersonalRecordRequest request = CreatePersonalRecordRequest.builder()
                .exerciseId(pullUp.getId())
                .prType(PersonalRecordType.MAX_WEIGHT)
                .value(BigDecimal.valueOf(35.0))
                .unit("kg")
                .achievedAt(achievedAt)
                .build();

        // First submission succeeds
        mockMvc.perform(post("/api/analytics/pr")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Identical duplicate submission fails with 409 Conflict
        mockMvc.perform(post("/api/analytics/pr")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createPR_InvalidValue_ReturnsBadRequest() throws Exception {
        CreatePersonalRecordRequest request = CreatePersonalRecordRequest.builder()
                .exerciseId(pullUp.getId())
                .prType(PersonalRecordType.MAX_WEIGHT)
                .value(BigDecimal.valueOf(-5.0)) // invalid negative value
                .unit("kg")
                .achievedAt(ZonedDateTime.now())
                .build();

        mockMvc.perform(post("/api/analytics/pr")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserPRs_WithFilters_ReturnsMatchingPRs() throws Exception {
        // Create 2 records
        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(30.0))
                .unit("kg")
                .achievedAt(ZonedDateTime.now().minusDays(5))
                .build());

        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(muscleUp)
                .recordType(PersonalRecordType.MAX_REPS)
                .recordValue(BigDecimal.valueOf(10.0))
                .unit("reps")
                .achievedAt(ZonedDateTime.now())
                .build());

        // Filter by exercise
        mockMvc.perform(get("/api/analytics/pr")
                        .param("exerciseId", pullUp.getId().toString())
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recordType").value("MAX_WEIGHT"));

        // Filter by PR type
        mockMvc.perform(get("/api/analytics/pr")
                        .param("prType", "MAX_REPS")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recordValue").value(10.0));
    }

    @Test
    void getPRHistory_ReturnsAscendingProgression() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(25.0))
                .unit("kg")
                .achievedAt(now.minusDays(10))
                .build());

        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(30.0))
                .unit("kg")
                .achievedAt(now.minusDays(5))
                .build());

        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(35.0))
                .unit("kg")
                .achievedAt(now)
                .build());

        mockMvc.perform(get("/api/analytics/pr/" + pullUp.getId() + "/history")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].recordValue").value(25.0))
                .andExpect(jsonPath("$[1].recordValue").value(30.0))
                .andExpect(jsonPath("$[2].recordValue").value(35.0));
    }

    @Test
    void getLatestPR_ReturnsMostRecentBestRecord() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(25.0))
                .unit("kg")
                .achievedAt(now.minusDays(10))
                .build());

        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(40.0))
                .unit("kg")
                .achievedAt(now)
                .build());

        mockMvc.perform(get("/api/analytics/pr/" + pullUp.getId() + "/latest")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordValue").value(40.0));
    }

    @Test
    void updatePR_OwnerCanUpdate_NonOwnerForbidden() throws Exception {
        PersonalRecord pr = prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(30.0))
                .unit("kg")
                .achievedAt(ZonedDateTime.now())
                .notes("Original notes")
                .build());

        UpdatePersonalRecordRequest updateRequest = UpdatePersonalRecordRequest.builder()
                .value(BigDecimal.valueOf(32.5))
                .unit("kg")
                .notes("Updated notes")
                .build();

        // Non-owner cannot update
        mockMvc.perform(put("/api/analytics/pr/" + pr.getId())
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // Owner can update
        mockMvc.perform(put("/api/analytics/pr/" + pr.getId())
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordValue").value(32.5))
                .andExpect(jsonPath("$.notes").value("Updated notes"));
    }

    @Test
    void deletePR_OwnerCanDelete_NonOwnerForbidden() throws Exception {
        PersonalRecord pr = prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(30.0))
                .unit("kg")
                .achievedAt(ZonedDateTime.now())
                .build());

        // Non-owner cannot delete
        mockMvc.perform(delete("/api/analytics/pr/" + pr.getId())
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());

        // Owner can delete
        mockMvc.perform(delete("/api/analytics/pr/" + pr.getId())
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isNoContent());

        assertThat(prRepository.findById(pr.getId())).isEmpty();
    }

    @Test
    void unauthorizedAccess_WithoutToken_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/analytics/pr"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void coachViewClientPRs_AuthorizedVsUnauthorized() throws Exception {
        prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(37.5))
                .unit("kg")
                .achievedAt(ZonedDateTime.now())
                .build());

        // Assigned coach can view client PRs
        mockMvc.perform(get("/api/v1/coach/clients/" + clientUser.getId() + "/personal-records")
                        .header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recordValue").value(37.5));

        // Unassigned coach is forbidden
        mockMvc.perform(get("/api/v1/coach/clients/" + clientUser.getId() + "/personal-records")
                        .header("Authorization", "Bearer " + unassignedCoachToken))
                .andExpect(status().isForbidden());
    }
}

