package com.calisthenics.backend.workout;

import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.workout.dto.*;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PersistentWorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutPlanDayRepository dayRepository;
    private final WorkoutPlanExerciseRepository planExerciseRepository;
    private final TodayWorkoutResolver todayWorkoutResolver;
    private final com.calisthenics.backend.analytics.service.PersonalRecordService personalRecordService;

    @Transactional
    public PersistentWorkoutSessionResponse startOrResume(Long userId, StartWorkoutSessionRequest request) {
        List<WorkoutSession> activeSessions = sessionRepository.findByUserIdAndWorkoutDateAndStatus(
                userId, request.workoutDate(), WorkoutSessionStatus.IN_PROGRESS);
        if (activeSessions.size() == 1) {
            return map(activeSessions.getFirst());
        }
        if (activeSessions.size() > 1) {
            List<WorkoutSession> requestedSession = activeSessions.stream()
                    .filter(session -> session.getWorkoutPlan() != null
                            && session.getWorkoutPlan().getId().equals(request.planId())
                            && session.getWorkoutPlanDay() != null
                            && session.getWorkoutPlanDay().getId().equals(request.dayId())
                            && Objects.equals(
                                    session.getWorkoutPlanAssignment() == null
                                            ? null : session.getWorkoutPlanAssignment().getId(),
                                    request.assignmentId()))
                    .toList();
            if (requestedSession.size() == 1) {
                return map(requestedSession.getFirst());
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Choose one of the unfinished workouts before starting another");
        }

        WorkoutPlanDay day = dayRepository.findByIdAndWorkoutPlanId(request.dayId(), request.planId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout day not found"));
        WorkoutPlan plan = day.getWorkoutPlan();
        if (day.getDayOfWeek().getOrder() != request.workoutDate().getDayOfWeek().getValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout date does not match the scheduled day");
        }
        if (day.isRestDay() || day.getSessionType() == WorkoutSessionType.REST) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A rest day cannot be started as a workout");
        }

        List<TodayWorkoutResolver.ResolvedWorkout> candidates = todayWorkoutResolver
                .resolveCandidates(userId, request.workoutDate()).candidates();
        TodayWorkoutResolver.ResolvedWorkout selectedWorkout = candidates.stream()
                .filter(candidate -> candidate.plan().getId().equals(request.planId())
                        && candidate.day().getId().equals(request.dayId())
                        && Objects.equals(
                                candidate.assignment() == null ? null : candidate.assignment().getId(),
                                request.assignmentId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        plan.getUser().getId().equals(userId) ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                        plan.getUser().getId().equals(userId)
                                ? "Today's workout source changed; refresh and try again"
                                : "Workout plan not found"));
        WorkoutPlanAssignment assignment = selectedWorkout.assignment();
        if (!plan.getUser().getId().equals(userId) && assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout plan not found");
        }

        List<WorkoutPlanExercise> plannedExercises =
                planExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
        if (plannedExercises.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This workout has no exercises");
        }

        ZonedDateTime now = ZonedDateTime.now();
        WorkoutSession session = WorkoutSession.builder()
                .user(assignment == null ? plan.getUser() : assignment.getClient())
                .workoutPlan(plan)
                .workoutPlanDay(day)
                .workoutPlanAssignment(assignment)
                .source(selectedWorkout.source())
                .workoutDate(request.workoutDate())
                .title(plan.getName() + " - " + workoutDayTitle(day))
                .status(WorkoutSessionStatus.IN_PROGRESS)
                .startedAt(now)
                .lastSavedAt(now)
                .build();

        List<WorkoutSessionExercise> exercises = new ArrayList<>();
        for (WorkoutPlanExercise planned : plannedExercises) {
            WorkoutSessionExercise exercise = WorkoutSessionExercise.builder()
                    .workoutSession(session)
                    .exercise(planned.getExercise())
                    .exerciseNameSnapshot(planned.getExercise().getName())
                    .instructionsSnapshot(planned.getExercise().getInstructions())
                    .planNotesSnapshot(planned.getNotes())
                    .orderIndex(planned.getDisplayOrder())
                    .targetSets(planned.getSets())
                    .targetReps(planned.getReps())
                    .targetHoldDurationSeconds(planned.getDurationSeconds())
                    .restSeconds(planned.getRestSeconds())
                    .status(WorkoutSessionExerciseStatus.IN_PROGRESS)
                    .build();
            for (int setNumber = 1; setNumber <= planned.getSets(); setNumber++) {
                exercise.getSets().add(WorkoutSessionSet.builder()
                        .workoutSessionExercise(exercise)
                        .setNumber(setNumber)
                        .completed(false)
                        .build());
            }
            exercises.add(exercise);
        }
        session.setExercises(exercises);

        return map(sessionRepository.saveAndFlush(session));
    }

    @Transactional(readOnly = true)
    public PersistentWorkoutSessionResponse get(Long userId, Long sessionId) {
        return map(ownedSession(userId, sessionId));
    }

    public PersistentWorkoutSessionResponse mapForAdmin(WorkoutSession session) {
        return map(session);
    }

    @Transactional(readOnly = true)
    public List<PersistentWorkoutSessionResponse> getActive(Long userId) {
        return sessionRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutSessionStatus.IN_PROGRESS)
                .stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public WorkoutSessionPageResponse getHistory(Long userId, int page, int size) {
        Page<WorkoutSession> result = sessionRepository.findByUserIdAndStatus(
                userId,
                WorkoutSessionStatus.COMPLETED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "completedAt")));
        return new WorkoutSessionPageResponse(
                result.getContent().stream().map(this::map).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public PersistentWorkoutSessionResponse updateSet(
            Long userId, Long sessionId, Long exerciseId, Long setId, UpdateWorkoutSessionSetRequest request) {
        WorkoutSession session = activeOwnedSession(userId, sessionId);
        WorkoutSessionExercise exercise = sessionExercise(session, exerciseId);
        if (exercise.getStatus() == WorkoutSessionExerciseStatus.SKIPPED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unskip the exercise before editing its sets");
        }
        WorkoutSessionSet set = exercise.getSets().stream()
                .filter(item -> item.getId().equals(setId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout set not found"));
        if (!set.getVersion().equals(request.expectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This set changed on another device; reload before replacing it");
        }

        if (Boolean.TRUE.equals(request.completed())) {
            if (exercise.getTargetReps() != null && request.reps() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repetitions are required for this exercise");
            }
            if (exercise.getTargetHoldDurationSeconds() != null && request.holdDurationSeconds() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hold duration is required for this exercise");
            }
        }

        set.setReps(request.reps());
        set.setHoldDurationSeconds(request.holdDurationSeconds());
        set.setWeightKg(request.weightKg());
        set.setAssistanceWeightKg(request.assistanceWeightKg());
        set.setNotes(request.notes());
        set.setCompleted(request.completed());
        set.setCompletedAt(request.completed() ? ZonedDateTime.now() : null);
        exercise.setStatus(exercise.getSets().stream().allMatch(WorkoutSessionSet::isCompleted)
                ? WorkoutSessionExerciseStatus.COMPLETED
                : WorkoutSessionExerciseStatus.IN_PROGRESS);
        session.setLastSavedAt(ZonedDateTime.now());
        sessionRepository.flush();
        return map(session);
    }

    @Transactional
    public PersistentWorkoutSessionResponse updateExercise(
            Long userId, Long sessionId, Long exerciseId, UpdateWorkoutSessionExerciseRequest request) {
        WorkoutSession session = activeOwnedSession(userId, sessionId);
        WorkoutSessionExercise exercise = sessionExercise(session, exerciseId);
        if (request.status() != WorkoutSessionExerciseStatus.SKIPPED
                && request.status() != WorkoutSessionExerciseStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise status is derived from completed sets");
        }
        if (request.status() == WorkoutSessionExerciseStatus.SKIPPED
                && exercise.getStatus() == WorkoutSessionExerciseStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A completed exercise cannot be skipped");
        }
        if (!exercise.getVersion().equals(request.expectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This exercise changed on another device; reload before replacing it");
        }
        exercise.setStatus(request.status());
        session.setLastSavedAt(ZonedDateTime.now());
        sessionRepository.flush();
        return map(session);
    }

    @Transactional
    public PersistentWorkoutSessionResponse finish(
            Long userId, Long sessionId, FinishWorkoutSessionRequest request) {
        WorkoutSession session = activeOwnedSession(userId, sessionId);
        List<WorkoutSessionExercise> unfinished = session.getExercises().stream()
                .filter(exercise -> exercise.getStatus() != WorkoutSessionExerciseStatus.COMPLETED
                        && exercise.getStatus() != WorkoutSessionExerciseStatus.SKIPPED)
                .toList();
        if (!unfinished.isEmpty() && !request.confirmPartial()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirm partial completion before finishing");
        }
        unfinished.forEach(exercise -> exercise.setStatus(WorkoutSessionExerciseStatus.INCOMPLETE));
        ZonedDateTime completedAt = ZonedDateTime.now();
        session.setCompletedAt(completedAt);
        session.setLastSavedAt(completedAt);
        session.setDurationSeconds(Math.max(0, Duration.between(session.getStartedAt(), completedAt).getSeconds()));
        session.setStatus(WorkoutSessionStatus.COMPLETED);
        sessionRepository.flush();
        personalRecordService.detectAndSavePRs(session);
        return map(session);
    }

    private String workoutDayTitle(WorkoutPlanDay day) {
        if (day.getName() != null && !day.getName().isBlank()) {
            return day.getName();
        }
        return day.getSessionType() == null ? "Workout" : day.getSessionType().name().replace('_', ' ');
    }

    private WorkoutSession ownedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout session not found"));
    }

    private WorkoutSession activeOwnedSession(Long userId, Long sessionId) {
        WorkoutSession session = ownedSession(userId, sessionId);
        if (session.getStatus() != WorkoutSessionStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workout session is not in progress");
        }
        return session;
    }

    private WorkoutSessionExercise sessionExercise(WorkoutSession session, Long exerciseId) {
        return session.getExercises().stream()
                .filter(exercise -> Objects.equals(exercise.getId(), exerciseId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session exercise not found"));
    }

    private PersistentWorkoutSessionResponse map(WorkoutSession session) {
        List<WorkoutSessionExerciseResponse> exercises = session.getExercises().stream()
                .sorted(Comparator.comparing(WorkoutSessionExercise::getOrderIndex))
                .map(exercise -> new WorkoutSessionExerciseResponse(
                        exercise.getId(),
                        exercise.getVersion(),
                        exercise.getExercise() == null ? null : exercise.getExercise().getId(),
                        exercise.getExerciseNameSnapshot(),
                        exercise.getInstructionsSnapshot(),
                        exercise.getPlanNotesSnapshot(),
                        exercise.getOrderIndex(),
                        exercise.getTargetSets(),
                        exercise.getTargetReps(),
                        exercise.getTargetHoldDurationSeconds(),
                        exercise.getRestSeconds(),
                        exercise.getStatus(),
                        exercise.getSets().stream()
                                .sorted(Comparator.comparing(WorkoutSessionSet::getSetNumber))
                                .map(set -> new WorkoutSessionSetResponse(
                                        set.getId(), set.getVersion(), set.getSetNumber(), set.getReps(),
                                        set.getHoldDurationSeconds(), set.getWeightKg(),
                                        set.getAssistanceWeightKg(), set.isCompleted(),
                                        set.getCompletedAt(), set.getNotes()))
                                .toList()))
                .toList();

        int completedExercises = (int) exercises.stream()
                .filter(item -> item.status() == WorkoutSessionExerciseStatus.COMPLETED).count();
        int skippedExercises = (int) exercises.stream()
                .filter(item -> item.status() == WorkoutSessionExerciseStatus.SKIPPED).count();
        int unfinishedExercises = (int) exercises.stream()
                .filter(item -> item.status() == WorkoutSessionExerciseStatus.IN_PROGRESS
                        || item.status() == WorkoutSessionExerciseStatus.INCOMPLETE).count();
        int completedSets = (int) exercises.stream().flatMap(item -> item.sets().stream())
                .filter(WorkoutSessionSetResponse::completed).count();
        int totalReps = exercises.stream().flatMap(item -> item.sets().stream())
                .filter(WorkoutSessionSetResponse::completed)
                .map(WorkoutSessionSetResponse::reps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int totalHoldSeconds = exercises.stream().flatMap(item -> item.sets().stream())
                .filter(WorkoutSessionSetResponse::completed)
                .map(WorkoutSessionSetResponse::holdDurationSeconds).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();

        return new PersistentWorkoutSessionResponse(
                session.getId(),
                session.getWorkoutPlan() == null ? null : session.getWorkoutPlan().getId(),
                session.getWorkoutPlanDay() == null ? null : session.getWorkoutPlanDay().getId(),
                session.getWorkoutPlanAssignment() == null ? null : session.getWorkoutPlanAssignment().getId(),
                session.getSource(),
                session.getTitle(),
                session.getWorkoutDate(),
                session.getStatus(),
                session.getStartedAt(),
                session.getLastSavedAt(),
                session.getCompletedAt(),
                session.getDurationSeconds(),
                completedExercises,
                skippedExercises,
                unfinishedExercises,
                completedSets,
                totalReps,
                totalHoldSeconds,
                exercises);
    }
}
