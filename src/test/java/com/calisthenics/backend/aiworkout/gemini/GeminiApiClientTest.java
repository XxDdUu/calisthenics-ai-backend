package com.calisthenics.backend.aiworkout.gemini;

import com.calisthenics.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.server.ResponseStatusException;

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
}
