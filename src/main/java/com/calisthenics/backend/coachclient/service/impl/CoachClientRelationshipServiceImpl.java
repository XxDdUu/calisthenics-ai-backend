package com.calisthenics.backend.coachclient.service.impl;

import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateClientCoachRequest;
import com.calisthenics.backend.coachclient.dto.response.CoachClientRelationshipResponse;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.exception.CoachClientConflictException;
import com.calisthenics.backend.coachclient.exception.CoachClientForbiddenException;
import com.calisthenics.backend.coachclient.exception.CoachClientRelationshipNotFoundException;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.service.CoachClientRelationshipService;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CoachClientRelationshipServiceImpl implements CoachClientRelationshipService {

    private static final Logger log = LoggerFactory.getLogger(CoachClientRelationshipServiceImpl.class);
    private static final List<CoachClientRelationshipStatus> DUPLICATE_BLOCKING_STATUSES =
            List.of(CoachClientRelationshipStatus.PENDING, CoachClientRelationshipStatus.ACTIVE);
    private static final List<WorkoutPlanAssignmentStatus> CANCELLABLE_ASSIGNMENT_STATUSES =
            List.of(
                    WorkoutPlanAssignmentStatus.ACTIVE,
                    WorkoutPlanAssignmentStatus.INACTIVE,
                    WorkoutPlanAssignmentStatus.UPCOMING);
    private static final List<WorkoutSessionStatus> CANCELLABLE_SESSION_STATUSES =
            List.of(WorkoutSessionStatus.SCHEDULED, WorkoutSessionStatus.IN_PROGRESS);

    private final CoachClientRelationshipRepository relationshipRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CoachClientRelationshipResponse request(Long coachId, CreateCoachClientRequest request) {
        return createRequest(coachId, request.getClientId(), coachId, false);
    }

    @Override
    @Transactional
    public CoachClientRelationshipResponse requestAsClient(Long clientId, CreateClientCoachRequest request) {
        return createRequest(request.getCoachId(), clientId, clientId, true);
    }

    private CoachClientRelationshipResponse createRequest(
            Long coachId, Long clientId, Long requesterId, boolean requireRegularCoach) {
        String operation = requesterId.equals(coachId) ? "coach_to_client" : "client_to_coach";
        Long receiverId = requesterId.equals(coachId) ? clientId : coachId;
        log.info(
                "Coach-client request operation={} senderId={} receiverId={} result=started",
                operation, requesterId, receiverId);
        try {
            if (coachId.equals(clientId)) {
                throw new IllegalArgumentException("A user cannot connect with themselves");
            }

            User coach = userRepository.findById(coachId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);
            User client = userRepository.findByIdForUpdate(clientId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);
            if (requireRegularCoach && coach.getRole() != Role.ROLE_USER) {
                throw new CoachClientRelationshipNotFoundException();
            }

            if (relationshipRepository.existsByCoachIdAndClientIdAndStatusIn(
                    coachId, clientId, DUPLICATE_BLOCKING_STATUSES)) {
                throw new CoachClientConflictException();
            }

            User requester = requesterId.equals(coachId) ? coach : client;
            CoachClientRelationship relationship = CoachClientRelationship.builder()
                    .coach(coach)
                    .client(client)
                    .requestedBy(requester)
                    .status(CoachClientRelationshipStatus.PENDING)
                    .build();
            CoachClientRelationship saved = relationshipRepository.saveAndFlush(relationship);
            log.info(
                    "Coach-client request operation={} senderId={} receiverId={} result=created relationshipId={}",
                    operation, requesterId, receiverId, saved.getId());
            return toResponse(saved);
        } catch (RuntimeException exception) {
            log.warn(
                    "Coach-client request operation={} senderId={} receiverId={} result=failed exceptionType={} message={}",
                    operation, requesterId, receiverId, exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoachClientRelationshipResponse> listForUser(Long userId) {
        return relationshipRepository.findByCoachIdOrClientIdOrderByCreatedAtDesc(userId, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CoachClientRelationshipResponse transition(
            Long userId, Long relationshipId, CoachClientRelationshipStatus targetStatus) {
        if (targetStatus == CoachClientRelationshipStatus.TERMINATED) {
            return terminate(userId, relationshipId);
        }
        String operation = switch (targetStatus) {
            case ACTIVE -> "accept";
            case REJECTED -> "reject";
            case CANCELLED -> "cancel";
            default -> "transition";
        };
        Long senderId = null;
        Long receiverId = null;
        log.info(
                "Coach-client request operation={} actorId={} relationshipId={} result=started",
                operation, userId, relationshipId);
        try {
            CoachClientRelationship relationship = relationshipRepository.findById(relationshipId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);
            Long clientId = relationship.getClient().getId();
            userRepository.findByIdForUpdate(clientId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);
            relationship = relationshipRepository.findByIdForUpdate(relationshipId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);

            Long coachId = relationship.getCoach().getId();
            senderId = relationship.getRequestedBy().getId();
            receiverId = senderId.equals(coachId) ? clientId : coachId;
            boolean isCoach = coachId.equals(userId);
            boolean isClient = relationship.getClient().getId().equals(userId);
            if (!isCoach && !isClient) {
                throw new CoachClientRelationshipNotFoundException();
            }

            if (relationship.getStatus() != CoachClientRelationshipStatus.PENDING
                    && relationship.getStatus() != CoachClientRelationshipStatus.ACTIVE) {
                throw new CoachClientConflictException();
            }

            if (targetStatus == CoachClientRelationshipStatus.ACTIVE) {
                if (!isRespondent(relationship, userId)
                        || relationship.getStatus() != CoachClientRelationshipStatus.PENDING) {
                    throw new CoachClientForbiddenException();
                }
                if (relationshipRepository.existsByClientIdAndStatus(
                        clientId, CoachClientRelationshipStatus.ACTIVE)) {
                    throw new CoachClientConflictException();
                }
                relationship.setStatus(CoachClientRelationshipStatus.ACTIVE);
            } else if (targetStatus == CoachClientRelationshipStatus.REJECTED) {
                if (!isRespondent(relationship, userId)
                        || relationship.getStatus() != CoachClientRelationshipStatus.PENDING) {
                    throw new CoachClientForbiddenException();
                }
                relationship.setStatus(CoachClientRelationshipStatus.REJECTED);
            } else if (targetStatus == CoachClientRelationshipStatus.CANCELLED) {
                if (relationship.getStatus() == CoachClientRelationshipStatus.PENDING
                        && !Objects.equals(senderId, userId)) {
                    throw new CoachClientForbiddenException();
                }
                relationship.setStatus(CoachClientRelationshipStatus.CANCELLED);
                cancelRelationshipAssignments(relationship.getId());
            } else {
                throw new IllegalArgumentException("Unsupported relationship state transition");
            }

            CoachClientRelationship saved = relationshipRepository.saveAndFlush(relationship);
            log.info(
                    "Coach-client request operation={} senderId={} receiverId={} actorId={} relationshipId={} result=updated status={}",
                    operation, senderId, receiverId, userId, relationshipId, saved.getStatus());
            return toResponse(saved);
        } catch (RuntimeException exception) {
            log.warn(
                    "Coach-client request operation={} senderId={} receiverId={} actorId={} relationshipId={} result=failed exceptionType={} message={}",
                    operation, senderId, receiverId, userId, relationshipId,
                    exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
    }

    @Override
    @Transactional
    public CoachClientRelationshipResponse terminate(Long userId, Long relationshipId) {
        log.info(
                "Coach-client request operation=terminate actorId={} relationshipId={} result=started",
                userId, relationshipId);
        try {
            CoachClientRelationship relationship = relationshipRepository.findById(relationshipId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);
            Long clientId = relationship.getClient().getId();
            userRepository.findByIdForUpdate(clientId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);
            relationship = relationshipRepository.findByIdForUpdate(relationshipId)
                    .orElseThrow(CoachClientRelationshipNotFoundException::new);

            Long coachId = relationship.getCoach().getId();
            boolean isCoach = coachId.equals(userId);
            boolean isClient = relationship.getClient().getId().equals(userId);
            if (!isCoach && !isClient) {
                throw new CoachClientForbiddenException();
            }

            if (relationship.getStatus() != CoachClientRelationshipStatus.ACTIVE) {
                throw new CoachClientConflictException();
            }

            relationship.setStatus(CoachClientRelationshipStatus.TERMINATED);
            cancelRelationshipAssignments(relationship.getId());

            CoachClientRelationship saved = relationshipRepository.saveAndFlush(relationship);
            log.info(
                    "Coach-client request operation=terminate actorId={} relationshipId={} coachId={} clientId={} result=updated status={}",
                    userId, relationshipId, coachId, clientId, saved.getStatus());
            return toResponse(saved);
        } catch (RuntimeException exception) {
            log.warn(
                    "Coach-client request operation=terminate actorId={} relationshipId={} result=failed exceptionType={} message={}",
                    userId, relationshipId, exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
    }

    private boolean isRespondent(CoachClientRelationship relationship, Long userId) {
        Long requesterId = relationship.getRequestedBy().getId();
        Long respondentId = requesterId.equals(relationship.getCoach().getId())
                ? relationship.getClient().getId()
                : relationship.getCoach().getId();
        return respondentId.equals(userId);
    }

    private void cancelRelationshipAssignments(Long relationshipId) {
        List<WorkoutPlanAssignment> assignments = assignmentRepository.findByRelationshipIdAndStatusInForUpdate(
                relationshipId, CANCELLABLE_ASSIGNMENT_STATUSES);
        if (assignments.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (WorkoutPlanAssignment assignment : assignments) {
            assignment.setStatus(WorkoutPlanAssignmentStatus.CANCELLED);
            LocalDate endDate = assignment.getStartDate().isAfter(today)
                    ? assignment.getStartDate()
                    : today;
            if (assignment.getEndDate() == null || assignment.getEndDate().isAfter(endDate)) {
                assignment.setEndDate(endDate);
            }
        }
        assignmentRepository.saveAllAndFlush(assignments);

        List<Long> canceledAssignmentIds = assignments.stream()
                .map(WorkoutPlanAssignment::getId)
                .toList();
        List<WorkoutSession> uncompletedSessions = workoutSessionRepository
                .findByWorkoutPlanAssignmentIdInAndStatusIn(canceledAssignmentIds, CANCELLABLE_SESSION_STATUSES);
        if (!uncompletedSessions.isEmpty()) {
            for (WorkoutSession session : uncompletedSessions) {
                session.setStatus(WorkoutSessionStatus.CANCELLED);
            }
            workoutSessionRepository.saveAllAndFlush(uncompletedSessions);
        }
    }

    private CoachClientRelationshipResponse toResponse(CoachClientRelationship relationship) {
        return CoachClientRelationshipResponse.builder()
                .id(relationship.getId())
                .coachId(relationship.getCoach().getId())
                .coachName(relationship.getCoach().getFullName())
                .coachAvatarUrl(relationship.getCoach().getAvatarUrl())
                .coachExperienceLevel(relationship.getCoach().getExperienceLevel())
                .coachGender(relationship.getCoach().getGender())
                .clientId(relationship.getClient().getId())
                .clientName(relationship.getClient().getFullName())
                .clientAvatarUrl(relationship.getClient().getAvatarUrl())
                .clientExperienceLevel(relationship.getClient().getExperienceLevel())
                .clientGender(relationship.getClient().getGender())
                .requestedBy(relationship.getRequestedBy().getId())
                .status(relationship.getStatus())
                .createdAt(relationship.getCreatedAt())
                .updatedAt(relationship.getUpdatedAt())
                .build();
    }
}
