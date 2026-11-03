package com.calisthenics.backend.coachclient.service.impl;

import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.exception.CoachClientRelationshipNotFoundException;
import com.calisthenics.backend.coachclient.exception.WorkoutPlanAssignmentNotFoundException;
import com.calisthenics.backend.coachclient.exception.WorkoutPlanAssignmentConflictException;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.service.WorkoutPlanAssignmentService;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.exception.WorkoutPlanNotFoundException;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkoutPlanAssignmentServiceImpl implements WorkoutPlanAssignmentService {

    private final CoachClientRelationshipRepository relationshipRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final UserRepository userRepository;
    private final WorkoutPlanMapper workoutPlanMapper;

    @Override
    @Transactional
    public WorkoutPlanAssignmentResponse assign(
            Long coachId, Long relationshipId, CreateWorkoutPlanAssignmentRequest request) {
        CoachClientRelationship relationship = relationshipRepository.findByIdForUpdate(relationshipId)
                .orElseThrow(CoachClientRelationshipNotFoundException::new);
        requireCoach(relationship, coachId);
        requireActiveRelationship(relationship);

        User coach = userRepository.findById(coachId)
                .orElseThrow(CoachClientRelationshipNotFoundException::new);
        WorkoutPlan plan = workoutPlanRepository.findByIdAndUserId(request.getWorkoutPlanId(), coachId)
                .orElseThrow(WorkoutPlanNotFoundException::new);
        LocalDate start = request.getStartDate() == null ? LocalDate.now() : request.getStartDate();
        validateDates(start, request.getEndDate());

        WorkoutPlanAssignment assignment = WorkoutPlanAssignment.builder()
                .relationship(relationship)
                .workoutPlan(plan)
                .coach(coach)
                .client(relationship.getClient())
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(start)
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .build();
        return toResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForRelationship(Long coachId, Long relationshipId) {
        CoachClientRelationship relationship = findRelationship(relationshipId);
        requireCoach(relationship, coachId);
        return assignmentRepository.findByRelationshipIdOrderByCreatedAtDesc(relationshipId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForClient(Long clientId) {
        return assignmentRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public WorkoutPlanAssignmentResponse update(
            Long coachId, Long relationshipId, Long assignmentId,
            UpdateWorkoutPlanAssignmentRequest request) {
        WorkoutPlanAssignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(WorkoutPlanAssignmentNotFoundException::new);
        if (!assignment.getCoach().getId().equals(coachId)) {
            throw new WorkoutPlanAssignmentNotFoundException();
        }
        if (!assignment.getRelationship().getId().equals(relationshipId)) {
            throw new WorkoutPlanAssignmentNotFoundException();
        }

        WorkoutPlanAssignmentStatus status = request.getStatus() == null
                ? assignment.getStatus()
                : request.getStatus();
        LocalDate start = request.getStartDate() == null ? assignment.getStartDate() : request.getStartDate();
        LocalDate end = request.getEndDate() == null ? assignment.getEndDate() : request.getEndDate();
        validateDates(start, end);

        if (status == WorkoutPlanAssignmentStatus.ACTIVE
                && assignment.getStatus() == WorkoutPlanAssignmentStatus.ENDED) {
            throw new IllegalArgumentException("Ended plan assignments cannot be reactivated");
        }
        if (status == WorkoutPlanAssignmentStatus.ACTIVE) {
            requireActiveRelationship(assignment.getRelationship());
            end = request.getEndDate();
        } else if (status == WorkoutPlanAssignmentStatus.ENDED && end == null) {
            end = LocalDate.now().isBefore(start) ? start : LocalDate.now();
        }

        assignment.setStatus(status);
        assignment.setStartDate(start);
        assignment.setEndDate(end);
        assignment.setNotes(request.getNotes());
        return toResponse(assignmentRepository.save(assignment));
    }

    private CoachClientRelationship findRelationship(Long relationshipId) {
        return relationshipRepository.findById(relationshipId)
                .orElseThrow(CoachClientRelationshipNotFoundException::new);
    }

    private void requireCoach(CoachClientRelationship relationship, Long coachId) {
        if (!relationship.getCoach().getId().equals(coachId)) {
            throw new CoachClientRelationshipNotFoundException();
        }
    }

    private void requireActiveRelationship(CoachClientRelationship relationship) {
        if (relationship.getStatus() != CoachClientRelationshipStatus.ACTIVE) {
            throw new WorkoutPlanAssignmentConflictException();
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || (end != null && end.isBefore(start))) {
            throw new IllegalArgumentException("Assignment end date cannot be before its start date");
        }
    }

    private WorkoutPlanAssignmentResponse toResponse(WorkoutPlanAssignment assignment) {
        WorkoutPlanResponse plan = workoutPlanMapper.toResponse(assignment.getWorkoutPlan());
        return WorkoutPlanAssignmentResponse.builder()
                .id(assignment.getId())
                .relationshipId(assignment.getRelationship().getId())
                .coachId(assignment.getCoach().getId())
                .coachName(assignment.getCoach().getFullName())
                .clientId(assignment.getClient().getId())
                .clientName(assignment.getClient().getFullName())
                .status(assignment.getStatus())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .notes(assignment.getNotes())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .workoutPlan(plan)
                .build();
    }
}
