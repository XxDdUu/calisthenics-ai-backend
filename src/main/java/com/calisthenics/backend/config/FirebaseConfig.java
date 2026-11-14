package com.calisthenics.backend.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;

    @Bean
    public FirebaseRuntime firebaseRuntime() {
        AppProperties.Firebase config = appProperties.getFirebase();
        String configuredProjectId = StringUtils.hasText(config.getProjectId())
                ? config.getProjectId().trim()
                : null;

        if (!config.isEnabled()) {
            log.info("Firebase Authentication is disabled via configuration.");
            return new FirebaseRuntime(null, null, configuredProjectId, false);
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            FirebaseApp existingApp = FirebaseApp.getInstance();
            String projectId = StringUtils.hasText(existingApp.getOptions().getProjectId())
                    ? existingApp.getOptions().getProjectId()
                    : configuredProjectId;
            return new FirebaseRuntime(existingApp, FirebaseAuth.getInstance(existingApp), projectId, false);
        }

        try {
            CredentialsResolution resolved = resolveCredentials(config);
            String resolvedProjectId = StringUtils.hasText(configuredProjectId)
                    ? configuredProjectId
                    : resolved.serviceAccountProjectId();

            if (!StringUtils.hasText(resolvedProjectId)) {
                log.error("Firebase Authentication is enabled but no projectId is configured.");
                return new FirebaseRuntime(null, null, null, false);
            }

            if (StringUtils.hasText(resolved.serviceAccountProjectId())
                    && !resolved.serviceAccountProjectId().equals(resolvedProjectId)) {
                log.warn("Configured Firebase projectId ({}) differs from service account project_id ({}). Using configured projectId.",
                        resolvedProjectId, resolved.serviceAccountProjectId());
            }

            GoogleCredentials credentials = resolved.credentials();
            boolean adminCredentialsLoaded = resolved.adminCredentialsLoaded();

            if (credentials == null) {
                if (config.isRequireCredentials()) {
                    log.error("Firebase Authentication requires server-side credentials, but none were found.");
                    return new FirebaseRuntime(null, null, resolvedProjectId, false);
                }
                log.warn("Firebase service account credentials not found; initializing Firebase ID token verification in public-key-only mode for projectId={} (set FIREBASE_CREDENTIALS_PATH or FIREBASE_SERVICE_ACCOUNT_JSON to enable revocation checks).",
                        resolvedProjectId);
                credentials = GoogleCredentials.create(new AccessToken("public-key-verification-only", new Date(Long.MAX_VALUE)));
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(resolvedProjectId)
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            FirebaseAuth auth = FirebaseAuth.getInstance(app);
            log.info("Firebase Admin SDK initialized for projectId={} (adminCredentialsLoaded={})",
                    resolvedProjectId, adminCredentialsLoaded);
            return new FirebaseRuntime(app, auth, resolvedProjectId, adminCredentialsLoaded);
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage());
            return new FirebaseRuntime(null, null, configuredProjectId, false);
        }
    }

    @Bean
    public FirebaseApp firebaseApp(ObjectProvider<FirebaseRuntime> runtimeProvider) {
        FirebaseRuntime runtime = runtimeProvider.getIfAvailable();
        return runtime != null ? runtime.app() : null;
    }

    private CredentialsResolution resolveCredentials(AppProperties.Firebase config) throws IOException {
        if (StringUtils.hasText(config.getCredentialsJson())) {
            try (InputStream stream = new ByteArrayInputStream(
                    config.getCredentialsJson().trim().getBytes(StandardCharsets.UTF_8))) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
                return new CredentialsResolution(credentials, extractProjectId(credentials), true);
            }
        }

        String credentialsPath = config.getCredentialsPath();
        if (StringUtils.hasText(credentialsPath)) {
            Resource resource = resourceLoader.getResource(credentialsPath.trim());
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
                    log.info("Loaded Firebase service account credentials from configured path.");
                    return new CredentialsResolution(credentials, extractProjectId(credentials), true);
                }
            }
        }

        String googleAppCreds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (StringUtils.hasText(googleAppCreds)) {
            GoogleCredentials adc = GoogleCredentials.getApplicationDefault();
            log.info("Loaded Firebase credentials from GOOGLE_APPLICATION_CREDENTIALS ADC.");
            return new CredentialsResolution(adc, extractProjectId(adc), true);
        }

        return new CredentialsResolution(null, null, false);
    }

    private String extractProjectId(GoogleCredentials credentials) {
        if (credentials instanceof ServiceAccountCredentials serviceAccount) {
            return serviceAccount.getProjectId();
        }
        return null;
    }

    private record CredentialsResolution(
            GoogleCredentials credentials,
            String serviceAccountProjectId,
            boolean adminCredentialsLoaded
    ) {}
}
