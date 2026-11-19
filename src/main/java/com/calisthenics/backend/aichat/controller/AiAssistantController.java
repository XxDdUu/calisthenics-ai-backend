package com.calisthenics.backend.aichat.controller;

import com.calisthenics.backend.aichat.dto.*;
import com.calisthenics.backend.aichat.dto.preview.CreateRecommendationPreviewRequest;
import com.calisthenics.backend.aichat.dto.preview.WorkoutPlanChangePreviewActionResponse;
import com.calisthenics.backend.aichat.dto.preview.WorkoutPlanChangePreviewResponse;
import com.calisthenics.backend.aichat.service.AiAssistantService;
import com.calisthenics.backend.aichat.service.WorkoutPlanChangePreviewService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
@Tag(name = "AI Assistant Chat", description = "Personal Calisthenics AI Assistant conversation endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    @GetMapping
    @Operation(summary = "Get all AI conversations for the current user")
    public ResponseEntity<List<AiConversationSummaryResponse>> getConversations(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(aiAssistantService.getUserConversations(currentUser.getId()));
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the latest AI conversation summary for current user (for Chat screen header)")
    public ResponseEntity<AiConversationSummaryResponse> getLatestConversation(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        AiConversationSummaryResponse summary = aiAssistantService.getLatestConversationSummary(currentUser.getId());
        return ResponseEntity.ok(summary);
    }

    @PostMapping
    @Operation(summary = "Create a new AI conversation")
    public ResponseEntity<AiConversationSummaryResponse> createConversation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody(required = false) CreateAiConversationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiAssistantService.createConversation(currentUser.getId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific AI conversation with message history")
    public ResponseEntity<AiConversationDetailResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(aiAssistantService.getConversation(currentUser.getId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an AI conversation")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id
    ) {
        aiAssistantService.deleteConversation(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a message to an AI conversation and receive AI response")
    public ResponseEntity<AiMessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SendAiMessageRequest request
    ) {
        return ResponseEntity.ok(aiAssistantService.sendMessage(currentUser.getId(), id, request));
    }

    @PostMapping("/messages")
    @Operation(summary = "Send a message to the active AI conversation (or auto-create one)")
    public ResponseEntity<AiMessageResponse> sendQuickMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody SendAiMessageRequest request
    ) {
        return ResponseEntity.ok(aiAssistantService.sendMessageToActiveConversation(currentUser.getId(), request));
    }

    @GetMapping("/{id}/previews/pending")
    @Operation(summary = "Get the active pending workout plan change preview for a conversation")
    public ResponseEntity<WorkoutPlanChangePreviewResponse> getPendingWorkoutPreview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id
    ) {
        WorkoutPlanChangePreviewResponse pending = aiAssistantService.getPendingWorkoutPreview(currentUser.getId(), id);
        if (pending == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/previews/{previewId}/confirm")
    @Operation(summary = "Confirm and atomically apply a pending workout plan change preview")
    public ResponseEntity<WorkoutPlanChangePreviewActionResponse> confirmWorkoutPreview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @PathVariable String previewId
    ) {
        WorkoutPlanChangePreviewService.PreviewActionOutcome outcome =
                aiAssistantService.confirmWorkoutPreview(currentUser.getId(), id, previewId);
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.response());
    }

    @PostMapping("/{id}/previews/{previewId}/cancel")
    @Operation(summary = "Cancel and discard a pending workout plan change preview")
    public ResponseEntity<WorkoutPlanChangePreviewActionResponse> cancelWorkoutPreview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @PathVariable String previewId
    ) {
        WorkoutPlanChangePreviewService.PreviewActionOutcome outcome =
                aiAssistantService.cancelWorkoutPreview(currentUser.getId(), id, previewId);
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.response());
    }

    @PostMapping("/{id}/recommendations/preview")
    @Operation(summary = "Generate a workout plan change preview from selected exercise recommendations")
    public ResponseEntity<AiMessageResponse> createRecommendationsPreview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody CreateRecommendationPreviewRequest request
    ) {
        return ResponseEntity.ok(
                aiAssistantService.createRecommendationsPreview(currentUser.getId(), id, request));
    }
}
