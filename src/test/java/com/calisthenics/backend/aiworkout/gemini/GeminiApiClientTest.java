package com.calisthenics.backend.aiworkout.gemini;

import com.calisthenics.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiApiClientTest {

    @Test
    void generatePerformanceSummary_missingApiKey_returnsEmpty() {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey("");

        GeminiApiClient client = new GeminiApiClient(props, new ObjectMapper());
        Optional<GeminiApiClient.GeminiSummaryResult> result =
                client.generatePerformanceSummary("sys", "user");

        assertThat(result).isEmpty();
    }

    @Test
    void generateWorkoutPlan_missingApiKey_throwsServiceUnavailableInsteadOfSilentFallback() {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey("");

        GeminiApiClient client = new GeminiApiClient(props, new ObjectMapper());
        assertThatThrownBy(() -> client.generateWorkoutPlan("sys", "user"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void generatePerformanceSummary_retriesTransient503AndSucceeds() {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey("test-key-value");
        props.getGemini().setModel("gemini-flash-lite-latest");
        props.getGemini().setBaseUrl("https://generativelanguage.googleapis.com/v1beta");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String expectedUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";
        String geminiPayload = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"overall_assessment\\":\\"Strong squat control.\\",\\"strengths\\":[\\"Good depth\\"],\\"weaknesses\\":[\\"Slight knee valgus\\"],\\"recommendations\\":[\\"Drive knees out\\",\\"Slow eccentric\\",\\"Pause at bottom\\"],\\"next_session_focus\\":\\"Balance\\"}"
                          }
                        ]
                      }
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 95,
                    "candidatesTokenCount": 70
                  }
                }
                """;

        server.expect(requestTo(expectedUrl))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(geminiPayload, MediaType.APPLICATION_JSON));

        GeminiApiClient client = new GeminiApiClient(props, new ObjectMapper()) {
            @Override
            protected RestTemplate createRestTemplate() {
                return restTemplate;
            }
        };

        Optional<GeminiApiClient.GeminiSummaryResult> result =
                client.generatePerformanceSummary("system", "metrics");

        server.verify();
        assertThat(result).isPresent();
        assertThat(result.get().summary().getOverallAssessment()).isEqualTo("Strong squat control.");
        assertThat(result.get().summary().getRecommendations()).hasSize(3);
        assertThat(result.get().modelUsed()).isEqualTo("gemini-flash-lite-latest");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildResponseSchema_omitsMinMaxItemsWhilePreservingNumericBoundsAndFields() {
        Map<String, Object> schema = GeminiApiClient.buildResponseSchema();
        assertNoArrayCardinalityBounds(schema);

        Map<String, Object> rootProps = (Map<String, Object>) schema.get("properties");
        assertThat(rootProps).containsKeys("plan_name", "description", "duration_weeks", "suggested_start_date", "split_type", "sessions");

        Map<String, Object> durationSchema = (Map<String, Object>) rootProps.get("duration_weeks");
        assertThat(durationSchema).containsEntry("minimum", 1).containsEntry("maximum", 12);

        Map<String, Object> sessionsArray = (Map<String, Object>) rootProps.get("sessions");
        Map<String, Object> sessionItem = (Map<String, Object>) sessionsArray.get("items");
        Map<String, Object> sessionProps = (Map<String, Object>) sessionItem.get("properties");
        assertThat(sessionProps).containsKeys("day_of_week", "title", "is_rest_day", "exercises");

        Map<String, Object> exercisesArray = (Map<String, Object>) sessionProps.get("exercises");
        Map<String, Object> exerciseItem = (Map<String, Object>) exercisesArray.get("items");
        Map<String, Object> exProps = (Map<String, Object>) exerciseItem.get("properties");
        assertThat(exProps).containsKeys("exercise_id", "exercise_name", "sets", "reps", "hold_seconds", "rest_seconds", "notes");
        assertThat((Map<String, Object>) exProps.get("sets")).containsEntry("minimum", 1).containsEntry("maximum", 10);
        assertThat((Map<String, Object>) exProps.get("reps")).containsEntry("minimum", 1).containsEntry("maximum", 100);
        assertThat((Map<String, Object>) exProps.get("hold_seconds")).containsEntry("minimum", 1).containsEntry("maximum", 300);
        assertThat((Map<String, Object>) exProps.get("rest_seconds")).containsEntry("minimum", 0).containsEntry("maximum", 600);
    }

    @Test
    void generateWorkoutPlan_retries503WithRetryAfterAndExponentialBackoffWithJitter() {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey("test-key-value");
        props.getGemini().setModel("gemini-flash-lite-latest");
        props.getGemini().setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        props.getGemini().setMaxRetries(2);
        props.getGemini().setInitialRetryDelayMs(500L);
        props.getGemini().setMaxRetryDelayMs(4000L);
        props.getGemini().setMaxRetryAfterMs(10000L);

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String expectedUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";
        HttpHeaders retryAfterHeaders = new HttpHeaders();
        retryAfterHeaders.add(HttpHeaders.RETRY_AFTER, "3");

        String planJsonPayload = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"plan_name\\":\\"3-Day Plan\\",\\"duration_weeks\\":4,\\"sessions\\":[{\\"day_of_week\\":\\"MONDAY\\",\\"title\\":\\"Push\\",\\"is_rest_day\\":false,\\"exercises\\":[{\\"exercise_id\\":11,\\"exercise_name\\":\\"Push-Up\\",\\"sets\\":3,\\"reps\\":10,\\"rest_seconds\\":90}]}]}"
                          }
                        ]
                      }
                    }
                  ],
                  "usageMetadata": { "promptTokenCount": 80, "candidatesTokenCount": 110 }
                }
                """;

        server.expect(requestTo(expectedUrl))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .headers(retryAfterHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
        server.expect(requestTo(expectedUrl))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(planJsonPayload, MediaType.APPLICATION_JSON));

        List<Long> recordedSleeps = new ArrayList<>();
        GeminiApiClient client = new GeminiApiClient(props, new ObjectMapper()) {
            @Override
            protected RestTemplate createRestTemplate() {
                return restTemplate;
            }

            @Override
            protected void sleepBeforeRetry(long delayMs) {
                recordedSleeps.add(delayMs);
            }

            @Override
            protected long computeJitterMs(long maxJitterMs) {
                return 250L;
            }
        };

        GeminiApiClient.GeminiCallResult result = client.generateWorkoutPlan("sys", "user");

        server.verify();
        assertThat(result.plan().getPlanName()).isEqualTo("3-Day Plan");
        assertThat(recordedSleeps).hasSize(2);
        // Attempt 1 honored Retry-After: 3 seconds -> 3000ms
        assertThat(recordedSleeps.get(0)).isEqualTo(3000L);
        // Attempt 2 used exponential backoff: 500 * 2^(2-1) + jitter(250) = 1250ms
        assertThat(recordedSleeps.get(1)).isEqualTo(1250L);
    }

    @Test
    void generateWorkoutPlan_doesNotRetryDeterministic400() {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey("test-key-value");
        props.getGemini().setModel("gemini-flash-lite-latest");
        props.getGemini().setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        props.getGemini().setMaxRetries(2);

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        String expectedUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";
        server.expect(requestTo(expectedUrl))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\"}}"));

        List<Long> recordedSleeps = new ArrayList<>();
        GeminiApiClient client = new GeminiApiClient(props, new ObjectMapper()) {
            @Override
            protected RestTemplate createRestTemplate() {
                return restTemplate;
            }

            @Override
            protected void sleepBeforeRetry(long delayMs) {
                recordedSleeps.add(delayMs);
            }
        };

        assertThatThrownBy(() -> client.generateWorkoutPlan("sys", "user"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        server.verify();
        assertThat(recordedSleeps).isEmpty();
    }

    @Test
    void generateWorkoutPlan_exhaustsRetriesOnPersistent503AndFailsWithoutFallback() {
        AppProperties props = new AppProperties();
        props.getGemini().setApiKey("test-key-value");
        props.getGemini().setModel("gemini-flash-lite-latest");
        props.getGemini().setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        props.getGemini().setMaxRetries(2);

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        String expectedUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";

        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(expectedUrl))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}"));
        }

        GeminiApiClient client = new GeminiApiClient(props, new ObjectMapper()) {
            @Override
            protected RestTemplate createRestTemplate() {
                return restTemplate;
            }

            @Override
            protected void sleepBeforeRetry(long delayMs) {
                // No-op in unit test
            }
        };

        assertThatThrownBy(() -> client.generateWorkoutPlan("sys", "user"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        server.verify();
    }

    @SuppressWarnings("unchecked")
    private void assertNoArrayCardinalityBounds(Object node) {
        if (node instanceof Map<?, ?> map) {
            assertThat(map.keySet().stream().map(Object::toString))
                    .doesNotContain("minItems", "maxItems");
            for (Object value : map.values()) {
                assertNoArrayCardinalityBounds(value);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                assertNoArrayCardinalityBounds(item);
            }
        }
    }
}
