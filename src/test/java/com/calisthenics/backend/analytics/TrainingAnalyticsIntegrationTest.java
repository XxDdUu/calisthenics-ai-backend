package com.calisthenics.backend.analytics;

import com.calisthenics.backend.analytics.dto.CoachFeedbackRequest;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.model.SharedPersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.repository.SharedPersonalRecordRepository;
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
import com.calisthenics.backend.workout.*;
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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TrainingAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private PersonalRecordRepository prRepository;

    @Autowired
    private SharedPersonalRecordRepository sharedPrRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User clientUser;
    private User coachUser;
    private User otherCoachUser;
    private Exercise pullUp;
    private String clientToken;
    private String coachToken;
    private String otherCoachToken;

    @BeforeEach
    void setUp() {
        clientUser = userRepository.save(User.builder()
                .email("client_analytics@calisthenics.ai")
                .fullName("Analytics Client")
                .role(Role.ROLE_USER)
                .build());

        coachUser = userRepository.save(User.builder()
                .email("coach_analytics@calisthenics.ai")
                .fullName("Analytics Coach")
                .role(Role.ROLE_USER)
                .build());

        otherCoachUser = userRepository.save(User.builder()
                .email("other_coach@calisthenics.ai")
                .fullName("Other Coach")
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
                .code("weighted-pull-up-analytics-" + System.currentTimeMillis())
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .build());

        clientToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(clientUser));
        coachToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(coachUser));
        otherCoachToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(otherCoachUser));
    }

    @Test
    void getOverviewAndWeeklyAnalytics_Success() throws Exception {
        // Create completed session
        WorkoutSession session = sessionRepository.save(WorkoutSession.builder()
                .user(clientUser)
                .title("Upper Body Power")
                .workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.COMPLETED)
                .completedAt(ZonedDateTime.now())
                .durationSeconds(3600L)
                .build());

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorkouts").value(1))
                .andExpect(jsonPath("$.currentStreak").value(1));

        mockMvc.perform(get("/api/v1/analytics/weekly")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekWorkouts").value(1));

        mockMvc.perform(get("/api/v1/analytics/insights")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights").isArray());
    }

    @Test
    void getExerciseProgress_ReturnsDetailsAndCharts() throws Exception {
        WorkoutSession session = WorkoutSession.builder()
                .user(clientUser)
                .title("Pull Focus")
                .workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.COMPLETED)
                .completedAt(ZonedDateTime.now())
                .durationSeconds(2400L)
                .build();

        WorkoutSessionExercise exerciseItem = WorkoutSessionExercise.builder()
                .workoutSession(session)
                .exercise(pullUp)
                .exerciseNameSnapshot("Weighted Pull-up")
                .orderIndex(1)
                .targetSets(3)
                .restSeconds(90)
                .build();

        WorkoutSessionSet set1 = WorkoutSessionSet.builder()
                .workoutSessionExercise(exerciseItem)
                .setNumber(1)
                .weightKg(BigDecimal.valueOf(25.0))
                .reps(8)
                .completed(true)
                .build();

        exerciseItem.setSets(List.of(set1));
        session.setExercises(List.of(exerciseItem));
        sessionRepository.save(session);

        mockMvc.perform(get("/api/v1/exercises/" + pullUp.getId() + "/progress")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseName").value("Weighted Pull-up"))
                .andExpect(jsonPath("$.totalCompletedSessions").value(1))
                .andExpect(jsonPath("$.chartData").isArray());
    }

    @Test
    void clientSharesPRWithCoach_AndCoachLeavesFeedback() throws Exception {
        WorkoutSession session = sessionRepository.save(WorkoutSession.builder()
                .user(clientUser)
                .title("PR Day")
                .workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.COMPLETED)
                .completedAt(ZonedDateTime.now())
                .build());

        PersonalRecord pr = prRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(pullUp)
                .workoutSession(session)
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(30.0))
                .secondaryValue(BigDecimal.valueOf(5))
                .previousRecordValue(BigDecimal.valueOf(27.5))
                .improvementPercentage(BigDecimal.valueOf(9.09))
                .achievedAt(ZonedDateTime.now())
                .build());

        // 1. Client shares PR with Coach
        String shareJson = mockMvc.perform(post("/api/v1/prs/" + pr.getId() + "/share")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(clientUser.getId()))
                .andExpect(jsonPath("$.coachId").value(coachUser.getId()))
                .andReturn().getResponse().getContentAsString();

        Long shareId = objectMapper.readTree(shareJson).get("id").asLong();

        // 2. Client verifies shared PR list
        mockMvc.perform(get("/api/v1/client/shared-prs")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personalRecord.recordValue").value(30.0));

        // 3. Coach views client PRs
        mockMvc.perform(get("/api/v1/coach/clients/" + clientUser.getId() + "/prs")
                        .header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shareId));

        // 4. Unauthorized coach is rejected
        mockMvc.perform(get("/api/v1/coach/clients/" + clientUser.getId() + "/prs")
                        .header("Authorization", "Bearer " + otherCoachToken))
                .andExpect(status().isForbidden());

        // 5. Coach adds feedback
        CoachFeedbackRequest feedbackRequest = new CoachFeedbackRequest("Awesome work on the pull-up PR!");
        mockMvc.perform(post("/api/v1/coach/pr-shares/" + shareId + "/feedback")
                        .header("Authorization", "Bearer " + coachToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coachFeedback").value("Awesome work on the pull-up PR!"));
    }
}
