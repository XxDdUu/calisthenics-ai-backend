package com.calisthenics.backend.analysis;

import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredPerformanceSummary;
import com.calisthenics.backend.analysis.dto.CompleteAnalysisSessionRequest;
import com.calisthenics.backend.analysis.dto.CreateAnalysisSessionRequest;
import com.calisthenics.backend.analysis.dto.DetectedFormErrorDto;
import com.calisthenics.backend.analysis.dto.ExerciseFormMetricDto;
import com.calisthenics.backend.analysis.dto.RepHistoryDto;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalysisSessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private GeminiApiClient geminiApiClient;

    private UserPrincipal athletePrincipal;

    @BeforeEach
    void setUp() {
        reset(geminiApiClient);
        User athlete = userRepository.save(User.builder()
                .email("summary_it_" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Summary Test Athlete")
                .role(Role.ROLE_USER)
                .membershipTier(MembershipTier.PRO)
                .experienceLevel("INTERMEDIATE")
                .build());
        athletePrincipal = UserPrincipal.create(athlete);
    }

    @Test
    void pushUpEndToEnd_createRecordCompleteGenerateCachedAiAndReopenHistory() throws Exception {
        String sessionId = "it-push-" + UUID.randomUUID();
        CreateAnalysisSessionRequest createReq = CreateAnalysisSessionRequest.builder()
                .sessionId(sessionId)
                .exerciseType("PUSH_UP")
                .cameraView("SIDE")
                .build();

        mockMvc.perform(post("/api/v1/analysis/sessions")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.exerciseType").value("PUSH_UP"))
                .andExpect(jsonPath("$.analysisStatus").value("IN_PROGRESS"));

        RepHistoryDto rep1 = RepHistoryDto.builder()
                .repNumber(1)
                .durationMs(2400L)
                .timestampOffsetMs(2600L)
                .minimumJointAngle(new BigDecimal("92.0"))
                .minimumElbowAngle(new BigDecimal("92.0"))
                .formScore(new BigDecimal("82.0"))
                .depthRating("GOOD")
                .completionStatus("SUCCESS")
                .detectedErrors(List.of("Hip Sagging"))
                .build();

        RepHistoryDto rep2 = RepHistoryDto.builder()
                .repNumber(2)
                .durationMs(2300L)
                .timestampOffsetMs(5200L)
                .minimumJointAngle(new BigDecimal("89.0"))
                .minimumElbowAngle(new BigDecimal("89.0"))
                .formScore(new BigDecimal("91.0"))
                .depthRating("GOOD")
                .completionStatus("SUCCESS")
                .build();

        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/reps", sessionId)
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rep1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReps").value(1));

        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/reps", sessionId)
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rep2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReps").value(2));

        CompleteAnalysisSessionRequest completeReq = CompleteAnalysisSessionRequest.builder()
                .durationSeconds(new BigDecimal("18.5"))
                .totalReps(2)
                .correctReps(2)
                .incorrectReps(0)
                .overallScore(new BigDecimal("86.5"))
                .averageFormScore(new BigDecimal("86.5"))
                .analysisConfidence(new BigDecimal("0.93"))
                .analysisStatus("COMPLETED")
                .exerciseMetrics(List.of(
                        ExerciseFormMetricDto.builder()
                                .name("body_alignment").label("Body Alignment")
                                .value(173.0).unit("°").score(87.0).confidence(0.94).status("VALID")
                                .build()
                ))
                .detectedErrors(List.of(
                        DetectedFormErrorDto.builder()
                                .code("HIP_SAG").errorName("Hip Sagging")
                                .occurrences(1).severity("MEDIUM")
                                .timestampsMs(List.of(2600L)).repNumbers(List.of(1))
                                .suggestedCorrection("Brace core and glutes.")
                                .build()
                ))
                .reps(List.of(rep1, rep2))
                .build();

        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/complete", sessionId)
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.overallScore").value(86.5))
                .andExpect(jsonPath("$.exerciseMetrics[0].name").value("body_alignment"))
                .andExpect(jsonPath("$.detectedErrors[0].code").value("HIP_SAG"))
                .andExpect(jsonPath("$.reps", hasSize(2)))
                .andExpect(jsonPath("$.reps[0].timestampOffsetMs").value(2600));

        GeminiStructuredPerformanceSummary mockAi = GeminiStructuredPerformanceSummary.builder()
                .overallAssessment("Solid push-up session with clean lockouts.")
                .strengths(List.of("Consistent depth across both reps"))
                .weaknesses(List.of("Minor hip sag on rep 1"))
                .recommendations(List.of("Brace core early", "Squeeze glutes", "Maintain steady 2s tempo"))
                .nextSessionFocus("Target 3x6 strict push-ups")
                .build();

        when(geminiApiClient.generatePerformanceSummary(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiApiClient.GeminiSummaryResult(
                        mockAi, "{}", 120, 90, 320L, "gemini-flash-lite-latest"
                )));

        // First AI summary call hits Gemini and persists COMPLETED status
        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/ai-summary", sessionId)
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overallAssessment").value("Solid push-up session with clean lockouts."))
                .andExpect(jsonPath("$.recommendations", hasSize(3)));

        // Second AI summary call returns persisted DB summary without duplicate Gemini API request
        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/ai-summary", sessionId)
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(geminiApiClient, times(1)).generatePerformanceSummary(anyString(), anyString());

        // Reopen historical session via history detail and list endpoints
        mockMvc.perform(get("/api/v1/analysis/history/{sessionId}", sessionId)
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSummaryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.aiRecommendations.overallAssessment").value("Solid push-up session with clean lockouts."));

        mockMvc.perform(get("/api/v1/analysis/sessions/history")
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(sessionId)));
    }

    @Test
    void zeroRepsAndHoldExercises_preserveNullScoresUnavailableMetricsAndGeminiFallback() throws Exception {
        String zeroSessionId = "it-zero-squat-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/analysis/sessions")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAnalysisSessionRequest.builder()
                                .sessionId(zeroSessionId)
                                .exerciseType("SQUAT")
                                .cameraView("SIDE")
                                .build())))
                .andExpect(status().isCreated());

        CompleteAnalysisSessionRequest zeroComplete = CompleteAnalysisSessionRequest.builder()
                .durationSeconds(new BigDecimal("10.0"))
                .totalReps(0)
                .correctReps(0)
                .incorrectReps(0)
                .analysisStatus("COMPLETED")
                .build();

        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/complete", zeroSessionId)
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroComplete)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReps").value(0))
                .andExpect(jsonPath("$.overallScore").value(nullValue()))
                .andExpect(jsonPath("$.averageFormScore").value(nullValue()))
                .andExpect(jsonPath("$.exerciseMetrics[*].status", hasItem("UNAVAILABLE")));

        when(geminiApiClient.generatePerformanceSummary(anyString(), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/ai-summary", zeroSessionId)
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));

        // Verify Planche & Front Lever hold sessions preserve exercise-specific metrics
        String flSessionId = "it-fl-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/analysis/sessions")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateAnalysisSessionRequest.builder()
                                .sessionId(flSessionId)
                                .exerciseType("FRONT_LEVER")
                                .cameraView("SIDE")
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/analysis/sessions/{sessionId}/complete", flSessionId)
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CompleteAnalysisSessionRequest.builder()
                                .durationSeconds(new BigDecimal("14.0"))
                                .totalReps(0)
                                .peakHoldDurationSeconds(new BigDecimal("11.2"))
                                .overallScore(new BigDecimal("88.0"))
                                .analysisConfidence(new BigDecimal("0.91"))
                                .analysisStatus("COMPLETED")
                                .exerciseMetrics(List.of(
                                        ExerciseFormMetricDto.builder()
                                                .name("body_horizontal_alignment").label("Body Horizontal Alignment")
                                                .value(174.0).unit("°").score(90.0).confidence(0.92).status("VALID")
                                                .build()
                                ))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakHoldDurationSeconds").value(11.2))
                .andExpect(jsonPath("$.exerciseMetrics[0].name").value("body_horizontal_alignment"))
                .andExpect(jsonPath("$.aiSummaryStatus", notNullValue()));
    }
}
