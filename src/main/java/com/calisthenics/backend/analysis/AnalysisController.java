package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.AnalysisResponse;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "AI pose estimation & biomechanical form analysis endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analyze single image frame for calisthenics pose estimation & form scoring")
    public ResponseEntity<AnalysisResponse> analyzeImage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestPart("file") MultipartFile file) {
        AnalysisResponse response = analysisService.analyzeAndSaveImage(currentUser.getId(), exerciseId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analyze video clip for hold duration, joint angles timeline, and form scoring")
    public ResponseEntity<AnalysisResponse> analyzeVideo(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestPart("file") MultipartFile file) {
        AnalysisResponse response = analysisService.analyzeAndSaveVideo(currentUser.getId(), exerciseId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get user's past AI form analysis history")
    public ResponseEntity<List<AnalysisResponse>> getUserHistory(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<AnalysisResponse> history = analysisService.getUserAnalysisHistory(currentUser.getId());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed analysis report by ID")
    public ResponseEntity<AnalysisResponse> getAnalysisById(@PathVariable Long id) {
        AnalysisResponse response = analysisService.getAnalysisById(id);
        return ResponseEntity.ok(response);
    }
}
