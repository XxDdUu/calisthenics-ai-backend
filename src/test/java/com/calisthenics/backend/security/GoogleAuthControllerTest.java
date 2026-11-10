package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.GoogleAuthRequest;
import com.calisthenics.backend.security.dto.GoogleUserInfo;
import com.calisthenics.backend.user.Gender;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class GoogleAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void setUp() {
        // clean up test users if needed
    }

    @Test
    void authenticateWithGoogle_NewUser_CreatesUserWithRoleUser() throws Exception {
        GoogleUserInfo googleUser = GoogleUserInfo.builder()
                .sub("google-sub-1001")
                .email("newgoogleuser@example.com")
                .name("New Google Athlete")
                .picture("https://example.com/avatar1001.png")
                .build();

        when(googleTokenVerifier.verifyToken("valid-new-token")).thenReturn(googleUser);

        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("valid-new-token")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("newgoogleuser@example.com"))
                .andExpect(jsonPath("$.fullName").value("New Google Athlete"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.gender").doesNotExist());

        User savedUser = userRepository.findByGoogleId("google-sub-1001").orElse(null);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("newgoogleuser@example.com");
        assertThat(savedUser.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(savedUser.getGender()).isNull();
        assertThat(savedUser.getPasswordHash()).isNull();
    }

    @Test
    void authenticateWithGoogle_ExistingGoogleUser_AuthenticatesNormally() throws Exception {
        User existing = User.builder()
                .email("existinggoogle@example.com")
                .googleId("google-sub-2002")
                .fullName("Existing Google User")
                .role(Role.ROLE_USER)
                .gender(Gender.FEMALE)
                .build();
        userRepository.save(existing);

        GoogleUserInfo googleUser = GoogleUserInfo.builder()
                .sub("google-sub-2002")
                .email("existinggoogle@example.com")
                .name("Existing Google User")
                .build();

        when(googleTokenVerifier.verifyToken("valid-existing-token")).thenReturn(googleUser);

        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("valid-existing-token")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("existinggoogle@example.com"))
                .andExpect(jsonPath("$.userId").value(existing.getId()))
                .andExpect(jsonPath("$.gender").value("FEMALE"));
    }

    @Test
    void authenticateWithGoogle_ExistingPasswordAccount_RejectsWithConflict409() throws Exception {
        User existingPasswordUser = User.builder()
                .email("passworduser@example.com")
                .passwordHash(passwordEncoder.encode("secretPassword123"))
                .fullName("Password Athlete")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(existingPasswordUser);

        GoogleUserInfo googleUser = GoogleUserInfo.builder()
                .sub("google-sub-3003")
                .email("passworduser@example.com")
                .name("Google Profile Athlete")
                .build();

        when(googleTokenVerifier.verifyToken("token-conflict-pwd")).thenReturn(googleUser);

        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("token-conflict-pwd")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        // Ensure accounts were not automatically linked
        User afterAttempt = userRepository.findByEmail("passworduser@example.com").orElseThrow();
        assertThat(afterAttempt.getGoogleId()).isNull();
    }

    @Test
    void authenticateWithGoogle_InvalidOrExpiredToken_ReturnsUnauthorized401() throws Exception {
        when(googleTokenVerifier.verifyToken(anyString())).thenReturn(null);

        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("invalid-google-token")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticateWithGoogle_MissingIdToken_ReturnsBadRequest400() throws Exception {
        GoogleAuthRequest request = GoogleAuthRequest.builder()
                .idToken("")
                .build();

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

