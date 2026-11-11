package com.calisthenics.backend.workoutplan.service.impl;

import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseMediaMapper;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanExerciseResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.exception.ExerciseCatalogEntryNotFoundException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanExerciseNotFoundException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanExerciseOrderConflictException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanNotFoundException;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanExerciseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkoutPlanExerciseServiceImpl implements WorkoutPlanExerciseService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanDayRepository workoutPlanDayRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final CoachClientRelationshipRepository relationshipRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanExerciseResponse> list(Long userId, Long planId, Long dayId) {
        WorkoutPlanDay day = findReadableDay(userId, planId, dayId);
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public WorkoutPlanExerciseResponse add(
            Long userId, Long planId, Long dayId, WorkoutPlanExerciseRequest request) {
        WorkoutPlanDay day = findOwnedDay(userId, planId, dayId);
        Exercise exercise = exerciseRepository.findByIdAndActiveTrue(request.getExerciseId())
                .orElseThrow(ExerciseCatalogEntryNotFoundException::new);
        if (request.getDisplayOrder() == null) {
            int nextOrder = workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(dayId)
                    .stream()
                    .mapToInt(WorkoutPlanExercise::getDisplayOrder)
                    .max()
                    .orElse(0) + 1;
            request.setDisplayOrder(nextOrder);
        }
        if (request.getSets() == null) {
            request.setSets(3);
        }
        if (request.getRestSeconds() == null) {
            request.setRestSeconds(60);
        }
        if (request.getReps() == null && request.getDurationSeconds() == null) {
            request.setReps(10);
        }
        ensureOrderAvailable(dayId, request.getDisplayOrder(), null);

        WorkoutPlanExercise association = WorkoutPlanExercise.builder()
                .workoutPlanDay(day)
                .exercise(exercise)
                .sets(request.getSets())
                .reps(request.getReps())
                .durationSeconds(request.getDurationSeconds())
                .restSeconds(request.getRestSeconds())
                .displayOrder(request.getDisplayOrder())
                .notes(request.getNotes())
                .build();
        day.getExercises().add(association);
        return toResponse(workoutPlanExerciseRepository.save(association));
    }

    @Override
    @Transactional
    public WorkoutPlanExerciseResponse update(
            Long userId, Long planId, Long dayId, Long assignmentId, WorkoutPlanExerciseRequest request) {
        WorkoutPlanDay day = findOwnedDay(userId, planId, dayId);
        WorkoutPlanExercise association = findAssignment(dayId, assignmentId);
        if (request.getDisplayOrder() == null) {
            request.setDisplayOrder(association.getDisplayOrder());
        }
        if (request.getSets() == null) {
            request.setSets(association.getSets());
        }
        if (request.getRestSeconds() == null) {
            request.setRestSeconds(association.getRestSeconds());
        }
        ensureOrderAvailable(dayId, request.getDisplayOrder(), assignmentId);

        Exercise exercise = exerciseRepository.findByIdAndActiveTrue(request.getExerciseId())
                .orElseThrow(ExerciseCatalogEntryNotFoundException::new);
        association.setExercise(exercise);
        association.setSets(request.getSets());
        association.setReps(request.getReps());
        association.setDurationSeconds(request.getDurationSeconds());
        association.setRestSeconds(request.getRestSeconds());
        association.setDisplayOrder(request.getDisplayOrder());
        association.setNotes(request.getNotes());
        return toResponse(workoutPlanExerciseRepository.save(association));
    }

    @Override
    @Transactional
    public List<WorkoutPlanExerciseResponse> updateAll(
            Long userId, Long planId, Long dayId, List<WorkoutPlanExerciseRequest> exercises) {
        WorkoutPlanDay day = findOwnedDay(userId, planId, dayId);
        synchronizeExercises(day, exercises);
        workoutPlanDayRepository.saveAndFlush(day);
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void synchronizeExercises(WorkoutPlanDay day, List<WorkoutPlanExerciseRequest> requestedExercises) {
        List<WorkoutPlanExerciseRequest> requests = normalizeRequests(requestedExercises);
        List<WorkoutPlanExercise> existing = new ArrayList<>(
                workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId()));
        Map<WorkoutPlanExercise, Integer> originalDisplayOrders = new IdentityHashMap<>();
        for (WorkoutPlanExercise assignment : existing) {
            originalDisplayOrders.put(assignment, assignment.getDisplayOrder());
        }

        if (!existing.isEmpty()) {
            parkExistingExerciseOrders(existing, requests.size());
            workoutPlanExerciseRepository.saveAllAndFlush(existing);
        }

        Map<Long, WorkoutPlanExercise> existingById = new HashMap<>();
        for (WorkoutPlanExercise assignment : existing) {
            existingById.put(assignment.getId(), assignment);
        }
        Set<WorkoutPlanExercise> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        List<WorkoutPlanExercise> synchronizedExercises = new ArrayList<>(requests.size());

        for (int index = 0; index < requests.size(); index++) {
            WorkoutPlanExerciseRequest request = requests.get(index);
            WorkoutPlanExercise assignment = findMatchingAssignment(
                    existing, existingById, originalDisplayOrders, retained, request);
            if (assignment == null) {
                assignment = WorkoutPlanExercise.builder().workoutPlanDay(day).build();
            }

            applyExerciseRequest(assignment, request, index + 1);
            retained.add(assignment);
            synchronizedExercises.add(assignment);
        }

        List<WorkoutPlanExercise> toRemove = existing.stream()
                .filter(a -> !retained.contains(a))
                .toList();
        if (!toRemove.isEmpty()) {
            day.getExercises().removeAll(toRemove);
            workoutPlanExerciseRepository.deleteAll(toRemove);
            workoutPlanExerciseRepository.flush();
        }

        for (WorkoutPlanExercise assignment : synchronizedExercises) {
            workoutPlanExerciseRepository.save(assignment);
        }
        day.getExercises().clear();
        day.getExercises().addAll(synchronizedExercises);
        workoutPlanExerciseRepository.flush();
    }

    private List<WorkoutPlanExerciseRequest> normalizeRequests(List<WorkoutPlanExerciseRequest> requestedExercises) {
        if (requestedExercises == null) {
            return List.of();
        }
        List<WorkoutPlanExerciseRequest> list = new ArrayList<>(requestedExercises);
        for (int i = 0; i < list.size(); i++) {
            WorkoutPlanExerciseRequest req = list.get(i);
            if (req.getSets() == null) {
                req.setSets(3);
            }
            if (req.getRestSeconds() == null) {
                req.setRestSeconds(60);
            }
            if (req.getDisplayOrder() == null) {
                req.setDisplayOrder(i + 1);
            }
            if (req.getReps() == null && req.getDurationSeconds() == null) {
                req.setReps(10);
            }
        }
        list.sort(Comparator.comparing(WorkoutPlanExerciseRequest::getDisplayOrder));
        return list;
    }

    private WorkoutPlanExercise findMatchingAssignment(
            List<WorkoutPlanExercise> existing,
            Map<Long, WorkoutPlanExercise> existingById,
            Map<WorkoutPlanExercise, Integer> originalDisplayOrders,
            Set<WorkoutPlanExercise> retained,
            WorkoutPlanExerciseRequest request) {
        if (request.getId() != null) {
            WorkoutPlanExercise assignment = existingById.get(request.getId());
            if (assignment == null) {
                throw new WorkoutPlanExerciseNotFoundException();
            }
            return assignment;
        }

        return existing.stream()
                .filter(assignment -> !retained.contains(assignment))
                .filter(assignment -> assignment.getExercise().getId().equals(request.getExerciseId()))
                .findFirst()
                .or(() -> existing.stream()
                        .filter(assignment -> !retained.contains(assignment))
                        .filter(assignment -> originalDisplayOrders.get(assignment) != null
                                && originalDisplayOrders.get(assignment).equals(request.getDisplayOrder()))
                        .findFirst())
                .orElse(null);
    }

    private void applyExerciseRequest(
            WorkoutPlanExercise assignment, WorkoutPlanExerciseRequest request, int displayOrder) {
        Exercise exercise = exerciseRepository.findByIdAndActiveTrue(request.getExerciseId())
                .orElseThrow(ExerciseCatalogEntryNotFoundException::new);
        assignment.setExercise(exercise);
        assignment.setSets(request.getSets());
        assignment.setReps(request.getReps());
        assignment.setDurationSeconds(request.getDurationSeconds());
        assignment.setRestSeconds(request.getRestSeconds());
        assignment.setDisplayOrder(displayOrder);
        assignment.setNotes(request.getNotes());
    }

    private void parkExistingExerciseOrders(List<WorkoutPlanExercise> existing, int requestedSize) {
        int greatestOrder = existing.stream()
                .map(WorkoutPlanExercise::getDisplayOrder)
                .max(Integer::compareTo)
                .orElse(0);
        long firstTemporaryOrder = (long) greatestOrder + existing.size() + requestedSize + 1;
        if (firstTemporaryOrder + existing.size() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Unable to reorder exercises with the supplied display orders");
        }
        for (int index = 0; index < existing.size(); index++) {
            existing.get(index).setDisplayOrder((int) firstTemporaryOrder + index);
        }
    }

    @Override
    @Transactional
    public void remove(Long userId, Long planId, Long dayId, Long assignmentId) {
        WorkoutPlanDay day = findOwnedDay(userId, planId, dayId);
        WorkoutPlanExercise assignment = findAssignment(dayId, assignmentId);
        day.getExercises().remove(assignment);
        workoutPlanExerciseRepository.delete(assignment);
    }

    private WorkoutPlanDay findOwnedDay(Long userId, Long planId, Long dayId) {
        workoutPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(WorkoutPlanNotFoundException::new);
        return workoutPlanDayRepository.findByIdAndWorkoutPlanId(dayId, planId)
                .orElseThrow(WorkoutPlanNotFoundException::new);
    }

    private WorkoutPlanDay findReadableDay(Long userId, Long planId, Long dayId) {
        boolean ownsPlan = workoutPlanRepository.findByIdAndUserId(planId, userId).isPresent();
        boolean hasActiveAssignment = !ownsPlan && assignmentRepository.findByClientIdOrderByCreatedAtDesc(userId)
                .stream()
                .anyMatch(assignment -> assignment.getWorkoutPlan().getId().equals(planId)
                        && assignment.getStatus() == WorkoutPlanAssignmentStatus.ACTIVE
                        && assignment.getRelationship().getStatus() == CoachClientRelationshipStatus.ACTIVE);
        boolean isActiveCoachOfOwner = !ownsPlan && !hasActiveAssignment && isActiveCoachOfPlanOwner(userId, planId);
        if (!ownsPlan && !hasActiveAssignment && !isActiveCoachOfOwner) {
            throw new WorkoutPlanNotFoundException();
        }
        return workoutPlanDayRepository.findByIdAndWorkoutPlanId(dayId, planId)
                .orElseThrow(WorkoutPlanNotFoundException::new);
    }

    private boolean isActiveCoachOfPlanOwner(Long coachId, Long planId) {
        return workoutPlanRepository.findWithDetailsById(planId)
                .map(plan -> relationshipRepository.existsByCoachIdAndClientIdAndStatusIn(
                        coachId,
                        plan.getUser().getId(),
                        List.of(CoachClientRelationshipStatus.ACTIVE)))
                .orElse(false);
    }

    private WorkoutPlanExercise findAssignment(Long dayId, Long assignmentId) {
        return workoutPlanExerciseRepository.findByIdAndWorkoutPlanDayId(assignmentId, dayId)
                .orElseThrow(WorkoutPlanExerciseNotFoundException::new);
    }

    private void ensureOrderAvailable(Long dayId, Integer displayOrder, Long assignmentId) {
        boolean inUse = assignmentId == null
                ? workoutPlanExerciseRepository.existsByWorkoutPlanDayIdAndDisplayOrder(dayId, displayOrder)
                : workoutPlanExerciseRepository.existsByWorkoutPlanDayIdAndDisplayOrderAndIdNot(
                        dayId, displayOrder, assignmentId);
        if (inUse) {
            throw new WorkoutPlanExerciseOrderConflictException();
        }
    }

    private WorkoutPlanExerciseResponse toResponse(WorkoutPlanExercise association) {
        Exercise exercise = association.getExercise();
        ExerciseResponse exerciseResponse = ExerciseResponse.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .code(exercise.getCode())
                .category(exercise.getCategory())
                .difficulty(exercise.getDifficulty())
                .description(exercise.getDescription())
                .targetMuscles(exercise.getTargetMuscles())
                .instructions(exercise.getInstructions())
                .imageUrl(exercise.getImageUrl())
                .videoUrl(exercise.getVideoUrl())
                .equipment(exercise.getEquipment())
                .active(exercise.isActive())
                .mediaItems(ExerciseMediaMapper.toResponses(exercise))
                .build();

        return WorkoutPlanExerciseResponse.builder()
                .id(association.getId())
                .exercise(exerciseResponse)
                .sets(association.getSets())
                .reps(association.getReps())
                .durationSeconds(association.getDurationSeconds())
                .restSeconds(association.getRestSeconds())
                .displayOrder(association.getDisplayOrder())
                .notes(association.getNotes())
                .createdAt(association.getCreatedAt())
                .updatedAt(association.getUpdatedAt())
                .build();
    }
}
