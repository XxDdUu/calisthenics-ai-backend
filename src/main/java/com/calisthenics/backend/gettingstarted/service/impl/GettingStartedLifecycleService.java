package com.calisthenics.backend.gettingstarted.service.impl;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedDayCompletion;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedDayProgress;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedEnrollment;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramDay;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramExercise;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedDayStatus;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedDayCompletionRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedDayProgressRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedEnrollmentRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionExerciseStatus;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coordinates real WorkoutSession execution and completion rules with Getting Started
 * per-day progress and enrollment lifecycle state without creating circular service dependencies.
 */
@Service
@RequiredArgsConstructor
public class GettingStartedLifecycleService {

    private final GettingStartedEnrollmentRepository enrollmentRepository;
    private final GettingStartedDayProgressRepository dayProgressRepository;
    private final GettingStartedDayCompletionRepository dayCompletionRepository;

    public void populateGettingStartedExercises(WorkoutSession session, GettingStartedProgramDay programDay) {
        if (session == null || programDay == null || programDay.getExercises() == null) {
            return;
        }
        List<GettingStartedProgramExercise> ordered = programDay.getExercises().stream()
                .sorted(Comparator.comparing(
                        GettingStartedProgramExercise::getDisplayOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();

        for (int index = 0; index < ordered.size(); index++) {
            GettingStartedProgramExercise prescribed = ordered.get(index);
            int targetSets = prescribed.getSets() != null && prescribed.getSets() > 0 ? prescribed.getSets() : 1;
            int restSeconds = prescribed.getRestSeconds() != null && prescribed.getRestSeconds() >= 0
                    ? prescribed.getRestSeconds()
                    : 60;
            String exerciseName = prescribed.getExerciseName() != null && !prescribed.getExerciseName().isBlank()
                    ? prescribed.getExerciseName()
                    : (prescribed.getExercise() != null ? prescribed.getExercise().getName() : "Exercise " + (index + 1));

            WorkoutSessionExercise sessionExercise = WorkoutSessionExercise.builder()
                    .workoutSession(session)
                    .exercise(prescribed.getExercise())
                    .exerciseNameSnapshot(exerciseName)
                    .instructionsSnapshot(prescribed.getInstructions())
                    .orderIndex(index)
                    .targetSets(targetSets)
                    .targetReps(prescribed.getReps())
                    .targetHoldDurationSeconds(prescribed.getDurationSeconds())
                    .restSeconds(restSeconds)
                    .status(WorkoutSessionExerciseStatus.IN_PROGRESS)
                    .build();

            for (int setNumber = 1; setNumber <= targetSets; setNumber++) {
                WorkoutSessionSet set = WorkoutSessionSet.builder()
                        .workoutSessionExercise(sessionExercise)
                        .setNumber(setNumber)
                        .reps(prescribed.getReps())
                        .holdDurationSeconds(prescribed.getDurationSeconds())
                        .completed(false)
                        .build();
                sessionExercise.getSets().add(set);
            }
            session.getExercises().add(sessionExercise);
        }
    }

    @Transactional
    public void initializeDayProgressForEnrollment(GettingStartedEnrollment enrollment, LocalDate startDate) {
        if (enrollment == null || enrollment.getId() == null) {
            return;
        }
        for (int day = 1; day <= GettingStartedCampaignHelper.EXPECTED_DAYS; day++) {
            final int dayNum = day;
            dayProgressRepository.findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNum)
                    .orElseGet(() -> dayProgressRepository.save(
                            GettingStartedDayProgress.builder()
                                    .enrollment(enrollment)
                                    .dayNumber(dayNum)
                                    .status(GettingStartedDayStatus.NOT_STARTED)
                                    .scheduledDate(startDate != null ? startDate.plusDays(dayNum - 1L) : null)
                                    .build()
                    ));
        }
    }

    @Transactional
    public void markDayInProgress(GettingStartedEnrollment enrollment, Integer dayNumber, WorkoutSession session) {
        if (enrollment == null || dayNumber == null) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        GettingStartedDayProgress progress = dayProgressRepository
                .findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber)
                .orElseGet(() -> GettingStartedDayProgress.builder()
                        .enrollment(enrollment)
                        .dayNumber(dayNumber)
                        .status(GettingStartedDayStatus.NOT_STARTED)
                        .build());

        if (progress.getStatus() != GettingStartedDayStatus.COMPLETED) {
            progress.setStatus(GettingStartedDayStatus.IN_PROGRESS);
        }
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(now);
        }
        if (session != null) {
            progress.setWorkoutSession(session);
        }
        dayProgressRepository.save(progress);

