package com.calisthenics.backend.workout;

import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.workout.dto.TodayWorkoutCandidateResponse;
import com.calisthenics.backend.workout.dto.TodayWorkoutResolutionResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TodayWorkoutResolver {

    private final CoachClientRelationshipRepository relationshipRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final WorkoutPlanRepository planRepository;
    private final WorkoutPlanMapper planMapper;

    @Transactional(readOnly = true)
    public TodayWorkoutResolutionResponse resolve(Long clientId, LocalDate date, Long activePlanId) {
        ResolutionCandidates resolution = resolveCandidates(clientId, date);
        List<TodayWorkoutCandidateResponse> candidates = resolution.candidates().stream()
                .map(this::toResponse)
                .toList();
        Long selectedPlanId = resolution.candidates().stream()
                .filter(candidate -> candidate.plan().getId().equals(activePlanId))
                .map(candidate -> candidate.plan().getId())
                .findFirst()
                .orElseGet(() -> resolution.candidates().size() == 1
                        ? resolution.candidates().getFirst().plan().getId()
                        : null);
        return new TodayWorkoutResolutionResponse(
                date,
                resolution.activeCoach(),
                resolution.source(),
                resolution.candidates().size() > 1 && selectedPlanId == null,
                selectedPlanId,
                candidates);
    }

    @Transactional(readOnly = true)
    public ResolutionCandidates resolveCandidates(Long clientId, LocalDate date) {
        CoachClientRelationship activeCoach = relationshipRepository
                .findFirstByClientIdAndStatusOrderByUpdatedAtDesc(clientId, CoachClientRelationshipStatus.ACTIVE)
                .orElse(null);
        if (activeCoach != null) {
            Map<Long, ResolvedWorkout> coachPlans = new LinkedHashMap<>();
            for (WorkoutPlanAssignment assignment : assignmentRepository.findByClientIdOrderByCreatedAtDesc(clientId)) {
                if (assignment.getStatus() != WorkoutPlanAssignmentStatus.ACTIVE
                        || !assignment.getRelationship().getId().equals(activeCoach.getId())
                        || assignment.getStartDate().isAfter(date)
                        || (assignment.getEndDate() != null && assignment.getEndDate().isBefore(date))) {
                    continue;
                }
                WorkoutPlan plan = assignment.getWorkoutPlan();
                plan.getDays().stream()
                        .filter(day -> day.getDayOfWeek().getOrder() == date.getDayOfWeek().getValue())
                        .findFirst()
                        .ifPresent(day -> coachPlans.putIfAbsent(
                                plan.getId(), new ResolvedWorkout(plan, day, assignment)));
            }
            List<ResolvedWorkout> personalPlans = resolvePersonalPlans(clientId, date);
            List<ResolvedWorkout> candidates = new ArrayList<>(coachPlans.values());
            candidates.addAll(personalPlans);
            if (!candidates.isEmpty()) {
                return new ResolutionCandidates(
                        true,
                        !coachPlans.isEmpty() ? WorkoutSessionSource.COACH : WorkoutSessionSource.PERSONAL,
                        List.copyOf(candidates));
            }
        }

        List<ResolvedWorkout> personalPlans = resolvePersonalPlans(clientId, date);
        return new ResolutionCandidates(
                activeCoach != null,
                personalPlans.isEmpty() ? WorkoutSessionSource.NONE : WorkoutSessionSource.PERSONAL,
                List.copyOf(personalPlans));
    }

    private List<ResolvedWorkout> resolvePersonalPlans(Long clientId, LocalDate date) {
        // A saved active personal plan is the default weekly split. The former "all personal
        // plans are candidates" behavior is retained only as a fallback for legacy users who
        // have no active plan yet.
        List<ResolvedWorkout> personalPlans = new ArrayList<>();
        planRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(clientId)
                .ifPresentOrElse(activePlan -> addDayForDate(personalPlans, activePlan, date), () -> {
                    for (WorkoutPlan plan : planRepository.findByUserIdOrderByCreatedAtDesc(clientId)) {
                        addDayForDate(personalPlans, plan, date);
                    }
                });
        return personalPlans;
    }

    private void addDayForDate(List<ResolvedWorkout> results, WorkoutPlan plan, LocalDate date) {
        plan.getDays().stream()
                .filter(day -> day.getDayOfWeek().getOrder() == date.getDayOfWeek().getValue())
                .findFirst()
                .ifPresent(day -> results.add(new ResolvedWorkout(plan, day, null)));
    }

    private TodayWorkoutCandidateResponse toResponse(ResolvedWorkout candidate) {
        WorkoutPlanAssignment assignment = candidate.assignment();
        return new TodayWorkoutCandidateResponse(
                planMapper.toResponse(candidate.plan()),
                planMapper.toDayResponse(candidate.day()),
                assignment == null ? null : assignment.getId(),
                assignment == null ? null : assignment.getCoach().getFullName());
    }

    public record ResolvedWorkout(
            WorkoutPlan plan,
            WorkoutPlanDay day,
            WorkoutPlanAssignment assignment
    ) {
        public WorkoutSessionSource source() {
            return assignment == null ? WorkoutSessionSource.PERSONAL : WorkoutSessionSource.COACH;
        }
    }

    public record ResolutionCandidates(
            boolean activeCoach,
            WorkoutSessionSource source,
            List<ResolvedWorkout> candidates
    ) {
    }
}
