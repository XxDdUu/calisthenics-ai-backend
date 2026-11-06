package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseMediaServiceTest {

    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private ExerciseMediaItemRepository mediaItemRepository;

    private ExerciseMediaService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseMediaService(exerciseRepository, mediaItemRepository);
    }

    @Test
    void addMedia_PersistsMediaAgainstRequestedExercise() {
        Exercise exercise = Exercise.builder().id(17L).build();
        when(exerciseRepository.findById(17L)).thenReturn(Optional.of(exercise));
        when(mediaItemRepository.saveAndFlush(any(ExerciseMediaItem.class))).thenAnswer(invocation -> {
            ExerciseMediaItem media = invocation.getArgument(0);
            media.setId(42L);
            return media;
        });

        ExerciseMediaResponse response = service.addMedia(
                17L,
                ExerciseMediaType.VIDEO,
                "https://cdn.example.com/exercises/17/video.mp4",
                3
        );

        ArgumentCaptor<ExerciseMediaItem> mediaCaptor = ArgumentCaptor.forClass(ExerciseMediaItem.class);
        verify(mediaItemRepository).saveAndFlush(mediaCaptor.capture());
        ExerciseMediaItem persisted = mediaCaptor.getValue();
        assertEquals(17L, persisted.getExercise().getId());
        assertEquals(ExerciseMediaType.VIDEO, persisted.getType());
        assertEquals(3, persisted.getDisplayOrder());
        assertEquals(42L, response.id());
        assertEquals(persisted.getUrl(), response.url());
    }

    @Test
    void requireExercise_ReturnsNotFoundForInvalidExerciseId() {
        when(exerciseRepository.existsById(999L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requireExercise(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }
}
