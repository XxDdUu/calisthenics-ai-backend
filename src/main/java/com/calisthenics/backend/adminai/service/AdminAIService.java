package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.AdminExerciseService;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.adminai.dto.*;
import com.calisthenics.backend.adminai.dto.data.*;
import com.calisthenics.backend.adminai.entity.AdminAiAuditLog;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiMessage;
import com.calisthenics.backend.adminai.entity.AdminAiPendingAction;
import com.calisthenics.backend.adminai.entity.AdminAiRecommendation;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiPendingActionRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.type.*;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedDayResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedExerciseResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAIService {

    private final AdminConversationService conversationService;
    private final AdminContextService contextService;
    private final PlatformAnalyticsService platformAnalyticsService;
    private final TrainingAnalyticsService trainingAnalyticsService;
    private final ExerciseRecommendationService exerciseRecommendationService;
    private final WorkoutProgramRecommendationService workoutProgramRecommendationService;
    private final AdminExerciseService adminExerciseService;
    private final AdminAiRecommendationRepository recommendationRepository;
    private final AdminAiAuditLogRepository auditLogRepository;
    private final AdminAiPendingActionRepository pendingActionRepository;
    private final AdminToolRouter adminToolRouter;
    private final AdminGettingStartedToolService adminGettingStartedToolService;
    private final AdminProToolService adminProToolService;
    private final ExerciseDraftValidationService exerciseDraftValidationService;
    private final AdminPendingConfirmationSupport confirmationSupport;
    private final AdminAuthorizationService adminAuthorizationService;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminChatResponse chat(Long adminUserId, AdminChatRequest request) {
        User admin = conversationService.requireAdminUser(adminUserId);
        adminAuthorizationService.requireAuthenticatedAdmin();
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be blank");
        }

        String userPrompt = request.getMessage().trim();
        AdminAiConversation conversation = conversationService.resolveOrCreateConversation(adminUserId, request.getConversationId());
        AdminAiMessage savedUserMsg = conversationService.saveMessage(conversation, "user", userPrompt, null, null);

        Optional<AdminAiPendingAction> activePending = confirmationSupport
                .findActivePendingConfirmation(admin.getId(), conversation.getId());
        if (activePending.isPresent()) {
            boolean confirmed = confirmationSupport.isExplicitConfirmationMessage(userPrompt);
            boolean cancelled = !confirmed && confirmationSupport.isExplicitCancellationMessage(userPrompt);
            if (confirmed || cancelled) {
                AdminPendingActionType pendingType = activePending.get().getActionType();
                boolean isProPending = pendingType == AdminPendingActionType.GRANT_PRO
                        || pendingType == AdminPendingActionType.REVOKE_PRO;
                AdminConfirmationDecisionResponse decision = isProPending
                        ? adminProToolService.resolveConfirmationDecision(
                                admin, activePending.get().getConfirmationToken(), confirmed, conversation.getId())
                        : adminGettingStartedToolService.resolveConfirmationDecision(
                                admin, activePending.get().getConfirmationToken(), confirmed, conversation.getId());
                AdminAiIntent resolvedIntent = isProPending
                        ? AdminAiIntent.PRO_MANAGEMENT
                        : AdminAiIntent.CAMPAIGN_MANAGEMENT;
                List<AdminToolExecutionRecord> tools = decision.getExecution() != null
                        ? List.of(decision.getExecution())
                        : Collections.emptyList();
                AdminAiMessage savedAssistantMsg = conversationService.saveMessage(
                        conversation, "assistant", decision.getReply(), resolvedIntent.name(), null);
                conversationService.updateTitleIfDefault(conversation, generateConversationTitle(userPrompt, resolvedIntent));
                return AdminChatResponse.builder()
                        .conversationId(conversation.getId())
                        .conversationTitle(conversation.getTitle())
                        .userMessage(conversationService.mapToMessageResponse(savedUserMsg))
                        .assistantMessage(conversationService.mapToMessageResponse(savedAssistantMsg))
                        .intent(resolvedIntent.name())
                        .recommendations(Collections.emptyList())
                        .executedTools(tools)
                        .gettingStartedProgram(decision.getGettingStartedProgram())
                        .activeCampaigns(decision.getActiveCampaigns())
                        .updatedProUser(decision.getUpdatedUser())
                        .pendingConfirmation(null)
                        .suggestedFollowUps(buildSuggestedFollowUps(resolvedIntent, null))
                        .build();
            }
        }

        AdminChatRequest routedRequest = AdminChatRequest.builder()
                .conversationId(conversation.getId())
                .message(request.getMessage())
                .range(request.getRange())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .targetUserId(request.getTargetUserId())
                .includeEmail(request.getIncludeEmail())
                .build();

        AdminAnalyticsRange effectiveRange = resolveRangeFromPrompt(userPrompt, request.getRange());
        String detectedSkill = detectSkillFromPrompt(userPrompt);
        ExerciseCategory detectedCategory = ExerciseDraftValidationService.detectCategoryFromText(userPrompt);
        DifficultyLevel detectedLevel = detectDifficultyFromPrompt(userPrompt);

        AdminContextService.AdminFullContext context = contextService.buildContext(
                effectiveRange,
                request.getStartDate(),
                request.getEndDate(),
                detectedSkill
        );

        // Prepare multi-turn Admin conversation history for Gemini (never includes regular users' chats/emails)
        List<AdminAiMessage> history = new ArrayList<>(conversationService.getRecentHistoryForPrompt(conversation.getId()));
        Collections.reverse(history);
        List<GeminiRequestDto.Content> geminiContents = new ArrayList<>();
        for (AdminAiMessage m : history) {
            String role = "user".equalsIgnoreCase(m.getRole()) ? "user" : "model";
            geminiContents.add(GeminiRequestDto.Content.builder()
                    .role(role)
                    .parts(List.of(new GeminiRequestDto.Part(m.getContent())))
                    .build());
        }

        // Step 1: Tool planning (Gemini planner if configured + deterministic rule router)
        GeminiAdminToolPlan toolPlan = null;
        try {
            toolPlan = geminiApiClient.planAdminTools(buildToolPlannerSystemPrompt(), geminiContents).orElse(null);
        } catch (Exception ex) {
            log.warn("Admin AI tool planner call skipped/failed, using deterministic router: {}", ex.getMessage());
        }

        AdminAiIntent plannedIntent = resolveIntent(userPrompt, null, toolPlan, routedRequest);
        AdminToolRouter.RoutedChatToolsResult routedTools = adminToolRouter.routeAndExecuteForChat(
                userPrompt,
                routedRequest,
                plannedIntent,
                effectiveRange,
                request.getStartDate(),
                request.getEndDate(),
                detectedSkill,
                toolPlan
        );

        // If AI-supplied exercise_drafts failed validation, allow one corrective AI regeneration retry against the exact user spec
        if (routedTools.exerciseDraftResult() != null
                && !routedTools.exerciseDraftResult().isSuccess()
                && routedTools.exerciseDraftResult().isValidationFailed()
                && toolPlan != null
                && toolPlan.getExerciseDrafts() != null
                && !toolPlan.getExerciseDrafts().isEmpty()) {
            try {
                String correctionPrompt = buildExerciseDraftCorrectionPrompt(
                        userPrompt,
                        routedTools.exerciseDraftResult(),
                        toolPlan.getExerciseDrafts()
                );
                GeminiAdminToolPlan correctedPlan = geminiApiClient.planAdminTools(correctionPrompt, geminiContents).orElse(null);
                if (correctedPlan != null
                        && correctedPlan.getExerciseDrafts() != null
                        && !correctedPlan.getExerciseDrafts().isEmpty()) {
                    routedTools = adminToolRouter.routeAndExecuteForChat(
                            userPrompt,
                            routedRequest,
                            AdminAiIntent.EXERCISE_DRAFT_CREATION,
                            effectiveRange,
                            request.getStartDate(),
                            request.getEndDate(),
                            detectedSkill,
                            correctedPlan
                    );
                }
            } catch (Exception ex) {
                log.warn("Corrective AI exercise regeneration failed: {}", ex.getMessage());
            }
        }

        String baseSystemPrompt = contextService.buildAdminSystemPrompt(context);
        String fullSystemPrompt = baseSystemPrompt + "\n" + routedTools.sanitizedToolContextForPrompt();

        GeminiAdminAgentOutput geminiOutput = null;
        if (routedTools.pendingConfirmation() == null
                && routedTools.proToolResult() == null
                && routedTools.exerciseDraftResult() == null) {
            try {
                geminiOutput = geminiApiClient.callAdminAgent(fullSystemPrompt, geminiContents).orElse(null);
            } catch (Exception ex) {
                log.warn("Admin AI Gemini call failed, falling back to deterministic analytical engine: {}", ex.getMessage());
            }
        }

        AdminAiIntent intent = resolveIntentAfterTools(userPrompt, geminiOutput, toolPlan, routedRequest, routedTools);
        boolean hasPromptCategoryOrList = detectedCategory != null
                || !exerciseDraftValidationService.extractRequestedExercises(userPrompt).isEmpty();
        if (!hasPromptCategoryOrList
                && geminiOutput != null
                && geminiOutput.getTargetSkill() != null
                && !geminiOutput.getTargetSkill().isBlank()) {
            detectedSkill = geminiOutput.getTargetSkill().trim();
        }
        String recommendationFilter = detectedSkill != null
                ? detectedSkill
                : (detectedCategory != null ? detectedCategory.name() : null);

        List<AdminRecommendationResponse> generatedRecommendations = new ArrayList<>();
        boolean skipGlobalRecs = isGettingStartedOrCampaignIntent(intent)
                || intent == AdminAiIntent.PRO_MANAGEMENT
                || intent == AdminAiIntent.EXERCISE_DRAFT_CREATION;
        boolean wantsGlobalRecs = !skipGlobalRecs && ((geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateRecommendations()))
                || (userPrompt.toLowerCase().contains("recommend")
                && !userPrompt.toLowerCase().contains("exercise")
                && !userPrompt.toLowerCase().contains("workout program")));

        if (!skipGlobalRecs && (intent == AdminAiIntent.EXERCISE_RECOMMENDATION
                || (geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateExerciseRecommendations())))) {
            generatedRecommendations.addAll(createExerciseRecommendations(admin, conversation, recommendationFilter, detectedLevel));
        } else if (!skipGlobalRecs && (intent == AdminAiIntent.WORKOUT_PROGRAM_RECOMMENDATION
                || (geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateWorkoutProgram())))) {
            String levelStr = detectedLevel != null ? detectedLevel.name() : (geminiOutput != null ? geminiOutput.getTargetLevel() : null);
            String focus = detectedSkill != null ? detectedSkill : userPrompt;
            generatedRecommendations.addAll(createWorkoutProgramRecommendations(admin, conversation, focus, levelStr));
        } else if (!skipGlobalRecs && (intent == AdminAiIntent.OPERATIONAL_INSIGHTS
                || (geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateOperationalInsights()))
                || wantsGlobalRecs)) {
            List<AdminContextService.OperationalInsightCandidate> combinedInsights = !routedTools.dataRecommendations().isEmpty()
                    ? routedTools.dataRecommendations()
                    : context.operationalInsights();
            generatedRecommendations.addAll(createOperationalRecommendations(admin, conversation, combinedInsights));
        }

        boolean requireDeterministicReply = routedTools.pendingConfirmation() != null
                || routedTools.proToolResult() != null
                || routedTools.exerciseDraftResult() != null
                || intent == AdminAiIntent.EXERCISE_RECOMMENDATION
                || (geminiOutput != null && claimsUnsubstantiatedExerciseCreation(geminiOutput.getReply(), routedTools));
        String replyContent = (!requireDeterministicReply
                && geminiOutput != null
                && geminiOutput.getReply() != null
                && !geminiOutput.getReply().isBlank())
                ? geminiOutput.getReply()
                : buildDataGroundedReply(intent, userPrompt, recommendationFilter, context, generatedRecommendations, routedTools);

        List<String> followUps = (geminiOutput != null && geminiOutput.getSuggestedFollowUps() != null && !geminiOutput.getSuggestedFollowUps().isEmpty())
                ? geminiOutput.getSuggestedFollowUps()
                : buildSuggestedFollowUps(intent, detectedSkill);

        String metadataJson = buildMessageMetadata(intent, generatedRecommendations, context.overview(), routedTools.executedTools());
        AdminAiMessage savedAssistantMsg = conversationService.saveMessage(
                conversation,
                "assistant",
                replyContent,
                intent.name(),
                metadataJson
        );

        conversationService.updateTitleIfDefault(conversation, generateConversationTitle(userPrompt, intent));

        List<AdminExerciseResponse> createdDrafts = routedTools.exerciseDraftResult() != null
                ? routedTools.exerciseDraftResult().getCreatedExercises()
                : null;

        return AdminChatResponse.builder()
                .conversationId(conversation.getId())
                .conversationTitle(conversation.getTitle())
                .userMessage(conversationService.mapToMessageResponse(savedUserMsg))
                .assistantMessage(conversationService.mapToMessageResponse(savedAssistantMsg))
                .intent(intent.name())
                .recommendations(generatedRecommendations)
                .platformOverview(context.overview())
                .trainingAnalytics(context.trainingAnalytics())
                .executedTools(routedTools.executedTools())
                .timeRangeUsed(routedTools.timeRange())
                .userStatistics(routedTools.userStatistics())
                .matchedUsers(routedTools.matchedUsers())
                .inspectedUserDetail(routedTools.inspectedUserDetail())
                .segmentAnalysis(routedTools.segmentAnalysis())
                .platformEngagement(routedTools.platformEngagement())
                .coachClientStatistics(routedTools.coachClientStatistics())
                .gettingStartedProgram(routedTools.gettingStartedProgram())
                .gettingStartedPreview(routedTools.gettingStartedPreview())
                .activeCampaigns(routedTools.activeCampaigns())
                .campaignAnalytics(routedTools.campaignAnalytics())
                .pendingConfirmation(routedTools.pendingConfirmation())
                .updatedProUser(routedTools.proToolResult() != null && routedTools.proToolResult().isExecuted()
                        ? routedTools.proToolResult().getTargetUser()
                        : null)
                .proCandidates(routedTools.proToolResult() != null
                        ? routedTools.proToolResult().getCandidates()
                        : null)
                .createdExercises(createdDrafts)
                .exerciseDraftResult(routedTools.exerciseDraftResult())
                .missingDataNotes(routedTools.missingDataNotes())
                .suggestedFollowUps(followUps)
                .build();
    }

    @Transactional
    public AdminConfirmationDecisionResponse resolveConfirmation(
            Long adminUserId,
            String confirmationToken,
            AdminConfirmationDecisionRequest request
    ) {
        User admin = conversationService.requireAdminUser(adminUserId);
        adminAuthorizationService.requireAuthenticatedAdmin();
        boolean approved = request == null || request.getApproved() == null || Boolean.TRUE.equals(request.getApproved());
        Long convHint = request != null ? request.getConversationId() : null;

        AdminPendingActionType actionType = pendingActionRepository
                .findByConfirmationTokenAndAdminUserId(confirmationToken, admin.getId())
                .map(AdminAiPendingAction::getActionType)
                .orElse(AdminPendingActionType.PUBLISH_PROGRAM);

        boolean isProPending = actionType == AdminPendingActionType.GRANT_PRO
                || actionType == AdminPendingActionType.REVOKE_PRO;
        AdminConfirmationDecisionResponse decision = isProPending
                ? adminProToolService.resolveConfirmationDecision(admin, confirmationToken, approved, convHint)
                : adminGettingStartedToolService.resolveConfirmationDecision(admin, confirmationToken, approved, convHint);

        AdminAiIntent decisionIntent = isProPending
                ? AdminAiIntent.PRO_MANAGEMENT
                : AdminAiIntent.CAMPAIGN_MANAGEMENT;
        if (decision.getConversationId() != null) {
            conversationService.findOwnedConversation(adminUserId, decision.getConversationId())
                    .ifPresent(conv -> conversationService.saveMessage(
                            conv, "assistant", decision.getReply(), decisionIntent.name(), null));
        }
        return decision;
    }

    @Transactional(readOnly = true)
    public List<AdminAiAuditLogResponse> getAuditLogs(Long adminUserId) {
        conversationService.requireAdminUser(adminUserId);
        adminAuthorizationService.requireAuthenticatedAdmin();
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(logEntry -> AdminAiAuditLogResponse.builder()
                        .id(logEntry.getId())
                        .adminUserId(logEntry.getAdminUser() != null ? logEntry.getAdminUser().getId() : null)
                        .action(logEntry.getAction())
                        .targetType(logEntry.getTargetType())
                        .targetId(logEntry.getTargetId())
                        .details(logEntry.getDetails())
                        .createdAt(logEntry.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminRecommendationResponse> getRecommendations(
            Long adminUserId,
            AdminRecommendationType type,
            AdminRecommendationStatus status
    ) {
        conversationService.requireAdminUser(adminUserId);
        List<AdminAiRecommendation> list;
        if (type != null && status != null) {
            list = recommendationRepository.findByRecommendationTypeAndStatusOrderByCreatedAtDesc(type, status);
        } else if (type != null) {
            list = recommendationRepository.findByRecommendationTypeOrderByCreatedAtDesc(type);
        } else if (status != null) {
            list = recommendationRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            list = recommendationRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::mapToRecommendationResponse).toList();
    }

    @Transactional
    public List<AdminRecommendationResponse> createRecommendations(
            Long adminUserId,
            CreateAdminRecommendationRequest request
    ) {
        User admin = conversationService.requireAdminUser(adminUserId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recommendation request is required");
        }

        AdminRecommendationType type = request.getType() != null
                ? request.getType()
                : AdminRecommendationType.EXERCISE;

        // If explicit payload is provided, persist that single recommendation directly
        if (request.getExerciseDetails() != null || request.getProgramDetails() != null || request.getOperationalDetails() != null) {
            Object payload = switch (type) {
                case EXERCISE -> request.getExerciseDetails();
                case WORKOUT_PROGRAM -> request.getProgramDetails();
                case OPERATIONAL -> request.getOperationalDetails();
            };
            AdminRecommendationStatus initialStatus = request.getStatus() != null
                    ? request.getStatus()
                    : (type == AdminRecommendationType.WORKOUT_PROGRAM
                            ? AdminRecommendationStatus.DRAFT
                            : AdminRecommendationStatus.PENDING_APPROVAL);
            AdminRecommendationPriority priority = request.getPriority() != null
                    ? request.getPriority()
                    : AdminRecommendationPriority.MEDIUM;
            String title = request.getTitle() != null && !request.getTitle().isBlank()
                    ? request.getTitle().trim()
                    : "Admin AI Recommendation";

            AdminAiRecommendation saved = saveRecommendationEntity(
                    type,
                    initialStatus,
                    priority,
                    title,
                    request.getSummary(),
                    request.getRationale(),
                    payload,
                    null,
                    admin
            );
            recordAudit(admin, "RECOMMENDATION_CREATED", type.name(), saved.getId(), "Created recommendation: " + title);
            return List.of(mapToRecommendationResponse(saved));
        }

        // Otherwise generate via AI recommendation engines
        DifficultyLevel level = detectDifficultyFromPrompt(request.getTrainingLevel());
        return switch (type) {
            case EXERCISE -> createExerciseRecommendations(admin, null, request.getTargetSkill(), level);
            case WORKOUT_PROGRAM -> createWorkoutProgramRecommendations(
                    admin,
                    null,
                    request.getTargetSkill() != null ? request.getTargetSkill() : request.getPrompt(),
                    request.getTrainingLevel()
            );
            case OPERATIONAL -> {
                AdminContextService.AdminFullContext ctx = contextService.buildContext(
                        AdminAnalyticsRange.LAST_30_DAYS, null, null, null);
                yield createOperationalRecommendations(admin, null, ctx.operationalInsights());
            }
        };
    }

    @Transactional
    public AdminRecommendationResponse approveRecommendation(
            Long adminUserId,
            Long recommendationId,
            ReviewAdminRecommendationRequest request
    ) {
        User admin = conversationService.requireAdminUser(adminUserId);
        AdminAiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));

        if (rec.getStatus() == AdminRecommendationStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rejected recommendations cannot be approved");
        }

        boolean publishImmediately = request != null && Boolean.TRUE.equals(request.getPublishExercise());

        if (rec.getRecommendationType() == AdminRecommendationType.EXERCISE) {
            ExerciseRecommendationPayload payload = deserializePayload(rec.getPayloadJson(), ExerciseRecommendationPayload.class);
            if (payload != null) {
                AdminExerciseResponse createdExercise =
                        exerciseRecommendationService.createExerciseFromRecommendation(payload, publishImmediately);
                rec.setCreatedResourceId(createdExercise.id());
                rec.setCreatedResourceType("EXERCISE");
                recordAudit(
                        admin,
                        "EXERCISE_CREATED_FROM_RECOMMENDATION",
                        "EXERCISE",
                        createdExercise.id(),
                        "Created exercise '" + createdExercise.name() + "' (active=" + createdExercise.active() + ") from recommendation #" + rec.getId()
                );
            }
            rec.setStatus(publishImmediately ? AdminRecommendationStatus.IMPLEMENTED : AdminRecommendationStatus.APPROVED);
        } else if (rec.getRecommendationType() == AdminRecommendationType.WORKOUT_PROGRAM) {
            WorkoutProgramRecommendationPayload payload =
                    deserializePayload(rec.getPayloadJson(), WorkoutProgramRecommendationPayload.class);
            if (payload != null) {
                WorkoutPlan draftPlan =
                        workoutProgramRecommendationService.createDraftWorkoutPlanFromRecommendation(payload, admin);
                rec.setCreatedResourceId(draftPlan.getId());
                rec.setCreatedResourceType("WORKOUT_PLAN");
                recordAudit(
                        admin,
                        "DRAFT_PLAN_CREATED_FROM_RECOMMENDATION",
                        "WORKOUT_PLAN",
                        draftPlan.getId(),
                        "Created draft workout plan '" + draftPlan.getName() + "' (active=false) from recommendation #" + rec.getId()
                );
            }
            rec.setStatus(AdminRecommendationStatus.APPROVED);
        } else {
            rec.setStatus(AdminRecommendationStatus.APPROVED);
        }

        rec.setReviewedByAdmin(admin);
        rec.setReviewedAt(ZonedDateTime.now());
        if (request != null && request.getReviewNotes() != null && !request.getReviewNotes().isBlank()) {
            rec.setReviewNotes(request.getReviewNotes().trim());
        }

        AdminAiRecommendation saved = recommendationRepository.save(rec);
        recordAudit(
                admin,
                "RECOMMENDATION_APPROVED",
                rec.getRecommendationType().name(),
                saved.getId(),
                "Approved recommendation '" + saved.getTitle() + "' with status " + saved.getStatus()
        );
        return mapToRecommendationResponse(saved);
    }

    @Transactional
    public AdminRecommendationResponse rejectRecommendation(
            Long adminUserId,
            Long recommendationId,
            ReviewAdminRecommendationRequest request
    ) {
        User admin = conversationService.requireAdminUser(adminUserId);
        AdminAiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));

        if (rec.getStatus() == AdminRecommendationStatus.IMPLEMENTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Implemented recommendations cannot be rejected");
        }

        rec.setStatus(AdminRecommendationStatus.REJECTED);
        rec.setReviewedByAdmin(admin);
        rec.setReviewedAt(ZonedDateTime.now());
        if (request != null && request.getReviewNotes() != null && !request.getReviewNotes().isBlank()) {
            rec.setReviewNotes(request.getReviewNotes().trim());
        }

        AdminAiRecommendation saved = recommendationRepository.save(rec);
        recordAudit(
                admin,
                "RECOMMENDATION_REJECTED",
                rec.getRecommendationType().name(),
                saved.getId(),
                "Rejected recommendation '" + saved.getTitle() + "'"
        );
        return mapToRecommendationResponse(saved);
    }

    @Transactional
    public AdminRecommendationResponse confirmImplementation(Long adminUserId, Long recommendationId) {
        User admin = conversationService.requireAdminUser(adminUserId);
        AdminAiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));

        if (rec.getStatus() == AdminRecommendationStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rejected recommendations cannot be implemented");
        }

        if (rec.getRecommendationType() == AdminRecommendationType.EXERCISE && rec.getCreatedResourceId() != null) {
            adminExerciseService.updateStatus(rec.getCreatedResourceId(), true);
        }

        rec.setStatus(AdminRecommendationStatus.IMPLEMENTED);
        rec.setReviewedByAdmin(admin);
        rec.setReviewedAt(ZonedDateTime.now());
        AdminAiRecommendation saved = recommendationRepository.save(rec);
        recordAudit(
                admin,
                "RECOMMENDATION_IMPLEMENTED",
                rec.getRecommendationType().name(),
                saved.getId(),
                "Confirmed publication/implementation of recommendation '" + saved.getTitle() + "'"
        );
        return mapToRecommendationResponse(saved);
    }

    private List<AdminRecommendationResponse> createExerciseRecommendations(
            User admin,
            AdminAiConversation conversation,
            String targetSkill,
            DifficultyLevel difficulty
    ) {
        List<ExerciseRecommendationPayload> candidates =
                exerciseRecommendationService.analyzeMissingExercises(targetSkill, difficulty);

        List<AdminRecommendationResponse> savedList = new ArrayList<>();
        for (ExerciseRecommendationPayload p : candidates.stream().limit(6).toList()) {
            String title = "Add Exercise: " + p.getName() + " (" + p.getTargetSkill() + ")";
            List<AdminAiRecommendation> existing = recommendationRepository.findByRecommendationTypeOrderByCreatedAtDesc(
                    AdminRecommendationType.EXERCISE);
            Optional<AdminAiRecommendation> match = existing.stream()
                    .filter(r -> r.getTitle().equalsIgnoreCase(title)
                            && r.getStatus() != AdminRecommendationStatus.REJECTED)
                    .findFirst();

            if (match.isPresent()) {
                savedList.add(mapToRecommendationResponse(match.get()));
                continue;
            }

            AdminAiRecommendation saved = saveRecommendationEntity(
                    AdminRecommendationType.EXERCISE,
                    AdminRecommendationStatus.PENDING_APPROVAL,
                    AdminRecommendationPriority.MEDIUM,
                    title,
                    p.getDescription(),
                    p.getWhyAdd(),
                    p,
                    conversation,
                    admin
            );
            recordAudit(admin, "RECOMMENDATION_GENERATED", "EXERCISE", saved.getId(), "Generated exercise recommendation: " + p.getName());
            savedList.add(mapToRecommendationResponse(saved));
        }
        return savedList;
    }

    private List<AdminRecommendationResponse> createWorkoutProgramRecommendations(
            User admin,
            AdminAiConversation conversation,
            String focusQuery,
            String requestedLevel
    ) {
        List<WorkoutProgramRecommendationPayload> programs =
                workoutProgramRecommendationService.generateProgramRecommendations(focusQuery, requestedLevel);

        List<AdminRecommendationResponse> savedList = new ArrayList<>();
        for (WorkoutProgramRecommendationPayload p : programs.stream().limit(3).toList()) {
            String title = "Draft Program: " + p.getProgramName();
            List<AdminAiRecommendation> existing = recommendationRepository.findByRecommendationTypeOrderByCreatedAtDesc(
                    AdminRecommendationType.WORKOUT_PROGRAM);
            Optional<AdminAiRecommendation> match = existing.stream()
                    .filter(r -> r.getTitle().equalsIgnoreCase(title)
                            && r.getStatus() != AdminRecommendationStatus.REJECTED)
                    .findFirst();

            if (match.isPresent()) {
                savedList.add(mapToRecommendationResponse(match.get()));
                continue;
            }

            String summary = String.format("%s | Level: %s | %d days/week (%d weeks)",
                    p.getTargetAudience(), p.getTrainingLevel(), p.getWeeklyFrequency(), p.getDurationWeeks());

            AdminAiRecommendation saved = saveRecommendationEntity(
                    AdminRecommendationType.WORKOUT_PROGRAM,
                    AdminRecommendationStatus.DRAFT,
                    AdminRecommendationPriority.MEDIUM,
                    title,
                    summary,
                    p.getRationale(),
                    p,
                    conversation,
                    admin
            );
            recordAudit(admin, "RECOMMENDATION_GENERATED", "WORKOUT_PROGRAM", saved.getId(), "Generated draft workout program: " + p.getProgramName());
            savedList.add(mapToRecommendationResponse(saved));
        }
        return savedList;
    }

    private List<AdminRecommendationResponse> createOperationalRecommendations(
            User admin,
            AdminAiConversation conversation,
            List<AdminContextService.OperationalInsightCandidate> insights
    ) {
        List<AdminRecommendationResponse> savedList = new ArrayList<>();
        for (AdminContextService.OperationalInsightCandidate item : insights) {
            List<AdminAiRecommendation> existing = recommendationRepository.findByRecommendationTypeOrderByCreatedAtDesc(
                    AdminRecommendationType.OPERATIONAL);
            Optional<AdminAiRecommendation> match = existing.stream()
                    .filter(r -> r.getTitle().equalsIgnoreCase(item.title())
                            && r.getStatus() != AdminRecommendationStatus.REJECTED)
                    .findFirst();

            if (match.isPresent()) {
                savedList.add(mapToRecommendationResponse(match.get()));
                continue;
            }

            AdminAiRecommendation saved = saveRecommendationEntity(
                    AdminRecommendationType.OPERATIONAL,
                    AdminRecommendationStatus.PENDING_APPROVAL,
                    item.priority(),
                    item.title(),
                    item.summary(),
                    item.rationale(),
                    item.payload(),
                    conversation,
                    admin
            );
            recordAudit(admin, "RECOMMENDATION_GENERATED", "OPERATIONAL", saved.getId(), "Generated operational insight: " + item.title());
            savedList.add(mapToRecommendationResponse(saved));
        }
        return savedList;
    }

    private AdminAiRecommendation saveRecommendationEntity(
            AdminRecommendationType type,
            AdminRecommendationStatus status,
            AdminRecommendationPriority priority,
            String title,
            String summary,
            String rationale,
            Object payload,
            AdminAiConversation conversation,
            User admin
    ) {
        String payloadJson = "{}";
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize recommendation payload: {}", ex.getMessage());
            }
        }

        AdminAiRecommendation entity = AdminAiRecommendation.builder()
                .recommendationType(type)
                .status(status)
                .priority(priority)
                .title(title)
                .summary(summary)
                .rationale(rationale)
                .payloadJson(payloadJson)
                .conversation(conversation)
                .createdByAdmin(admin)
                .build();
        return recommendationRepository.save(entity);
    }

    public AdminRecommendationResponse mapToRecommendationResponse(AdminAiRecommendation r) {
        ExerciseRecommendationPayload exPayload = null;
        WorkoutProgramRecommendationPayload progPayload = null;
        OperationalRecommendationPayload opPayload = null;

        if (r.getRecommendationType() == AdminRecommendationType.EXERCISE) {
            exPayload = deserializePayload(r.getPayloadJson(), ExerciseRecommendationPayload.class);
        } else if (r.getRecommendationType() == AdminRecommendationType.WORKOUT_PROGRAM) {
            progPayload = deserializePayload(r.getPayloadJson(), WorkoutProgramRecommendationPayload.class);
        } else if (r.getRecommendationType() == AdminRecommendationType.OPERATIONAL) {
            opPayload = deserializePayload(r.getPayloadJson(), OperationalRecommendationPayload.class);
        }

        return AdminRecommendationResponse.builder()
                .id(r.getId())
                .type(r.getRecommendationType())
                .status(r.getStatus())
                .priority(r.getPriority())
                .title(r.getTitle())
                .summary(r.getSummary())
                .rationale(r.getRationale())
                .exerciseDetails(exPayload)
                .programDetails(progPayload)
                .operationalDetails(opPayload)
                .conversationId(r.getConversation() != null ? r.getConversation().getId() : null)
                .createdByAdminId(r.getCreatedByAdmin() != null ? r.getCreatedByAdmin().getId() : null)
                .reviewedByAdminId(r.getReviewedByAdmin() != null ? r.getReviewedByAdmin().getId() : null)
                .reviewNotes(r.getReviewNotes())
                .createdResourceId(r.getCreatedResourceId())
                .createdResourceType(r.getCreatedResourceType())
                .reviewedAt(r.getReviewedAt())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private <T> T deserializePayload(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            log.warn("Could not deserialize {} from JSON: {}", clazz.getSimpleName(), ex.getMessage());
            return null;
        }
    }

    private void recordAudit(User admin, String action, String targetType, Long targetId, String details) {
        AdminAiAuditLog audit = AdminAiAuditLog.builder()
                .adminUser(admin)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build();
        auditLogRepository.save(audit);
        log.info("[ADMIN-AI-AUDIT] adminId={} action={} targetType={} targetId={} details={}",
                admin.getId(), action, targetType, targetId, details);
    }

    private String buildToolPlannerSystemPrompt() {
        return """
                You are the Admin AI Tool Planner for Calisthenics AI.
                Decide which authorized administrative tools should execute to gather exact database facts or perform Getting Started – 3 Days program/campaign actions.
                Available tools:
                - get_user_statistics
                - get_all_users
                - get_user_details
                - get_user_workout_history
                - get_user_personal_records
                - get_user_progress
                - get_user_activity
                - get_coach_client_statistics
                - get_platform_engagement
                - analyze_user_segments
                - generate_admin_recommendations
                - createProgram (generate/create a 3-day beginner Getting Started program draft)
                - updateProgram (edit existing Getting Started program days, exercises, sets/reps/duration/rest/instructions, or focus)
                - previewProgram (preview Getting Started program and check eligible audience size)
                - publishProgram (request publication approval; extract target_audience ALL_ATHLETES / LAST_30_DAYS / LAST_14_DAYS if stated)
                - unpublishProgram (request confirmation before unpublishing the active Getting Started campaign)
                - configureAudience (set target_audience to ALL_ATHLETES, LAST_30_DAYS, or LAST_14_DAYS)
                - getActiveCampaigns (list currently published Getting Started campaigns)
                - getCampaignAnalytics (retrieve eligible athlete count, modal impressions, dismissals, detail views, enrollments, Day 1/2/3 & overall completion rates)
                - grantPro (prepare a confirmation request before granting unlimited admin PRO access to target_user_identifier [email, username, or user ID])
                - revokePro (prepare a confirmation request before revoking admin-granted PRO access from target_user_identifier while preserving paid subscriptions)
                - checkProStatus (inspect a user's current effective tier, paid subscription, and admin-granted PRO status)
                - create_exercise_drafts (persist new AI-recommended exercises directly in the exercises catalog with DRAFT status and AI_RECOMMENDED origin when admin asks to add/create exercises; MUST set exercise_category matching the latest user prompt and populate exercise_drafts with the EXACT requested exercise names, categories, difficulties, descriptions, instructions, target_muscles, and equipment. NEVER substitute unrelated skills like Front Lever or reuse stale exercises/skill_keyword from earlier turns)
                Never fabricate data. Never request raw user passwords or private message text.
                """;
    }

    private String buildExerciseDraftCorrectionPrompt(
            String userPrompt,
            ExerciseDraftBatchResultResponse failedResult,
            List<ExerciseDraftCandidateDto> rejectedDrafts
    ) {
        return buildToolPlannerSystemPrompt() + "\n\n"
                + "CORRECTION REQUIRED — YOUR PREVIOUS EXERCISE DRAFTS WERE REJECTED BEFORE PERSISTENCE:\n"
                + "- Latest user request: " + userPrompt + "\n"
                + "- Validation failure reason: " + failedResult.getErrorMessage() + "\n"
                + "- Rejected names: " + rejectedDrafts.stream().map(ExerciseDraftCandidateDto::getName).toList() + "\n"
                + "Regenerate create_exercise_drafts using ONLY the exact requested exercise names, categories, and difficulty levels.\n";
    }

    private AdminAiIntent resolveIntent(
            String userPrompt,
            GeminiAdminAgentOutput geminiOutput,
            GeminiAdminToolPlan toolPlan,
            AdminChatRequest request
    ) {
        if (geminiOutput != null && geminiOutput.getIntent() != null) {
            try {
                return AdminAiIntent.valueOf(geminiOutput.getIntent().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (request != null && request.getTargetUserId() != null) {
            return AdminAiIntent.USER_DRILL_DOWN;
        }
        String lower = userPrompt != null ? userPrompt.toLowerCase() : "";

        if (AdminToolRouter.isGettingStartedOrCampaignPrompt(lower)) {
            if (lower.contains("analytics") || lower.contains("impression") || lower.contains("dismissal")
                    || lower.contains("detail view") || lower.contains("completion rate")) {
                return AdminAiIntent.CAMPAIGN_ANALYTICS;
            }
            if (lower.contains("publish") || lower.contains("unpublish")
                    || lower.contains("target audience") || lower.contains("active") && lower.contains("campaign")
                    || lower.contains("all athletes")) {
                return AdminAiIntent.CAMPAIGN_MANAGEMENT;
            }
            return AdminAiIntent.GETTING_STARTED_PROGRAM_MANAGEMENT;
        }

        if (AdminToolRouter.isProManagementPrompt(lower)) {
            return AdminAiIntent.PRO_MANAGEMENT;
        }

        if (AdminToolRouter.isExerciseDraftCreationPrompt(lower)) {
            return AdminAiIntent.EXERCISE_DRAFT_CREATION;
        }

        if (lower.contains("what exercises")
                || lower.contains("add exercise")
                || lower.contains("missing exercise")
                || lower.contains("missing progression")
                || lower.contains("progression")
                || ((lower.contains("recommend") || lower.contains("suggest")) && lower.contains("exercise"))
                || ((detectSkillFromPrompt(userPrompt) != null || ExerciseDraftValidationService.detectCategoryFromText(userPrompt) != null)
                        && (lower.contains("exercise") || lower.contains("library") || lower.contains("add")))) {
            return AdminAiIntent.EXERCISE_RECOMMENDATION;
        }
        if (lower.contains("workout program")
                || lower.contains("draft program")
                || lower.contains("suggest new workout")
                || lower.contains("suggest workout")
                || lower.contains("propose a program")
                || (lower.contains("program") && (lower.contains("suggest") || lower.contains("create") || lower.contains("recommend") || lower.contains("draft")))) {
            return AdminAiIntent.WORKOUT_PROGRAM_RECOMMENDATION;
        }
        if (lower.contains("inspect user")
                || lower.contains("user details")
                || lower.contains("details for user")
                || lower.contains("workout history for")
                || lower.contains("personal records for")
                || lower.contains("prs for ")
                || lower.contains("progress for ")
                || lower.contains("activity for ")
                || lower.contains("activity of ")
                || lower.matches(".*\\buser(?:\\s+id|\\s*#)?\\s*[:#]?\\s*\\d+.*")) {
            return AdminAiIntent.USER_DRILL_DOWN;
        }
        if (lower.contains("active coach")
                || lower.contains("coach-client")
                || lower.contains("coach relationship")
                || lower.contains("top coaches")
                || lower.contains("pending invitation")
                || (lower.contains("coach") && (lower.contains("how many") || lower.contains("stats") || lower.contains("statistics")))) {
            return AdminAiIntent.COACH_CLIENT_ANALYTICS;
        }
        if (lower.contains("not completed any workouts")
                || lower.contains("users who have not")
                || lower.contains("not updated their personal records")
                || lower.contains("stale pr")
                || lower.contains("segment")
                || lower.contains("filter users")
                || lower.contains("users without")
                || lower.contains("no coach")
                || lower.contains("completion rate below")
                || lower.contains("completion rate above")) {
            return AdminAiIntent.USER_SEGMENTATION_ANALYSIS;
        }
        if (lower.contains("improved their")
                || lower.contains("most improved")
                || lower.contains("consistency has declined")
                || lower.contains("declining consistency")
                || lower.contains("experience level")
                || lower.contains("compare user engagement")
                || lower.contains("stickiness")
                || (lower.contains("compare") && lower.contains("engagement"))) {
            return AdminAiIntent.PLATFORM_ENGAGEMENT_COMPARISON;
        }
        if (lower.contains("how many users")
                || lower.contains("currently registered")
                || lower.contains("total registered users")
                || lower.contains("search user")
                || lower.contains("find user")
                || lower.contains("list users")) {
            return AdminAiIntent.USER_STATISTICS_AND_SEARCH;
        }
        if (lower.contains("need improvement")
                || lower.contains("operational")
                || lower.contains("bottleneck")
                || lower.contains("opportunity")
                || lower.contains("opportunities")
                || lower.contains("issues")
                || lower.contains("attention")
                || lower.contains("at risk of becoming inactive")
                || lower.contains("benefit from coach")
                || lower.contains("weaknesses in current")) {
            return AdminAiIntent.OPERATIONAL_INSIGHTS;
        }
        if (lower.contains("trend")
                || lower.contains("frequently performed")
                || lower.contains("most performed")
                || lower.contains("completion rate")
                || lower.contains("inactive")
                || lower.contains("following assigned")
                || lower.contains("focusing on")
                || lower.contains("training goal")) {
            return AdminAiIntent.USER_TRAINING_ANALYTICS;
        }
        if (toolPlan != null && toolPlan.getIntent() != null) {
            try {
                return AdminAiIntent.valueOf(toolPlan.getIntent().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (lower.contains("summarize")
                || lower.contains("overview")
                || lower.contains("platform activity")
                || lower.contains("training activity")
                || lower.contains("active users")
                || lower.contains("statistics")
                || lower.contains("this month")
                || lower.contains("last 7 days")
                || lower.contains("today")) {
            return AdminAiIntent.PLATFORM_OVERVIEW;
        }
        return AdminAiIntent.PLATFORM_OVERVIEW;
    }

    private AdminAnalyticsRange resolveRangeFromPrompt(String prompt, AdminAnalyticsRange explicitRange) {
        if (explicitRange != null) {
            return explicitRange;
        }
        String lower = prompt.toLowerCase();
        if (lower.contains("today")) {
            return AdminAnalyticsRange.TODAY;
        }
        if (lower.contains("7 day") || lower.contains("week") || lower.contains("weekly")) {
            return AdminAnalyticsRange.LAST_7_DAYS;
        }
        return AdminAnalyticsRange.LAST_30_DAYS;
    }

    private String detectSkillFromPrompt(String prompt) {
        if (prompt == null) {
            return null;
        }
        String lower = prompt.toLowerCase();
        if (lower.contains("planche")) return "Planche";
        if (lower.contains("front lever")) return "Front Lever";
        if (lower.contains("back lever")) return "Back Lever";
        if (lower.contains("handstand")) return "Handstand";
        if (lower.contains("muscle-up") || lower.contains("muscle up")) return "Muscle-up";
        if (lower.contains("human flag") || lower.contains("flag")) return "Human Flag";
        return null;
    }

    private DifficultyLevel detectDifficultyFromPrompt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase();
        if (lower.contains("beginner")) return DifficultyLevel.BEGINNER;
        if (lower.contains("intermediate")) return DifficultyLevel.INTERMEDIATE;
        if (lower.contains("advanced")) return DifficultyLevel.ADVANCED;
        if (lower.contains("elite")) return DifficultyLevel.ELITE;
        return null;
    }

    private String buildDataGroundedReply(
            AdminAiIntent intent,
            String userPrompt,
            String detectedSkill,
            AdminContextService.AdminFullContext context,
            List<AdminRecommendationResponse> recommendations,
            AdminToolRouter.RoutedChatToolsResult routedTools
    ) {
        AdminPlatformOverviewResponse ov = context.overview();
        AdminTrainingAnalyticsResponse tr = context.trainingAnalytics();
        AdminTimeRangeMetadata rangeMeta = routedTools.timeRange();
        StringBuilder sb = new StringBuilder();

        switch (intent) {
            case PLATFORM_OVERVIEW -> {
                sb.append("### 📊 Platform Activity Summary (").append(ov.getRange()).append(": ")
                        .append(ov.getStartDate()).append(" to ").append(ov.getEndDate()).append(")\n\n");
                sb.append("#### 1. Measured Platform & Coaching Metrics\n");
                sb.append("- **Total Registered Users**: ").append(ov.getTotalRegisteredUsers()).append("\n");
                sb.append("- **Active Users in Period**: ").append(ov.getActiveUsersInPeriod())
                        .append(" (**").append(ov.getEngagementMetrics().getActiveUserRatePercent()).append("%** engagement rate)\n");
                sb.append("- **New Registrations in Period**: ").append(ov.getNewRegistrationsInPeriod())
                        .append(" (").append(ov.getActivityTrends().getUserGrowthPercent() >= 0 ? "+" : "")
                        .append(ov.getActivityTrends().getUserGrowthPercent()).append("% vs previous period)\n");
                sb.append("- **Profiles**: ").append(ov.getTotalCoaches()).append(" Coaches | ")
                        .append(ov.getTotalClients()).append(" Clients | ")
                        .append(ov.getTotalAdmins()).append(" Administrators\n");
                sb.append("- **Coach-Client Relationships**: ").append(ov.getActiveCoachClientRelationships())
                        .append(" Active | ").append(ov.getPendingCoachClientRelationships()).append(" Pending\n\n");

                sb.append("#### 2. Training & PR Operations\n");
                sb.append("- **Total Workout Plans**: ").append(ov.getTotalWorkoutPlans())
                        .append(" (").append(ov.getActiveWorkoutPlans()).append(" active, ")
                        .append(ov.getAiGeneratedWorkoutPlans()).append(" AI-generated)\n");
                sb.append("- **Completed Workouts**: ").append(ov.getCompletedWorkoutsInPeriod())
                        .append(" in period (**").append(ov.getTotalCompletedWorkoutsAllTime()).append("** all-time)\n");
                sb.append("- **Workout Completion Rate**: **")
                        .append(ov.getEngagementMetrics().getWorkoutCompletionRatePercent()).append("%**\n");
                sb.append("- **Recorded Personal Records (PRs)**: ").append(ov.getRecordedPrsInPeriod())
                        .append(" in period (**").append(ov.getTotalRecordedPrsAllTime()).append("** all-time)\n");
                sb.append("- **Exercise Catalog**: ").append(ov.getActiveExercises())
                        .append(" active / ").append(ov.getTotalExercises()).append(" total exercises\n");
            }
            case USER_STATISTICS_AND_SEARCH -> {
                AdminGlobalUserStatisticsResponse s = routedTools.userStatistics();
                sb.append("### 👥 Global User Statistics (").append(rangeMeta.getRange()).append(": ")
                        .append(rangeMeta.getStartDate()).append(" to ").append(rangeMeta.getEndDate()).append(")\n\n");
                if (s != null) {
                    sb.append("- **Total Registered Users**: **").append(s.getTotalRegisteredUsers()).append("** (")
                            .append(s.getTotalClients()).append(" Clients, ")
                            .append(s.getTotalCoaches()).append(" Coaches [").append(s.getVerifiedCoaches()).append(" verified], ")
                            .append(s.getTotalAdmins()).append(" Admins)\n");
                    sb.append("- **Active Users**: ").append(s.getActiveUsersToday()).append(" Today | ")
                            .append(s.getActiveUsers7Days()).append(" (7d) | ")
                            .append(s.getActiveUsers30Days()).append(" (30d)\n");
                    sb.append("- **Inactive (14+ days without completed workout)**: **").append(s.getInactiveUsers14PlusDays())
                            .append("** (At-risk 7-14d: ").append(s.getAtRiskUsers7To14Days()).append(")\n");
                    sb.append("- **Workout Completion Rate (in range)**: **").append(s.getOverallWorkoutCompletionRate())
                            .append("%** (").append(s.getCompletedWorkoutsInRange()).append(" / ").append(s.getTotalWorkoutsInRange()).append(")\n");
                    sb.append("- **Personal Records**: ").append(s.getTotalPersonalRecordsAllTime()).append(" all-time (")
                            .append(s.getNewPersonalRecordsInRange()).append(" in range, ").append(s.getUsersWithStalePrs30PlusDays()).append(" users with stale PRs 30d+)\n");
                }
            }
            case USER_SEGMENTATION_ANALYSIS -> {
                AdminUserSegmentsResponse seg = routedTools.segmentAnalysis();
                sb.append("### 🎯 User Segmentation Analysis (").append(rangeMeta.getRange()).append(": ")
                        .append(rangeMeta.getStartDate()).append(" to ").append(rangeMeta.getEndDate()).append(")\n\n");
                if (seg != null) {
                    sb.append("- **Measured Segment Summary**: ").append(seg.getSegmentSummary()).append("\n");
                    sb.append("- **Matched Users**: **").append(seg.getMatchedCount()).append("** (showing ").append(seg.getReturnedCount()).append(" on page ").append(seg.getPage() + 1).append(")\n\n");
                    if (seg.getUsers().isEmpty()) {
                        sb.append("- No users matched all specified segment criteria.\n");
                    } else {
                        for (AdminGlobalUserSummaryDto u : seg.getUsers().stream().limit(15).toList()) {
                            sb.append("- **#").append(u.getUserId()).append(" ").append(u.getFullName()).append("** (`")
                                    .append(u.getExperienceLevel()).append("` · `").append(u.getActivityStatus()).append("`) — ")
                                    .append("Workouts: ").append(u.getCompletedWorkouts()).append("/").append(u.getTotalWorkouts())
                                    .append(" (**").append(u.getWorkoutCompletionRate()).append("%**), ")
                                    .append("PRs: ").append(u.getTotalPersonalRecords())
                                    .append(", Coach: `").append(u.getCoachRelationshipStatus()).append("`\n");
                        }
                    }
                }
            }
            case USER_DRILL_DOWN -> {
                AdminUserDetailDossierResponse d = routedTools.inspectedUserDetail();
                sb.append("### 🔎 User Drill-Down Inspection (").append(rangeMeta.getRange()).append(": ")
                        .append(rangeMeta.getStartDate()).append(" to ").append(rangeMeta.getEndDate()).append(")\n\n");
                if (d == null) {
                    sb.append("- Could not identify a unique target user from the request. Specify a user ID or select a user from the Users & Segments directory.\n");
                } else {
                    sb.append("- **User**: **#").append(d.getUserId()).append(" ").append(d.getFullName()).append("** (`")
                            .append(d.getAccountCategory()).append("` · `").append(d.getExperienceLevel()).append("`) — Email: `")
                            .append(d.getMaskedEmail()).append("`\n");
                    sb.append("- **Profile**: Goals=`").append(d.getPrimaryGoals()).append("`, Preferred Frequency=`")
                            .append(d.getPreferredFrequency()).append("`, Height=").append(d.getHeightCm()).append("cm, Latest Weight=")
                            .append(d.getLatestWeightKg()).append("kg\n");
                    sb.append("- **Workouts**: ").append(d.getWorkoutSummary().getCompletedSessions()).append(" / ")
                            .append(d.getWorkoutSummary().getTotalSessions()).append(" completed (**")
                            .append(d.getWorkoutSummary().getCompletionRate()).append("%**), Consistency=`")
                            .append(d.getWorkoutSummary().getConsistencyTrend()).append("`\n");
                    sb.append("- **Personal Records**: ").append(d.getPersonalRecordsSummary().getTotalRecords())
                            .append(" total (Max improvement: ").append(d.getPersonalRecordsSummary().getMaxImprovementPercent())
                            .append("%, Coach feedback received: ").append(d.getPersonalRecordsSummary().getCoachFeedbackCount()).append(")\n");
                    sb.append("- **Platform & AI Activity**: ").append(d.getActivitySummary().getAiMessagesInRange())
                            .append(" AI assistant messages, ").append(d.getActivitySummary().getChatMessagesSentInRange())
                            .append(" chat messages, ").append(d.getActivitySummary().getWorkoutPlannerRunsInRange())
                            .append(" planner runs (private message text redacted)\n");
                    sb.append("- **Coach Relationships**: ").append(d.getCoachRelationships().size()).append(" record(s)\n");
                }
            }
            case COACH_CLIENT_ANALYTICS -> {
                AdminCoachClientStatisticsResponse c = routedTools.coachClientStatistics();
                sb.append("### 🤝 Coach-Client Ecosystem Analytics (").append(rangeMeta.getRange()).append(": ")
                        .append(rangeMeta.getStartDate()).append(" to ").append(rangeMeta.getEndDate()).append(")\n\n");
                if (c != null) {
                    sb.append("- **Coaches**: **").append(c.getTotalCoaches()).append("** total (")
                            .append(c.getVerifiedCoaches()).append(" verified, ").append(c.getAcceptingClientsCoaches()).append(" accepting clients)\n");
                    sb.append("- **Active Coach-Client Relationships**: **").append(c.getActiveRelationships())
                            .append("** across **").append(c.getClientsWithActiveCoach()).append("** clients (")
                            .append(c.getPendingInvitations()).append(" pending invitations, ").append(c.getTerminatedRelationships()).append(" terminated)\n");
                    sb.append("- **Assigned Workout Plans**: ").append(c.getActiveAssignedPlans()).append(" active / ")
                            .append(c.getTotalAssignedPlans()).append(" total (").append(c.getCompletedAssignedPlans()).append(" completed)\n");
                    sb.append("- **Coach PR Feedback Rate**: **").append(c.getFeedbackResponseRate()).append("%** (")
                            .append(c.getSharedPrsWithFeedback()).append(" / ").append(c.getTotalSharedPrs()).append(" shared PRs)\n");
                }
            }
            case PLATFORM_ENGAGEMENT_COMPARISON -> {
                AdminPlatformEngagementResponse e = routedTools.platformEngagement();
                sb.append("### 📈 Platform Engagement & Progression Comparison (").append(rangeMeta.getRange()).append(": ")
                        .append(rangeMeta.getStartDate()).append(" to ").append(rangeMeta.getEndDate()).append(")\n\n");
                if (e != null) {
                    sb.append("- **Engagement**: DAU=").append(e.getDauCount()).append(", WAU=").append(e.getWauCount())
                            .append(", MAU=").append(e.getMauCount()).append(" (Stickiness: **")
                            .append(e.getStickinessDauToMauPercent()).append("%**)\n\n");
                    sb.append("#### Experience-Level Comparison\n");
                    for (AdminPlatformEngagementResponse.ExperienceComparisonItem item : e.getExperienceComparisons()) {
                        sb.append("- **").append(item.getExperienceLevel()).append("**: ")
                                .append(item.getUserCount()).append(" users, ").append(item.getActiveUserCount())
                                .append(" active (**").append(item.getActiveRatePercent()).append("%**), completion **")
                                .append(item.getAvgCompletionRate()).append("%**, PRs=").append(item.getPrCount())
                                .append(", avg AI msgs/user=").append(item.getAvgAiInteractionsPerUser()).append("\n");
                    }
                    if (!e.getTopSkillImprovers().isEmpty()) {
                        sb.append("\n#### Top Skill Improvers\n");
                        for (AdminPlatformEngagementResponse.SkillImproverItem imp : e.getTopSkillImprovers()) {
                            sb.append("- **#").append(imp.getUserId()).append(" ").append(imp.getFullName()).append("** — ")
                                    .append(imp.getExerciseName()).append(": **+").append(imp.getImprovementPercent())
                                    .append("%** (latest ").append(imp.getLatestRecordValue()).append(" ").append(imp.getUnit()).append(")\n");
                        }
                    }
                    if (!e.getDecliningConsistencyUsers().isEmpty()) {
                        sb.append("\n#### Users with Declining Training Consistency\n");
                        for (AdminGlobalUserSummaryDto u : e.getDecliningConsistencyUsers()) {
                            sb.append("- **#").append(u.getUserId()).append(" ").append(u.getFullName())
                                    .append("** (").append(u.getWorkoutCompletionRate()).append("% completion)\n");
                        }
                    }
                    if (!e.getStalePrUsers().isEmpty()) {
                        sb.append("\n#### Users Without Recent PR Updates (30+ days)\n");
                        for (AdminGlobalUserSummaryDto u : e.getStalePrUsers()) {
                            sb.append("- **#").append(u.getUserId()).append(" ").append(u.getFullName())
                                    .append("** (Total PRs: ").append(u.getTotalPersonalRecords()).append(")\n");
                        }
                    }
                }
            }
            case USER_TRAINING_ANALYTICS -> {
                sb.append("### 🏋️ User & Training Analytics Report\n\n");
                sb.append("#### 1. Most Frequently Performed Exercises\n");
                if (tr.getMostPerformedExercises() == null || tr.getMostPerformedExercises().isEmpty()) {
                    sb.append("- No exercise performance records logged yet.\n");
                } else {
                    for (AdminTrainingAnalyticsResponse.ExerciseUsageStatDto ex : tr.getMostPerformedExercises().stream().limit(5).toList()) {
                        sb.append("- **").append(ex.getExerciseName()).append("** (").append(ex.getCategory())
                                .append(" / ").append(ex.getDifficulty()).append("): **")
                                .append(ex.getSessionPerformances()).append("** sessions, **")
                                .append(ex.getPlanAssignmentsCount()).append("** plan assignments, **")
                                .append(ex.getPrCount()).append("** PRs (Completion rate: **")
                                .append(ex.getCompletionRatePercent()).append("%**)\n");
                    }
                }
                sb.append("\n#### 2. Skill Focus & Progression Coverage\n");
                for (AdminTrainingAnalyticsResponse.SkillFocusStatDto s : tr.getSkillFocusBreakdown()) {
                    sb.append("- **").append(s.getSkillName()).append("**: ")
                            .append(s.getExistingExerciseCount()).append(" catalog exercises | ")
                            .append(s.getPlanReferencesCount()).append(" plan refs | ")
                            .append(s.getSessionPerformancesCount()).append(" session performances");
                    if (!s.getMissingProgressions().isEmpty()) {
                        sb.append(" | Missing progressions: *").append(String.join(", ", s.getMissingProgressions())).append("*");
                    }
                    sb.append("\n");
                }

                AdminTrainingAnalyticsResponse.InactiveUsersSummaryDto inact = tr.getInactiveUsersSummary();
                if (inact != null) {
                    sb.append("\n#### 3. User Activity & Plan Adherence\n");
                    sb.append("- **Inactive Users (14+ days)**: ").append(inact.getInactiveOver14DaysCount())
                            .append(" of ").append(inact.getTotalUsersEvaluated())
                            .append(" non-admin users (").append(inact.getInactiveRatePercent()).append("%)\n");
                }
                AdminTrainingAnalyticsResponse.PlanAdherenceSummaryDto adh = tr.getPlanAdherenceSummary();
                if (adh != null) {
                    sb.append("- **Workout Plan Adherence**: ").append(adh.getCompletedAssignedSessions())
                            .append(" / ").append(adh.getTotalAssignedSessions()).append(" plan sessions completed (**")
                            .append(adh.getAdherenceRatePercent()).append("%**) across ")
                            .append(adh.getUsersWithActivePersonalPlan()).append(" active personal plans and ")
                            .append(adh.getActiveCoachAssignmentsCount()).append(" active coach assignments.\n");
                }
            }
            case EXERCISE_DRAFT_CREATION -> appendExerciseDraftCreationSummary(sb, routedTools);
            case EXERCISE_RECOMMENDATION -> {
                String skillLabel = detectedSkill != null ? detectedSkill : "Calisthenics Skill & Strength";
                sb.append("### 💡 Exercise Recommendations for ").append(skillLabel).append("\n\n");
                sb.append("Based on an audit of our current **").append(ov.getTotalExercises())
                        .append("-exercise** catalog and athlete training patterns, the following non-duplicate progressions are suggested for administrator review (conversational recommendations — not yet saved in the Exercise Catalog until approved):\n\n");
                if (recommendations.isEmpty()) {
                    sb.append("- All canonical progressions for **").append(skillLabel)
                            .append("** are already present in the exercise library!\n");
                } else {
                    for (AdminRecommendationResponse rec : recommendations) {
                        ExerciseRecommendationPayload p = rec.getExerciseDetails();
                        if (p != null) {
                            sb.append("1. **").append(p.getName()).append("** (`").append(p.getCode()).append("`)\n");
                            sb.append("   - **Category / Difficulty**: ").append(p.getCategory()).append(" · ").append(p.getDifficulty()).append("\n");
                            sb.append("   - **Progression Level**: ").append(p.getProgressionLevel()).append("\n");
                            sb.append("   - **Target Muscles**: ").append(p.getTargetMuscles()).append("\n");
                            sb.append("   - **Equipment**: ").append(p.getEquipment()).append("\n");
                            sb.append("   - **Prerequisites**: ").append(p.getPrerequisites()).append("\n");
                            sb.append("   - **Why Add**: ").append(p.getWhyAdd()).append("\n\n");
                        }
                    }
                    sb.append("> **Action Required**: These suggestions are queued with status `PENDING_APPROVAL` and are **not published or saved as active catalog exercises** until approved by an Admin.\n");
                }
            }
            case WORKOUT_PROGRAM_RECOMMENDATION -> {
                sb.append("### 📋 Draft Workout Program Recommendations\n\n");
                sb.append("I have generated the following structured draft workout program(s) grounded in our platform's exercise library:\n\n");
                for (AdminRecommendationResponse rec : recommendations) {
                    WorkoutProgramRecommendationPayload p = rec.getProgramDetails();
                    if (p != null) {
                        sb.append("#### ").append(p.getProgramName()).append(" (`").append(p.getTrainingLevel()).append("`)\n");
                        sb.append("- **Target Audience**: ").append(p.getTargetAudience()).append("\n");
                        sb.append("- **Schedule**: ").append(p.getWeeklyFrequency()).append(" days/week · ")
                                .append(p.getDurationWeeks()).append(" weeks · Split: `").append(p.getSplitType()).append("`\n");
                        sb.append("- **Required Equipment**: ").append(p.getRequiredEquipment()).append("\n");
                        sb.append("- **Progression Criteria**: ").append(p.getProgressionCriteria()).append("\n");
                        sb.append("- **Safety Considerations**: ").append(p.getSafetyConsiderations()).append("\n");
                        sb.append("- **Reasoning**: ").append(p.getRationale()).append("\n\n");
                    }
                }
                sb.append("> **Governance Notice**: Draft programs are saved with status `DRAFT` and are **never** automatically published or assigned to users. Approve a program to create a draft `WorkoutPlan` for manual publication or assignment.\n");
            }
            case OPERATIONAL_INSIGHTS -> {
                sb.append("### 🎯 Operational Insights & Platform Recommendations\n\n");
                sb.append("Based on live database metrics across **").append(ov.getTotalRegisteredUsers())
                        .append(" users**, **").append(ov.getTotalWorkoutPlans()).append(" workout plans**, and **")
                        .append(ov.getTotalExercises()).append(" exercises**, here are the prioritized operational findings:\n\n");
                for (AdminRecommendationResponse rec : recommendations) {
                    OperationalRecommendationPayload op = rec.getOperationalDetails();
                    sb.append("- **[").append(rec.getPriority()).append("] ").append(rec.getTitle()).append("**\n");
                    if (op != null) {
                        sb.append("  - **Identified Issue**: ").append(op.getIdentifiedIssue()).append("\n");
                        sb.append("  - **Supporting Data**: ").append(op.getSupportingData()).append("\n");
                        sb.append("  - **Suggested Action**: ").append(op.getSuggestedAction()).append("\n");
                        sb.append("  - **Expected Benefit**: ").append(op.getExpectedBenefit()).append("\n\n");
                    }
                }
            }
            case CONFIRMATION_REQUIRED -> {
                AdminPendingConfirmationResponse pending = routedTools.pendingConfirmation();
                if (pending != null && (pending.getActionType() == AdminPendingActionType.GRANT_PRO
                        || pending.getActionType() == AdminPendingActionType.REVOKE_PRO)) {
                    if (routedTools.proToolResult() != null && routedTools.proToolResult().getMessage() != null) {
                        sb.append(routedTools.proToolResult().getMessage()).append("\n");
                    } else {
                        sb.append("### Confirmation Required Before PRO Change\n\n")
                                .append(pending.getSummary()).append("\n");
                    }
                } else {
                    sb.append("### Confirmation Required Before Campaign Change\n\n");
                    if (pending != null) {
                        sb.append(pending.getSummary()).append("\n\n");
                        sb.append("- **Action**: ").append(pending.getActionType()).append("\n");
                        sb.append("- **Program**: ").append(pending.getProgramTitle())
                                .append(" (ID ").append(pending.getProgramId()).append(")\n");
                        sb.append("- **Target Audience**: ").append(pending.getAudienceLabel())
                                .append(" (`").append(pending.getTargetAudience()).append("`)\n");
                        sb.append("- **Estimated Eligible Athletes**: **").append(pending.getEligibleAthleteCount()).append("**\n");
                        sb.append("- **Confirmation Token**: `").append(pending.getConfirmationToken()).append("`\n");
                        sb.append("- **Status**: Pending Admin confirmation — campaign has NOT been changed yet.\n");
                    }
                }
            }
            case PRO_MANAGEMENT -> appendProManagementSummary(sb, routedTools);
            case GETTING_STARTED_PROGRAM_MANAGEMENT -> appendGettingStartedProgramSummary(sb, routedTools);
            case CAMPAIGN_MANAGEMENT -> appendCampaignManagementSummary(sb, routedTools);
            case CAMPAIGN_ANALYTICS -> appendCampaignAnalyticsSummary(sb, routedTools);
            default -> {
                sb.append("### 🛡️ Admin AI Operations Advisor\n\n");
                sb.append("Current platform snapshot (**").append(ov.getStartDate()).append(" to ").append(ov.getEndDate()).append("**):\n");
                sb.append("- **Users**: ").append(ov.getTotalRegisteredUsers()).append(" total (")
                        .append(ov.getActiveUsersInPeriod()).append(" active in period)\n");
                sb.append("- **Completed Workouts**: ").append(ov.getCompletedWorkoutsInPeriod())
                        .append(" in period (").append(ov.getTotalCompletedWorkoutsAllTime()).append(" all-time)\n");
                sb.append("- **Exercise Catalog**: ").append(ov.getTotalExercises()).append(" exercises\n\n");
                sb.append("Ask me to analyze skill progression gaps, propose new exercises, generate draft workout programs, or audit user retention.");
            }
        }

        if (routedTools.missingDataNotes() != null && !routedTools.missingDataNotes().isEmpty()) {
            sb.append("\n#### Data Completeness Notes\n");
            for (String note : routedTools.missingDataNotes()) {
                sb.append("- ").append(note).append("\n");
            }
        }
        return sb.toString();
    }

    private List<String> buildSuggestedFollowUps(AdminAiIntent intent, String detectedSkill) {
        return switch (intent) {
            case PLATFORM_OVERVIEW, USER_STATISTICS_AND_SEARCH -> List.of(
                    "Show users who have not completed any workouts in the last 14 days.",
                    "Compare user engagement across different experience levels.",
                    "What exercises should we add to improve our Planche training library?"
            );
            case USER_SEGMENTATION_ANALYSIS -> List.of(
                    "Identify users whose training consistency has declined.",
                    "How many users have active coaches?",
                    "Which areas of the platform need improvement?"
            );
            case USER_DRILL_DOWN -> List.of(
                    "Show users who have not updated their Personal Records recently.",
                    "How many users have active coaches?",
                    "Suggest new workout programs"
            );
            case COACH_CLIENT_ANALYTICS, PLATFORM_ENGAGEMENT_COMPARISON -> List.of(
                    "Which exercises are most frequently performed?",
                    "Which areas of the platform need improvement?",
                    "Suggest new workout programs"
            );
            case USER_TRAINING_ANALYTICS -> List.of(
                    "Identify missing exercise progressions",
                    "Suggest new workout programs",
                    "Which areas of the platform need improvement?"
            );
            case EXERCISE_DRAFT_CREATION -> List.of(
                    "Show all draft exercises in the catalog",
                    "Add 2 more core exercises to draft",
                    "What exercises should we add to improve our Planche training library?"
            );
            case EXERCISE_RECOMMENDATION -> List.of(
                    "What exercises should we add for Front Lever and Back Lever?",
                    "Add 3 leg exercises to draft",
                    "Summarize platform activity this month"
            );
            case WORKOUT_PROGRAM_RECOMMENDATION -> List.of(
                    "Identify missing exercise progressions",
                    "Analyze user training trends",
                    "Which areas of the platform need improvement?"
            );
            case PRO_MANAGEMENT -> List.of(
                    "Grant PRO to user@example.com",
                    "Revoke PRO from user@example.com",
                    "Check PRO status for user@example.com"
            );
            case GETTING_STARTED_PROGRAM_MANAGEMENT -> List.of(
                    "Make Day 1 focused on pushing movements.",
                    "Preview the Getting Started program and check eligible audience size.",
                    "Publish this program for users who joined within the last 14 days."
            );
            case CAMPAIGN_MANAGEMENT, CONFIRMATION_REQUIRED -> List.of(
                    "Show me the currently active Getting Started campaign.",
                    "Show Getting Started campaign analytics.",
                    "Change the target audience to all athletes."
            );
            case CAMPAIGN_ANALYTICS -> List.of(
                    "Show me the currently active Getting Started campaign.",
                    "Unpublish the current campaign.",
                    "Change the target audience to all athletes."
            );
            default -> List.of(
                    "Summarize platform activity this month",
                    "What exercises should we add?",
                    "Suggest new workout programs"
            );
        };
    }

    private String buildMessageMetadata(
            AdminAiIntent intent,
            List<AdminRecommendationResponse> recommendations,
            AdminPlatformOverviewResponse overview,
            List<AdminToolExecutionRecord> executedTools
    ) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("intent", intent.name());
            meta.put("range", overview.getRange().name());
            meta.put("recommendationIds", recommendations.stream().map(AdminRecommendationResponse::getId).toList());
            meta.put("recommendationCount", recommendations.size());
            meta.put("executedTools", executedTools.stream().map(AdminToolExecutionRecord::getToolName).toList());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception ex) {
            return null;
        }
    }

    private AdminAiIntent resolveIntentAfterTools(
            String userPrompt,
            GeminiAdminAgentOutput geminiOutput,
            GeminiAdminToolPlan toolPlan,
            AdminChatRequest request,
            AdminToolRouter.RoutedChatToolsResult routedTools
    ) {
        if (routedTools != null) {
            if (routedTools.exerciseDraftResult() != null) {
                return AdminAiIntent.EXERCISE_DRAFT_CREATION;
            }
            if (routedTools.pendingConfirmation() != null) {
                return AdminAiIntent.CONFIRMATION_REQUIRED;
            }
            if (routedTools.proToolResult() != null) {
                return AdminAiIntent.PRO_MANAGEMENT;
            }
            if (routedTools.campaignAnalytics() != null) {
                return AdminAiIntent.CAMPAIGN_ANALYTICS;
            }
            if (routedTools.activeCampaigns() != null) {
                return AdminAiIntent.CAMPAIGN_MANAGEMENT;
            }
            if (routedTools.executedTools() != null) {
                for (AdminToolExecutionRecord rec : routedTools.executedTools()) {
                    if (AdminToolName.CONFIGURE_AUDIENCE.getToolName().equalsIgnoreCase(rec.getToolName())
                            || AdminToolName.PUBLISH_PROGRAM.getToolName().equalsIgnoreCase(rec.getToolName())
                            || AdminToolName.UNPUBLISH_PROGRAM.getToolName().equalsIgnoreCase(rec.getToolName())) {
                        return AdminAiIntent.CAMPAIGN_MANAGEMENT;
                    }
                    if (AdminToolName.CREATE_PROGRAM.getToolName().equalsIgnoreCase(rec.getToolName())
                            || AdminToolName.UPDATE_PROGRAM.getToolName().equalsIgnoreCase(rec.getToolName())
                            || AdminToolName.PREVIEW_PROGRAM.getToolName().equalsIgnoreCase(rec.getToolName())) {
                        return AdminAiIntent.GETTING_STARTED_PROGRAM_MANAGEMENT;
                    }
                }
            }
        }
        return resolveIntent(userPrompt, geminiOutput, toolPlan, request);
    }

    private boolean isGettingStartedOrCampaignIntent(AdminAiIntent intent) {
        return intent == AdminAiIntent.GETTING_STARTED_PROGRAM_MANAGEMENT
                || intent == AdminAiIntent.CAMPAIGN_MANAGEMENT
                || intent == AdminAiIntent.CAMPAIGN_ANALYTICS
                || intent == AdminAiIntent.CONFIRMATION_REQUIRED;
    }

    private void appendGettingStartedProgramSummary(StringBuilder sb, AdminToolRouter.RoutedChatToolsResult routedTools) {
        GettingStartedProgramPreviewResponse preview = routedTools.gettingStartedPreview();
        GettingStartedProgramResponse program = preview != null ? preview.getProgram() : routedTools.gettingStartedProgram();
        if (program == null) {
            sb.append("No Getting Started program data was returned.\n");
            return;
        }
        sb.append("### Getting Started – 3 Days Program: ").append(program.getTitle()).append("\n\n");
        sb.append("- **Program ID**: ").append(program.getId())
                .append(" | **Version**: v").append(program.getVersion())
                .append(" | **Status**: ").append(program.isPublished() ? "PUBLISHED" : "UNPUBLISHED DRAFT").append("\n");
        sb.append("- **Target Audience**: ").append(program.getTargetAudience()).append("\n");
        if (preview != null) {
            sb.append("- **Audience Label**: ").append(preview.getAudienceLabel())
                    .append(" | **Estimated Eligible Athletes**: **").append(preview.getEligibleAthleteCount())
                    .append("** (of ").append(preview.getTotalAthleteCount()).append(" total athletes)\n");
            sb.append("- **Validation**: ").append(preview.isValid() ? "PASSED" : "FAILED (" + preview.getValidationErrors() + ")").append("\n");
        }
        if (program.getDays() != null) {
            sb.append("\n");
            for (GettingStartedDayResponse day : program.getDays()) {
                sb.append("#### Day ").append(day.getDayNumber()).append(" — ").append(day.getTitle()).append("\n");
                if (day.getExercises() != null) {
                    for (GettingStartedExerciseResponse ex : day.getExercises()) {
                        String prescription = ex.getReps() != null
                                ? ex.getReps() + " reps"
                                : (ex.getDurationSeconds() != null ? ex.getDurationSeconds() + "s hold" : "");
                        sb.append("- **").append(ex.getExerciseName()).append("**: ")
                                .append(ex.getSets()).append(" sets x ").append(prescription)
                                .append(", ").append(ex.getRestSeconds()).append("s rest");
                        if (ex.getInstructions() != null && !ex.getInstructions().isBlank()) {
                            sb.append(" — _").append(ex.getInstructions()).append("_");
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }
    }

    private void appendCampaignManagementSummary(StringBuilder sb, AdminToolRouter.RoutedChatToolsResult routedTools) {
        if (routedTools.activeCampaigns() != null) {
            List<GettingStartedCampaignSummaryResponse> campaigns = routedTools.activeCampaigns();
            sb.append("### Active Getting Started Campaigns (").append(campaigns.size()).append(")\n\n");
            if (campaigns.isEmpty()) {
                sb.append("There are currently no published Getting Started campaigns.\n");
            } else {
                for (GettingStartedCampaignSummaryResponse c : campaigns) {
                    sb.append("- **").append(c.getTitle()).append("** (ID ").append(c.getProgramId())
                            .append(", v").append(c.getVersion()).append(") — Target: **").append(c.getAudienceLabel())
                            .append("** (`").append(c.getTargetAudience()).append("`), Eligible Athletes: **")
                            .append(c.getEligibleAthleteCount()).append("**, Enrollments: **")
                            .append(c.getEnrollments()).append("**\n");
                }
            }
            return;
        }
        appendGettingStartedProgramSummary(sb, routedTools);
    }

    private void appendCampaignAnalyticsSummary(StringBuilder sb, AdminToolRouter.RoutedChatToolsResult routedTools) {
        GettingStartedCampaignAnalyticsResponse a = routedTools.campaignAnalytics();
        if (a == null) {
            sb.append("No Getting Started campaign analytics are available yet.\n");
            return;
        }
        sb.append("### Getting Started Campaign Analytics: ").append(a.getProgramTitle()).append("\n\n");
        sb.append("- **Program ID**: ").append(a.getProgramId()).append(" (").append(a.isPublished() ? "PUBLISHED" : "DRAFT").append(", v").append(a.getVersion()).append(")\n");
        sb.append("- **Target Audience**: ").append(a.getAudienceLabel()).append(" (`").append(a.getTargetAudience()).append("`)\n");
        sb.append("- **Eligible Athletes**: ").append(a.getEligibleAthleteCount()).append("\n");
        sb.append("- **Modal Impressions**: ").append(a.getModalImpressions()).append("\n");
        sb.append("- **Modal Dismissals**: ").append(a.getModalDismissals()).append("\n");
        sb.append("- **Program Detail Views**: ").append(a.getProgramDetailViews()).append("\n");
        sb.append("- **Enrollments**: ").append(a.getEnrollments()).append("\n");
        sb.append("- **Day 1 Completion Rate**: ").append(a.getDay1CompletionRate()).append("% (").append(a.getDay1Completions()).append(")\n");
        sb.append("- **Day 2 Completion Rate**: ").append(a.getDay2CompletionRate()).append("% (").append(a.getDay2Completions()).append(")\n");
        sb.append("- **Day 3 Completion Rate**: ").append(a.getDay3CompletionRate()).append("% (").append(a.getDay3Completions()).append(")\n");
        sb.append("- **Overall Completion Rate**: ").append(a.getOverallCompletionRate()).append("% (").append(a.getCompletedEnrollments()).append(" completed)\n");
    }

    private void appendProManagementSummary(StringBuilder sb, AdminToolRouter.RoutedChatToolsResult routedTools) {
        AdminProToolResultResponse pro = routedTools.proToolResult();
        if (pro != null && pro.getMessage() != null && !pro.getMessage().isBlank()) {
            sb.append(pro.getMessage()).append("\n");
            return;
        }
        sb.append("### Admin PRO Access Management\n\n")
                .append("Specify an action and target user by **email**, **username**, or **user ID** ")
                .append("(e.g. `Grant PRO to user@example.com` or `Revoke PRO from user123`).\n");
    }

    private void appendExerciseDraftCreationSummary(StringBuilder sb, AdminToolRouter.RoutedChatToolsResult routedTools) {
        ExerciseDraftBatchResultResponse res = routedTools.exerciseDraftResult();
        if (res == null) {
            sb.append("Error: Exercise creation tool did not execute. No exercises were added to the database.\n");
            return;
        }
        if (!res.isSuccess()) {
            sb.append("### ❌ Failed to Save Exercise Drafts\n\n")
                    .append(res.getErrorMessage() != null
                            ? res.getErrorMessage()
                            : "Validation or persistence failed before exercises could be saved.")
                    .append("\n\nNo exercises were added to the database.\n");
            return;
        }
        if (res.getCreatedExercises() != null && !res.getCreatedExercises().isEmpty()) {
            sb.append("### Added ").append(res.getCreatedExercises().size())
                    .append(" Exercise(s) to Draft (`AI Recommended`)\n\n");
            int idx = 1;
            for (AdminExerciseResponse ex : res.getCreatedExercises()) {
                sb.append(idx++).append(". **").append(ex.name()).append("** (`").append(ex.code())
                        .append("`, ID #").append(ex.id()).append(") — ")
                        .append(ex.category()).append(" · ").append(ex.difficulty())
                        .append(" · Status: `").append(ex.status()).append("` · Origin: `")
                        .append(ex.origin()).append("`\n");
            }
            if (res.getExistingDrafts() != null && !res.getExistingDrafts().isEmpty()) {
                sb.append("\nAlready existing catalog entry/entries preserved without duplicates:\n");
                for (AdminExerciseResponse existing : res.getExistingDrafts()) {
                    sb.append("- **").append(existing.name()).append("** (`").append(existing.code())
                            .append("`, ID #").append(existing.id()).append(") — ")
                            .append(existing.category()).append(" · ").append(existing.difficulty())
                            .append(" · Status: `").append(existing.status()).append("`\n");
                }
            }
            sb.append("\nAll newly created exercises are saved as **Draft** in **Admin > Exercises > Draft** ")
                    .append("and remain hidden from regular users until published by an Admin.\n");
        } else if (res.getExistingDrafts() != null && !res.getExistingDrafts().isEmpty()) {
            sb.append("### Existing Exercise Drafts Preserved (No Duplicates Created)\n\n")
                    .append(res.getMessage()).append("\n\n");
            int idx = 1;
            for (AdminExerciseResponse ex : res.getExistingDrafts()) {
                sb.append(idx++).append(". **").append(ex.name()).append("** (`").append(ex.code())
                        .append("`, ID #").append(ex.id()).append(") — ")
                        .append(ex.category()).append(" · ").append(ex.difficulty())
                        .append(" · Status: `").append(ex.status()).append("` · Origin: `")
                        .append(ex.origin()).append("`\n");
            }
        } else {
            sb.append("No new exercises were created in the database.\n");
        }
    }

    private boolean claimsUnsubstantiatedExerciseCreation(
            String reply,
            AdminToolRouter.RoutedChatToolsResult routedTools
    ) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        boolean hasPersistedDrafts = routedTools != null
                && routedTools.exerciseDraftResult() != null
                && routedTools.exerciseDraftResult().isSuccess()
                && routedTools.exerciseDraftResult().getCreatedCount() > 0;
        if (hasPersistedDrafts) {
            return false;
        }
        String lower = reply.toLowerCase(Locale.US);
        return lower.matches("(?s).*\\b(i've added|i have added|added \\d+ .*exercise|added the .*exercise|added these .*exercise|saved .* to (?:the )?draft|created \\d+ .*exercise).*");
    }

    private String generateConversationTitle(String userPrompt, AdminAiIntent intent) {
        String clean = userPrompt.replaceAll("[^a-zA-Z0-9\\s-]", "").trim();
        if (clean.isBlank()) {
            return switch (intent) {
                case PLATFORM_OVERVIEW -> "Platform Activity Overview";
                case USER_TRAINING_ANALYTICS -> "Training & User Analytics";
                case EXERCISE_RECOMMENDATION -> "Exercise Library Recommendations";
                case EXERCISE_DRAFT_CREATION -> "Exercise Drafts Added";
                case WORKOUT_PROGRAM_RECOMMENDATION -> "Draft Workout Programs";
                case OPERATIONAL_INSIGHTS -> "Operational Improvements";
                case USER_STATISTICS_AND_SEARCH -> "Global User Statistics";
                case USER_DRILL_DOWN -> "User Inspection Dossier";
                case USER_SEGMENTATION_ANALYSIS -> "User Segment Analysis";
                case COACH_CLIENT_ANALYTICS -> "Coach-Client Ecosystem";
                case PLATFORM_ENGAGEMENT_COMPARISON -> "Engagement Comparison";
                case GETTING_STARTED_PROGRAM_MANAGEMENT -> "Getting Started – 3 Days";
                case CAMPAIGN_MANAGEMENT, CONFIRMATION_REQUIRED -> "Getting Started Campaign";
                case CAMPAIGN_ANALYTICS -> "Getting Started Analytics";
                case PRO_MANAGEMENT -> "PRO Entitlement Management";
                default -> "Admin AI Advisory";
            };
        }
        String[] words = clean.split("\\s+");
        if (words.length <= 6) {
            return clean;
        }
        return String.join(" ", Arrays.copyOfRange(words, 0, 6)) + "...";
    }
}
