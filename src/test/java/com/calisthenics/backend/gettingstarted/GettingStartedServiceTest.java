package com.calisthenics.backend.gettingstarted;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.dto.*;
import com.calisthenics.backend.gettingstarted.entity.*;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.repository.*;
import com.calisthenics.backend.gettingstarted.service.impl.GettingStartedServiceImpl;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GettingStartedServiceTest {

    @Mock
    private GettingStartedProgramRepository programRepository;

    @Mock
    private GettingStartedEnrollmentRepository enrollmentRepository;

    @Mock
    private GettingStartedDayCompletionRepository dayCompletionRepository;

    @Mock
    private GettingStartedProgramUserStateRepository programUserStateRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GettingStartedServiceImpl service;

    private User admin;
    private User newAthlete;
    private User oldAthlete;
    private Exercise pushupExercise;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(1L)
                .email("admin@calisthenics.ai")
                .fullName("Admin User")
                .role(Role.ROLE_ADMIN)
                .createdAt(ZonedDateTime.now().minusDays(5))
                .build();

        newAthlete = User.builder()
                .id(2L)
                .email("new.athlete@calisthenics.ai")
                .fullName("New Athlete")
                .role(Role.ROLE_USER)
                .createdAt(ZonedDateTime.now().minusDays(3))
                .build();

        oldAthlete = User.builder()
                .id(3L)
                .email("old.athlete@calisthenics.ai")
                .fullName("Old Athlete")
                .role(Role.ROLE_USER)
                .createdAt(ZonedDateTime.now().minusDays(60))
                .build();

        pushupExercise = Exercise.builder()
                .id(10L)
                .name("Push-ups")
                .build();
    }

    private AdminGettingStartedProgramRequest buildValidProgramRequest(TargetAudience audience) {
        List<AdminGettingStartedDayRequest> days = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            days.add(AdminGettingStartedDayRequest.builder()
                    .dayNumber(i)
                    .title("Day " + i)
                    .description("Description " + i)
                    .exercises(List.of(
                            AdminGettingStartedExerciseRequest.builder()
                                    .exerciseId(10L)
                                    .exerciseName("Push-ups")
                                    .sets(3)
                                    .reps(10)
                                    .restSeconds(60)
                                    .instructions("Keep core tight")
                                    .displayOrder(0)
                                    .build()
                    ))
                    .build());
        }

        return AdminGettingStartedProgramRequest.builder()
                .title("Getting Started – 3 Days")
                .description("Beginner calisthenics routine")
                .targetAudience(audience)
                .days(days)
                .build();
    }

    @Test
    @DisplayName("Create program creates new 3-day program with specified audience")
    void createProgram_createsNew() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(programRepository.findLatest()).thenReturn(Optional.empty());
        when(exerciseRepository.findById(10L)).thenReturn(Optional.of(pushupExercise));
        when(programRepository.save(any(GettingStartedProgram.class))).thenAnswer(invocation -> {
            GettingStartedProgram p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        GettingStartedProgramResponse response = service.createOrUpdateProgram(1L, buildValidProgramRequest(TargetAudience.LAST_30_DAYS));

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Getting Started – 3 Days");
        assertThat(response.getTargetAudience()).isEqualTo(TargetAudience.LAST_30_DAYS);
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.isPublished()).isFalse();
        assertThat(response.getDays()).hasSize(3);
    }

    @Test
    @DisplayName("Audience ALL_ATHLETES makes both old and new accounts eligible")
    void audience_allAthletes_bothEligible() {
        GettingStartedProgram program = GettingStartedProgram.builder()
                .id(100L)
                .published(true)
                .targetAudience(TargetAudience.ALL_ATHLETES)
                .build();

        when(programRepository.findFirstPublished()).thenReturn(Optional.of(program));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newAthlete));
        when(userRepository.findById(3L)).thenReturn(Optional.of(oldAthlete));

        GettingStartedProgramResponse newResp = service.getPublishedProgram(2L);
        GettingStartedProgramResponse oldResp = service.getPublishedProgram(3L);

        assertThat(newResp.isEligible()).isTrue();
        assertThat(oldResp.isEligible()).isTrue();
    }

    @Test
    @DisplayName("Audience LAST_30_DAYS includes account within 30 days and excludes older accounts")
    void audience_last30Days_filtering() {
        GettingStartedProgram program = GettingStartedProgram.builder()
                .id(100L)
                .published(true)
                .targetAudience(TargetAudience.LAST_30_DAYS)
                .build();

        when(programRepository.findFirstPublished()).thenReturn(Optional.of(program));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newAthlete)); // 3 days old
        when(userRepository.findById(3L)).thenReturn(Optional.of(oldAthlete)); // 60 days old

        GettingStartedProgramResponse newResp = service.getPublishedProgram(2L);
        GettingStartedProgramResponse oldResp = service.getPublishedProgram(3L);

        assertThat(newResp.isEligible()).isTrue();
        assertThat(oldResp.isEligible()).isFalse();
    }

    @Test
    @DisplayName("Audience LAST_14_DAYS excludes accounts older than 14 days")
    void audience_last14Days_filtering() {
        User athlete20Days = User.builder()
                .id(4L)
                .role(Role.ROLE_USER)
                .createdAt(ZonedDateTime.now().minusDays(20))
                .build();

        GettingStartedProgram program = GettingStartedProgram.builder()
                .id(100L)
                .published(true)
                .targetAudience(TargetAudience.LAST_14_DAYS)
                .build();

        when(programRepository.findFirstPublished()).thenReturn(Optional.of(program));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newAthlete)); // 3 days old
        when(userRepository.findById(4L)).thenReturn(Optional.of(athlete20Days)); // 20 days old

        GettingStartedProgramResponse newResp = service.getPublishedProgram(2L);
        GettingStartedProgramResponse olderResp = service.getPublishedProgram(4L);

        assertThat(newResp.isEligible()).isTrue();
        assertThat(olderResp.isEligible()).isFalse();
    }

    @Test
    @DisplayName("Admin accounts are never eligible for athlete onboarding")
    void audience_adminNeverEligible() {
        GettingStartedProgram program = GettingStartedProgram.builder()
                .id(100L)
                .published(true)
                .targetAudience(TargetAudience.ALL_ATHLETES)
                .build();

        when(programRepository.findFirstPublished()).thenReturn(Optional.of(program));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        GettingStartedProgramResponse response = service.getPublishedProgram(1L);

        assertThat(response.isEligible()).isFalse();
    }

    @Test
    @DisplayName("updateDismissalStatus persists state per user and program version")
    void updateDismissalStatus_persistsPerUserAndProgram() {
        GettingStartedProgram program = GettingStartedProgram.builder()
                .id(100L)
                .version(2)
                .published(true)
                .targetAudience(TargetAudience.ALL_ATHLETES)
                .build();

        when(programRepository.findFirstPublished()).thenReturn(Optional.of(program));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newAthlete));
        when(programUserStateRepository.findByUserIdAndProgramIdAndProgramVersion(2L, 100L, 2))
                .thenReturn(Optional.empty());

        GettingStartedProgramResponse response = service.updateDismissalStatus(2L, OnboardingStatus.COMPACT_BANNER);

        assertThat(response.getUserDismissalState()).isEqualTo(OnboardingStatus.COMPACT_BANNER);
        verify(programUserStateRepository).save(any(GettingStartedProgramUserState.class));
    }

    @Test
    @DisplayName("Changing target audience updates eligibility without resetting dismissal states")
    void audienceChange_preservesDismissalState() {
        GettingStartedProgram program = GettingStartedProgram.builder()
                .id(100L)
                .version(1)
                .published(true)
                .targetAudience(TargetAudience.LAST_30_DAYS)
                .build();

        GettingStartedProgramUserState existingDismissal = GettingStartedProgramUserState.builder()
                .user(newAthlete)
                .program(program)
                .programVersion(1)
                .state(OnboardingStatus.COMPACT_BANNER)
                .build();

        when(programRepository.findFirstPublished()).thenReturn(Optional.of(program));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newAthlete));
        when(programUserStateRepository.findByUserIdAndProgramIdAndProgramVersion(2L, 100L, 1))
                .thenReturn(Optional.of(existingDismissal));

        GettingStartedProgramResponse response = service.getPublishedProgram(2L);

        assertThat(response.isEligible()).isTrue();
        assertThat(response.getUserDismissalState()).isEqualTo(OnboardingStatus.COMPACT_BANNER);
    }
}
