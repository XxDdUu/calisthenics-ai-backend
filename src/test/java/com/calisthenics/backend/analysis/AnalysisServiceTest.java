package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.AiImageAnalysisResponseDto;
import com.calisthenics.backend.analysis.dto.AiVideoAnalysisResponseDto;
import com.calisthenics.backend.analysis.dto.AnalysisResponse;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.storage.StorageService;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {

    private final AnalysisRepository analysisRepository = mock(AnalysisRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final AiServiceClient aiServiceClient = mock(AiServiceClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AnalysisService analysisService = new AnalysisService(
            analysisRepository,
            userRepository,
            exerciseRepository,
            storageService,
            aiServiceClient,
            objectMapper
    );

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/v1/analysis")
        ));
        when(storageService.store(any())).thenReturn("exercise.jpg");
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(exerciseRepository.findById(4L)).thenReturn(Optional.of(Exercise.builder()
                .id(4L)
                .name("Full Planche")
                .code("FULL_PLANCHE")
                .category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.ADVANCED)
                .build()));
        when(analysisRepository.save(any(Analysis.class))).thenAnswer(invocation -> {
            Analysis analysis = invocation.getArgument(0);
            analysis.setId(6L);
            analysis.setCreatedAt(ZonedDateTime.parse("2026-10-05T03:24:33.199234Z"));
            return analysis;
        });
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void unanalysedImageReturnsMetadataWithoutAnalysisMetrics() throws Exception {
        when(aiServiceClient.analyzeImage(any(), eq(true))).thenReturn(new AiImageAnalysisResponseDto());

        AnalysisResponse response = analysisService.analyzeAndSaveImage(2L, 4L, imageFile());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("IMAGE", json.get("mediaType").asText());
        assertEquals(6L, json.get("id").asLong());
        assertEquals(2L, json.get("userId").asLong());
        assertEquals(4L, json.get("exerciseId").asLong());
        assertEquals("Full Planche", json.get("exerciseName").asText());
        assertTrue(json.has("mediaUrl"));
        assertTrue(json.has("createdAt"));
        assertFalse(json.has("overallScore"));
        assertFalse(json.has("holdDurationSeconds"));
        assertFalse(json.has("formFeedback"));
        assertFalse(json.has("elbowAngle"));
        assertFalse(json.has("bodyLineAngle"));
        assertFalse(json.has("shoulderLeanAngle"));
        assertFalse(json.has("rawKeypointsJson"));

        Analysis persisted = savedAnalysis();
        assertNull(persisted.getHoldDurationSeconds());
    }

    @Test
    void analysedImageReturnsMetricsAndKeypointsActuallyProducedByImageWorkflow() throws Exception {
        AiImageAnalysisResponseDto aiResult = new AiImageAnalysisResponseDto();
        AiImageAnalysisResponseDto.PlancheAnalysisResultDto planche =
                new AiImageAnalysisResponseDto.PlancheAnalysisResultDto();
        planche.setFormScore(new BigDecimal("87.5"));
        planche.setJointAngles(jointAngles());
        planche.setFormErrors(List.of("Keep elbows locked"));
        planche.setPositiveFeedback(List.of("Good body line"));
        planche.setActionableCues(List.of("Lean shoulders forward"));
        aiResult.setPlancheAnalysis(planche);
        aiResult.setPoseKeypoints(poseKeypoints());
        when(aiServiceClient.analyzeImage(any(), eq(true))).thenReturn(aiResult);

        AnalysisResponse response = analysisService.analyzeAndSaveImage(2L, 4L, imageFile());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals(87.5, json.get("overallScore").asDouble());
        assertEquals(178, json.get("elbowAngle").asInt());
        assertEquals(174, json.get("bodyLineAngle").asInt());
        assertEquals(32, json.get("shoulderLeanAngle").asInt());
        assertEquals("Keep elbows locked; Good body line; Lean shoulders forward",
                json.get("formFeedback").asText());
        assertTrue(json.has("rawKeypointsJson"));
        assertFalse(json.has("holdDurationSeconds"));
    }

    @Test
    void partialImageAnalysisReturnsAvailableKeypointsAndOmitsMissingMetrics() throws Exception {
        AiImageAnalysisResponseDto aiResult = new AiImageAnalysisResponseDto();
        aiResult.setPoseKeypoints(poseKeypoints());
        when(aiServiceClient.analyzeImage(any(), eq(true))).thenReturn(aiResult);

        AnalysisResponse response = analysisService.analyzeAndSaveImage(2L, 4L, imageFile());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.has("rawKeypointsJson"));
        assertFalse(json.has("overallScore"));
        assertFalse(json.has("formFeedback"));
        assertFalse(json.has("elbowAngle"));
        assertFalse(json.has("bodyLineAngle"));
        assertFalse(json.has("shoulderLeanAngle"));
        assertFalse(json.has("holdDurationSeconds"));
    }

    @Test
    void legacyImageDurationPlaceholderIsNotReturned() throws Exception {
        Analysis legacyImage = Analysis.builder()
                .id(6L)
                .user(User.builder().id(2L).build())
                .mediaUrl("http://localhost/files/legacy.jpg")
                .mediaType("IMAGE")
                .holdDurationSeconds(BigDecimal.ZERO)
                .createdAt(ZonedDateTime.parse("2026-10-05T03:24:33.199234Z"))
                .build();
        when(analysisRepository.findById(6L)).thenReturn(Optional.of(legacyImage));

        AnalysisResponse response = analysisService.getAnalysisById(6L);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertTrue(json.has("exerciseId"));
        assertTrue(json.get("exerciseId").isNull());
        assertFalse(json.has("holdDurationSeconds"));
    }

    @Test
    void videoResponsePreservesVideoMetricsAndExistingNullMetricFields() throws Exception {
        AiVideoAnalysisResponseDto aiResult = new AiVideoAnalysisResponseDto();
        aiResult.setAverageFormScore(new BigDecimal("82.0"));
        aiResult.setPeakFormScore(new BigDecimal("91.0"));
        aiResult.setValidHoldDurationSec(new BigDecimal("3.7"));
        aiResult.setTotalHoldDurationSec(new BigDecimal("5.2"));
        aiResult.setSummaryFeedback(List.of("Stable hold"));
        aiResult.setKeyRecommendations(List.of("Keep elbows locked"));
        when(aiServiceClient.analyzeVideo(any(), eq(5))).thenReturn(aiResult);

        AnalysisResponse response = analysisService.analyzeAndSaveVideo(2L, 4L, imageFile());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertEquals("VIDEO", json.get("mediaType").asText());
        assertEquals(82.0, json.get("overallScore").asDouble());
        assertEquals(3.7, json.get("holdDurationSeconds").asDouble());
        assertEquals("Stable hold; Keep elbows locked", json.get("formFeedback").asText());
        assertTrue(json.has("elbowAngle"));
        assertTrue(json.get("elbowAngle").isNull());
        assertTrue(json.has("rawKeypointsJson"));
        assertTrue(json.get("rawKeypointsJson").isNull());
    }

    private Analysis savedAnalysis() {
        var captor = org.mockito.ArgumentCaptor.forClass(Analysis.class);
        verify(analysisRepository).save(captor.capture());
        return captor.getValue();
    }

    private static MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "exercise.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private static AiImageAnalysisResponseDto.PoseKeypointsDto poseKeypoints() {
        AiImageAnalysisResponseDto.PoseKeypointsDto poseKeypoints =
                new AiImageAnalysisResponseDto.PoseKeypointsDto();
        poseKeypoints.setKeypoints(Map.of("nose", Map.of("x", 12, "y", 18)));
        return poseKeypoints;
    }

    private static AiImageAnalysisResponseDto.JointAnglesDto jointAngles() {
        AiImageAnalysisResponseDto.JointAnglesDto jointAngles =
                new AiImageAnalysisResponseDto.JointAnglesDto();
        jointAngles.setPrimaryElbowDeg(new BigDecimal("178"));
        jointAngles.setPrimaryShoulderLeanDeg(new BigDecimal("32"));
        jointAngles.setHipAngleDeg(new BigDecimal("174"));
        jointAngles.setBodyHorizontalTiltDeg(new BigDecimal("3"));
        return jointAngles;
    }
}
