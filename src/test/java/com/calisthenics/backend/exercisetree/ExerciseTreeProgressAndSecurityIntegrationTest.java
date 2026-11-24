package com.calisthenics.backend.exercisetree;

import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercisetree.dto.ExerciseTreeUserProgressResponse;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeSeederService;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeService;
import com.calisthenics.backend.exercisetree.type.ExerciseNodeProgressStatus;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExerciseTreeProgressAndSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private PersonalRecordRepository personalRecordRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ExerciseTreeSeederService seederService;

    @Autowired
    private ExerciseTreeService exerciseTreeService;

    private User athleteA;
    private User athleteB;
    private Exercise plancheLean;
    private Exercise tuckPlanche;
    private Exercise advTuckPlanche;

    @BeforeEach
    void setUp() {
        athleteA = userRepository.save(User.builder()
                .email("tree-athlete-a@test.com")
                .passwordHash("encoded-hash")
                .fullName("Athlete A")
                .role(Role.ROLE_USER)
                .build());
        athleteB = userRepository.save(User.builder()
                .email("tree-athlete-b@test.com")
                .passwordHash("encoded-hash")
                .fullName("Athlete B")
                .role(Role.ROLE_USER)
                .build());

        plancheLean = saveIfMissing("PLANCHE_LEAN", "Planche Lean", DifficultyLevel.BEGINNER, ExerciseCategory.ISOMETRIC);
        tuckPlanche = saveIfMissing("TUCK_PLANCHE", "Tuck Planche", DifficultyLevel.INTERMEDIATE, ExerciseCategory.SKILL);
        advTuckPlanche = saveIfMissing("ADV_TUCK_PLANCHE", "Advanced Tuck Planche", DifficultyLevel.ADVANCED, ExerciseCategory.SKILL);

        seederService.seedOrSyncCatalog();
    }

    @Test
    void userProgressEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-trees/user-progress"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/exercise-trees/user-progress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void singlePrDoesNotMarkMasteredButQualifyingWorkoutsMasterAndUnlockNextNodeWithUserIsolation() throws Exception {
        // 1. Fresh state: Planche Lean = AVAILABLE, Tuck Planche = LOCKED, Adv Tuck = LOCKED
        ExerciseTreeUserProgressResponse initial = exerciseTreeService.getUserProgress(athleteA.getId());
        assertThat(initial.getProgressByExerciseId().get(plancheLean.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.AVAILABLE);
        assertThat(initial.getProgressByExerciseId().get(tuckPlanche.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.LOCKED);
        assertThat(initial.getProgressByExerciseId().get(advTuckPlanche.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.LOCKED);

        // 2. Record ONE huge PR on Planche Lean -> must be IN_PROGRESS, NEVER MASTERED!
        personalRecordRepository.save(PersonalRecord.builder()
                .user(athleteA)
                .exercise(plancheLean)
                .recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(BigDecimal.valueOf(35))
                .unit("seconds")
                .achievedAt(ZonedDateTime.now())
                .build());

        ExerciseTreeUserProgressResponse afterSinglePr = exerciseTreeService.getUserProgress(athleteA.getId());
        assertThat(afterSinglePr.getProgressByExerciseId().get(plancheLean.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.IN_PROGRESS);
        assertThat(afterSinglePr.getProgressByExerciseId().get(plancheLean.getId()).getPersonalRecordCount())
                .isEqualTo(1);
        assertThat(afterSinglePr.getProgressByExerciseId().get(tuckPlanche.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.LOCKED);

        // 3. Complete 3 qualifying workouts (4x20s Planche Lean holds each) -> MASTERED and unlocks Tuck Planche!
        for (int i = 0; i < 3; i++) {
            createCompletedHoldSession(athleteA, plancheLean, LocalDate.now().minusDays(i + 1), 4, 22);
        }

        UserPrincipal principalA = UserPrincipal.create(athleteA);
        mockMvc.perform(get("/api/v1/exercise-trees/user-progress")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principalA, null, principalA.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(athleteA.getId()))
                .andExpect(jsonPath("$.masteredCount").value(1))
                .andExpect(jsonPath("$.progressByExerciseId['" + plancheLean.getId() + "'].status").value("MASTERED"))
                .andExpect(jsonPath("$.progressByExerciseId['" + tuckPlanche.getId() + "'].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.progressByExerciseId['" + advTuckPlanche.getId() + "'].status").value("LOCKED"));

        // 4. Verify locked nodes remain visible & explorable in tree response for Athlete A
        mockMvc.perform(get("/api/v1/exercise-trees/CALISTHENICS/STATIC_SKILLS")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principalA, null, principalA.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.exerciseId == " + advTuckPlanche.getId() + ")].progress.status").value("LOCKED"));

        // 5. Verify Athlete B's progress is completely isolated from Athlete A
        ExerciseTreeUserProgressResponse progressB = exerciseTreeService.getUserProgress(athleteB.getId());
        assertThat(progressB.getMasteredCount()).isEqualTo(0);
        assertThat(progressB.getProgressByExerciseId().get(plancheLean.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.AVAILABLE);
        assertThat(progressB.getProgressByExerciseId().get(tuckPlanche.getId()).getStatus())
                .isEqualTo(ExerciseNodeProgressStatus.LOCKED);
    }

    private void createCompletedHoldSession(User user, Exercise exercise, LocalDate date, int setsCount, int holdSeconds) {
        WorkoutSession session = WorkoutSession.builder()
                .user(user)
                .title("Planche Prep Session")
                .source(WorkoutSessionSource.LEGACY)
                .workoutDate(date)
                .status(WorkoutSessionStatus.COMPLETED)
                .completedAt(ZonedDateTime.now())
                .build();

        WorkoutSessionExercise wse = WorkoutSessionExercise.builder()
                .workoutSession(session)
                .exercise(exercise)
                .exerciseNameSnapshot(exercise.getName())
                .orderIndex(1)
                .targetSets(setsCount)
                .targetHoldDurationSeconds(holdSeconds)
                .restSeconds(90)
                .build();

        for (int s = 1; s <= setsCount; s++) {
            wse.getSets().add(WorkoutSessionSet.builder()
                    .workoutSessionExercise(wse)
                    .setNumber(s)
                    .holdDurationSeconds(holdSeconds)
                    .completed(true)
                    .completedAt(ZonedDateTime.now())
                    .build());
        }
        session.getExercises().add(wse);
        workoutSessionRepository.saveAndFlush(session);
    }

    private Exercise saveIfMissing(String code, String name, DifficultyLevel difficulty, ExerciseCategory category) {
        return exerciseRepository.findByCode(code).orElseGet(() -> exerciseRepository.save(Exercise.builder()
                .code(code)
                .name(name)
                .trainingType(ExerciseTrainingType.CALISTHENICS)
                .category(category)
                .difficulty(difficulty)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .targetMuscles("Front Deltoids, Core")
                .build()));
    }
}
