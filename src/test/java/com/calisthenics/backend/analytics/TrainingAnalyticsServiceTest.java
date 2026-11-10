package com.calisthenics.backend.analytics;

import com.calisthenics.backend.analytics.dto.*;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.analytics.service.TrainingAnalyticsService;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workout.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TrainingAnalyticsServiceTest {

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private PersonalRecordRepository personalRecordRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private CoachClientRelationshipRepository coachClientRelationshipRepository;

    @Mock
    private PersonalRecordService personalRecordService;

    private TrainingAnalyticsService analyticsService;

    private User athlete;
    private Exercise pullUp;
    private Exercise plancheHold;

    @BeforeEach
    void setUp() {
        analyticsService = new TrainingAnalyticsService(
                workoutSessionRepository,
                personalRecordRepository,
                exerciseRepository,
                coachClientRelationshipRepository,
                personalRecordService,
                new UserTimeResolver(new AppProperties())
        );
        athlete = User.builder().id(1L).email("athlete@calisthenics.ai").fullName("Athlete One").role(Role.ROLE_USER).build();
        pullUp = Exercise.builder().id(10L).name("Weighted Pull-up").code("weighted-pull-up")
                .category(ExerciseCategory.PULL).difficulty(DifficultyLevel.INTERMEDIATE).build();
        plancheHold = Exercise.builder().id(20L).name("Planche Hold").code("planche-hold")
                .category(ExerciseCategory.ISOMETRIC).difficulty(DifficultyLevel.ADVANCED).build();
    }

    @Test
    void getTrainingOverview_CalculatesStreaksAndConsistencyCorrectly() {
        LocalDate today = LocalDate.now();
        List<LocalDate> distinctDates = List.of(
                today,
                today.minusDays(1),
                today.minusDays(2), // 3-day current streak
                today.minusDays(5),
                today.minusDays(6),
                today.minusDays(7),
                today.minusDays(8)  // 4-day longest streak earlier
        );

        when(workoutSessionRepository.findDistinctCompletedWorkoutDates(1L, WorkoutSessionStatus.COMPLETED))
                .thenReturn(distinctDates);
        when(workoutSessionRepository.countByUserIdAndStatus(1L, WorkoutSessionStatus.COMPLETED)).thenReturn(10L);
        when(personalRecordRepository.countByUserId(1L)).thenReturn(5L);
        when(personalRecordRepository.findByUserIdOrderByAchievedAtDesc(1L)).thenReturn(Collections.emptyList());

        TrainingOverviewResponse overview = analyticsService.getTrainingOverview(1L);

        assertThat(overview.getTotalWorkouts()).isEqualTo(10);
        assertThat(overview.getTotalActiveDays()).isEqualTo(7);
        assertThat(overview.getCurrentStreak()).isEqualTo(3);
        assertThat(overview.getLongestStreak()).isEqualTo(4);
        assertThat(overview.getTotalPRs()).isEqualTo(5);
    }

    @Test
    void getExerciseProgress_CalculatesProgressiveOverloadComparison() {
        LocalDate date1 = LocalDate.of(2026, 10, 15);
        LocalDate date2 = LocalDate.of(2026, 10, 22);

        // Previous session: +25 kg x 8 reps
        WorkoutSession prevSession = WorkoutSession.builder()
                .id(101L).user(athlete).workoutDate(date1).status(WorkoutSessionStatus.COMPLETED)
                .completedAt(date1.atStartOfDay(java.time.ZoneId.systemDefault()))
                .build();
        WorkoutSessionExercise prevEx = WorkoutSessionExercise.builder()
                .id(201L).workoutSession(prevSession).exercise(pullUp).exerciseNameSnapshot("Weighted Pull-up")
                .orderIndex(1).targetSets(3).restSeconds(90).build();
        WorkoutSessionSet prevSet = WorkoutSessionSet.builder()
                .id(301L).workoutSessionExercise(prevEx).setNumber(1).reps(8).weightKg(BigDecimal.valueOf(25.0))
                .completed(true).build();
        prevEx.setSets(List.of(prevSet));
        prevSession.setExercises(List.of(prevEx));

        // Latest session: +27.5 kg x 8 reps (+10% external load improvement)
        WorkoutSession latestSession = WorkoutSession.builder()
                .id(102L).user(athlete).workoutDate(date2).status(WorkoutSessionStatus.COMPLETED)
                .completedAt(date2.atStartOfDay(java.time.ZoneId.systemDefault()))
                .build();
        WorkoutSessionExercise latestEx = WorkoutSessionExercise.builder()
                .id(202L).workoutSession(latestSession).exercise(pullUp).exerciseNameSnapshot("Weighted Pull-up")
                .orderIndex(1).targetSets(3).restSeconds(90).build();
        WorkoutSessionSet latestSet = WorkoutSessionSet.builder()
                .id(302L).workoutSessionExercise(latestEx).setNumber(1).reps(8).weightKg(BigDecimal.valueOf(27.5))
                .completed(true).build();
        latestEx.setSets(List.of(latestSet));
        latestSession.setExercises(List.of(latestEx));

        when(exerciseRepository.findById(10L)).thenReturn(Optional.of(pullUp));
        when(workoutSessionRepository.findCompletedSessionsByExercise(1L, WorkoutSessionStatus.COMPLETED, 10L))
                .thenReturn(List.of(latestSession, prevSession));
        when(personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedAtDesc(1L, 10L))
                .thenReturn(Collections.emptyList());

        ExerciseProgressResponse response = analyticsService.getExerciseProgress(1L, 10L, null);

        assertThat(response.getExerciseName()).isEqualTo("Weighted Pull-up");
        assertThat(response.getTotalCompletedSessions()).isEqualTo(2);
        assertThat(response.getLatestVsPrevious()).isNotNull();
        assertThat(response.getLatestVsPrevious().isHasComparison()).isTrue();
        assertThat(response.getLatestVsPrevious().getWeightDiff()).isEqualByComparingTo(BigDecimal.valueOf(2.5));
        assertThat(response.getLatestVsPrevious().getWeightPercentage()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(response.getChartData()).hasSize(2);
    }

    @Test
    void getTrainingInsights_ReturnsInsufficientDataGuidanceWhenFewerThanTwoSessions() {
        when(workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(1L, WorkoutSessionStatus.COMPLETED))
                .thenReturn(List.of(WorkoutSession.builder().id(1L).status(WorkoutSessionStatus.COMPLETED).build()));

        TrainingInsightsResponse insights = analyticsService.getTrainingInsights(1L);

        assertThat(insights.isHasSufficientData()).isFalse();
        assertThat(insights.getInsights().getFirst()).contains("Log at least 2 completed workout sessions");
    }

    @Test
    void getCoachClientOverview_EnforcesAuthorization() {
        when(coachClientRelationshipRepository.findActiveRelationshipForUsers(99L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> analyticsService.getCoachClientOverview(99L, 1L));
    }
}

