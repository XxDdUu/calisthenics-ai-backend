package com.calisthenics.backend.adminai.controller;

import com.calisthenics.backend.adminai.dto.*;
import com.calisthenics.backend.adminai.service.AdminAIService;
import com.calisthenics.backend.adminai.service.AdminConversationService;
import com.calisthenics.backend.adminai.service.PlatformAnalyticsService;
import com.calisthenics.backend.adminai.service.TrainingAnalyticsService;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/admin/ai", "/api/admin/ai"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin AI Agent", description = "Dedicated AI Agent, analytics, and recommendation governance exclusively for administrators")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminAiController {

    private final AdminAIService adminAIService;
    private final AdminConversationService adminConversationService;
    private final PlatformAnalyticsService platformAnalyticsService;
    private final TrainingAnalyticsService trainingAnalyticsService;
    private final UserRepository userRepository;

    @PostMapping("/chat")
    @Operation(summary = "Send a prompt to the Admin AI Agent and receive grounded insights & recommendations")
    public ResponseEntity<AdminChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @Valid @RequestBody AdminChatRequest request
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.chat(adminId, request));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List persistent Admin AI conversations for the current administrator")
    public ResponseEntity<List<AdminConversationSummaryResponse>> getConversations(
            @AuthenticationPrincipal UserPrincipal currentAdmin
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminConversationService.getConversations(adminId));
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create a new Admin AI conversation")
    public ResponseEntity<AdminConversationSummaryResponse> createConversation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @Valid @RequestBody(required = false) CreateAdminConversationRequest request
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminConversationService.createConversation(adminId, request));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get Admin AI conversation detail with message history")
    public ResponseEntity<AdminConversationDetailResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable Long id
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminConversationService.getConversationDetail(adminId, id));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get message history for an Admin AI conversation")
    public ResponseEntity<List<AdminMessageResponse>> getConversationMessages(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable Long id
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminConversationService.getConversationMessages(adminId, id));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete an Admin AI conversation")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable Long id
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        adminConversationService.deleteConversation(adminId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analytics/overview")
    @Operation(summary = "Get platform overview analytics for administrators")
    public ResponseEntity<AdminPlatformOverviewResponse> getAnalyticsOverview(
            @RequestParam(required = false, defaultValue = "LAST_30_DAYS") AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(platformAnalyticsService.getOverview(range, startDate, endDate));
    }

    @GetMapping("/analytics/training")
    @Operation(summary = "Get platform-wide user and training analytics for administrators")
    public ResponseEntity<AdminTrainingAnalyticsResponse> getTrainingAnalytics(
            @RequestParam(required = false, defaultValue = "LAST_30_DAYS") AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(trainingAnalyticsService.getTrainingAnalytics(range, startDate, endDate));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "List Admin AI recommendations with optional type and status filters")
    public ResponseEntity<List<AdminRecommendationResponse>> getRecommendations(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @RequestParam(required = false) AdminRecommendationType type,
            @RequestParam(required = false) AdminRecommendationStatus status
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.getRecommendations(adminId, type, status));
    }

    @PostMapping("/recommendations")
    @Operation(summary = "Create or generate Admin AI recommendations (exercises, draft workout programs, operational insights)")
    public ResponseEntity<List<AdminRecommendationResponse>> createRecommendations(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @Valid @RequestBody CreateAdminRecommendationRequest request
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminAIService.createRecommendations(adminId, request));
    }

    @PatchMapping("/recommendations/{id}/approve")
    @Operation(summary = "Approve a recommendation and create the corresponding catalog exercise or draft workout plan")
    public ResponseEntity<AdminRecommendationResponse> approveRecommendation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReviewAdminRecommendationRequest request
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.approveRecommendation(adminId, id, request));
    }

    @PatchMapping("/recommendations/{id}/reject")
    @Operation(summary = "Reject an Admin AI recommendation")
    public ResponseEntity<AdminRecommendationResponse> rejectRecommendation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReviewAdminRecommendationRequest request
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.rejectRecommendation(adminId, id, request));
    }

    @PatchMapping("/recommendations/{id}/implement")
    @Operation(summary = "Confirm publication/implementation of an approved recommendation")
    public ResponseEntity<AdminRecommendationResponse> implementRecommendation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable Long id
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.confirmImplementation(adminId, id));
    }

    private Long resolveAdminUserId(UserPrincipal principal) {
        if (principal != null && principal.getId() != null) {
            return principal.getId();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            User byEmail = userRepository.findByEmailIgnoreCase(auth.getName()).orElse(null);
            if (byEmail != null) {
                return byEmail.getId();
            }
        }
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                .map(User::getId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrator account required"));
    }
}
