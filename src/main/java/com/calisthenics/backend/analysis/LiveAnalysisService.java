package com.calisthenics.backend.analysis;

import com.calisthenics.backend.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
public class LiveAnalysisService {

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;
    @Qualifier("liveAnalysisExecutor")
    private final Executor liveAnalysisExecutor;

    public JsonNode getSupportedExercises() {
        return get("/api/v1/analysis/exercises");
    }

    public JsonNode analyzeFrameSync(
            byte[] imageBytes,
            String exercise,
            String sessionId,
            long frameSequence,
            long timestampMs,
            boolean sideView,
            boolean drawOverlay,
            boolean debug
    ) {
        return postFrame(imageBytes, exercise, sessionId, frameSequence, timestampMs, sideView, drawOverlay, debug);
    }

    public CompletableFuture<JsonNode> analyzeFrame(
            byte[] imageBytes,
            String exercise,
            String sessionId,
            long frameSequence,
            long timestampMs,
            boolean sideView
    ) {
        return analyzeFrame(imageBytes, exercise, sessionId, frameSequence, timestampMs, sideView, false, false);
    }

    public CompletableFuture<JsonNode> analyzeFrame(
            byte[] imageBytes,
            String exercise,
            String sessionId,
            long frameSequence,
            long timestampMs,
            boolean sideView,
            boolean drawOverlay,
            boolean debug
    ) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> postFrame(imageBytes, exercise, sessionId, frameSequence, timestampMs, sideView, drawOverlay, debug),
                    liveAnalysisExecutor
            );
        } catch (RejectedExecutionException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Live analysis is at capacity. Retry with the next camera frame.",
                    exception
            );
        }
    }

    public JsonNode resetSession(String sessionId) {
        return restTemplate.postForObject(
                serviceUrl("/api/v1/analysis/live/session/" + sessionId + "/reset"),
                null,
                JsonNode.class
        );
    }

    public JsonNode terminateSession(String sessionId) {
        return restTemplate.exchange(
                serviceUrl("/api/v1/analysis/live/session/" + sessionId),
                org.springframework.http.HttpMethod.DELETE,
                HttpEntity.EMPTY,
                JsonNode.class
        ).getBody();
    }

    private JsonNode postFrame(
            byte[] imageBytes,
            String exercise,
            String sessionId,
            long frameSequence,
            long timestampMs,
            boolean sideView,
            boolean drawOverlay,
            boolean debug
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_JPEG);
        fileHeaders.setContentDispositionFormData("file", "live-frame.jpg");
        ByteArrayResource image = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "live-frame.jpg";
            }
        };
        body.add("file", new HttpEntity<>(image, fileHeaders));
        body.add("exercise", exercise);
        body.add("session_id", sessionId);
        body.add("frame_sequence", Long.toString(frameSequence));
        body.add("timestamp_ms", Long.toString(timestampMs));
        body.add("side_view", Boolean.toString(sideView));
        body.add("draw_overlay", Boolean.toString(drawOverlay));
        body.add("debug", Boolean.toString(debug));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            return restTemplate.postForObject(
                    serviceUrl("/api/v1/analysis/live/frame"),
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI live analysis timed out.",
                    exception
            );
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI live analysis rejected the frame.",
                    exception
            );
        }
    }

    private JsonNode get(String path) {
        try {
            return restTemplate.getForObject(serviceUrl(path), JsonNode.class);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI service timed out.", exception);
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service request failed.", exception);
        }
    }

    private URI serviceUrl(String path) {
        String baseUrl = appProperties.getAiService().getUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI service URL is not configured.");
        }
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl + path).build().toUri();
    }
}
