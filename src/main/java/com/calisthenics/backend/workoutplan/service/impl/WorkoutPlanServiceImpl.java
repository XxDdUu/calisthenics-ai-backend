package com.calisthenics.backend.workoutplan.service.impl;

import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.UpdateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanDayRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanDayResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanTemplateResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.exception.ExerciseCatalogEntryNotFoundException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanExerciseNotFoundException;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanNotFoundException;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkoutPlanServiceImpl implements WorkoutPlanService {

    private static final Map<WorkoutSplitType, List<WorkoutPlanDayResponse>> TEMPLATES = createTemplates();

    private final WorkoutPlanRepository workoutPlanRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanMapper workoutPlanMapper;
    private final WorkoutPlanAssignmentRepository assignmentRepository;

    @Override
    @Transactional
    public WorkoutPlanResponse create(Long userId, CreateWorkoutPlanRequest request) {
        List<WorkoutPlanDayRequest> days = validateDays(request);
        User user = userRepository.findById(userId)
                .orElseThrow(WorkoutPlanNotFoundException::new);

        WorkoutPlan plan = WorkoutPlan.builder()
                .user(user)
                .name(request.getName())
                .splitType(request.getSplitType())
                .build();
        addDays(plan, days);

        return workoutPlanMapper.toResponse(workoutPlanRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanResponse> listForUser(Long userId) {
        return workoutPlanRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(workoutPlanMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutPlanResponse get(Long userId, Long planId) {
        WorkoutPlan plan = workoutPlanRepository.findByIdAndUserId(planId, userId)
                .orElseGet(() -> findAssignedPlan(userId, planId));
        return workoutPlanMapper.toResponse(plan);
    }

    @Override
    @Transactional
    public WorkoutPlanResponse update(Long userId, Long planId, UpdateWorkoutPlanRequest request) {
        List<WorkoutPlanDayRequest> requestedDays = validateDays(request);
        WorkoutPlan plan = findOwnedPlan(userId, planId);
        plan.setName(request.getName());
        if (request.getSplitType() != null) {
            plan.setSplitType(request.getSplitType());
        }

        Map<WorkoutDayOfWeek, WorkoutPlanDay> existingDays = new EnumMap<>(WorkoutDayOfWeek.class);
        for (WorkoutPlanDay day : plan.getDays()) {
            existingDays.put(day.getDayOfWeek(), day);
        }
        Set<WorkoutDayOfWeek> requestedWeekdays = new HashSet<>();

        for (WorkoutPlanDayRequest requestedDay : requestedDays) {
            requestedWeekdays.add(requestedDay.getDayOfWeek());
            WorkoutPlanDay day = existingDays.get(requestedDay.getDayOfWeek());
            if (day == null) {
                day = createDay(plan, requestedDay);
                plan.getDays().add(day);
            } else {
                day.setSessionType(requestedDay.getSessionType());
                day.setName(requestedDay.getName());
                if (requestedDay.getExercises() != null) {
                    synchronizeExercises(day, requestedDay.getExercises());
                }
            }
        }

        plan.getDays().removeIf(day -> !requestedWeekdays.contains(day.getDayOfWeek()));
        return workoutPlanMapper.toResponse(workoutPlanRepository.save(plan));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long planId) {
        workoutPlanRepository.delete(findOwnedPlan(userId, planId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanTemplateResponse> getTemplates() {
        return TEMPLATES.entrySet().stream()
                .map(entry -> WorkoutPlanTemplateResponse.builder()
                        .splitType(entry.getKey())
                        .days(entry.getValue())
                        .build())
                .toList();
    }

    private WorkoutPlan findOwnedPlan(Long userId, Long planId) {
        return workoutPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(WorkoutPlanNotFoundException::new);
    }

    private WorkoutPlan findAssignedPlan(Long clientId, Long planId) {
        return assignmentRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(assignment -> assignment.getWorkoutPlan().getId().equals(planId)
                        && isReadableAssignment(assignment))
                .map(WorkoutPlanAssignment::getWorkoutPlan)
                .findFirst()
                .orElseThrow(WorkoutPlanNotFoundException::new);
    }

    private boolean isReadableAssignment(WorkoutPlanAssignment assignment) {
        return assignment.getStatus() == WorkoutPlanAssignmentStatus.ACTIVE
                && assignment.getRelationship().getStatus() == CoachClientRelationshipStatus.ACTIVE;
    }

    private List<WorkoutPlanDayRequest> validateDays(WorkoutPlanRequest request) {
        List<WorkoutPlanDayRequest> days = request.getDays();
        if (days == null || days.isEmpty() || days.size() > WorkoutDayOfWeek.values().length) {
            throw new IllegalArgumentException("A workout plan must contain between one and seven days");
        }

        Set<WorkoutDayOfWeek> weekdays = new HashSet<>();
        for (WorkoutPlanDayRequest day : days) {
            if (day.getDayOfWeek() == null) {
                throw new IllegalArgumentException("Each day requires a weekday");
            }
            if (day.getSessionType() == null) {
                day.setSessionType(inferSessionType(day.getName()));
            }
            if (!weekdays.add(day.getDayOfWeek())) {
                throw new IllegalArgumentException("Each weekday may appear only once");
            }
            validateExercises(day.getExercises());
        }

        if (request.getSplitType() == null) {
            request.setSplitType(inferSplitType(days));
        }

        return days;
    }

    private WorkoutSessionType inferSessionType(String dayName) {
        if (dayName == null || dayName.isBlank()) {
            return WorkoutSessionType.FULL_BODY;
        }
        String lower = dayName.toLowerCase();
        if (lower.contains("push")) {
            return WorkoutSessionType.PUSH;
        } else if (lower.contains("pull")) {
            return WorkoutSessionType.PULL;
        } else if (lower.contains("leg")) {
            return WorkoutSessionType.LEGS;
        } else if (lower.contains("upper")) {
            return WorkoutSessionType.UPPER;
        } else if (lower.contains("lower")) {
            return WorkoutSessionType.LOWER;
        } else if (lower.contains("rest")) {
            return WorkoutSessionType.REST;
        }
        return WorkoutSessionType.FULL_BODY;
    }

    private WorkoutSplitType inferSplitType(List<WorkoutPlanDayRequest> days) {
        boolean hasPpl = false;
        boolean hasUpperLower = false;
        for (WorkoutPlanDayRequest day : days) {
            WorkoutSessionType st = day.getSessionType();
            if (st == WorkoutSessionType.PUSH || st == WorkoutSessionType.PULL || st == WorkoutSessionType.LEGS) {
                hasPpl = true;
            } else if (st == WorkoutSessionType.UPPER || st == WorkoutSessionType.LOWER) {
                hasUpperLower = true;
            }
        }
        if (hasPpl) {
            return WorkoutSplitType.PPL;
        } else if (hasUpperLower) {
            return WorkoutSplitType.UPPER_LOWER;
        }
        return WorkoutSplitType.FULL_BODY;
    }

    private void validateExercises(List<WorkoutPlanExerciseRequest> exercises) {
        if (exercises == null) {
            return;
        }

        Set<Integer> displayOrders = new HashSet<>();
        Set<Long> assignmentIds = new HashSet<>();
        for (int i = 0; i < exercises.size(); i++) {
            WorkoutPlanExerciseRequest exercise = exercises.get(i);
            if (exercise.getExerciseId() == null) {
                throw new IllegalArgumentException("Each exercise requires an exercise ID");
            }
            if (exercise.getSets() == null) {
                exercise.setSets(3);
            }
            if (exercise.getRestSeconds() == null) {
                exercise.setRestSeconds(60);
            }
            if (exercise.getDisplayOrder() == null) {
                exercise.setDisplayOrder(i + 1);
            }
            if (exercise.getReps() == null && exercise.getDurationSeconds() == null) {
                exercise.setReps(10);
            }
            if (!displayOrders.add(exercise.getDisplayOrder())) {
                throw new IllegalArgumentException("Exercise display orders must be unique within a day");
            }
            if (exercise.getId() != null && !assignmentIds.add(exercise.getId())) {
                throw new IllegalArgumentException("An exercise assignment may appear only once in a day");
            }
        }
    }

    private void addDays(WorkoutPlan plan, List<WorkoutPlanDayRequest> days) {
        for (WorkoutPlanDayRequest day : days) {
            plan.getDays().add(createDay(plan, day));
        }
    }

    private WorkoutPlanDay createDay(WorkoutPlan plan, WorkoutPlanDayRequest request) {
        WorkoutPlanDay day = WorkoutPlanDay.builder()
                .workoutPlan(plan)
                .dayOfWeek(request.getDayOfWeek())
                .sessionType(request.getSessionType())
                .name(request.getName())
                .build();
        synchronizeExercises(day, request.getExercises());
        return day;
    }

    /**
     * Reconciles a day's exercise assignments in one transaction. Existing assignments are
     * retained when their id is supplied. For older clients that do not send ids, matching by
     * exercise ID and then display order keeps stable assignments whenever possible.
     */
    private void synchronizeExercises(WorkoutPlanDay day, List<WorkoutPlanExerciseRequest> requestedExercises) {
        List<WorkoutPlanExerciseRequest> requests = requestedExercises == null
                ? List.of() : requestedExercises.stream()
                .sorted(Comparator.comparing(WorkoutPlanExerciseRequest::getDisplayOrder))
                .toList();
        List<WorkoutPlanExercise> existing = new ArrayList<>(day.getExercises());
        Map<WorkoutPlanExercise, Integer> originalDisplayOrders = new IdentityHashMap<>();
        for (WorkoutPlanExercise assignment : existing) {
            originalDisplayOrders.put(assignment, assignment.getDisplayOrder());
        }

        if (!existing.isEmpty()) {
            parkExistingExerciseOrders(existing, requests.size());
            workoutPlanRepository.flush();
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

        day.getExercises().removeIf(assignment -> !retained.contains(assignment));
        for (WorkoutPlanExercise assignment : synchronizedExercises) {
            if (!day.getExercises().contains(assignment)) {
                day.getExercises().add(assignment);
            }
        }
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
                        .filter(assignment -> originalDisplayOrders.get(assignment).equals(request.getDisplayOrder()))
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

    private static Map<WorkoutSplitType, List<WorkoutPlanDayResponse>> createTemplates() {
        Map<WorkoutSplitType, List<WorkoutPlanDayResponse>> templates = new EnumMap<>(WorkoutSplitType.class);
        templates.put(WorkoutSplitType.PPL, sessions(
                WorkoutSessionType.PUSH, WorkoutSessionType.PULL, WorkoutSessionType.LEGS,
                WorkoutSessionType.PUSH, WorkoutSessionType.PULL, WorkoutSessionType.LEGS,
                WorkoutSessionType.REST));
        templates.put(WorkoutSplitType.UPPER_LOWER, sessions(
                WorkoutSessionType.UPPER, WorkoutSessionType.LOWER, WorkoutSessionType.REST,
                WorkoutSessionType.UPPER, WorkoutSessionType.LOWER, WorkoutSessionType.REST,
                WorkoutSessionType.REST));
        templates.put(WorkoutSplitType.FULL_BODY, sessions(
                WorkoutSessionType.FULL_BODY, WorkoutSessionType.REST, WorkoutSessionType.FULL_BODY,
                WorkoutSessionType.REST, WorkoutSessionType.FULL_BODY, WorkoutSessionType.REST,
                WorkoutSessionType.REST));
        return Collections.unmodifiableMap(templates);
    }

    private static List<WorkoutPlanDayResponse> sessions(WorkoutSessionType... sessionTypes) {
        List<WorkoutPlanDayResponse> days = new ArrayList<>(WorkoutDayOfWeek.values().length);
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            days.add(WorkoutPlanDayResponse.builder()
                    .dayOfWeek(day)
                    .sessionType(sessionTypes[day.getOrder() - 1])
                    .exercises(List.of())
                    .build());
        }
        return List.copyOf(days);
    }
}
