package com.calisthenics.backend.profile.service.impl;

import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.profile.dto.request.ClientProfileRequest;
import com.calisthenics.backend.profile.dto.response.ClientProfileResponse;
import com.calisthenics.backend.profile.dto.response.ClientTrainingOverviewResponse;
import com.calisthenics.backend.profile.dto.response.ConnectedCoachSummary;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.service.ClientProfileService;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientProfileServiceImpl implements ClientProfileService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final ChatConversationRepository chatConversationRepository;

    @Override
    @Transactional(readOnly = true)
    public ClientProfileResponse getMyClientProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Optional<ClientProfile> profileOpt = clientProfileRepository.findByUserId(userId);
        return buildClientResponse(user, profileOpt.orElse(null));
    }

    @Override
    @Transactional
    public ClientProfileResponse updateMyClientProfile(Long userId, ClientProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        ClientProfile profile = clientProfileRepository.findByUserId(userId)
                .orElseGet(() -> ClientProfile.builder()
                        .user(user)
                        .experienceLevel(user.getExperienceLevel())
                        .build());

        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl().trim());
            user.setAvatarUrl(request.getAvatarUrl().trim());
            userRepository.save(user);
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio().trim());
        }
        if (request.getExperienceLevel() != null) {
            profile.setExperienceLevel(request.getExperienceLevel().trim());
            user.setExperienceLevel(request.getExperienceLevel().trim());
            userRepository.save(user);
        }
        if (request.getTrainingExperience() != null) {
            profile.setTrainingExperience(request.getTrainingExperience().trim());
        }
        if (request.getPrimaryGoals() != null) {
            profile.setPrimaryGoals(request.getPrimaryGoals().trim());
        }
        if (request.getTrainingInterests() != null) {
            profile.setTrainingInterests(request.getTrainingInterests().trim());
        }
        if (request.getPreferredFrequency() != null) {
            profile.setPreferredFrequency(request.getPreferredFrequency().trim());
        }
        if (request.getCoachingLanguage() != null) {
            profile.setCoachingLanguage(request.getCoachingLanguage().trim());
        }
        if (request.getHeightCm() != null) {
            profile.setHeightCm(BigDecimal.valueOf(request.getHeightCm()));
        }

        ClientProfile saved = clientProfileRepository.save(profile);
        return buildClientResponse(user, saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientProfileResponse getClientProfile(Long currentUserId, Long clientUserId) {
        authorizeAccessToClient(currentUserId, clientUserId);

        User user = userRepository.findById(clientUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

        Optional<ClientProfile> profileOpt = clientProfileRepository.findByUserId(clientUserId);
        return buildClientResponse(user, profileOpt.orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public ClientTrainingOverviewResponse getClientOverview(Long currentUserId, Long clientUserId) {
        authorizeAccessToClient(currentUserId, clientUserId);

        long activePlans = workoutPlanRepository.countByUserId(clientUserId);
        long completedSessions = workoutSessionRepository.countByUserIdAndCompletedAtIsNotNull(clientUserId);
        if (completedSessions == 0) {
            completedSessions = workoutSessionRepository.countByUserId(clientUserId);
        }

        Optional<WorkoutPlan> currentPlanOpt = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(clientUserId)
                .or(() -> workoutPlanRepository.findFirstByUserIdOrderByUpdatedAtDesc(clientUserId));
        ConnectedCoachSummary connectedCoach = getConnectedCoachSummary(clientUserId);

        List<WorkoutSession> sessions = workoutSessionRepository.findTop5ByUserIdOrderByCreatedAtDesc(clientUserId);
        List<ClientTrainingOverviewResponse.RecentWorkoutSummary> recent = sessions.stream()
                .map(s -> ClientTrainingOverviewResponse.RecentWorkoutSummary.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .startedAt(s.getStartedAt())
                        .completedAt(s.getCompletedAt())
                        .build())
                .toList();

        return ClientTrainingOverviewResponse.builder()
                .userId(clientUserId)
                .activePlansCount(activePlans)
                .completedSessionsCount(completedSessions)
                .currentPlanId(currentPlanOpt.map(WorkoutPlan::getId).orElse(null))
                .currentPlanName(currentPlanOpt.map(WorkoutPlan::getName).orElse(null))
                .connectedCoach(connectedCoach)
                .recentWorkouts(recent)
                .build();
    }

    private void authorizeAccessToClient(Long currentUserId, Long clientUserId) {
        if (currentUserId == null || clientUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (currentUserId.equals(clientUserId)) {
            return;
        }

        // Must be an active coach of this client
        boolean isActiveCoach = relationshipRepository.findRelationshipForUsers(
                currentUserId, clientUserId, CoachClientRelationshipStatus.ACTIVE
        ).isPresent();

        if (!isActiveCoach) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to client training information");
        }
    }

    private ConnectedCoachSummary getConnectedCoachSummary(Long clientUserId) {
        return relationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                clientUserId, CoachClientRelationshipStatus.ACTIVE
        ).map(rel -> {
            User coach = rel.getCoach();
            Long conversationId = chatConversationRepository.findByCoachIdAndClientId(coach.getId(), clientUserId)
                    .map(c -> c.getId())
                    .orElse(null);

            return ConnectedCoachSummary.builder()
                    .coachId(coach.getId())
                    .coachName(coach.getFullName())
                    .coachAvatarUrl(coach.getAvatarUrl())
                    .coachExperienceLevel(coach.getExperienceLevel())
                    .connectionStatus("ACTIVE")
                    .relationshipId(rel.getId())
                    .conversationId(conversationId)
                    .build();
        }).orElse(null);
    }

    private ClientProfileResponse buildClientResponse(User user, ClientProfile profile) {
        Long clientUserId = user.getId();

        long activePlans = workoutPlanRepository.countByUserId(clientUserId);
        long completedSessions = workoutSessionRepository.countByUserIdAndCompletedAtIsNotNull(clientUserId);
        if (completedSessions == 0) {
            completedSessions = workoutSessionRepository.countByUserId(clientUserId);
        }

        Optional<WorkoutPlan> currentPlanOpt = workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(clientUserId)
                .or(() -> workoutPlanRepository.findFirstByUserIdOrderByUpdatedAtDesc(clientUserId));
        ConnectedCoachSummary connectedCoach = getConnectedCoachSummary(clientUserId);

        String displayName = (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank())
                ? profile.getDisplayName()
                : user.getFullName();

        String avatarUrl = (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank())
                ? profile.getAvatarUrl()
                : user.getAvatarUrl();

        String experienceLevel = (profile != null && profile.getExperienceLevel() != null && !profile.getExperienceLevel().isBlank())
                ? profile.getExperienceLevel()
                : user.getExperienceLevel();

        return ClientProfileResponse.builder()
                .id(user.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .bio(profile != null ? profile.getBio() : null)
                .experienceLevel(experienceLevel)
                .trainingExperience(profile != null ? profile.getTrainingExperience() : null)
                .primaryGoals(profile != null ? profile.getPrimaryGoals() : null)
                .trainingInterests(profile != null ? profile.getTrainingInterests() : null)
                .preferredFrequency(profile != null ? profile.getPreferredFrequency() : null)
                .coachingLanguage(profile != null ? profile.getCoachingLanguage() : null)
                .heightCm(profile != null && profile.getHeightCm() != null ? profile.getHeightCm().doubleValue() : null)
                .activePlansCount(activePlans)
                .completedSessionsCount(completedSessions)
                .currentPlanId(currentPlanOpt.map(WorkoutPlan::getId).orElse(null))
                .currentPlanName(currentPlanOpt.map(WorkoutPlan::getName).orElse(null))
                .connectedCoach(connectedCoach)
                .createdAt(profile != null ? profile.getCreatedAt() : user.getCreatedAt())
                .updatedAt(profile != null ? profile.getUpdatedAt() : user.getUpdatedAt())
                .build();
    }
}
