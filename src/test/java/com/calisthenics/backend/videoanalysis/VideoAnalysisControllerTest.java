package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.videoanalysis.dto.AiVideoKeypointFrameDto;
import com.calisthenics.backend.videoanalysis.dto.AiVideoKeypointItemDto;
import com.calisthenics.backend.videoanalysis.dto.VideoAnalysisKeypointsResponseDto;
import com.calisthenics.backend.videoanalysis.dto.VideoAnalysisResponseDto;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoAnalysisControllerTest {

    @Mock
    private VideoAnalysisService videoAnalysisService;

    @Mock
    private MinioAnalysisMediaStorageService minioStorageService;

    @InjectMocks
    private VideoAnalysisController controller;

    private UserPrincipal testUser;
    private UserPrincipal otherUser;
    private UserPrincipal adminUser;

    @BeforeEach
    void setUp() {
        testUser = new UserPrincipal(
                1L,
                "user@test.com",
                "Test User",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        otherUser = new UserPrincipal(
                2L,
                "other@test.com",
                "Other User",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        adminUser = new UserPrincipal(
                99L,
                "admin@test.com",
                "Admin User",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    void uploadVideo_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "workout.mp4",
                "video/mp4",
                "dummy video data".getBytes()
        );

        VideoAnalysisResponseDto mockResponse = VideoAnalysisResponseDto.builder()
                .id(100L)
                .userId(1L)
                .status("PROCESSING")
                .originalVideoUrl("/api/v1/video-analysis/100/original")
                .build();

        when(videoAnalysisService.initiateVideoAnalysis(eq(1L), eq(5L), any())).thenReturn(mockResponse);

        ResponseEntity<VideoAnalysisResponseDto> response = controller.uploadVideo(testUser, 5L, file, null);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getId());
        assertEquals("PROCESSING", response.getBody().getStatus());
    }

    @Test
    void uploadVideo_EmptyFileThrowsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("video", "", "video/mp4", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.uploadVideo(testUser, null, emptyFile, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAnalysisById_Success() {
        VideoAnalysisResponseDto mockResponse = VideoAnalysisResponseDto.builder()
                .id(100L)
                .userId(1L)
                .status("COMPLETED")
                .overallScore(BigDecimal.valueOf(88.5))
                .build();

        when(videoAnalysisService.getAnalysisById(eq(100L), eq(1L), eq(false))).thenReturn(mockResponse);

        ResponseEntity<VideoAnalysisResponseDto> response = controller.getAnalysisById(testUser, 100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100L, response.getBody().getId());
        assertEquals(BigDecimal.valueOf(88.5), response.getBody().getOverallScore());
    }

    @Test
    void getAnalysisKeypoints_Success() {
        VideoAnalysisKeypointsResponseDto mockKeypoints = VideoAnalysisKeypointsResponseDto.builder()
                .analysisId(100L)
                .format("COCO_17")
                .totalFrames(1)
                .frames(List.of(
                        AiVideoKeypointFrameDto.builder()
                                .frameIndex(0)
                                .timestampMs(0L)
                                .keypoints(List.of(
                                        AiVideoKeypointItemDto.builder()
                                                .name("left_shoulder")
                                                .x(0.45f)
                                                .y(0.35f)
                                                .confidence(0.95f)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(videoAnalysisService.getAnalysisKeypoints(eq(100L), eq(1L), eq(false))).thenReturn(mockKeypoints);

        ResponseEntity<VideoAnalysisKeypointsResponseDto> response = controller.getAnalysisKeypoints(testUser, 100L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalFrames());
        assertEquals("COCO_17", response.getBody().getFormat());
    }

    @Test
    void streamOriginalVideo_FullContent_Success() throws IOException {
        User user = User.builder().id(1L).email("user@test.com").role(Role.ROLE_USER).build();
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(100L)
                .user(user)
                .originalVideoKey("orig_100.mp4")
                .build();

        when(videoAnalysisService.findAndVerifyOwnership(eq(100L), eq(1L), eq(false))).thenReturn(analysis);

        StatObjectResponse statMock = mock(StatObjectResponse.class);
        when(statMock.size()).thenReturn(1000L);
        when(statMock.contentType()).thenReturn("video/mp4");
        when(minioStorageService.stat("orig_100.mp4")).thenReturn(statMock);

        byte[] videoContent = new byte[1000];
        when(minioStorageService.open("orig_100.mp4")).thenReturn(new ByteArrayInputStream(videoContent));

        ResponseEntity<StreamingResponseBody> response = controller.streamOriginalVideo(testUser, 100L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("video/mp4", response.getHeaders().getContentType().toString());
        assertEquals(1000L, response.getHeaders().getContentLength());
        assertEquals("bytes", response.getHeaders().getFirst("Accept-Ranges"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.getBody().writeTo(baos);
        assertEquals(1000, baos.size());
    }

    @Test
    void streamOriginalVideo_RangeRequest_PartialContent() throws IOException {
        User user = User.builder().id(1L).email("user@test.com").role(Role.ROLE_USER).build();
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(100L)
                .user(user)
                .originalVideoKey("orig_100.mp4")
                .build();

        when(videoAnalysisService.findAndVerifyOwnership(eq(100L), eq(1L), eq(false))).thenReturn(analysis);

        StatObjectResponse statMock = mock(StatObjectResponse.class);
        when(statMock.size()).thenReturn(1000L);
        when(statMock.contentType()).thenReturn("video/mp4");
        when(minioStorageService.stat("orig_100.mp4")).thenReturn(statMock);

        byte[] partialContent = new byte[500];
        when(minioStorageService.open("orig_100.mp4", 0L, 500L)).thenReturn(new ByteArrayInputStream(partialContent));

        ResponseEntity<StreamingResponseBody> response = controller.streamOriginalVideo(testUser, 100L, "bytes=0-499");

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals(500L, response.getHeaders().getContentLength());
        assertEquals("bytes 0-499/1000", response.getHeaders().getFirst("Content-Range"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.getBody().writeTo(baos);
        assertEquals(500, baos.size());
    }

    @Test
    void streamAnnotatedVideo_NotReady_ThrowsConflict() {
        User user = User.builder().id(1L).email("user@test.com").role(Role.ROLE_USER).build();
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(100L)
                .user(user)
                .originalVideoKey("orig_100.mp4")
                .annotatedVideoKey(null)
                .status("PROCESSING")
                .build();

        when(videoAnalysisService.findAndVerifyOwnership(eq(100L), eq(1L), eq(false))).thenReturn(analysis);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.streamAnnotatedVideo(testUser, 100L, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }
}
