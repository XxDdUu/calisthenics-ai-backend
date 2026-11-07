package com.calisthenics.backend.profile.service.impl;

import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.profile.dto.request.CoachProfileRequest;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.profile.service.CoachProfileService;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.user.dto.CoachProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CoachProfileServiceImpl implements CoachProfileService {

    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final WorkoutPlanAssignmentRepository assignmentRepository;
    private final ChatConversationRepository chatConversationRepository;

    @Override
    @Transactional(readOnly = true)
    public CoachProfileResponse getMyCoachProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Optional<CoachProfile> profileOpt = coachProfileRepository.findByUserId(userId);
        return buildCoachResponse(user, profileOpt.orElse(null), userId);
    }

    @Override
    @Transactional
    public CoachProfileResponse updateMyCoachProfile(Long userId, CoachProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        CoachProfile profile = coachProfileRepository.findByUserId(userId)
                .orElseGet(() -> CoachProfile.builder()
                        .user(user)
                        .isPublic(true)
                        .isAcceptingClients(true)
                        .isVerified(false)
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
        if (request.getSpecialties() != null) {
            profile.setSpecialties(request.getSpecialties().trim());
        }
        if (request.getYearsExperience() != null) {
            profile.setYearsExperience(request.getYearsExperience());
        }
        if (request.getTrainingPhilosophy() != null) {
            profile.setTrainingPhilosophy(request.getTrainingPhilosophy().trim());
        }
        if (request.getCoachingDescription() != null) {
            profile.setCoachingDescription(request.getCoachingDescription().trim());
        }
        if (request.getLanguages() != null) {
            profile.setLanguages(request.getLanguages().trim());
        }
        if (request.getCertifications() != null) {
            profile.setCertifications(request.getCertifications().trim());
        }
        if (request.getSocialLinks() != null) {
            profile.setSocialLinks(request.getSocialLinks().trim());
        }
        if (request.getIsPublic() != null) {
            profile.setIsPublic(request.getIsPublic());
        }
        if (request.getIsAcceptingClients() != null) {
            profile.setIsAcceptingClients(request.getIsAcceptingClients());
        }

        CoachProfile saved = coachProfileRepository.save(profile);
        return buildCoachResponse(user, saved, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CoachProfileResponse getPublicCoachProfile(Long currentUserIdOrNull, Long coachUserId) {
        User user = userRepository.findById(coachUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found"));

        Optional<CoachProfile> profileOpt = coachProfileRepository.findByUserId(coachUserId);
        if (profileOpt.isPresent() && Boolean.FALSE.equals(profileOpt.get().getIsPublic())) {
            boolean isOwner = currentUserIdOrNull != null && currentUserIdOrNull.equals(coachUserId);
            boolean isClient = currentUserIdOrNull != null &&
                    relationshipRepository.findActiveRelationshipForUsers(coachUserId, currentUserIdOrNull).isPresent();
            if (!isOwner && !isClient) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coach profile is private");
            }
        }

        return buildCoachResponse(user, profileOpt.orElse(null), currentUserIdOrNull);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoachProfileResponse> listPublicCoaches(Long currentUserId) {
        List<User> coachUsers = userRepository.findByRoleAndIdNotOrderByFullNameAsc(Role.ROLE_USER, currentUserId);

        return coachUsers.stream()
                .map(u -> {
                    Optional<CoachProfile> p = coachProfileRepository.findByUserId(u.getId());
                    if (p.isPresent() && Boolean.FALSE.equals(p.get().getIsPublic())) {
                        return null;
                    }
                    return buildCoachResponse(u, p.orElse(null), currentUserId);
                })
                .filter(res -> res != null)
                .toList();
    }

    private CoachProfileResponse buildCoachResponse(User user, CoachProfile profile, Long viewerUserId) {
        Long coachId = user.getId();

        long activeClients = relationshipRepository.countByCoachIdAndStatus(coachId, CoachClientRelationshipStatus.ACTIVE);
        long completedClients = relationshipRepository.countByCoachIdAndStatusIn(
                coachId, List.of(
                        CoachClientRelationshipStatus.CANCELLED,
                        CoachClientRelationshipStatus.REJECTED,
                        CoachClientRelationshipStatus.TERMINATED));
        long assignedPlans = assignmentRepository.countByCoachIdAndStatus(coachId, WorkoutPlanAssignmentStatus.ACTIVE);

        String connectionStatus = null;
        Long relationshipId = null;
        Long conversationId = null;

        if (viewerUserId != null && !viewerUserId.equals(coachId)) {
            Optional<CoachClientRelationship> relOpt = relationshipRepository.findRelationshipForUsers(
                    coachId, viewerUserId, CoachClientRelationshipStatus.ACTIVE);
            if (relOpt.isEmpty()) {
                relOpt = relationshipRepository.findRelationshipForUsers(
                        coachId, viewerUserId, CoachClientRelationshipStatus.PENDING);
            }
            if (relOpt.isEmpty()) {
                relOpt = relationshipRepository.findRelationshipForUsers(
                        coachId, viewerUserId, CoachClientRelationshipStatus.REJECTED);
            }
            if (relOpt.isEmpty()) {
                relOpt = relationshipRepository.findRelationshipForUsers(
                        coachId, viewerUserId, CoachClientRelationshipStatus.CANCELLED);
            }
            if (relOpt.isEmpty()) {
                relOpt = relationshipRepository.findRelationshipForUsers(
                        coachId, viewerUserId, CoachClientRelationshipStatus.TERMINATED);
            }

            if (relOpt.isPresent()) {
                CoachClientRelationship rel = relOpt.get();
                connectionStatus = rel.getStatus().name();
                relationshipId = rel.getId();
            }

            conversationId = chatConversationRepository.findByCoachIdAndClientId(coachId, viewerUserId)
                    .or(() -> chatConversationRepository.findByCoachIdAndClientId(viewerUserId, coachId))
                    .map(c -> c.getId())
                    .orElse(null);
        }

        String displayName = (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank())
                ? profile.getDisplayName()
                : user.getFullName();

        String avatarUrl = (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank())
                ? profile.getAvatarUrl()
                : user.getAvatarUrl();

        return CoachProfileResponse.builder()
                .id(user.getId())
                .userId(user.getId())
                .fullName(user.getFullName())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .experienceLevel(user.getExperienceLevel())
                .bio(profile != null ? profile.getBio() : null)
                .specialties(profile != null ? profile.getSpecialties() : null)
                .yearsExperience(profile != null ? profile.getYearsExperience() : null)
                .trainingPhilosophy(profile != null ? profile.getTrainingPhilosophy() : null)
                .coachingDescription(profile != null ? profile.getCoachingDescription() : null)
                .languages(profile != null ? profile.getLanguages() : null)
                .certifications(profile != null ? profile.getCertifications() : null)
                .socialLinks(profile != null ? profile.getSocialLinks() : null)
                .isPublic(profile != null ? profile.getIsPublic() : true)
                .isAcceptingClients(profile != null ? profile.getIsAcceptingClients() : true)
                .isVerified(profile != null ? profile.getIsVerified() : false)
                .activeClientsCount(activeClients)
                .completedClientsCount(completedClients)
                .assignedPlansCount(assignedPlans)
                .connectionStatus(connectionStatus)
                .relationshipId(relationshipId)
                .conversationId(conversationId)
                .createdAt(profile != null ? profile.getCreatedAt() : user.getCreatedAt())
                .updatedAt(profile != null ? profile.getUpdatedAt() : user.getUpdatedAt())
                .build();
    }
}
