package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.FirebaseLoginRequest;
import com.calisthenics.backend.security.dto.FirebaseUserInfo;
import com.calisthenics.backend.security.dto.RefreshTokenRequest;
import com.calisthenics.backend.user.Gender;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class FirebaseAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Test
    void authenticateWithFirebase_NewUser_CreatesUserIssuesJwtAndAllowsProtectedApiAccess() throws Exception {
        FirebaseUserInfo firebaseUser = FirebaseUserInfo.builder()
                .uid("fb-uid-1001")
                .email("NewFirebaseAthlete@Example.com")
                .emailVerified(true)
                .name("Firebase Athlete")
                .picture("https://example.com/fb-1001.png")
                .googleId("google-sub-1001")
                .build();

        when(firebaseTokenVerifier.verifyToken("valid-new-firebase-token")).thenReturn(firebaseUser);

        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("valid-new-firebase-token");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("newfirebaseathlete@example.com"))
                .andExpect(jsonPath("$.fullName").value("Firebase Athlete"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.onboardingStatus").value("NOT_SHOWN"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();
        String refreshToken = json.get("refreshToken").asText();

        User saved = userRepository.findByFirebaseUid("fb-uid-1001").orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("newfirebaseathlete@example.com");
        assertThat(saved.getGoogleId()).isEqualTo("google-sub-1001");
        assertThat(saved.getPasswordHash()).isNull();

        // Backend JWT works for protected endpoints
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newfirebaseathlete@example.com"));

        // Backend refresh token rotates cleanly
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void authenticateWithFirebase_ExistingFirebaseUser_AuthenticatesWithoutDuplicates() throws Exception {
        User existing = User.builder()
                .email("existingfb@example.com")
                .firebaseUid("fb-uid-2002")
                .fullName("Existing Firebase Athlete")
                .role(Role.ROLE_ADMIN)
                .gender(Gender.MALE)
                .onboardingStatus(OnboardingStatus.DISMISSED)
                .build();
        existing = userRepository.save(existing);

        FirebaseUserInfo firebaseUser = FirebaseUserInfo.builder()
                .uid("fb-uid-2002")
                .email("existingfb@example.com")
                .emailVerified(true)
                .name("Different Display Name")
                .build();

        when(firebaseTokenVerifier.verifyToken("existing-fb-token")).thenReturn(firebaseUser);

        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("existing-fb-token");

        mockMvc.perform(post("/api/v1/auth/firebase/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(existing.getId()))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.onboardingStatus").value("DISMISSED"));

        assertThat(userRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void authenticateWithFirebase_ExistingLegacyGoogleUser_LinksFirebaseUidSafely() throws Exception {
        User legacyGoogleUser = User.builder()
                .email("legacygoogle@example.com")
                .googleId("google-sub-3003")
                .fullName("Legacy Google Athlete")
                .role(Role.ROLE_USER)
                .gender(Gender.FEMALE)
                .build();
        legacyGoogleUser = userRepository.save(legacyGoogleUser);

        FirebaseUserInfo firebaseUser = FirebaseUserInfo.builder()
                .uid("fb-uid-3003")
                .email("legacygoogle@example.com")
                .emailVerified(true)
                .name("Legacy Google Athlete")
                .googleId("google-sub-3003")
                .build();

        when(firebaseTokenVerifier.verifyToken("legacy-google-token")).thenReturn(firebaseUser);

        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("legacy-google-token");

        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(legacyGoogleUser.getId()))
                .andExpect(jsonPath("$.gender").value("FEMALE"));

        User updated = userRepository.findById(legacyGoogleUser.getId()).orElseThrow();
        assertThat(updated.getFirebaseUid()).isEqualTo("fb-uid-3003");
        assertThat(updated.getGoogleId()).isEqualTo("google-sub-3003");
    }

    @Test
    void authenticateWithFirebase_ExistingPasswordAccount_ReturnsConflict409() throws Exception {
        User passwordUser = User.builder()
                .email("pwduser@example.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .fullName("Password User")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(passwordUser);

        FirebaseUserInfo firebaseUser = FirebaseUserInfo.builder()
                .uid("fb-uid-4004")
                .email("pwduser@example.com")
                .emailVerified(true)
                .name("Password User")
                .build();

        when(firebaseTokenVerifier.verifyToken("pwd-conflict-token")).thenReturn(firebaseUser);

        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("pwd-conflict-token");

        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS_WITH_PASSWORD"));

        User unchanged = userRepository.findByEmailIgnoreCase("pwduser@example.com").orElseThrow();
        assertThat(unchanged.getFirebaseUid()).isNull();
    }

    @Test
    void authenticateWithFirebase_ExistingDifferentFirebaseUidForEmail_ReturnsConflict409() throws Exception {
        User existing = User.builder()
                .email("conflictuid@example.com")
                .firebaseUid("fb-uid-original")
                .fullName("Original User")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(existing);

        FirebaseUserInfo firebaseUser = FirebaseUserInfo.builder()
                .uid("fb-uid-different")
                .email("conflictuid@example.com")
                .emailVerified(true)
                .name("Different UID")
                .build();

        when(firebaseTokenVerifier.verifyToken("uid-conflict-token")).thenReturn(firebaseUser);

        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("uid-conflict-token");

        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIREBASE_ACCOUNT_CONFLICT"));
    }

    @Test
    void authenticateWithFirebase_ExpiredOrInvalidToken_ReturnsUnauthorized401() throws Exception {
        when(firebaseTokenVerifier.verifyToken("expired-fb-token"))
                .thenThrow(new FirebaseAuthenticationException(
                        "FIREBASE_TOKEN_EXPIRED",
                        "Firebase authentication session expired. Please sign in again.",
                        HttpStatus.UNAUTHORIZED
                ));

        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("expired-fb-token");

        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("FIREBASE_TOKEN_EXPIRED"));
    }

    @Test
    void authenticateWithFirebase_MissingIdToken_ReturnsBadRequest400() throws Exception {
        FirebaseLoginRequest request = new FirebaseLoginRequest();
        request.setIdToken("");

        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpoint_DoesNotAcceptNonBackendBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-new-firebase-token"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByFirebaseUid("fb-uid-1001")).isEmpty();
    }
}
