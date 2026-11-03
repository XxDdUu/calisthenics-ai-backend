package com.calisthenics.backend.security;

import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.security.dto.GoogleUserInfo;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final List<String> VALID_ISSUERS = List.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    private final GoogleIdTokenVerifier verifier;
    private final String configuredClientId;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenVerifier(AppProperties appProperties) {
        this(appProperties, new NetHttpTransport(), GsonFactory.getDefaultInstance());
    }

    public GoogleTokenVerifier(AppProperties appProperties, NetHttpTransport transport, GsonFactory jsonFactory) {
        String clientId = appProperties.getGoogle() != null ? appProperties.getGoogle().getClientId() : null;
        this.configuredClientId = clientId != null ? clientId.trim() : null;

        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(transport, jsonFactory);
        if (this.configuredClientId != null && !this.configuredClientId.isBlank()) {
            builder.setAudience(Collections.singletonList(this.configuredClientId));
        }
        this.verifier = builder.build();
    }

    GoogleTokenVerifier(GoogleIdTokenVerifier verifier, String configuredClientId) {
        this.verifier = verifier;
        this.configuredClientId = configuredClientId;
    }

    public GoogleUserInfo verifyToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            return null;
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token signature or claims verification failed");
                return null;
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Validate issuer explicitly
            String issuer = payload.getIssuer();
            if (issuer == null || !VALID_ISSUERS.contains(issuer)) {
                log.warn("Google ID token issuer invalid: {}", issuer);
                return null;
            }

            // Validate audience against configured client ID if configured
            if (configuredClientId != null && !configuredClientId.isBlank()) {
                List<String> audienceList = payload.getAudienceAsList();
                if (audienceList == null || !audienceList.contains(configuredClientId)) {
                    log.warn("Google ID token audience mismatch: expected={}, got={}", configuredClientId, audienceList);
                    return null;
                }
            }

            String sub = payload.getSubject();
            String email = payload.getEmail();
            Boolean emailVerified = payload.getEmailVerified();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                log.warn("Google ID token missing subject or email");
                return null;
            }

            if (Boolean.FALSE.equals(emailVerified)) {
                log.warn("Google ID token email is not verified");
                return null;
            }

            return GoogleUserInfo.builder()
                    .sub(sub)
                    .email(email)
                    .name(name)
                    .picture(picture)
                    .build();
        } catch (Exception e) {
            log.warn("Exception during Google ID token verification: {}", e.getMessage());
            return null;
        }
    }
}

