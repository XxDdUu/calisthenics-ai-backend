package com.calisthenics.backend.aiworkout.gemini;

import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiResponseDto;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredPerformanceSummary;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiApiClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public record GeminiCallResult(
            GeminiStructuredWorkoutPlan plan,
            String rawJson,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            String modelUsed
    ) {}

    public record GeminiSummaryResult(
            GeminiStructuredPerformanceSummary summary,
            String rawJson,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            String modelUsed
    ) {}

    private record GeminiStructuredRawResult<T>(
            T payload,
            String rawJson,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            String modelUsed
    ) {}

    public GeminiCallResult generateWorkoutPlan(String systemPrompt, String userPrompt) {
        Optional<GeminiStructuredRawResult<GeminiStructuredWorkoutPlan>> result = callGeminiStructured(
                systemPrompt,
                userPrompt,
                buildResponseSchema(),
                GeminiStructuredWorkoutPlan.class
        );
        if (result.isPresent()) {
            GeminiStructuredRawResult<GeminiStructuredWorkoutPlan> res = result.get();
            return new GeminiCallResult(res.payload(), res.rawJson(), res.inputTokens(), res.outputTokens(), res.latencyMs(), res.modelUsed());
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI workout planner is temporarily unavailable. Please try again shortly."
        );
    }

    public Optional<GeminiSummaryResult> generatePerformanceSummary(String systemPrompt, String userPrompt) {
        return callGeminiStructured(
                systemPrompt,
                userPrompt,
                buildPerformanceSummaryResponseSchema(),
                GeminiStructuredPerformanceSummary.class
        ).map(res -> new GeminiSummaryResult(
                res.payload(),
                res.rawJson(),
                res.inputTokens(),
                res.outputTokens(),
                res.latencyMs(),
                res.modelUsed()
        ));
    }

    private String resolveModel() {
        String model = appProperties.getGemini().getModel();
        return (model == null || model.isBlank()) ? "gemini-flash-lite-latest" : model;
    }

    protected RestTemplate createRestTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(appProperties.getGemini().getTimeoutSeconds()))
                .build();
    }

    private <T> Optional<GeminiStructuredRawResult<T>> callGeminiStructured(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> schema,
            Class<T> responseType
    ) {
        String apiKey = appProperties.getGemini().getApiKey();
        String model = resolveModel();

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("placeholder")) {
            log.warn("GEMINI_API_KEY is not configured or empty. Skipping remote Gemini call for {}", responseType.getSimpleName());
            return Optional.empty();
        }

        long startTime = System.currentTimeMillis();
        String endpoint = String.format("%s/models/%s:generateContent",
                appProperties.getGemini().getBaseUrl().replaceAll("/+$", ""),
                model);

        GeminiRequestDto requestDto = buildRequest(systemPrompt, userPrompt, schema);

        RestTemplate restTemplate = createRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<GeminiRequestDto> entity = new HttpEntity<>(requestDto, headers);

        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                attempt++;
                log.info("Sending request to Gemini API (model: {}, target: {}, attempt: {})", model, responseType.getSimpleName(), attempt);
                ResponseEntity<GeminiResponseDto> response = restTemplate.exchange(
                        endpoint,
                        HttpMethod.POST,
                        entity,
                        GeminiResponseDto.class
                );

                long latency = System.currentTimeMillis() - startTime;
                GeminiResponseDto body = response.getBody();
                if (body == null || body.getFirstText() == null) {
                    throw new IllegalStateException("Empty response from Gemini API");
                }

                String jsonText = body.getFirstText();
                log.debug("Received structured JSON from Gemini ({}): {}", responseType.getSimpleName(), jsonText);

                T parsed = objectMapper.readValue(jsonText, responseType);

                int inputTokens = body.getUsageMetadata() != null && body.getUsageMetadata().getPromptTokenCount() != null
                        ? body.getUsageMetadata().getPromptTokenCount() : 0;
                int outputTokens = body.getUsageMetadata() != null && body.getUsageMetadata().getCandidatesTokenCount() != null
                        ? body.getUsageMetadata().getCandidatesTokenCount() : 0;

                log.info("Gemini API call succeeded in {}ms ({}). Tokens: in={}, out={}", latency, responseType.getSimpleName(), inputTokens, outputTokens);
                return Optional.of(new GeminiStructuredRawResult<>(parsed, jsonText, inputTokens, outputTokens, latency, model));

            } catch (HttpStatusCodeException ex) {
                lastException = ex;
                log.warn("Gemini HTTP error (status={}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                boolean retryable = ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS || ex.getStatusCode().is5xxServerError();
                if (retryable && attempt <= maxRetries) {
                    try {
                        Thread.sleep(1500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Gemini API call failed on attempt {}: {}", attempt, ex.getMessage());
                if (attempt <= maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            }
        }

        log.error("All Gemini API attempts failed for {}. Cause: {}",
                responseType.getSimpleName(),
                lastException != null ? lastException.getMessage() : "Unknown");
        return Optional.empty();
    }

    private GeminiRequestDto buildRequest(String systemPrompt, String userPrompt, Map<String, Object> schema) {
        GeminiRequestDto.GenerationConfig config = GeminiRequestDto.GenerationConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature(0.2)
                .maxOutputTokens(8192)
                .build();

        GeminiRequestDto.SystemInstruction systemInstruction = GeminiRequestDto.SystemInstruction.builder()
                .parts(List.of(new GeminiRequestDto.Part(systemPrompt)))
                .build();

        GeminiRequestDto.Content content = GeminiRequestDto.Content.builder()
                .role("user")
                .parts(List.of(new GeminiRequestDto.Part(userPrompt)))
                .build();

        return GeminiRequestDto.builder()
                .systemInstruction(systemInstruction)
                .contents(List.of(content))
                .generationConfig(config)
                .build();
    }

    public static Map<String, Object> buildPerformanceSummaryResponseSchema() {
        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("overall_assessment", Map.of("type", "STRING", "description", "2-3 sentence honest assessment grounded only in provided metrics"));
        rootProperties.put("strengths", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "description", "Observed strengths supported by metrics"));
        rootProperties.put("weaknesses", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "description", "Observed weaknesses or errors strictly from input"));
        rootProperties.put("recommendations", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "description", "Up to 3 actionable training/form recommendations"));
        rootProperties.put("next_session_focus", Map.of("type", "STRING", "description", "Specific actionable focus for the next session"));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "OBJECT");
        rootSchema.put("properties", rootProperties);
        rootSchema.put("required", List.of("overall_assessment", "strengths", "weaknesses", "recommendations", "next_session_focus"));
        return rootSchema;
    }

    public static Map<String, Object> buildResponseSchema() {
        Map<String, Object> exerciseProperties = new LinkedHashMap<>();
        exerciseProperties.put("exercise_id", Map.of("type", "INTEGER", "description", "Exact active catalog exercise ID"));
        exerciseProperties.put("exercise_name", Map.of("type", "STRING", "description", "Exact active catalog exercise name"));
        exerciseProperties.put("sets", Map.of("type", "INTEGER", "minimum", 1, "maximum", 10, "description", "Number of sets between 1 and 10 (typically 3 to 5)"));
        exerciseProperties.put("reps", Map.of("type", "INTEGER", "minimum", 1, "maximum", 100, "description", "Target reps for dynamic rep-based exercises; omit or null for static holds"));
        exerciseProperties.put("hold_seconds", Map.of("type", "INTEGER", "minimum", 1, "maximum", 300, "description", "Hold duration in seconds for static holds; omit or null for dynamic exercises"));
        exerciseProperties.put("rest_seconds", Map.of("type", "INTEGER", "minimum", 0, "maximum", 600, "description", "Rest duration between sets in seconds (0-600)"));
        exerciseProperties.put("notes", Map.of("type", "STRING", "description", "Concise form cues or progression tip"));

        Map<String, Object> exerciseSchema = new LinkedHashMap<>();
        exerciseSchema.put("type", "OBJECT");
        exerciseSchema.put("properties", exerciseProperties);
        exerciseSchema.put("required", List.of("exercise_id", "exercise_name", "sets", "rest_seconds"));

        Map<String, Object> exercisesArraySchema = new LinkedHashMap<>();
        exercisesArraySchema.put("type", "ARRAY");
        exercisesArraySchema.put("maxItems", 10);
        exercisesArraySchema.put("description", "4-8 exercises per training session (maximum 10), empty array for rest days, no duplicate exercise_ids");
        exercisesArraySchema.put("items", exerciseSchema);

        Map<String, Object> sessionProperties = new LinkedHashMap<>();
        sessionProperties.put("day_of_week", Map.of(
                "type", "STRING",
                "enum", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        ));
        sessionProperties.put("title", Map.of("type", "STRING", "description", "Name of the day session, e.g. Planche + Push or Rest Day"));
        sessionProperties.put("is_rest_day", Map.of("type", "BOOLEAN"));
        sessionProperties.put("exercises", exercisesArraySchema);

        Map<String, Object> sessionSchema = new LinkedHashMap<>();
        sessionSchema.put("type", "OBJECT");
        sessionSchema.put("properties", sessionProperties);
        sessionSchema.put("required", List.of("day_of_week", "title", "is_rest_day", "exercises"));

        Map<String, Object> sessionsArraySchema = new LinkedHashMap<>();
        sessionsArraySchema.put("type", "ARRAY");
        sessionsArraySchema.put("minItems", 7);
        sessionsArraySchema.put("maxItems", 7);
        sessionsArraySchema.put("description", "Exactly 7 unique weekday entries covering MONDAY through SUNDAY");
        sessionsArraySchema.put("items", sessionSchema);

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("plan_name", Map.of("type", "STRING", "description", "Descriptive title for the plan"));
        rootProperties.put("description", Map.of("type", "STRING", "description", "Summary of target focus and progression goals without AI provider branding"));
        rootProperties.put("duration_weeks", Map.of("type", "INTEGER", "minimum", 1, "maximum", 12, "description", "Program duration in weeks (1 to 12, typically 4)"));
        rootProperties.put("suggested_start_date", Map.of("type", "STRING", "description", "Suggested starting date in YYYY-MM-DD"));
        rootProperties.put("split_type", Map.of("type", "STRING", "enum", List.of("PPL", "UPPER_LOWER", "FULL_BODY", "CUSTOM")));
        rootProperties.put("sessions", sessionsArraySchema);

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "OBJECT");
        rootSchema.put("properties", rootProperties);
        rootSchema.put("required", List.of("plan_name", "duration_weeks", "sessions"));

        return rootSchema;
    }
}
