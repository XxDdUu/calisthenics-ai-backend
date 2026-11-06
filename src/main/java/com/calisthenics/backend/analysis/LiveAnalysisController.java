package com.calisthenics.backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/analysis/live")
@RequiredArgsConstructor
public class LiveAnalysisController {

    private static final long MAX_FRAME_BYTES = 2L * 1024L * 1024L;

    private final LiveAnalysisService liveAnalysisService;

    @GetMapping("/exercises")
    public JsonNode getSupportedExercises() {
        return liveAnalysisService.getSupportedExercises();
    }

    @PostMapping(value = "/frame", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<JsonNode>> analyzeFrame(
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank String exercise,
            @RequestParam("session_id") @NotBlank String sessionId,
            @RequestParam("frame_sequence") @Min(0) long frameSequence,
            @RequestParam("timestamp_ms") @Min(0) long timestampMs,
            @RequestParam(defaultValue = "false") boolean sideView
    ) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Camera frame is empty.");
        }
        if (file.getSize() > MAX_FRAME_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Camera frame exceeds 2 MiB.");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Camera frame must be an image.");
        }

        try {
            byte[] imageBytes = file.getBytes();
            return liveAnalysisService
                    .analyzeFrame(imageBytes, exercise, sessionId, frameSequence, timestampMs, sideView)
                    .thenApply(ResponseEntity::ok);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read camera frame.", exception);
        }
    }

    @PostMapping("/session/{sessionId}/reset")
    public JsonNode resetSession(@PathVariable @NotBlank String sessionId) {
        return liveAnalysisService.resetSession(sessionId);
    }

    @DeleteMapping("/session/{sessionId}")
    public JsonNode terminateSession(@PathVariable @NotBlank String sessionId) {
        return liveAnalysisService.terminateSession(sessionId);
    }
}
