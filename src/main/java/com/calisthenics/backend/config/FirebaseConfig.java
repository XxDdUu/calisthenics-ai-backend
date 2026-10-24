package com.calisthenics.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!appProperties.getFirebase().isEnabled()) {
            log.warn("Firebase Authentication is disabled via configuration.");
            return null;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            try {
                String credentialsPath = appProperties.getFirebase().getCredentialsPath();
                log.info("Attempting to initialize Firebase App with credentials path: {}", credentialsPath);

                Resource resource = resourceLoader.getResource(credentialsPath);
                if (resource.exists()) {
                    try (InputStream serviceAccount = resource.getInputStream()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        FirebaseApp app = FirebaseApp.initializeApp(options);
                        log.info("Firebase Application successfully initialized.");
                        return app;
                    }
                } else {
                    log.warn("Firebase service account credentials file NOT found at {}. Firebase token verification will run in fallback mode until valid credentials are provided.", credentialsPath);
                    // Initialize default app for dev / fallback if needed
                    FirebaseOptions defaultOptions = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(InputStream.nullInputStream()))
                            .build();
                    return null;
                }
            } catch (Exception e) {
                log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage());
                return null;
            }
        } else {
            return FirebaseApp.getInstance();
        }
    }
}
