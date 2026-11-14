package com.calisthenics.backend.security;

import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.FirebaseRuntime;
import com.calisthenics.backend.security.dto.FirebaseUserInfo;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
public class FirebaseTokenVerifier {

    private static final String FIREBASE_ISSUER_PREFIX = "https://securetoken.google.com/";

    private final FirebaseAuth firebaseAuth;
    private final String expectedProjectId;
    private final boolean checkRevoked;

    @Autowired
    public FirebaseTokenVerifier(
            ObjectProvider<FirebaseRuntime> runtimeProvider,
            AppProperties appProperties
    ) {
        FirebaseRuntime runtime = runtimeProvider.getIfAvailable();
        AppProperties.Firebase firebaseConfig = appProperties.getFirebase();
        this.firebaseAuth = runtime != null ? runtime.auth() : null;
        String projectId = runtime != null && StringUtils.hasText(runtime.projectId())
                ? runtime.projectId()
                : (firebaseConfig != null ? firebaseConfig.getProjectId() : null);
        this.expectedProjectId = StringUtils.hasText(projectId) ? projectId.trim() : null;
        this.checkRevoked = firebaseConfig != null
                && firebaseConfig.isCheckRevoked()
                && runtime != null
                && runtime.adminCredentialsLoaded();
    }

    FirebaseTokenVerifier(FirebaseAuth firebaseAuth, String expectedProjectId, boolean checkRevoked) {
        this.firebaseAuth = firebaseAuth;
        this.expectedProjectId = StringUtils.hasText(expectedProjectId) ? expectedProjectId.trim() : null;
        this.checkRevoked = checkRevoked;
    }

