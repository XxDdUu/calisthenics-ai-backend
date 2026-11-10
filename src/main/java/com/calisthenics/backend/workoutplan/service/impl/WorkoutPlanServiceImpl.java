package com.calisthenics.backend.workoutplan.service.impl;

import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.DuplicateWorkoutPlanRequest;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkoutPlanServiceImpl implements WorkoutPlanService {

    private static final int DAYS_PER_WEEK = WorkoutDayOfWeek.values().length;
    private static final Map<WorkoutSplitType, List<WorkoutPlanDayResponse>> TEMPLATES = createTemplates();

    private final WorkoutPlanRepository workoutPlanRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanMapper workoutPlanMapper;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final CoachClientRelationshipRepository relationshipRepository;

    @Override
    @Transactional
    public WorkoutPlanResponse create(Long userId, CreateWorkoutPlanRequest request) {
        List<WorkoutPlanDayRequest> days = validateDays(request, true);
        // A user-row lock serializes activation changes, including the first-plan case.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(WorkoutPlanNotFoundException::new);

        boolean shouldActivate = Boolean.TRUE.equals(request.getActive())
                || (request.getActive() == null && !workoutPlanRepository.existsByUserIdAndActiveTrue(userId));
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .splitType(request.getSplitType())
                .active(false)
                .build();
        addDays(plan, days);
        plan = workoutPlanRepository.save(plan);

        if (shouldActivate) {
            activatePlanForLockedUser(userId, plan);
        }
        return workoutPlanMapper.toResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanResponse> listForUser(Long userId) {
        return workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(userId).stream()
                .map(workoutPlanMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanResponse> listForAuthorizedClient(Long coachId, Long clientId) {
        if (relationshipRepository.findActiveRelationshipForUsers(coachId, clientId).isEmpty()) {
            throw new WorkoutPlanNotFoundException();
        }
        return listForUser(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkoutPlanResponse getActive(Long userId) {
        WorkoutPlan plan = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(userId)
                .orElseThrow(WorkoutPlanNotFoundException::new);
        return workoutPlanMapper.toResponse(plan);
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
        List<WorkoutPlanDayRequest> requestedDays = validateDays(request, false);
        WorkoutPlan plan = findOwnedPlan(userId, planId);
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setSplitType(request.getSplitType());

        Map<WorkoutDayOfWeek, WorkoutPlanDay> existingDays = new EnumMap<>(WorkoutDayOfWeek.class);
        for (WorkoutPlanDay day : plan.getDays()) {
            existingDays.put(day.getDayOfWeek(), day);
        }

        for (WorkoutPlanDayRequest requestedDay : requestedDays) {
            WorkoutPlanDay day = existingDays.get(requestedDay.getDayOfWeek());
            if (day == null) {
                day = createDay(plan, requestedDay);
                plan.getDays().add(day);
            } else {
                applyDayRequest(day, requestedDay);
                if (requestedDay.getExercises() != null) {
                    synchronizeExercises(day, requestedDay.getExercises());
                }
            }
        }

        // validateDays has already guaranteed all weekdays, so old/unknown days cannot survive.
        plan.getDays().removeIf(day -> !existingDays.containsKey(day.getDayOfWeek()));
        if (request.getActive() != null) {
            userRepository.findByIdForUpdate(userId).orElseThrow(WorkoutPlanNotFoundException::new);
            if (request.getActive()) {
                activatePlanForLockedUser(userId, plan);
            } else {
                plan.setActive(false);
            }
        }
        WorkoutPlan savedPlan = workoutPlanRepository.save(plan);
        if (requestedDays.size() < DAYS_PER_WEEK) {
            List<WorkoutPlanDay> responseDays = requestedDays.stream()
                    .map(requestedDay -> savedPlan.getDays().stream()
                            .filter(day -> day.getDayOfWeek() == requestedDay.getDayOfWeek())
                            .findFirst()
                            .orElseThrow())
                    .toList();
            return workoutPlanMapper.toResponse(savedPlan, responseDays);
        }
        return workoutPlanMapper.toResponse(savedPlan);
    }

    @Override
    @Transactional
    public WorkoutPlanResponse duplicate(Long userId, Long planId, DuplicateWorkoutPlanRequest request) {
        userRepository.findByIdForUpdate(userId).orElseThrow(WorkoutPlanNotFoundException::new);
        WorkoutPlan source = findOwnedPlan(userId, planId);
        WorkoutPlan copy = WorkoutPlan.builder()
                .user(source.getUser())
                .name(request.getName() == null ? copyName(source.getName()) : request.getName())
                .description(source.getDescription())
                .splitType(source.getSplitType())
                .active(false)
                .build();
        source.getDays().stream()
                .sorted(Comparator.comparing(WorkoutPlanDay::getDisplayOrder))
                .forEach(day -> copy.getDays().add(copyDay(copy, day)));
        return workoutPlanMapper.toResponse(workoutPlanRepository.save(copy));
    }

    @Override
    @Transactional
    public WorkoutPlanResponse activate(Long userId, Long planId) {
        userRepository.findByIdForUpdate(userId).orElseThrow(WorkoutPlanNotFoundException::new);
        WorkoutPlan target = findOwnedPlan(userId, planId);
        activatePlanForLockedUser(userId, target);
        return workoutPlanMapper.toResponse(workoutPlanRepository.save(target));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long planId) {
        WorkoutPlan plan = findOwnedPlan(userId, planId);
        workoutPlanRepository.delete(plan);
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

    private void activatePlanForLockedUser(Long userId, WorkoutPlan target) {
        for (WorkoutPlan plan : workoutPlanRepository.findByUserIdForUpdate(userId)) {
            if (!plan.getId().equals(target.getId()) && plan.isActive()) {
                plan.setActive(false);
                workoutPlanRepository.saveAndFlush(plan);
            }
        }
        target.setActive(true);
        workoutPlanRepository.saveAndFlush(target);
    }

    private List<WorkoutPlanDayRequest> validateDays(WorkoutPlanRequest request, boolean requireCompleteWeek) {
        List<WorkoutPlanDayRequest> days = request.getDays();
        if (days == null || days.isEmpty() || days.size() > DAYS_PER_WEEK) {
            throw new IllegalArgumentException("A workout plan must contain between one and seven days");
        }

        Map<WorkoutDayOfWeek, WorkoutPlanDayRequest> dayMap = new EnumMap<>(WorkoutDayOfWeek.class);
        Set<Integer> displayOrders = new HashSet<>();
        for (WorkoutPlanDayRequest day : days) {
            if (day == null) {
                throw new IllegalArgumentException("Each day configuration may not be null");
            }
            if (day.getDayOfWeek() == null) {
                throw new IllegalArgumentException("Each day requires a weekday");
            }
            if (dayMap.putIfAbsent(day.getDayOfWeek(), day) != null) {
                throw new IllegalArgumentException("Each weekday may appear only once");
            }
            if (day.getDisplayOrder() == null) {
                day.setDisplayOrder(day.getDayOfWeek().getOrder());
            }
            if (!displayOrders.add(day.getDisplayOrder())) {
                throw new IllegalArgumentException("Each display order may appear only once");
            }
            normalizeDayConfiguration(day);
            validateExercises(day.getExercises());
        }

        if (requireCompleteWeek) {
            List<WorkoutPlanDayRequest> normalizedDays = new ArrayList<>(DAYS_PER_WEEK);
            for (WorkoutDayOfWeek weekday : WorkoutDayOfWeek.values()) {
                WorkoutPlanDayRequest day = dayMap.get(weekday);
                if (day == null) {
                    day = new WorkoutPlanDayRequest();
                    day.setDayOfWeek(weekday);
                    day.setDisplayOrder(weekday.getOrder());
                    day.setSessionType(defaultSessionTypeForWeekday(weekday, request.getName(), request.getSplitType()));
                    day.setName(defaultWorkoutNameForWeekday(weekday));
                    dayMap.put(weekday, day);
                }
                normalizedDays.add(day);
            }
            request.setDays(normalizedDays);
            if (request.getSplitType() == null) {
                request.setSplitType(inferSplitType(normalizedDays, request.getName()));
            }
            return normalizedDays;
        }

        if (request.getSplitType() == null) {
            request.setSplitType(inferSplitType(days, request.getName()));
        }
        return days;
    }

    private void normalizeDayConfiguration(WorkoutPlanDayRequest day) {
        if (day.getSessionType() == null) {
            WorkoutSessionType inferred = inferSessionTypeFromText(day.getName());
            if (inferred != null) {
                day.setSessionType(inferred);
            }
        }
        boolean restDay = Boolean.TRUE.equals(day.getRestDay()) || day.getSessionType() == WorkoutSessionType.REST;
        day.setRestDay(restDay);
        if (restDay) {
            day.setSessionType(WorkoutSessionType.REST);
            day.setName(null);
            return;
        }
        if (day.getSessionType() == null && (day.getName() == null || day.getName().isBlank())) {
            throw new IllegalArgumentException("A non-rest day requires a workout type or custom workout name");
        }
    }

    private WorkoutSessionType inferSessionTypeFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase();
        if (normalized.contains("rest")) return WorkoutSessionType.REST;
        if (normalized.contains("push")) return WorkoutSessionType.PUSH;
        if (normalized.contains("pull")) return WorkoutSessionType.PULL;
        if (normalized.contains("legs")) return WorkoutSessionType.LEGS;
        if (normalized.contains("upper")) return WorkoutSessionType.UPPER;
        if (normalized.contains("lower")) return WorkoutSessionType.LOWER;
        if (normalized.contains("full body")) return WorkoutSessionType.FULL_BODY;
        if (normalized.contains("bent arm")) return WorkoutSessionType.BENT_ARM;
        if (normalized.contains("straight arm")) return WorkoutSessionType.STRAIGHT_ARM;
        if (normalized.contains("planche")) return WorkoutSessionType.PLANCHE;
        if (normalized.contains("front lever")) return WorkoutSessionType.FRONT_LEVER;
        if (normalized.contains("handstand")) return WorkoutSessionType.HANDSTAND;
        if (normalized.contains("skill")) return WorkoutSessionType.SKILL_TRAINING;
        if (normalized.contains("strength")) return WorkoutSessionType.STRENGTH;
        if (normalized.contains("hypertrophy")) return WorkoutSessionType.HYPERTROPHY;
        if (normalized.contains("mobility")) return WorkoutSessionType.MOBILITY;
        if (normalized.contains("conditioning")) return WorkoutSessionType.CONDITIONING;
        if (normalized.contains("custom")) return WorkoutSessionType.CUSTOM;
        return null;
    }

    private WorkoutSessionType defaultSessionTypeForWeekday(
            WorkoutDayOfWeek weekday, String planName, WorkoutSplitType splitType) {
        if (planName != null) {
            String normalized = planName.toLowerCase();
            if (normalized.contains("push") && normalized.contains("pull") && normalized.contains("legs")) {
                return switch (weekday) {
                    case MONDAY, THURSDAY -> WorkoutSessionType.PUSH;
                    case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
                    case WEDNESDAY, SATURDAY -> WorkoutSessionType.LEGS;
                    case SUNDAY -> WorkoutSessionType.REST;
                };
            }
            if (normalized.contains("upper") && normalized.contains("lower")) {
                return switch (weekday) {
                    case MONDAY, THURSDAY -> WorkoutSessionType.UPPER;
                    case TUESDAY, FRIDAY -> WorkoutSessionType.LOWER;
                    case WEDNESDAY, SATURDAY -> WorkoutSessionType.REST;
                    case SUNDAY -> WorkoutSessionType.REST;
                };
            }
            if (normalized.contains("full body")) {
                return switch (weekday) {
                    case MONDAY, WEDNESDAY, FRIDAY, SUNDAY -> WorkoutSessionType.FULL_BODY;
                    case TUESDAY, THURSDAY, SATURDAY -> WorkoutSessionType.REST;
                };
            }
        }
        if (splitType != null) {
            return switch (splitType) {
                case PPL -> switch (weekday) {
                    case MONDAY, THURSDAY -> WorkoutSessionType.PUSH;
                    case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
                    case WEDNESDAY, SATURDAY -> WorkoutSessionType.LEGS;
                    case SUNDAY -> WorkoutSessionType.REST;
                };
                case UPPER_LOWER -> switch (weekday) {
                    case MONDAY, THURSDAY -> WorkoutSessionType.UPPER;
                    case TUESDAY, FRIDAY -> WorkoutSessionType.LOWER;
                    case WEDNESDAY, SATURDAY -> WorkoutSessionType.REST;
                    case SUNDAY -> WorkoutSessionType.REST;
                };
                case FULL_BODY -> switch (weekday) {
                    case MONDAY, WEDNESDAY, FRIDAY, SUNDAY -> WorkoutSessionType.FULL_BODY;
                    case TUESDAY, THURSDAY, SATURDAY -> WorkoutSessionType.REST;
                };
                case CUSTOM -> switch (weekday) {
                    case MONDAY, THURSDAY -> WorkoutSessionType.PUSH;
                    case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
                    case WEDNESDAY, SATURDAY -> WorkoutSessionType.LEGS;
                    case SUNDAY -> WorkoutSessionType.REST;
                };
            };
        }
        return switch (weekday) {
            case MONDAY, THURSDAY -> WorkoutSessionType.PUSH;
            case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
            case WEDNESDAY, SATURDAY -> WorkoutSessionType.LEGS;
            case SUNDAY -> WorkoutSessionType.REST;
        };
    }

    private String defaultWorkoutNameForWeekday(WorkoutDayOfWeek weekday) {
        return switch (weekday) {
            case MONDAY -> "Push Day";
            case TUESDAY -> "Pull Day";
            case WEDNESDAY -> "Legs Day";
            case THURSDAY -> "Push Day";
            case FRIDAY -> "Pull Day";
            case SATURDAY -> "Legs Day";
            case SUNDAY -> "Rest Day";
        };
    }

    private WorkoutSplitType inferSplitType(List<WorkoutPlanDayRequest> days, String planName) {
        if (planName != null) {
            String normalized = planName.toLowerCase();
            if (normalized.contains("push") && normalized.contains("pull") && normalized.contains("legs")) {
                return WorkoutSplitType.PPL;
            }
            if (normalized.contains("upper") && normalized.contains("lower")) {
                return WorkoutSplitType.UPPER_LOWER;
            }
            if (normalized.contains("full body")) {
                return WorkoutSplitType.FULL_BODY;
            }
        }

        boolean hasPpl = false;
        boolean hasUpperLower = false;
        boolean hasCustomCategory = false;
        for (WorkoutPlanDayRequest day : days) {
            WorkoutSessionType type = day.getSessionType();
            if (type == null) {
                hasCustomCategory = true;
                continue;
            }
            if (type == WorkoutSessionType.PUSH || type == WorkoutSessionType.PULL || type == WorkoutSessionType.LEGS) {
                hasPpl = true;
            } else if (type == WorkoutSessionType.UPPER || type == WorkoutSessionType.LOWER) {
                hasUpperLower = true;
            } else if (type != WorkoutSessionType.FULL_BODY && type != WorkoutSessionType.REST) {
                hasCustomCategory = true;
            }
        }
        if (hasCustomCategory || (hasPpl && hasUpperLower)) {
            return WorkoutSplitType.CUSTOM;
        }
        if (hasPpl) {
            return WorkoutSplitType.PPL;
        }
        if (hasUpperLower) {
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
                .build();
        applyDayRequest(day, request);
        synchronizeExercises(day, request.getExercises());
        return day;
    }

    private void applyDayRequest(WorkoutPlanDay day, WorkoutPlanDayRequest request) {
        day.setDayOfWeek(request.getDayOfWeek());
        day.setSessionType(request.getSessionType());
        day.setName(request.getName());
        day.setDescription(request.getDescription());
        day.setRestDay(Boolean.TRUE.equals(request.getRestDay()));
        day.setDisplayOrder(request.getDisplayOrder());
    }

    private WorkoutPlanDay copyDay(WorkoutPlan targetPlan, WorkoutPlanDay sourceDay) {
        WorkoutPlanDay copy = WorkoutPlanDay.builder()
                .workoutPlan(targetPlan)
                .dayOfWeek(sourceDay.getDayOfWeek())
                .sessionType(sourceDay.getSessionType())
                .name(sourceDay.getName())
                .description(sourceDay.getDescription())
                .restDay(sourceDay.isRestDay())
                .displayOrder(sourceDay.getDisplayOrder())
                .build();
        sourceDay.getExercises().stream()
                .sorted(Comparator.comparing(WorkoutPlanExercise::getDisplayOrder))
                .forEach(sourceExercise -> copy.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(copy)
                        .exercise(sourceExercise.getExercise())
                        .sets(sourceExercise.getSets())
                        .reps(sourceExercise.getReps())
                        .durationSeconds(sourceExercise.getDurationSeconds())
                        .restSeconds(sourceExercise.getRestSeconds())
                        .displayOrder(sourceExercise.getDisplayOrder())
                        .notes(sourceExercise.getNotes())
                        .build()));
        return copy;
    }

    private String copyName(String sourceName) {
        String prefix = "Copy of ";
        String copy = prefix + sourceName;
        return copy.length() <= 100 ? copy : copy.substring(0, 100);
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
        List<WorkoutPlanDayResponse> days = new ArrayList<>(DAYS_PER_WEEK);
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            WorkoutSessionType type = sessionTypes[day.getOrder() - 1];
            boolean rest = type == WorkoutSessionType.REST;
            days.add(WorkoutPlanDayResponse.builder()
                    .dayOfWeek(day)
                    .sessionType(type)
                    .workoutType(type)
                    .workoutName(rest ? "Rest Day" : type.name().replace('_', ' '))
                    .restDay(rest)
                    .displayOrder(day.getOrder())
                    .exercises(List.of())
                    .build());
        }
        return List.copyOf(days);
    }
}
