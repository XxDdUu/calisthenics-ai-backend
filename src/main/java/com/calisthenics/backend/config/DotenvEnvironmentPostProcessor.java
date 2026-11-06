package com.calisthenics.backend.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Automatically loads key-value pairs from a local .env file into the Spring
 * Environment when running locally or during development.
 *
 * Placed immediately after system environment variables in the PropertySource
 * hierarchy, allowing actual OS environment variables (Docker, Kubernetes, CI/CD)
 * to take precedence while seamlessly providing defaults from .env for local
 * runs without manual shell exports.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Log logger = LogFactory.getLog(DotenvEnvironmentPostProcessor.class);

    public static final String PROPERTY_SOURCE_NAME = "dotenvProperties";
    // Runs right before ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE + 10)
    // so placeholders in application.yml can resolve values from .env.
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 9;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String enabled = environment.getProperty("calisthenics.dotenv.enabled");
        if ("false".equalsIgnoreCase(enabled)) {
            return;
        }

        // Avoid loading dev .env during automated tests
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile)) {
                return;
            }
        }
        String profilesProp = environment.getProperty("spring.profiles.active");
        if (profilesProp != null && profilesProp.toLowerCase().contains("test")) {
            return;
        }

        Path envPath = findDotenvFile();
        if (envPath == null || !Files.isRegularFile(envPath)) {
            return;
        }

        Map<String, Object> envMap = loadDotenv(envPath);
        if (envMap.isEmpty()) {
            return;
        }

        MutablePropertySources propertySources = environment.getPropertySources();
        if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new MapPropertySource(PROPERTY_SOURCE_NAME, envMap)
            );
        } else {
            propertySources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, envMap));
        }

        logger.info("Loaded " + envMap.size() + " environment properties from " + envPath.toAbsolutePath());
    }

    Path findDotenvFile() {
        // 1. Current working directory (.env)
        Path direct = Paths.get(".env");
        if (Files.isRegularFile(direct)) {
            return direct;
        }

        // 2. System user.dir (.env)
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            Path inUserDir = Paths.get(userDir, ".env");
            if (Files.isRegularFile(inUserDir)) {
                return inUserDir;
            }
        }

        // 3. Parent directory (../.env) for sub-module execution
        Path parent = Paths.get("..", ".env");
        if (Files.isRegularFile(parent)) {
            return parent;
        }

        return null;
    }

    Map<String, Object> loadDotenv(Path path) {
        Map<String, Object> map = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();

                // Strip matching quotes if wrapped
                if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                        || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
                    value = value.substring(1, value.length() - 1);
                }

                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not read .env file at " + path + ": " + e.getMessage());
        }
        return map;
    }
}
