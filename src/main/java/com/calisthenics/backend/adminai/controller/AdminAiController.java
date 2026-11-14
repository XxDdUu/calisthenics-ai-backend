package com.calisthenics.backend.adminai.controller;

import com.calisthenics.backend.adminai.dto.*;
import com.calisthenics.backend.adminai.dto.data.*;
import com.calisthenics.backend.adminai.service.AdminAIService;
import com.calisthenics.backend.adminai.service.AdminConversationService;
import com.calisthenics.backend.adminai.service.AdminDataAccessService;
import com.calisthenics.backend.adminai.service.AdminToolRouter;
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
    private final AdminDataAccessService adminDataAccessService;
    private final AdminToolRouter adminToolRouter;
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

    @GetMapping("/analytics/users")
    @Operation(summary = "Get aggregated global statistics across all registered users")
    public ResponseEntity<AdminGlobalUserStatisticsResponse> getGlobalUserStatistics(
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getUserStatistics(range, startDate, endDate));
    }

    @GetMapping("/users")
    @Operation(summary = "List and filter all platform users with server-side pagination and privacy masking")
    public ResponseEntity<AdminPagedResult<AdminGlobalUserSummaryDto>> listGlobalUsers(
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) String fitnessGoal,
            @RequestParam(required = false) String preferredFrequency,
            @RequestParam(required = false) String activityStatus,
            @RequestParam(required = false) Integer inactiveDaysThreshold,
            @RequestParam(required = false) Double minCompletionRate,
            @RequestParam(required = false) Double maxCompletionRate,
            @RequestParam(required = false) String prProgressionStatus,
            @RequestParam(required = false) Integer stalePrDaysThreshold,
            @RequestParam(required = false) String coachRelationshipStatus,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String skillKeyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeEmail
    ) {
        AdminUserSegmentFilter filter = AdminUserSegmentFilter.builder()
                .searchQuery(searchQuery)
                .experienceLevel(experienceLevel)
                .fitnessGoal(fitnessGoal)
                .preferredFrequency(preferredFrequency)
                .activityStatus(activityStatus)
                .inactiveDaysThreshold(inactiveDaysThreshold)
                .minCompletionRate(minCompletionRate)
                .maxCompletionRate(maxCompletionRate)
                .prProgressionStatus(prProgressionStatus)
                .stalePrDaysThreshold(stalePrDaysThreshold)
                .coachRelationshipStatus(coachRelationshipStatus)
                .roleFilter(roleFilter)
                .skillKeyword(skillKeyword)
                .sortBy(sortBy)
                .range(range)
                .startDate(startDate)
                .endDate(endDate)
                .page(page)
                .size(size)
                .includeEmail(includeEmail)
                .build();
        return ResponseEntity.ok(adminDataAccessService.getAllUsers(filter));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Inspect comprehensive read-only dossier for a single user")
    public ResponseEntity<AdminUserDetailDossierResponse> getUserDetailDossier(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean includeEmail,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getUserDetails(userId, includeEmail, range, startDate, endDate));
    }

    @GetMapping("/users/{userId}/workouts")
    @Operation(summary = "Get read-only workout history and plans for a single user")
    public ResponseEntity<AdminUserWorkoutHistoryResponse> getUserWorkoutHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getUserWorkoutHistory(userId, range, startDate, endDate));
    }

    @GetMapping("/users/{userId}/personal-records")
    @Operation(summary = "Get read-only Personal Records, sharing, and coach feedback history for a single user")
    public ResponseEntity<AdminUserPersonalRecordsResponse> getUserPersonalRecords(
            @PathVariable Long userId,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getUserPersonalRecords(userId, range, startDate, endDate));
    }

    @GetMapping("/users/{userId}/progress")
    @Operation(summary = "Get read-only strength and workout consistency progression for a single user")
    public ResponseEntity<AdminUserProgressResponse> getUserProgress(
            @PathVariable Long userId,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getUserProgress(userId, range, startDate, endDate));
    }

    @GetMapping("/users/{userId}/activity")
    @Operation(summary = "Get privacy-safe chat, AI assistant, planner, and analysis usage metadata for a single user")
    public ResponseEntity<AdminUserActivityResponse> getUserActivity(
            @PathVariable Long userId,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getUserActivity(userId, range, startDate, endDate));
    }

    @GetMapping("/analytics/coaching")
    @Operation(summary = "Get aggregated coach-client relationships, invitations, assignments, and PR feedback metrics")
    public ResponseEntity<AdminCoachClientStatisticsResponse> getCoachClientStatistics(
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getCoachClientStatistics(range, startDate, endDate));
    }

    @GetMapping("/analytics/engagement")
    @Operation(summary = "Get platform engagement (DAU/WAU/MAU), experience-level comparisons, top skill improvers, and declining users")
    public ResponseEntity<AdminPlatformEngagementResponse> getPlatformEngagement(
            @RequestParam(required = false) String skillKeyword,
            @RequestParam(required = false) AdminAnalyticsRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(adminDataAccessService.getPlatformEngagement(skillKeyword, range, startDate, endDate));
    }

    @PostMapping("/analytics/segments")
    @Operation(summary = "Filter and analyze custom user segments with paginated drill-down results")
    public ResponseEntity<AdminUserSegmentsResponse> analyzeUserSegments(
            @RequestBody(required = false) AdminUserSegmentFilter filter
    ) {
        return ResponseEntity.ok(adminDataAccessService.analyzeUserSegments(filter));
    }

    @PostMapping("/tools/execute")
    @Operation(summary = "Invoke any authorized Admin Agent tool directly")
    public ResponseEntity<AdminToolExecuteResponse> executeAdminTool(
            @RequestBody AdminToolExecuteRequest request
    ) {
        return ResponseEntity.ok(adminToolRouter.executeSingleTool(request));
    }

    @PostMapping("/confirmations/{confirmationToken}")
    @Operation(summary = "Confirm or cancel a pending Getting Started campaign publish or unpublish action")
    public ResponseEntity<AdminConfirmationDecisionResponse> resolvePendingConfirmation(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable String confirmationToken,
            @RequestBody(required = false) AdminConfirmationDecisionRequest request
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.resolveConfirmation(adminId, confirmationToken, request));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get recent administrative AI and global data access audit logs")
    public ResponseEntity<List<AdminAiAuditLogResponse>> getAuditLogs(
            @AuthenticationPrincipal UserPrincipal currentAdmin
    ) {
        Long adminId = resolveAdminUserId(currentAdmin);
        return ResponseEntity.ok(adminAIService.getAuditLogs(adminId));
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
