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

import io.minio.StatObjectResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void getExerciseMedia_ReturnsFullContentWithAcceptRangesWhenNoRangeHeader() throws Exception {
        String objectName = "123e4567-e89b-12d3-a456-426614174000.mp4";
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1000L);
        when(stat.contentType()).thenReturn("video/mp4");
        when(mediaStorageService.stat(objectName)).thenReturn(stat);
        when(mediaStorageService.open(objectName)).thenReturn(new ByteArrayInputStream(new byte[1000]));

        ResponseEntity<StreamingResponseBody> response = controller.getExerciseMedia(objectName, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("video/mp4", response.getHeaders().getContentType().toString());
        assertEquals(1000L, response.getHeaders().getContentLength());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertNotNull(response.getBody());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertEquals(1000, out.size());
    }

    @Test
    void getExerciseMedia_ReturnsPartialContentWhenValidRangeRequested() throws Exception {
        String objectName = "123e4567-e89b-12d3-a456-426614174000.mp4";
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1000L);
        when(stat.contentType()).thenReturn("video/mp4");
        when(mediaStorageService.stat(objectName)).thenReturn(stat);
        when(mediaStorageService.open(objectName, 0L, 500L)).thenReturn(new ByteArrayInputStream(new byte[500]));

        ResponseEntity<StreamingResponseBody> response = controller.getExerciseMedia(objectName, "bytes=0-499");

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals(500L, response.getHeaders().getContentLength());
        assertEquals("bytes 0-499/1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertEquals(500, out.size());
    }

    @Test
    void getExerciseMedia_ReturnsRequestedRangeNotSatisfiableWhenRangeOutOfBound() {
        String objectName = "123e4567-e89b-12d3-a456-426614174000.mp4";
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1000L);
        when(stat.contentType()).thenReturn("video/mp4");
        when(mediaStorageService.stat(objectName)).thenReturn(stat);

        ResponseEntity<StreamingResponseBody> response = controller.getExerciseMedia(objectName, "bytes=2000-3000");

        assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());
        assertEquals("bytes */1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
    }
}
