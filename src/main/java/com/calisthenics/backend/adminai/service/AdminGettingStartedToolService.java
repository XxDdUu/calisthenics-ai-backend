package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.data.AdminConfirmationDecisionResponse;
import com.calisthenics.backend.adminai.dto.data.AdminPendingConfirmationResponse;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecuteRequest;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecutionRecord;
import com.calisthenics.backend.adminai.dto.data.GettingStartedToolResultResponse;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiPendingAction;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiPendingActionRepository;
import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminPendingActionType;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedProgramRepository;
import com.calisthenics.backend.gettingstarted.service.GettingStartedAiDraftService;
import com.calisthenics.backend.gettingstarted.service.GettingStartedService;
import com.calisthenics.backend.gettingstarted.service.impl.GettingStartedCampaignHelper;
import com.calisthenics.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Authorized Admin AI backend tool coordinator for Getting Started – 3 Days
 * program generation, editing, preview, audience targeting, campaign analytics,
 * and explicit two-step confirmation for publishing and unpublishing.
 */
@Service
@RequiredArgsConstructor
public class AdminGettingStartedToolService {

    private static final int CONFIRMATION_TTL_MINUTES = AdminPendingConfirmationSupport.CONFIRMATION_TTL_MINUTES;

    private final GettingStartedService gettingStartedService;
    private final GettingStartedAiDraftService aiDraftService;
    private final GettingStartedCampaignHelper campaignHelper;
    private final GettingStartedProgramRepository programRepository;
    private final ExerciseRepository exerciseRepository;
    private final AdminAiPendingActionRepository pendingActionRepository;
    private final AdminAiConversationRepository conversationRepository;
    private final AdminAuthorizationService authorizationService;
    private final AdminPendingConfirmationSupport confirmationSupport;

    public GettingStartedToolResultResponse executeTool(
            User admin,
            AdminToolName toolName,
            AdminToolExecuteRequest request
    ) {
        return switch (toolName) {
            case CREATE_PROGRAM -> handleCreateProgram(admin, request);
            case UPDATE_PROGRAM -> handleUpdateProgram(admin, request);
            case PREVIEW_PROGRAM -> handlePreviewProgram(admin, request);
            case CONFIGURE_AUDIENCE -> handleConfigureAudience(admin, request);
            case PUBLISH_PROGRAM -> handlePublishProgram(admin, request);
            case UNPUBLISH_PROGRAM -> handleUnpublishProgram(admin, request);
            case GET_ACTIVE_CAMPAIGNS -> handleGetActiveCampaigns(admin);
            case GET_CAMPAIGN_ANALYTICS -> handleGetCampaignAnalytics(admin, request);
            default -> throw new IllegalArgumentException("Unsupported Getting Started tool: " + toolName.getToolName());
        };
    }

    public boolean isGettingStartedTool(AdminToolName toolName) {
        return switch (toolName) {
            case CREATE_PROGRAM, UPDATE_PROGRAM, PREVIEW_PROGRAM, PUBLISH_PROGRAM,
                 UNPUBLISH_PROGRAM, CONFIGURE_AUDIENCE, GET_ACTIVE_CAMPAIGNS, GET_CAMPAIGN_ANALYTICS -> true;
            default -> false;
        };
    }

    private GettingStartedToolResultResponse handleCreateProgram(User admin, AdminToolExecuteRequest request) {
        TargetAudience audience = resolveOptionalAudience(
                request.getProgram() != null && request.getProgram().getTargetAudience() != null
                        ? request.getProgram().getTargetAudience().name()
                        : request.getTargetAudience()
        );
        AdminGettingStartedProgramRequest draftReq;
        if (request.getProgram() != null
                && request.getProgram().getDays() != null
                && !request.getProgram().getDays().isEmpty()) {
            draftReq = request.getProgram();
            if (draftReq.getTargetAudience() == null && audience != null) {
                draftReq.setTargetAudience(audience);
            }
        } else {
            String instruction = request.getInstruction() != null ? request.getInstruction() : request.getGoal();
            draftReq = aiDraftService.generateDraftRequest(
                    admin.getId(),
                    instruction,
                    request.getTitle(),
                    request.getDescription(),
                    audience
            );
        }

        GettingStartedProgramResponse created = gettingStartedService.createNewProgram(admin.getId(), draftReq);
        bindProgramToConversation(request.getConversationId(), created.getId());
        long eligible = gettingStartedService.countEligibleAthletes(created.getTargetAudience());

        authorizationService.logToolExecution(
                AdminToolName.CREATE_PROGRAM, admin.getId(), created.getId(),
                "title=" + created.getTitle() + ", audience=" + created.getTargetAudience() + ", published=false"
        );

        return GettingStartedToolResultResponse.builder()
                .status("DRAFT_CREATED")
                .confirmationRequired(false)
                .program(created)
                .eligibleAthleteCount(eligible)
                .message("Created unpublished 3-day Getting Started draft '" + created.getTitle()
                        + "' (ID " + created.getId() + ") targeting "
                        + campaignHelper.audienceLabel(created.getTargetAudience())
                        + " (" + eligible + " eligible athletes). No athletes were enrolled and coach plans were not modified.")
                .build();
    }

