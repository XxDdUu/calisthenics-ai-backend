package com.calisthenics.backend.workout;

import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.workout.dto.*;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class PersistentWorkoutSessionService {

    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutPlanDayRepository dayRepository;
    private final WorkoutPlanExerciseRepository planExerciseRepository;
    private final TodayWorkoutResolver todayWorkoutResolver;
    private final com.calisthenics.backend.analytics.service.PersonalRecordService personalRecordService;
    private final UserTimeResolver userTimeResolver;

    public PersistentWorkoutSessionService(
            WorkoutSessionRepository sessionRepository,
            WorkoutPlanDayRepository dayRepository,
            WorkoutPlanExerciseRepository planExerciseRepository,
            TodayWorkoutResolver todayWorkoutResolver,
            com.calisthenics.backend.analytics.service.PersonalRecordService personalRecordService
    ) {
        this(sessionRepository, dayRepository, planExerciseRepository, todayWorkoutResolver,
                personalRecordService, new UserTimeResolver(new AppProperties()));
    }

    @Autowired
    public PersistentWorkoutSessionService(
            WorkoutSessionRepository sessionRepository,
            WorkoutPlanDayRepository dayRepository,
            WorkoutPlanExerciseRepository planExerciseRepository,
            TodayWorkoutResolver todayWorkoutResolver,
            com.calisthenics.backend.analytics.service.PersonalRecordService personalRecordService,
            UserTimeResolver userTimeResolver
    ) {
        this.sessionRepository = sessionRepository;
        this.dayRepository = dayRepository;
        this.planExerciseRepository = planExerciseRepository;
        this.todayWorkoutResolver = todayWorkoutResolver;
        this.personalRecordService = personalRecordService;
        this.userTimeResolver = userTimeResolver;
    }

    @Transactional
    public PersistentWorkoutSessionResponse startOrResume(Long userId, StartWorkoutSessionRequest request) {
        List<WorkoutSession> activeSessions = sessionRepository.findByUserIdAndWorkoutDateAndStatus(
                        userId, request.workoutDate(), WorkoutSessionStatus.IN_PROGRESS).stream()
                .filter(session -> isSessionAssignmentUsable(session, true))
                .toList();
        if (activeSessions.size() == 1) {
            return resumeAndHydrateIfNeeded(activeSessions.getFirst());
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
                return resumeAndHydrateIfNeeded(requestedSession.getFirst());
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Choose one of the unfinished workouts before starting another");
        }

        List<WorkoutSession> scheduledSessions = sessionRepository.findByUserIdAndWorkoutDateAndStatus(
                        userId, request.workoutDate(), WorkoutSessionStatus.SCHEDULED).stream()
                .filter(session -> isSessionAssignmentUsable(session, false))
                .toList();
        OptionalScheduledMatch scheduledMatch = findMatchingScheduledSession(scheduledSessions, request);
        if (scheduledMatch.matched() != null) {
            return promoteScheduledSession(scheduledMatch.matched());
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
        return getActive(userId, null, null);
    }

    @Transactional(readOnly = true)
    public List<PersistentWorkoutSessionResponse> getActive(Long userId, LocalDate explicitLocalDate, String timezone) {
        LocalDate today = userTimeResolver.today(explicitLocalDate, timezone);
        ZoneId zone = userTimeResolver.resolveZone(timezone);
        return sessionRepository.findUnfinishedSessionsAsOf(userId, today).stream()
                .filter(session -> WorkoutSessionClassifier.isUnfinished(session, today, zone))
                .filter(session -> isSessionAssignmentUsable(
                        session, session.getStatus() == WorkoutSessionStatus.IN_PROGRESS))
                .map(session -> map(session, today, zone))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PersistentWorkoutSessionResponse> getUpcoming(Long userId, LocalDate explicitLocalDate, String timezone) {
        LocalDate today = userTimeResolver.today(explicitLocalDate, timezone);
        ZoneId zone = userTimeResolver.resolveZone(timezone);
        return sessionRepository.findUpcomingSessionsAfter(userId, today).stream()
                .filter(session -> WorkoutSessionClassifier.isUpcoming(session, today, zone))
                // Keep scheduled rows for audit/history, but do not present an inactive or
                // cancelled assignment as an active assigned workout.
                .filter(session -> isSessionAssignmentUsable(session, false))
                .map(session -> map(session, today, zone))
                .toList();
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
    public PersistentWorkoutSessionResponse addSet(Long userId, Long sessionId, Long exerciseId) {
        WorkoutSession session = activeOwnedSession(userId, sessionId);
        WorkoutSessionExercise exercise = sessionExercise(session, exerciseId);
        if (exercise.getStatus() == WorkoutSessionExerciseStatus.SKIPPED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unskip the exercise before adding sets");
        }
        int nextSetNumber = exercise.getSets().stream()
                .mapToInt(WorkoutSessionSet::getSetNumber)
                .max().orElse(0) + 1;
        WorkoutSessionSet newSet = WorkoutSessionSet.builder()
                .workoutSessionExercise(exercise)
                .setNumber(nextSetNumber)
                .completed(false)
                .reps(exercise.getTargetReps())
                .holdDurationSeconds(exercise.getTargetHoldDurationSeconds())
                .build();
        exercise.getSets().add(newSet);
        exercise.setTargetSets(exercise.getSets().size());
        exercise.setStatus(WorkoutSessionExerciseStatus.IN_PROGRESS);
        session.setLastSavedAt(ZonedDateTime.now());
        sessionRepository.flush();
        return map(session);
    }

    @Transactional
    public PersistentWorkoutSessionResponse deleteSet(Long userId, Long sessionId, Long exerciseId, Long setId) {
        WorkoutSession session = activeOwnedSession(userId, sessionId);
        WorkoutSessionExercise exercise = sessionExercise(session, exerciseId);
        if (exercise.getStatus() == WorkoutSessionExerciseStatus.SKIPPED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unskip the exercise before removing sets");
        }
        if (exercise.getSets().size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An exercise must contain at least one set");
        }
        WorkoutSessionSet set = exercise.getSets().stream()
                .filter(item -> item.getId().equals(setId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout set not found"));
        exercise.getSets().remove(set);
        int currentNum = 1;
        for (WorkoutSessionSet s : exercise.getSets()) {
            s.setSetNumber(currentNum++);
        }
        exercise.setTargetSets(exercise.getSets().size());
        exercise.setStatus(exercise.getSets().stream().allMatch(WorkoutSessionSet::isCompleted) && !exercise.getSets().isEmpty()
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
        long elapsedSeconds = session.getStartedAt() != null
                ? Math.max(0, Duration.between(session.getStartedAt(), completedAt).getSeconds())
                : 0L;
        session.setDurationSeconds(elapsedSeconds);
        session.setStatus(WorkoutSessionStatus.COMPLETED);
        sessionRepository.flush();
        personalRecordService.detectAndSavePRs(session);
        return map(session);
    }

    private record OptionalScheduledMatch(WorkoutSession matched) {}

    private OptionalScheduledMatch findMatchingScheduledSession(
            List<WorkoutSession> scheduledSessions,
            StartWorkoutSessionRequest request
    ) {
        if (scheduledSessions == null || scheduledSessions.isEmpty()) {
            return new OptionalScheduledMatch(null);
        }
        WorkoutSession exact = scheduledSessions.stream()
                .filter(session -> session.getWorkoutPlan() != null
                        && session.getWorkoutPlan().getId().equals(request.planId())
                        && session.getWorkoutPlanDay() != null
                        && session.getWorkoutPlanDay().getId().equals(request.dayId()))
                .findFirst()
                .orElse(scheduledSessions.size() == 1 ? scheduledSessions.getFirst() : null);
        return new OptionalScheduledMatch(exact);
    }

    private PersistentWorkoutSessionResponse resumeAndHydrateIfNeeded(WorkoutSession session) {
        boolean changed = false;
        if (session.getStartedAt() == null) {
            ZonedDateTime now = ZonedDateTime.now();
            session.setStartedAt(now);
            if (session.getLastSavedAt() == null) {
                session.setLastSavedAt(now);
            }
            changed = true;
        }
        if ((session.getExercises() == null || session.getExercises().isEmpty()) && session.getWorkoutPlanDay() != null) {
            List<WorkoutPlanExercise> planned = planExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(session.getWorkoutPlanDay().getId());
            if (!planned.isEmpty()) {
                populateSessionExercises(session, planned);
                changed = true;
            }
        }
        if (changed) {
            return map(sessionRepository.saveAndFlush(session));
        }
        return map(session);
    }

    private PersistentWorkoutSessionResponse promoteScheduledSession(WorkoutSession session) {
        ZonedDateTime now = ZonedDateTime.now();
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        if (session.getStartedAt() == null) {
            session.setStartedAt(now);
        }
        session.setLastSavedAt(now);
        if ((session.getExercises() == null || session.getExercises().isEmpty()) && session.getWorkoutPlanDay() != null) {
            List<WorkoutPlanExercise> planned = planExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(session.getWorkoutPlanDay().getId());
            if (!planned.isEmpty()) {
                populateSessionExercises(session, planned);
            }
        }
        return map(sessionRepository.saveAndFlush(session));
    }

    private void populateSessionExercises(WorkoutSession session, List<WorkoutPlanExercise> plannedExercises) {
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
        session.getExercises().clear();
        session.getExercises().addAll(exercises);
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
        if (session.getWorkoutPlanAssignment() != null && !isSessionAssignmentUsable(session, true)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This coach workout assignment has been canceled and can no longer be continued");
        }
        return session;
    }

    private boolean isSessionAssignmentUsable(WorkoutSession session, boolean allowInactive) {
        WorkoutPlanAssignment assignment = session.getWorkoutPlanAssignment();
        if (assignment == null) {
            return true;
        }
        if (assignment.getRelationship() != null
                && assignment.getRelationship().getStatus() != CoachClientRelationshipStatus.ACTIVE) {
            return false;
        }
        if (allowInactive) {
            return assignment.getStatus() == WorkoutPlanAssignmentStatus.ACTIVE
                    || assignment.getStatus() == WorkoutPlanAssignmentStatus.INACTIVE;
        }
        return assignment.getStatus() == WorkoutPlanAssignmentStatus.ACTIVE;
    }

    private WorkoutSessionExercise sessionExercise(WorkoutSession session, Long exerciseId) {
        return session.getExercises().stream()
                .filter(exercise -> Objects.equals(exercise.getId(), exerciseId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session exercise not found"));
    }

    private PersistentWorkoutSessionResponse map(WorkoutSession session) {
        return map(session, userTimeResolver.today(null), userTimeResolver.resolveZone(null));
    }

    private PersistentWorkoutSessionResponse map(WorkoutSession session, LocalDate today, ZoneId zone) {
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
                WorkoutSessionClassifier.classify(session, today, zone),
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
