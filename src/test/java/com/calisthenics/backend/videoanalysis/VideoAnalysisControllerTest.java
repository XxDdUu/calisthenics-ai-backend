package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.config.GlobalExceptionHandler;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.videoanalysis.dto.AiVideoKeypointFrameDto;
import com.calisthenics.backend.videoanalysis.dto.AiVideoKeypointItemDto;
import com.calisthenics.backend.videoanalysis.dto.QuotaEntitlementResponseDto;
import com.calisthenics.backend.videoanalysis.dto.VideoAnalysisKeypointsResponseDto;
import com.calisthenics.backend.videoanalysis.dto.VideoAnalysisResponseDto;
import com.calisthenics.backend.videoanalysis.exception.ProMembershipRequiredException;
import com.calisthenics.backend.videoanalysis.exception.QuotaExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VideoAnalysisControllerTest {

    @Mock
    private VideoAnalysisService videoAnalysisService;

    @Mock
    private MinioAnalysisMediaStorageService minioStorageService;

    @Mock
    private VideoAnalysisQuotaService videoAnalysisQuotaService;

    @InjectMocks
    private VideoAnalysisController controller;

    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserPrincipal(
                1L,
                "user@test.com",
                "Test User",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void getQuota_Success() {
        QuotaEntitlementResponseDto quota = QuotaEntitlementResponseDto.builder()
                .userId(1L)
                .membershipTier(MembershipTier.FREE)
                .dailyLimit(1)
                .usedToday(0)
                .remainingToday(1)
                .liveAnalysisUnlocked(false)
                .timezone("Asia/Ho_Chi_Minh")
                .quotaDate(LocalDate.of(2026, 11, 16))
                .resetsAt(LocalDate.of(2026, 11, 17).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();
        when(videoAnalysisQuotaService.getQuotaStatus(1L)).thenReturn(quota);

        ResponseEntity<QuotaEntitlementResponseDto> response = controller.getQuota(testUser);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(MembershipTier.FREE, response.getBody().getMembershipTier());
        assertEquals(1, response.getBody().getRemainingToday());
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
                .exerciseCode("SQUAT")
                .status("PROCESSING")
                .originalVideoUrl("/api/v1/video-analysis/100/original")
                .idempotentReplay(false)
                .build();

        when(videoAnalysisService.initiateVideoAnalysis(
                eq(1L), eq(5L), eq("SQUAT"), eq("idem-1"), eq(true), any()
        )).thenReturn(mockResponse);

        ResponseEntity<VideoAnalysisResponseDto> response = controller.uploadVideo(
                testUser, 5L, "SQUAT", null, "idem-1", null, true, file, null
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getId());
        assertEquals("PROCESSING", response.getBody().getStatus());
    }

    @Test
    void uploadVideo_IdempotentReplay_ReturnsOk() {
        MockMultipartFile file = new MockMultipartFile(
                "video",
                "workout.mp4",
                "video/mp4",
                "dummy video data".getBytes()
        );

        VideoAnalysisResponseDto mockResponse = VideoAnalysisResponseDto.builder()
                .id(100L)
                .userId(1L)
                .exerciseCode("PUSH_UP")
                .status("COMPLETED")
                .idempotentReplay(true)
                .build();

        when(videoAnalysisService.initiateVideoAnalysis(
                eq(1L), isNull(), eq("PUSH_UP"), eq("idem-param"), eq(false), any()
        )).thenReturn(mockResponse);

        ResponseEntity<VideoAnalysisResponseDto> response = controller.uploadVideo(
                testUser, null, null, "PUSH_UP", null, "idem-param", false, file, null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100L, response.getBody().getId());
    }

    @Test
    void uploadVideo_EmptyFileThrowsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("video", "", "video/mp4", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.uploadVideo(testUser, null, null, null, null, null, false, emptyFile, null));

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

    @Test
    void globalExceptionHandler_MapsQuotaExceededAndProMembershipRequiredToExpectedJson() throws Exception {
        QuotaEntitlementResponseDto quota = QuotaEntitlementResponseDto.builder()
                .userId(1L)
                .membershipTier(MembershipTier.FREE)
                .dailyLimit(1)
                .usedToday(1)
                .remainingToday(0)
                .liveAnalysisUnlocked(false)
                .timezone("Asia/Ho_Chi_Minh")
                .quotaDate(LocalDate.of(2026, 11, 16))
                .resetsAt(LocalDate.of(2026, 11, 17).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")))
                .build();

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var tooManyResp = handler.handleQuotaExceededException(new QuotaExceededException("Limit reached", quota));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, tooManyResp.getStatusCode());
        assertEquals("QUOTA_EXCEEDED", tooManyResp.getBody().get("code"));
        assertEquals("FREE", tooManyResp.getBody().get("membershipTier"));
        assertEquals(1, tooManyResp.getBody().get("dailyLimit"));
        assertEquals(1, tooManyResp.getBody().get("usedToday"));
        assertEquals(0, tooManyResp.getBody().get("remainingToday"));
        assertEquals("Asia/Ho_Chi_Minh", tooManyResp.getBody().get("timezone"));
        assertEquals("2026-11-17T00:00:00+07:00", tooManyResp.getBody().get("resetsAt"));

        var forbiddenResp = handler.handleProMembershipRequiredException(
                new ProMembershipRequiredException("PRO required", MembershipTier.FREE, false)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbiddenResp.getStatusCode());
        assertEquals("PRO_MEMBERSHIP_REQUIRED", forbiddenResp.getBody().get("code"));
        assertEquals("FREE", forbiddenResp.getBody().get("membershipTier"));
        assertEquals(false, forbiddenResp.getBody().get("liveAnalysisUnlocked"));

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        MockMvc standalone = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(handler)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();

        // Standalone MockMvc without SecurityContext resolves AuthenticationPrincipal as null -> 401
        standalone.perform(get("/api/v1/video-analysis/quota"))
                .andExpect(status().isUnauthorized());

        // Set SecurityContext for authenticated MockMvc call and verify 429 JSON serialization via GlobalExceptionHandler
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        testUser, null, testUser.getAuthorities())
        );
        try {
            when(videoAnalysisQuotaService.getQuotaStatus(1L)).thenReturn(quota);
            standalone.perform(get("/api/v1/video-analysis/quota"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.membershipTier").value("FREE"))
                    .andExpect(jsonPath("$.quotaDate").value("2026-11-16"))
                    .andExpect(jsonPath("$.resetsAt").value("2026-11-17T00:00:00+07:00"));

            when(videoAnalysisService.initiateVideoAnalysis(eq(1L), isNull(), eq("SQUAT"), isNull(), eq(false), any()))
                    .thenThrow(new QuotaExceededException("Daily quota reached", quota));
            MockMultipartFile video = new MockMultipartFile("video", "squat.mp4", "video/mp4", "abc".getBytes());
            standalone.perform(multipart("/api/v1/video-analysis")
                            .file(video)
                            .param("exerciseType", "SQUAT"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                    .andExpect(jsonPath("$.membershipTier").value("FREE"))
                    .andExpect(jsonPath("$.dailyLimit").value(1))
                    .andExpect(jsonPath("$.usedToday").value(1))
                    .andExpect(jsonPath("$.remainingToday").value(0))
                    .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                    .andExpect(jsonPath("$.resetsAt").value("2026-11-17T00:00:00+07:00"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
