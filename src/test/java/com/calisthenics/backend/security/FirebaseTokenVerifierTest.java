package com.calisthenics.backend.security;

import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.FirebaseConfig;
import com.calisthenics.backend.config.FirebaseRuntime;
import com.calisthenics.backend.security.dto.FirebaseUserInfo;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FirebaseTokenVerifierTest {

    private static final String PROJECT_ID = "sky-bf5aa";
    private static final String VALID_ISSUER = "https://securetoken.google.com/" + PROJECT_ID;

    @Mock
    private FirebaseAuth mockFirebaseAuth;

    private FirebaseTokenVerifier verifier;
    private FirebaseTokenVerifier verifierWithRevocationCheck;

    @BeforeEach
    void setUp() {
        verifier = new FirebaseTokenVerifier(mockFirebaseAuth, PROJECT_ID, false);
        verifierWithRevocationCheck = new FirebaseTokenVerifier(mockFirebaseAuth, PROJECT_ID, true);
    }

    @AfterEach
    void tearDownFirebaseApps() {
        for (FirebaseApp app : List.copyOf(FirebaseApp.getApps())) {
            app.delete();
        }
    }

    @Test
    void verifyToken_ValidFirebaseToken_ReturnsUserInfo() throws Exception {
        FirebaseToken token = mockToken(
                "firebase-uid-123",
                VALID_ISSUER,
                PROJECT_ID,
                "Athlete@Example.com",
                true,
                "Calisthenics Athlete",
                "https://example.com/avatar.png",
                Map.of(
                        "aud", PROJECT_ID,
                        "firebase", Map.of("identities", Map.of("google.com", List.of("google-sub-999")))
                )
        );

        when(mockFirebaseAuth.verifyIdToken("valid-firebase-token")).thenReturn(token);

        FirebaseUserInfo info = verifier.verifyToken("valid-firebase-token");

        assertThat(info.getUid()).isEqualTo("firebase-uid-123");
        assertThat(info.getEmail()).isEqualTo("athlete@example.com");
        assertThat(info.isEmailVerified()).isTrue();
        assertThat(info.getName()).isEqualTo("Calisthenics Athlete");
        assertThat(info.getPicture()).isEqualTo("https://example.com/avatar.png");
        assertThat(info.getGoogleId()).isEqualTo("google-sub-999");
        verify(mockFirebaseAuth).verifyIdToken("valid-firebase-token");
    }

    @Test
    void verifyToken_WithRevocationCheckEnabled_CallsCheckRevoked() throws Exception {
        FirebaseToken token = mockToken(
                "firebase-uid-123",
                VALID_ISSUER,
                PROJECT_ID,
                "athlete@example.com",
                true,
                "Athlete",
                null,
                Map.of("aud", PROJECT_ID)
        );
        when(mockFirebaseAuth.verifyIdToken("valid-token", true)).thenReturn(token);

        FirebaseUserInfo info = verifierWithRevocationCheck.verifyToken("valid-token");

        assertThat(info.getUid()).isEqualTo("firebase-uid-123");
        verify(mockFirebaseAuth).verifyIdToken("valid-token", true);
    }

    @Test
    void verifyToken_BlankToken_ThrowsUnauthorized() {
        assertThatThrownBy(() -> verifier.verifyToken("   "))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("INVALID_FIREBASE_TOKEN");
                });
    }

    @Test
    void verifyToken_UnavailableFirebaseAuth_ThrowsServiceUnavailable() {
        FirebaseTokenVerifier unconfigured = new FirebaseTokenVerifier(null, PROJECT_ID, false);

        assertThatThrownBy(() -> unconfigured.verifyToken("some-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(authEx.getCode()).isEqualTo("FIREBASE_AUTH_UNAVAILABLE");
                });
    }

    @Test
    void verifyToken_ExpiredToken_ThrowsUnauthorizedWithExpiredCode() throws Exception {
        FirebaseAuthException expiredEx = mockFirebaseAuthException(AuthErrorCode.EXPIRED_ID_TOKEN, "Token expired");
        when(mockFirebaseAuth.verifyIdToken("expired-token")).thenThrow(expiredEx);

        assertThatThrownBy(() -> verifier.verifyToken("expired-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("FIREBASE_TOKEN_EXPIRED");
                });
    }

    @Test
    void verifyToken_RevokedToken_ThrowsUnauthorizedWithRevokedCode() throws Exception {
        FirebaseAuthException revokedEx = mockFirebaseAuthException(AuthErrorCode.REVOKED_ID_TOKEN, "Token revoked");
        when(mockFirebaseAuth.verifyIdToken("revoked-token", true)).thenThrow(revokedEx);

        assertThatThrownBy(() -> verifierWithRevocationCheck.verifyToken("revoked-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("FIREBASE_TOKEN_REVOKED");
                });
    }

    @Test
    void verifyToken_UserDisabled_ThrowsForbidden() throws Exception {
        FirebaseAuthException disabledEx = mockFirebaseAuthException(AuthErrorCode.USER_DISABLED, "User disabled");
        when(mockFirebaseAuth.verifyIdToken("disabled-token")).thenThrow(disabledEx);

        assertThatThrownBy(() -> verifier.verifyToken("disabled-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(authEx.getCode()).isEqualTo("ACCOUNT_DISABLED");
                });
    }

    @Test
    void verifyToken_WrongProjectIssuer_ThrowsUnauthorized() throws Exception {
        FirebaseToken token = mockToken(
                "uid-other",
                "https://securetoken.google.com/wrong-project",
                PROJECT_ID,
                "athlete@example.com",
                true,
                "Athlete",
                null,
                Map.of("aud", PROJECT_ID)
        );
        when(mockFirebaseAuth.verifyIdToken("wrong-issuer-token")).thenReturn(token);

        assertThatThrownBy(() -> verifier.verifyToken("wrong-issuer-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("INVALID_FIREBASE_TOKEN");
                });
    }

    @Test
    void verifyToken_WrongAudience_ThrowsUnauthorized() throws Exception {
        FirebaseToken token = mockToken(
                "uid-other",
                VALID_ISSUER,
                "wrong-project",
                "athlete@example.com",
                true,
                "Athlete",
                null,
                Map.of("aud", "wrong-project")
        );
        when(mockFirebaseAuth.verifyIdToken("wrong-aud-token")).thenReturn(token);

        assertThatThrownBy(() -> verifier.verifyToken("wrong-aud-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(authEx.getCode()).isEqualTo("INVALID_FIREBASE_TOKEN");
                });
    }

    @Test
    void verifyToken_UnverifiedEmail_ThrowsForbidden() throws Exception {
        FirebaseToken token = mockToken(
                "uid-unverified",
                VALID_ISSUER,
                PROJECT_ID,
                "athlete@example.com",
                false,
                "Athlete",
                null,
                Map.of("aud", PROJECT_ID)
        );
        when(mockFirebaseAuth.verifyIdToken("unverified-email-token")).thenReturn(token);

        assertThatThrownBy(() -> verifier.verifyToken("unverified-email-token"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> {
                    FirebaseAuthenticationException authEx = (FirebaseAuthenticationException) ex;
                    assertThat(authEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(authEx.getCode()).isEqualTo("FIREBASE_EMAIL_NOT_VERIFIED");
                });
    }

    @Test
    void firebaseConfig_PublicKeyFallbackInitializesCleanlyForProjectId() {
        AppProperties props = new AppProperties();
        AppProperties.Firebase firebase = new AppProperties.Firebase();
        firebase.setEnabled(true);
        firebase.setProjectId(PROJECT_ID);
        firebase.setCredentialsPath("classpath:non-existent-service-account.json");
        firebase.setRequireCredentials(false);
        props.setFirebase(firebase);

        FirebaseConfig config = new FirebaseConfig(props, new DefaultResourceLoader());
        FirebaseRuntime runtime = config.firebaseRuntime();

        assertThat(runtime.isAvailable()).isTrue();
        assertThat(runtime.projectId()).isEqualTo(PROJECT_ID);
        assertThat(runtime.adminCredentialsLoaded()).isFalse();

        FirebaseTokenVerifier realVerifier = new FirebaseTokenVerifier(runtime.auth(), PROJECT_ID, false);
        assertThatThrownBy(() -> realVerifier.verifyToken("not-a-valid-firebase-jwt"))
                .isInstanceOf(FirebaseAuthenticationException.class)
                .satisfies(ex -> assertThat(((FirebaseAuthenticationException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private FirebaseToken mockToken(
            String uid,
            String issuer,
            String aud,
            String email,
            boolean emailVerified,
            String name,
            String picture,
            Map<String, Object> claims
    ) {
        FirebaseToken token = mock(FirebaseToken.class);
        lenient().when(token.getUid()).thenReturn(uid);
        lenient().when(token.getIssuer()).thenReturn(issuer);
        lenient().when(token.getEmail()).thenReturn(email);
        lenient().when(token.isEmailVerified()).thenReturn(emailVerified);
        lenient().when(token.getName()).thenReturn(name);
        lenient().when(token.getPicture()).thenReturn(picture);
        lenient().when(token.getClaims()).thenReturn(claims);
        return token;
    }

    private FirebaseAuthException mockFirebaseAuthException(AuthErrorCode errorCode, String message) {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        lenient().when(ex.getAuthErrorCode()).thenReturn(errorCode);
        lenient().when(ex.getMessage()).thenReturn(message);
        return ex;
    }
}
