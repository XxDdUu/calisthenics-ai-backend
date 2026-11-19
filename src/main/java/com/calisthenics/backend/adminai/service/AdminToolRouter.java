package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.dto.AdminUserResponse;
import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.OperationalRecommendationPayload;
import com.calisthenics.backend.adminai.dto.data.*;
import com.calisthenics.backend.adminai.dto.data.AdminPlatformEngagementResponse.ExerciseUsageItem;
import com.calisthenics.backend.adminai.dto.data.AdminPlatformEngagementResponse.SkillImproverItem;
import com.calisthenics.backend.adminai.entity.AdminAiMessage;
import com.calisthenics.backend.adminai.repository.AdminAiMessageRepository;
import com.calisthenics.backend.adminai.type.AdminAiIntent;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedDayResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import com.calisthenics.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes Administrative Intents and Tool Plans to authorized, read-only Admin Data Access Layer tools.
 * Never exposes raw user emails (unless explicitly authorized for direct tool output), credentials,
 * or private chat/AI message text in LLM prompt summaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminToolRouter {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("(?:user(?:\\s+id|\\s*#)?\\s*[:#]?\\s*)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRO_EXPLICIT_USER_ID_PATTERN = Pattern.compile("(?:\\buser\\s+(?:id\\s*)?[:#]?\\s*|\\bid\\s*[:#]\\s*|#)(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PRO_PREPOSITION_TARGET_PATTERN = Pattern.compile(
            "(?:grant|give|add|enable|activate|assign|revoke|remove|cancel|disable|strip)\\s+"
                    + "(?:unlimited\\s+|admin[- ]?granted\\s+|admin\\s+)?pro(?:\\s+(?:access|membership|tier|plan|status))?\\s+"
                    + "(?:to|from|for|of)\\s+(.+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRO_SUBJECT_BEFORE_PRO_PATTERN = Pattern.compile(
            "(?:make|set|upgrade|grant|give)\\s+(.+?)\\s+(?:to\\s+|as\\s+)?(?:a\\s+|an\\s+)?(?:unlimited\\s+|admin\\s+)?pro"
                    + "(?:\\s+(?:member|user|athlete|account|access|membership|tier|status))?(?:$|\\s+because|\\s+due\\s+to|\\s+reason)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRO_CHECK_PATTERN = Pattern.compile(
            "(?:check|show|get)\\s+(?:the\\s+)?pro\\s+(?:status|access|membership)?\\s*(?:for|of)\\s+(.+)|(?:does|has)\\s+(.+?)\\s+have\\s+pro\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRO_REASON_PATTERN = Pattern.compile(
            "(?:\\s+(?:because|due\\s+to|with\\s+reason|reason\\s*:?)\\s+)(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETION_BELOW_PATTERN = Pattern.compile("(?:completion(?:\\s+rate)?\\s*(?:below|under|<|less than)\\s*)(\\d+(?:\\.\\d+)?)%?", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETION_ABOVE_PATTERN = Pattern.compile("(?:completion(?:\\s+rate)?\\s*(?:above|over|>|greater than|at least)\\s*)(\\d+(?:\\.\\d+)?)%?", Pattern.CASE_INSENSITIVE);
    private static final Pattern INACTIVE_DAYS_PATTERN = Pattern.compile("(?:inactive|not completed.*?workouts?|no workout).*?(?:last\\s+|for\\s+)?(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED_USER_PATTERN = Pattern.compile("(?:user|details for|inspect|workout history for|personal records for|prs for|progress for|activity for|activity of)\\s+([A-Z][a-zA-Z'\\-]+(?:\\s+[A-Z][a-zA-Z'\\-]+){0,2})");

    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminDataAccessService dataAccessService;
    private final AdminContextService adminContextService;
    private final AdminGettingStartedToolService adminGettingStartedToolService;
    private final AdminProToolService adminProToolService;
    private final ExerciseRecommendationService exerciseRecommendationService;
    private final ExerciseDraftValidationService exerciseDraftValidationService;
    private final BulkExerciseGenerationService bulkExerciseGenerationService;
    private final AdminAiMessageRepository adminAiMessageRepository;

    public record RoutedChatToolsResult(
            AdminAiIntent resolvedIntent,
            AdminTimeRangeMetadata timeRange,
            List<AdminToolExecutionRecord> executedTools,
            AdminGlobalUserStatisticsResponse userStatistics,
            List<AdminGlobalUserSummaryDto> matchedUsers,
            AdminUserDetailDossierResponse inspectedUserDetail,
            AdminUserSegmentsResponse segmentAnalysis,
            AdminPlatformEngagementResponse platformEngagement,
            AdminCoachClientStatisticsResponse coachClientStatistics,
            List<AdminContextService.OperationalInsightCandidate> dataRecommendations,
            List<String> missingDataNotes,
            String sanitizedToolContextForPrompt,
            GettingStartedProgramResponse gettingStartedProgram,
            GettingStartedProgramPreviewResponse gettingStartedPreview,
            List<GettingStartedCampaignSummaryResponse> activeCampaigns,
            GettingStartedCampaignAnalyticsResponse campaignAnalytics,
            AdminPendingConfirmationResponse pendingConfirmation,
            AdminProToolResultResponse proToolResult,
            ExerciseDraftBatchResultResponse exerciseDraftResult,
            BulkExerciseGenerationJobResponse bulkGenerationJob
    ) {}

    public AdminToolExecuteResponse executeSingleTool(AdminToolExecuteRequest request) {
        if (request == null || request.getToolName() == null || request.getToolName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolName is required");
        }
        AdminToolName toolName = AdminToolName.fromWireName(request.getToolName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown admin tool: " + request.getToolName()));

        User admin = adminAuthorizationService.requireAdminToolAccess(toolName);

        long startMs = System.currentTimeMillis();
        if (adminGettingStartedToolService.isGettingStartedTool(toolName)) {
            GettingStartedToolResultResponse gsResult;
            try {
                gsResult = adminGettingStartedToolService.executeTool(admin, toolName, request);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
            }
            long durationMs = Math.max(0L, System.currentTimeMillis() - startMs);
            long recordCount = gsResult.getActiveCampaigns() != null
                    ? gsResult.getActiveCampaigns().size()
                    : (gsResult.getEligibleAthleteCount() != null ? gsResult.getEligibleAthleteCount() : 1L);
            AdminToolExecutionRecord record = AdminToolExecutionRecord.builder()
                    .toolName(toolName.getToolName())
                    .status(gsResult.isConfirmationRequired() ? "CONFIRMATION_REQUIRED" : "EXECUTED")
                    .parametersSummary("programId=" + request.getProgramId() + ",audience=" + request.getTargetAudience()
                            + ",confirmed=" + Boolean.TRUE.equals(request.getConfirmed()))
                    .summary(gsResult.getMessage())
                    .recordCount(recordCount)
                    .durationMs(durationMs)
                    .executedAt(ZonedDateTime.now())
                    .build();
            return AdminToolExecuteResponse.builder()
                    .execution(record)
                    .data(gsResult)
                    .build();
        }
        if (adminProToolService.isProTool(toolName)) {
            AdminProToolResultResponse proResult;
            try {
                proResult = adminProToolService.executeTool(admin, toolName, request);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
            }
            long durationMs = Math.max(0L, System.currentTimeMillis() - startMs);
            String status = proResult.isConfirmationRequired()
                    ? "CONFIRMATION_REQUIRED"
                    : (proResult.isExecuted() ? "EXECUTED" : proResult.getOutcomeStatus());
            long recordCount = proResult.getCandidates() != null
                    ? proResult.getCandidates().size()
                    : (proResult.getTargetUser() != null ? 1L : 0L);
            AdminToolExecutionRecord record = AdminToolExecutionRecord.builder()
                    .toolName(toolName.getToolName())
                    .status(status)
                    .parametersSummary("userId=" + request.getUserId()
                            + ",identifier=" + request.getUserIdentifier()
                            + ",confirmed=" + Boolean.TRUE.equals(request.getConfirmed()))
                    .summary(proResult.getMessage())
                    .recordCount(recordCount)
                    .durationMs(durationMs)
                    .executedAt(ZonedDateTime.now())
                    .build();
            return AdminToolExecuteResponse.builder()
                    .execution(record)
                    .data(proResult)
                    .build();
        }
        AdminAnalyticsRange range = request.getRange() != null ? request.getRange() : AdminAnalyticsRange.LAST_30_DAYS;
        boolean includeEmail = Boolean.TRUE.equals(request.getIncludeEmail());
        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 20;

        Object data;
        long recordCount;
        String summary;

        switch (toolName) {
            case GET_USER_STATISTICS -> {
                AdminGlobalUserStatisticsResponse res = dataAccessService.getUserStatistics(range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getTotalRegisteredUsers();
                summary = "Total users=" + res.getTotalRegisteredUsers() + ", active30d=" + res.getActiveUsers30Days();
            }
            case GET_ALL_USERS -> {
                AdminUserSegmentFilter filter = request.getFilter() != null ? request.getFilter() : new AdminUserSegmentFilter();
                if (request.getPage() != null) filter.setPage(page);
                if (request.getSize() != null) filter.setSize(size);
                if (request.getIncludeEmail() != null) filter.setIncludeEmail(includeEmail);
                if (request.getRange() != null) filter.setRange(range);
                if (request.getStartDate() != null) filter.setStartDate(request.getStartDate());
                if (request.getEndDate() != null) filter.setEndDate(request.getEndDate());
                AdminPagedResult<AdminGlobalUserSummaryDto> res = dataAccessService.getAllUsers(filter);
                data = res;
                recordCount = res.getTotalElements();
                summary = "Returned " + res.getItems().size() + " of " + res.getTotalElements() + " users";
            }
            case GET_USER_DETAILS -> {
                AdminUserDetailDossierResponse res = dataAccessService.getUserDetails(
                        request.getUserId(), includeEmail, range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = 1L;
                summary = "Loaded profile, workouts, PRs, progress & activity for user #" + res.getUserId();
            }
            case GET_USER_WORKOUT_HISTORY -> {
                AdminUserWorkoutHistoryResponse res = dataAccessService.getUserWorkoutHistory(
                        request.getUserId(), range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getTotalSessions();
                summary = "Loaded " + res.getTotalSessions() + " sessions (completion " + res.getCompletionRate() + "%)";
            }
            case GET_USER_PERSONAL_RECORDS -> {
                AdminUserPersonalRecordsResponse res = dataAccessService.getUserPersonalRecords(
                        request.getUserId(), range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getTotalRecords();
                summary = "Loaded " + res.getTotalRecords() + " Personal Records";
            }
            case GET_USER_PROGRESS -> {
                AdminUserProgressResponse res = dataAccessService.getUserProgress(
                        request.getUserId(), range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getExercisePerformance().size();
                summary = "Loaded progress trend (" + res.getConsistencyTrend() + ")";
            }
            case GET_USER_ACTIVITY -> {
                AdminUserActivityResponse res = dataAccessService.getUserActivity(
                        request.getUserId(), range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getAiMessagesInRange() + res.getChatMessagesSentInRange();
                summary = "Loaded privacy-safe activity metadata for user #" + res.getUserId();
            }
            case GET_COACH_CLIENT_STATISTICS -> {
                AdminCoachClientStatisticsResponse res = dataAccessService.getCoachClientStatistics(range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getTotalRelationships();
                summary = "Coaches=" + res.getTotalCoaches() + ", active relationships=" + res.getActiveRelationships();
            }
            case GET_PLATFORM_ENGAGEMENT -> {
                AdminPlatformEngagementResponse res = dataAccessService.getPlatformEngagement(
                        request.getSkillKeyword(), range, request.getStartDate(), request.getEndDate());
                data = res;
                recordCount = res.getTopPerformedExercises().size();
                summary = "DAU=" + res.getDauCount() + ", MAU=" + res.getMauCount();
            }
            case ANALYZE_USER_SEGMENTS -> {
                AdminUserSegmentFilter filter = request.getFilter() != null ? request.getFilter() : new AdminUserSegmentFilter();
                if (request.getPage() != null) filter.setPage(page);
                if (request.getSize() != null) filter.setSize(size);
                if (request.getIncludeEmail() != null) filter.setIncludeEmail(includeEmail);
                if (request.getRange() != null) filter.setRange(range);
                if (request.getStartDate() != null) filter.setStartDate(request.getStartDate());
                if (request.getEndDate() != null) filter.setEndDate(request.getEndDate());
                AdminUserSegmentsResponse res = dataAccessService.analyzeUserSegments(filter);
                data = res;
                recordCount = res.getMatchedCount();
                summary = res.getSegmentSummary();
            }
            case GENERATE_ADMIN_RECOMMENDATIONS -> {
                AdminGlobalUserStatisticsResponse stats = dataAccessService.getUserStatistics(range, request.getStartDate(), request.getEndDate());
                AdminCoachClientStatisticsResponse coaching = dataAccessService.getCoachClientStatistics(range, request.getStartDate(), request.getEndDate());
                AdminPlatformEngagementResponse engagement = dataAccessService.getPlatformEngagement(request.getSkillKeyword(), range, request.getStartDate(), request.getEndDate());
                List<AdminContextService.OperationalInsightCandidate> recs = generateGlobalDataRecommendations(
                        stats, coaching, engagement, range, request.getStartDate(), request.getEndDate());
                data = recs.stream().map(AdminContextService.OperationalInsightCandidate::payload).toList();
                recordCount = recs.size();
                summary = "Generated " + recs.size() + " data-backed administrative recommendations";
            }
            case CREATE_EXERCISE_DRAFTS -> {
                ExerciseDraftBatchResultResponse res = exerciseRecommendationService.createExerciseDrafts(
                        admin,
                        request.getConversationId(),
                        request.getInstruction(),
                        null,
                        request.getExerciseCategory(),
                        request.getSkillKeyword(),
                        request.getDifficultyLevel(),
                        request.getRequestedCount(),
                        request.getExerciseDrafts()
                );
                if (!res.isSuccess()) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, res.getErrorMessage());
                }
                data = res;
                recordCount = res.getCreatedCount();
                summary = res.getMessage();
            }
            case BULK_GENERATE_EXERCISES -> {
                BulkExerciseGenerationRequest bulkReq = BulkExerciseGenerationRequest.builder()
                        .prompt(request.getInstruction())
                        .quantity(request.getQuantity() != null ? request.getQuantity() : request.getRequestedCount())
                        .trainingType(request.getTrainingType())
                        .categories(request.getCategories())
                        .focus(request.getFocus() != null ? request.getFocus() : request.getSkillKeyword())
                        .batchSize(request.getBatchSize())
                        .conversationId(request.getConversationId())
                        .build();
                BulkExerciseGenerationJobResponse res = bulkExerciseGenerationService.launchAndAwaitIfFast(admin, bulkReq, 6000L);
                data = res;
                recordCount = res.getSavedCount();
                summary = res.getStatusMessage();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported admin tool");
        }

        long durationMs = Math.max(0L, System.currentTimeMillis() - startMs);
        AdminToolExecutionRecord record = AdminToolExecutionRecord.builder()
                .toolName(toolName.getToolName())
                .status("EXECUTED")
                .parametersSummary("userId=" + request.getUserId() + ",range=" + range + ",skill=" + request.getSkillKeyword())
                .summary(summary)
                .recordCount(recordCount)
                .durationMs(durationMs)
                .executedAt(ZonedDateTime.now())
                .build();

        return AdminToolExecuteResponse.builder()
                .execution(record)
                .data(data)
                .build();
    }

    public RoutedChatToolsResult routeAndExecuteForChat(
            String userPrompt,
            AdminChatRequest request,
            AdminAiIntent intent,
            AdminAnalyticsRange range,
            LocalDate customStart,
            LocalDate customEnd,
            String skillKeyword,
            GeminiAdminToolPlan geminiPlan
    ) {
        adminAuthorizationService.requireAuthenticatedAdmin();

        LocalDate startDate = customStart;
        LocalDate endDate = customEnd;
        AdminAnalyticsRange effectiveRange = range != null ? range : AdminAnalyticsRange.LAST_30_DAYS;
        if (geminiPlan != null && geminiPlan.getRange() != null) {
            try {
                effectiveRange = AdminAnalyticsRange.valueOf(geminiPlan.getRange().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        ExerciseCategory explicitPromptCategory = ExerciseDraftValidationService.detectCategoryFromText(userPrompt);
        boolean hasExplicitExercisesInPrompt = !exerciseDraftValidationService.extractRequestedExercises(userPrompt).isEmpty();
        // Never allow stale geminiPlan.skillKeyword from prior conversation turns to override current category/exercises
        String effectiveSkill;
        if (!isBlank(skillKeyword)) {
            effectiveSkill = skillKeyword.trim();
        } else if (explicitPromptCategory == null && !hasExplicitExercisesInPrompt
                && geminiPlan != null && !isBlank(geminiPlan.getSkillKeyword())) {
            effectiveSkill = geminiPlan.getSkillKeyword().trim();
        } else {
            effectiveSkill = null;
        }
        boolean includeEmail = Boolean.TRUE.equals(request.getIncludeEmail())
                || (geminiPlan != null && Boolean.TRUE.equals(geminiPlan.getIncludeEmail()));

        AdminTimeRangeMetadata timeRange = dataAccessService.buildTimeRangeMetadata(effectiveRange, startDate, endDate);
        Set<AdminToolName> toolsToRun = determineToolsToRun(userPrompt, request, intent, geminiPlan, effectiveSkill);
        Long targetUserId = resolveTargetUserId(userPrompt, request, geminiPlan);
        AdminUserSegmentFilter segmentFilter = extractSegmentFilter(userPrompt, effectiveSkill, effectiveRange, startDate, endDate, includeEmail, geminiPlan);

        List<AdminToolExecutionRecord> executionRecords = new ArrayList<>();
        Set<String> missingDataNotes = new LinkedHashSet<>();

        AdminGlobalUserStatisticsResponse userStats = null;
        List<AdminGlobalUserSummaryDto> matchedUsers = new ArrayList<>();
        AdminUserDetailDossierResponse inspectedUser = null;
        AdminUserSegmentsResponse segmentResponse = null;
        AdminPlatformEngagementResponse engagementResponse = null;
        AdminCoachClientStatisticsResponse coachingResponse = null;
        List<AdminContextService.OperationalInsightCandidate> dataRecommendations = new ArrayList<>();
        GettingStartedProgramResponse gettingStartedProgram = null;
        GettingStartedProgramPreviewResponse gettingStartedPreview = null;
        // Null sentinel indicates no campaign tool executed (checked by AdminAIService.resolveIntentAfterTools)
        List<GettingStartedCampaignSummaryResponse> activeCampaigns = null;
        GettingStartedCampaignAnalyticsResponse campaignAnalytics = null;
        AdminPendingConfirmationResponse pendingConfirmation = null;
        AdminProToolResultResponse proToolResult = null;
        ExerciseDraftBatchResultResponse exerciseDraftResult = null;
        BulkExerciseGenerationJobResponse bulkGenerationJob = null;

        boolean hasProTools = toolsToRun.stream().anyMatch(adminProToolService::isProTool);
        if (hasProTools) {
            User admin = adminAuthorizationService.requireAuthenticatedAdmin();
            for (AdminToolName proTool : toolsToRun) {
                if (!adminProToolService.isProTool(proTool)) {
                    continue;
                }
                long t0 = System.currentTimeMillis();
                AdminToolExecuteRequest proReq = buildProChatRequest(userPrompt, request, geminiPlan, proTool);
                try {
                    AdminProToolResultResponse res = adminProToolService.executeTool(admin, proTool, proReq);
                    proToolResult = res;
                    if (res.getPendingConfirmation() != null) {
                        pendingConfirmation = res.getPendingConfirmation();
                    }
                    String status = res.isConfirmationRequired()
                            ? "CONFIRMATION_REQUIRED"
                            : (res.isExecuted() ? "EXECUTED" : res.getOutcomeStatus());
                    long count = res.getCandidates() != null ? res.getCandidates().size()
                            : (res.getTargetUser() != null ? 1L : 0L);
                    executionRecords.add(AdminToolExecutionRecord.builder()
                            .toolName(proTool.getToolName())
                            .status(status)
                            .parametersSummary("userId=" + proReq.getUserId()
                                    + ",identifier=" + proReq.getUserIdentifier() + ",confirmed=false")
                            .summary(res.getMessage())
                            .recordCount(count)
                            .durationMs(Math.max(0L, System.currentTimeMillis() - t0))
                            .executedAt(ZonedDateTime.now())
                            .build());
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    missingDataNotes.add(ex.getMessage());
                    executionRecords.add(AdminToolExecutionRecord.builder()
                            .toolName(proTool.getToolName())
                            .status("FAILED")
                            .parametersSummary("userId=" + proReq.getUserId() + ",identifier=" + proReq.getUserIdentifier())
                            .summary(ex.getMessage())
                            .recordCount(0L)
                            .durationMs(Math.max(0L, System.currentTimeMillis() - t0))
                            .executedAt(ZonedDateTime.now())
                            .build());
                }
            }
        }

        boolean hasGettingStartedTools = toolsToRun.stream().anyMatch(adminGettingStartedToolService::isGettingStartedTool);
        if (hasGettingStartedTools) {
            User admin = adminAuthorizationService.requireAuthenticatedAdmin();
            for (AdminToolName gsTool : toolsToRun) {
                if (!adminGettingStartedToolService.isGettingStartedTool(gsTool)) {
                    continue;
                }
                long t0 = System.currentTimeMillis();
                AdminToolExecuteRequest gsReq = buildGettingStartedChatRequest(userPrompt, request, geminiPlan, gsTool);
                try {
                    GettingStartedToolResultResponse gsRes = adminGettingStartedToolService.executeTool(admin, gsTool, gsReq);
                    if (gsRes.getProgram() != null) gettingStartedProgram = gsRes.getProgram();
                    if (gsRes.getPreview() != null) gettingStartedPreview = gsRes.getPreview();
                    if (gsRes.getActiveCampaigns() != null) activeCampaigns = gsRes.getActiveCampaigns();
                    if (gsRes.getAnalytics() != null) campaignAnalytics = gsRes.getAnalytics();
                    if (gsRes.getPendingConfirmation() != null) pendingConfirmation = gsRes.getPendingConfirmation();

                    long count = gsRes.getActiveCampaigns() != null ? gsRes.getActiveCampaigns().size()
                            : (gsRes.getEligibleAthleteCount() != null ? gsRes.getEligibleAthleteCount() : 1L);
                    String status = gsRes.isConfirmationRequired() ? "CONFIRMATION_REQUIRED" : "EXECUTED";
                    executionRecords.add(AdminToolExecutionRecord.builder()
                            .toolName(gsTool.getToolName())
                            .status(status)
                            .parametersSummary("programId=" + gsReq.getProgramId() + ",audience=" + gsReq.getTargetAudience() + ",confirmed=false")
                            .summary(gsRes.getMessage())
                            .recordCount(count)
                            .durationMs(Math.max(0L, System.currentTimeMillis() - t0))
                            .executedAt(ZonedDateTime.now())
                            .build());
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    missingDataNotes.add(ex.getMessage());
                    executionRecords.add(AdminToolExecutionRecord.builder()
                            .toolName(gsTool.getToolName())
                            .status("FAILED")
                            .parametersSummary("programId=" + gsReq.getProgramId() + ",audience=" + gsReq.getTargetAudience())
                            .summary(ex.getMessage())
                            .recordCount(0L)
                            .durationMs(Math.max(0L, System.currentTimeMillis() - t0))
                            .executedAt(ZonedDateTime.now())
                            .build());
                }
            }
        }

        if (toolsToRun.contains(AdminToolName.GET_USER_STATISTICS)) {
            long t0 = System.currentTimeMillis();
            userStats = dataAccessService.getUserStatistics(effectiveRange, startDate, endDate);
            if (userStats.getMissingDataNotes() != null) missingDataNotes.addAll(userStats.getMissingDataNotes());
            executionRecords.add(record(AdminToolName.GET_USER_STATISTICS, "range=" + effectiveRange,
                    userStats.getTotalRegisteredUsers() + " users evaluated", userStats.getTotalRegisteredUsers(), t0));
        }

        if (toolsToRun.contains(AdminToolName.GET_COACH_CLIENT_STATISTICS)) {
            long t0 = System.currentTimeMillis();
            coachingResponse = dataAccessService.getCoachClientStatistics(effectiveRange, startDate, endDate);
            if (coachingResponse.getMissingDataNotes() != null) missingDataNotes.addAll(coachingResponse.getMissingDataNotes());
            executionRecords.add(record(AdminToolName.GET_COACH_CLIENT_STATISTICS, "range=" + effectiveRange,
                    coachingResponse.getActiveRelationships() + " active coach-client pairs", coachingResponse.getTotalRelationships(), t0));
        }

        if (toolsToRun.contains(AdminToolName.GET_PLATFORM_ENGAGEMENT)) {
            long t0 = System.currentTimeMillis();
            engagementResponse = dataAccessService.getPlatformEngagement(effectiveSkill, effectiveRange, startDate, endDate);
            if (engagementResponse.getMissingDataNotes() != null) missingDataNotes.addAll(engagementResponse.getMissingDataNotes());
            executionRecords.add(record(AdminToolName.GET_PLATFORM_ENGAGEMENT, "skill=" + effectiveSkill + ",range=" + effectiveRange,
                    "DAU=" + engagementResponse.getDauCount() + ", MAU=" + engagementResponse.getMauCount(),
                    engagementResponse.getExperienceComparisons().size(), t0));
        }

        if (toolsToRun.contains(AdminToolName.ANALYZE_USER_SEGMENTS)) {
            long t0 = System.currentTimeMillis();
            segmentResponse = dataAccessService.analyzeUserSegments(segmentFilter);
            matchedUsers.addAll(segmentResponse.getUsers());
            if (segmentResponse.getMissingDataNotes() != null) missingDataNotes.addAll(segmentResponse.getMissingDataNotes());
            executionRecords.add(record(AdminToolName.ANALYZE_USER_SEGMENTS,
                    "matched=" + segmentResponse.getMatchedCount(),
                    segmentResponse.getSegmentSummary(), segmentResponse.getMatchedCount(), t0));
        } else if (toolsToRun.contains(AdminToolName.GET_ALL_USERS)) {
            long t0 = System.currentTimeMillis();
            AdminPagedResult<AdminGlobalUserSummaryDto> paged = dataAccessService.getAllUsers(segmentFilter);
            matchedUsers.addAll(paged.getItems());
            if (paged.getMissingDataNotes() != null) missingDataNotes.addAll(paged.getMissingDataNotes());
            executionRecords.add(record(AdminToolName.GET_ALL_USERS,
                    "page=0,size=" + segmentFilter.getSize(),
                    "Returned " + paged.getItems().size() + " of " + paged.getTotalElements() + " users",
                    paged.getTotalElements(), t0));
        }

        if (targetUserId != null && (toolsToRun.contains(AdminToolName.GET_USER_DETAILS)
                || toolsToRun.contains(AdminToolName.GET_USER_WORKOUT_HISTORY)
                || toolsToRun.contains(AdminToolName.GET_USER_PERSONAL_RECORDS)
                || toolsToRun.contains(AdminToolName.GET_USER_PROGRESS)
                || toolsToRun.contains(AdminToolName.GET_USER_ACTIVITY))) {
            long t0 = System.currentTimeMillis();
            inspectedUser = dataAccessService.getUserDetails(targetUserId, includeEmail, effectiveRange, startDate, endDate);
            if (inspectedUser.getMissingDataFields() != null) missingDataNotes.addAll(inspectedUser.getMissingDataFields());
            executionRecords.add(record(AdminToolName.GET_USER_DETAILS, "userId=" + targetUserId,
                    "Inspected user #" + targetUserId + " (" + inspectedUser.getFullName() + ")", 1L, t0));
            if (toolsToRun.contains(AdminToolName.GET_USER_WORKOUT_HISTORY)) {
                executionRecords.add(record(AdminToolName.GET_USER_WORKOUT_HISTORY, "userId=" + targetUserId,
                        inspectedUser.getWorkoutSummary().getTotalSessions() + " workouts", inspectedUser.getWorkoutSummary().getTotalSessions(), t0));
            }
            if (toolsToRun.contains(AdminToolName.GET_USER_PERSONAL_RECORDS)) {
                executionRecords.add(record(AdminToolName.GET_USER_PERSONAL_RECORDS, "userId=" + targetUserId,
                        inspectedUser.getPersonalRecordsSummary().getTotalRecords() + " PRs", inspectedUser.getPersonalRecordsSummary().getTotalRecords(), t0));
            }
            if (toolsToRun.contains(AdminToolName.GET_USER_PROGRESS)) {
                executionRecords.add(record(AdminToolName.GET_USER_PROGRESS, "userId=" + targetUserId,
                        "Trend=" + inspectedUser.getProgressSummary().getConsistencyTrend(), 1L, t0));
            }
            if (toolsToRun.contains(AdminToolName.GET_USER_ACTIVITY)) {
                executionRecords.add(record(AdminToolName.GET_USER_ACTIVITY, "userId=" + targetUserId,
                        "AI msgs=" + inspectedUser.getActivitySummary().getAiMessagesInRange(), 1L, t0));
            }
        }

        if (toolsToRun.contains(AdminToolName.CREATE_EXERCISE_DRAFTS)) {
            long t0 = System.currentTimeMillis();
            User admin = adminAuthorizationService.requireAuthenticatedAdmin();
            DifficultyLevel diff = null;
            if (!hasExplicitExercisesInPrompt && geminiPlan != null && !isBlank(geminiPlan.getExperienceLevel())) {
                try {
                    diff = DifficultyLevel.valueOf(geminiPlan.getExperienceLevel().trim().toUpperCase(Locale.US));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ExerciseCategory geminiPlanCategory = parseGeminiExerciseCategory(geminiPlan);
            ExerciseCategory inferredCategory = explicitPromptCategory != null
                    ? explicitPromptCategory
                    : (geminiPlanCategory != null
                            ? geminiPlanCategory
                            : ExerciseDraftValidationService.detectCategoryFromText(effectiveSkill));
            Integer requestedCount = geminiPlan != null && geminiPlan.getRequestedCount() != null
                    ? geminiPlan.getRequestedCount()
                    : ExerciseRecommendationService.resolveRequestedCount(userPrompt, null);
            String recentRecContext = !hasExplicitExercisesInPrompt
                    ? findImmediatePriorRecommendationContent(request.getConversationId())
                    : null;
            exerciseDraftResult = exerciseRecommendationService.createExerciseDrafts(
                    admin,
                    request.getConversationId(),
                    userPrompt,
                    recentRecContext,
                    inferredCategory,
                    effectiveSkill,
                    diff,
                    requestedCount,
                    geminiPlan != null ? geminiPlan.getExerciseDrafts() : null
            );
            String toolStatus = exerciseDraftResult.isSuccess() ? "EXECUTED" : "FAILED";
            String toolSummary = exerciseDraftResult.isSuccess()
                    ? exerciseDraftResult.getMessage()
                    : exerciseDraftResult.getErrorMessage();
            long count = exerciseDraftResult.getCreatedCount();
            if (!exerciseDraftResult.isSuccess() && exerciseDraftResult.getErrorMessage() != null) {
                missingDataNotes.add(exerciseDraftResult.getErrorMessage());
            }
            executionRecords.add(AdminToolExecutionRecord.builder()
                    .toolName(AdminToolName.CREATE_EXERCISE_DRAFTS.getToolName())
                    .status(toolStatus)
                    .parametersSummary("category=" + exerciseDraftResult.getCategory() + ",requested=" + requestedCount)
                    .summary(toolSummary)
                    .recordCount(count)
                    .durationMs(Math.max(0L, System.currentTimeMillis() - t0))
                    .executedAt(ZonedDateTime.now())
                    .build());
        }

        if (toolsToRun.contains(AdminToolName.BULK_GENERATE_EXERCISES)) {
            long t0 = System.currentTimeMillis();
            User admin = adminAuthorizationService.requireAuthenticatedAdmin();
            BulkExerciseGenerationRequest bulkReq = BulkExerciseGenerationRequest.builder()
                    .prompt(userPrompt)
                    .quantity(geminiPlan != null ? geminiPlan.getRequestedCount() : null)
                    .conversationId(request.getConversationId())
                    .build();
            bulkGenerationJob = bulkExerciseGenerationService.launchAndAwaitIfFast(admin, bulkReq, 6000L);
            executionRecords.add(record(
                    AdminToolName.BULK_GENERATE_EXERCISES,
                    "qty=" + bulkGenerationJob.getRequestedQuantity() + ",type=" + bulkGenerationJob.getTrainingTypeFilter(),
                    bulkGenerationJob.getStatusMessage(),
                    bulkGenerationJob.getSavedCount(),
                    t0
            ));
        }

        if (toolsToRun.contains(AdminToolName.GENERATE_ADMIN_RECOMMENDATIONS)) {
            long t0 = System.currentTimeMillis();
            if (userStats == null) {
                userStats = dataAccessService.getUserStatistics(effectiveRange, startDate, endDate);
            }
            if (coachingResponse == null) {
                coachingResponse = dataAccessService.getCoachClientStatistics(effectiveRange, startDate, endDate);
            }
            if (engagementResponse == null) {
                engagementResponse = dataAccessService.getPlatformEngagement(effectiveSkill, effectiveRange, startDate, endDate);
            }
            dataRecommendations.addAll(generateGlobalDataRecommendations(
                    userStats, coachingResponse, engagementResponse, effectiveRange, startDate, endDate));
            executionRecords.add(record(AdminToolName.GENERATE_ADMIN_RECOMMENDATIONS, "range=" + effectiveRange,
                    "Generated " + dataRecommendations.size() + " recommendations", dataRecommendations.size(), t0));
        }

        String sanitizedPromptContext = formatSanitizedPromptContext(
                timeRange, executionRecords, userStats, segmentResponse, engagementResponse, coachingResponse, inspectedUser,
                dataRecommendations, missingDataNotes, gettingStartedProgram, gettingStartedPreview, activeCampaigns,
                campaignAnalytics, pendingConfirmation, proToolResult, exerciseDraftResult);

        return new RoutedChatToolsResult(
                intent,
                timeRange,
                executionRecords,
                userStats,
                matchedUsers.stream().limit(25).toList(),
                inspectedUser,
                segmentResponse,
                engagementResponse,
                coachingResponse,
                dataRecommendations,
                new ArrayList<>(missingDataNotes),
                sanitizedPromptContext,
                gettingStartedProgram,
                gettingStartedPreview,
                activeCampaigns,
                campaignAnalytics,
                pendingConfirmation,
                proToolResult,
                exerciseDraftResult,
                bulkGenerationJob
        );
    }

    public List<AdminContextService.OperationalInsightCandidate> generateGlobalDataRecommendations(
            AdminGlobalUserStatisticsResponse stats,
            AdminCoachClientStatisticsResponse coaching,
            AdminPlatformEngagementResponse engagement,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        AdminContextService.AdminFullContext baseContext = adminContextService.buildContext(range, startDate, endDate, null);
        List<AdminContextService.OperationalInsightCandidate> recs = new ArrayList<>();

        if (stats.getInactiveUsers14PlusDays() > 0 && stats.getTotalRegisteredUsers() > 0) {
            double inactivePct = Math.round((stats.getInactiveUsers14PlusDays() * 10000.0) / stats.getTotalRegisteredUsers()) / 100.0;
            String support = stats.getInactiveUsers14PlusDays() + " users (" + inactivePct + "%) have no completed workout in 14+ days; "
                    + stats.getAtRiskUsers7To14Days() + " additional users are in the 7-14 day at-risk window.";
            recs.add(new AdminContextService.OperationalInsightCandidate(
                    inactivePct >= 30.0 ? AdminRecommendationPriority.HIGH : AdminRecommendationPriority.MEDIUM,
                    "Users at Risk of Becoming Inactive & Disengaged Athletes",
                    inactivePct + "% of users inactive 14+ days; " + stats.getAtRiskUsers7To14Days() + " at risk.",
                    "Early re-engagement in the 7-14 day window prevents long-term churn.",
                    OperationalRecommendationPayload.builder()
                            .affectedArea("USER_RETENTION")
                            .identifiedIssue("Users at Risk of Becoming Inactive & Disengaged Athletes")
                            .supportingData(support)
                            .suggestedAction("Trigger re-engagement prompts, assign lower-friction beginner/deload routines, and prioritize outreach for the "
                                    + stats.getAtRiskUsers7To14Days() + " at-risk users before day 14.")
                            .expectedBenefit("Improves 30-day retention and reduces premature drop-off.")
                            .build()
            ));
        }

        if (stats.getTotalClients() > 0 && coaching.getClientsWithActiveCoach() < stats.getTotalClients()) {
            long uncoached = Math.max(0L, stats.getTotalClients() - coaching.getClientsWithActiveCoach());
            String support = uncoached + " of " + stats.getTotalClients() + " clients do not have an active coach; "
                    + coaching.getAcceptingClientsCoaches() + " coaches are currently accepting clients.";
            recs.add(new AdminContextService.OperationalInsightCandidate(
                    AdminRecommendationPriority.MEDIUM,
                    "Clients Without Active Coach Assignment Who Could Benefit from Guidance",
                    uncoached + " clients lack an active coach across " + coaching.getAcceptingClientsCoaches() + " available coaches.",
                    "Coached athletes exhibit higher adherence and faster PR progression.",
                    OperationalRecommendationPayload.builder()
                            .affectedArea("COACH_MATCHING")
                            .identifiedIssue("Clients Without Active Coach Assignment Who Could Benefit from Guidance")
                            .supportingData(support)
                            .suggestedAction("Surface coach matching suggestions to intermediate/stalled or declining-consistency users without a coach.")
                            .expectedBenefit("Increases workout adherence and Personal Record progression speed.")
                            .build()
            ));
        }

        if (engagement != null && !engagement.getTopPerformedExercises().isEmpty()) {
            ExerciseUsageItem topEx = engagement.getTopPerformedExercises().get(0);
            if (topEx.getCompletionRate() < 75.0) {
                String support = topEx.getExerciseName() + " logged " + topEx.getTotalPerformances()
                        + " times across " + topEx.getUniqueUsersCount() + " users, with completion rate "
                        + topEx.getCompletionRate() + "%.";
                recs.add(new AdminContextService.OperationalInsightCandidate(
                        AdminRecommendationPriority.HIGH,
                        "Popular Exercise Needs Regressions or Supporting Content: " + topEx.getExerciseName(),
                        topEx.getExerciseName() + " completion is " + topEx.getCompletionRate() + "%.",
                        "High-volume exercises with lower completion indicate missing prerequisite regressions or technique guidance.",
                        OperationalRecommendationPayload.builder()
                                .affectedArea("WORKOUT_PROGRAMS_AND_CONTENT")
                                .identifiedIssue("Popular Exercise Needs Regressions or Supporting Content: " + topEx.getExerciseName())
                                .supportingData(support)
                                .suggestedAction("Add form cue videos, prerequisite progressions, and easier regressions around " + topEx.getExerciseName() + ".")
                                .expectedBenefit("Lifts exercise completion rate and reduces failed attempts.")
                                .build()
                ));
            }
        }

        if (stats.getTotalWorkoutsInRange() > 0 && stats.getOverallWorkoutCompletionRate() < 75.0) {
            String support = stats.getCompletedWorkoutsInRange() + " completed out of " + stats.getTotalWorkoutsInRange()
                    + " workouts (" + stats.getOverallWorkoutCompletionRate() + "%), with "
                    + stats.getIncompleteOrAbandonedWorkoutsInRange() + " incomplete/cancelled.";
            recs.add(new AdminContextService.OperationalInsightCandidate(
                    AdminRecommendationPriority.HIGH,
                    "Workout Completion Rate Below 75% Target",
                    "Overall completion rate is " + stats.getOverallWorkoutCompletionRate() + "% in the active window.",
                    "Abandonment patterns typically indicate excessive session duration or steep progression steps.",
                    OperationalRecommendationPayload.builder()
                            .affectedArea("WORKOUT_PROGRAM_DESIGN")
                            .identifiedIssue("Workout Completion Rate Below 75% Target")
                            .supportingData(support)
                            .suggestedAction("Review current workout plans for excessive session volume or difficulty jumps and generate structured draft programs per experience level.")
                            .expectedBenefit("Raises weekly session completion and user consistency.")
                            .build()
            ));
        }

        if (recs.isEmpty() && baseContext.operationalInsights() != null) {
            recs.addAll(baseContext.operationalInsights());
        }
        return recs;
    }

    // ---------------- Intent/Parameter Extraction Helpers ----------------

    private Set<AdminToolName> determineToolsToRun(
            String userPrompt,
            AdminChatRequest request,
            AdminAiIntent intent,
            GeminiAdminToolPlan plan,
            String skill
    ) {
        Set<AdminToolName> tools = new LinkedHashSet<>();
        if (plan != null && plan.getTools() != null) {
            for (String raw : plan.getTools()) {
                AdminToolName.fromWireName(raw).ifPresent(tools::add);
            }
        }
        String lower = userPrompt != null ? userPrompt.toLowerCase() : "";

        // Prioritize Getting Started – 3 Days program & campaign tools when detected
        boolean mentionsGsOrCampaign = isGettingStartedOrCampaignPrompt(lower);

        if (mentionsGsOrCampaign || tools.stream().anyMatch(adminGettingStartedToolService::isGettingStartedTool)) {
            if (lower.contains("unpublish") || lower.contains("take down")) {
                tools.add(AdminToolName.UNPUBLISH_PROGRAM);
            } else if (lower.contains("publish") && !lower.contains("unpublish")) {
                tools.add(AdminToolName.PUBLISH_PROGRAM);
            } else if (lower.contains("analytics") || lower.contains("impression") || lower.contains("dismissal")
                    || lower.contains("detail view") || lower.contains("completion rate") || lower.contains("enrollments")) {
                tools.add(AdminToolName.GET_CAMPAIGN_ANALYTICS);
            } else if (lower.contains("active") && lower.contains("campaign")) {
                tools.add(AdminToolName.GET_ACTIVE_CAMPAIGNS);
            } else if (lower.contains("target audience") || lower.contains("audience targeting")
                    || lower.contains("all athletes")
                    || (lower.contains("audience") && !lower.contains("size") && !lower.contains("estimated"))
                    || (lower.contains("new members") && (lower.contains("14") || lower.contains("30")))) {
                tools.add(AdminToolName.CONFIGURE_AUDIENCE);
            } else if (lower.contains("preview") || lower.contains("eligibility") || lower.contains("audience size") || lower.contains("estimated audience")) {
                tools.add(AdminToolName.PREVIEW_PROGRAM);
            } else if (lower.contains("day 1") || lower.contains("day 2") || lower.contains("day 3")
                    || lower.contains("focus") || lower.contains("add ") || lower.contains("remove ")
                    || lower.contains("reorder") || lower.contains("swap") || lower.contains("edit ")
                    || lower.contains("update ") || lower.contains("change ") || lower.contains("make ") || lower.contains("set ")) {
                if (!tools.contains(AdminToolName.CREATE_PROGRAM)) {
                    tools.add(AdminToolName.UPDATE_PROGRAM);
                }
            } else if (lower.contains("create") || lower.contains("generate") || lower.contains("beginner") || lower.contains("full-body") || lower.contains("full body")) {
                tools.add(AdminToolName.CREATE_PROGRAM);
            }
            if (!tools.isEmpty()) {
                return tools;
            }
        }

        if (isProManagementPrompt(lower) || tools.stream().anyMatch(adminProToolService::isProTool)) {
            AdminToolName proTool;
            if (lower.contains("revoke") || lower.contains("remove") || lower.contains("cancel")
                    || lower.contains("disable") || lower.contains("downgrade") || lower.contains("strip")) {
                proTool = AdminToolName.REVOKE_PRO;
            } else if (lower.contains("grant") || lower.contains("make ") || lower.contains("give ")
                    || lower.contains("upgrade") || lower.contains("enable") || lower.contains("add ")
                    || lower.contains("assign") || lower.contains("set ")) {
                proTool = AdminToolName.GRANT_PRO;
            } else if (tools.contains(AdminToolName.REVOKE_PRO)) {
                proTool = AdminToolName.REVOKE_PRO;
            } else if (tools.contains(AdminToolName.GRANT_PRO)) {
                proTool = AdminToolName.GRANT_PRO;
            } else {
                proTool = AdminToolName.CHECK_PRO_STATUS;
            }
            return new LinkedHashSet<>(Set.of(proTool));
        }

        if (BulkExerciseGenerationService.isBulkExerciseGenerationPrompt(lower)
                || intent == AdminAiIntent.BULK_EXERCISE_GENERATION
                || tools.contains(AdminToolName.BULK_GENERATE_EXERCISES)) {
            return new LinkedHashSet<>(Set.of(AdminToolName.BULK_GENERATE_EXERCISES));
        }

        if (isExerciseDraftCreationPrompt(lower)
                || intent == AdminAiIntent.EXERCISE_DRAFT_CREATION
                || isExerciseDraftCreationFollowUp(lower, request.getConversationId())) {
            return new LinkedHashSet<>(Set.of(AdminToolName.CREATE_EXERCISE_DRAFTS));
        }

        if (request.getTargetUserId() != null || intent == AdminAiIntent.USER_DRILL_DOWN) {
            tools.add(AdminToolName.GET_USER_DETAILS);
            if (lower.contains("workout") || lower.contains("history")) tools.add(AdminToolName.GET_USER_WORKOUT_HISTORY);
            if (lower.contains("pr") || lower.contains("personal record")) tools.add(AdminToolName.GET_USER_PERSONAL_RECORDS);
            if (lower.contains("progress") || lower.contains("trend")) tools.add(AdminToolName.GET_USER_PROGRESS);
            if (lower.contains("activity") || lower.contains("chat") || lower.contains("ai usage")) tools.add(AdminToolName.GET_USER_ACTIVITY);
        }

        if (intent == AdminAiIntent.USER_STATISTICS_AND_SEARCH || lower.contains("how many users") || lower.contains("registered")) {
            tools.add(AdminToolName.GET_USER_STATISTICS);
        }
        if (intent == AdminAiIntent.USER_SEGMENTATION_ANALYSIS
                || lower.contains("not completed")
                || lower.contains("inactive")
                || lower.contains("not updated their personal records")
                || lower.contains("stale pr")
                || lower.contains("segment")
                || lower.contains("without coach")
                || lower.contains("no coach")) {
            tools.add(AdminToolName.ANALYZE_USER_SEGMENTS);
            tools.add(AdminToolName.GET_USER_STATISTICS);
        }
        if (intent == AdminAiIntent.COACH_CLIENT_ANALYTICS || lower.contains("coach")) {
            tools.add(AdminToolName.GET_COACH_CLIENT_STATISTICS);
        }
        if (intent == AdminAiIntent.PLATFORM_ENGAGEMENT_COMPARISON
                || !isBlank(skill)
                || lower.contains("improved")
                || lower.contains("declined")
                || lower.contains("most frequently performed")
                || lower.contains("training activity")
                || lower.contains("overall training")
                || lower.contains("compare")
                || lower.contains("experience level")
                || lower.contains("engagement")) {
            tools.add(AdminToolName.GET_PLATFORM_ENGAGEMENT);
            tools.add(AdminToolName.GET_USER_STATISTICS);
        }
        if (intent == AdminAiIntent.OPERATIONAL_INSIGHTS
                || lower.contains("recommend")
                || lower.contains("at risk")
                || lower.contains("weaknesses")
                || lower.contains("benefit from coach")) {
            tools.add(AdminToolName.GENERATE_ADMIN_RECOMMENDATIONS);
        }

        if (tools.isEmpty()) {
            tools.add(AdminToolName.GET_USER_STATISTICS);
            if (intent == AdminAiIntent.USER_TRAINING_ANALYTICS) {
                tools.add(AdminToolName.GET_PLATFORM_ENGAGEMENT);
            }
        }
        return tools;
    }

    private Long resolveTargetUserId(String userPrompt, AdminChatRequest request, GeminiAdminToolPlan plan) {
        if (request.getTargetUserId() != null) {
            return request.getTargetUserId();
        }
        if (plan != null && plan.getTargetUserId() != null) {
            return plan.getTargetUserId();
        }
        if (userPrompt != null) {
            Matcher idMatcher = USER_ID_PATTERN.matcher(userPrompt);
            if (idMatcher.find()) {
                try {
                    return Long.parseLong(idMatcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
            String candidateName = plan != null && !isBlank(plan.getTargetUserQuery())
                    ? plan.getTargetUserQuery().trim()
                    : extractCandidateName(userPrompt);
            if (!isBlank(candidateName)) {
                AdminPagedResult<AdminGlobalUserSummaryDto> search = dataAccessService.getAllUsers(
                        AdminUserSegmentFilter.builder().searchQuery(candidateName).page(0).size(5).build());
                if (search.getItems().size() == 1) {
                    return search.getItems().get(0).getUserId();
                }
                for (AdminGlobalUserSummaryDto item : search.getItems()) {
                    if (candidateName.equalsIgnoreCase(item.getFullName())) {
                        return item.getUserId();
                    }
                }
            }
        }
        return null;
    }

    private String extractCandidateName(String prompt) {
        Matcher m = NAMED_USER_PATTERN.matcher(prompt);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private AdminUserSegmentFilter extractSegmentFilter(
            String userPrompt,
            String skill,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate,
            boolean includeEmail,
            GeminiAdminToolPlan plan
    ) {
        String lower = userPrompt != null ? userPrompt.toLowerCase() : "";
        AdminUserSegmentFilter.AdminUserSegmentFilterBuilder b = AdminUserSegmentFilter.builder()
                .naturalLanguageQuery(userPrompt)
                .range(range)
                .startDate(startDate)
                .endDate(endDate)
                .includeEmail(includeEmail)
                .page(0)
                .size(20);

        if (plan != null) {
            if (!isBlank(plan.getExperienceLevel())) b.experienceLevel(plan.getExperienceLevel().trim().toUpperCase());
            if (!isBlank(plan.getFitnessGoal())) b.fitnessGoal(plan.getFitnessGoal().trim());
            if (!isBlank(plan.getActivityStatus())) b.activityStatus(plan.getActivityStatus().trim().toUpperCase());
            if (plan.getInactiveDaysThreshold() != null) b.inactiveDaysThreshold(plan.getInactiveDaysThreshold());
            if (!isBlank(plan.getPrProgressionStatus())) b.prProgressionStatus(plan.getPrProgressionStatus().trim().toUpperCase());
            if (!isBlank(plan.getCoachRelationshipStatus())) b.coachRelationshipStatus(plan.getCoachRelationshipStatus().trim().toUpperCase());
            if (plan.getMinCompletionRate() != null) b.minCompletionRate(plan.getMinCompletionRate());
            if (plan.getMaxCompletionRate() != null) b.maxCompletionRate(plan.getMaxCompletionRate());
        }

        if (lower.contains("beginner")) b.experienceLevel("BEGINNER");
        else if (lower.contains("intermediate")) b.experienceLevel("INTERMEDIATE");
        else if (lower.contains("advanced")) b.experienceLevel("ADVANCED");
        else if (lower.contains("elite")) b.experienceLevel("ELITE");

        if (lower.contains("inactive") || lower.contains("not completed any workouts") || lower.contains("no workout")) {
            b.activityStatus("INACTIVE");
        } else if (lower.contains("at risk") || lower.contains("at-risk")) {
            b.activityStatus("AT_RISK");
        }

        Matcher daysMatcher = INACTIVE_DAYS_PATTERN.matcher(userPrompt != null ? userPrompt : "");
        if (daysMatcher.find()) {
            try {
                b.inactiveDaysThreshold(Integer.parseInt(daysMatcher.group(1)));
                b.activityStatus("INACTIVE");
            } catch (NumberFormatException ignored) {
            }
        }

        if (lower.contains("not updated their personal records")
                || lower.contains("stale pr")
                || lower.contains("haven't updated pr")
                || lower.contains("no recent pr")) {
            b.prProgressionStatus("STALE");
        } else if (lower.contains("improving pr") || lower.contains("pr progression")) {
            b.prProgressionStatus("IMPROVING");
        }

        if (lower.contains("no coach") || lower.contains("without a coach") || lower.contains("without coach") || lower.contains("unassigned")) {
            b.coachRelationshipStatus("NO_COACH");
        } else if (lower.contains("pending invitation") || lower.contains("pending coach")) {
            b.coachRelationshipStatus("PENDING_INVITATION");
        } else if (lower.contains("have active coach") || lower.contains("with an active coach") || lower.contains("with active coach")) {
            b.coachRelationshipStatus("HAS_ACTIVE_COACH");
        }

        Matcher belowM = COMPLETION_BELOW_PATTERN.matcher(userPrompt != null ? userPrompt : "");
        if (belowM.find()) {
            try {
                b.maxCompletionRate(Double.parseDouble(belowM.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher aboveM = COMPLETION_ABOVE_PATTERN.matcher(userPrompt != null ? userPrompt : "");
        if (aboveM.find()) {
            try {
                b.minCompletionRate(Double.parseDouble(aboveM.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }

        if (!isBlank(skill)) {
            b.skillKeyword(skill);
        }
        if (lower.contains("strength")) b.fitnessGoal("Strength");
        else if (lower.contains("hypertrophy")) b.fitnessGoal("Hypertrophy");
        else if (lower.contains("mobility")) b.fitnessGoal("Mobility");

        return b.build();
    }

    // ---------------- Privacy-Safe Prompt Serialization ----------------

    private String formatSanitizedPromptContext(
            AdminTimeRangeMetadata timeRange,
            List<AdminToolExecutionRecord> executedTools,
            AdminGlobalUserStatisticsResponse stats,
            AdminUserSegmentsResponse segments,
            AdminPlatformEngagementResponse engagement,
            AdminCoachClientStatisticsResponse coaching,
            AdminUserDetailDossierResponse inspectedUser,
            List<AdminContextService.OperationalInsightCandidate> recs,
            Set<String> missingNotes,
            GettingStartedProgramResponse program,
            GettingStartedProgramPreviewResponse preview,
            List<GettingStartedCampaignSummaryResponse> activeCampaigns,
            GettingStartedCampaignAnalyticsResponse analytics,
            AdminPendingConfirmationResponse pendingConfirmation,
            AdminProToolResultResponse proToolResult,
            ExerciseDraftBatchResultResponse exerciseDraftResult
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== EXECUTED ADMIN DATA TOOLS (TIME RANGE: ")
                .append(timeRange.getRange()).append(" [").append(timeRange.getStartDate())
                .append(" to ").append(timeRange.getEndDate()).append("]) ===\n");
        sb.append("Tools run: ").append(executedTools.stream().map(AdminToolExecutionRecord::getToolName).toList()).append("\n");

        if (stats != null) {
            sb.append(String.format(
                    "GLOBAL USER STATS: total=%d (regular=%d, clients=%d, coaches=%d [verified=%d], admins=%d), " +
                            "newInPeriod=%d, activeToday=%d, active7d=%d, active30d=%d, inactive14PlusDays=%d, atRisk7to14d=%d, " +
                            "workoutsInRange=%d (completed=%d, completionRate=%.2f%%), totalPRs=%d (newInRange=%d, stalePR30dUsers=%d)\n",
                    stats.getTotalRegisteredUsers(), stats.getTotalRegularUsers(), stats.getTotalClients(),
                    stats.getTotalCoaches(), stats.getVerifiedCoaches(), stats.getTotalAdmins(),
                    stats.getNewRegistrationsInPeriod(), stats.getActiveUsersToday(), stats.getActiveUsers7Days(),
                    stats.getActiveUsers30Days(), stats.getInactiveUsers14PlusDays(), stats.getAtRiskUsers7To14Days(),
                    stats.getTotalWorkoutsInRange(), stats.getCompletedWorkoutsInRange(), stats.getOverallWorkoutCompletionRate(),
                    stats.getTotalPersonalRecordsAllTime(), stats.getNewPersonalRecordsInRange(), stats.getUsersWithStalePrs30PlusDays()
            ));
        }

        if (coaching != null) {
            sb.append(String.format(
                    "COACH-CLIENT STATS: coaches=%d (public=%d, accepting=%d, verified=%d), " +
                            "relationships[total=%d, active=%d, pending=%d, terminated=%d], clientsWithActiveCoach=%d, " +
                            "assignedPlans[total=%d, active=%d, completed=%d], sharedPRs=%d (feedbackRate=%.2f%%)\n",
                    coaching.getTotalCoaches(), coaching.getPublicCoaches(), coaching.getAcceptingClientsCoaches(), coaching.getVerifiedCoaches(),
                    coaching.getTotalRelationships(), coaching.getActiveRelationships(), coaching.getPendingInvitations(), coaching.getTerminatedRelationships(),
                    coaching.getClientsWithActiveCoach(), coaching.getTotalAssignedPlans(), coaching.getActiveAssignedPlans(),
                    coaching.getCompletedAssignedPlans(), coaching.getTotalSharedPrs(), coaching.getFeedbackResponseRate()
            ));
        }

        if (engagement != null) {
            sb.append(String.format("ENGAGEMENT: DAU=%d, WAU=%d, MAU=%d, stickiness=%.2f%%\n",
                    engagement.getDauCount(), engagement.getWauCount(), engagement.getMauCount(), engagement.getStickinessDauToMauPercent()));
            for (AdminPlatformEngagementResponse.ExperienceComparisonItem item : engagement.getExperienceComparisons()) {
                sb.append(String.format("  - Level %s: users=%d, active=%d (%.2f%%), workouts=%d (completed=%d, rate=%.2f%%), prs=%d, avgAI=%.2f\n",
                        item.getExperienceLevel(), item.getUserCount(), item.getActiveUserCount(), item.getActiveRatePercent(),
                        item.getTotalWorkouts(), item.getCompletedWorkouts(), item.getAvgCompletionRate(), item.getPrCount(), item.getAvgAiInteractionsPerUser()));
            }
            if (!engagement.getTopSkillImprovers().isEmpty()) {
                sb.append("TOP SKILL IMPROVERS:\n");
                for (SkillImproverItem imp : engagement.getTopSkillImprovers()) {
                    sb.append(String.format("  - user #%d (%s): %s improved +%.2f%% (latest=%s %s)\n",
                            imp.getUserId(), imp.getFullName(), imp.getExerciseName(),
                            imp.getImprovementPercent(), imp.getLatestRecordValue(), imp.getUnit()));
                }
            }
            if (!engagement.getDecliningConsistencyUsers().isEmpty()) {
                sb.append("DECLINING CONSISTENCY USERS (anonymized of PII except name/id): ");
                sb.append(engagement.getDecliningConsistencyUsers().stream()
                        .map(u -> "#" + u.getUserId() + " " + u.getFullName()).toList()).append("\n");
            }
            if (!engagement.getStalePrUsers().isEmpty()) {
                sb.append("STALE PR USERS: ");
                sb.append(engagement.getStalePrUsers().stream()
                        .map(u -> "#" + u.getUserId() + " " + u.getFullName()).toList()).append("\n");
            }
        }

        if (segments != null) {
            sb.append("SEGMENT ANALYSIS: ").append(segments.getSegmentSummary()).append("\n");
            sb.append("Matched Users (up to 10 shown, emails redacted): ");
            sb.append(segments.getUsers().stream().limit(10)
                    .map(u -> String.format("#%d %s [%s, %s, comp=%.1f%%, coach=%s]",
                            u.getUserId(), u.getFullName(), u.getExperienceLevel(), u.getActivityStatus(),
                            u.getWorkoutCompletionRate(), u.getCoachRelationshipStatus()))
                    .toList()).append("\n");
        }

        if (inspectedUser != null) {
            // Note: email is NEVER placed in prompt context even if includeEmail=true!
            sb.append(String.format(
                    "INSPECTED USER #%d (%s): category=%s, level=%s, goals=%s, heightCm=%s, weightKg=%s, " +
                            "workouts=%d (completed=%d, rate=%.2f%%, trend=%s), PRs=%d (maxImp=%s%%), " +
                            "AI_msgs_in_range=%d, chat_msgs_in_range=%d, coachRels=%d [private chat/AI text redacted]\n",
                    inspectedUser.getUserId(), inspectedUser.getFullName(), inspectedUser.getAccountCategory(),
                    inspectedUser.getExperienceLevel(), inspectedUser.getPrimaryGoals(),
                    inspectedUser.getHeightCm(), inspectedUser.getLatestWeightKg(),
                    inspectedUser.getWorkoutSummary().getTotalSessions(), inspectedUser.getWorkoutSummary().getCompletedSessions(),
                    inspectedUser.getWorkoutSummary().getCompletionRate(), inspectedUser.getWorkoutSummary().getConsistencyTrend(),
                    inspectedUser.getPersonalRecordsSummary().getTotalRecords(), inspectedUser.getPersonalRecordsSummary().getMaxImprovementPercent(),
                    inspectedUser.getActivitySummary().getAiMessagesInRange(), inspectedUser.getActivitySummary().getChatMessagesSentInRange(),
                    inspectedUser.getCoachRelationships().size()
            ));
        }

        if (!recs.isEmpty()) {
            sb.append("DATA-BACKED RECOMMENDATIONS: ");
            sb.append(recs.stream().map(AdminContextService.OperationalInsightCandidate::title).toList()).append("\n");
        }
        if (!missingNotes.isEmpty()) {
            sb.append("MISSING OR INCOMPLETE DATA NOTES: ").append(missingNotes).append("\n");
        }
        appendGettingStartedPromptContext(sb, program, preview, activeCampaigns, analytics, pendingConfirmation);
        appendProPromptContext(sb, proToolResult);
        if (exerciseDraftResult != null) {
            sb.append("EXERCISE DRAFT TOOL RESULT: success=").append(exerciseDraftResult.isSuccess())
                    .append(", category=").append(exerciseDraftResult.getCategory())
                    .append(", createdCount=").append(exerciseDraftResult.getCreatedCount());
            if (!exerciseDraftResult.isSuccess() && exerciseDraftResult.getErrorMessage() != null) {
                sb.append(", error=").append(exerciseDraftResult.getErrorMessage());
            } else {
                sb.append(", createdIds=").append(exerciseDraftResult.getCreatedExercises().stream()
                        .map(e -> "#" + e.id() + " " + e.name() + " [" + e.status() + ", " + e.origin() + "]")
                        .toList());
            }
            sb.append("\n");
        }
        sb.append("STRICT RULES: Cite exact counts, percentages, and time range. Distinguish measured facts from AI recommendations. Never invent numbers.\n");
        return sb.toString();
    }

    private AdminToolExecuteRequest buildProChatRequest(
            String userPrompt,
            AdminChatRequest request,
            GeminiAdminToolPlan plan,
            AdminToolName tool
    ) {
        String workingPrompt = userPrompt != null ? userPrompt.trim() : "";
        String extractedReason = (plan != null && !isBlank(plan.getProActionReason()))
                ? plan.getProActionReason().trim()
                : null;

        Matcher reasonMatcher = PRO_REASON_PATTERN.matcher(workingPrompt);
        if (reasonMatcher.find()) {
            if (extractedReason == null) {
                extractedReason = reasonMatcher.group(1).trim();
            }
            workingPrompt = workingPrompt.substring(0, reasonMatcher.start()).trim();
        }

        Long explicitUserId = request.getTargetUserId();
        String extractedIdentifier = null;

        if (explicitUserId == null) {
            Matcher emailMatcher = EMAIL_PATTERN.matcher(workingPrompt);
            List<String> matchedEmails = new ArrayList<>();
            while (emailMatcher.find()) {
                String candidateEmail = emailMatcher.group(1);
                if (!matchedEmails.contains(candidateEmail)) {
                    matchedEmails.add(candidateEmail);
                }
            }
            if (matchedEmails.size() == 1) {
                extractedIdentifier = matchedEmails.get(0);
            } else {
                Matcher prepoMatcher = PRO_PREPOSITION_TARGET_PATTERN.matcher(workingPrompt);
                if (prepoMatcher.find()) {
                    extractedIdentifier = prepoMatcher.group(1).trim();
                } else {
                    Matcher subjectMatcher = PRO_SUBJECT_BEFORE_PRO_PATTERN.matcher(workingPrompt);
                    if (subjectMatcher.find()) {
                        extractedIdentifier = subjectMatcher.group(1).trim();
                    } else {
                        Matcher checkMatcher = PRO_CHECK_PATTERN.matcher(workingPrompt);
                        if (checkMatcher.find()) {
                            extractedIdentifier = checkMatcher.group(1) != null
                                    ? checkMatcher.group(1).trim()
                                    : checkMatcher.group(2).trim();
                        }
                    }
                }
                if (extractedIdentifier != null) {
                    Matcher explicitIdMatcher = PRO_EXPLICIT_USER_ID_PATTERN.matcher(extractedIdentifier);
                    if (explicitIdMatcher.find()) {
                        try {
                            explicitUserId = Long.parseLong(explicitIdMatcher.group(1));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    Matcher explicitIdMatcher = PRO_EXPLICIT_USER_ID_PATTERN.matcher(workingPrompt);
                    if (explicitIdMatcher.find()) {
                        try {
                            explicitUserId = Long.parseLong(explicitIdMatcher.group(1));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        if (explicitUserId == null && isBlank(extractedIdentifier)
                && plan != null && !isBlank(plan.getTargetUserIdentifier())) {
            extractedIdentifier = plan.getTargetUserIdentifier().trim();
        }
        if (explicitUserId == null && isBlank(extractedIdentifier)
                && plan != null && !isBlank(plan.getTargetUserQuery())) {
            extractedIdentifier = plan.getTargetUserQuery().trim();
        }
        if (explicitUserId == null && plan != null && plan.getTargetUserId() != null) {
            explicitUserId = plan.getTargetUserId();
        }

        // Privilege-changing PRO actions NEVER auto-execute on single NL turn without confirmation token
        return AdminToolExecuteRequest.builder()
                .toolName(tool.getToolName())
                .conversationId(request.getConversationId())
                .userId(explicitUserId)
                .userIdentifier(extractedIdentifier)
                .reason(extractedReason)
                .confirmed(false)
                .confirmationToken(null)
                .build();
    }

    private void appendProPromptContext(StringBuilder sb, AdminProToolResultResponse proResult) {
        if (proResult == null) {
            return;
        }
        sb.append("PRO MANAGEMENT TOOL RESULT: action=").append(proResult.getAction())
                .append(", outcome=").append(proResult.getOutcomeStatus())
                .append(", confirmationRequired=").append(proResult.isConfirmationRequired())
                .append(", executed=").append(proResult.isExecuted());
        if (proResult.getTargetUser() != null) {
            AdminUserResponse u = proResult.getTargetUser();
            sb.append(", targetUser=#").append(u.id()).append(" ").append(u.fullName())
                    .append(" (").append(AdminDataAccessService.maskEmail(u.email())).append(")")
                    .append(", tier=").append(u.membershipTier())
                    .append(", source=").append(u.membershipSource())
                    .append(", paidPro=").append(u.paidPro())
                    .append(", adminGrantedPro=").append(u.adminGrantedPro());
        }
        if (proResult.getCandidates() != null && !proResult.getCandidates().isEmpty()) {
            sb.append(", ambiguousCandidates=")
                    .append(proResult.getCandidates().stream()
                            .map(c -> "#" + c.id() + " " + c.fullName()
                                    + " (" + AdminDataAccessService.maskEmail(c.email()) + ")")
                            .toList());
        }
        sb.append("\n");
    }

    private AdminToolExecuteRequest buildGettingStartedChatRequest(
            String userPrompt,
            AdminChatRequest request,
            GeminiAdminToolPlan plan,
            AdminToolName tool
    ) {
        String audience = (plan != null && !isBlank(plan.getTargetAudience()))
                ? plan.getTargetAudience()
                : extractAudienceFromPrompt(userPrompt);
        String instruction = (plan != null && !isBlank(plan.getProgramInstruction()))
                ? plan.getProgramInstruction()
                : userPrompt;
        Long programId = plan != null ? plan.getProgramId() : null;
        String title = (plan != null && !isBlank(plan.getProgramTitle())) ? plan.getProgramTitle() : null;

        // Publish and Unpublish ALWAYS start unconfirmed inside NL chat until explicit confirmation!
        return AdminToolExecuteRequest.builder()
                .toolName(tool.getToolName())
                .conversationId(request.getConversationId())
                .programId(programId)
                .title(title)
                .instruction(instruction)
                .targetAudience(audience)
                .confirmed(false)
                .confirmationToken(null)
                .build();
    }

    private String extractAudienceFromPrompt(String prompt) {
        if (prompt == null) {
            return null;
        }
        String lower = prompt.toLowerCase(Locale.US);
        if (lower.contains("14 day") || lower.contains("last 14") || lower.contains("LAST_14_DAYS".toLowerCase(Locale.US))) {
            return "LAST_14_DAYS";
        }
        if (lower.contains("30 day") || lower.contains("last 30") || lower.contains("LAST_30_DAYS".toLowerCase(Locale.US))) {
            return "LAST_30_DAYS";
        }
        if (lower.contains("all athlete") || lower.contains("everyone") || lower.contains("ALL_ATHLETES".toLowerCase(Locale.US))) {
            return "ALL_ATHLETES";
        }
        return null;
    }

    private void appendGettingStartedPromptContext(
            StringBuilder sb,
            GettingStartedProgramResponse program,
            GettingStartedProgramPreviewResponse preview,
            List<GettingStartedCampaignSummaryResponse> activeCampaigns,
            GettingStartedCampaignAnalyticsResponse analytics,
            AdminPendingConfirmationResponse pending
    ) {
        if (program != null) {
            sb.append(String.format(
                    "GETTING STARTED PROGRAM #%d ('%s', version=%d, published=%s, audience=%s): %d days\n",
                    program.getId(), program.getTitle(), program.getVersion(),
                    program.isPublished(), program.getTargetAudience(),
                    program.getDays() != null ? program.getDays().size() : 0
            ));
            if (program.getDays() != null) {
                for (GettingStartedDayResponse day : program.getDays()) {
                    sb.append("  - Day ").append(day.getDayNumber()).append(" (").append(day.getTitle()).append("): ")
                            .append(day.getExercises() != null
                                    ? day.getExercises().stream().map(e -> e.getExerciseName() + " " + e.getSets() + "x"
                                    + (e.getReps() != null ? e.getReps() + "r" : e.getDurationSeconds() + "s")).toList()
                                    : List.of())
                            .append("\n");
                }
            }
        }
        if (preview != null) {
            sb.append(String.format(
                    "PROGRAM PREVIEW: valid=%s, audience=%s (%s), eligibleAthletes=%d/%d\n",
                    preview.isValid(), preview.getTargetAudience(), preview.getAudienceLabel(),
                    preview.getEligibleAthleteCount(), preview.getTotalAthleteCount()
            ));
        }
        if (activeCampaigns != null) {
            sb.append("ACTIVE CAMPAIGNS (" + activeCampaigns.size() + "): ")
                    .append(activeCampaigns.stream()
                            .map(c -> "#" + c.getProgramId() + " " + c.getTitle() + " [" + c.getTargetAudience() + ", eligible=" + c.getEligibleAthleteCount() + ", enrollments=" + c.getEnrollments() + "]")
                            .toList())
                    .append("\n");
        }
        if (analytics != null) {
            sb.append(String.format(
                    "CAMPAIGN ANALYTICS (#%d '%s'): eligible=%d, impressions=%d, dismissals=%d, detailViews=%d, enrollments=%d, day1=%.2f%%, day2=%.2f%%, day3=%.2f%%, overall=%.2f%%\n",
                    analytics.getProgramId(), analytics.getProgramTitle(), analytics.getEligibleAthleteCount(),
                    analytics.getModalImpressions(), analytics.getModalDismissals(), analytics.getProgramDetailViews(),
                    analytics.getEnrollments(), analytics.getDay1CompletionRate(), analytics.getDay2CompletionRate(),
                    analytics.getDay3CompletionRate(), analytics.getOverallCompletionRate()
            ));
        }
        if (pending != null) {
            sb.append("PENDING ADMIN CONFIRMATION REQUIRED: ").append(pending.getSummary())
                    .append(" [action=").append(pending.getActionType()).append(", status=NOT_YET_EXECUTED]\n");
        }
    }

    private AdminToolExecutionRecord record(
            AdminToolName name,
            String params,
            String summary,
            long count,
            long startMs
    ) {
        return AdminToolExecutionRecord.builder()
                .toolName(name.getToolName())
                .status("EXECUTED")
                .parametersSummary(params)
                .summary(summary)
                .recordCount(count)
                .durationMs(Math.max(0L, System.currentTimeMillis() - startMs))
                .executedAt(ZonedDateTime.now())
                .build();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static boolean isGettingStartedOrCampaignPrompt(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        return lower.contains("getting started")
                || lower.contains("onboarding")
                || lower.contains("3-day")
                || lower.contains("3 day")
                || lower.contains("campaign")
                || lower.contains("unpublish")
                || lower.contains("publish")
                || lower.contains("target audience")
                || lower.contains("audience targeting")
                || lower.contains("all athletes")
                || lower.contains("modal impression")
                || lower.contains("modal dismissal")
                || lower.contains("detail view")
                || (lower.contains("new members") && (lower.contains("14") || lower.contains("30")))
                || (lower.contains("preview") && (lower.contains("program") || lower.contains("draft") || lower.contains("campaign")))
                || ((lower.contains("day 1") || lower.contains("day 2") || lower.contains("day 3"))
                && (lower.contains("focus") || lower.contains("push") || lower.contains("pull")
                || lower.contains("leg") || lower.contains("core") || lower.contains("add")
                || lower.contains("remove") || lower.contains("reorder") || lower.contains("swap")
                || lower.contains("edit") || lower.contains("update") || lower.contains("change")
                || lower.contains("make") || lower.contains("set") || lower.contains("sets")
                || lower.contains("reps") || lower.contains("rest") || lower.contains("duration")
                || lower.contains("instruction")));
    }

    public static boolean isProManagementPrompt(String lower) {
        if (lower == null || lower.isBlank() || isGettingStartedOrCampaignPrompt(lower)) {
            return false;
        }
        if (!lower.matches(".*\\bpro\\b.*")) {
            return false;
        }
        return lower.contains("grant")
                || lower.contains("revoke")
                || lower.contains("remove")
                || lower.contains("make ")
                || lower.contains("give ")
                || lower.contains("upgrade ")
                || lower.contains("downgrade ")
                || lower.contains("enable ")
                || lower.contains("disable ")
                || lower.contains("strip ")
                || lower.contains("pro status")
                || lower.contains("pro access")
                || lower.contains("pro member")
                || lower.contains("pro membership")
                || lower.contains("unlimited pro")
                || lower.contains("have pro");
    }

    private ExerciseCategory parseGeminiExerciseCategory(GeminiAdminToolPlan plan) {
        if (plan == null || isBlank(plan.getExerciseCategory())) {
            return null;
        }
        return ExerciseDraftValidationService.parseCategoryEnum(plan.getExerciseCategory());
    }

    private String findImmediatePriorRecommendationContent(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        List<AdminAiMessage> recent = adminAiMessageRepository.findTop20ByConversationIdOrderByIdDesc(conversationId);
        for (AdminAiMessage msg : recent) {
            if ("assistant".equalsIgnoreCase(msg.getRole())) {
                if ("EXERCISE_DRAFT_CREATION".equalsIgnoreCase(msg.getIntent())) {
                    // Already-executed draft creations are not pending recommendations to re-add on vague follow-ups
                    return null;
                }
                if ("EXERCISE_RECOMMENDATION".equalsIgnoreCase(msg.getIntent())) {
                    return msg.getContent();
                }
                // Advisory/other reply: only match if it looks like an explicit numbered recommendation list
                String content = msg.getContent() != null ? msg.getContent() : "";
                if (!content.startsWith("### Added ")
                        && !content.startsWith("### ❌")
                        && !exerciseDraftValidationService.extractRequestedExercises(content).isEmpty()) {
                    return content;
                }
                // Stop at the most recent assistant message so older unrelated turns cannot leak stale recommendations
                return null;
            }
        }
        return null;
    }

    private boolean isExerciseDraftCreationFollowUp(String lower, Long conversationId) {
        if (lower == null || lower.isBlank() || conversationId == null
                || isGettingStartedOrCampaignPrompt(lower) || isProManagementPrompt(lower)) {
            return false;
        }
        boolean hasCreateVerb = lower.contains("add") || lower.contains("create")
                || lower.contains("save") || lower.contains("thêm") || lower.contains("lưu");
        boolean hasAnaphoricRef = lower.matches("(?s).*\\b(these|those|them|draft|suggested|recommended)\\b.*");
        boolean mentionsOtherTopic = lower.contains("program") || lower.contains("campaign") || lower.contains("user");
        if (!hasCreateVerb || !hasAnaphoricRef || mentionsOtherTopic) {
            return false;
        }
        return findImmediatePriorRecommendationContent(conversationId) != null;
    }

    public static boolean isExerciseDraftCreationPrompt(String lower) {
        if (lower == null || lower.isBlank()
                || isGettingStartedOrCampaignPrompt(lower)
                || isProManagementPrompt(lower)) {
            return false;
        }
        boolean hasExplicitListOrDirectDraftDirective = lower.matches("(?s).*\\b1[.)]\\s+.*")
                || lower.contains("add these")
                || lower.contains("add those")
                || lower.contains("to draft")
                || lower.contains("to the draft")
                || lower.matches("(?s).*\\b(add|create|save)\\s+\\d+\\s+.*");
        if (!hasExplicitListOrDirectDraftDirective
                && (lower.contains("what exercise") || lower.contains("which exercise")
                || lower.contains("missing exercise") || lower.contains("missing progression")
                || lower.contains("recommend exercises") || lower.contains("suggest exercises"))) {
            return false;
        }
        boolean mentionsExercise = lower.contains("exercise") || lower.contains("bài tập");
        boolean hasCreateVerb = lower.contains("add") || lower.contains("create")
                || lower.contains("generate") || lower.contains("save")
                || lower.contains("thêm") || lower.contains("tạo");
        boolean mentionsWorkoutProgram = (lower.contains("workout program") || lower.contains("draft program"))
                && !lower.contains("to draft") && !lower.contains("catalog") && !lower.contains("library");
        return mentionsExercise && hasCreateVerb && !mentionsWorkoutProgram;
    }
}
