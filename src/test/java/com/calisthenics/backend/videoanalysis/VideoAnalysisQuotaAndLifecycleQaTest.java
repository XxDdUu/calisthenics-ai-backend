package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.analysis.AiServiceClient;
import com.calisthenics.backend.analysis.LiveAnalysisService;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VideoAnalysisQuotaAndLifecycleQaTest.MutableClockConfig.class)
class VideoAnalysisQuotaAndLifecycleQaTest {

    private static final Instant DEFAULT_INSTANT = Instant.parse("2026-11-16T05:00:00Z");

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        public MutableTestClock testApplicationClock() {
            return new MutableTestClock(DEFAULT_INSTANT);
        }
    }

    static class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instantRef;
        private final ZoneId zone;

        MutableTestClock(Instant initialInstant) {
            this(new AtomicReference<>(initialInstant), ZoneOffset.UTC);
        }

        private MutableTestClock(AtomicReference<Instant> instantRef, ZoneId zone) {
            this.instantRef = instantRef;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            instantRef.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableTestClock(instantRef, zone);
        }

        @Override
        public Instant instant() {
            return instantRef.get();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VideoAnalysisRepository videoAnalysisRepository;

    @Autowired
    private VideoAnalysisQuotaUsageRepository quotaUsageRepository;

    @Autowired
    private VideoAnalysisQuotaService videoAnalysisQuotaService;

    @Autowired
    private MutableTestClock testClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = Collections.synchronizedList(new ArrayList<>());

    @MockBean
    private MinioAnalysisMediaStorageService minioStorageService;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private LiveAnalysisService liveAnalysisService;

    @BeforeEach
    void setUp() {
        createdUserIds.clear();
        testClock.setInstant(DEFAULT_INSTANT);
        reset(minioStorageService, aiServiceClient, liveAnalysisService);

        when(minioStorageService.store(any()))
                .thenAnswer(inv -> UUID.randomUUID() + "_orig.mp4");
        when(aiServiceClient.scoreVideo(any(byte[].class), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(buildCompletedAiResult());
        when(aiServiceClient.downloadAnnotatedVideo(any()))
                .thenReturn("annotated-mp4".getBytes());

        ObjectNode liveOk = objectMapper.createObjectNode();
        liveOk.put("status", "VALID");
        liveOk.put("exercise", "SQUAT");
        liveOk.put("score", 92.0);
        when(liveAnalysisService.analyzeFrameSync(
                any(), anyString(), anyString(), anyLong(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(liveOk);
        when(liveAnalysisService.resetSession(anyString()))
                .thenReturn(objectMapper.createObjectNode().put("status", "RESET_SUCCESS"));
        when(liveAnalysisService.terminateSession(anyString()))
                .thenReturn(objectMapper.createObjectNode().put("status", "TERMINATED"));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        List<Long> userIds = new ArrayList<>(createdUserIds);
        createdUserIds.clear();
        for (Long userId : userIds) {
            for (VideoAnalysis analysis : videoAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
                awaitTerminalStatus(analysis.getId());
            }
            jdbcTemplate.update(
                    "DELETE FROM video_analysis_keypoints WHERE video_analysis_id IN (SELECT id FROM video_analyses WHERE user_id = ?)",
                    userId
            );
            jdbcTemplate.update("DELETE FROM video_analyses WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM video_analysis_quota_usages WHERE user_id = ?", userId);
            jdbcTemplate.update(
                    "DELETE FROM rep_histories WHERE session_id IN (SELECT id FROM analysis_sessions WHERE user_id = ?)",
                    userId
            );
            jdbcTemplate.update("DELETE FROM analysis_sessions WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void freeMembership_enforcesOneDailyUploadAndBlocksLiveAnalysis() throws Exception {
        User freeUser = createUser(MembershipTier.FREE);

        mockMvc.perform(get("/api/v1/video-analysis/quota").with(auth(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.dailyLimit").value(1))
                .andExpect(jsonPath("$.usedToday").value(0))
                .andExpect(jsonPath("$.remainingToday").value(1))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(false))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        // 1st upload succeeds with 202
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "SQUAT")
                        .with(auth(freeUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quota.usedToday").value(1))
                .andExpect(jsonPath("$.quota.remainingToday").value(0));

        // 2nd upload fails with 429 QUOTA_EXCEEDED
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PUSH_UP")
                        .with(auth(freeUser)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.dailyLimit").value(1))
                .andExpect(jsonPath("$.remainingToday").value(0));

        // Live analysis endpoints return 403 PRO_MEMBERSHIP_REQUIRED
        MockMultipartFile frameFile = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4}
        );
        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(frameFile)
                        .param("exercise", "SQUAT")
                        .param("session_id", "free-sess-1")
                        .with(auth(freeUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_MEMBERSHIP_REQUIRED"))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(false));

        mockMvc.perform(post("/api/v1/analysis/live/session/free-sess-1/reset").with(auth(freeUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_MEMBERSHIP_REQUIRED"));

        mockMvc.perform(delete("/api/v1/analysis/live/session/free-sess-1").with(auth(freeUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_MEMBERSHIP_REQUIRED"));

        mockMvc.perform(post("/api/v1/analysis/sessions")
                        .with(auth(freeUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"free-sess-1\",\"exerciseType\":\"SQUAT\",\"cameraView\":\"SIDE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_MEMBERSHIP_REQUIRED"));
    }

    @Test
    void proMembership_allowsTenDailyUploadsBlocksEleventhAndUnlocksLiveAnalysis() throws Exception {
        User proUser = createUser(MembershipTier.PRO);

        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(multipart("/api/v1/video-analysis")
                            .file(sampleVideoFile())
                            .param("exerciseType", "SQUAT")
                            .with(auth(proUser)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.quota.membershipTier").value("PRO"))
                    .andExpect(jsonPath("$.quota.dailyLimit").value(10))
                    .andExpect(jsonPath("$.quota.usedToday").value(i))
                    .andExpect(jsonPath("$.quota.remainingToday").value(10 - i));
        }

        // 11th upload blocked with 429
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PULL_UP")
                        .with(auth(proUser)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.dailyLimit").value(10))
                .andExpect(jsonPath("$.usedToday").value(10))
                .andExpect(jsonPath("$.remainingToday").value(0));

        // Live analysis succeeds for PRO user
        MockMultipartFile frameFile = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4}
        );
        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(frameFile)
                        .param("exercise", "SQUAT")
                        .param("session_id", "pro-sess-1")
                        .param("frame_sequence", "1")
                        .param("timestamp_ms", "1000")
                        .with(auth(proUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void quotaResetsAtVietnamMidnight() throws Exception {
        User freeUser = createUser(MembershipTier.FREE);

        // 2026-11-16 23:59:59+07:00 == 2026-11-16T16:59:59Z
        testClock.setInstant(Instant.parse("2026-11-16T16:59:59Z"));

        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PUSH_UP")
                        .with(auth(freeUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quota.quotaDate").value("2026-11-16"))
                .andExpect(jsonPath("$.quota.usedToday").value(1))
                .andExpect(jsonPath("$.quota.remainingToday").value(0))
                .andExpect(jsonPath("$.quota.resetsAt").value("2026-11-17T00:00:00+07:00"));

        // Still before midnight -> 429
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PUSH_UP")
                        .with(auth(freeUser)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));

        // Advance past Vietnam midnight to 2026-11-17 00:00:01+07:00 == 2026-11-16T17:00:01Z
        testClock.setInstant(Instant.parse("2026-11-16T17:00:01Z"));

        mockMvc.perform(get("/api/v1/video-analysis/quota").with(auth(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotaDate").value("2026-11-17"))
                .andExpect(jsonPath("$.usedToday").value(0))
                .andExpect(jsonPath("$.remainingToday").value(1))
                .andExpect(jsonPath("$.resetsAt").value("2026-11-18T00:00:00+07:00"));

        // New day allows upload again
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "SQUAT")
                        .with(auth(freeUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quota.quotaDate").value("2026-11-17"))
                .andExpect(jsonPath("$.quota.usedToday").value(1))
                .andExpect(jsonPath("$.quota.remainingToday").value(0));
    }

    @Test
    void concurrentUploadsForFreeUser_allowOnlyOneAndRejectRestWith429() throws Exception {
        User freeUser = createUser(MembershipTier.FREE);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String idemKey = "concurrent-key-" + i + "-" + UUID.randomUUID();
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                MvcResult res = mockMvc.perform(multipart("/api/v1/video-analysis")
                                .file(sampleVideoFile())
                                .param("exerciseType", "SQUAT")
                                .header("Idempotency-Key", idemKey)
                                .with(auth(freeUser)))
                        .andReturn();
                return res.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(task));
        }
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(15, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        long acceptedCount = statuses.stream().filter(s -> s == 202).count();
        long quotaRejectedCount = statuses.stream().filter(s -> s == 429).count();

        assertThat(acceptedCount).isEqualTo(1L);
        assertThat(quotaRejectedCount).isEqualTo(threads - 1L);

        int usedCount = quotaUsageRepository
                .findUsedCountByUserIdAndQuotaDate(freeUser.getId(), LocalDate.of(2026, 11, 16))
                .orElse(0);
        assertThat(usedCount).isEqualTo(1);
    }

    @Test
    void idempotencyKey_replaysSameAnalysisAndConsumesSingleQuotaUnit() throws Exception {
        User freeUser = createUser(MembershipTier.FREE);
        String key = "idem-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PULL_UP")
                        .header("Idempotency-Key", key)
                        .with(auth(freeUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.quota.usedToday").value(1))
                .andExpect(jsonPath("$.quota.remainingToday").value(0))
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        long analysisId = firstJson.get("id").asLong();

        // Replay 1 with same Idempotency-Key
        MvcResult second = mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PULL_UP")
                        .header("Idempotency-Key", key)
                        .with(auth(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId))
                .andExpect(jsonPath("$.quota.usedToday").value(1))
                .andExpect(jsonPath("$.quota.remainingToday").value(0))
                .andReturn();

        // Replay 2 with same Idempotency-Key
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PULL_UP")
                        .header("Idempotency-Key", key)
                        .with(auth(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId))
                .andExpect(jsonPath("$.quota.usedToday").value(1));

        verify(minioStorageService, times(1)).store(any());

        // Different Idempotency-Key still hits FREE quota limit -> 429
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PULL_UP")
                        .header("Idempotency-Key", "different-" + UUID.randomUUID())
                        .with(auth(freeUser)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }

    @Test
    void ownershipValidation_forbidsOtherUserFromReadingOrStreamingAnalysis() throws Exception {
        User userA = createUser(MembershipTier.FREE);
        User userB = createUser(MembershipTier.PRO);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "SQUAT")
                        .with(auth(userA)))
                .andExpect(status().isAccepted())
                .andReturn();

        long analysisId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();
        VideoAnalysis completed = awaitTerminalStatus(analysisId);
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getAnnotatedVideoKey()).isNotBlank();

        StatObjectResponse statMock = mock(StatObjectResponse.class);
        when(statMock.size()).thenReturn(16L);
        when(statMock.contentType()).thenReturn("video/mp4");
        when(minioStorageService.stat(anyString())).thenReturn(statMock);
        when(minioStorageService.open(anyString())).thenAnswer(inv -> new ByteArrayInputStream(new byte[16]));

        // Owner A succeeds on all endpoints
        mockMvc.perform(get("/api/v1/video-analysis/{id}", analysisId).with(auth(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(analysisId));
        mockMvc.perform(get("/api/v1/video-analysis/{id}/keypoints", analysisId).with(auth(userA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/video-analysis/{id}/original", analysisId).with(auth(userA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/video-analysis/{id}/annotated", analysisId).with(auth(userA)))
                .andExpect(status().isOk());

        // User B receives 403 Forbidden on all endpoints
        mockMvc.perform(get("/api/v1/video-analysis/{id}", analysisId).with(auth(userB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/video-analysis/{id}/keypoints", analysisId).with(auth(userB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/video-analysis/{id}/original", analysisId).with(auth(userB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/video-analysis/{id}/annotated", analysisId).with(auth(userB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedAiJobLifecycle_persistsStatusAndErrorMessageForAiFailedResponseAndException() throws Exception {
        User proUser = createUser(MembershipTier.PRO);

        // 1. AI service returns FAILED status
        AiVideoScoringResponseDto failedDto = AiVideoScoringResponseDto.builder()
                .analysisId("ai-fail-1")
                .status("FAILED")
                .errorMessage("Corrupt MP4 container stream")
                .feedback(List.of("Corrupt MP4 container stream"))
                .build();
        when(aiServiceClient.scoreVideo(any(byte[].class), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(failedDto);

        MvcResult res1 = mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PUSH_UP")
                        .with(auth(proUser)))
                .andExpect(status().isAccepted())
                .andReturn();
        long id1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asLong();

        VideoAnalysis persisted1 = awaitTerminalStatus(id1);
        assertThat(persisted1.getStatus()).isEqualTo("FAILED");
        assertThat(persisted1.getErrorMessage()).contains("Corrupt MP4 container stream");

        mockMvc.perform(get("/api/v1/video-analysis/{id}", id1).with(auth(proUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Corrupt MP4 container stream"));

        // 2. AI service throws runtime exception
        when(aiServiceClient.scoreVideo(any(byte[].class), any(), any(), anyInt(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("AI Pose Analysis service unavailable"));

        MvcResult res2 = mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "SQUAT")
                        .with(auth(proUser)))
                .andExpect(status().isAccepted())
                .andReturn();
        long id2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asLong();

        VideoAnalysis persisted2 = awaitTerminalStatus(id2);
        assertThat(persisted2.getStatus()).isEqualTo("FAILED");
        assertThat(persisted2.getErrorMessage()).contains("AI Pose Analysis service unavailable");
    }

    @Test
    void adminTier_unlimitedVideoUploads_bypassesDailyQuotaChecks() throws Exception {
        User adminUser = createAdmin(MembershipTier.FREE);

        // Verify initial entitlements for ADMIN
        mockMvc.perform(get("/api/v1/video-analysis/quota").with(auth(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("ADMIN"))
                .andExpect(jsonPath("$.dailyLimit").doesNotExist())
                .andExpect(jsonPath("$.remainingToday").doesNotExist())
                .andExpect(jsonPath("$.unlimited").value(true))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(true))
                .andExpect(jsonPath("$.usedToday").value(0));

        // Upload 12 videos in a single day (exceeding PRO 10 and FREE 1)
        for (int i = 1; i <= 12; i++) {
            mockMvc.perform(multipart("/api/v1/video-analysis")
                            .file(sampleVideoFile())
                            .param("exerciseType", "SQUAT")
                            .with(auth(adminUser)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.quota.membershipTier").value("ADMIN"))
                    .andExpect(jsonPath("$.quota.unlimited").value(true))
                    .andExpect(jsonPath("$.quota.usedToday").value(i))
                    .andExpect(jsonPath("$.quota.remainingToday").doesNotExist());
        }

        // Verify usage count in DB
        LocalDate today = testClock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        int usedInDb = quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(adminUser.getId(), today).orElse(0);
        assertThat(usedInDb).isEqualTo(12);

        // Verify profile endpoint also reflects ADMIN unlimited quota
        mockMvc.perform(get("/api/v1/users/me").with(auth(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.membershipTier").value("ADMIN"))
                .andExpect(jsonPath("$.dailyQuotaLimit").doesNotExist())
                .andExpect(jsonPath("$.dailyQuotaRemaining").doesNotExist())
                .andExpect(jsonPath("$.dailyQuotaUsed").value(12))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(true));
    }

    @Test
    void adminTier_canUseLiveAnalysisWithoutProSubscription() throws Exception {
        User adminUser = createAdmin(MembershipTier.FREE);

        MockMultipartFile frameFile = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(frameFile)
                        .param("exercise", "SQUAT")
                        .param("session_id", "admin-live-sess-1")
                        .with(auth(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));

        mockMvc.perform(post("/api/v1/analysis/live/session/admin-live-sess-1/reset").with(auth(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET_SUCCESS"));

        mockMvc.perform(post("/api/v1/analysis/sessions")
                        .with(auth(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"admin-live-sess-1\",\"exerciseType\":\"SQUAT\",\"cameraView\":\"SIDE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminTier_retainsAccessWithoutProSubscriptionOrWhenManualProGrantRevoked() throws Exception {
        // Admin with underlying PRO (e.g. manual grant)
        User adminUser = createAdmin(MembershipTier.PRO);

        QuotaEntitlementResponseDto quotaBefore = videoAnalysisQuotaService.getQuotaStatus(adminUser.getId());
        assertThat(quotaBefore.getMembershipTier()).isEqualTo(MembershipTier.ADMIN);
        assertThat(quotaBefore.getUnlimited()).isTrue();
        assertThat(quotaBefore.getLiveAnalysisUnlocked()).isTrue();

        // Revoke manual PRO grant by updating underlying membership to FREE
        adminUser.setMembershipTier(MembershipTier.FREE);
        userRepository.saveAndFlush(adminUser);

        // ADMIN entitlements are completely unaffected
        QuotaEntitlementResponseDto quotaAfter = videoAnalysisQuotaService.getQuotaStatus(adminUser.getId());
        assertThat(quotaAfter.getMembershipTier()).isEqualTo(MembershipTier.ADMIN);
        assertThat(quotaAfter.getUnlimited()).isTrue();
        assertThat(quotaAfter.getLiveAnalysisUnlocked()).isTrue();
        assertThat(quotaAfter.getDailyLimit()).isNull();
    }

    @Test
    void adminTier_removingAdminRole_fallsBackToActualMembershipEntitlements() throws Exception {
        // Create 2 admins so last admin guard does not block demotion
        User admin1 = createAdmin(MembershipTier.FREE);
        User admin2 = createAdmin(MembershipTier.FREE);

        // Admin uploads 2 videos (more than FREE limit of 1)
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "SQUAT")
                        .with(auth(admin1)))
                .andExpect(status().isAccepted());

        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PUSH_UP")
                        .with(auth(admin1)))
                .andExpect(status().isAccepted());

        // Now admin2 demotes admin1 to ROLE_USER
        mockMvc.perform(put("/api/v1/admin/users/{userId}/role", admin1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_USER\"}")
                        .with(auth(admin2)))
                .andExpect(status().isOk());

        User demotedUser = userRepository.findById(admin1.getId()).orElseThrow();
        assertThat(demotedUser.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(demotedUser.getMembershipTier()).isEqualTo(MembershipTier.FREE);

        // Now verify demoted account falls back to actual FREE membership
        mockMvc.perform(get("/api/v1/video-analysis/quota").with(auth(demotedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.dailyLimit").value(1))
                .andExpect(jsonPath("$.usedToday").value(2))
                .andExpect(jsonPath("$.remainingToday").value(0))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(false));

        // 3rd upload is rejected because daily limit for FREE is reached (2 >= 1)
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(sampleVideoFile())
                        .param("exerciseType", "PULL_UP")
                        .with(auth(demotedUser)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.dailyLimit").value(1));

        // Live analysis is now locked
        MockMultipartFile frameFile = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4}
        );
        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(frameFile)
                        .param("exercise", "SQUAT")
                        .param("session_id", "demoted-sess-1")
                        .with(auth(demotedUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_MEMBERSHIP_REQUIRED"));
    }

    @Test
    void cannotSelfGrantAdminViaMembershipUpdate() throws Exception {
        User freeUser = createUser(MembershipTier.FREE);

        mockMvc.perform(put("/api/v1/users/me/membership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipTier\":\"ADMIN\"}")
                        .with(auth(freeUser)))
                .andExpect(status().isForbidden());

        User refreshed = userRepository.findById(freeUser.getId()).orElseThrow();
        assertThat(refreshed.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(refreshed.getMembershipTier()).isEqualTo(MembershipTier.FREE);
    }

    @Test
    void adminAccess_doesNotBypassSecurityAuthenticationOrInfrastructureSafeguards() throws Exception {
        User adminUser = createAdmin(MembershipTier.FREE);

        // 1. Unauthenticated requests are rejected with 401
        mockMvc.perform(get("/api/v1/video-analysis/quota"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/v1/video-analysis").file(sampleVideoFile()))
                .andExpect(status().isUnauthorized());

        // 2. Empty video file is rejected with 400
        MockMultipartFile emptyFile = new MockMultipartFile("video", "empty.mp4", "video/mp4", new byte[0]);
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(emptyFile)
                        .param("exerciseType", "SQUAT")
                        .with(auth(adminUser)))
                .andExpect(status().isBadRequest());

        // 3. Invalid video format is rejected with 400
        MockMultipartFile badFormat = new MockMultipartFile("video", "bad.exe", "application/octet-stream", new byte[]{1, 2});
        mockMvc.perform(multipart("/api/v1/video-analysis")
                        .file(badFormat)
                        .param("exerciseType", "SQUAT")
                        .with(auth(adminUser)))
                .andExpect(status().isBadRequest());

        // 4. Live analysis oversized frame (>2MB) is rejected with 413
        byte[] oversizedBytes = new byte[2 * 1024 * 1024 + 1024];
        MockMultipartFile bigFrame = new MockMultipartFile("file", "big.jpg", MediaType.IMAGE_JPEG_VALUE, oversizedBytes);
        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(bigFrame)
                        .param("exercise", "SQUAT")
                        .param("session_id", "admin-frame-sess")
                        .with(auth(adminUser)))
                .andExpect(status().isPayloadTooLarge());
    }

    // ---------------------------------------------------------------- helpers

    private User createAdmin(MembershipTier underlyingTier) {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("admin_" + UUID.randomUUID() + "@example.com")
                .fullName("Admin QA Athlete")
                .role(Role.ROLE_ADMIN)
                .membershipTier(underlyingTier != null ? underlyingTier : MembershipTier.FREE)
                .build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private User createUser(MembershipTier tier) {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("qa_" + UUID.randomUUID() + "@example.com")
                .fullName("QA Athlete " + tier.name())
                .role(Role.ROLE_USER)
                .membershipTier(tier)
                .build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private RequestPostProcessor auth(User user) {
        return user(UserPrincipal.create(user));
    }

    private MockMultipartFile sampleVideoFile() {
        return new MockMultipartFile(
                "video",
                "workout.mp4",
                "video/mp4",
                "synthetic-mp4-bytes".getBytes()
        );
    }

    private VideoAnalysis awaitTerminalStatus(long analysisId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000L;
        VideoAnalysis latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = videoAnalysisRepository.findById(analysisId).orElse(null);
            if (latest != null
                    && !"PENDING".equalsIgnoreCase(latest.getStatus())
                    && !"PROCESSING".equalsIgnoreCase(latest.getStatus())) {
                return latest;
            }
            Thread.sleep(40L);
        }
        return latest;
    }

    private AiVideoScoringResponseDto buildCompletedAiResult() {
        return AiVideoScoringResponseDto.builder()
                .schemaVersion("1.0")
                .analysisId(UUID.randomUUID().toString())
                .status("COMPLETED")
                .exercise("SQUAT")
                .exerciseCode("SQUAT")
                .score(88.0)
                .formAccuracy(90.0)
                .stability(86.0)
                .alignment(88.0)
                .repCount(2)
                .validReps(2)
                .incompleteReps(0)
                .metrics(Map.of("analyzedFrames", 20))
                .feedback(List.of("Good squat depth"))
                .recommendations(List.of("Keep chest proud"))
                .video(AiVideoOutputMetadataDto.builder()
                        .durationMs(4000L)
                        .width(640)
                        .height(480)
                        .fps(10.0)
                        .build())
                .keypoints(AiVideoKeypointsDataDto.builder()
                        .format("COCO_17")
                        .frames(List.of(
                                AiVideoKeypointFrameDto.builder()
                                        .frameIndex(0)
                                        .timestampMs(0L)
                                        .keypoints(List.of(
                                                AiVideoKeypointItemDto.builder()
                                                        .name("left_knee")
                                                        .x(0.45f)
                                                        .y(0.65f)
                                                        .confidence(0.95f)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();
    }
}
