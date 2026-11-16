package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.*;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.videoanalysis.VideoAnalysisQuotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisSessionController {

    private final AnalysisSessionService sessionService;
    private final VideoAnalysisQuotaService videoAnalysisQuotaService;

    @PostMapping("/sessions")
    public ResponseEntity<AnalysisSessionResponseDto> createSession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateAnalysisSessionRequest request
    ) {
        requireProLiveAccess(currentUser);
        AnalysisSessionResponseDto response = sessionService.createSession(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{sessionId}/reps")
    public ResponseEntity<AnalysisSessionResponseDto> recordRep(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String sessionId,
            @Valid @RequestBody RepHistoryDto repDto
    ) {
        requireProLiveAccess(currentUser);
        AnalysisSessionResponseDto response = sessionService.recordRep(currentUser.getId(), sessionId, repDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<AnalysisSessionResponseDto> completeSession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String sessionId,
            @RequestBody CompleteAnalysisSessionRequest request
    ) {
        requireProLiveAccess(currentUser);
        AnalysisSessionResponseDto response = sessionService.completeSession(currentUser.getId(), sessionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/ai-summary")
    public ResponseEntity<AiPerformanceSummaryDto> generateAiSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String sessionId
    ) {
        requireProLiveAccess(currentUser);
        AiPerformanceSummaryDto summary = sessionService.generateOrGetAiSummary(currentUser.getId(), sessionId);
        return ResponseEntity.ok(summary);
    }

    private void requireProLiveAccess(UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        videoAnalysisQuotaService.requireProForLiveAnalysis(currentUser.getId());
    }

    @GetMapping({"/sessions", "/sessions/history"})
    public ResponseEntity<List<AnalysisSessionResponseDto>> getUserHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "exerciseType", required = false) String exerciseType
    ) {
        List<AnalysisSessionResponseDto> history = sessionService.getUserHistory(currentUser.getId(), exerciseType);
        return ResponseEntity.ok(history);
    }

    @GetMapping({"/sessions/summary", "/history/summary"})
    public ResponseEntity<AnalysisHistorySummaryDto> getHistorySummary(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        AnalysisHistorySummaryDto summary = sessionService.getUserHistorySummary(currentUser.getId());
        return ResponseEntity.ok(summary);
    }

    @GetMapping({"/sessions/{sessionId}", "/history/{sessionId}"})
    public ResponseEntity<AnalysisSessionResponseDto> getSessionDetails(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable String sessionId
    ) {
        AnalysisSessionResponseDto session = sessionService.getSessionDetails(currentUser.getId(), sessionId);
        return ResponseEntity.ok(session);
    }
}
