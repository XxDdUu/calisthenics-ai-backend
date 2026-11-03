package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.GoogleUserInfo;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GoogleTokenVerifierTest {

    @Mock
    private GoogleIdTokenVerifier mockVerifier;

    private GoogleTokenVerifier googleTokenVerifier;
    private final String clientId = "test-client-id.apps.googleusercontent.com";

    @BeforeEach
    void setUp() {
        googleTokenVerifier = new GoogleTokenVerifier(mockVerifier, clientId);
    }

    @Test
    void verifyToken_Success() throws Exception {
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("https://accounts.google.com");
        payload.setAudience(List.of(clientId));
        payload.setSubject("google-uid-12345");
        payload.setEmail("athlete@example.com");
        payload.set("name", "Test Athlete");
        payload.set("picture", "https://example.com/avatar.jpg");

        when(mockVerifier.verify("valid-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        GoogleUserInfo userInfo = googleTokenVerifier.verifyToken("valid-id-token");

        assertThat(userInfo).isNotNull();
        assertThat(userInfo.getSub()).isEqualTo("google-uid-12345");
        assertThat(userInfo.getEmail()).isEqualTo("athlete@example.com");
        assertThat(userInfo.getName()).isEqualTo("Test Athlete");
        assertThat(userInfo.getPicture()).isEqualTo("https://example.com/avatar.jpg");
    }

    @Test
    void verifyToken_AcceptsAlternativeValidIssuer() throws Exception {
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("accounts.google.com");
        payload.setAudience(List.of(clientId));
        payload.setSubject("google-uid-12345");
        payload.setEmail("athlete@example.com");

        when(mockVerifier.verify("valid-id-token")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        GoogleUserInfo userInfo = googleTokenVerifier.verifyToken("valid-id-token");

        assertThat(userInfo).isNotNull();
        assertThat(userInfo.getSub()).isEqualTo("google-uid-12345");
        assertThat(userInfo.getEmail()).isEqualTo("athlete@example.com");
    }

    @Test
    void verifyToken_InvalidIssuer_ReturnsNull() throws Exception {
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("https://malicious-issuer.com");
        payload.setAudience(List.of(clientId));
        payload.setSubject("google-uid-12345");
        payload.setEmail("athlete@example.com");

        when(mockVerifier.verify("token-wrong-issuer")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        GoogleUserInfo userInfo = googleTokenVerifier.verifyToken("token-wrong-issuer");

        assertThat(userInfo).isNull();
    }

    @Test
    void verifyToken_AudienceMismatch_ReturnsNull() throws Exception {
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("https://accounts.google.com");
        payload.setAudience(List.of("different-client-id"));
        payload.setSubject("google-uid-12345");
        payload.setEmail("athlete@example.com");

        when(mockVerifier.verify("token-wrong-audience")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        GoogleUserInfo userInfo = googleTokenVerifier.verifyToken("token-wrong-audience");

        assertThat(userInfo).isNull();
    }

    @Test
    void verifyToken_VerifierReturnsNull_ReturnsNull() throws Exception {
        when(mockVerifier.verify(anyString())).thenReturn(null);

        GoogleUserInfo userInfo = googleTokenVerifier.verifyToken("invalid-token");

        assertThat(userInfo).isNull();
    }

    @Test
    void verifyToken_BlankOrNullToken_ReturnsNull() {
        assertThat(googleTokenVerifier.verifyToken(null)).isNull();
        assertThat(googleTokenVerifier.verifyToken("   ")).isNull();
    }

    @Test
    void verifyToken_UnverifiedEmail_ReturnsNull() throws Exception {
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("https://accounts.google.com");
        payload.setAudience(List.of(clientId));
        payload.setSubject("google-uid-12345");
        payload.setEmail("athlete@example.com");
        payload.setEmailVerified(false);

        when(mockVerifier.verify("token-unverified-email")).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);

        GoogleUserInfo userInfo = googleTokenVerifier.verifyToken("token-unverified-email");

        assertThat(userInfo).isNull();
    }
}

