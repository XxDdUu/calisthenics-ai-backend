package com.calisthenics.backend.analytics;

import com.calisthenics.backend.analytics.dto.PersonalRecordResponse;
import com.calisthenics.backend.analytics.dto.SharedPersonalRecordResponse;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.model.SharedPersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.repository.SharedPersonalRecordRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PersonalRecordServiceTest {

    @Mock
    private PersonalRecordRepository personalRecordRepository;

    @Mock
    private SharedPersonalRecordRepository sharedPersonalRecordRepository;

    @Mock
    private CoachClientRelationshipRepository coachClientRelationshipRepository;

    @InjectMocks
    private PersonalRecordService personalRecordService;

    private User athlete;
    private User coach;
    private Exercise weightedPullUp;
    private Exercise plancheHold;

    @BeforeEach
    void setUp() {
        athlete = User.builder().id(1L).email("athlete@calisthenics.ai").fullName("Athlete One").role(Role.ROLE_USER).build();
        coach = User.builder().id(2L).email("coach@calisthenics.ai").fullName("Coach Dave").role(Role.ROLE_USER).build();

        weightedPullUp = Exercise.builder().id(10L).name("Weighted Pull-up").code("weighted-pull-up")
                .category(ExerciseCategory.PULL).difficulty(DifficultyLevel.INTERMEDIATE).build();

        plancheHold = Exercise.builder().id(20L).name("Planche Hold").code("planche-hold")
                .category(ExerciseCategory.ISOMETRIC).difficulty(DifficultyLevel.ADVANCED).build();
    }

    @Test
    void detectAndSavePRs_DetectsMaxWeightAndMaxRepsPR() {
        WorkoutSession session = WorkoutSession.builder()
                .id(100L).user(athlete).status(WorkoutSessionStatus.COMPLETED)
                .workoutDate(LocalDate.now()).completedAt(ZonedDateTime.now())
                .build();

        WorkoutSessionExercise exerciseItem = WorkoutSessionExercise.builder()
                .id(200L).workoutSession(session).exercise(weightedPullUp).exerciseNameSnapshot("Weighted Pull-up")
                .orderIndex(1).targetSets(3).restSeconds(90)
                .build();

        WorkoutSessionSet set1 = WorkoutSessionSet.builder()
                .id(301L).workoutSessionExercise(exerciseItem).setNumber(1)
                .weightKg(BigDecimal.valueOf(27.5)).reps(8).completed(true)
                .build();

        exerciseItem.setSets(List.of(set1));
        session.setExercises(List.of(exerciseItem));

        // Previous best was 25.0 kg
        PersonalRecord prevWeight = PersonalRecord.builder()
                .recordValue(BigDecimal.valueOf(25.0))
                .secondaryValue(BigDecimal.valueOf(8))
                .build();
        when(personalRecordRepository.findBestHistoricalRecord(eq(1L), eq(10L), eq(PersonalRecordType.MAX_WEIGHT), any()))
                .thenReturn(Optional.of(prevWeight));

        // Previous reps was 6 reps
        PersonalRecord prevReps = PersonalRecord.builder()
                .recordValue(BigDecimal.valueOf(6))
                .build();
        when(personalRecordRepository.findBestHistoricalRecord(eq(1L), eq(10L), eq(PersonalRecordType.MAX_REPS), any()))
                .thenReturn(Optional.of(prevReps));

        when(personalRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PersonalRecordResponse> prs = personalRecordService.detectAndSavePRs(session);

        assertThat(prs).isNotEmpty();
        PersonalRecordResponse weightPr = prs.stream()
                .filter(p -> p.getRecordType().equals(PersonalRecordType.MAX_WEIGHT.name()))
                .findFirst().orElseThrow();
        assertThat(weightPr.getRecordValue()).isEqualByComparingTo(BigDecimal.valueOf(27.5));
        assertThat(weightPr.getPreviousRecordValue()).isEqualByComparingTo(BigDecimal.valueOf(25.0));
        assertThat(weightPr.getImprovementPercentage()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(weightPr.getFormattedRecord()).contains("+27.5 kg × 8 reps");
    }

    @Test
    void detectAndSavePRs_DetectsMaxDurationPR() {
        WorkoutSession session = WorkoutSession.builder()
                .id(101L).user(athlete).status(WorkoutSessionStatus.COMPLETED)
                .workoutDate(LocalDate.now()).completedAt(ZonedDateTime.now())
                .build();

        WorkoutSessionExercise exerciseItem = WorkoutSessionExercise.builder()
                .id(201L).workoutSession(session).exercise(plancheHold).exerciseNameSnapshot("Planche Hold")
                .orderIndex(1).targetSets(3).restSeconds(90)
                .build();

        WorkoutSessionSet set1 = WorkoutSessionSet.builder()
                .id(302L).workoutSessionExercise(exerciseItem).setNumber(1)
                .holdDurationSeconds(15).completed(true)
                .build();

        exerciseItem.setSets(List.of(set1));
        session.setExercises(List.of(exerciseItem));

        PersonalRecord prevDuration = PersonalRecord.builder()
                .recordValue(BigDecimal.valueOf(10))
                .build();
        when(personalRecordRepository.findBestHistoricalRecord(eq(1L), eq(20L), eq(PersonalRecordType.MAX_DURATION), any()))
                .thenReturn(Optional.of(prevDuration));

        when(personalRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PersonalRecordResponse> prs = personalRecordService.detectAndSavePRs(session);

        assertThat(prs).hasSize(1);
        PersonalRecordResponse pr = prs.getFirst();
        assertThat(pr.getRecordType()).isEqualTo(PersonalRecordType.MAX_DURATION.name());
        assertThat(pr.getRecordValue()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(pr.getPreviousRecordValue()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(pr.getImprovementPercentage()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
        assertThat(pr.getFormattedRecord()).isEqualTo("15s hold");
    }

    @Test
    void detectAndSavePRs_IncompleteSession_Ignored() {
        WorkoutSession session = WorkoutSession.builder()
                .id(102L).user(athlete).status(WorkoutSessionStatus.IN_PROGRESS)
                .build();

        List<PersonalRecordResponse> prs = personalRecordService.detectAndSavePRs(session);

        assertThat(prs).isEmpty();
        verify(personalRecordRepository, never()).save(any());
    }

    @Test
    void detectAndSavePRs_PreventsDuplicateRecordWhenSessionReprocessed() {
        WorkoutSession session = WorkoutSession.builder()
                .id(103L).user(athlete).status(WorkoutSessionStatus.COMPLETED)
                .workoutDate(LocalDate.now()).completedAt(ZonedDateTime.now())
                .build();

        WorkoutSessionExercise exerciseItem = WorkoutSessionExercise.builder()
                .id(203L).workoutSession(session).exercise(plancheHold).exerciseNameSnapshot("Planche Hold")
                .orderIndex(1).targetSets(1).restSeconds(90)
                .build();

        WorkoutSessionSet set1 = WorkoutSessionSet.builder()
                .id(303L).workoutSessionExercise(exerciseItem).setNumber(1)
                .holdDurationSeconds(20).completed(true)
                .build();

        exerciseItem.setSets(List.of(set1));
        session.setExercises(List.of(exerciseItem));

        when(personalRecordRepository.existsByUserIdAndWorkoutSessionIdAndExerciseIdAndRecordType(
                1L, 103L, 20L, PersonalRecordType.MAX_DURATION)).thenReturn(true);

        List<PersonalRecordResponse> prs = personalRecordService.detectAndSavePRs(session);

        assertThat(prs).isEmpty();
        verify(personalRecordRepository, never()).save(any());
    }

    @Test
    void sharePRWithCoach_Success() {
        PersonalRecord pr = PersonalRecord.builder()
                .id(500L).user(athlete).exercise(weightedPullUp)
                .workoutSession(WorkoutSession.builder().id(100L).build())
                .recordType(PersonalRecordType.MAX_WEIGHT).recordValue(BigDecimal.valueOf(27.5))
                .achievedAt(ZonedDateTime.now())
                .build();

        when(personalRecordRepository.findById(500L)).thenReturn(Optional.of(pr));

        CoachClientRelationship relationship = CoachClientRelationship.builder()
                .coach(coach).client(athlete).status(CoachClientRelationshipStatus.ACTIVE)
                .build();
        when(coachClientRelationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                1L, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.of(relationship));

        when(sharedPersonalRecordRepository.existsByPersonalRecordIdAndCoachId(500L, 2L)).thenReturn(false);
        when(sharedPersonalRecordRepository.save(any())).thenAnswer(invocation -> {
            SharedPersonalRecord s = invocation.getArgument(0);
            s.setId(800L);
            s.setSharedAt(ZonedDateTime.now());
            return s;
        });

        SharedPersonalRecordResponse response = personalRecordService.sharePRWithCoach(1L, 500L);

        assertThat(response).isNotNull();
        assertThat(response.getCoachId()).isEqualTo(2L);
        assertThat(response.getClientId()).isEqualTo(1L);
    }

    @Test
    void sharePRWithCoach_ClientWithoutAssignedCoach_ThrowsBadRequest() {
        PersonalRecord pr = PersonalRecord.builder()
                .id(501L).user(athlete).exercise(weightedPullUp)
                .build();

        when(personalRecordRepository.findById(501L)).thenReturn(Optional.of(pr));
        when(coachClientRelationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                1L, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> personalRecordService.sharePRWithCoach(1L, 501L));
    }

    @Test
    void sharePRWithCoach_DuplicateShare_ThrowsConflict() {
        PersonalRecord pr = PersonalRecord.builder()
                .id(502L).user(athlete).exercise(weightedPullUp)
                .build();

        when(personalRecordRepository.findById(502L)).thenReturn(Optional.of(pr));
        CoachClientRelationship relationship = CoachClientRelationship.builder()
                .coach(coach).client(athlete).status(CoachClientRelationshipStatus.ACTIVE)
                .build();
        when(coachClientRelationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                1L, CoachClientRelationshipStatus.ACTIVE)).thenReturn(Optional.of(relationship));
        when(sharedPersonalRecordRepository.existsByPersonalRecordIdAndCoachId(502L, 2L)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> personalRecordService.sharePRWithCoach(1L, 502L));
    }
}
