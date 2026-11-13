package com.calisthenics.backend.user;

import com.calisthenics.backend.user.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(10L)
                .email("newathlete@test.com")
                .fullName("New Athlete")
                .role(Role.ROLE_USER)
                .onboardingStatus(OnboardingStatus.NOT_SHOWN)
                .build();
    }

    @Test
    void getUserProfile_returnsCorrectOnboardingStatus() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = userService.getUserProfile(10L);

        assertNotNull(response);
        assertEquals(OnboardingStatus.NOT_SHOWN, response.getOnboardingStatus());
    }

    @Test
    void updateUserOnboardingStatus_updatesAndSaves() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateUserOnboardingStatus(10L, OnboardingStatus.COMPACT_BANNER);

        assertNotNull(response);
        assertEquals(OnboardingStatus.COMPACT_BANNER, response.getOnboardingStatus());
        assertEquals(OnboardingStatus.COMPACT_BANNER, sampleUser.getOnboardingStatus());
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    void updateUserOnboardingStatus_throwsOnNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> userService.updateUserOnboardingStatus(10L, null));
    }

    @Test
    void updateUserOnboardingStatus_throwsOnUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.updateUserOnboardingStatus(99L, OnboardingStatus.DISMISSED));
    }
}
