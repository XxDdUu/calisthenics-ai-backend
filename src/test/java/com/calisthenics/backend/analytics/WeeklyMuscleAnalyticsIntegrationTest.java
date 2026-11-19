package com.calisthenics.backend.analytics;

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
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WeeklyMuscleAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void muscleMapEndpointsReturnWeeklyBreakdownAndEnforceCoachAccess() throws Exception {
        User client = userRepository.save(User.builder()
                .email("muscle_client@calisthenics.ai")
                .fullName("Muscle Client")
                .role(Role.ROLE_USER)
                .build());
        User coach = userRepository.save(User.builder()
                .email("muscle_coach@calisthenics.ai")
                .fullName("Muscle Coach")
                .role(Role.ROLE_USER)
                .build());
        User otherCoach = userRepository.save(User.builder()
                .email("muscle_other_coach@calisthenics.ai")
                .fullName("Other Coach")
                .role(Role.ROLE_USER)
                .build());

        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coach)
                .client(client)
                .requestedBy(client)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .version(0L)
                .build());

        Exercise dips = exerciseRepository.save(Exercise.builder()
                .name("Parallel Bar Dips")
                .code("DIPS")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .description("Bodyweight dip")
                .instructions("Lower under control")
                .build());

        WorkoutSession session = WorkoutSession.builder()
                .user(client)
                .title("Push Workout")
                .workoutDate(LocalDate.now(ZoneOffset.UTC))
                .status(WorkoutSessionStatus.COMPLETED)
                .completedAt(ZonedDateTime.now(ZoneOffset.UTC))
                .build();
        WorkoutSessionExercise exItem = WorkoutSessionExercise.builder()
                .workoutSession(session)
                .exercise(dips)
                .exerciseNameSnapshot("Parallel Bar Dips")
                .orderIndex(1)
                .targetSets(3)
                .restSeconds(90)
                .build();
        exItem.setSets(List.of(
                WorkoutSessionSet.builder().workoutSessionExercise(exItem).setNumber(1).reps(10).completed(true).build(),
                WorkoutSessionSet.builder().workoutSessionExercise(exItem).setNumber(2).reps(10).completed(true).build(),
                WorkoutSessionSet.builder().workoutSessionExercise(exItem).setNumber(3).reps(8).completed(false).build()
        ));
        session.setExercises(List.of(exItem));
        sessionRepository.save(session);

        String clientToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(client));
        String coachToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(coach));
        String otherCoachToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(otherCoach));

        mockMvc.perform(get("/api/v1/analytics/muscle-map")
                        .param("weekOffset", "0")
                        .header("X-Timezone", "UTC")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedWorkouts").value(1))
                .andExpect(jsonPath("$.totalCompletedSets").value(2))
                .andExpect(jsonPath("$.muscles.length()").value(22))
                .andExpect(jsonPath("$.muscles[0].muscleId").value("chest"))
                .andExpect(jsonPath("$.muscles[0].effectiveSets").value(2.0))
                .andExpect(jsonPath("$.muscles[1].muscleId").value("front_deltoids"))
                .andExpect(jsonPath("$.muscles[1].effectiveSets").value(1.0))
                .andExpect(jsonPath("$.muscles[5].muscleId").value("triceps"))
                .andExpect(jsonPath("$.muscles[5].effectiveSets").value(2.0));

        mockMvc.perform(get("/api/v1/analytics/weekly")
                        .header("X-Timezone", "UTC")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muscleMap.totalCompletedSets").value(2))
                .andExpect(jsonPath("$.muscleMap.muscles.length()").value(22));

        mockMvc.perform(get("/api/v1/exercises/" + dips.getId() + "/progress")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("BEGINNER"))
                .andExpect(jsonPath("$.description").value("Bodyweight dip"))
                .andExpect(jsonPath("$.primaryMuscleIds[0]").value("triceps"))
                .andExpect(jsonPath("$.primaryMuscleIds[1]").value("chest"))
                .andExpect(jsonPath("$.secondaryMuscleIds[0]").value("front_deltoids"));

        mockMvc.perform(get("/api/v1/coach/clients/" + client.getId() + "/analytics/muscle-map")
                        .header("X-Timezone", "UTC")
                        .header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedSets").value(2));

        mockMvc.perform(get("/api/v1/coach/clients/" + client.getId() + "/analytics/muscle-map")
                        .header("Authorization", "Bearer " + otherCoachToken))
                .andExpect(status().isForbidden());
    }
}
