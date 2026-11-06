package com.calisthenics.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

    private final DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();

    @Test
    @DisplayName("Processor has correct order before ConfigDataEnvironmentPostProcessor")
    void testOrder() {
        assertThat(processor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 9);
    }

    @Test
    @DisplayName("loadDotenv parses key-values, ignores comments, strips double and single quotes")
    void testLoadDotenv(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        String content = """
                # Comment line
                MAIL_HOST=smtp.gmail.com
                MAIL_PORT=587
                MAIL_USERNAME="myuser@gmail.com"
                MAIL_APP_PASSWORD='secret password'
                EMPTY_LINE_AFTER=
                
                # Another comment
                SPACED_KEY = value_with_spaces  
                """;
        Files.writeString(envFile, content);

        Map<String, Object> map = processor.loadDotenv(envFile);

        assertThat(map).containsEntry("MAIL_HOST", "smtp.gmail.com");
        assertThat(map).containsEntry("MAIL_PORT", "587");
        assertThat(map).containsEntry("MAIL_USERNAME", "myuser@gmail.com");
        assertThat(map).containsEntry("MAIL_APP_PASSWORD", "secret password");
        assertThat(map).containsEntry("EMPTY_LINE_AFTER", "");
        assertThat(map).containsEntry("SPACED_KEY", "value_with_spaces");
        assertThat(map).doesNotContainKey("# Comment line");
    }

    @Test
    @DisplayName("postProcessEnvironment skips loading if active profile is test")
    void testSkipsWhenTestProfileActive(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MAIL_USERNAME=test@domain.com\n");

        DotenvEnvironmentPostProcessor customProcessor = new DotenvEnvironmentPostProcessor() {
            @Override
            Path findDotenvFile() {
                return envFile;
            }
        };

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");

        customProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }

    @Test
    @DisplayName("postProcessEnvironment skips loading if calisthenics.dotenv.enabled is false")
    void testSkipsWhenDisabled(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MAIL_USERNAME=test@domain.com\n");

        DotenvEnvironmentPostProcessor customProcessor = new DotenvEnvironmentPostProcessor() {
            @Override
            Path findDotenvFile() {
                return envFile;
            }
        };

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("testProps", Map.of("calisthenics.dotenv.enabled", "false"))
        );

        customProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }

    @Test
    @DisplayName("postProcessEnvironment successfully adds dotenv property source when .env is present")
    void testLoadsPropertiesSuccessfully(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "MAIL_USERNAME=loadeduser@gmail.com\nMAIL_HOST=smtp.gmail.com\n");

        DotenvEnvironmentPostProcessor customProcessor = new DotenvEnvironmentPostProcessor() {
            @Override
            Path findDotenvFile() {
                return envFile;
            }
        };

        ConfigurableEnvironment environment = new StandardEnvironment();
        customProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isTrue();
        assertThat(environment.getProperty("MAIL_USERNAME")).isEqualTo("loadeduser@gmail.com");
        assertThat(environment.getProperty("MAIL_HOST")).isEqualTo("smtp.gmail.com");
    }

    @Test
    @DisplayName("postProcessEnvironment gracefully handles absent .env file")
    void testHandlesMissingDotenv() {
        DotenvEnvironmentPostProcessor customProcessor = new DotenvEnvironmentPostProcessor() {
            @Override
            Path findDotenvFile() {
                return null;
            }
        };

        ConfigurableEnvironment environment = new StandardEnvironment();
        customProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }
}

