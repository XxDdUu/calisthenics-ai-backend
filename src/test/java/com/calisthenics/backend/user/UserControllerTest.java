package com.calisthenics.backend.user;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.dto.UpdateGenderRequest;
import com.calisthenics.backend.user.dto.UpdateOnboardingStatusRequest;
import com.calisthenics.backend.user.dto.UpdateProfileRequest;
import com.calisthenics.backend.user.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserPrincipal(1L, "athlete@test.com", "Athlete One", "password", Collections.emptyList());
    }

    @Test
    void getCurrentUser_returnsProfileWithOnboardingStatus() {
        UserProfileResponse profileResponse = UserProfileResponse.builder()
                .id(1L)
                .email("athlete@test.com")
                .fullName("Athlete One")
                .role("ROLE_USER")
                .onboardingStatus(OnboardingStatus.NOT_SHOWN)
                .build();

        when(userService.getUserProfile(1L)).thenReturn(profileResponse);

        ResponseEntity<UserProfileResponse> response = userController.getCurrentUser(testUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(OnboardingStatus.NOT_SHOWN, response.getBody().getOnboardingStatus());
        verify(userService, times(1)).getUserProfile(1L);
    }

    @Test
    void updateCurrentUserOnboardingStatus_toCompactBanner() {
        UpdateOnboardingStatusRequest request = UpdateOnboardingStatusRequest.builder()
                .status(OnboardingStatus.COMPACT_BANNER)
                .build();

        UserProfileResponse updatedProfile = UserProfileResponse.builder()
                .id(1L)
                .email("athlete@test.com")
                .fullName("Athlete One")
                .role("ROLE_USER")
                .onboardingStatus(OnboardingStatus.COMPACT_BANNER)
                .build();

        when(userService.updateUserOnboardingStatus(1L, OnboardingStatus.COMPACT_BANNER)).thenReturn(updatedProfile);

        ResponseEntity<UserProfileResponse> response = userController.updateCurrentUserOnboardingStatus(testUser, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(OnboardingStatus.COMPACT_BANNER, response.getBody().getOnboardingStatus());
        verify(userService, times(1)).updateUserOnboardingStatus(1L, OnboardingStatus.COMPACT_BANNER);
    }

    @Test
    void updateCurrentUserOnboardingStatus_toDismissed() {
        UpdateOnboardingStatusRequest request = UpdateOnboardingStatusRequest.builder()
                .status(OnboardingStatus.DISMISSED)
                .build();

        UserProfileResponse updatedProfile = UserProfileResponse.builder()
                .id(1L)
                .email("athlete@test.com")
                .fullName("Athlete One")
                .role("ROLE_USER")
                .onboardingStatus(OnboardingStatus.DISMISSED)
                .build();

        when(userService.updateUserOnboardingStatus(1L, OnboardingStatus.DISMISSED)).thenReturn(updatedProfile);

        ResponseEntity<UserProfileResponse> response = userController.updateCurrentUserOnboardingStatus(testUser, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(OnboardingStatus.DISMISSED, response.getBody().getOnboardingStatus());
        verify(userService, times(1)).updateUserOnboardingStatus(1L, OnboardingStatus.DISMISSED);
    }
}
