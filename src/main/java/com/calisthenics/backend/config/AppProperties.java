package com.calisthenics.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private AiService aiService = new AiService();
    private Storage storage = new Storage();
    private Firebase firebase = new Firebase();
    private Google google = new Google();
    private Gemini gemini = new Gemini();
    private String timezone = "Asia/Ho_Chi_Minh";
    private WorkoutPlanner workoutPlanner = new WorkoutPlanner();

    @Getter
    @Setter
    public static class WorkoutPlanner {
        private int minExercisesPerSession = 1;
        private int recommendedMinExercises = 4;
        private int recommendedMaxExercises = 8;
        private int maxExercisesPerSession = 10;
        private int beginnerMaxExercises = 8;
        private int intermediateMaxExercises = 10;
        private int advancedMaxExercises = 10;
        private int skillMobilityMaxExercises = 8;
        private int minTrainingDaysPerWeek = 1;
        private int maxTrainingDaysPerWeek = 7;
        private int minDurationWeeks = 1;
        private int maxDurationWeeks = 12;
        private int correctionRetries = 1;
    }

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-flash-lite-latest";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private int timeoutSeconds = 30;
        private int maxRetries = 2;
        private long initialRetryDelayMs = 1000L;
        private long maxRetryDelayMs = 8000L;
        private long maxRetryAfterMs = 15000L;
        private int exerciseGenerationMaxRetries = 3;
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId;
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs;
        private long refreshExpirationMs;
    }

    @Getter
    @Setter
    public static class AiService {
        private String url;
        private int timeoutSeconds;
    }

    @Getter
    @Setter
    public static class Storage {
        private String location;
    }

    @Getter
    @Setter
    public static class Firebase {
        private boolean enabled = true;
        private String credentialsPath;
        private String credentialsJson;
        private String projectId = "sky-bf5aa";
        private boolean checkRevoked = true;
        private boolean requireCredentials = false;
    }
}
