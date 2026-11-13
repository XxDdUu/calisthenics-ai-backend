package com.calisthenics.backend.user;

import com.calisthenics.backend.user.dto.CoachProfileResponse;
import com.calisthenics.backend.user.dto.UpdateProfileRequest;
import com.calisthenics.backend.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return mapToProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getExperienceLevel() != null) {
            user.setExperienceLevel(request.getExperienceLevel());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }

        User updated = userRepository.save(user);
        return mapToProfileResponse(updated);
    }

    @Transactional
    public UserProfileResponse updateUserGender(Long userId, Gender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("Gender must be MALE or FEMALE");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setGender(gender);
        User updated = userRepository.save(user);
        return mapToProfileResponse(updated);
    }

    @Transactional
    public UserProfileResponse updateUserOnboardingStatus(Long userId, OnboardingStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Onboarding status must not be null");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setOnboardingStatus(status);
        User updated = userRepository.save(user);
        return mapToProfileResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToProfileResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CoachProfileResponse> getCoachProfiles(Long currentUserId) {
        return userRepository.findByRoleAndIdNotOrderByFullNameAsc(Role.ROLE_USER, currentUserId)
                .stream()
                .map(this::mapToCoachProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public CoachProfileResponse getCoachProfile(Long userId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getRole() == Role.ROLE_USER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return mapToCoachProfile(user);
    }

    private CoachProfileResponse mapToCoachProfile(User user) {
        return CoachProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .experienceLevel(user.getExperienceLevel())
                .gender(user.getGender())
                .build();
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .experienceLevel(user.getExperienceLevel())
                .gender(user.getGender())
                .onboardingStatus(user.getOnboardingStatus() != null ? user.getOnboardingStatus() : OnboardingStatus.NOT_SHOWN)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