    private GettingStartedToolResultResponse handleUpdateProgram(User admin, AdminToolExecuteRequest request) {
        Long targetId = resolveTargetProgramId(request, true);
        if (targetId == null) {
            throw new IllegalStateException("No Getting Started program found to update. Create a draft first.");
        }
        GettingStartedProgram existing = programRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Getting Started program not found: " + targetId));

        TargetAudience parsedAudience = resolveOptionalAudience(request.getTargetAudience());
        AdminGettingStartedProgramRequest updatedReq;

        if (request.getProgram() != null
                && request.getProgram().getDays() != null
                && !request.getProgram().getDays().isEmpty()) {
            updatedReq = request.getProgram();
            if (updatedReq.getTargetAudience() == null && parsedAudience != null) {
                updatedReq.setTargetAudience(parsedAudience);
            }
        } else if (request.getOperations() != null && !request.getOperations().isEmpty()) {
            updatedReq = campaignHelper.applyOperations(
                    existing, request.getOperations(), exerciseRepository,
                    request.getTitle(), request.getDescription(), parsedAudience
            );
        } else if (request.getInstruction() != null && !request.getInstruction().isBlank()) {
            GettingStartedProgramResponse currentResp = gettingStartedService.getProgramById(existing.getId());
            updatedReq = aiDraftService.applyNaturalLanguageEdit(
                    admin.getId(), existing, currentResp, request.getInstruction());
            if (request.getTitle() != null && !request.getTitle().isBlank()) {
                updatedReq.setTitle(request.getTitle().trim());
            }
            if (request.getDescription() != null) {
                updatedReq.setDescription(request.getDescription().trim());
            }
            if (parsedAudience != null) {
                updatedReq.setTargetAudience(parsedAudience);
            }
        } else if (request.getTitle() != null || request.getDescription() != null || parsedAudience != null) {
            updatedReq = campaignHelper.applyOperations(
                    existing, Collections.emptyList(), exerciseRepository,
                    request.getTitle(), request.getDescription(), parsedAudience
            );
        } else {
            throw new IllegalArgumentException("Provide updated days, exercise operations, or an edit instruction.");
        }

        GettingStartedProgramResponse updated = gettingStartedService.updateProgramById(admin.getId(), existing.getId(), updatedReq);
        bindProgramToConversation(request.getConversationId(), updated.getId());
        long eligible = gettingStartedService.countEligibleAthletes(updated.getTargetAudience());

        authorizationService.logToolExecution(
                AdminToolName.UPDATE_PROGRAM, admin.getId(), updated.getId(),
                "version=" + updated.getVersion() + ", published=" + updated.isPublished()
        );

        return GettingStartedToolResultResponse.builder()
                .status("UPDATED")
                .confirmationRequired(false)
                .program(updated)
                .eligibleAthleteCount(eligible)
                .message("Updated Getting Started program '" + updated.getTitle() + "' (ID " + updated.getId()
                        + ", v" + updated.getVersion() + ", status=" + (updated.isPublished() ? "PUBLISHED" : "DRAFT") + ").")
                .build();
    }

