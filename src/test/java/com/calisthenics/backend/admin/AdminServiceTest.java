package com.calisthenics.backend.admin;

import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.PersistentWorkoutSessionService;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private WorkoutPlanRepository workoutPlanRepository;
    @Mock
    private WorkoutSessionRepository workoutSessionRepository;
    @Mock
    private WorkoutPlanMapper workoutPlanMapper;
    @Mock
    private PersistentWorkoutSessionService persistentWorkoutSessionService;
    @Mock
    private com.calisthenics.backend.exercise.ExerciseRepository exerciseRepository;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(
                userRepository,
                coachProfileRepository,
                clientProfileRepository,
                workoutPlanRepository,
                workoutSessionRepository,
                workoutPlanMapper,
                persistentWorkoutSessionService,
                exerciseRepository
        );
    }

    @Test
    void dashboardUsesRepositoryCounts() {
        when(userRepository.count()).thenReturn(9L);
        when(clientProfileRepository.count()).thenReturn(5L);
        when(coachProfileRepository.count()).thenReturn(2L);
        when(userRepository.countByRole(Role.ROLE_ADMIN)).thenReturn(2L);
        when(workoutSessionRepository.count()).thenReturn(12L);
        when(workoutPlanRepository.count()).thenReturn(7L);
        when(exerciseRepository.count()).thenReturn(15L);

        var dashboard = service.getDashboard();

        assertEquals(9L, dashboard.totalUsers());
        assertEquals(5L, dashboard.totalClients());
        assertEquals(2L, dashboard.totalCoaches());
        assertEquals(2L, dashboard.totalAdmins());
        assertEquals(12L, dashboard.totalWorkoutSessions());
        assertEquals(7L, dashboard.totalWorkoutPlans());
        assertEquals(15L, dashboard.totalExercises());
    }

    @Test
    void lastAdministratorCannotBeDemoted() {
        User admin = User.builder().id(1L).role(Role.ROLE_ADMIN).build();
        when(userRepository.findByRoleForUpdate(Role.ROLE_ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateUserRole(1L, Role.ROLE_USER, 2L)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(Role.ROLE_ADMIN, admin.getRole());
        verify(userRepository, never()).save(admin);
    }

    @Test
    void administratorCannotDemoteThemself() {
        User admin = User.builder().id(1L).role(Role.ROLE_ADMIN).build();
        when(userRepository.findByRoleForUpdate(Role.ROLE_ADMIN)).thenReturn(List.of(admin));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateUserRole(1L, Role.ROLE_USER, 1L)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(Role.ROLE_ADMIN, admin.getRole());
        verify(userRepository, never()).save(admin);
    }
}
