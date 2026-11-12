package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.AdminExerciseService;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.adminai.dto.*;
import com.calisthenics.backend.adminai.entity.AdminAiAuditLog;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiMessage;
import com.calisthenics.backend.adminai.entity.AdminAiRecommendation;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.type.*;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.exercise.DifficultyLevel;
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
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminChatResponse chat(Long adminUserId, AdminChatRequest request) {
        User admin = conversationService.requireAdminUser(adminUserId);
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be blank");
        }

        String userPrompt = request.getMessage().trim();
        AdminAiConversation conversation = conversationService.resolveOrCreateConversation(adminUserId, request.getConversationId());
        AdminAiMessage savedUserMsg = conversationService.saveMessage(conversation, "user", userPrompt, null, null);

        AdminAnalyticsRange effectiveRange = resolveRangeFromPrompt(userPrompt, request.getRange());
        String detectedSkill = detectSkillFromPrompt(userPrompt);
        DifficultyLevel detectedLevel = detectDifficultyFromPrompt(userPrompt);

        AdminContextService.AdminFullContext context = contextService.buildContext(
                effectiveRange,
                request.getStartDate(),
                request.getEndDate(),
                detectedSkill
        );

        // Prepare multi-turn conversation history for Gemini
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

        String systemPrompt = contextService.buildAdminSystemPrompt(context);
        GeminiAdminAgentOutput geminiOutput = null;
        try {
            geminiOutput = geminiApiClient.callAdminAgent(systemPrompt, geminiContents).orElse(null);
        } catch (Exception ex) {
            log.warn("Admin AI Gemini call failed, falling back to deterministic analytical engine: {}", ex.getMessage());
        }

        AdminAiIntent intent = resolveIntent(userPrompt, geminiOutput);
        if (geminiOutput != null && geminiOutput.getTargetSkill() != null && !geminiOutput.getTargetSkill().isBlank()) {
            detectedSkill = geminiOutput.getTargetSkill().trim();
        }

        List<AdminRecommendationResponse> generatedRecommendations = new ArrayList<>();
        if (intent == AdminAiIntent.EXERCISE_RECOMMENDATION
                || (geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateExerciseRecommendations()))) {
            generatedRecommendations.addAll(createExerciseRecommendations(admin, conversation, detectedSkill, detectedLevel));
        } else if (intent == AdminAiIntent.WORKOUT_PROGRAM_RECOMMENDATION
                || (geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateWorkoutProgram()))) {
            String levelStr = detectedLevel != null ? detectedLevel.name() : (geminiOutput != null ? geminiOutput.getTargetLevel() : null);
            String focus = detectedSkill != null ? detectedSkill : userPrompt;
            generatedRecommendations.addAll(createWorkoutProgramRecommendations(admin, conversation, focus, levelStr));
        } else if (intent == AdminAiIntent.OPERATIONAL_INSIGHTS
                || (geminiOutput != null && Boolean.TRUE.equals(geminiOutput.getGenerateOperationalInsights()))) {
            generatedRecommendations.addAll(createOperationalRecommendations(admin, conversation, context.operationalInsights()));
        }

        String replyContent = (geminiOutput != null && geminiOutput.getReply() != null && !geminiOutput.getReply().isBlank())
                ? geminiOutput.getReply()
                : buildDataGroundedReply(intent, userPrompt, detectedSkill, context, generatedRecommendations);

        List<String> followUps = (geminiOutput != null && geminiOutput.getSuggestedFollowUps() != null && !geminiOutput.getSuggestedFollowUps().isEmpty())
                ? geminiOutput.getSuggestedFollowUps()
                : buildSuggestedFollowUps(intent, detectedSkill);

        String metadataJson = buildMessageMetadata(intent, generatedRecommendations, context.overview());
        AdminAiMessage savedAssistantMsg = conversationService.saveMessage(
                conversation,
                "assistant",
                replyContent,
                intent.name(),
                metadataJson
        );

        conversationService.updateTitleIfDefault(conversation, generateConversationTitle(userPrompt, intent));

        return AdminChatResponse.builder()
                .conversationId(conversation.getId())
                .conversationTitle(conversation.getTitle())
                .userMessage(conversationService.mapToMessageResponse(savedUserMsg))
                .assistantMessage(conversationService.mapToMessageResponse(savedAssistantMsg))
                .intent(intent.name())
                .recommendations(generatedRecommendations)
                .platformOverview(context.overview())
                .trainingAnalytics(context.trainingAnalytics())
                .suggestedFollowUps(followUps)
                .build();
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

    private AdminAiIntent resolveIntent(String userPrompt, GeminiAdminAgentOutput geminiOutput) {
        if (geminiOutput != null && geminiOutput.getIntent() != null) {
            try {
                return AdminAiIntent.valueOf(geminiOutput.getIntent().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        String lower = userPrompt.toLowerCase();
        if (lower.contains("what exercises")
                || lower.contains("add exercise")
                || lower.contains("missing exercise")
                || lower.contains("missing progression")
                || lower.contains("progression")
                || (detectSkillFromPrompt(userPrompt) != null && (lower.contains("exercise") || lower.contains("library") || lower.contains("add")))) {
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
        if (lower.contains("need improvement")
                || lower.contains("operational")
                || lower.contains("bottleneck")
                || lower.contains("opportunity")
                || lower.contains("opportunities")
                || lower.contains("issues")
                || lower.contains("attention")) {
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
        if (lower.contains("summarize")
                || lower.contains("overview")
                || lower.contains("platform activity")
                || lower.contains("how many users")
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
            List<AdminRecommendationResponse> recommendations
    ) {
        AdminPlatformOverviewResponse ov = context.overview();
        AdminTrainingAnalyticsResponse tr = context.trainingAnalytics();
        StringBuilder sb = new StringBuilder();

        switch (intent) {
            case PLATFORM_OVERVIEW -> {
                sb.append("### 📊 Platform Activity Summary (").append(ov.getRange()).append(": ")
                        .append(ov.getStartDate()).append(" to ").append(ov.getEndDate()).append(")\n\n");
                sb.append("#### 1. User & Coaching Ecosystem\n");
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
            case EXERCISE_RECOMMENDATION -> {
                String skillLabel = detectedSkill != null ? detectedSkill : "Calisthenics Skill & Strength";
                sb.append("### 💡 Exercise Recommendations for ").append(skillLabel).append("\n\n");
                sb.append("Based on an audit of our current **").append(ov.getTotalExercises())
                        .append("-exercise** catalog and athlete training patterns, the following non-duplicate progressions are recommended for administrator review:\n\n");
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
                    sb.append("> **Action Required**: These recommendations have been queued with status `PENDING_APPROVAL`. Approve any recommendation to create the exercise in the library.\n");
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

        return sb.toString();
    }

    private List<String> buildSuggestedFollowUps(AdminAiIntent intent, String detectedSkill) {
        return switch (intent) {
            case PLATFORM_OVERVIEW -> List.of(
                    "Analyze user training trends",
                    "Which areas of the platform need improvement?",
                    "What exercises should we add to improve our Planche training library?"
            );
            case USER_TRAINING_ANALYTICS -> List.of(
                    "Identify missing exercise progressions",
                    "Suggest new workout programs",
                    "Which areas of the platform need improvement?"
            );
            case EXERCISE_RECOMMENDATION -> List.of(
                    "What exercises should we add for Front Lever and Back Lever?",
                    "Suggest new workout programs",
                    "Summarize platform activity this month"
            );
            case WORKOUT_PROGRAM_RECOMMENDATION -> List.of(
                    "Identify missing exercise progressions",
                    "Analyze user training trends",
                    "Which areas of the platform need improvement?"
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
            AdminPlatformOverviewResponse overview
    ) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("intent", intent.name());
            meta.put("range", overview.getRange().name());
            meta.put("recommendationIds", recommendations.stream().map(AdminRecommendationResponse::getId).toList());
            meta.put("recommendationCount", recommendations.size());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception ex) {
            return null;
        }
    }

    private String generateConversationTitle(String userPrompt, AdminAiIntent intent) {
        String clean = userPrompt.replaceAll("[^a-zA-Z0-9\\s-]", "").trim();
        if (clean.isBlank()) {
            return switch (intent) {
                case PLATFORM_OVERVIEW -> "Platform Activity Overview";
                case USER_TRAINING_ANALYTICS -> "Training & User Analytics";
                case EXERCISE_RECOMMENDATION -> "Exercise Library Recommendations";
                case WORKOUT_PROGRAM_RECOMMENDATION -> "Draft Workout Programs";
                case OPERATIONAL_INSIGHTS -> "Operational Improvements";
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
