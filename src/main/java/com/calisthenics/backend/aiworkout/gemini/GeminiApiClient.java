package com.calisthenics.backend.aiworkout.gemini;

import com.calisthenics.backend.adminai.dto.GeminiAdminAgentOutput;
import com.calisthenics.backend.adminai.dto.data.GeminiAdminToolPlan;
import com.calisthenics.backend.adminai.dto.data.GeminiBulkExerciseBatchOutput;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
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
    private volatile RestTemplate pooledRestTemplate;
    private volatile int pooledTimeoutSeconds = -1;

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

    public Optional<GeminiAdminToolPlan> planAdminTools(
            String systemPrompt,
            List<GeminiRequestDto.Content> conversationContents
    ) {
        return callGeminiConversation(
                systemPrompt,
                conversationContents,
                buildAdminToolPlanSchema(),
                GeminiAdminToolPlan.class
        ).result().map(GeminiStructuredRawResult::payload);
    }

    private String resolveModel() {
        String model = appProperties.getGemini().getModel();
        return (model == null || model.isBlank()) ? "gemini-flash-lite-latest" : model;
    }

    protected RestTemplate createRestTemplate() {
        int timeoutSec = Math.max(10, appProperties.getGemini().getTimeoutSeconds());
        RestTemplate existing = pooledRestTemplate;
        if (existing != null && pooledTimeoutSeconds == timeoutSec) {
            return existing;
        }
        synchronized (this) {
            if (pooledRestTemplate != null && pooledTimeoutSeconds == timeoutSec) {
                return pooledRestTemplate;
            }
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSec));
            RestTemplate built = new RestTemplateBuilder()
                    .requestFactory(() -> requestFactory)
                    .build();
            pooledRestTemplate = built;
            pooledTimeoutSeconds = timeoutSec;
            return built;
        }
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
        planProperties.put("prompt", Map.of("type", "STRING", "description", "Distilled workout plan generation or query prompt"));
        planProperties.put("fitness_level", Map.of("type", "STRING", "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED")));
        planProperties.put("duration_weeks", Map.of("type", "INTEGER", "description", "Program duration in weeks (1-12)"));
        planProperties.put("tool_name", Map.of(
                "type", "STRING",
                "enum", List.of(
                        "get_my_workout_plan",
                        "get_weekly_workout_schedule",
                        "get_workout_day_details",
                        "get_exercise_details",
                        "create_workout_plan",
                        "update_workout_plan",
                        "assign_workout_plan",
                        "add_exercise_to_workout",
                        "remove_exercise_from_workout",
                        "move_exercise_between_days",
                        "regenerate_workout_plan",
                        "recommend_exercises",
                        "consult_workout_plan"
                ),
                "description", "Read-only/advisory tools for view, recommendation, and consultation requests; write tools only for explicit create/modify/replace requests"
        ));
        planProperties.put("day_of_week", Map.of(
                "type", "STRING",
                "enum", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY", "TODAY", "NEXT"),
                "description", "Target weekday explicitly requested by the user"
        ));
        planProperties.put("source_day_of_week", Map.of(
                "type", "STRING",
                "enum", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                "description", "Source weekday when moving an exercise from one day to another"
        ));
        planProperties.put("exercise_name", Map.of(
                "type", "STRING",
                "description", "Exact exercise name requested by the user"
        ));
        planProperties.put("detailed", Map.of("type", "BOOLEAN", "description", "True when user asks for full details or instructions"));

        Map<String, Object> planSchema = new LinkedHashMap<>();
        planSchema.put("type", "OBJECT");
        planSchema.put("properties", planProperties);

        Map<String, Object> progressProperties = new LinkedHashMap<>();
        progressProperties.put("exercise_name", Map.of("type", "STRING", "description", "Target exercise for progress analysis, if any"));
        progressProperties.put("focus_area", Map.of("type", "STRING", "description", "Focus area: plateaus, strength, consistency, or overall"));

        Map<String, Object> progressSchema = new LinkedHashMap<>();
        progressSchema.put("type", "OBJECT");
        progressSchema.put("properties", progressProperties);

        Map<String, Object> profileProperties = new LinkedHashMap<>();
        profileProperties.put("display_name", Map.of("type", "STRING", "description", "New user display name or full name"));
        profileProperties.put("bio", Map.of("type", "STRING", "description", "New user bio or profile description"));
        profileProperties.put("height_cm", Map.of("type", "NUMBER", "description", "Height in centimeters (e.g. 175.0)"));
        profileProperties.put("weight_kg", Map.of("type", "NUMBER", "description", "Weight in kilograms (e.g. 76.0)"));
        profileProperties.put("experience_level", Map.of("type", "STRING", "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), "description", "Experience level"));
        profileProperties.put("training_experience", Map.of("type", "STRING", "description", "Training history or experience summary"));
        profileProperties.put("primary_goals", Map.of("type", "STRING", "description", "Fitness goals (e.g. Straddle Planche, Muscle-up, Handstand)"));
        profileProperties.put("training_interests", Map.of("type", "STRING", "description", "Preferred training style, focus areas, or interests"));
        profileProperties.put("preferred_frequency", Map.of("type", "STRING", "description", "Preferred workout frequency (e.g. 3-4 days/week)"));
        profileProperties.put("coaching_language", Map.of("type", "STRING", "description", "Preferred coaching language"));
        profileProperties.put("gender", Map.of("type", "STRING", "enum", List.of("MALE", "FEMALE", "OTHER"), "description", "User gender"));
        profileProperties.put("is_ambiguous", Map.of("type", "BOOLEAN", "description", "True if request is ambiguous and needs clarification"));
        profileProperties.put("clarification_question", Map.of("type", "STRING", "description", "Clarification question when request is ambiguous"));

        Map<String, Object> profileSchema = new LinkedHashMap<>();
        profileSchema.put("type", "OBJECT");
        profileSchema.put("properties", profileProperties);

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("intent", Map.of(
                "type", "STRING",
                "enum", List.of(
                        "WORKOUT_PLAN",
                        "WORKOUT_PLAN_VIEW",
                        "WORKOUT_DAY_VIEW",
                        "EXERCISE_DETAILS_VIEW",
                        "WORKOUT_PLAN_UPDATE",
                        "EXERCISE_RECOMMENDATION",
                        "WORKOUT_CONSULTATION",
                        "PR_RECORD",
                        "PROGRESS_ANALYSIS",
                        "PROFILE_UPDATE",
                        "GENERAL_CHAT"
                ),
                "description", "Detected user intent"
        ));
        rootProperties.put("reply", Map.of(
                "type", "STRING",
                "description", "Personalized, motivational, and Markdown-formatted assistant response"
        ));
        rootProperties.put("pr_action", prSchema);
        rootProperties.put("workout_plan_action", planSchema);
        rootProperties.put("progress_analysis_action", progressSchema);
        rootProperties.put("profile_update_action", profileSchema);

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "OBJECT");
        rootSchema.put("properties", rootProperties);
        rootSchema.put("required", List.of("intent", "reply"));

        return rootSchema;
    }

    public static Map<String, Object> buildAdminToolPlanSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("intent", Map.of(
                "type", "STRING",
                "enum", List.of(
                        "PLATFORM_OVERVIEW",
                        "USER_TRAINING_ANALYTICS",
                        "EXERCISE_RECOMMENDATION",
                        "EXERCISE_DRAFT_CREATION",
                        "BULK_EXERCISE_GENERATION",
                        "WORKOUT_PROGRAM_RECOMMENDATION",
                        "OPERATIONAL_INSIGHTS",
                        "GENERAL_ADMIN_ADVISORY",
                        "USER_STATISTICS_AND_SEARCH",
                        "USER_DRILL_DOWN",
                        "USER_SEGMENTATION_ANALYSIS",
                        "COACH_CLIENT_ANALYTICS",
                        "PLATFORM_ENGAGEMENT_COMPARISON",
                        "GETTING_STARTED_PROGRAM_MANAGEMENT",
                        "CAMPAIGN_MANAGEMENT",
                        "CAMPAIGN_ANALYTICS",
                        "PRO_MANAGEMENT",
                        "CONFIRMATION_REQUIRED"
                ),
                "description", "Primary administrative intent"
        ));
        props.put("tools", Map.of(
                "type", "ARRAY",
                "items", Map.of(
                        "type", "STRING",
                        "enum", List.of(
                                "get_user_statistics",
                                "get_all_users",
                                "get_user_details",
                                "get_user_workout_history",
                                "get_user_personal_records",
                                "get_user_progress",
                                "get_user_activity",
                                "get_coach_client_statistics",
                                "get_platform_engagement",
                                "analyze_user_segments",
                                "generate_admin_recommendations",
                                "create_exercise_drafts",
                                "bulk_generate_exercises",
                                "createProgram",
                                "updateProgram",
                                "previewProgram",
                                "publishProgram",
                                "unpublishProgram",
                                "configureAudience",
                                "getActiveCampaigns",
                                "getCampaignAnalytics",
                                "grantPro",
                                "revokePro",
                                "checkProStatus"
                        )
                ),
                "description", "Ordered list of authorized admin tools to invoke before answering"
        ));
        props.put("target_user_id", Map.of("type", "INTEGER", "description", "Target numeric user ID if inspecting a specific user"));
        props.put("target_user_query", Map.of("type", "STRING", "description", "Target user full name or search query if inspecting a specific user"));
        props.put("skill_keyword", Map.of("type", "STRING", "description", "Current message's calisthenics skill keyword only if explicitly requested in the latest turn (do not reuse stale skills from prior turns)"));
        props.put("experience_level", Map.of("type", "STRING", "description", "Experience level filter such as BEGINNER, INTERMEDIATE, ADVANCED, ELITE"));
        props.put("fitness_goal", Map.of("type", "STRING", "description", "Fitness goal filter keyword"));
        props.put("activity_status", Map.of("type", "STRING", "enum", List.of("ACTIVE", "AT_RISK", "INACTIVE"), "description", "User workout activity status filter"));
        props.put("inactive_days_threshold", Map.of("type", "INTEGER", "description", "Days threshold for inactivity (e.g. 14)"));
        props.put("pr_progression_status", Map.of("type", "STRING", "enum", List.of("IMPROVING", "STALE", "NONE"), "description", "PR progression filter"));
        props.put("coach_relationship_status", Map.of(
                "type", "STRING",
                "enum", List.of("HAS_ACTIVE_COACH", "PENDING_INVITATION", "NO_COACH", "TERMINATED", "COACH_ROLE"),
                "description", "Coach-client relationship status filter"
        ));
        props.put("min_completion_rate", Map.of("type", "NUMBER", "description", "Minimum workout completion rate percentage (0-100)"));
        props.put("max_completion_rate", Map.of("type", "NUMBER", "description", "Maximum workout completion rate percentage (0-100)"));
        props.put("range", Map.of("type", "STRING", "enum", List.of("TODAY", "LAST_7_DAYS", "LAST_30_DAYS", "CUSTOM"), "description", "Analytics time range"));
        props.put("start_date", Map.of("type", "STRING", "description", "Optional custom start date YYYY-MM-DD"));
        props.put("end_date", Map.of("type", "STRING", "description", "Optional custom end date YYYY-MM-DD"));
        props.put("include_email", Map.of("type", "BOOLEAN", "description", "Set true only when admin explicitly asks for an authorized user email lookup"));
        props.put("program_id", Map.of("type", "INTEGER", "description", "Target Getting Started program ID when specified"));
        props.put("program_title", Map.of("type", "STRING", "description", "Title for a Getting Started program when creating or renaming"));
        props.put("program_instruction", Map.of("type", "STRING", "description", "Detailed Getting Started workout generation or editing instruction"));
        props.put("target_audience", Map.of(
                "type", "STRING",
                "enum", List.of("ALL_ATHLETES", "LAST_30_DAYS", "LAST_14_DAYS"),
                "description", "Getting Started campaign target audience segment"
        ));
        props.put("target_user_identifier", Map.of(
                "type", "STRING",
                "description", "Exact email, username/display name, or user ID of the target user for PRO entitlement actions"
        ));
        props.put("pro_action_reason", Map.of(
                "type", "STRING",
                "description", "Optional admin reason for granting or revoking PRO entitlement"
        ));
        List<String> allCategories = List.of(
                "PUSH", "PULL", "CORE", "LEGS", "SKILL", "ISOMETRIC",
                "FLEXIBILITY", "MOBILITY", "CHEST", "BACK", "SHOULDERS",
                "BICEPS", "TRICEPS", "GLUTES", "FULL_BODY", "CARDIO"
        );
        props.put("exercise_category", Map.of(
                "type", "STRING",
                "enum", allCategories,
                "description", "Requested exercise category from the latest admin prompt when creating or recommending exercises"
        ));
        props.put("training_type", Map.of(
                "type", "STRING",
                "enum", List.of("CALISTHENICS", "GYM", "BOTH"),
                "description", "Training discipline requested for bulk or single exercise generation"
        ));
        props.put("bulk_categories", Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING", "enum", allCategories),
                "description", "Target categories for bulk exercise generation"
        ));
        props.put("bulk_focus", Map.of(
                "type", "STRING",
                "description", "Specific anatomical or training focus for bulk generation"
        ));
        props.put("requested_count", Map.of(
                "type", "INTEGER",
                "description", "Number of exercises explicitly requested in the latest admin prompt"
        ));

        Map<String, Object> draftItemProps = new LinkedHashMap<>();
        draftItemProps.put("name", Map.of("type", "STRING", "description", "Exact requested or recommended exercise name"));
        draftItemProps.put("code", Map.of("type", "STRING", "description", "Uppercase underscore identifier slug, max 50 chars"));
        draftItemProps.put("category", Map.of("type", "STRING", "enum", allCategories, "description", "Strict movement category matching the user request"));
        draftItemProps.put("difficulty", Map.of("type", "STRING", "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "ELITE"), "description", "Difficulty level matching the user request"));
        draftItemProps.put("description", Map.of("type", "STRING", "description", "Concise technical summary of the exercise"));
        draftItemProps.put("instructions", Map.of("type", "STRING", "description", "Clear step-by-step execution instructions"));
        draftItemProps.put("target_muscles", Map.of("type", "STRING", "description", "Primary and secondary target muscle groups"));
        draftItemProps.put("equipment", Map.of("type", "STRING", "description", "Required equipment such as Floor, Box, Bench, Pull-Up Bar, Partner"));
        draftItemProps.put("target_skill", Map.of("type", "STRING", "description", "Associated training focus or skill"));
        Map<String, Object> draftItemSchema = new LinkedHashMap<>();
        draftItemSchema.put("type", "OBJECT");
        draftItemSchema.put("properties", draftItemProps);
        draftItemSchema.put("required", List.of("name", "code", "category", "difficulty", "description", "instructions", "target_muscles", "equipment"));
        props.put("exercise_drafts", Map.of(
                "type", "ARRAY",
                "items", draftItemSchema,
                "description", "Exact exercise draft specifications matching the latest user prompt when create_exercise_drafts is invoked"
        ));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "OBJECT");
        root.put("properties", props);
        root.put("required", List.of("intent", "tools"));
        return root;
    }

    public static Map<String, Object> buildAdminAgentResponseSchema() {
        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("intent", Map.of(
                "type", "STRING",
                "enum", List.of(
                        "PLATFORM_OVERVIEW",
                        "USER_TRAINING_ANALYTICS",
                        "EXERCISE_RECOMMENDATION",
                        "EXERCISE_DRAFT_CREATION",
                        "BULK_EXERCISE_GENERATION",
                        "WORKOUT_PROGRAM_RECOMMENDATION",
                        "OPERATIONAL_INSIGHTS",
                        "GENERAL_ADMIN_ADVISORY",
                        "USER_STATISTICS_AND_SEARCH",
                        "USER_DRILL_DOWN",
                        "USER_SEGMENTATION_ANALYSIS",
                        "COACH_CLIENT_ANALYTICS",
                        "PLATFORM_ENGAGEMENT_COMPARISON",
                        "GETTING_STARTED_PROGRAM_MANAGEMENT",
                        "CAMPAIGN_MANAGEMENT",
                        "CAMPAIGN_ANALYTICS",
                        "PRO_MANAGEMENT",
                        "CONFIRMATION_REQUIRED"
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
        rootProperties.put("generate_recommendations", Map.of(
                "type", "BOOLEAN",
                "description", "True if proactive administrative recommendations should be generated from global data"
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

    public boolean isConfigured() {
        String apiKey = appProperties.getGemini().getApiKey();
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("placeholder");
    }

    public Optional<GeminiBulkExerciseBatchOutput> generateBulkExerciseBatch(
            String systemPrompt,
            String userPrompt
    ) {
        return callGeminiStructured(
                systemPrompt,
                userPrompt,
                buildBulkExerciseBatchSchema(),
                GeminiBulkExerciseBatchOutput.class
        ).result().map(GeminiStructuredRawResult::payload);
    }

    public static Map<String, Object> buildBulkExerciseBatchSchema() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("name", Map.of("type", "STRING"));
        itemProps.put("category", Map.of("type", "STRING", "enum", List.of(
                "PUSH", "PULL", "CORE", "LEGS", "SKILL", "ISOMETRIC",
                "FLEXIBILITY", "MOBILITY", "CHEST", "BACK", "SHOULDERS",
                "BICEPS", "TRICEPS", "GLUTES", "FULL_BODY", "CARDIO"
        )));
        itemProps.put("training_type", Map.of("type", "STRING", "enum", List.of("CALISTHENICS", "GYM", "BOTH")));
        itemProps.put("difficulty", Map.of("type", "STRING", "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "ELITE")));
        itemProps.put("exercise_type", Map.of("type", "STRING"));
        itemProps.put("target_muscles", Map.of("type", "STRING"));
        itemProps.put("secondary_muscles", Map.of("type", "STRING"));
        itemProps.put("equipment", Map.of("type", "STRING"));
        itemProps.put("recommended_sets", Map.of("type", "INTEGER"));
        itemProps.put("recommended_reps", Map.of("type", "STRING"));
        itemProps.put("recommended_rest_seconds", Map.of("type", "INTEGER"));
        itemProps.put("description", Map.of("type", "STRING"));
        itemProps.put("instructions", Map.of("type", "STRING"));
        itemProps.put("common_mistakes", Map.of("type", "STRING"));
        itemProps.put("safety_considerations", Map.of("type", "STRING"));
        itemProps.put("progression_notes", Map.of("type", "STRING"));
        itemProps.put("aliases", Map.of("type", "STRING"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "OBJECT");
        itemSchema.put("properties", itemProps);
        itemSchema.put("required", List.of(
                "name", "category", "training_type", "difficulty",
                "target_muscles", "equipment", "description", "instructions"
        ));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "OBJECT");
        root.put("properties", Map.of(
                "exercises", Map.of("type", "ARRAY", "items", itemSchema)
        ));
        root.put("required", List.of("exercises"));
        return root;
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
