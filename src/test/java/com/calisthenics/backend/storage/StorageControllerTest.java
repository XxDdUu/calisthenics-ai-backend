package com.calisthenics.backend.storage;

import com.calisthenics.backend.exercise.ExerciseMediaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class StorageControllerTest {

    private StorageService storageService;
    private MinioExerciseMediaStorageService mediaStorageService;
    private ExerciseMediaService exerciseMediaService;
    private StorageController controller;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        mediaStorageService = mock(MinioExerciseMediaStorageService.class);
        exerciseMediaService = mock(ExerciseMediaService.class);
        controller = new StorageController(
                storageService,
                mock(MinioAvatarStorageService.class),
                mediaStorageService,
                exerciseMediaService
        );
        ReflectionTestUtils.setField(controller, "storageProvider", "minio");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        request.setScheme("http");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void uploadExerciseMedia_CleansUpObjectWhenMetadataPersistenceFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.mp4",
                "video/mp4",
                new byte[]{1, 2, 3}
        );
        org.mockito.Mockito.when(mediaStorageService.store(file)).thenReturn("123e4567-e89b-12d3-a456-426614174000.mp4");
        doNothing().when(exerciseMediaService).requireExercise(7L);
        doThrow(new IllegalStateException("database failure"))
                .when(exerciseMediaService)
                .addMedia(
                        org.mockito.ArgumentMatchers.eq(7L),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(2)
                );

        assertThrows(IllegalStateException.class, () -> controller.uploadExerciseMedia(file, 7L, 2));

        verify(mediaStorageService).delete("123e4567-e89b-12d3-a456-426614174000.mp4");
    }
}