        if (enrollment.getStatus() == GettingStartedEnrollmentStatus.NOT_STARTED
                || enrollment.getStatus() == GettingStartedEnrollmentStatus.ACTIVE) {
            enrollment.setStatus(GettingStartedEnrollmentStatus.IN_PROGRESS);
        }
        if (enrollment.getStartedAt() == null) {
            enrollment.setStartedAt(now);
        }
        enrollmentRepository.save(enrollment);
    }

    @Transactional
    public void onWorkoutSessionFinished(WorkoutSession session) {
        if (session == null
                || session.getGettingStartedEnrollment() == null
                || session.getGettingStartedDayNumber() == null) {
            return;
        }

        GettingStartedEnrollment enrollment = session.getGettingStartedEnrollment();
        Integer dayNumber = session.getGettingStartedDayNumber();
        ZonedDateTime now = ZonedDateTime.now();

        GettingStartedDayProgress progress = dayProgressRepository
                .findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber)
                .orElseGet(() -> GettingStartedDayProgress.builder()
                        .enrollment(enrollment)
                        .dayNumber(dayNumber)
                        .status(GettingStartedDayStatus.IN_PROGRESS)
                        .startedAt(session.getStartedAt() != null ? session.getStartedAt() : now)
                        .build());

        if (progress.getStartedAt() == null) {
            progress.setStartedAt(session.getStartedAt() != null ? session.getStartedAt() : now);
        }
        if (enrollment.getStartedAt() == null) {
            enrollment.setStartedAt(progress.getStartedAt());
        }

        boolean qualifiesForDayCompletion = qualifiesForDayCompletion(session);
        if (!qualifiesForDayCompletion) {
            if (progress.getStatus() != GettingStartedDayStatus.COMPLETED) {
                progress.setStatus(GettingStartedDayStatus.IN_PROGRESS);
                progress.setWorkoutSession(session);
                dayProgressRepository.save(progress);
            }
            if (enrollment.getStatus() == GettingStartedEnrollmentStatus.NOT_STARTED
                    || enrollment.getStatus() == GettingStartedEnrollmentStatus.ACTIVE) {
                enrollment.setStatus(GettingStartedEnrollmentStatus.IN_PROGRESS);
                enrollmentRepository.save(enrollment);
            }
            return;
        }

        progress.setStatus(GettingStartedDayStatus.COMPLETED);
        progress.setWorkoutSession(session);
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }
        dayProgressRepository.save(progress);

        if (dayCompletionRepository.findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber).isEmpty()) {
            dayCompletionRepository.save(GettingStartedDayCompletion.builder()
                    .enrollment(enrollment)
                    .dayNumber(dayNumber)
                    .workoutSession(session)
                    .completedAt(now)
                    .build());
        }

        syncEnrollmentCompletionStatus(enrollment, dayNumber, now);
    }

    public boolean qualifiesForDayCompletion(WorkoutSession session) {
        if (session == null || session.getStatus() != WorkoutSessionStatus.COMPLETED) {
            return false;
        }
        List<WorkoutSessionExercise> exercises = session.getExercises();
        if (exercises == null || exercises.isEmpty()) {
            return false;
        }
        return exercises.stream().allMatch(ex -> ex.getStatus() == WorkoutSessionExerciseStatus.COMPLETED);
    }

    private void syncEnrollmentCompletionStatus(
            GettingStartedEnrollment enrollment,
            Integer newlyCompletedDay,
            ZonedDateTime now
    ) {
        Set<Integer> completedDays = new HashSet<>();
        dayCompletionRepository.findByEnrollmentIdOrderByDayNumberAsc(enrollment.getId())
                .forEach(c -> completedDays.add(c.getDayNumber()));
        dayProgressRepository.findByEnrollmentIdOrderByDayNumberAsc(enrollment.getId())
                .stream()
                .filter(p -> p.getStatus() == GettingStartedDayStatus.COMPLETED)
                .forEach(p -> completedDays.add(p.getDayNumber()));
        if (newlyCompletedDay != null) {
            completedDays.add(newlyCompletedDay);
        }

        if (completedDays.size() >= GettingStartedCampaignHelper.EXPECTED_DAYS) {
            enrollment.setStatus(GettingStartedEnrollmentStatus.COMPLETED);
            if (enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(now);
            }
        } else {
            enrollment.setStatus(GettingStartedEnrollmentStatus.IN_PROGRESS);
        }
        enrollmentRepository.save(enrollment);
    }
}