    private GettingStartedToolResultResponse handlePreviewProgram(User admin, AdminToolExecuteRequest request) {
        Long targetId = resolveTargetProgramId(request, false);
        GettingStartedProgramPreviewResponse preview = gettingStartedService.previewProgram(targetId);
        if (preview.getProgram() != null) {
            bindProgramToConversation(request.getConversationId(), preview.getProgram().getId());
        }
        authorizationService.logToolExecution(
                AdminToolName.PREVIEW_PROGRAM, admin.getId(),
                preview.getProgram() != null ? preview.getProgram().getId() : null,
                "valid=" + preview.isValid() + ", eligibleAthletes=" + preview.getEligibleAthleteCount()
        );

        return GettingStartedToolResultResponse.builder()
                .status("PREVIEW")
                .confirmationRequired(false)
                .program(preview.getProgram())
                .preview(preview)
                .eligibleAthleteCount(preview.getEligibleAthleteCount())
                .message("Previewed Getting Started program '" + preview.getProgram().getTitle()
                        + "' (" + preview.getAudienceLabel() + ", " + preview.getEligibleAthleteCount() + " eligible athletes).")
                .build();
    }

    private GettingStartedToolResultResponse handleConfigureAudience(User admin, AdminToolExecuteRequest request) {
        String rawAudience = request.getTargetAudience() != null ? request.getTargetAudience() : request.getInstruction();
        TargetAudience audience = campaignHelper.parseTargetAudience(rawAudience);
        Long targetId = resolveTargetProgramId(request, false);

        GettingStartedProgramResponse updated = gettingStartedService.configureAudience(admin.getId(), targetId, audience);
        bindProgramToConversation(request.getConversationId(), updated.getId());
        long eligible = gettingStartedService.countEligibleAthletes(audience);

        authorizationService.logToolExecution(
                AdminToolName.CONFIGURE_AUDIENCE, admin.getId(), updated.getId(),
                "audience=" + audience + ", eligibleAthletes=" + eligible
        );

        return GettingStartedToolResultResponse.builder()
                .status("AUDIENCE_CONFIGURED")
                .confirmationRequired(false)
                .program(updated)
                .eligibleAthleteCount(eligible)
                .message("Configured target audience for '" + updated.getTitle() + "' to "
                        + campaignHelper.audienceLabel(audience) + " (" + eligible + " eligible athletes).")
                .build();
    }

    @Transactional
    protected GettingStartedToolResultResponse handlePublishProgram(User admin, AdminToolExecuteRequest request) {
        Long targetId = resolveTargetProgramId(request, true);
        if (targetId == null) {
            throw new IllegalStateException("No Getting Started program found to publish.");
        }
        TargetAudience requestedAudience = resolveOptionalAudience(request.getTargetAudience());
        GettingStartedProgramPreviewResponse preview = gettingStartedService.previewProgram(targetId);
        if (!preview.isValid()) {
            throw new IllegalArgumentException("Cannot publish invalid program: " + String.join(" ", preview.getValidationErrors()));
        }

        if (!Boolean.TRUE.equals(request.getConfirmed())
                || request.getConfirmationToken() == null
                || request.getConfirmationToken().isBlank()) {
            TargetAudience effectiveAudience = requestedAudience != null ? requestedAudience : preview.getTargetAudience();
            AdminPendingConfirmationResponse pending = createPendingAction(
                    admin,
                    request.getConversationId(),
                    AdminPendingActionType.PUBLISH_PROGRAM,
                    targetId,
                    effectiveAudience,
                    preview.getProgram().getTitle()
            );
            bindProgramToConversation(request.getConversationId(), targetId);
            return GettingStartedToolResultResponse.builder()
                    .status("CONFIRMATION_REQUIRED")
                    .confirmationRequired(true)
                    .program(preview.getProgram())
                    .preview(preview)
                    .pendingConfirmation(pending)
                    .eligibleAthleteCount(pending.getEligibleAthleteCount())
                    .message(pending.getSummary())
                    .build();
        }

        AdminAiPendingAction action = validateAndConsumePendingAction(
                admin, request.getConfirmationToken(), AdminPendingActionType.PUBLISH_PROGRAM, targetId);
        TargetAudience finalAudience = requestedAudience != null ? requestedAudience : action.getTargetAudience();
        if (finalAudience != null) {
            gettingStartedService.configureAudience(admin.getId(), targetId, finalAudience);
        }
        GettingStartedProgramResponse published = gettingStartedService.publishProgramById(admin.getId(), targetId);
        long eligible = gettingStartedService.countEligibleAthletes(published.getTargetAudience());
        bindProgramToConversation(request.getConversationId(), published.getId());

        authorizationService.logToolExecution(
                AdminToolName.PUBLISH_PROGRAM, admin.getId(), published.getId(),
                "confirmed=true, audience=" + published.getTargetAudience() + ", eligibleAthletes=" + eligible
        );

        return GettingStartedToolResultResponse.builder()
                .status("PUBLISHED")
                .confirmationRequired(false)
                .program(published)
                .activeCampaigns(gettingStartedService.getActiveCampaigns())
                .eligibleAthleteCount(eligible)
                .message("Published Getting Started campaign '" + published.getTitle() + "' (ID " + published.getId()
                        + ") for " + campaignHelper.audienceLabel(published.getTargetAudience())
                        + " (" + eligible + " eligible athletes). No athletes were auto-enrolled and existing coach plans are untouched.")
                .build();
    }

