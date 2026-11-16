package com.calisthenics.backend.analysis;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.videoanalysis.VideoAnalysisQuotaService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/analysis/live")
@RequiredArgsConstructor
public class LiveAnalysisController {

    private static final long MAX_FRAME_BYTES = 2L * 1024L * 1024L;

    private final LiveAnalysisService liveAnalysisService;
    private final VideoAnalysisQuotaService videoAnalysisQuotaService;

    @GetMapping("/exercises")
    public JsonNode getSupportedExercises() {
        return liveAnalysisService.getSupportedExercises();
    }

    @PostMapping(value = "/frame", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JsonNode> analyzeFrame(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "file", required = false) MultipartFile fileParam,
            @RequestPart(value = "file", required = false) MultipartFile filePart,
            @RequestParam @NotBlank String exercise,
            @RequestParam("session_id") @NotBlank String sessionId,
            @RequestParam(name = "frame_sequence", defaultValue = "0") @Min(0) long frameSequence,
            @RequestParam(name = "timestamp_ms", defaultValue = "0") @Min(0) long timestampMs,
            @RequestParam(name = "side_view", required = false) Boolean sideViewSnake,
            @RequestParam(name = "sideView", required = false) Boolean sideViewCamel,
            @RequestParam(name = "draw_overlay", required = false) Boolean drawOverlaySnake,
            @RequestParam(name = "drawOverlay", required = false) Boolean drawOverlayCamel,
            @RequestParam(name = "debug", defaultValue = "false") boolean debug
    ) {
        requireProLiveAccess(currentUser);

        MultipartFile file = fileParam != null ? fileParam : filePart;
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Camera frame is empty.");
        }
        if (file.getSize() > MAX_FRAME_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Camera frame exceeds 2 MiB.");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Camera frame must be an image.");
        }

        boolean sideView = Boolean.TRUE.equals(sideViewSnake) || Boolean.TRUE.equals(sideViewCamel);
        boolean drawOverlay = Boolean.TRUE.equals(drawOverlaySnake) || Boolean.TRUE.equals(drawOverlayCamel);

        try {
            byte[] imageBytes = file.getBytes();
            JsonNode result = liveAnalysisService.analyzeFrameSync(
                    imageBytes, exercise, sessionId, frameSequence, timestampMs, sideView, drawOverlay, debug
            );
            return ResponseEntity.ok(result);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read camera frame.", exception);
        }
    }

    @PostMapping("/session/{sessionId}/reset")
    public JsonNode resetSession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable @NotBlank String sessionId) {
        requireProLiveAccess(currentUser);
        return liveAnalysisService.resetSession(sessionId);
    }

    @DeleteMapping("/session/{sessionId}")
    public JsonNode terminateSession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable @NotBlank String sessionId) {
        requireProLiveAccess(currentUser);
        return liveAnalysisService.terminateSession(sessionId);
    }

    private void requireProLiveAccess(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        videoAnalysisQuotaService.requireProForLiveAnalysis(currentUser.getId());
    }
}
