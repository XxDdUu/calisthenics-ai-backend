package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.analysis.AiServiceClient;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.dto.*;
import com.calisthenics.backend.videoanalysis.exception.ProMembershipRequiredException;
import com.calisthenics.backend.videoanalysis.exception.QuotaExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoAnalysisServiceTest {

    @Mock
    private VideoAnalysisRepository videoAnalysisRepository;

    @Mock
    private VideoAnalysisKeypointRepository keypointRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private MinioAnalysisMediaStorageService minioStorageService;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private VideoAnalysisQuotaService videoAnalysisQuotaService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Spy
    private TaskExecutor taskExecutor = new SyncTaskExecutor();

    private VideoAnalysisService service;

    private User testUser;
    private Exercise testExercise;

    @BeforeEach
    void setUp() {
        service = new VideoAnalysisService(
                videoAnalysisRepository,
                keypointRepository,
                userRepository,
                exerciseRepository,
                minioStorageService,
                aiServiceClient,
                objectMapper,
                taskExecutor,
                videoAnalysisQuotaService
        );

        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .role(Role.ROLE_USER)
                .membershipTier(MembershipTier.FREE)
                .build();
        testExercise = Exercise.builder().id(5L).name("Planche").code("PLANCHE").build();
    }

    @Test
    void initiateVideoAnalysis_Success() {
        MockMultipartFile file = new MockMultipartFile("video", "test.mp4", "video/mp4", "dummy content".getBytes());

        QuotaEntitlementResponseDto reservedQuota = QuotaEntitlementResponseDto.builder()
                .userId(1L)
                .membershipTier(MembershipTier.FREE)
                .dailyLimit(1)
                .usedToday(1)
                .remainingToday(0)
                .liveAnalysisUnlocked(false)
                .timezone("Asia/Ho_Chi_Minh")
                .quotaDate(LocalDate.of(2026, 11, 16))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findById(5L)).thenReturn(Optional.of(testExercise));
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L)).thenReturn(reservedQuota);
        when(minioStorageService.store(any())).thenReturn("uuid_orig.mp4");

        VideoAnalysis savedEntity = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .exercise(testExercise)
                .schemaVersion("1.0")
                .exerciseCode("PLANCHE")
                .originalVideoKey("uuid_orig.mp4")
                .status("PENDING")
                .build();
        when(videoAnalysisRepository.save(any())).thenReturn(savedEntity);

        VideoAnalysisResponseDto response = service.initiateVideoAnalysis(1L, 5L, file);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("1.0", response.getSchemaVersion());
        assertEquals("PLANCHE", response.getExerciseCode());
        assertNotNull(response.getQuota());
        assertEquals(1, response.getQuota().getUsedToday());
        assertFalse(response.isIdempotentReplay());
        verify(videoAnalysisQuotaService).reserveVideoAnalysisQuota(1L);
        verify(minioStorageService).store(file);
    }

    @Test
    void initiateVideoAnalysis_WithExerciseTypeAndSideView_PassesSideViewAndNormalizedCode() {
        MockMultipartFile file = new MockMultipartFile("video", "squat.mp4", "video/mp4", "video bytes".getBytes());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("SQUAT")).thenReturn(Optional.empty());
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L)).thenReturn(
                QuotaEntitlementResponseDto.builder().userId(1L).quotaDate(LocalDate.of(2026, 11, 16)).build()
        );
        when(minioStorageService.store(any())).thenReturn("squat_orig.mp4");
        when(videoAnalysisRepository.save(any())).thenAnswer(inv -> {
            VideoAnalysis va = inv.getArgument(0);
            va.setId(22L);
            return va;
        });

        VideoAnalysisResponseDto response = service.initiateVideoAnalysis(1L, null, "squat", "idem-squat-1", true, file);

        assertEquals("SQUAT", response.getExerciseCode());
        assertEquals("Bodyweight Squat", response.getExerciseName());
        verify(aiServiceClient).scoreVideo(any(), eq("squat.mp4"), eq("SQUAT"), eq(10), anyString(), eq(true));
    }

    @Test
    void initiateVideoAnalysis_IdempotentReplay_DoesNotReserveQuotaOrReupload() {
        MockMultipartFile file = new MockMultipartFile("video", "squat.mp4", "video/mp4", "video bytes".getBytes());
        VideoAnalysis existing = VideoAnalysis.builder()
                .id(77L)
                .user(testUser)
                .schemaVersion("1.0")
                .exerciseCode("PUSH_UP")
                .idempotencyKey("idem-abc")
                .originalVideoKey("orig_77.mp4")
                .status("COMPLETED")
                .repCount(5)
                .validReps(4)
                .incompleteReps(1)
                .build();
        QuotaEntitlementResponseDto currentQuota = QuotaEntitlementResponseDto.builder()
                .userId(1L)
                .membershipTier(MembershipTier.FREE)
                .dailyLimit(1)
                .usedToday(1)
                .remainingToday(0)
                .build();

        when(videoAnalysisRepository.findByUserIdAndIdempotencyKey(1L, "idem-abc")).thenReturn(Optional.of(existing));
        when(videoAnalysisQuotaService.getQuotaStatus(1L)).thenReturn(currentQuota);

        VideoAnalysisResponseDto response = service.initiateVideoAnalysis(1L, null, "PUSH_UP", "  idem-abc  ", false, file);

        assertTrue(response.isIdempotentReplay());
        assertEquals(77L, response.getId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(5, response.getRepCount());
        assertEquals(1, response.getQuota().getUsedToday());
        verify(videoAnalysisQuotaService, never()).reserveVideoAnalysisQuota(anyLong());
        verify(minioStorageService, never()).store(any());
        verify(taskExecutor, never()).execute(any());
    }

    @Test
    void initiateVideoAnalysis_InvalidFileOrExercise_DoesNotReserveQuota() {
        MockMultipartFile badFile = new MockMultipartFile("video", "notes.txt", "text/plain", "hello".getBytes());
        ResponseStatusException badFileEx = assertThrows(ResponseStatusException.class, () ->
                service.initiateVideoAnalysis(1L, null, "SQUAT", null, false, badFile));
        assertEquals(HttpStatus.BAD_REQUEST, badFileEx.getStatusCode());

        MockMultipartFile validFile = new MockMultipartFile("video", "clip.mp4", "video/mp4", "bytes".getBytes());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("UNSUPPORTED_BARBELL")).thenReturn(Optional.empty());
        ResponseStatusException badExerciseEx = assertThrows(ResponseStatusException.class, () ->
                service.initiateVideoAnalysis(1L, null, "UNSUPPORTED_BARBELL", null, false, validFile));
        assertEquals(HttpStatus.BAD_REQUEST, badExerciseEx.getStatusCode());

        when(exerciseRepository.findById(999L)).thenReturn(Optional.empty());
        ResponseStatusException badIdEx = assertThrows(ResponseStatusException.class, () ->
                service.initiateVideoAnalysis(1L, 999L, null, null, false, validFile));
        assertEquals(HttpStatus.BAD_REQUEST, badIdEx.getStatusCode());

        verify(videoAnalysisQuotaService, never()).reserveVideoAnalysisQuota(anyLong());
    }

    @Test
    void initiateVideoAnalysis_QuotaExceeded_Propagates429WithoutUploading() {
        MockMultipartFile file = new MockMultipartFile("video", "clip.mp4", "video/mp4", "bytes".getBytes());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("PULL_UP")).thenReturn(Optional.empty());
        QuotaEntitlementResponseDto quota = QuotaEntitlementResponseDto.builder()
                .userId(1L).membershipTier(MembershipTier.FREE).dailyLimit(1).usedToday(1).remainingToday(0).build();
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L))
                .thenThrow(new QuotaExceededException("Daily limit reached", quota));

        assertThrows(QuotaExceededException.class, () ->
                service.initiateVideoAnalysis(1L, null, "PULL_UP", null, false, file));

        verify(minioStorageService, never()).store(any());
        verify(videoAnalysisRepository, never()).save(any());
    }

    @Test
    void initiateVideoAnalysis_MinioFailure_ReleasesReservedQuota() {
        MockMultipartFile file = new MockMultipartFile("video", "clip.mp4", "video/mp4", "bytes".getBytes());
        LocalDate date = LocalDate.of(2026, 11, 16);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("SQUAT")).thenReturn(Optional.empty());
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L)).thenReturn(
                QuotaEntitlementResponseDto.builder().userId(1L).quotaDate(date).build()
        );
        when(minioStorageService.store(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MinIO down"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.initiateVideoAnalysis(1L, null, "SQUAT", null, false, file));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verify(videoAnalysisQuotaService).releaseReservedQuota(1L, date);
    }

    @Test
    void initiateVideoAnalysis_ConcurrentIdempotencyConflict_ReleasesQuotaAndReturnsExisting() {
        MockMultipartFile file = new MockMultipartFile("video", "clip.mp4", "video/mp4", "bytes".getBytes());
        LocalDate date = LocalDate.of(2026, 11, 16);
        VideoAnalysis winningRow = VideoAnalysis.builder()
                .id(88L).user(testUser).schemaVersion("1.0").exerciseCode("SQUAT")
                .idempotencyKey("race-key").originalVideoKey("winner.mp4").status("PENDING").build();

        when(videoAnalysisRepository.findByUserIdAndIdempotencyKey(1L, "race-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winningRow));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("SQUAT")).thenReturn(Optional.empty());
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L)).thenReturn(
                QuotaEntitlementResponseDto.builder().userId(1L).quotaDate(date).usedToday(2).build()
        );
        when(videoAnalysisQuotaService.getQuotaStatus(1L)).thenReturn(
                QuotaEntitlementResponseDto.builder().userId(1L).quotaDate(date).usedToday(1).build()
        );
        when(minioStorageService.store(any())).thenReturn("duplicate_orig.mp4");
        when(videoAnalysisRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        VideoAnalysisResponseDto response = service.initiateVideoAnalysis(1L, null, "SQUAT", "race-key", false, file);

        assertTrue(response.isIdempotentReplay());
        assertEquals(88L, response.getId());
        verify(videoAnalysisQuotaService).releaseReservedQuota(1L, date);
        verify(minioStorageService).delete("duplicate_orig.mp4");
        verify(taskExecutor, never()).execute(any());
    }

    @Test
    void completeAnalysis_SavesScoresV1FieldsAndKeypoints() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .exercise(testExercise)
                .originalVideoKey("uuid_orig.mp4")
                .status("PROCESSING")
                .build();
        when(videoAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        AiVideoScoringResponseDto aiResult = AiVideoScoringResponseDto.builder()
                .schemaVersion("1.0")
                .analysisId("test-uuid")
                .status("COMPLETED")
                .exerciseCode("SQUAT")
                .score(85.5)
                .formAccuracy(90.0)
                .stability(82.0)
                .alignment(84.0)
                .repCount(4)
                .validReps(3)
                .incompleteReps(1)
                .reps(List.of(
                        AiRepBreakdownDto.builder().repNumber(1).valid(true).status("VALID").formScore(91.0).minAngleDeg(88.0).build(),
                        AiRepBreakdownDto.builder().repNumber(2).valid(false).status("INCOMPLETE").formScore(65.0).issues(List.of("SHALLOW_DEPTH")).build()
                ))
                .movementMetrics(Map.of(
                        "squat_depth", AiMovementMetricDto.builder().key("squat_depth").name("Squat Depth").value(89.5).unit("deg").score(92.0).status("VALID").build()
                ))
                .issues(List.of(
                        AiStructuredIssueDto.builder().code("SHALLOW_DEPTH").severity("MEDIUM").message("Reach parallel on rep 2").affectedReps(List.of(2)).build()
                ))
                .recommendations(List.of("Sit deeper into the bottom position", "Keep heels planted"))
                .cameraQuality(AiCameraQualityDto.builder().overallQuality("GOOD").score(94.0).fullBodyVisibleRatio(0.96).sideViewDetected(true).build())
                .video(AiVideoOutputMetadataDto.builder()
                        .durationMs(5000L)
                        .width(1920)
                        .height(1080)
                        .fps(30.0)
                        .build())
                .metrics(Map.of("elbow_extension", 175.0))
                .feedback(List.of("Lock elbows completely"))
                .keypoints(AiVideoKeypointsDataDto.builder()
                        .format("COCO_17")
                        .frames(List.of(
                                AiVideoKeypointFrameDto.builder()
                                        .frameIndex(0)
                                        .timestampMs(0L)
                                        .keypoints(List.of(
                                                AiVideoKeypointItemDto.builder()
                                                        .name("left_shoulder")
                                                        .x(0.5f)
                                                        .y(0.3f)
                                                        .confidence(0.9f)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();

        service.completeAnalysis(10L, aiResult, "uuid_annot.mp4");

        assertEquals("COMPLETED", analysis.getStatus());
        assertEquals("1.0", analysis.getSchemaVersion());
        assertEquals("SQUAT", analysis.getExerciseCode());
        assertEquals(4, analysis.getRepCount());
        assertEquals(3, analysis.getValidReps());
        assertEquals(1, analysis.getIncompleteReps());
        assertEquals(BigDecimal.valueOf(85.5), analysis.getOverallScore());
        assertEquals("uuid_annot.mp4", analysis.getAnnotatedVideoKey());
        assertNotNull(analysis.getRepsJson());
        assertNotNull(analysis.getMovementMetricsJson());
        assertNotNull(analysis.getIssuesJson());
        assertNotNull(analysis.getRecommendationsJson());
        assertNotNull(analysis.getCameraQualityJson());
        verify(keypointRepository).saveAll(anyList());
        verify(videoAnalysisRepository).save(analysis);

        VideoAnalysisResponseDto dto = service.getAnalysisById(10L, 1L, false);
        assertEquals(4, dto.getRepCount());
        assertEquals(2, dto.getReps().size());
        assertEquals(1, dto.getMovementMetrics().size());
        assertEquals(1, dto.getIssues().size());
        assertEquals(2, dto.getRecommendations().size());
        assertNotNull(dto.getCameraQuality());
        assertEquals("GOOD", dto.getCameraQuality().getOverallQuality());
        assertEquals("VALID", dto.getMovementMetrics().get("squat_depth").getStatus());
        assertEquals(List.of(2), dto.getIssues().get(0).getAffectedReps());
    }

    @Test
    void processAnalysisAsync_InsufficientPoseQuality_RecordsFailureAndQualityMetadata() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(33L)
                .user(testUser)
                .exerciseCode("SQUAT")
                .originalVideoKey("orig_33.mp4")
                .status("PENDING")
                .build();
        when(videoAnalysisRepository.findById(33L)).thenReturn(Optional.of(analysis));

        AiVideoScoringResponseDto failedAi = AiVideoScoringResponseDto.builder()
                .schemaVersion("1.0")
                .status("INSUFFICIENT_POSE_QUALITY")
                .errorMessage("Full body not visible for enough frames")
                .feedback(List.of("Step back so full body fits inside frame"))
                .cameraQuality(AiCameraQualityDto.builder()
                        .overallQuality("POOR")
                        .fullBodyVisibleRatio(0.18)
                        .warnings(List.of("LOW_COVERAGE"))
                        .build())
                .movementMetrics(Map.of(
                        "squat_depth", AiMovementMetricDto.builder().name("Squat Depth").value(null).score(null).status("UNAVAILABLE").build()
                ))
                .build();
        when(aiServiceClient.scoreVideo(any(), eq("bad.mp4"), eq("SQUAT"), eq(10), eq("uuid-33"), eq(true)))
                .thenReturn(failedAi);

        service.processAnalysisAsync(33L, new byte[]{1, 2, 3}, "bad.mp4", "SQUAT", "uuid-33", true);

        assertEquals("INSUFFICIENT_POSE_QUALITY", analysis.getStatus());
        assertEquals("Full body not visible for enough frames", analysis.getErrorMessage());
        assertNotNull(analysis.getFeedbackJson());
        assertNotNull(analysis.getCameraQualityJson());
        assertNotNull(analysis.getMovementMetricsJson());
        verify(aiServiceClient, never()).downloadAnnotatedVideo(anyString());
    }

    @Test
    void aiVideoScoringResponseDto_DeserializesPythonAiServiceV1PayloadAndSerializesForAndroid() throws Exception {
        String aiServicePayload = """
                {
                  "schemaVersion": "1.0",
                  "analysisId": "abc-123",
                  "status": "COMPLETED",
                  "exercise": "SQUAT",
                  "score": 88.0,
                  "formAccuracy": 90.0,
                  "stability": 85.0,
                  "alignment": 89.0,
                  "repCount": 3,
                  "validReps": 2,
                  "incompleteReps": 1,
                  "reps": [
                    {
                      "repNumber": 1,
                      "startFrame": 2,
                      "endFrame": 20,
                      "startTimestampMs": 200,
                      "endTimestampMs": 2000,
                      "durationMs": 1800,
                      "isValid": true,
                      "status": "VALID",
                      "formScore": 91.5,
                      "minAngleDeg": 86.0,
                      "maxAngleDeg": 175.0,
                      "romDeg": 89.0,
                      "depthClassification": "GOOD",
                      "keyMetrics": {"knee_angle": 86.0},
                      "issues": [],
                      "feedback": ["Good depth.", "Clean lockout."]
                    }
                  ],
                  "movementMetrics": {
                    "squat_depth": {
                      "name": "Squat Depth",
                      "unit": "deg",
                      "value": 86.0,
                      "min": 86.0,
                      "max": 115.0,
                      "avg": 95.0,
                      "targetRange": "<= 100 deg",
                      "score": 92.0,
                      "status": "VALID",
                      "confidence": 0.94
                    },
                    "knee_alignment": {
                      "name": "Knee Alignment",
                      "unit": "ratio",
                      "value": null,
                      "score": null,
                      "targetRange": ">= 0.82",
                      "status": "UNAVAILABLE",
                      "confidence": 0.0
                    }
                  },
                  "issues": [
                    {
                      "code": "SHALLOW_DEPTH",
                      "metric": "squat_depth",
                      "severity": "MEDIUM",
                      "title": "Shallow Depth",
                      "message": "Rep 2 did not reach parallel.",
                      "recommendation": "Sink hips lower.",
                      "measuredValue": 115.0,
                      "targetRange": "<= 100 deg",
                      "occurrences": 1,
                      "affectedReps": [2]
                    }
                  ],
                  "recommendations": ["Sink hips lower."],
                  "cameraQuality": {
                    "overallQuality": "GOOD",
                    "score": 91.0,
                    "viewAngle": "SIDE",
                    "fullBodyVisibleRatio": 0.95,
                    "averageConfidence": 0.92,
                    "missingLandmarks": [],
                    "warnings": []
                  }
                }
                """;
        AiVideoScoringResponseDto dto = objectMapper.readValue(aiServicePayload, AiVideoScoringResponseDto.class);
        assertEquals("1.0", dto.getSchemaVersion());
        assertEquals("SQUAT", dto.getExercise());
        assertEquals(3, dto.getRepCount());
        assertEquals(2, dto.getValidReps());
        assertEquals(1, dto.getIncompleteReps());
        assertEquals(1, dto.getReps().size());
        assertTrue(dto.getReps().get(0).getValid());
        assertEquals(200L, dto.getReps().get(0).getStartTimestampMs());
        assertEquals(91.5, dto.getReps().get(0).getFormScore());
        assertEquals(86.0, dto.getReps().get(0).getMinAngleDeg());
        assertEquals(89.0, dto.getReps().get(0).getRomDeg());
        assertEquals("GOOD", dto.getReps().get(0).getDepthClassification());
        assertEquals("Good depth.; Clean lockout.", dto.getReps().get(0).getFeedback());
        assertEquals(2, dto.getMovementMetrics().size());
        assertEquals("VALID", dto.getMovementMetrics().get("squat_depth").getStatus());
        assertEquals("UNAVAILABLE", dto.getMovementMetrics().get("knee_alignment").getStatus());
        assertNull(dto.getMovementMetrics().get("knee_alignment").getValue());
        assertEquals(1, dto.getIssues().size());
        assertEquals(List.of(2), dto.getIssues().get(0).getAffectedReps());
        assertEquals("GOOD", dto.getCameraQuality().getOverallQuality());
        assertEquals("SIDE", dto.getCameraQuality().getViewAngle());
        assertEquals(0.95, dto.getCameraQuality().getFullBodyVisibleRatio());
    }

    @Test
    void findAndVerifyOwnership_ThrowsForbiddenWhenDifferentUser() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .build();
        when(videoAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        assertThrows(ResponseStatusException.class, () ->
                service.findAndVerifyOwnership(10L, 999L, false));
    }

    @Test
    void findAndVerifyOwnership_AllowsAdminAccess() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .build();
        when(videoAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        VideoAnalysis result = service.findAndVerifyOwnership(10L, 999L, true);
        assertNotNull(result);
        assertEquals(10L, result.getId());
    }

    @Test
    void initiateVideoAnalysis_AiExceptionWithTransientLookupMiss_RetriesAndPersistsFailedStatus() {
        MockMultipartFile file = new MockMultipartFile("video", "squat.mp4", "video/mp4", "video bytes".getBytes());
        LocalDate date = LocalDate.of(2026, 11, 16);
        VideoAnalysis[] savedHolder = new VideoAnalysis[1];
        List<String> savedStatuses = new ArrayList<>();
        boolean[] aiCalled = new boolean[1];
        boolean[] transientMissInjected = new boolean[1];

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("SQUAT")).thenReturn(Optional.empty());
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L)).thenReturn(
                QuotaEntitlementResponseDto.builder().userId(1L).quotaDate(date).build()
        );
        when(minioStorageService.store(any())).thenReturn("orig_99.mp4");
        when(videoAnalysisRepository.save(any(VideoAnalysis.class))).thenAnswer(invocation -> {
            VideoAnalysis entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(99L);
            }
            savedHolder[0] = entity;
            savedStatuses.add(entity.getStatus());
            return entity;
        });
        when(videoAnalysisRepository.findById(99L)).thenAnswer(invocation -> {
            if (aiCalled[0] && !transientMissInjected[0]) {
                transientMissInjected[0] = true;
                return Optional.empty();
            }
            return Optional.ofNullable(savedHolder[0]);
        });
        when(aiServiceClient.scoreVideo(any(), anyString(), anyString(), anyInt(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    aiCalled[0] = true;
                    throw new RuntimeException("AI inference timed out");
                });

        VideoAnalysisResponseDto response = service.initiateVideoAnalysis(1L, null, "SQUAT", null, false, file);

        assertNotNull(response);
        assertNotNull(savedHolder[0]);
        assertEquals("FAILED", savedHolder[0].getStatus());
        assertNotNull(savedHolder[0].getErrorMessage());
        assertTrue(savedHolder[0].getErrorMessage().contains("AI inference timed out"));
        assertTrue(savedStatuses.contains("PENDING"));
        assertTrue(savedStatuses.contains("FAILED"));
        assertTrue(transientMissInjected[0]);
    }

    @Test
    void initiateVideoAnalysis_AiFailedResponse_PersistsFailedStatusAndErrorMessage() {
        MockMultipartFile file = new MockMultipartFile("video", "squat.mp4", "video/mp4", "video bytes".getBytes());
        LocalDate date = LocalDate.of(2026, 11, 16);
        VideoAnalysis[] savedHolder = new VideoAnalysis[1];
        List<String> savedStatuses = new ArrayList<>();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findByCode("SQUAT")).thenReturn(Optional.empty());
        when(videoAnalysisQuotaService.reserveVideoAnalysisQuota(1L)).thenReturn(
                QuotaEntitlementResponseDto.builder().userId(1L).quotaDate(date).build()
        );
        when(minioStorageService.store(any())).thenReturn("orig_100.mp4");
        when(videoAnalysisRepository.save(any(VideoAnalysis.class))).thenAnswer(invocation -> {
            VideoAnalysis entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(100L);
            }
            savedHolder[0] = entity;
            savedStatuses.add(entity.getStatus());
            return entity;
        });
        when(videoAnalysisRepository.findById(100L)).thenAnswer(invocation -> Optional.ofNullable(savedHolder[0]));
        when(aiServiceClient.scoreVideo(any(), anyString(), anyString(), anyInt(), anyString(), anyBoolean()))
                .thenReturn(AiVideoScoringResponseDto.builder()
                        .status("FAILED")
                        .errorMessage("Unable to detect pose landmarks")
                        .build());

        service.initiateVideoAnalysis(1L, null, "SQUAT", null, false, file);

        assertNotNull(savedHolder[0]);
        assertEquals("FAILED", savedHolder[0].getStatus());
        assertEquals("Unable to detect pose landmarks", savedHolder[0].getErrorMessage());
        assertTrue(savedStatuses.contains("FAILED"));
    }

    @Nested
    class QuotaServiceUnitTests {

        @Mock
        private UserRepository quotaUserRepository;

        @Mock
        private VideoAnalysisQuotaUsageRepository quotaUsageRepository;

        @Test
        void timezoneRolloverUsesAsiaHoChiMinhMidnight() {
            // 2026-11-16T16:59:59Z == 2026-11-16T23:59:59+07:00
            Clock beforeMidnight = Clock.fixed(Instant.parse("2026-11-16T16:59:59Z"), ZoneId.of("UTC"));
            VideoAnalysisQuotaService beforeSvc = new VideoAnalysisQuotaService(
                    quotaUserRepository, quotaUsageRepository, beforeMidnight);
            LocalDate dateBefore = beforeSvc.currentQuotaDate();
            assertEquals(LocalDate.of(2026, 11, 16), dateBefore);
            assertEquals(
                    "2026-11-17T00:00:00+07:00",
                    beforeSvc.nextResetAt(dateBefore).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            );

            // 1 second later: 2026-11-16T17:00:00Z == 2026-11-17T00:00:00+07:00
            Clock atMidnight = Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneId.of("UTC"));
            VideoAnalysisQuotaService atMidnightSvc = new VideoAnalysisQuotaService(
                    quotaUserRepository, quotaUsageRepository, atMidnight);
            assertEquals(LocalDate.of(2026, 11, 17), atMidnightSvc.currentQuotaDate());
        }

        @Test
        void reserveQuota_ThrowsQuotaExceededWhenExhaustedAndAllowsAfterProUpgrade() {
            Clock fixedClock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneId.of("UTC"));
            VideoAnalysisQuotaService quotaSvc = new VideoAnalysisQuotaService(
                    quotaUserRepository, quotaUsageRepository, fixedClock);
            LocalDate expectedDate = LocalDate.of(2026, 11, 16);

            User freeUser = User.builder().id(5L).membershipTier(MembershipTier.FREE).build();
            when(quotaUserRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(freeUser));
            when(quotaUsageRepository.incrementUsedCountIfUnderLimit(5L, expectedDate, 1)).thenReturn(0);
            when(quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(5L, expectedDate)).thenReturn(Optional.of(1));

            QuotaExceededException ex = assertThrows(QuotaExceededException.class, () ->
                    quotaSvc.reserveVideoAnalysisQuota(5L));
            assertEquals(1, ex.getQuota().getDailyLimit());
            assertEquals(1, ex.getQuota().getUsedToday());
            assertEquals(0, ex.getQuota().getRemainingToday());

            // Upgrade user to PRO on the same day -> limit becomes 10, remaining becomes 8 after next reserve
            freeUser.setMembershipTier(MembershipTier.PRO);
            when(quotaUsageRepository.incrementUsedCountIfUnderLimit(5L, expectedDate, 10)).thenReturn(1);
            when(quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(5L, expectedDate)).thenReturn(Optional.of(2));

            QuotaEntitlementResponseDto proDto = quotaSvc.reserveVideoAnalysisQuota(5L);
            assertEquals(MembershipTier.PRO, proDto.getMembershipTier());
            assertEquals(10, proDto.getDailyLimit());
            assertEquals(2, proDto.getUsedToday());
            assertEquals(8, proDto.getRemainingToday());
            assertTrue(proDto.getLiveAnalysisUnlocked());
        }

        @Test
        void requireProForLiveAnalysis_BlocksFreeAndAllowsPro() {
            Clock fixedClock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneId.of("UTC"));
            VideoAnalysisQuotaService quotaSvc = new VideoAnalysisQuotaService(
                    quotaUserRepository, quotaUsageRepository, fixedClock);

            User freeUser = User.builder().id(5L).membershipTier(MembershipTier.FREE).build();
            when(quotaUserRepository.findById(5L)).thenReturn(Optional.of(freeUser));
            ProMembershipRequiredException ex = assertThrows(ProMembershipRequiredException.class, () ->
                    quotaSvc.requireProForLiveAnalysis(5L));
            assertEquals(MembershipTier.FREE, ex.getMembershipTier());
            assertFalse(ex.isLiveAnalysisUnlocked());

            User proUser = User.builder().id(6L).membershipTier(MembershipTier.PRO).build();
            when(quotaUserRepository.findById(6L)).thenReturn(Optional.of(proUser));
            assertDoesNotThrow(() -> quotaSvc.requireProForLiveAnalysis(6L));
        }
    }
}