    public FirebaseUserInfo verifyToken(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new FirebaseAuthenticationException(
                    "INVALID_FIREBASE_TOKEN",
                    "Firebase ID token is required.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (firebaseAuth == null) {
            log.error("Firebase ID token verification attempted while FirebaseAuth is unavailable.");
            throw new FirebaseAuthenticationException(
                    "FIREBASE_AUTH_UNAVAILABLE",
                    "Firebase authentication is currently unavailable on the server.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        FirebaseToken decodedToken;
        try {
            decodedToken = checkRevoked
                    ? firebaseAuth.verifyIdToken(idToken.trim(), true)
                    : firebaseAuth.verifyIdToken(idToken.trim());
        } catch (FirebaseAuthException ex) {
            throw mapFirebaseAuthException(ex);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid Firebase ID token argument: {}", ex.getMessage());
            throw new FirebaseAuthenticationException(
                    "INVALID_FIREBASE_TOKEN",
                    "Invalid Firebase authentication token.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (decodedToken == null) {
            throw new FirebaseAuthenticationException(
                    "INVALID_FIREBASE_TOKEN",
                    "Invalid Firebase authentication token.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        validateClaims(decodedToken);

        String email = decodedToken.getEmail();
        if (!StringUtils.hasText(email)) {
            log.warn("Firebase token for uid={} missing email claim", decodedToken.getUid());
            throw new FirebaseAuthenticationException(
                    "FIREBASE_EMAIL_MISSING",
                    "Your Google/Firebase account did not provide an email address.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (!decodedToken.isEmailVerified()) {
            log.warn("Firebase token rejected for unverified email, uid={}", decodedToken.getUid());
            throw new FirebaseAuthenticationException(
                    "FIREBASE_EMAIL_NOT_VERIFIED",
                    "Please verify your email address before signing in.",
                    HttpStatus.FORBIDDEN
            );
        }

        return FirebaseUserInfo.builder()
                .uid(decodedToken.getUid().trim())
                .email(email.trim().toLowerCase())
                .emailVerified(true)
                .name(StringUtils.hasText(decodedToken.getName()) ? decodedToken.getName().trim() : null)
                .picture(StringUtils.hasText(decodedToken.getPicture()) ? decodedToken.getPicture().trim() : null)
                .googleId(extractGoogleProviderId(decodedToken.getClaims()))
                .build();
    }

    private void validateClaims(FirebaseToken decodedToken) {
        if (!StringUtils.hasText(decodedToken.getUid())) {
            log.warn("Firebase token missing subject/uid claim");
            throw new FirebaseAuthenticationException(
                    "INVALID_FIREBASE_TOKEN",
                    "Invalid Firebase authentication token.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (StringUtils.hasText(expectedProjectId)) {
            String expectedIssuer = FIREBASE_ISSUER_PREFIX + expectedProjectId;
            if (!expectedIssuer.equals(decodedToken.getIssuer())) {
                log.warn("Firebase token issuer mismatch: expected={}, got={}", expectedIssuer, decodedToken.getIssuer());
                throw new FirebaseAuthenticationException(
                        "INVALID_FIREBASE_TOKEN",
                        "Firebase token does not belong to this application.",
                        HttpStatus.UNAUTHORIZED
                );
            }

            Map<String, Object> claims = decodedToken.getClaims();
            Object aud = claims != null ? claims.get("aud") : null;
            if (!matchesAudience(aud, expectedProjectId)) {
                log.warn("Firebase token audience mismatch: expected={}, got={}", expectedProjectId, aud);
                throw new FirebaseAuthenticationException(
                        "INVALID_FIREBASE_TOKEN",
                        "Firebase token audience does not match this project.",
                        HttpStatus.UNAUTHORIZED
                );
            }
        }
    }

    private boolean matchesAudience(Object audClaim, String projectId) {
        if (audClaim instanceof String audString) {
            return projectId.equals(audString);
        }
        if (audClaim instanceof Collection<?> audList) {
            return audList.contains(projectId);
        }
        return false;
    }

    private String extractGoogleProviderId(Map<String, Object> claims) {
        if (claims == null) {
            return null;
        }
        Object firebaseObj = claims.get("firebase");
        if (!(firebaseObj instanceof Map<?, ?> firebaseMap)) {
            return null;
        }
        Object identitiesObj = firebaseMap.get("identities");
        if (!(identitiesObj instanceof Map<?, ?> identitiesMap)) {
            return null;
        }
        Object googleIds = identitiesMap.get("google.com");
        if (googleIds instanceof Collection<?> collection) {
            for (Object id : collection) {
                if (id instanceof String s && StringUtils.hasText(s)) {
                    return s.trim();
                }
            }
        }
        return null;
    }

    private FirebaseAuthenticationException mapFirebaseAuthException(FirebaseAuthException ex) {
        AuthErrorCode errorCode = ex.getAuthErrorCode();
        if (errorCode == null) {
            log.warn("Firebase ID token verification failed: {}", ex.getMessage());
            return new FirebaseAuthenticationException(
                    "INVALID_FIREBASE_TOKEN",
                    "Invalid Firebase authentication token.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        return switch (errorCode) {
            case EXPIRED_ID_TOKEN -> {
                log.info("Expired Firebase ID token rejected.");
                yield new FirebaseAuthenticationException(
                        "FIREBASE_TOKEN_EXPIRED",
                        "Firebase authentication session expired. Please sign in again.",
                        HttpStatus.UNAUTHORIZED
                );
            }
            case REVOKED_ID_TOKEN -> {
                log.warn("Revoked Firebase ID token rejected.");
                yield new FirebaseAuthenticationException(
                        "FIREBASE_TOKEN_REVOKED",
                        "Firebase session has been revoked. Please sign in again.",
                        HttpStatus.UNAUTHORIZED
                );
            }
            case USER_DISABLED -> {
                log.warn("Disabled Firebase user rejected.");
                yield new FirebaseAuthenticationException(
                        "ACCOUNT_DISABLED",
                        "This account has been disabled.",
                        HttpStatus.FORBIDDEN
                );
            }
            case CERTIFICATE_FETCH_FAILED, CONFIGURATION_NOT_FOUND -> {
                log.error("Firebase service error during verification ({}): {}", errorCode, ex.getMessage());
                yield new FirebaseAuthenticationException(
                        "FIREBASE_AUTH_UNAVAILABLE",
                        "Unable to verify Firebase credentials right now. Please try again.",
                        HttpStatus.SERVICE_UNAVAILABLE
                );
            }
            default -> {
                log.warn("Firebase ID token rejected ({}): {}", errorCode, ex.getMessage());
                yield new FirebaseAuthenticationException(
                        "INVALID_FIREBASE_TOKEN",
                        "Invalid Firebase authentication token.",
                        HttpStatus.UNAUTHORIZED
                );
            }
        };
    }
}
