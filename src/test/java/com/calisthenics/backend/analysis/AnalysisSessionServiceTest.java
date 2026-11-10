package com.calisthenics.backend.analysis;

import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredPerformanceSummary;
import com.calisthenics.backend.analysis.dto.*;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisSessionServiceTest {

    @Mock
    private AnalysisSessionRepository sessionRepository;

    @Mock
    private RepHistoryRepository repHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private GeminiApiClient geminiApiClient;

    @Spy
    private WorkoutSummaryMetricsProcessor metricsProcessor = new WorkoutSummaryMetricsProcessor();

    private ObjectMapper objectMapper;

    private AnalysisSessionService sessionService;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sessionService = new AnalysisSessionService(
                sessionRepository,
                repHistoryRepository,
                userRepository,
                exerciseRepository,
                geminiApiClient,
                metricsProcessor,
                objectMapper
        );

        testUser = User.builder()
                .id(1L)
                .email("user1@example.com")
                .fullName("User One")
                .role(Role.ROLE_USER)
                .build();

        otherUser = User.builder()
                .id(2L)
                .email("user2@example.com")
                .fullName("User Two")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    void testCreateSession_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(sessionRepository.findById("test-session-1")).thenReturn(Optional.empty());
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAnalysisSessionRequest request = CreateAnalysisSessionRequest.builder()
                .sessionId("test-session-1")
                .exerciseType("SQUAT")
                .cameraView("SIDE")
                .build();

        AnalysisSessionResponseDto response = sessionService.createSession(1L, request);

        assertNotNull(response);
        assertEquals("test-session-1", response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals("SQUAT", response.getExerciseType());
        assertEquals("IN_PROGRESS", response.getAnalysisStatus());
        verify(sessionRepository, times(1)).save(any(AnalysisSession.class));
    }

    @Test
    void testCompleteSession_SuccessWithReps() {
        AnalysisSession existing = AnalysisSession.builder()
                .id("test-session-1")
                .user(testUser)
                .exerciseType("SQUAT")
                .startedAt(Instant.now().minusSeconds(30))
                .analysisStatus("IN_PROGRESS")
                .reps(new ArrayList<>())
                .build();

        when(sessionRepository.findById("test-session-1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteAnalysisSessionRequest request = CompleteAnalysisSessionRequest.builder()
                .completedAt(Instant.now())
                .durationSeconds(BigDecimal.valueOf(30.5))
                .totalReps(3)
                .averageFormScore(BigDecimal.valueOf(92.0))
                .bestFormScore(BigDecimal.valueOf(95.0))
                .averageKneeAngle(BigDecimal.valueOf(94.5))
                .deepestKneeAngle(BigDecimal.valueOf(88.0))
                .reps(List.of(
                        RepHistoryDto.builder()
                                .repNumber(1)
                                .durationMs(1500L)
                                .minimumKneeAngle(BigDecimal.valueOf(92.0))
                                .formScore(BigDecimal.valueOf(90.0))
                                .depthRating("GOOD")
                                .feedback("Great depth!")
                                .build(),
                        RepHistoryDto.builder()
                                .repNumber(2)
                                .durationMs(1600L)
                                .minimumKneeAngle(BigDecimal.valueOf(88.0))
                                .formScore(BigDecimal.valueOf(95.0))
                                .depthRating("GOOD")
                                .feedback("Rep completed!")
                                .build()
                ))
                .build();

        AnalysisSessionResponseDto response = sessionService.completeSession(1L, "test-session-1", request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getAnalysisStatus());
        assertEquals(3, response.getTotalReps());
        assertEquals(2, response.getReps().size());
        assertEquals(BigDecimal.valueOf(88.0), response.getDeepestKneeAngle());
        verify(sessionRepository).save(existing);
    }

    @Test
    void testUserIsolation_ForbiddenWhenAccessingOtherUsersSession() {
        AnalysisSession otherSession = AnalysisSession.builder()
                .id("other-session")
                .user(otherUser)
                .exerciseType("SQUAT")
                .startedAt(Instant.now())
                .analysisStatus("IN_PROGRESS")
                .build();

        when(sessionRepository.findById("other-session")).thenReturn(Optional.of(otherSession));

        assertThrows(ResponseStatusException.class, () ->
                sessionService.getSessionDetails(1L, "other-session")
        );

        CompleteAnalysisSessionRequest completeRequest = CompleteAnalysisSessionRequest.builder()
                .totalReps(5)
                .build();

        assertThrows(ResponseStatusException.class, () ->
                sessionService.completeSession(1L, "other-session", completeRequest)
        );
    }

    @Test
    void testGetUserHistorySummary() {
        when(sessionRepository.countSessionsByUserId(1L)).thenReturn(5L);
        when(sessionRepository.sumRepsByUserId(1L)).thenReturn(25L);
        when(sessionRepository.averageScoreByUserId(1L)).thenReturn(BigDecimal.valueOf(91.23));
        when(sessionRepository.maxScoreByUserId(1L)).thenReturn(BigDecimal.valueOf(98.5));
        when(sessionRepository.sumDurationByUserId(1L)).thenReturn(BigDecimal.valueOf(620.0));

        AnalysisHistorySummaryDto summary = sessionService.getUserHistorySummary(1L);

        assertEquals(5L, summary.getTotalSessions());
        assertEquals(25L, summary.getTotalReps());
        assertEquals(BigDecimal.valueOf(91.2), summary.getAverageFormScore());
        assertEquals(BigDecimal.valueOf(98.5), summary.getBestFormScore());
        assertEquals(BigDecimal.valueOf(620.0), summary.getTrainingDurationSeconds());
    }

    @Test
    void testRecordRep_SuccessAndDeduplication() {
        AnalysisSession session = AnalysisSession.builder()
                .id("session-reps")
                .user(testUser)
                .exerciseType("PUSH_UP")
                .startedAt(Instant.now().minusSeconds(10))
                .analysisStatus("IN_PROGRESS")
                .reps(new ArrayList<>())
                .build();

        when(sessionRepository.findById("session-reps")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RepHistoryDto rep1 = RepHistoryDto.builder()
                .repNumber(1)
                .durationMs(1400L)
                .minimumJointAngle(BigDecimal.valueOf(85.0))
                .formScore(BigDecimal.valueOf(95.0))
                .depthRating("GOOD")
                .feedback("Good push-up depth")
                .build();

        AnalysisSessionResponseDto dto1 = sessionService.recordRep(1L, "session-reps", rep1);
        assertEquals(1, dto1.getTotalReps());
        assertEquals(1, dto1.getReps().size());
        assertEquals(BigDecimal.valueOf(95.0), dto1.getAverageFormScore());
        assertEquals(BigDecimal.valueOf(85.0), dto1.getReps().get(0).getMinimumJointAngle());

        // Duplicate submission of same repNumber should be ignored gracefully
        AnalysisSessionResponseDto dtoDup = sessionService.recordRep(1L, "session-reps", rep1);
        assertEquals(1, dtoDup.getTotalReps());
        assertEquals(1, dtoDup.getReps().size());
    }

    @Test
    void testGetUserHistory_WithFilter() {
        AnalysisSession s1 = AnalysisSession.builder().id("s1").user(testUser).exerciseType("SQUAT").reps(new ArrayList<>()).build();
        AnalysisSession s2 = AnalysisSession.builder().id("s2").user(testUser).exerciseType("PUSH_UP").reps(new ArrayList<>()).build();

        when(sessionRepository.findByUserIdAndExerciseTypeOrderByCreatedAtDesc(1L, "SQUAT")).thenReturn(List.of(s1));
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(s1, s2));

        List<AnalysisSessionResponseDto> squats = sessionService.getUserHistory(1L, "SQUAT");
        assertEquals(1, squats.size());
        assertEquals("SQUAT", squats.get(0).getExerciseType());

        List<AnalysisSessionResponseDto> all = sessionService.getUserHistory(1L, null);
        assertEquals(2, all.size());
    }

    @Test
    void testCompleteSession_PushUpMetricsAndErrorDeduplication() {
        AnalysisSession existing = AnalysisSession.builder()
                .id("pushup-1")
                .user(testUser)
                .exerciseType("PUSH_UP")
                .startedAt(Instant.now().minusSeconds(40))
                .analysisStatus("IN_PROGRESS")
                .reps(new ArrayList<>())
                .build();

        when(exerciseRepository.findByCode("PUSH_UP")).thenReturn(Optional.of(Exercise.builder().id(11L).code("PUSH_UP").build()));
        when(sessionRepository.findById("pushup-1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteAnalysisSessionRequest request = CompleteAnalysisSessionRequest.builder()
                .durationSeconds(BigDecimal.valueOf(40.0))
                .totalReps(3)
                .overallScore(BigDecimal.valueOf(86.5))
                .averageFormScore(BigDecimal.valueOf(86.5))
                .analysisConfidence(BigDecimal.valueOf(0.92))
                .exerciseMetrics(List.of(
                        ExerciseFormMetricDto.builder().name("body_alignment").value(175.0).score(90.0).confidence(0.95).status("VALID").build(),
                        ExerciseFormMetricDto.builder().name("elbow_movement").value(5.0).score(90.0).confidence(0.90).status("VALID").build(),
                        ExerciseFormMetricDto.builder().name("range_of_motion").value(92.0).score(92.0).confidence(0.92).status("VALID").build()
                ))
                .detectedErrors(List.of(
                        DetectedFormErrorDto.builder().code("HIP_SAG").errorName("Hip Sagging").occurrences(2).severity("HIGH").repNumbers(List.of(1, 2)).timestampsMs(List.of(4000L, 12000L)).suggestedCorrection("Squeeze glutes and brace core").build(),
                        DetectedFormErrorDto.builder().code("HIP_SAG").errorName("Hip Sagging").occurrences(1).severity("MEDIUM").repNumbers(List.of(2, 3)).timestampsMs(List.of(12000L, 21000L)).suggestedCorrection("Squeeze glutes").build()
                ))
                .reps(List.of(
                        RepHistoryDto.builder().repNumber(1).durationMs(1500L).formScore(BigDecimal.valueOf(88.0)).depthRating("GOOD").timestampOffsetMs(4000L).detectedErrors(List.of("HIP_SAG")).build(),
                        RepHistoryDto.builder().repNumber(2).durationMs(1600L).formScore(BigDecimal.valueOf(85.0)).depthRating("GOOD").timestampOffsetMs(12000L).detectedErrors(List.of("HIP_SAG")).build(),
                        RepHistoryDto.builder().repNumber(3).durationMs(1550L).formScore(BigDecimal.valueOf(86.5)).depthRating("GOOD").timestampOffsetMs(21000L).build()
                ))
                .build();

        AnalysisSessionResponseDto response = sessionService.completeSession(1L, "pushup-1", request);

        assertEquals(11L, response.getExerciseId());
        assertEquals(5, response.getExerciseMetrics().size());
        // Should contain computed Tempo Consistency & Rep Consistency from the 3 reps
        assertTrue(response.getExerciseMetrics().stream().anyMatch(m -> "tempo_consistency".equals(m.getName()) && "VALID".equals(m.getStatus())));
        assertTrue(response.getExerciseMetrics().stream().anyMatch(m -> "rep_consistency".equals(m.getName()) && "VALID".equals(m.getStatus())));
        // Push-up metrics must NOT contain squat_depth
        assertFalse(response.getExerciseMetrics().stream().anyMatch(m -> "squat_depth".equals(m.getName())));

        // Error deduplication preserves unique reps & max severity
        assertEquals(1, response.getDetectedErrors().size());
        DetectedFormErrorDto err = response.getDetectedErrors().get(0);
        assertEquals("HIGH", err.getSeverity());
        assertEquals(List.of(1, 2, 3), err.getRepNumbers());
    }

    @Test
    void testCompleteSession_PlancheUnavailableScapularMetricAndZeroReps() {
        AnalysisSession existing = AnalysisSession.builder()
                .id("planche-1")
                .user(testUser)
                .exerciseType("PLANCHE")
                .startedAt(Instant.now().minusSeconds(15))
                .analysisStatus("IN_PROGRESS")
                .reps(new ArrayList<>())
                .build();

        when(sessionRepository.findById("planche-1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteAnalysisSessionRequest request = CompleteAnalysisSessionRequest.builder()
                .durationSeconds(BigDecimal.valueOf(15.0))
                .totalReps(0)
                .peakHoldDurationSeconds(BigDecimal.valueOf(8.5))
                .overallScore(BigDecimal.valueOf(87.0))
                .averageFormScore(BigDecimal.valueOf(87.0))
                .analysisConfidence(BigDecimal.valueOf(0.89))
                .exerciseMetrics(List.of(
                        ExerciseFormMetricDto.builder().name("shoulder_lean").value(45.0).score(96.0).confidence(0.92).status("VALID").build(),
                        ExerciseFormMetricDto.builder().name("hip_elevation").value(172.0).score(92.0).confidence(0.90).status("VALID").build(),
                        ExerciseFormMetricDto.builder().name("arm_lockout").value(174.0).score(100.0).confidence(0.95).status("VALID").build(),
                        ExerciseFormMetricDto.builder().name("scapular_protraction").value(0.0).score(0.0).confidence(0.0).status("INSUFFICIENT_DATA").build(),
                        ExerciseFormMetricDto.builder().name("body_line").value(4.0).score(95.0).confidence(0.90).status("VALID").build()
                ))
                .build();

        AnalysisSessionResponseDto response = sessionService.completeSession(1L, "planche-1", request);

        assertEquals(0, response.getTotalReps());
        assertEquals(BigDecimal.valueOf(8.5), response.getPeakHoldDurationSeconds());
        assertEquals(5, response.getExerciseMetrics().size());
        ExerciseFormMetricDto scapular = response.getExerciseMetrics().stream()
                .filter(m -> "scapular_positioning".equals(m.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("UNAVAILABLE", scapular.getStatus());
        assertNull(scapular.getScore());
    }

    @Test
    void testCompleteSession_FrontLeverMetricsIsolatedFromPlanche() {
        AnalysisSession existing = AnalysisSession.builder()
                .id("fl-1")
                .user(testUser)
                .exerciseType("FRONT_LEVER")
                .startedAt(Instant.now().minusSeconds(12))
                .analysisStatus("IN_PROGRESS")
                .reps(new ArrayList<>())
                .build();

        when(sessionRepository.findById("fl-1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompleteAnalysisSessionRequest request = CompleteAnalysisSessionRequest.builder()
                .durationSeconds(BigDecimal.valueOf(12.0))
                .totalReps(0)
                .peakHoldDurationSeconds(BigDecimal.valueOf(6.2))
                .exerciseMetrics(List.of(
                        ExerciseFormMetricDto.builder().name("horizontal_tilt").value(5.0).score(90.0).confidence(0.9).status("VALID").build(),
                        ExerciseFormMetricDto.builder().name("shoulder_lean").value(45.0).score(99.0).confidence(0.9).status("VALID").build() // wrong exercise metric should be ignored!
                ))
                .build();

        AnalysisSessionResponseDto response = sessionService.completeSession(1L, "fl-1", request);

        assertEquals(5, response.getExerciseMetrics().size());
        assertFalse(response.getExerciseMetrics().stream().anyMatch(m -> "shoulder_lean".equals(m.getName())));
        assertTrue(response.getExerciseMetrics().stream().anyMatch(m -> "body_horizontal_alignment".equals(m.getName()) && "VALID".equals(m.getStatus())));
        assertTrue(response.getExerciseMetrics().stream().anyMatch(m -> "hold_consistency".equals(m.getName()) && "UNAVAILABLE".equals(m.getStatus())));
    }

    @Test
    void testGenerateAiSummary_CachesResultAndAvoidsDuplicateCalls() {
        AnalysisSession session = AnalysisSession.builder()
                .id("ai-session-1")
                .user(testUser)
                .exerciseType("SQUAT")
                .totalReps(8)
                .correctReps(7)
                .incorrectReps(1)
                .overallScore(BigDecimal.valueOf(88.5))
                .averageFormScore(BigDecimal.valueOf(88.5))
                .durationSeconds(BigDecimal.valueOf(45.0))
                .analysisStatus("COMPLETED")
                .aiSummaryStatus("PENDING")
                .reps(new ArrayList<>())
                .build();

        when(sessionRepository.findById("ai-session-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GeminiStructuredPerformanceSummary mockSummary = GeminiStructuredPerformanceSummary.builder()
                .overallAssessment("Solid squat session with 7/8 clean reps and an 88.5 average score.")
                .strengths(List.of("Consistent squat depth", "High completion rate"))
                .weaknesses(List.of("1 rep had slight forward lean"))
                .recommendations(List.of("Maintain chest upright", "Control eccentric tempo", "Warm up ankle dorsiflexion"))
                .nextSessionFocus("Add 2 reps per set while maintaining depth")
                .build();

        when(geminiApiClient.generatePerformanceSummary(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiApiClient.GeminiSummaryResult(
                        mockSummary, "{}", 150, 120, 450L, "gemini-flash-lite-latest"
                )));

        // First call invokes Gemini and persists to session
        AiPerformanceSummaryDto firstCall = sessionService.generateOrGetAiSummary(1L, "ai-session-1");
        assertEquals("COMPLETED", firstCall.getStatus());
        assertEquals(3, firstCall.getRecommendations().size());
        assertEquals("COMPLETED", session.getAiSummaryStatus());
        assertNotNull(session.getAiRecommendationsJson());
        verify(geminiApiClient, times(1)).generatePerformanceSummary(anyString(), anyString());

        // Second call returns cached DB summary without calling Gemini again
        AiPerformanceSummaryDto secondCall = sessionService.generateOrGetAiSummary(1L, "ai-session-1");
        assertEquals("COMPLETED", secondCall.getStatus());
        assertEquals(firstCall.getOverallAssessment(), secondCall.getOverallAssessment());
        verify(geminiApiClient, times(1)).generatePerformanceSummary(anyString(), anyString());
    }

    @Test
    void testGenerateAiSummary_GeminiUnavailableReturnsGracefulFallback() {
        AnalysisSession session = AnalysisSession.builder()
                .id("ai-session-offline")
                .user(testUser)
                .exerciseType("PUSH_UP")
                .totalReps(5)
                .durationSeconds(BigDecimal.valueOf(25.0))
                .analysisStatus("COMPLETED")
                .aiSummaryStatus("PENDING")
                .reps(new ArrayList<>())
                .build();

        when(sessionRepository.findById("ai-session-offline")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(AnalysisSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(geminiApiClient.generatePerformanceSummary(anyString(), anyString())).thenReturn(Optional.empty());

        AiPerformanceSummaryDto result = sessionService.generateOrGetAiSummary(1L, "ai-session-offline");

        assertEquals("UNAVAILABLE", result.getStatus());
        assertNull(result.getOverallAssessment());
        assertEquals("UNAVAILABLE", session.getAiSummaryStatus());
    }
}
