package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminExerciseDeleteResponse;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.CreateAdminExerciseRequest;
import com.calisthenics.backend.admin.dto.UpdateAdminExerciseRequest;
import com.calisthenics.backend.analysis.AnalysisRepository;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.workout.WorkoutSessionExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminExerciseServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    @Mock
    private WorkoutSessionExerciseRepository workoutSessionExerciseRepository;
    @Mock
    private PersonalRecordRepository personalRecordRepository;
    @Mock
    private AnalysisRepository analysisRepository;

    private AdminExerciseService service;

    @BeforeEach
    void setUp() {
        service = new AdminExerciseService(
                exerciseRepository,
                workoutPlanExerciseRepository,
                workoutSessionExerciseRepository,
                personalRecordRepository,
                analysisRepository
        );
    }

    @Test
    void getExercises_ReturnsPagedExercises() {
        Exercise ex = Exercise.builder()
                .id(1L)
                .name("Push-Up")
                .code("PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .active(true)
                .build();

        when(exerciseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ex)));
        when(workoutPlanExerciseRepository.countByExerciseId(1L)).thenReturn(2L);
        when(workoutSessionExerciseRepository.countByExerciseId(1L)).thenReturn(5L);

        var page = service.getExercises(0, 10, "push", ExerciseCategory.PUSH, DifficultyLevel.BEGINNER, true, "name", "ASC");

        assertEquals(1, page.content().size());
        AdminExerciseResponse item = page.content().get(0);
        assertEquals("Push-Up", item.name());
        assertEquals(2L, item.planReferencesCount());
        assertEquals(5L, item.sessionReferencesCount());
        assertFalse(item.canDelete());
    }

    @Test
    void getExercise_Found_ReturnsExercise() {
        Exercise ex = Exercise.builder()
                .id(2L)
                .name("Pull-Up")
                .code("PULL_UP")
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.BEGINNER)
                .active(true)
                .build();

        when(exerciseRepository.findById(2L)).thenReturn(Optional.of(ex));
        when(workoutPlanExerciseRepository.countByExerciseId(2L)).thenReturn(0L);
        when(workoutSessionExerciseRepository.countByExerciseId(2L)).thenReturn(0L);
        when(personalRecordRepository.countByExerciseId(2L)).thenReturn(0L);
        when(analysisRepository.countByExerciseId(2L)).thenReturn(0L);

        var response = service.getExercise(2L);
        assertEquals("Pull-Up", response.name());
        assertTrue(response.canDelete());
    }

    @Test
    void getExercise_NotFound_ThrowsNotFound() {
        when(exerciseRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getExercise(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void createExercise_Success() {
        CreateAdminExerciseRequest request = new CreateAdminExerciseRequest(
                "Ring Dips",
                null,
                ExerciseCategory.PUSH,
                DifficultyLevel.INTERMEDIATE,
                "Dips performed on gymnastic rings",
                "Chest, Triceps",
                "Lock out at top with rings turned out",
                "https://example.com/ring_dips.jpg",
                "https://example.com/ring_dips.mp4",
                "Gymnastic Rings",
                true
        );

        when(exerciseRepository.existsByNameIgnoreCase("Ring Dips")).thenReturn(false);
        when(exerciseRepository.existsByCode("RING_DIPS")).thenReturn(false);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> {
            Exercise saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        AdminExerciseResponse created = service.createExercise(request);

        assertNotNull(created);
        assertEquals("Ring Dips", created.name());
        assertEquals("RING_DIPS", created.code());
        assertEquals("Gymnastic Rings", created.equipment());
        assertTrue(created.canDelete());
    }

    @Test
    void createExercise_DuplicateName_ThrowsConflict() {
        CreateAdminExerciseRequest request = new CreateAdminExerciseRequest(
                "Handstand",
                "HANDSTAND",
                ExerciseCategory.SKILL,
                DifficultyLevel.INTERMEDIATE,
                null, null, null, null, null, null, true
        );

        when(exerciseRepository.existsByNameIgnoreCase("Handstand")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.createExercise(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already exists"));
    }

    @Test
    void updateExercise_Success() {
        Exercise existing = Exercise.builder()
                .id(5L)
                .name("Old Name")
                .code("OLD_CODE")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .active(true)
                .build();

        when(exerciseRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(exerciseRepository.existsByNameIgnoreCaseAndIdNot("New Name", 5L)).thenReturn(false);
        when(exerciseRepository.existsByCodeAndIdNot("NEW_CODE", 5L)).thenReturn(false);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAdminExerciseRequest request = new UpdateAdminExerciseRequest(
                "New Name",
                "NEW_CODE",
                ExerciseCategory.PUSH,
                DifficultyLevel.INTERMEDIATE,
                "Updated desc",
                "Chest",
                "Instructions",
                null, null, "Parallettes", false
        );

        AdminExerciseResponse updated = service.updateExercise(5L, request);

        assertEquals("New Name", updated.name());
        assertEquals("NEW_CODE", updated.code());
        assertFalse(updated.active());
    }

    @Test
    void updateStatus_Success() {
        Exercise existing = Exercise.builder()
                .id(8L)
                .name("Muscle-Up")
                .code("MUSCLE_UP")
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.ADVANCED)
                .active(true)
                .build();

        when(exerciseRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminExerciseResponse response = service.updateStatus(8L, false);

        assertFalse(response.active());
    }

    @Test
    void deleteExercise_SafeNoReferences_PermanentlyDeletes() {
        Exercise existing = Exercise.builder()
                .id(12L)
                .name("Obsolete Exercise")
                .code("OBSOLETE")
                .build();

        when(exerciseRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(workoutPlanExerciseRepository.countByExerciseId(12L)).thenReturn(0L);
        when(workoutSessionExerciseRepository.countByExerciseId(12L)).thenReturn(0L);
        when(personalRecordRepository.countByExerciseId(12L)).thenReturn(0L);
        when(analysisRepository.countByExerciseId(12L)).thenReturn(0L);

        AdminExerciseDeleteResponse result = service.deleteExercise(12L, false);

        assertTrue(result.success());
        assertFalse(result.softDeleted());
        verify(exerciseRepository).delete(eq(existing));
    }

    @Test
    void deleteExercise_HasReferences_WithoutSoftDelete_ThrowsConflict() {
        Exercise existing = Exercise.builder()
                .id(14L)
                .name("Front Lever")
                .code("FRONT_LEVER")
                .active(true)
                .build();

        when(exerciseRepository.findById(14L)).thenReturn(Optional.of(existing));
        when(workoutPlanExerciseRepository.countByExerciseId(14L)).thenReturn(3L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.deleteExercise(14L, false));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("referenced in"));
        verify(exerciseRepository, never()).delete(any(Exercise.class));
    }

    @Test
    void deleteExercise_HasReferences_WithSoftDelete_Deactivates() {
        Exercise existing = Exercise.builder()
                .id(14L)
                .name("Front Lever")
                .code("FRONT_LEVER")
                .active(true)
                .build();

        when(exerciseRepository.findById(14L)).thenReturn(Optional.of(existing));
        when(workoutPlanExerciseRepository.countByExerciseId(14L)).thenReturn(3L);
        when(workoutSessionExerciseRepository.countByExerciseId(14L)).thenReturn(2L);
        when(exerciseRepository.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminExerciseDeleteResponse result = service.deleteExercise(14L, true);

        assertTrue(result.success());
        assertTrue(result.softDeleted());
        assertFalse(existing.isActive());
        verify(exerciseRepository, never()).delete(any(Exercise.class));
        verify(exerciseRepository).save(existing);
    }
}
