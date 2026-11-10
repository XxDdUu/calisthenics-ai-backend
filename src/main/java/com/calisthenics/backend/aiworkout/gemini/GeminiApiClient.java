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
        String model = resolveModel();
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
        return createFallbackResult(userPrompt, model);
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
        exerciseProperties.put("exercise_id", Map.of("type", "INTEGER", "description", "Existing exercise ID from catalog"));
        exerciseProperties.put("exercise_name", Map.of("type", "STRING", "description", "Exact exercise name from catalog"));
        exerciseProperties.put("sets", Map.of("type", "INTEGER", "description", "Number of sets, e.g. 3 or 4"));
        exerciseProperties.put("reps", Map.of("type", "INTEGER", "description", "Target reps for rep-based exercises"));
        exerciseProperties.put("hold_seconds", Map.of("type", "INTEGER", "description", "Hold duration in seconds for static holds"));
        exerciseProperties.put("rest_seconds", Map.of("type", "INTEGER", "description", "Rest duration between sets in seconds"));
        exerciseProperties.put("notes", Map.of("type", "STRING", "description", "Cues, form tips, or progression advice"));

        Map<String, Object> exerciseSchema = new LinkedHashMap<>();
        exerciseSchema.put("type", "OBJECT");
        exerciseSchema.put("properties", exerciseProperties);
        exerciseSchema.put("required", List.of("exercise_id", "exercise_name", "sets", "rest_seconds"));

        Map<String, Object> sessionProperties = new LinkedHashMap<>();
        sessionProperties.put("day_of_week", Map.of(
                "type", "STRING",
                "enum", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        ));
        sessionProperties.put("title", Map.of("type", "STRING", "description", "Name of the day session, e.g. Planche + Push"));
        sessionProperties.put("is_rest_day", Map.of("type", "BOOLEAN"));
        sessionProperties.put("exercises", Map.of(
                "type", "ARRAY",
                "items", exerciseSchema
        ));

        Map<String, Object> sessionSchema = new LinkedHashMap<>();
        sessionSchema.put("type", "OBJECT");
        sessionSchema.put("properties", sessionProperties);
        sessionSchema.put("required", List.of("day_of_week", "title", "is_rest_day", "exercises"));

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("plan_name", Map.of("type", "STRING", "description", "Descriptive title for the plan"));
        rootProperties.put("description", Map.of("type", "STRING", "description", "Summary of target focus and progression goals"));
        rootProperties.put("duration_weeks", Map.of("type", "INTEGER", "description", "Program duration in weeks (e.g. 4)"));
        rootProperties.put("suggested_start_date", Map.of("type", "STRING", "description", "Suggested starting date in YYYY-MM-DD"));
        rootProperties.put("split_type", Map.of("type", "STRING", "enum", List.of("PPL", "UPPER_LOWER", "FULL_BODY", "CUSTOM")));
        rootProperties.put("sessions", Map.of("type", "ARRAY", "items", sessionSchema));

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "OBJECT");
        rootSchema.put("properties", rootProperties);
        rootSchema.put("required", List.of("plan_name", "duration_weeks", "sessions"));

        return rootSchema;
    }

    private GeminiCallResult createFallbackResult(String userPrompt, String model) {
        String lower = userPrompt.toLowerCase();
        boolean hasPlanche = lower.contains("planche");
        boolean hasFrontLever = lower.contains("front lever") || lower.contains("lever") || lower.contains("pull");
        boolean is6Day = lower.contains("6-day") || lower.contains("6 day");
        boolean is5Day = lower.contains("5-day") || lower.contains("5 day");
        boolean is3Day = lower.contains("3-day") || lower.contains("3 day");

        String planName = is6Day ? "6-Day Calisthenics Strength Split"
                : (is5Day ? "5-Day Push/Pull/Legs Split"
                : (is3Day ? "3-Day Full Body Calisthenics Routine" : "Custom Calisthenics AI Program"));

        List<GeminiStructuredWorkoutPlan.GeminiSession> sessions = new ArrayList<>();

        // Monday
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("MONDAY")
                .title(hasPlanche ? "Planche + Push" : "Push Focus")
                .restDay(false)
                .exercises(List.of(
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(6L) // Planche
                                .exerciseName("Planche")
                                .sets(4)
                                .holdSeconds(12)
                                .restSeconds(150)
                                .notes("Keep arms locked (>170°), lean forward with scapula protracted")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(1L) // Push-Up
                                .exerciseName("Push-Up")
                                .sets(4)
                                .reps(12)
                                .restSeconds(90)
                                .notes("Full range of motion, maintain hollow body")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(4L) // Parallel Bar Dips
                                .exerciseName("Parallel Bar Dips")
                                .sets(3)
                                .reps(8)
                                .restSeconds(120)
                                .notes("Lower to 90 degrees elbow bend, press to full lockout")
                                .build()
                ))
                .build());

        // Tuesday
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("TUESDAY")
                .title(hasFrontLever ? "Front Lever + Pull" : "Pull Focus")
                .restDay(false)
                .exercises(List.of(
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(7L) // Front Lever
                                .exerciseName("Front Lever")
                                .sets(4)
                                .holdSeconds(10)
                                .restSeconds(150)
                                .notes("Depress and retract scapula, straight arm pull-down force")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(3L) // Pull-Up
                                .exerciseName("Pull-Up")
                                .sets(4)
                                .reps(8)
                                .restSeconds(120)
                                .notes("Chin cleanly over bar, dead hang at bottom")
                                .build()
                ))
                .build());

        // Wednesday
        boolean wedRest = is3Day;
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("WEDNESDAY")
                .title(wedRest ? "Rest & Recovery" : "Legs & Core")
                .restDay(wedRest)
                .exercises(wedRest ? Collections.emptyList() : List.of(
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(2L) // Bodyweight Squat
                                .exerciseName("Bodyweight Squat")
                                .sets(4)
                                .reps(20)
                                .restSeconds(90)
                                .notes("Deep squat below parallel, knees tracking over toes")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(9L) // L-Sit
                                .exerciseName("L-Sit")
                                .sets(3)
                                .holdSeconds(15)
                                .restSeconds(90)
                                .notes("Push tall through shoulders, legs locked horizontal")
                                .build()
                ))
                .build());

        // Thursday
        boolean thurRest = is3Day || is5Day;
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("THURSDAY")
                .title(thurRest ? "Active Recovery" : "Handstand & Skill")
                .restDay(thurRest)
                .exercises(thurRest ? Collections.emptyList() : List.of(
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(5L) // Handstand
                                .exerciseName("Handstand")
                                .sets(5)
                                .holdSeconds(20)
                                .restSeconds(90)
                                .notes("Elevate shoulders, fingers spread, maintain balance")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(8L) // Pike Push-Up
                                .exerciseName("Pike Push-Up")
                                .sets(3)
                                .reps(8)
                                .restSeconds(90)
                                .notes("Tripod head placement forward of hands")
                                .build()
                ))
                .build());

        // Friday
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("FRIDAY")
                .title(hasPlanche ? "Planche Strength" : "Upper Body Push")
                .restDay(false)
                .exercises(List.of(
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(6L) // Planche
                                .exerciseName("Planche")
                                .sets(4)
                                .holdSeconds(10)
                                .restSeconds(150)
                                .notes("Focus on max forward lean and scapular depression")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(4L) // Parallel Bar Dips
                                .exerciseName("Parallel Bar Dips")
                                .sets(4)
                                .reps(10)
                                .restSeconds(120)
                                .notes("Control eccentric phase, explosive drive")
                                .build()
                ))
                .build());

        // Saturday
        boolean satRest = is3Day || is5Day;
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("SATURDAY")
                .title(satRest ? "Rest Day" : (hasFrontLever ? "Front Lever Strength" : "Pull & Core"))
                .restDay(satRest)
                .exercises(satRest ? Collections.emptyList() : List.of(
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(7L) // Front Lever
                                .exerciseName("Front Lever")
                                .sets(4)
                                .holdSeconds(10)
                                .restSeconds(150)
                                .notes("Full body tension, posterior pelvic tilt")
                                .build(),
                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                .exerciseId(3L) // Pull-Up
                                .exerciseName("Pull-Up")
                                .sets(3)
                                .reps(8)
                                .restSeconds(120)
                                .notes("Strict form, no swinging")
                                .build()
                ))
                .build());

        // Sunday
        sessions.add(GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek("SUNDAY")
                .title("Rest & Regeneration")
                .restDay(true)
                .exercises(Collections.emptyList())
                .build());

        GeminiStructuredWorkoutPlan plan = GeminiStructuredWorkoutPlan.builder()
                .planName(planName)
                .description("Targeted calisthenics routine designed from prompt: " + userPrompt)
                .durationWeeks(4)
                .splitType(is6Day ? "CUSTOM" : (is5Day ? "PPL" : "FULL_BODY"))
                .sessions(sessions)
                .build();

        return new GeminiCallResult(plan, "{}", 120, 480, 250L, model + " (local-fallback)");
    }
}
