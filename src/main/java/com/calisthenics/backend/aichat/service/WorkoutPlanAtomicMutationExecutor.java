package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.aichat.dto.preview.ExercisePrescriptionValues;
import com.calisthenics.backend.aichat.dto.preview.WorkoutPlanChangeOperation;
import com.calisthenics.backend.aichat.type.WorkoutPlanOperationType;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanExerciseResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanExerciseService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Executes a validated batch of structured workout plan operations inside one atomic database transaction,
 * reloads the persisted workout plan from the database, and verifies that the final persisted state
 * exactly matches the approved preview before committing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutPlanAtomicMutationExecutor {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutPlanExerciseService workoutPlanExerciseService;
    private final ExerciseRepository exerciseRepository;

    public record ExecutionVerificationResult(
            WorkoutPlan reloadedPlan,
            Map<Long, WorkoutPlanDay> reloadedDaysById,
            Map<Long, List<WorkoutPlanExercise>> reloadedExercisesByDayId,
            Set<Long> modifiedDayIds
    ) {}

    private record ExpectedExerciseState(
            Long assignmentId,
            Long exerciseId,
            Integer sets,
            Integer reps,
            Integer durationSeconds,
            Integer restSeconds
    ) {}

    private record ExpectedDayState(
            Long dayId,
            WorkoutDayOfWeek dayOfWeek,
            String name,
            WorkoutSessionType sessionType,
            boolean restDay,
            List<ExpectedExerciseState> exercises
    ) {}

    @Transactional(rollbackFor = Exception.class)
    public ExecutionVerificationResult executeAtomicallyAndVerify(
            Long userId,
            Long planId,
            List<WorkoutPlanChangeOperation> operations
    ) {
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("No workout plan operations to execute");
        }

        WorkoutPlan plan = workoutPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalStateException("Workout plan not found for authenticated user"));

        List<WorkoutPlanDay> preDays = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(plan.getId());
        Map<Long, ExpectedDayState> expectedByDayId = captureExpectedState(preDays);
        Set<Long> allowedModifiedDayIds = new LinkedHashSet<>();
        for (WorkoutPlanChangeOperation op : operations) {
            if (op.getSourceDayId() != null) {
                allowedModifiedDayIds.add(op.getSourceDayId());
            }
            if (op.getDestinationDayId() != null) {
                allowedModifiedDayIds.add(op.getDestinationDayId());
            }
        }

        for (WorkoutPlanChangeOperation op : operations) {
            executeSingleOperation(userId, plan.getId(), op, expectedByDayId);
        }

        workoutPlanExerciseRepository.flush();
        workoutPlanDayRepository.flush();

        // Reload fresh from DB and verify every single day matches the expected post-state
        WorkoutPlan reloadedPlan = workoutPlanRepository.findWithDetailsById(plan.getId())
                .orElseThrow(() -> new IllegalStateException("Unable to reload workout plan after mutation"));
        List<WorkoutPlanDay> reloadedDays = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(plan.getId());
        Map<Long, WorkoutPlanDay> reloadedDaysById = new LinkedHashMap<>();
        Map<Long, List<WorkoutPlanExercise>> reloadedExercisesByDayId = new LinkedHashMap<>();

        for (WorkoutPlanDay day : reloadedDays) {
            reloadedDaysById.put(day.getId(), day);
            List<WorkoutPlanExercise> actualExercises = workoutPlanExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
            reloadedExercisesByDayId.put(day.getId(), actualExercises);

            ExpectedDayState expected = expectedByDayId.get(day.getId());
            if (expected == null) {
                throw new IllegalStateException("Unexpected workout day found after mutation: dayId=" + day.getId());
            }
            verifyDayMatchesExpected(day, actualExercises, expected, allowedModifiedDayIds.contains(day.getId()));
        }

        return new ExecutionVerificationResult(
                reloadedPlan,
                reloadedDaysById,
                reloadedExercisesByDayId,
                allowedModifiedDayIds
        );
    }

    private void executeSingleOperation(
            Long userId,
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        if (op == null || op.getOperationType() == null) {
            throw new IllegalArgumentException("Invalid workout plan operation");
        }
        switch (op.getOperationType()) {
            case ADD_EXERCISE -> executeAdd(userId, planId, op, expectedByDayId);
            case REMOVE_EXERCISE -> executeRemove(userId, planId, op, expectedByDayId);
            case UPDATE_EXERCISE -> executeUpdate(userId, planId, op, expectedByDayId);
            case MOVE_EXERCISE -> executeMove(userId, planId, op, expectedByDayId);
            case REORDER_EXERCISES -> executeReorder(planId, op, expectedByDayId);
            case UPDATE_DAY -> executeUpdateDay(planId, op, expectedByDayId);
        }
    }

    private void executeAdd(
            Long userId,
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        WorkoutPlanDay destDay = requireDay(planId, op.getDestinationDayId());
        Exercise exercise = exerciseRepository.findByIdAndActiveTrue(op.getExerciseId())
                .orElseThrow(() -> new IllegalStateException("Exercise not found in catalog: " + op.getExerciseId()));

        List<WorkoutPlanExercise> currentDest = workoutPlanExerciseRepository
                .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(destDay.getId());
        boolean alreadyExists = currentDest.stream()
                .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), exercise.getId()));
        if (alreadyExists) {
            throw new IllegalStateException("Duplicate exercise prevented on destination day " + destDay.getDayOfWeek());
        }

        ExpectedDayState expectedDest = expectedByDayId.get(destDay.getId());
        expectedDest = activateRestDayIfNeeded(destDay, exercise, expectedDest);

        ExercisePrescriptionValues proposed = requireProposed(op);
        WorkoutPlanExerciseRequest addReq = toExerciseRequest(exercise.getId(), proposed, null);
        WorkoutPlanExerciseResponse created = workoutPlanExerciseService.add(userId, planId, destDay.getId(), addReq);

        List<ExpectedExerciseState> updatedList = new ArrayList<>(expectedDest.exercises());
        updatedList.add(new ExpectedExerciseState(
                created.getId(),
                exercise.getId(),
                addReq.getSets(),
                addReq.getReps(),
                addReq.getDurationSeconds(),
                addReq.getRestSeconds()
        ));
        expectedByDayId.put(destDay.getId(), withExercises(expectedDest, updatedList));
    }

    private void executeRemove(
            Long userId,
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        WorkoutPlanDay sourceDay = requireDay(planId, op.getSourceDayId());
        WorkoutPlanExercise existing = workoutPlanExerciseRepository
                .findByIdAndWorkoutPlanDayId(op.getWorkoutPlanExerciseId(), sourceDay.getId())
                .orElseThrow(() -> new IllegalStateException("Source exercise assignment not found: " + op.getWorkoutPlanExerciseId()));
        if (op.getExerciseId() != null && !Objects.equals(existing.getExercise().getId(), op.getExerciseId())) {
            throw new IllegalStateException("Source exercise assignment mismatch");
        }

        workoutPlanExerciseService.remove(userId, planId, sourceDay.getId(), existing.getId());
        resequenceDayExercises(sourceDay.getId());

        ExpectedDayState expectedSource = expectedByDayId.get(sourceDay.getId());
        List<ExpectedExerciseState> updatedList = expectedSource.exercises().stream()
                .filter(e -> !Objects.equals(e.assignmentId(), existing.getId()))
                .toList();
        expectedByDayId.put(sourceDay.getId(), withExercises(expectedSource, updatedList));
    }

    private void executeUpdate(
            Long userId,
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
        WorkoutPlanDay targetDay = requireDay(planId, dayId);
        WorkoutPlanExercise existing = workoutPlanExerciseRepository
                .findByIdAndWorkoutPlanDayId(op.getWorkoutPlanExerciseId(), targetDay.getId())
                .orElseThrow(() -> new IllegalStateException("Exercise assignment not found for update: " + op.getWorkoutPlanExerciseId()));

        ExercisePrescriptionValues proposed = requireProposed(op);
        WorkoutPlanExerciseRequest updateReq = toExerciseRequest(
                existing.getExercise().getId(),
                proposed,
                existing
        );
        updateReq.setDisplayOrder(existing.getDisplayOrder());

        workoutPlanExerciseService.update(userId, planId, targetDay.getId(), existing.getId(), updateReq);

        ExpectedDayState expectedDay = expectedByDayId.get(targetDay.getId());
        List<ExpectedExerciseState> updatedList = expectedDay.exercises().stream()
                .map(e -> Objects.equals(e.assignmentId(), existing.getId())
                        ? new ExpectedExerciseState(
                                existing.getId(),
                                existing.getExercise().getId(),
                                updateReq.getSets(),
                                updateReq.getReps(),
                                updateReq.getDurationSeconds(),
                                updateReq.getRestSeconds())
                        : e)
                .toList();
        expectedByDayId.put(targetDay.getId(), withExercises(expectedDay, updatedList));
    }

    private void executeMove(
            Long userId,
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        if (Objects.equals(op.getSourceDayId(), op.getDestinationDayId())) {
            throw new IllegalStateException("Source and destination days cannot be the same for MOVE_EXERCISE");
        }
        WorkoutPlanDay sourceDay = requireDay(planId, op.getSourceDayId());
        WorkoutPlanDay destDay = requireDay(planId, op.getDestinationDayId());

        WorkoutPlanExercise sourceEx = workoutPlanExerciseRepository
                .findByIdAndWorkoutPlanDayId(op.getWorkoutPlanExerciseId(), sourceDay.getId())
                .orElseThrow(() -> new IllegalStateException("Source exercise assignment no longer exists for MOVE"));
        Exercise exercise = sourceEx.getExercise();
        if (op.getExerciseId() != null && !Objects.equals(exercise.getId(), op.getExerciseId())) {
            throw new IllegalStateException("Source exercise mismatch for MOVE");
        }

        List<WorkoutPlanExercise> currentDest = workoutPlanExerciseRepository
                .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(destDay.getId());
        boolean destAlreadyHas = currentDest.stream()
                .anyMatch(pe -> pe.getExercise() != null && Objects.equals(pe.getExercise().getId(), exercise.getId()));
        if (destAlreadyHas) {
            throw new IllegalStateException("Destination day already contains " + exercise.getName());
        }

        ExercisePrescriptionValues proposed = requireProposed(op);
        WorkoutPlanExerciseRequest addReq = toExerciseRequest(exercise.getId(), proposed, sourceEx);
        addReq.setDisplayOrder(null); // append cleanly at destination end

        // 1. Remove source exactly once & resequence source day
        Long removedSourceId = sourceEx.getId();
        workoutPlanExerciseService.remove(userId, planId, sourceDay.getId(), removedSourceId);
        resequenceDayExercises(sourceDay.getId());

        ExpectedDayState expectedSource = expectedByDayId.get(sourceDay.getId());
        List<ExpectedExerciseState> updatedSourceList = expectedSource.exercises().stream()
                .filter(e -> !Objects.equals(e.assignmentId(), removedSourceId))
                .toList();
        expectedByDayId.put(sourceDay.getId(), withExercises(expectedSource, updatedSourceList));

        // 2. Activate destination day if currently rest day & add destination exactly once
        ExpectedDayState expectedDest = expectedByDayId.get(destDay.getId());
        expectedDest = activateRestDayIfNeeded(destDay, exercise, expectedDest);

        WorkoutPlanExerciseResponse created = workoutPlanExerciseService.add(userId, planId, destDay.getId(), addReq);
        List<ExpectedExerciseState> updatedDestList = new ArrayList<>(expectedDest.exercises());
        updatedDestList.add(new ExpectedExerciseState(
                created.getId(),
                exercise.getId(),
                addReq.getSets(),
                addReq.getReps(),
                addReq.getDurationSeconds(),
                addReq.getRestSeconds()
        ));
        expectedByDayId.put(destDay.getId(), withExercises(expectedDest, updatedDestList));
    }

    private void executeReorder(
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
        WorkoutPlanDay day = requireDay(planId, dayId);
        ExercisePrescriptionValues proposed = requireProposed(op);
        List<Long> orderedIds = proposed.getOrderedAssignmentIds();
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new IllegalStateException("REORDER_EXERCISES requires orderedAssignmentIds");
        }

        List<WorkoutPlanExercise> current = workoutPlanExerciseRepository
                .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
        Map<Long, WorkoutPlanExercise> byId = new LinkedHashMap<>();
        for (WorkoutPlanExercise pe : current) {
            byId.put(pe.getId(), pe);
        }
        if (byId.size() != orderedIds.size() || !byId.keySet().containsAll(orderedIds)) {
            throw new IllegalStateException("REORDER_EXERCISES assignment IDs do not match current day exercises");
        }

        int maxOrder = current.stream().mapToInt(WorkoutPlanExercise::getDisplayOrder).max().orElse(0);
        int offset = maxOrder + current.size() + 1;
        for (int i = 0; i < current.size(); i++) {
            current.get(i).setDisplayOrder(offset + i);
        }
        workoutPlanExerciseRepository.saveAllAndFlush(current);

        List<WorkoutPlanExercise> reordered = new ArrayList<>(orderedIds.size());
        for (int i = 0; i < orderedIds.size(); i++) {
            WorkoutPlanExercise pe = byId.get(orderedIds.get(i));
            pe.setDisplayOrder(i + 1);
            reordered.add(pe);
        }
        workoutPlanExerciseRepository.saveAllAndFlush(reordered);

        ExpectedDayState expectedDay = expectedByDayId.get(day.getId());
        Map<Long, ExpectedExerciseState> expectedById = new HashMap<>();
        for (ExpectedExerciseState es : expectedDay.exercises()) {
            expectedById.put(es.assignmentId(), es);
        }
        List<ExpectedExerciseState> updatedList = orderedIds.stream()
                .map(expectedById::get)
                .toList();
        expectedByDayId.put(day.getId(), withExercises(expectedDay, updatedList));
    }

    private void executeUpdateDay(
            Long planId,
            WorkoutPlanChangeOperation op,
            Map<Long, ExpectedDayState> expectedByDayId
    ) {
        Long dayId = op.getDestinationDayId() != null ? op.getDestinationDayId() : op.getSourceDayId();
        WorkoutPlanDay day = requireDay(planId, dayId);
        ExercisePrescriptionValues proposed = requireProposed(op);

        if (proposed.getDayName() != null) {
            day.setName(proposed.getDayName());
        }
        if (proposed.getRestDay() != null) {
            day.setRestDay(proposed.getRestDay());
        }
        if (proposed.getSessionType() != null && !proposed.getSessionType().isBlank()) {
            try {
                day.setSessionType(WorkoutSessionType.valueOf(proposed.getSessionType()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        workoutPlanDayRepository.save(day);

        ExpectedDayState expectedDay = expectedByDayId.get(day.getId());
        expectedByDayId.put(day.getId(), new ExpectedDayState(
                day.getId(),
                day.getDayOfWeek(),
                day.getName(),
                day.getSessionType(),
                day.isRestDay(),
                expectedDay.exercises()
        ));
    }

    private ExpectedDayState activateRestDayIfNeeded(
            WorkoutPlanDay destDay,
            Exercise exercise,
            ExpectedDayState expectedDest
    ) {
        if (!destDay.isRestDay()) {
            return expectedDest;
        }
        destDay.setRestDay(false);
        if (destDay.getName() == null || destDay.getName().isBlank()
                || destDay.getName().toLowerCase(Locale.US).contains("rest")) {
            destDay.setName(exercise.getCategory() != null
                    ? exercise.getCategory().name() + " Day"
                    : "Custom Training");
        }
        if (destDay.getSessionType() == null || destDay.getSessionType() == WorkoutSessionType.REST) {
            destDay.setSessionType(inferSessionTypeFromCategory(exercise.getCategory()));
        }
        workoutPlanDayRepository.save(destDay);
        return new ExpectedDayState(
                destDay.getId(),
                destDay.getDayOfWeek(),
                destDay.getName(),
                destDay.getSessionType(),
                destDay.isRestDay(),
                expectedDest.exercises()
        );
    }

    private WorkoutPlanDay requireDay(Long planId, Long dayId) {
        if (dayId == null) {
            throw new IllegalStateException("Workout day ID is required");
        }
        return workoutPlanDayRepository.findByIdAndWorkoutPlanId(dayId, planId)
                .orElseThrow(() -> new IllegalStateException("Workout day not found in plan: dayId=" + dayId));
    }

    private ExercisePrescriptionValues requireProposed(WorkoutPlanChangeOperation op) {
        if (op.getProposedValues() == null) {
            throw new IllegalStateException("Proposed values missing for " + op.getOperationType());
        }
        return op.getProposedValues();
    }

    private WorkoutPlanExerciseRequest toExerciseRequest(
            Long exerciseId,
            ExercisePrescriptionValues proposed,
            WorkoutPlanExercise fallbackSource
    ) {
        WorkoutPlanExerciseRequest req = new WorkoutPlanExerciseRequest();
        req.setExerciseId(exerciseId);

        Integer sets = proposed.getSets() != null
                ? proposed.getSets()
                : (fallbackSource != null && fallbackSource.getSets() != null ? fallbackSource.getSets() : 3);
        Integer restSeconds = proposed.getRestSeconds() != null
                ? proposed.getRestSeconds()
                : (fallbackSource != null && fallbackSource.getRestSeconds() != null ? fallbackSource.getRestSeconds() : 90);
        Integer reps = proposed.getReps();
        Integer durationSeconds = proposed.getDurationSeconds();
        if (reps == null && durationSeconds == null && fallbackSource != null) {
            reps = fallbackSource.getReps();
            durationSeconds = fallbackSource.getDurationSeconds();
        }
        if (reps == null && durationSeconds == null) {
            reps = 10;
        }
        String notes = proposed.getNotes() != null
                ? proposed.getNotes()
                : (fallbackSource != null ? fallbackSource.getNotes() : null);

        req.setSets(sets);
        req.setReps(reps);
        req.setDurationSeconds(durationSeconds);
        req.setRestSeconds(restSeconds);
        req.setNotes(notes);
        return req;
    }

    private void resequenceDayExercises(Long dayId) {
        workoutPlanExerciseRepository.flush();
        List<WorkoutPlanExercise> remaining = workoutPlanExerciseRepository
                .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(dayId);
        if (!remaining.isEmpty()) {
            int maxOrder = remaining.stream().mapToInt(WorkoutPlanExercise::getDisplayOrder).max().orElse(0);
            int offset = maxOrder + remaining.size() + 1;
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).setDisplayOrder(offset + i);
            }
            workoutPlanExerciseRepository.saveAllAndFlush(remaining);
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).setDisplayOrder(i + 1);
            }
            workoutPlanExerciseRepository.saveAllAndFlush(remaining);
        }
    }

    private Map<Long, ExpectedDayState> captureExpectedState(List<WorkoutPlanDay> days) {
        Map<Long, ExpectedDayState> map = new LinkedHashMap<>();
        for (WorkoutPlanDay day : days) {
            List<ExpectedExerciseState> exs = workoutPlanExerciseRepository
                    .findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId())
                    .stream()
                    .map(pe -> new ExpectedExerciseState(
                            pe.getId(),
                            pe.getExercise().getId(),
                            pe.getSets(),
                            pe.getReps(),
                            pe.getDurationSeconds(),
                            pe.getRestSeconds()
                    ))
                    .toList();
            map.put(day.getId(), new ExpectedDayState(
                    day.getId(),
                    day.getDayOfWeek(),
                    day.getName(),
                    day.getSessionType(),
                    day.isRestDay(),
                    exs
            ));
        }
        return map;
    }

    private ExpectedDayState withExercises(ExpectedDayState day, List<ExpectedExerciseState> exercises) {
        return new ExpectedDayState(
                day.dayId(),
                day.dayOfWeek(),
                day.name(),
                day.sessionType(),
                day.restDay(),
                exercises
        );
    }

    private void verifyDayMatchesExpected(
            WorkoutPlanDay actualDay,
            List<WorkoutPlanExercise> actualExercises,
            ExpectedDayState expected,
            boolean wasAllowedModified
    ) {
        if (!Objects.equals(actualDay.getName(), expected.name())
                || !Objects.equals(actualDay.getSessionType(), expected.sessionType())
                || actualDay.isRestDay() != expected.restDay()) {
            throw new IllegalStateException("Day metadata mismatch after persistence on " + actualDay.getDayOfWeek());
        }

        if (actualExercises.size() != expected.exercises().size()) {
            throw new IllegalStateException(
                    (wasAllowedModified ? "Persisted exercise count mismatch on " : "Unexpected mutation on non-target day ")
                            + actualDay.getDayOfWeek()
                            + ": expected=" + expected.exercises().size()
                            + ", actual=" + actualExercises.size()
            );
        }

        for (int i = 0; i < actualExercises.size(); i++) {
            WorkoutPlanExercise actual = actualExercises.get(i);
            ExpectedExerciseState exp = expected.exercises().get(i);
            if (!Objects.equals(actual.getId(), exp.assignmentId())
                    || !Objects.equals(actual.getExercise().getId(), exp.exerciseId())
                    || !Objects.equals(actual.getSets(), exp.sets())
                    || !Objects.equals(actual.getReps(), exp.reps())
                    || !Objects.equals(actual.getDurationSeconds(), exp.durationSeconds())
                    || !Objects.equals(actual.getRestSeconds(), exp.restSeconds())) {
                throw new IllegalStateException(
                        "Persisted exercise state mismatch on " + actualDay.getDayOfWeek()
                                + " at index " + i + " (assignmentId=" + actual.getId() + ")"
                );
            }
        }
    }

    private static WorkoutSessionType inferSessionTypeFromCategory(ExerciseCategory category) {
        if (category == null) {
            return WorkoutSessionType.CUSTOM;
        }
        return switch (category) {
            case PUSH -> WorkoutSessionType.PUSH;
            case PULL -> WorkoutSessionType.PULL;
            case LEGS -> WorkoutSessionType.LEGS;
            case SKILL -> WorkoutSessionType.SKILL_TRAINING;
            case FLEXIBILITY -> WorkoutSessionType.MOBILITY;
            default -> WorkoutSessionType.CUSTOM;
        };
    }
}
