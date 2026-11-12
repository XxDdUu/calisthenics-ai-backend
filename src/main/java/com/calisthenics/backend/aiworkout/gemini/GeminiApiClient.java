package com.calisthenics.backend.aiworkout.gemini;

import com.calisthenics.backend.adminai.dto.GeminiAdminAgentOutput;
import com.calisthenics.backend.aichat.dto.GeminiAssistantOutput;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiResponseDto;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredPerformanceSummary;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.config.AppProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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

    private record GeminiCallAttempt<T>(
            Optional<GeminiStructuredRawResult<T>> result,
            boolean nonRetryableClientError
    ) {}

    public GeminiCallResult generateWorkoutPlan(String systemPrompt, String userPrompt) {
        GeminiCallAttempt<GeminiStructuredWorkoutPlan> outcome = callGeminiStructured(
                systemPrompt,
                userPrompt,
                buildResponseSchema(),
                GeminiStructuredWorkoutPlan.class
        );
        if (outcome.result().isPresent()) {
            GeminiStructuredRawResult<GeminiStructuredWorkoutPlan> res = outcome.result().get();
            return new GeminiCallResult(res.payload(), res.rawJson(), res.inputTokens(), res.outputTokens(), res.latencyMs(), res.modelUsed());
        }
        if (outcome.nonRetryableClientError()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI workout service rejected the structured plan request. Please try rephrasing your prompt."
            );
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
        ).result().map(res -> new GeminiSummaryResult(
                res.payload(),
                res.rawJson(),
                res.inputTokens(),
                res.outputTokens(),
                res.latencyMs(),
                res.modelUsed()
        ));
    }

    public Optional<GeminiAssistantOutput> callAssistant(
            String systemPrompt,
            List<GeminiRequestDto.Content> conversationContents
    ) {
        return callGeminiConversation(
                systemPrompt,
                conversationContents,
                buildAssistantResponseSchema(),
                GeminiAssistantOutput.class
        ).result().map(GeminiStructuredRawResult::payload);
    }

    public Optional<GeminiAdminAgentOutput> callAdminAgent(
            String systemPrompt,
            List<GeminiRequestDto.Content> conversationContents
    ) {
        return callGeminiConversation(
                systemPrompt,
                conversationContents,
                buildAdminAgentResponseSchema(),
                GeminiAdminAgentOutput.class
        ).result().map(GeminiStructuredRawResult::payload);
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

    protected void sleepBeforeRetry(long delayMs) throws InterruptedException {
        if (delayMs > 0L) {
            Thread.sleep(delayMs);
        }
    }

    protected long computeJitterMs(long maxJitterMs) {
        if (maxJitterMs <= 0L) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(0L, maxJitterMs + 1L);
    }

    private <T> GeminiCallAttempt<T> callGeminiStructured(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> schema,
            Class<T> responseType
    ) {
        return callGeminiConversation(
                systemPrompt,
                List.of(GeminiRequestDto.Content.builder()
                        .role("user")
                        .parts(List.of(new GeminiRequestDto.Part(userPrompt)))
                        .build()),
                schema,
                responseType
        );
    }

    private <T> GeminiCallAttempt<T> callGeminiConversation(
            String systemPrompt,
            List<GeminiRequestDto.Content> conversationContents,
            Map<String, Object> schema,
            Class<T> responseType
    ) {
        String apiKey = appProperties.getGemini().getApiKey();
        String model = resolveModel();

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("placeholder")) {
            log.warn("GEMINI_API_KEY is not configured or empty. Skipping remote Gemini call for {}", responseType.getSimpleName());
            return new GeminiCallAttempt<>(Optional.empty(), false);
        }

        long startTime = System.currentTimeMillis();
        String endpoint = String.format("%s/models/%s:generateContent",
                appProperties.getGemini().getBaseUrl().replaceAll("/+$", ""),
                model);

        GeminiRequestDto requestDto = buildRequest(systemPrompt, conversationContents, schema);

        RestTemplate restTemplate = createRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<GeminiRequestDto> entity = new HttpEntity<>(requestDto, headers);

        int maxRetries = Math.min(3, Math.max(0, appProperties.getGemini().getMaxRetries()));
        int attempt = 0;
        Exception lastException = null;
        boolean nonRetryableClientError = false;

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
                return new GeminiCallAttempt<>(
                        Optional.of(new GeminiStructuredRawResult<>(parsed, jsonText, inputTokens, outputTokens, latency, model)),
                        false
                );

            } catch (HttpStatusCodeException ex) {
                lastException = ex;
                log.warn("Gemini HTTP error (status={}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                if (!isTransientHttpStatus(ex.getStatusCode())) {
                    nonRetryableClientError = ex.getStatusCode().is4xxClientError();
                    break;
                }
                if (attempt <= maxRetries) {
                    long delayMs = resolveRetryDelayMs(attempt, ex.getResponseHeaders());
                    if (!waitForRetry(delayMs)) {
                        break;
                    }
                    continue;
                }
                break;
            } catch (JsonProcessingException ex) {
                // Deterministic JSON deserialization mismatch: do not retry and waste API quota.
                lastException = ex;
                log.warn("Gemini returned non-deserializable JSON for {}: {}", responseType.getSimpleName(), ex.getMessage());
                break;
            } catch (ResourceAccessException ex) {
                lastException = ex;
                log.warn("Gemini network/timeout failure on attempt {}: {}", attempt, ex.getMessage());
                if (attempt <= maxRetries) {
                    long delayMs = resolveRetryDelayMs(attempt, null);
                    if (!waitForRetry(delayMs)) {
                        break;
                    }
                    continue;
                }
                break;
            } catch (Exception ex) {
                lastException = ex;
                log.warn("Gemini API call failed on attempt {}: {}", attempt, ex.getMessage());
                break;
            }
        }

        log.error("All Gemini API attempts failed for {}. Cause: {}",
                responseType.getSimpleName(),
                lastException != null ? lastException.getMessage() : "Unknown");
        return new GeminiCallAttempt<>(Optional.empty(), nonRetryableClientError);
    }

    private boolean isTransientHttpStatus(HttpStatusCode statusCode) {
        return statusCode == HttpStatus.TOO_MANY_REQUESTS
                || statusCode == HttpStatus.SERVICE_UNAVAILABLE
                || statusCode.is5xxServerError();
    }

    private boolean waitForRetry(long delayMs) {
        try {
            sleepBeforeRetry(delayMs);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private long resolveRetryDelayMs(int attemptNumber, HttpHeaders responseHeaders) {
        long maxRetryAfterMs = Math.max(0L, appProperties.getGemini().getMaxRetryAfterMs());
        OptionalLong retryAfter = parseRetryAfterMs(responseHeaders);
        if (retryAfter.isPresent()) {
            return Math.min(maxRetryAfterMs, Math.max(0L, retryAfter.getAsLong()));
        }
        long initialDelay = Math.max(50L, appProperties.getGemini().getInitialRetryDelayMs());
        long maxDelay = Math.max(initialDelay, appProperties.getGemini().getMaxRetryDelayMs());
        long expBase = Math.min(maxDelay, initialDelay * (1L << Math.min(attemptNumber - 1, 10)));
        long jitter = computeJitterMs(Math.max(1L, expBase / 2L));
        return Math.min(maxDelay, expBase + jitter);
    }

    private OptionalLong parseRetryAfterMs(HttpHeaders headers) {
        if (headers == null) {
            return OptionalLong.empty();
        }
        String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        String trimmed = raw.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return OptionalLong.of(Math.max(0L, seconds * 1000L));
        } catch (NumberFormatException ignored) {
            // Try RFC-1123 HTTP-date format next.
        }
        try {
            Instant retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            long deltaMs = Duration.between(Instant.now(), retryAt).toMillis();
            return OptionalLong.of(Math.max(0L, deltaMs));
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }
    }

    private GeminiRequestDto buildRequest(String systemPrompt, String userPrompt, Map<String, Object> schema) {
        return buildRequest(
                systemPrompt,
                List.of(GeminiRequestDto.Content.builder()
                        .role("user")
                        .parts(List.of(new GeminiRequestDto.Part(userPrompt)))
                        .build()),
                schema
        );
    }

    private GeminiRequestDto buildRequest(String systemPrompt, List<GeminiRequestDto.Content> contents, Map<String, Object> schema) {
        GeminiRequestDto.GenerationConfig config = GeminiRequestDto.GenerationConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(schema)
                .temperature(0.3)
                .maxOutputTokens(8192)
                .build();

        GeminiRequestDto.SystemInstruction systemInstruction = GeminiRequestDto.SystemInstruction.builder()
                .parts(List.of(new GeminiRequestDto.Part(systemPrompt)))
                .build();

        return GeminiRequestDto.builder()
                .systemInstruction(systemInstruction)
                .contents(contents)
                .generationConfig(config)
                .build();
    }

    public static Map<String, Object> buildAssistantResponseSchema() {
        Map<String, Object> prProperties = new LinkedHashMap<>();
        prProperties.put("exercise_name", Map.of("type", "STRING", "description", "Name of the exercise for the PR, e.g. Pull Up, Muscle Up, Weighted Dip"));
        prProperties.put("value", Map.of("type", "NUMBER", "description", "Numeric record value achieved (e.g. 15, 30.5)"));
        prProperties.put("unit", Map.of("type", "STRING", "description", "Unit of measurement, e.g. reps, kg, s"));
        prProperties.put("pr_type", Map.of("type", "STRING", "enum", List.of("MAX_REPS", "MAX_WEIGHT", "MAX_HOLD")));
        prProperties.put("is_ambiguous", Map.of("type", "BOOLEAN", "description", "True if critical PR details like exercise or value were missing"));
        prProperties.put("notes", Map.of("type", "STRING", "description", "Optional notes or form conditions"));

        Map<String, Object> prSchema = new LinkedHashMap<>();
        prSchema.put("type", "OBJECT");
        prSchema.put("properties", prProperties);

        Map<String, Object> planProperties = new LinkedHashMap<>();
        planProperties.put("prompt", Map.of("type", "STRING", "description", "Distilled workout plan generation prompt"));
        planProperties.put("fitness_level", Map.of("type", "STRING", "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED")));
        planProperties.put("duration_weeks", Map.of("type", "INTEGER", "description", "Program duration in weeks (1-12)"));

        Map<String, Object> planSchema = new LinkedHashMap<>();
        planSchema.put("type", "OBJECT");
        planSchema.put("properties", planProperties);

        Map<String, Object> progressProperties = new LinkedHashMap<>();
        progressProperties.put("exercise_name", Map.of("type", "STRING", "description", "Target exercise for progress analysis, if any"));
        progressProperties.put("focus_area", Map.of("type", "STRING", "description", "Focus area: plateaus, strength, consistency, or overall"));

        Map<String, Object> progressSchema = new LinkedHashMap<>();
        progressSchema.put("type", "OBJECT");
        progressSchema.put("properties", progressProperties);

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("intent", Map.of(
                "type", "STRING",
                "enum", List.of("WORKOUT_PLAN", "PR_RECORD", "PROGRESS_ANALYSIS", "GENERAL_CHAT"),
                "description", "Detected user intent"
        ));
        rootProperties.put("reply", Map.of(
                "type", "STRING",
                "description", "Personalized, motivational, and Markdown-formatted assistant response"
        ));
        rootProperties.put("pr_action", prSchema);
        rootProperties.put("workout_plan_action", planSchema);
        rootProperties.put("progress_analysis_action", progressSchema);

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "OBJECT");
        rootSchema.put("properties", rootProperties);
        rootSchema.put("required", List.of("intent", "reply"));

        return rootSchema;
    }

    public static Map<String, Object> buildAdminAgentResponseSchema() {
        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("intent", Map.of(
                "type", "STRING",
                "enum", List.of(
                        "PLATFORM_OVERVIEW",
                        "USER_TRAINING_ANALYTICS",
                        "EXERCISE_RECOMMENDATION",
                        "WORKOUT_PROGRAM_RECOMMENDATION",
                        "OPERATIONAL_INSIGHTS",
                        "GENERAL_ADMIN_ADVISORY"
                ),
                "description", "Detected administrative intent"
        ));
        rootProperties.put("reply", Map.of(
                "type", "STRING",
                "description", "Detailed, factual, Markdown-formatted administrative advisory response grounded strictly in the provided platform metrics"
        ));
        rootProperties.put("target_skill", Map.of(
                "type", "STRING",
                "description", "Target calisthenics skill if applicable (e.g. Planche, Front Lever, Back Lever, Handstand, Muscle-up, Human Flag)"
        ));
        rootProperties.put("target_level", Map.of(
                "type", "STRING",
                "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "ELITE"),
                "description", "Target difficulty level if applicable"
        ));
        rootProperties.put("generate_exercise_recommendations", Map.of(
                "type", "BOOLEAN",
                "description", "True if the admin is asking for exercise or progression additions"
        ));
        rootProperties.put("generate_workout_program", Map.of(
                "type", "BOOLEAN",
                "description", "True if the admin is asking for a draft workout program recommendation"
        ));
        rootProperties.put("generate_operational_insights", Map.of(
                "type", "BOOLEAN",
                "description", "True if the admin is asking for operational improvements or platform bottlenecks"
        ));
        rootProperties.put("suggested_follow_ups", Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING"),
                "description", "Up to 3 relevant administrative follow-up prompts"
        ));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "OBJECT");
        rootSchema.put("properties", rootProperties);
        rootSchema.put("required", List.of("intent", "reply"));

        return rootSchema;
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
