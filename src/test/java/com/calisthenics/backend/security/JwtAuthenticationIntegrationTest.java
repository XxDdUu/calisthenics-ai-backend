package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.LoginRequest;
import com.calisthenics.backend.security.dto.RefreshTokenRequest;
import com.calisthenics.backend.security.dto.RegisterRequest;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class JwtAuthenticationIntegrationTest {

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

    private User testUser;
    private UserPrincipal testUserPrincipal;
    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "jwtathlete_" + UUID.randomUUID() + "@example.com";
        testUser = User.builder()
                .email(testEmail)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("JWT Athlete")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build();
        testUser = userRepository.save(testUser);
        testUserPrincipal = UserPrincipal.create(testUser);
    }

    @Test
    void testPublicEndpointsAccessibleWithoutToken() throws Exception {
        // Public endpoint GET /api/v1/exercises
        mockMvc.perform(get("/api/v1/exercises"))
                .andExpect(status().isOk());

        // Public auth register endpoint
        String newEmail = "register_" + UUID.randomUUID() + "@example.com";
        RegisterRequest register = new RegisterRequest();
        register.setEmail(newEmail);
        register.setPassword("Password123!");
        register.setFullName("New Athlete");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void testLoginFlow_ReturnsAccessAndRefreshToken() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(testEmail);
        login.setPassword("Password123!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = responseNode.get("accessToken").asText();
        String refreshToken = responseNode.get("refreshToken").asText();

        assertTrue(jwtTokenProvider.isAccessToken(accessToken));
        assertTrue(jwtTokenProvider.isRefreshToken(refreshToken));
    }

    @Test
    void testProtectedEndpoint_WithValidToken_Succeeds() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(testUserPrincipal);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));
    }

    @Test
    void testProtectedEndpoint_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void testProtectedEndpoint_WithExpiredToken_Returns401AndSpecificMessage() throws Exception {
        // Generate an expired access token (duration -10000ms)
        String expiredToken = jwtTokenProvider.generateToken(testUserPrincipal, -10000, JwtTokenProvider.TOKEN_TYPE_ACCESS);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("error=\"invalid_token\"")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("The access token expired")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Token has expired"));
    }

    @Test
    void testProtectedEndpoint_WithMalformedToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed.invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("error=\"invalid_token\"")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void testProtectedEndpoint_WithRefreshTokenAsAccessToken_Returns401() throws Exception {
        // Refresh token cannot be used to access protected endpoints
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUserPrincipal);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void testRefreshToken_SuccessAndRotation() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUserPrincipal);

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String newAccessToken = responseNode.get("accessToken").asText();
        String newRefreshToken = responseNode.get("refreshToken").asText();

        // Check new tokens are valid
        assertTrue(jwtTokenProvider.validateAccessToken(newAccessToken) == JwtValidationResult.VALID);
        assertTrue(jwtTokenProvider.validateRefreshToken(newRefreshToken) == JwtValidationResult.VALID);

        // Verify newly issued access token can access protected endpoints
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));
    }

    @Test
    void testRefreshToken_ExpiredRefreshToken_Returns401() throws Exception {
        String expiredRefreshToken = jwtTokenProvider.generateToken(testUserPrincipal, -10000, JwtTokenProvider.TOKEN_TYPE_REFRESH);

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(expiredRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Refresh token has expired"));
    }

    @Test
    void testRefreshToken_AccessTokenSubmittedAsRefreshToken_Returns401() throws Exception {
        // Access token must not be accepted as a refresh token
        String accessToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(testUserPrincipal);

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(accessToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    void testLogout_Succeeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
}

