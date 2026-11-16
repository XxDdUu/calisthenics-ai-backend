package com.calisthenics.backend.user;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.dto.*;
import com.calisthenics.backend.videoanalysis.VideoAnalysisQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private VideoAnalysisQuotaService videoAnalysisQuotaService;

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

    @Test
    void getCurrentUserUsage_returnsUsage_success() {
        UserUsageResponseDto usageDto = UserUsageResponseDto.builder()
                .membership(MembershipUsageDto.builder()
                        .tier("FREE")
                        .status("ACTIVE")
                        .source("Default")
                        .expiresAt(null)
                        .build())
                .videoAnalysis(VideoAnalysisUsageDto.builder()
                        .used(0)
                        .limit(1)
                        .remaining(1)
                        .unlimited(false)
                        .resetAt(ZonedDateTime.parse("2026-11-17T00:00:00+07:00"))
                        .timezone("Asia/Ho_Chi_Minh")
                        .build())
                .liveAnalysis(LiveAnalysisUsageDto.builder()
                        .available(false)
                        .unlimited(false)
                        .build())
                .build();

        when(videoAnalysisQuotaService.getUserUsage(1L)).thenReturn(usageDto);

        ResponseEntity<UserUsageResponseDto> response = userController.getCurrentUserUsage(testUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FREE", response.getBody().getMembership().getTier());
        assertEquals(1, response.getBody().getVideoAnalysis().getLimit());
        assertFalse(response.getBody().getLiveAnalysis().isAvailable());
        verify(videoAnalysisQuotaService, times(1)).getUserUsage(1L);
    }

    @Test
    void getCurrentUserUsage_unauthenticated_throwsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                userController.getCurrentUserUsage(null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(videoAnalysisQuotaService);
    }

    @Test
    void getCurrentUserUsage_proTier_returnsCorrectDto() {
        ZonedDateTime expiry = ZonedDateTime.parse("2026-12-16T12:00:00+07:00");
        UserUsageResponseDto usageDto = UserUsageResponseDto.builder()
                .membership(MembershipUsageDto.builder()
                        .tier("PRO")
                        .status("ACTIVE")
                        .source("Subscription")
                        .expiresAt(expiry)
                        .build())
                .videoAnalysis(VideoAnalysisUsageDto.builder()
                        .used(3)
                        .limit(10)
                        .remaining(7)
                        .unlimited(false)
                        .resetAt(ZonedDateTime.parse("2026-11-17T00:00:00+07:00"))
                        .timezone("Asia/Ho_Chi_Minh")
                        .build())
                .liveAnalysis(LiveAnalysisUsageDto.builder()
                        .available(true)
                        .unlimited(false)
                        .build())
                .build();

        when(videoAnalysisQuotaService.getUserUsage(1L)).thenReturn(usageDto);

        ResponseEntity<UserUsageResponseDto> response = userController.getCurrentUserUsage(testUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PRO", response.getBody().getMembership().getTier());
        assertEquals("Subscription", response.getBody().getMembership().getSource());
        assertEquals(expiry, response.getBody().getMembership().getExpiresAt());
        assertEquals(10, response.getBody().getVideoAnalysis().getLimit());
        assertEquals(7, response.getBody().getVideoAnalysis().getRemaining());
        assertTrue(response.getBody().getLiveAnalysis().isAvailable());
        assertFalse(response.getBody().getLiveAnalysis().isUnlimited());
    }

    @Test
    void getCurrentUserUsage_adminTier_returnsCorrectDtoWithNulls() {
        UserUsageResponseDto usageDto = UserUsageResponseDto.builder()
                .membership(MembershipUsageDto.builder()
                        .tier("ADMIN")
                        .status("ACTIVE")
                        .source("ADMIN Role")
                        .expiresAt(null)
                        .build())
                .videoAnalysis(VideoAnalysisUsageDto.builder()
                        .used(5)
                        .limit(null)
                        .remaining(null)
                        .unlimited(true)
                        .resetAt(null)
                        .timezone("Asia/Ho_Chi_Minh")
                        .build())
                .liveAnalysis(LiveAnalysisUsageDto.builder()
                        .available(true)
                        .unlimited(true)
                        .build())
                .build();

        when(videoAnalysisQuotaService.getUserUsage(1L)).thenReturn(usageDto);

        ResponseEntity<UserUsageResponseDto> response = userController.getCurrentUserUsage(testUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ADMIN", response.getBody().getMembership().getTier());
        assertEquals("ADMIN Role", response.getBody().getMembership().getSource());
        assertNull(response.getBody().getMembership().getExpiresAt());
        assertNull(response.getBody().getVideoAnalysis().getLimit());
        assertNull(response.getBody().getVideoAnalysis().getRemaining());
        assertNull(response.getBody().getVideoAnalysis().getResetAt());
        assertTrue(response.getBody().getVideoAnalysis().isUnlimited());
        assertTrue(response.getBody().getLiveAnalysis().isAvailable());
        assertTrue(response.getBody().getLiveAnalysis().isUnlimited());
    }
}
