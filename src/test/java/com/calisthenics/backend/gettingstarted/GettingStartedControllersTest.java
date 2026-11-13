package com.calisthenics.backend.gettingstarted;

import com.calisthenics.backend.gettingstarted.controller.AdminGettingStartedController;
import com.calisthenics.backend.gettingstarted.controller.GettingStartedController;
import com.calisthenics.backend.gettingstarted.dto.*;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.service.GettingStartedService;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GettingStartedControllersTest {

    @Mock
    private GettingStartedService service;

    @InjectMocks
    private AdminGettingStartedController adminController;

    @InjectMocks
    private GettingStartedController athleteController;

    private UserPrincipal adminPrincipal;
    private UserPrincipal athletePrincipal;

    @BeforeEach
    void setUp() {
        User admin = User.builder().id(1L).email("admin@test.com").role(Role.ROLE_ADMIN).build();
        User athlete = User.builder().id(2L).email("athlete@test.com").role(Role.ROLE_USER).build();
        adminPrincipal = UserPrincipal.create(admin);
        athletePrincipal = UserPrincipal.create(athlete);
    }

    @Test
    @DisplayName("Admin create program saves target audience")
    void admin_createProgram_withAudience() {
        AdminGettingStartedProgramRequest request = AdminGettingStartedProgramRequest.builder()
                .title("Program")
                .targetAudience(TargetAudience.LAST_14_DAYS)
                .days(List.of())
                .build();
        GettingStartedProgramResponse programResponse = GettingStartedProgramResponse.builder()
                .id(10L)
                .title("Program")
                .targetAudience(TargetAudience.LAST_14_DAYS)
                .build();
        when(service.createOrUpdateProgram(eq(1L), any())).thenReturn(programResponse);

        ResponseEntity<GettingStartedProgramResponse> response = adminController.createOrUpdateProgram(adminPrincipal, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTargetAudience()).isEqualTo(TargetAudience.LAST_14_DAYS);
    }

    @Test
    @DisplayName("Athlete update dismissal status succeeds")
    void athlete_updateDismissalStatus_success() {
        UpdateDismissalStatusRequest request = UpdateDismissalStatusRequest.builder()
                .status(OnboardingStatus.COMPACT_BANNER)
                .build();
        GettingStartedProgramResponse programResponse = GettingStartedProgramResponse.builder()
                .id(10L)
                .userDismissalState(OnboardingStatus.COMPACT_BANNER)
                .build();
        when(service.updateDismissalStatus(2L, OnboardingStatus.COMPACT_BANNER)).thenReturn(programResponse);

        ResponseEntity<GettingStartedProgramResponse> response = athleteController.updateDismissalStatus(athletePrincipal, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUserDismissalState()).isEqualTo(OnboardingStatus.COMPACT_BANNER);
    }
}
