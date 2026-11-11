package com.calisthenics.backend.coachclient.service.impl;

import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentStatusRequest;
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

    private static final List<WorkoutPlanAssignmentStatus> EXCLUDED_TERMINAL_STATUSES = List.of(
            WorkoutPlanAssignmentStatus.CANCELLED,
            WorkoutPlanAssignmentStatus.ENDED,
            WorkoutPlanAssignmentStatus.REVOKED
    );

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

        // Reuse an inactive row so re-assigning a previously deactivated plan does not
        // create a duplicate assignment or discard its history.
        WorkoutPlanAssignment inactiveAssignment = assignmentRepository
                .findFirstByRelationshipIdAndWorkoutPlanIdAndStatusOrderByCreatedAtDesc(
                        relationshipId, request.getWorkoutPlanId(), WorkoutPlanAssignmentStatus.INACTIVE)
                .orElse(null);
        if (inactiveAssignment != null) {
            inactiveAssignment.setStatus(WorkoutPlanAssignmentStatus.ACTIVE);
            inactiveAssignment.setStartDate(start);
            inactiveAssignment.setEndDate(request.getEndDate());
            inactiveAssignment.setNotes(request.getNotes());
            return toResponse(assignmentRepository.saveAndFlush(inactiveAssignment));
        }

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
        return toResponse(assignmentRepository.saveAndFlush(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForRelationship(Long coachId, Long relationshipId) {
        return listForRelationship(coachId, relationshipId, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForRelationship(
            Long coachId, Long relationshipId, boolean includeCancelled, WorkoutPlanAssignmentStatus status) {
        CoachClientRelationship relationship = findRelationship(relationshipId);
        requireCoach(relationship, coachId);
        List<WorkoutPlanAssignment> assignments;
        if (status != null) {
            assignments = assignmentRepository.findByRelationshipIdAndStatusOrderByCreatedAtDesc(relationshipId, status);
        } else if (includeCancelled) {
            assignments = assignmentRepository.findByRelationshipIdOrderByCreatedAtDesc(relationshipId);
        } else {
            assignments = assignmentRepository.findByRelationshipIdAndStatusNotInOrderByCreatedAtDesc(
                    relationshipId, EXCLUDED_TERMINAL_STATUSES);
        }
        return assignments.stream()
                .filter(assignment -> status != null || includeCancelled || isActiveOrManuallyPaused(assignment))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForClient(Long clientId) {
        return listForClient(clientId, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForClient(
            Long clientId, boolean includeCancelled, WorkoutPlanAssignmentStatus status) {
        List<WorkoutPlanAssignment> assignments;
        if (status != null) {
            assignments = assignmentRepository.findByClientIdAndStatusOrderByCreatedAtDesc(clientId, status);
        } else if (includeCancelled) {
            assignments = assignmentRepository.findByClientIdOrderByCreatedAtDesc(clientId);
        } else {
            assignments = assignmentRepository.findByClientIdAndStatusNotInOrderByCreatedAtDesc(
                    clientId, EXCLUDED_TERMINAL_STATUSES);
        }
        return assignments.stream()
                .filter(assignment -> status != null || includeCancelled || isActiveOrManuallyPaused(assignment))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForCoach(Long coachId) {
        return listForCoach(coachId, false, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkoutPlanAssignmentResponse> listForCoach(
            Long coachId, boolean includeCancelled, WorkoutPlanAssignmentStatus status) {
        List<WorkoutPlanAssignment> assignments;
        if (status != null) {
            assignments = assignmentRepository.findByCoachIdAndStatusOrderByCreatedAtDesc(coachId, status);
        } else if (includeCancelled) {
            assignments = assignmentRepository.findByCoachIdOrderByCreatedAtDesc(coachId);
        } else {
            assignments = assignmentRepository.findByCoachIdAndStatusNotInOrderByCreatedAtDesc(
                    coachId, EXCLUDED_TERMINAL_STATUSES);
        }
        return assignments.stream()
                .filter(assignment -> status != null || includeCancelled || isActiveOrManuallyPaused(assignment))
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

        if (assignment.getStatus() == WorkoutPlanAssignmentStatus.CANCELLED
                || assignment.getStatus() == WorkoutPlanAssignmentStatus.ENDED
                || assignment.getStatus() == WorkoutPlanAssignmentStatus.REVOKED
                || assignment.getStatus() == WorkoutPlanAssignmentStatus.COMPLETED) {
            throw new WorkoutPlanAssignmentConflictException();
        }
        if (status == WorkoutPlanAssignmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Assignments cannot be manually set to CANCELLED");
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

    @Override
    @Transactional
    public WorkoutPlanAssignmentResponse updateStatus(
            Long coachId, Long assignmentId, UpdateWorkoutPlanAssignmentStatusRequest request) {
        WorkoutPlanAssignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(WorkoutPlanAssignmentNotFoundException::new);
        if (!assignment.getCoach().getId().equals(coachId)) {
            // Do not disclose another coach's assignment to the caller.
            throw new WorkoutPlanAssignmentNotFoundException();
        }

        WorkoutPlanAssignmentStatus targetStatus = request.getStatus();
        if (targetStatus != WorkoutPlanAssignmentStatus.ACTIVE
                && targetStatus != WorkoutPlanAssignmentStatus.INACTIVE) {
            throw new IllegalArgumentException("Assignment status must be ACTIVE or INACTIVE");
        }

        WorkoutPlanAssignmentStatus currentStatus = assignment.getStatus();
        if (currentStatus != targetStatus) {
            if (currentStatus != WorkoutPlanAssignmentStatus.ACTIVE
                    && currentStatus != WorkoutPlanAssignmentStatus.INACTIVE) {
                throw new WorkoutPlanAssignmentConflictException();
            }
            if (targetStatus == WorkoutPlanAssignmentStatus.ACTIVE) {
                requireActiveRelationship(assignment.getRelationship());
            }
            assignment.setStatus(targetStatus);
        }

        return toResponse(assignmentRepository.saveAndFlush(assignment));
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

    private boolean isActiveOrManuallyPaused(WorkoutPlanAssignment assignment) {
        if (EXCLUDED_TERMINAL_STATUSES.contains(assignment.getStatus())) {
            return false;
        }
        return assignment.getRelationship() == null
                || assignment.getRelationship().getStatus() == CoachClientRelationshipStatus.ACTIVE;
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
                .workoutPlanId(assignment.getWorkoutPlan().getId())
                .coachId(assignment.getCoach().getId())
                .coachName(assignment.getCoach().getFullName())
                .clientId(assignment.getClient().getId())
                .athleteId(assignment.getClient().getId())
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