    @Transactional
    protected GettingStartedToolResultResponse handleUnpublishProgram(User admin, AdminToolExecuteRequest request) {
        Long targetId = request.getProgramId();
        if (targetId == null) {
            targetId = programRepository.findFirstPublished()
                    .map(GettingStartedProgram::getId)
                    .orElseThrow(() -> new IllegalStateException("No active published Getting Started campaign found to unpublish."));
        }
        GettingStartedProgramResponse targetProgram = gettingStartedService.getProgramById(targetId);
        if (!targetProgram.isPublished()) {
            throw new IllegalStateException("Getting Started program '" + targetProgram.getTitle() + "' is not currently published.");
        }

        if (!Boolean.TRUE.equals(request.getConfirmed())
                || request.getConfirmationToken() == null
                || request.getConfirmationToken().isBlank()) {
            AdminPendingConfirmationResponse pending = createPendingAction(
                    admin,
                    request.getConversationId(),
                    AdminPendingActionType.UNPUBLISH_PROGRAM,
                    targetId,
                    targetProgram.getTargetAudience(),
                    targetProgram.getTitle()
            );
            bindProgramToConversation(request.getConversationId(), targetId);
            return GettingStartedToolResultResponse.builder()
                    .status("CONFIRMATION_REQUIRED")
                    .confirmationRequired(true)
                    .program(targetProgram)
                    .pendingConfirmation(pending)
                    .eligibleAthleteCount(pending.getEligibleAthleteCount())
                    .message(pending.getSummary())
                    .build();
        }

        validateAndConsumePendingAction(admin, request.getConfirmationToken(), AdminPendingActionType.UNPUBLISH_PROGRAM, targetId);
        GettingStartedProgramResponse unpublished = gettingStartedService.unpublishProgramById(admin.getId(), targetId);
        bindProgramToConversation(request.getConversationId(), unpublished.getId());

        authorizationService.logToolExecution(
                AdminToolName.UNPUBLISH_PROGRAM, admin.getId(), unpublished.getId(),
                "confirmed=true, published=false"
        );

        return GettingStartedToolResultResponse.builder()
                .status("UNPUBLISHED")
                .confirmationRequired(false)
                .program(unpublished)
                .activeCampaigns(gettingStartedService.getActiveCampaigns())
                .message("Unpublished Getting Started campaign '" + unpublished.getTitle() + "' (ID " + unpublished.getId() + ").")
                .build();
    }

    private GettingStartedToolResultResponse handleGetActiveCampaigns(User admin) {
        List<GettingStartedCampaignSummaryResponse> campaigns = gettingStartedService.getActiveCampaigns();
        authorizationService.logToolExecution(
                AdminToolName.GET_ACTIVE_CAMPAIGNS, admin.getId(), null,
                "activeCampaignCount=" + campaigns.size()
        );
        String msg = campaigns.isEmpty()
                ? "There are currently no active published Getting Started campaigns."
                : "Found " + campaigns.size() + " active Getting Started campaign(s).";
        return GettingStartedToolResultResponse.builder()
                .status("ACTIVE_CAMPAIGNS")
                .confirmationRequired(false)
                .activeCampaigns(campaigns)
                .message(msg)
                .build();
    }

