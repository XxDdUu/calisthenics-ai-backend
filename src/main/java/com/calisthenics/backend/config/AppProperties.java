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
    }
}
