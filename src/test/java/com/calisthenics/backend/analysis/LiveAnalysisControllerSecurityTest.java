package com.calisthenics.backend.analysis;

import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class LiveAnalysisControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private LiveAnalysisService liveAnalysisService;

    private User testUser;
    private String validAccessToken;

    @BeforeEach
    void setUp() {
        String testEmail = "live_user_" + UUID.randomUUID() + "@example.com";
        testUser = User.builder()
                .email(testEmail)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Live Athlete")
                .role(Role.ROLE_USER)
                .membershipTier(MembershipTier.PRO)
                .experienceLevel("INTERMEDIATE")
                .build();
        testUser = userRepository.save(testUser);
        UserPrincipal userPrincipal = UserPrincipal.create(testUser);
        validAccessToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(userPrincipal);
    }

    @Test
    @DisplayName("1. GET /api/v1/analysis/live/exercises is public and returns 200 without token")
    void getExercises_PublicEndpoint_SucceedsWithoutAuth() throws Exception {
        ObjectNode catalog = objectMapper.createObjectNode();
        catalog.put("status", "ok");
        when(liveAnalysisService.getSupportedExercises()).thenReturn(catalog);

        mockMvc.perform(get("/api/v1/analysis/live/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("2. POST /api/v1/analysis/live/frame without Authorization header returns 401 Unauthorized")
    void analyzeFrame_Unauthenticated_Returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(file)
                        .param("exercise", "PLANCHE")
                        .param("session_id", "session-123")
                        .param("frame_sequence", "1")
                        .param("timestamp_ms", "1000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("3. POST /api/v1/analysis/live/frame with invalid token returns 401 Unauthorized")
    void analyzeFrame_InvalidToken_Returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(file)
                        .param("exercise", "PLANCHE")
                        .param("session_id", "session-123")
                        .param("frame_sequence", "1")
                        .param("timestamp_ms", "1000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. POST /api/v1/analysis/live/frame with valid JWT token returns 200 OK")
    void analyzeFrame_ValidToken_Returns200Ok() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4}
        );

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "VALID");
        response.put("exercise", "PLANCHE");
        response.put("score", 95.0);
        response.put("pose_detected", true);

        when(liveAnalysisService.analyzeFrameSync(
                any(), eq("PLANCHE"), eq("session-123"), eq(1L), eq(1000L), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(file)
                        .param("exercise", "PLANCHE")
                        .param("session_id", "session-123")
                        .param("frame_sequence", "1")
                        .param("timestamp_ms", "1000")
                        .param("side_view", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.exercise").value("PLANCHE"))
                .andExpect(jsonPath("$.score").value(95.0))
                .andExpect(jsonPath("$.pose_detected").value(true));
    }

    @Test
    @DisplayName("5. POST /api/v1/analysis/live/session/{id}/reset requires authentication and succeeds when authorized")
    void resetSession_RequiresAuthAndSucceedsWithToken() throws Exception {
        ObjectNode resetResp = objectMapper.createObjectNode();
        resetResp.put("message", "reset");
        when(liveAnalysisService.resetSession("sess-456")).thenReturn(resetResp);

        // Without token -> 401
        mockMvc.perform(post("/api/v1/analysis/live/session/sess-456/reset"))
                .andExpect(status().isUnauthorized());

        // With valid token -> 200
        mockMvc.perform(post("/api/v1/analysis/live/session/sess-456/reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("reset"));
    }
}