    private GettingStartedToolResultResponse handleGetCampaignAnalytics(User admin, AdminToolExecuteRequest request) {
        Long targetId = request.getProgramId();
        if (targetId == null && request.getConversationId() != null) {
            targetId = programRepository.findFirstPublished()
                    .map(GettingStartedProgram::getId)
                    .or(() -> conversationRepository.findByIdAndAdminUserId(request.getConversationId(), admin.getId())
                            .map(AdminAiConversation::getActiveGettingStartedProgramId))
                    .orElse(null);
        }
        GettingStartedCampaignAnalyticsResponse analytics = gettingStartedService.getCampaignAnalytics(targetId);
        authorizationService.logToolExecution(
                AdminToolName.GET_CAMPAIGN_ANALYTICS, admin.getId(), analytics.getProgramId(),
                "enrollments=" + analytics.getEnrollments() + ", eligible=" + analytics.getEligibleAthleteCount()
        );
        return GettingStartedToolResultResponse.builder()
                .status("CAMPAIGN_ANALYTICS")
                .confirmationRequired(false)
                .analytics(analytics)
                .eligibleAthleteCount(analytics.getEligibleAthleteCount())
                .message("Retrieved campaign analytics for '" + analytics.getProgramTitle() + "'.")
                .build();
    }

    @Transactional
    public AdminConfirmationDecisionResponse resolveConfirmationDecision(
            User admin,
            String confirmationToken,
            boolean approved,
            Long conversationIdHint
    ) {
        AdminAiPendingAction action = confirmationSupport.validatePendingActionForResolution(
                admin, confirmationToken, null);
        if (action.getActionType() != AdminPendingActionType.PUBLISH_PROGRAM
                && action.getActionType() != AdminPendingActionType.UNPUBLISH_PROGRAM) {
            throw new IllegalArgumentException("Confirmation token is not a Getting Started campaign action.");
        }

        Long convId = action.getConversation() != null ? action.getConversation().getId() : conversationIdHint;

        if (!approved) {
            action.setStatus(AdminPendingActionStatus.CANCELLED);
            action.setResolvedAt(ZonedDateTime.now());
            pendingActionRepository.save(action);
            return AdminConfirmationDecisionResponse.builder()
                    .confirmationToken(confirmationToken)
                    .actionType(action.getActionType())
                    .status(AdminPendingActionStatus.CANCELLED)
                    .conversationId(convId)
                    .reply("Cancelled " + (action.getActionType() == AdminPendingActionType.PUBLISH_PROGRAM ? "publishing" : "unpublishing")
                            + " Getting Started program (ID " + action.getProgram().getId() + "). No live campaign state was changed.")
                    .build();
        }

        AdminToolName toolName = action.getActionType() == AdminPendingActionType.PUBLISH_PROGRAM
                ? AdminToolName.PUBLISH_PROGRAM
                : AdminToolName.UNPUBLISH_PROGRAM;
        GettingStartedToolResultResponse result = executeTool(
                admin,
                toolName,
                AdminToolExecuteRequest.builder()
                        .toolName(toolName.getToolName())
                        .programId(action.getProgram().getId())
                        .conversationId(convId)
                        .targetAudience(action.getTargetAudience() != null ? action.getTargetAudience().name() : null)
                        .confirmed(true)
                        .confirmationToken(confirmationToken)
                        .build()
        );

        return AdminConfirmationDecisionResponse.builder()
                .confirmationToken(confirmationToken)
                .actionType(action.getActionType())
                .status(AdminPendingActionStatus.CONFIRMED)
                .conversationId(convId)
                .reply(result.getMessage())
                .execution(AdminToolExecutionRecord.builder()
                        .toolName(toolName.getToolName())
                        .status("EXECUTED")
                        .recordCount(1L)
                        .summary(result.getMessage())
                        .executedAt(ZonedDateTime.now())
                        .build())
                .gettingStartedProgram(result.getProgram())
                .activeCampaigns(result.getActiveCampaigns())
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<AdminAiPendingAction> findActivePendingConfirmation(Long adminId, Long conversationId) {
        return confirmationSupport.findActivePendingConfirmation(adminId, conversationId);
    }

    public boolean isExplicitConfirmationMessage(String message) {
        return confirmationSupport.isExplicitConfirmationMessage(message);
    }

    public boolean isExplicitCancellationMessage(String message) {
        return confirmationSupport.isExplicitCancellationMessage(message);
    }

    private AdminPendingConfirmationResponse createPendingAction(
            User admin,
            Long conversationId,
            AdminPendingActionType actionType,
            Long programId,
            TargetAudience targetAudience,
            String programTitle
    ) {
        confirmationSupport.expirePendingActionsForConversation(admin.getId(), conversationId);

        GettingStartedProgram programRef = programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Getting Started program not found: " + programId));
        TargetAudience resolvedAudience = targetAudience != null ? targetAudience
                : (programRef.getTargetAudience() != null ? programRef.getTargetAudience() : TargetAudience.ALL_ATHLETES);
        long eligibleCount = gettingStartedService.countEligibleAthletes(resolvedAudience);
        String audienceLabel = campaignHelper.audienceLabel(resolvedAudience);
        AdminAiConversation convRef = conversationId != null
                ? conversationRepository.findByIdAndAdminUserId(conversationId, admin.getId()).orElse(null)
                : null;

        String actionVerb = actionType == AdminPendingActionType.PUBLISH_PROGRAM ? "publish" : "unpublish";
        String summary = "Explicit Admin confirmation required before " + actionVerb + "ing Getting Started program '"
                + programTitle + "' (ID " + programId + ", target audience: " + audienceLabel
                + ", estimated eligible athletes: " + eligibleCount
                + "). Reply with 'Confirm' or click Confirm to proceed.";

        AdminAiPendingAction saved = pendingActionRepository.save(AdminAiPendingAction.builder()
                .confirmationToken(UUID.randomUUID().toString())
                .adminUser(admin)
                .conversation(convRef)
                .actionType(actionType)
                .program(programRef)
                .targetAudience(resolvedAudience)
                .status(AdminPendingActionStatus.PENDING)
                .summary(summary)
                .expiresAt(ZonedDateTime.now().plusMinutes(CONFIRMATION_TTL_MINUTES))
                .build());

        return AdminPendingConfirmationResponse.builder()
                .confirmationToken(saved.getConfirmationToken())
                .actionType(saved.getActionType())
                .status(saved.getStatus())
                .programId(programId)
                .programTitle(programTitle)
                .targetAudience(resolvedAudience)
                .audienceLabel(audienceLabel)
                .eligibleAthleteCount(eligibleCount)
                .summary(summary)
                .createdAt(saved.getCreatedAt())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    private AdminAiPendingAction validateAndConsumePendingAction(
            User admin,
            String token,
            AdminPendingActionType expectedType,
            Long expectedProgramId
    ) {
        AdminAiPendingAction action = confirmationSupport.validatePendingActionForResolution(
                admin, token, expectedType);
        if (expectedProgramId != null && (action.getProgram() == null
                || !Objects.equals(action.getProgram().getId(), expectedProgramId))) {
            throw new IllegalArgumentException("Confirmation token does not match target program ID " + expectedProgramId);
        }

        action.setStatus(AdminPendingActionStatus.CONFIRMED);
        action.setResolvedAt(ZonedDateTime.now());
        return pendingActionRepository.save(action);
    }

    private Long resolveTargetProgramId(AdminToolExecuteRequest request, boolean preferDraft) {
        if (request.getProgramId() != null) {
            return request.getProgramId();
        }
        if (request.getConversationId() != null) {
            Optional<Long> bound = conversationRepository.findById(request.getConversationId())
                    .map(AdminAiConversation::getActiveGettingStartedProgramId)
                    .filter(programRepository::existsById);
            if (bound.isPresent()) {
                return bound.get();
            }
        }
        if (preferDraft) {
            Optional<GettingStartedProgram> draft = programRepository.findLatestDraft();
            if (draft != null && draft.isPresent()) {
                return draft.get().getId();
            }
        }
        return programRepository.findFirstPublished()
                .or(programRepository::findLatest)
                .map(GettingStartedProgram::getId)
                .orElse(null);
    }

    private TargetAudience resolveOptionalAudience(String rawAudience) {
        if (rawAudience == null || rawAudience.isBlank()) {
            return null;
        }
        return campaignHelper.parseTargetAudience(rawAudience);
    }

    private void bindProgramToConversation(Long conversationId, Long programId) {
        if (conversationId == null || programId == null) {
            return;
        }
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setActiveGettingStartedProgramId(programId);
            conversationRepository.save(conv);
        });
    }
}
