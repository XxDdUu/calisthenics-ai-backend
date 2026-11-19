package com.calisthenics.backend.aichat.service.impl;

import com.calisthenics.backend.aichat.dto.*;
import com.calisthenics.backend.aichat.dto.preview.CreateRecommendationPreviewRequest;
import com.calisthenics.backend.aichat.dto.preview.WorkoutPlanChangePreviewResponse;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.entity.AiWorkoutPlanChangePreview;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aichat.service.AiAssistantService;
import com.calisthenics.backend.aichat.service.AiWorkoutRecommendationService;
import com.calisthenics.backend.aichat.service.UserAiWorkoutToolService;
import com.calisthenics.backend.aichat.service.WorkoutPlanChangePreviewService;
import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.analytics.dto.CreatePersonalRecordRequest;
import com.calisthenics.backend.analytics.dto.PersonalRecordResponse;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.Gender;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private final AiConversationRepository aiConversationRepository;
    private final AiMessageRepository aiMessageRepository;
    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final PersonalRecordService personalRecordService;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final AiWorkoutPlanService aiWorkoutPlanService;
    private final GeminiApiClient geminiApiClient;
    private final ExerciseRepository exerciseRepository;
    private final ObjectMapper objectMapper;
    private final ClientBodyMeasurementRepository measurementRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final UserAiWorkoutToolService userAiWorkoutToolService;
    private final WorkoutPlanChangePreviewService workoutPlanChangePreviewService;
    private final AiWorkoutRecommendationService aiWorkoutRecommendationService;

    @Autowired(required = false)
    private CoachClientRelationshipRepository coachClientRelationshipRepository;

    private static final String TOOL_LIST_EXERCISES = "list_exercises";
    private static final String TOOL_RECOMMEND_EXERCISES = "recommend_exercises";
    private static final String TOOL_CONSULT_WORKOUT_PLAN = "consult_workout_plan";
    private static final String TOOL_GET_MY_COACH_INFO = "get_my_coach_info";
    private static final String TOOL_STORE_RECOMMENDATION_SELECTION = "store_recommendation_selection";
    private static final String TOOL_APPLY_SELECTED_RECOMMENDATIONS = "apply_selected_recommendations";

    @Override
    @Transactional(readOnly = true)
    public List<AiConversationSummaryResponse> getUserConversations(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return aiConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::mapToSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiConversationSummaryResponse getLatestConversationSummary(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return aiConversationRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .map(this::mapToSummary)
                .orElse(null);
    }

    @Override
    @Transactional
    public AiConversationDetailResponse getConversation(Long userId, Long conversationId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));

        List<AiMessageResponse> messages = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(msg -> mapToMessageResponse(msg, userId))
                .toList();

        return AiConversationDetailResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .messages(messages)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public AiConversationSummaryResponse createConversation(Long userId, CreateAiConversationRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "New Conversation";

        AiConversation conversation = AiConversation.builder()
                .user(user)
                .title(title)
                .build();

        AiConversation saved = aiConversationRepository.save(conversation);
        log.info("Created new AI Conversation id={} for user={}", saved.getId(), userId);
        return mapToSummary(saved);
    }

    @Override
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));

        if (workoutPlanChangePreviewService != null) {
            workoutPlanChangePreviewService.deleteConversationPreviews(conversationId);
        }
        aiMessageRepository.deleteByConversationId(conversationId);
        aiConversationRepository.delete(conversation);
        aiConversationRepository.flush();
        log.info("Deleted AI Conversation id={} for user={}", conversationId, userId);
    }

    @Override
    public WorkoutPlanChangePreviewResponse getPendingWorkoutPreview(Long userId, Long conversationId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return workoutPlanChangePreviewService.getPendingPreviewResponse(userId, conversationId);
    }

    @Override
    public WorkoutPlanChangePreviewService.PreviewActionOutcome confirmWorkoutPreview(
            Long userId, Long conversationId, String previewId
    ) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return workoutPlanChangePreviewService.confirmPreview(userId, conversationId, previewId, false, true);
    }

    @Override
    public WorkoutPlanChangePreviewService.PreviewActionOutcome cancelWorkoutPreview(
            Long userId, Long conversationId, String previewId
    ) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return workoutPlanChangePreviewService.cancelPreview(userId, conversationId, previewId, false, true);
    }

    @Override
    @Transactional
    public AiMessageResponse createRecommendationsPreview(
            Long userId, Long conversationId, CreateRecommendationPreviewRequest request
    ) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (request == null || request.getExerciseIds() == null || request.getExerciseIds().isEmpty()
                || request.getDayOfWeek() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise IDs and target weekday are required");
        }
        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));

        WorkoutPlanChangePreviewService.PrescriptionInput prescription =
                new WorkoutPlanChangePreviewService.PrescriptionInput(
                        request.getSets(),
                        request.getReps(),
                        request.getDurationSeconds(),
                        request.getRestSeconds()
                );

        UserAiWorkoutToolService.WorkoutToolResult res = workoutPlanChangePreviewService.proposeAddExercisesBatch(
                userId,
                conversationId,
                request.getExerciseIds(),
                request.getDayOfWeek(),
                prescription,
                "Add selected recommendations to " + request.getDayOfWeek().name(),
                false
        );

        String replyIntent = res.intent() != null ? res.intent() : "WORKOUT_PLAN_PREVIEW";
        AiMessage assistantMsg = aiMessageRepository.save(AiMessage.builder()
                .conversation(conversation)
                .role("assistant")
                .content(res.reply())
                .intent(replyIntent)
                .metadata(res.metadataJson())
                .build());
        attachPreviewAssistantMessageIfPresent(userId, res.metadataJson(), assistantMsg.getId());

        conversation.setUpdatedAt(ZonedDateTime.now());
        aiConversationRepository.save(conversation);
        return mapToMessageResponse(assistantMsg, userId);
    }

    @Override
    @Transactional
    public AiMessageResponse sendMessageToActiveConversation(Long userId, SendAiMessageRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        AiConversation conversation = aiConversationRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
                    return aiConversationRepository.save(AiConversation.builder()
                            .user(user)
                            .title("New Conversation")
                            .build());
                });

        return sendMessage(userId, conversation.getId(), request);
    }

    @Override
    @Transactional
    public AiMessageResponse sendMessage(Long userId, Long conversationId, SendAiMessageRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be blank");
        }

        AiConversation conversation = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Conversation not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // 1. Save User Message
        String userContent = request.getMessage().trim();
        AiMessage userMessage = AiMessage.builder()
                .conversation(conversation)
                .role("user")
                .content(userContent)
                .build();
        aiMessageRepository.save(userMessage);

        // 2. Load User Profile Context & Memory
        Optional<ClientProfile> clientProfileOpt = clientProfileRepository.findByUserId(userId);
        Double latestWeight = measurementRepository.findFirstByClientIdOrderByMeasuredAtDesc(userId)
                .map(ClientBodyMeasurement::getWeightKg)
                .map(BigDecimal::doubleValue)
                .orElse(null);
        List<PersonalRecord> recentPrs = personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId);
        List<WorkoutSession> recentSessions = workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(
                userId, WorkoutSessionStatus.COMPLETED);

        // 3. Prepare Multi-turn Conversation Context for Gemini + Full Recommendation History
        List<AiMessage> history = aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history); // chronological order
        List<AiMessage> priorHistory = history.stream()
                .filter(m -> userMessage.getId() == null || !Objects.equals(m.getId(), userMessage.getId()))
                .filter(m -> !("user".equalsIgnoreCase(m.getRole()) && userContent.equals(m.getContent()) && m == history.get(history.size() - 1)))
                .toList();
        List<AiMessage> fullAscHistoryRaw = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<AiMessage> fullPriorHistory = (fullAscHistoryRaw != null && !fullAscHistoryRaw.isEmpty())
                ? fullAscHistoryRaw.stream()
                    .filter(m -> userMessage.getId() == null || !Objects.equals(m.getId(), userMessage.getId()))
                    .filter(m -> !("user".equalsIgnoreCase(m.getRole()) && userContent.equals(m.getContent()) && m == fullAscHistoryRaw.get(fullAscHistoryRaw.size() - 1)))
                    .toList()
                : priorHistory;

        WorkoutDayOfWeek selectedDayOfWeek = request.getSelectedDayOfWeek();
        boolean isVn = isVietnamese(userContent);

        // 3b. Check active PENDING workout plan preview context FIRST (Confirm / Cancel / Follow-up Revision)
        Optional<AiWorkoutPlanChangePreview> activePendingPreview = workoutPlanChangePreviewService != null
                ? workoutPlanChangePreviewService.findActivePendingPreview(userId, conversationId)
                : Optional.empty();
        if (activePendingPreview.isPresent()) {
            ActionResult pendingFollowUpResult = tryHandlePendingPreviewFollowUp(
                    userId, conversationId, activePendingPreview.get(), userContent, isVn);
            if (pendingFollowUpResult != null) {
                AiConversation managedConv = aiConversationRepository.findByIdAndUserId(conversationId, userId)
                        .orElse(conversation);
                String followUpIntent = pendingFollowUpResult.intentOverride() != null
                        ? pendingFollowUpResult.intentOverride()
                        : "WORKOUT_PLAN_PREVIEW";
                AiMessage savedMsg = aiMessageRepository.save(AiMessage.builder()
                        .conversation(managedConv)
                        .role("assistant")
                        .content(pendingFollowUpResult.replyOverride())
                        .intent(followUpIntent)
                        .metadata(pendingFollowUpResult.metadataJson())
                        .build());
                attachPreviewAssistantMessageIfPresent(userId, pendingFollowUpResult.metadataJson(), savedMsg.getId());
                if ("New Conversation".equalsIgnoreCase(managedConv.getTitle())) {
                    managedConv.setTitle(generateTitleFromMessage(userContent, followUpIntent));
                }
                managedConv.setUpdatedAt(ZonedDateTime.now());
                aiConversationRepository.save(managedConv);
                return mapToMessageResponse(savedMsg, userId);
            }
        } else if (isPureConfirmMessage(userContent) || isPureCancelMessage(userContent)) {
            // If user tried to confirm/cancel after a preview expired or when none is pending, reply cleanly without mutating DB
            String noPendingReply = isPureConfirmMessage(userContent)
                    ? (isVn ? "Hiện không có bản xem trước thay đổi lịch tập nào đang chờ xác nhận (hoặc bản trước đó đã hết hạn). Hãy gửi yêu cầu chỉnh sửa để tôi tạo bản xem trước mới nhé."
                            : "There is no active pending workout plan preview to confirm (it may have already been resolved or expired). Let me know what change you'd like to make and I'll generate a fresh preview.")
                    : (isVn ? "Hiện không có bản xem trước thay đổi lịch tập nào đang chờ để hủy. Lịch tập của bạn vẫn được giữ nguyên."
                            : "There is no pending workout plan preview to cancel. Your workout plan remains unchanged.");
            AiMessage savedMsg = aiMessageRepository.save(AiMessage.builder()
                    .conversation(conversation)
                    .role("assistant")
                    .content(noPendingReply)
                    .intent("WORKOUT_PLAN_PREVIEW")
                    .metadata(safeToJson(Map.of(
                            "type", WorkoutPlanChangePreviewService.METADATA_TYPE_CANCELLED,
                            "code", "NO_PENDING_PREVIEW",
                            "mutated", false
                    )))
                    .build());
            conversation.setUpdatedAt(ZonedDateTime.now());
            aiConversationRepository.save(conversation);
            return mapToMessageResponse(savedMsg, userId);
        }

        WorkoutIntentClassification preClassifiedWorkout = classifyWorkoutIntent(userContent, fullPriorHistory);

        List<GeminiRequestDto.Content> geminiContents = new ArrayList<>();
        for (AiMessage msg : history) {
            String geminiRole = "user".equalsIgnoreCase(msg.getRole()) ? "user" : "model";
            geminiContents.add(GeminiRequestDto.Content.builder()
                    .role(geminiRole)
                    .parts(List.of(new GeminiRequestDto.Part(msg.getContent())))
                    .build());
        }

        String systemPrompt = buildSystemPrompt(user, clientProfileOpt.orElse(null), latestWeight, recentPrs, recentSessions);

        // 4. Call Gemini Structured Intent Classifier & Assistant (skip when deterministic read-only or exercise-mutation intent applies)
        GeminiAssistantOutput assistantOutput = null;
        if (preClassifiedWorkout == null
                || (!preClassifiedWorkout.readOnly() && !isDeterministicWorkoutTool(preClassifiedWorkout.toolName()))) {
            try {
                assistantOutput = geminiApiClient.callAssistant(systemPrompt, geminiContents).orElse(null);
            } catch (Exception ex) {
                log.warn("Gemini assistant call encountered error: {}. Falling back to rule-based engine.", ex.getMessage());
            }
        }

        // 5. Rule-Based Fallback if Gemini is offline or skipped for deterministic safety
        if (assistantOutput == null || assistantOutput.getIntent() == null) {
            assistantOutput = fallbackIntentEngine(userContent, recentPrs, fullPriorHistory);
        }

        // Guardrail: enforce deterministic read-only/write workout classification and explicit weekday priority
        WorkoutIntentClassification finalWorkoutDecision = reconcileWorkoutDecision(
                preClassifiedWorkout, userContent, assistantOutput, fullPriorHistory);

        // 6. Action Execution based on Intent
        String detectedIntent = finalWorkoutDecision != null
                ? finalWorkoutDecision.intent()
                : assistantOutput.getIntent();
        String replyContent = assistantOutput.getReply();
        String actionMetadataJson = null;

        if ("PR_RECORD".equalsIgnoreCase(detectedIntent)) {
            ActionResult prResult = executePrRecordAction(userId, userContent, assistantOutput.getPrAction());
            if (prResult.replyOverride != null) {
                replyContent = prResult.replyOverride;
            }
            actionMetadataJson = prResult.metadataJson;
        } else if (finalWorkoutDecision != null) {
            ActionResult planResult = executeClassifiedWorkoutTool(
                    userId,
                    conversationId,
                    userContent,
                    finalWorkoutDecision,
                    assistantOutput.getWorkoutPlanAction(),
                    fullPriorHistory,
                    selectedDayOfWeek,
                    request.getTimezone()
            );
            if (planResult.replyOverride != null) {
                replyContent = planResult.replyOverride;
            }
            if (planResult.intentOverride != null) {
                detectedIntent = planResult.intentOverride;
            }
            actionMetadataJson = planResult.metadataJson;
        } else if ("WORKOUT_PLAN".equalsIgnoreCase(detectedIntent)) {
            ActionResult planResult = executeWorkoutPlanAction(userId, userContent, assistantOutput.getWorkoutPlanAction(), request.getTimezone());
            if (planResult.replyOverride != null) {
                replyContent = planResult.replyOverride;
            }
            actionMetadataJson = planResult.metadataJson;
        } else if ("PROGRESS_ANALYSIS".equalsIgnoreCase(detectedIntent)) {
            ActionResult progressResult = executeProgressAnalysisAction(userId, recentPrs, recentSessions);
            if (progressResult.replyOverride != null && (replyContent == null || replyContent.isBlank())) {
                replyContent = progressResult.replyOverride;
            }
            actionMetadataJson = progressResult.metadataJson;
        } else if ("PROFILE_UPDATE".equalsIgnoreCase(detectedIntent)) {
            ActionResult profileResult = executeProfileUpdateAction(userId, userContent, assistantOutput.getProfileUpdateAction());
            if (profileResult.replyOverride != null) {
                replyContent = profileResult.replyOverride;
            }
            actionMetadataJson = profileResult.metadataJson;
        }

        if (replyContent == null || replyContent.isBlank()) {
            replyContent = "I'm your Calisthenics AI Assistant. Let me know if you want to create a workout plan, record a personal record, update your profile, or analyze your training progress!";
        }

        // 7. Save Assistant Message
        AiMessage assistantMessage = AiMessage.builder()
                .conversation(conversation)
                .role("assistant")
                .content(replyContent)
                .intent(detectedIntent)
                .metadata(actionMetadataJson)
                .build();
        AiMessage savedAssistantMessage = aiMessageRepository.save(assistantMessage);
        attachPreviewAssistantMessageIfPresent(userId, actionMetadataJson, savedAssistantMessage.getId());

        // 8. Auto-update Conversation Title if still "New Conversation"
        if ("New Conversation".equalsIgnoreCase(conversation.getTitle())) {
            conversation.setTitle(generateTitleFromMessage(userContent, detectedIntent));
        }
        conversation.setUpdatedAt(ZonedDateTime.now());
        aiConversationRepository.save(conversation);

        log.info("AI message processed in conversation={}. Intent={}, HasMetadata={}",
                conversationId, detectedIntent, actionMetadataJson != null);

        return mapToMessageResponse(savedAssistantMessage, userId);
    }

    private String buildSystemPrompt(
            User user,
            ClientProfile profile,
            Double latestWeight,
            List<PersonalRecord> recentPrs,
            List<WorkoutSession> recentSessions
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the Calisthenics AI Assistant, an automated AI software assistant (NEVER claim to be the user's assigned human coach and NEVER invent a coach profile).\n");
        sb.append("User Information:\n");
        sb.append("- Name: ").append(user.getFullName() != null ? user.getFullName() : "Athlete").append("\n");
        if (user.getGender() != null) {
            sb.append("- Gender: ").append(user.getGender()).append("\n");
        }
        if (profile != null) {
            if (profile.getDisplayName() != null) sb.append("- Display Name: ").append(profile.getDisplayName()).append("\n");
            if (profile.getBio() != null) sb.append("- Bio: ").append(profile.getBio()).append("\n");
            if (profile.getHeightCm() != null) sb.append("- Height: ").append(profile.getHeightCm()).append(" cm\n");
            if (profile.getExperienceLevel() != null) sb.append("- Experience Level: ").append(profile.getExperienceLevel()).append("\n");
            if (profile.getPrimaryGoals() != null) sb.append("- Primary Goals: ").append(profile.getPrimaryGoals()).append("\n");
            if (profile.getTrainingInterests() != null) sb.append("- Training Interests: ").append(profile.getTrainingInterests()).append("\n");
            if (profile.getPreferredFrequency() != null) sb.append("- Preferred Frequency: ").append(profile.getPreferredFrequency()).append("\n");
            if (profile.getCoachingLanguage() != null) sb.append("- Coaching Language: ").append(profile.getCoachingLanguage()).append("\n");
        }
        if (latestWeight != null) {
            sb.append("- Current Weight: ").append(latestWeight).append(" kg\n");
        }

        sb.append("\nUser Calisthenics History & Memory:\n");
        if (recentPrs.isEmpty()) {
            sb.append("- No Personal Records recorded yet.\n");
        } else {
            sb.append("- Recent PRs: ");
            String prSummary = recentPrs.stream().limit(6)
                    .map(pr -> pr.getEffectiveExerciseName() + " (" + pr.getRecordValue() + " " + (pr.getUnit() != null ? pr.getUnit() : "") + ")")
                    .collect(Collectors.joining(", "));
            sb.append(prSummary).append("\n");
        }

        sb.append("- Completed Sessions Count: ").append(recentSessions.size()).append("\n");

        sb.append("\nCapabilities & Intent Detection Instructions:\n");
        sb.append("1. 'PR_RECORD': The user wants to record a Personal Record or reports achieving a new best (e.g., 'I did 10 muscle ups', 'Hit 30kg dip for 3 reps', 'Recorded a 10s handstand').\n");
        sb.append("   - If unambiguous (exercise, value, and type are clear), populate 'pr_action' with 'is_ambiguous=false'.\n");
        sb.append("   - If ambiguous (e.g. missing value, missing exercise), set 'is_ambiguous=true' and ask a clarifying question in 'reply'.\n");
        sb.append("2. 'WORKOUT_PLAN_VIEW' / 'WORKOUT_DAY_VIEW' / 'EXERCISE_DETAILS_VIEW' (STRICTLY READ-ONLY): The user asks to view, show, list, or explain their existing workout plan, weekly schedule, today's workout, or a specific day's workout (e.g., 'Show my workout plan', 'What am I training today?', 'Show Wednesday', 'Lịch tập của tôi thế nào?'). NEVER create or modify a workout plan for these queries.\n");
        sb.append("3. 'LIST_EXERCISES' (READ-ONLY INFORMATIONAL): The user asks for a list of exercises (e.g., 'List me some leg exercises', 'What exercises can I do for my legs?'). Return ONLY a clean numbered list of relevant exercises. NEVER analyze their workout plan, report muscle gaps, display weekly sets, suggest weekdays, ask which day to train, or append follow-up questions.\n");
        sb.append("4. 'EXERCISE_RECOMMENDATION' (READ-ONLY ADVISORY): The user asks for exercise recommendations (e.g., 'Recommend some exercises for handstand push-ups', 'Suggest exercises to improve my planche', 'Recommend some leg exercises to add to my leg day', 'What exercises am I missing in my current routine?'). Unless the user explicitly mentions their plan/routine/day, do NOT analyze their workout plan, report muscle gaps, display weekly sets, or suggest weekdays. NEVER modify the schedule automatically.\n");
        sb.append("5. 'WORKOUT_CONSULTATION' (READ-ONLY COACHING): The user asks to evaluate, review, check recovery, muscle balance, or training volume of their workout routine or day (e.g., 'Is my leg day balanced?', 'Am I training my hamstrings enough?', 'Does my weekly routine have enough recovery?', 'Is my current planche training volume too high?'). NEVER modify the schedule.\n");
        sb.append("6. 'COACH_INFO' (READ-ONLY ACCOUNT LOOKUP): The user asks about their assigned human coach ('Who is my coach?', 'Show me my coach information', 'Show my coach profile'). Retrieve only real backend data.\n");
        sb.append("7. 'WORKOUT_PLAN' / 'WORKOUT_PLAN_UPDATE' (WRITE ONLY ON EXPLICIT REQUEST): The user explicitly commands creating, generating, replacing, regenerating, updating, adding specific exercises to, removing exercises from, or moving exercises between days in a workout plan.\n");
        sb.append("   - Populate 'workout_plan_action' with tool_name, prompt, fitness_level, duration_weeks, day_of_week, source_day_of_week (when moving), exercise_name, and detailed.\n");
        sb.append("   - CRITICAL RULES: Explicit weekday requests ALWAYS take priority over any selected day. Never infer a different weekday when one is explicitly stated (e.g. 'to Tuesday not Monday' means TUESDAY). Never substitute or add unrelated exercises.\n");
        sb.append("8. 'PROGRESS_ANALYSIS': The user asks about their progress, plateaus, improvements, or training trends (e.g., 'How is my pull up progress?', 'Am I plateauing on dips?').\n");
        sb.append("   - Populate 'progress_analysis_action'. Provide data-backed insights.\n");
        sb.append("9. 'PROFILE_UPDATE': The user requests to update their personal/athlete profile details (e.g. 'Cập nhật cân nặng của tôi thành 76kg', 'Đổi mục tiêu tập luyện thành Straddle Planche', 'My height is 178 cm', 'Update my bio to Calisthenics athlete', 'Change display name to Alex').\n");
        sb.append("   - Supported editable fields: display_name, full_name, bio, height_cm, weight_kg, experience_level, training_experience, primary_goals, training_interests, preferred_frequency, coaching_language, gender.\n");
        sb.append("   - If unambiguous and has specific field and value, set 'is_ambiguous=false' and populate 'profile_update_action'.\n");
        sb.append("   - If ambiguous or missing specific values (e.g. 'update my profile' without specifying what), set 'is_ambiguous=true' and ask what they want to change in 'clarification_question'.\n");
        sb.append("10. 'GENERAL_CHAT': Form tips, exercise breakdowns, mobility advice, recovery, or motivation.\n");
        sb.append("   - Provide structured, rich Markdown responses (headers, bold, bullet points).\n");
        return sb.toString();
    }

    private record ActionResult(String replyOverride, String metadataJson, String intentOverride) {
        private ActionResult(String replyOverride, String metadataJson) {
            this(replyOverride, metadataJson, null);
        }
    }

    private ActionResult executePrRecordAction(Long userId, String userMessage, GeminiAssistantOutput.PrAction prAction) {
        if (prAction == null || Boolean.TRUE.equals(prAction.getIsAmbiguous()) || prAction.getValue() == null) {
            return new ActionResult(null, null);
        }

        try {
            String exerciseName = prAction.getExerciseName();
            if (exerciseName == null || exerciseName.isBlank()) {
                exerciseName = "Calisthenics Movement";
            }

            PersonalRecordType type = PersonalRecordType.MAX_REPS;
            if (prAction.getPrType() != null) {
                try {
                    type = PersonalRecordType.valueOf(prAction.getPrType().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    type = inferRecordType(prAction.getUnit());
                }
            } else {
                type = inferRecordType(prAction.getUnit());
            }

            String unit = prAction.getUnit();
            if (unit == null || unit.isBlank()) {
                unit = switch (type) {
                    case MAX_WEIGHT -> "kg";
                    case MAX_HOLD -> "s";
                    case MAX_REPS, BODYWEIGHT, MAX_DISTANCE, MAX_DURATION, MAX_VOLUME -> "reps";
                };
            }

            CreatePersonalRecordRequest req = CreatePersonalRecordRequest.builder()
                    .exerciseName(exerciseName)
                    .prType(type)
                    .value(prAction.getValue())
                    .unit(unit)
                    .achievedAt(ZonedDateTime.now())
                    .notes(prAction.getNotes() != null ? prAction.getNotes() : "Logged via AI Assistant")
                    .build();

            PersonalRecordResponse prResponse = personalRecordService.createPR(userId, req);

            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "PR_RECORDED");
            cardData.put("pr", prResponse);
            String metadataJson = objectMapper.writeValueAsString(cardData);

            String prTypeDisplay = prResponse.getRecordType() != null
                    ? prResponse.getRecordType().replace('_', ' ')
                    : "PR";

            String reply = String.format("🏆 **Personal Record Saved!**\n\nI've recorded your new **%s** PR: **%s %s** (%s)!\n\nKeep pushing the limits! You can view and manage all your achievements in the Personal Records section.",
                    prResponse.getExerciseName(),
                    prResponse.getValue(),
                    prResponse.getUnit() != null ? prResponse.getUnit() : "",
                    prTypeDisplay);

            return new ActionResult(reply, metadataJson);
        } catch (Exception ex) {
            log.warn("Failed to automatically record PR from AI assistant: {}", ex.getMessage());
            return new ActionResult(null, null);
        }
    }

    private ActionResult executeWorkoutPlanAction(
            Long userId,
            String userMessage,
            GeminiAssistantOutput.WorkoutPlanAction planAction,
            String timezone
    ) {
        try {
            String prompt = (planAction != null && planAction.getPrompt() != null && !planAction.getPrompt().isBlank())
                    ? planAction.getPrompt()
                    : userMessage;

            String fitnessLevel = (planAction != null && planAction.getFitnessLevel() != null)
                    ? planAction.getFitnessLevel()
                    : "INTERMEDIATE";

            int durationWeeks = (planAction != null && planAction.getDurationWeeks() != null && planAction.getDurationWeeks() > 0)
                    ? Math.min(12, Math.max(1, planAction.getDurationWeeks()))
                    : 4;

            GenerateAiWorkoutPlanRequest req = GenerateAiWorkoutPlanRequest.builder()
                    .prompt(prompt)
                    .fitnessLevel(fitnessLevel)
                    .durationWeeks(durationWeeks)
                    .build();

            AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(userId, req, timezone);

            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "WORKOUT_PLAN_PREVIEW");
            cardData.put("toolName", UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN);
            cardData.put("readOnly", false);
            cardData.put("plan", preview);
            String metadataJson = objectMapper.writeValueAsString(cardData);

            String reply = String.format("📋 **AI Workout Plan Generated!**\n\nI have crafted a **%d-week %s** routine: **%s**.\n\n%s\n\nYou can review each day's sessions below and commit it directly to your training calendar!",
                    preview.getDurationWeeks(),
                    preview.getSplitType() != null ? preview.getSplitType().name().replace('_', ' ') : "Calisthenics",
                    preview.getPlanName(),
                    preview.getDescription() != null ? preview.getDescription() : "");

            return new ActionResult(reply, metadataJson);
        } catch (Exception ex) {
            log.warn("Failed to generate workout plan from AI assistant: {}", ex.getMessage());
            return new ActionResult(null, null);
        }
    }

    private ActionResult executeProgressAnalysisAction(
            Long userId,
            List<PersonalRecord> recentPrs,
            List<WorkoutSession> recentSessions
    ) {
        try {
            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "PROGRESS_SUMMARY");

            int totalPrs = recentPrs.size();
            long uniqueExercisesWithPr = recentPrs.stream()
                    .map(PersonalRecord::getEffectiveExerciseName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            List<Map<String, Object>> topPrs = recentPrs.stream().limit(5)
                    .map(pr -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("exerciseName", pr.getEffectiveExerciseName());
                        m.put("value", pr.getRecordValue());
                        m.put("unit", pr.getUnit());
                        m.put("type", pr.getRecordType().name());
                        m.put("achievedAt", pr.getAchievedAt() != null ? pr.getAchievedAt().toString() : "");
                        return m;
                    })
                    .toList();

            cardData.put("totalPrs", totalPrs);
            cardData.put("uniqueExercisesWithPr", uniqueExercisesWithPr);
            cardData.put("completedSessionsCount", recentSessions.size());
            cardData.put("topPrs", topPrs);

            String metadataJson = objectMapper.writeValueAsString(cardData);

            String reply = String.format("📈 **Calisthenics Training Progress Analysis**\n\n"
                            + "- **Total Personal Records**: %d across %d different exercises\n"
                            + "- **Completed Workout Sessions**: %d\n\n"
                            + "### Highlights & Plateaus:\n"
                            + "%s\n\n"
                            + "### Recommendations:\n"
                            + "1. Ensure systematic progressive overload (increase hold times by 1-2s or reps by 1 each week).\n"
                            + "2. Focus on joint conditioning (wrists, elbows, and shoulders) to support higher skill demands.",
                    totalPrs,
                    uniqueExercisesWithPr,
                    recentSessions.size(),
                    recentPrs.isEmpty()
                            ? "No PRs recorded yet. Start logging your max reps, holds, and weighted attempts!"
                            : "Your strongest progress is concentrated in: " + recentPrs.stream().limit(3).map(PersonalRecord::getEffectiveExerciseName).collect(Collectors.joining(", ")) + ".");

            return new ActionResult(reply, metadataJson);
        } catch (Exception ex) {
            log.warn("Failed to analyze progress: {}", ex.getMessage());
            return new ActionResult(null, null);
        }
    }

    private ActionResult executeProfileUpdateAction(
            Long userId,
            String userMessage,
            GeminiAssistantOutput.ProfileUpdateAction action
    ) {
        boolean isVn = isVietnamese(userMessage);

        if (action == null) {
            String reply = isVn
                    ? "Tôi chưa nhận diện được thông tin cần cập nhật. Bạn muốn đổi cân nặng, chiều cao, mục tiêu tập luyện hay thông tin nào khác?"
                    : "I couldn't identify what you'd like to update. Would you like to update your weight, height, training goals, or something else?";
            return new ActionResult(reply, null);
        }

        if (Boolean.TRUE.equals(action.getIsAmbiguous())) {
            String question = action.getClarificationQuestion();
            if (question == null || question.isBlank()) {
                question = isVn
                        ? "Bạn muốn cập nhật thông tin nào trong hồ sơ của mình (ví dụ: cân nặng, chiều cao, mục tiêu tập luyện, trình độ...) và giá trị mới là gì?"
                        : "Which profile detail would you like to update (e.g., weight, height, training goals, experience level...) and what is the new value?";
            }
            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "PROFILE_UPDATED");
            cardData.put("actionType", "PROFILE_UPDATE");
            cardData.put("success", false);
            cardData.put("isAmbiguous", true);
            return new ActionResult(question, safeToJson(cardData));
        }

        // Validate weight range (20kg to 500kg)
        if (action.getWeightKg() != null) {
            double w = action.getWeightKg();
            if (w < 20.0 || w > 500.0) {
                String reply = isVn
                        ? String.format("Cân nặng không hợp lệ (%.1f kg). Vui lòng cung cấp cân nặng thực tế trong khoảng từ 20 kg đến 500 kg.", w)
                        : String.format("Invalid weight (%.1f kg). Please enter a realistic weight between 20 kg and 500 kg.", w);
                Map<String, Object> cardData = new LinkedHashMap<>();
                cardData.put("type", "PROFILE_UPDATED");
                cardData.put("actionType", "PROFILE_UPDATE");
                cardData.put("success", false);
                cardData.put("error", "INVALID_WEIGHT");
                return new ActionResult(reply, safeToJson(cardData));
            }
        }

        // Validate height range (50cm to 300cm)
        if (action.getHeightCm() != null) {
            double h = action.getHeightCm();
            if (h < 50.0 || h > 300.0) {
                String reply = isVn
                        ? String.format("Chiều cao không hợp lệ (%.1f cm). Vui lòng cung cấp chiều cao thực tế trong khoảng từ 50 cm đến 300 cm.", h)
                        : String.format("Invalid height (%.1f cm). Please enter a realistic height between 50 cm and 300 cm.", h);
                Map<String, Object> cardData = new LinkedHashMap<>();
                cardData.put("type", "PROFILE_UPDATED");
                cardData.put("actionType", "PROFILE_UPDATE");
                cardData.put("success", false);
                cardData.put("error", "INVALID_HEIGHT");
                return new ActionResult(reply, safeToJson(cardData));
            }
        }

        // Check if at least one editable field is present
        boolean hasAnyField = action.getWeightKg() != null
                || action.getHeightCm() != null
                || (action.getDisplayName() != null && !action.getDisplayName().isBlank())
                || (action.getFullName() != null && !action.getFullName().isBlank())
                || (action.getBio() != null && !action.getBio().isBlank())
                || (action.getPrimaryGoals() != null && !action.getPrimaryGoals().isBlank())
                || (action.getExperienceLevel() != null && !action.getExperienceLevel().isBlank())
                || (action.getTrainingExperience() != null && !action.getTrainingExperience().isBlank())
                || (action.getTrainingInterests() != null && !action.getTrainingInterests().isBlank())
                || (action.getPreferredFrequency() != null && !action.getPreferredFrequency().isBlank())
                || (action.getCoachingLanguage() != null && !action.getCoachingLanguage().isBlank())
                || (action.getGender() != null && !action.getGender().isBlank());

        if (!hasAnyField) {
            String reply = isVn
                    ? "Bạn muốn cập nhật thông tin nào trong hồ sơ của mình (cân nặng, chiều cao, mục tiêu tập luyện...)? Hãy nói rõ để tôi hỗ trợ nhé!"
                    : "Which profile detail would you like to update (weight, height, goals...)? Please let me know!";
            return new ActionResult(reply, null);
        }

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            ClientProfile profile = clientProfileRepository.findByUserId(userId)
                    .orElseGet(() -> ClientProfile.builder()
                            .user(user)
                            .experienceLevel(user.getExperienceLevel())
                            .build());

            Map<String, Object> updatedFields = new LinkedHashMap<>();

            if (action.getDisplayName() != null && !action.getDisplayName().isBlank()) {
                profile.setDisplayName(action.getDisplayName().trim());
                updatedFields.put("displayName", profile.getDisplayName());
            }

            if (action.getFullName() != null && !action.getFullName().isBlank()) {
                user.setFullName(action.getFullName().trim());
                if (action.getDisplayName() == null) {
                    profile.setDisplayName(action.getFullName().trim());
                }
                updatedFields.put("fullName", user.getFullName());
            }

            if (action.getBio() != null) {
                profile.setBio(action.getBio().trim());
                updatedFields.put("bio", profile.getBio());
            }

            if (action.getHeightCm() != null) {
                profile.setHeightCm(BigDecimal.valueOf(action.getHeightCm()));
                updatedFields.put("heightCm", action.getHeightCm());
            }

            if (action.getExperienceLevel() != null && !action.getExperienceLevel().isBlank()) {
                String exp = action.getExperienceLevel().trim().toUpperCase();
                profile.setExperienceLevel(exp);
                user.setExperienceLevel(exp);
                updatedFields.put("experienceLevel", exp);
            }

            if (action.getTrainingExperience() != null) {
                profile.setTrainingExperience(action.getTrainingExperience().trim());
                updatedFields.put("trainingExperience", profile.getTrainingExperience());
            }

            if (action.getPrimaryGoals() != null) {
                profile.setPrimaryGoals(action.getPrimaryGoals().trim());
                updatedFields.put("primaryGoals", profile.getPrimaryGoals());
            }

            if (action.getTrainingInterests() != null) {
                profile.setTrainingInterests(action.getTrainingInterests().trim());
                updatedFields.put("trainingInterests", profile.getTrainingInterests());
            }

            if (action.getPreferredFrequency() != null) {
                profile.setPreferredFrequency(action.getPreferredFrequency().trim());
                updatedFields.put("preferredFrequency", profile.getPreferredFrequency());
            }

            if (action.getCoachingLanguage() != null) {
                profile.setCoachingLanguage(action.getCoachingLanguage().trim());
                updatedFields.put("coachingLanguage", profile.getCoachingLanguage());
            }

            if (action.getGender() != null && !action.getGender().isBlank()) {
                try {
                    Gender g = Gender.valueOf(action.getGender().trim().toUpperCase());
                    user.setGender(g);
                    updatedFields.put("gender", g.name());
                } catch (IllegalArgumentException ignored) {}
            }

            userRepository.save(user);
            clientProfileRepository.save(profile);

            if (action.getWeightKg() != null) {
                measurementRepository.save(ClientBodyMeasurement.builder()
                        .client(user)
                        .weightKg(BigDecimal.valueOf(action.getWeightKg()))
                        .measuredAt(ZonedDateTime.now())
                        .build());
                updatedFields.put("weightKg", action.getWeightKg());
            }

            // Sync CoachProfile if exists
            coachProfileRepository.findByUserId(userId).ifPresent(cp -> {
                if (updatedFields.containsKey("displayName")) {
                    cp.setDisplayName((String) updatedFields.get("displayName"));
                }
                if (updatedFields.containsKey("bio")) {
                    cp.setBio((String) updatedFields.get("bio"));
                }
                coachProfileRepository.save(cp);
            });

            Double currentWeight = measurementRepository.findFirstByClientIdOrderByMeasuredAtDesc(userId)
                    .map(ClientBodyMeasurement::getWeightKg)
                    .map(BigDecimal::doubleValue)
                    .orElse(null);

            Map<String, Object> currentProfile = new LinkedHashMap<>();
            currentProfile.put("userId", userId);
            currentProfile.put("fullName", user.getFullName());
            currentProfile.put("displayName", profile.getDisplayName() != null ? profile.getDisplayName() : user.getFullName());
            currentProfile.put("bio", profile.getBio());
            currentProfile.put("experienceLevel", profile.getExperienceLevel());
            currentProfile.put("primaryGoals", profile.getPrimaryGoals());
            currentProfile.put("trainingInterests", profile.getTrainingInterests());
            currentProfile.put("preferredFrequency", profile.getPreferredFrequency());
            currentProfile.put("coachingLanguage", profile.getCoachingLanguage());
            currentProfile.put("gender", user.getGender() != null ? user.getGender().name() : null);
            currentProfile.put("heightCm", profile.getHeightCm() != null ? profile.getHeightCm().doubleValue() : null);
            currentProfile.put("weightKg", currentWeight);

            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "PROFILE_UPDATED");
            cardData.put("actionType", "PROFILE_UPDATE");
            cardData.put("success", true);
            cardData.put("updatedFields", updatedFields);
            cardData.put("currentProfile", currentProfile);

            String metadataJson = safeToJson(cardData);

            // Construct truthful localized reply
            StringBuilder replySb = new StringBuilder();
            if (isVn) {
                replySb.append("✅ **Cập nhật hồ sơ thành công!**\n\nTôi đã lưu các thông tin mới vào hồ sơ của bạn:\n");
                if (updatedFields.containsKey("weightKg")) {
                    replySb.append(String.format("- **Cân nặng**: %.1f kg\n", (Double) updatedFields.get("weightKg")));
                }
                if (updatedFields.containsKey("heightCm")) {
                    replySb.append(String.format("- **Chiều cao**: %.1f cm\n", (Double) updatedFields.get("heightCm")));
                }
                if (updatedFields.containsKey("primaryGoals")) {
                    replySb.append(String.format("- **Mục tiêu tập luyện**: %s\n", updatedFields.get("primaryGoals")));
                }
                if (updatedFields.containsKey("experienceLevel")) {
                    replySb.append(String.format("- **Trình độ**: %s\n", updatedFields.get("experienceLevel")));
                }
                if (updatedFields.containsKey("displayName")) {
                    replySb.append(String.format("- **Tên hiển thị**: %s\n", updatedFields.get("displayName")));
                }
                if (updatedFields.containsKey("fullName")) {
                    replySb.append(String.format("- **Họ và tên**: %s\n", updatedFields.get("fullName")));
                }
                if (updatedFields.containsKey("bio")) {
                    replySb.append(String.format("- **Tiểu sử**: %s\n", updatedFields.get("bio")));
                }
                if (updatedFields.containsKey("trainingInterests")) {
                    replySb.append(String.format("- **Sở thích tập luyện**: %s\n", updatedFields.get("trainingInterests")));
                }
                replySb.append("\nThông tin đã được đồng bộ tự động trên toàn bộ ứng dụng!");
            } else {
                replySb.append("✅ **Profile Updated Successfully!**\n\nI've saved the following changes to your profile:\n");
                if (updatedFields.containsKey("weightKg")) {
                    replySb.append(String.format("- **Weight**: %.1f kg\n", (Double) updatedFields.get("weightKg")));
                }
                if (updatedFields.containsKey("heightCm")) {
                    replySb.append(String.format("- **Height**: %.1f cm\n", (Double) updatedFields.get("heightCm")));
                }
                if (updatedFields.containsKey("primaryGoals")) {
                    replySb.append(String.format("- **Primary Goals**: %s\n", updatedFields.get("primaryGoals")));
                }
                if (updatedFields.containsKey("experienceLevel")) {
                    replySb.append(String.format("- **Experience Level**: %s\n", updatedFields.get("experienceLevel")));
                }
                if (updatedFields.containsKey("displayName")) {
                    replySb.append(String.format("- **Display Name**: %s\n", updatedFields.get("displayName")));
                }
                if (updatedFields.containsKey("fullName")) {
                    replySb.append(String.format("- **Full Name**: %s\n", updatedFields.get("fullName")));
                }
                if (updatedFields.containsKey("bio")) {
                    replySb.append(String.format("- **Bio**: %s\n", updatedFields.get("bio")));
                }
                if (updatedFields.containsKey("trainingInterests")) {
                    replySb.append(String.format("- **Interests**: %s\n", updatedFields.get("trainingInterests")));
                }
                replySb.append("\nYour profile details have been synchronized across the application!");
            }

            return new ActionResult(replySb.toString(), metadataJson);
        } catch (Exception ex) {
            log.error("Failed to update profile via AI assistant for userId={}: {}", userId, ex.getMessage(), ex);
            String failReply = isVn
                    ? "Tôi chưa thể cập nhật hồ sơ do máy chủ gặp lỗi. Thông tin hiện tại vẫn được giữ nguyên. Vui lòng thử lại sau."
                    : "I couldn't update your profile due to a server error. Your current profile remains unchanged. Please try again later.";
            Map<String, Object> cardData = new LinkedHashMap<>();
            cardData.put("type", "PROFILE_UPDATED");
            cardData.put("actionType", "PROFILE_UPDATE");
            cardData.put("success", false);
            cardData.put("error", ex.getMessage());
            return new ActionResult(failReply, safeToJson(cardData));
        }
    }

    private record WorkoutIntentClassification(
            String intent,
            String toolName,
            boolean readOnly,
            WorkoutDayOfWeek sourceDay,
            WorkoutDayOfWeek targetDay,
            String exerciseName,
            boolean isToday,
            boolean nextSession,
            boolean detailed,
            boolean anotherFollowUp
    ) {
        private WorkoutIntentClassification(
                String intent,
                String toolName,
                boolean readOnly,
                WorkoutDayOfWeek sourceDay,
                WorkoutDayOfWeek targetDay,
                String exerciseName,
                boolean isToday,
                boolean nextSession,
                boolean detailed
        ) {
            this(intent, toolName, readOnly, sourceDay, targetDay, exerciseName, isToday, nextSession, detailed, false);
        }
    }

    private record PendingWorkoutClarification(
            String pendingAction,
            String exerciseName,
            WorkoutDayOfWeek targetDayOfWeek,
            List<Long> selectedExerciseIds,
            List<String> selectedExerciseNames
    ) {}

    private record RecommendedSelectionContext(
            List<Long> selectedIds,
            List<String> selectedNames,
            WorkoutDayOfWeek suggestedDay
    ) {}

    private static boolean isDeterministicWorkoutTool(String toolName) {
        return UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT.equals(toolName)
                || UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT.equals(toolName)
                || UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS.equals(toolName)
                || WorkoutPlanChangePreviewService.TOOL_UPDATE_EXERCISE_IN_WORKOUT.equals(toolName)
                || WorkoutPlanChangePreviewService.TOOL_REORDER_EXERCISES_IN_WORKOUT.equals(toolName)
                || UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN.equals(toolName)
                || UserAiWorkoutToolService.TOOL_ASSIGN_WORKOUT_PLAN.equals(toolName)
                || TOOL_STORE_RECOMMENDATION_SELECTION.equals(toolName)
                || TOOL_APPLY_SELECTED_RECOMMENDATIONS.equals(toolName);
    }

    private ActionResult tryHandlePendingPreviewFollowUp(
            Long userId,
            Long conversationId,
            AiWorkoutPlanChangePreview pendingPreview,
            String userContent,
            boolean isVn
    ) {
        if (isPureConfirmMessage(userContent)) {
            WorkoutPlanChangePreviewService.PreviewActionOutcome outcome =
                    workoutPlanChangePreviewService.confirmPreview(
                            userId, conversationId, pendingPreview.getPreviewId(), isVn, false);
            UserAiWorkoutToolService.WorkoutToolResult res =
                    workoutPlanChangePreviewService.toConversationalToolResult(outcome);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }
        if (isPureCancelMessage(userContent)) {
            WorkoutPlanChangePreviewService.PreviewActionOutcome outcome =
                    workoutPlanChangePreviewService.cancelPreview(
                            userId, conversationId, pendingPreview.getPreviewId(), isVn, false);
            UserAiWorkoutToolService.WorkoutToolResult res =
                    workoutPlanChangePreviewService.toConversationalToolResult(outcome);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }
        if (isPendingPreviewRevisionFollowUp(userContent)) {
            UserAiWorkoutToolService.WorkoutToolResult res =
                    workoutPlanChangePreviewService.revisePendingPreview(
                            userId, conversationId, pendingPreview, userContent, isVn);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }
        return null;
    }

    private static String normalizeShortCommand(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.US)
                .replaceAll("[.!?]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isPureConfirmMessage(String text) {
        String norm = normalizeShortCommand(text);
        return Set.of(
                "confirm", "confirm changes", "confirm it", "confirm please",
                "yes", "yes confirm", "yes, confirm", "yes please", "yes apply", "yes, apply",
                "approve", "approved", "apply", "apply changes", "apply it",
                "go ahead", "looks good", "looks good confirm", "looks good, confirm",
                "do it", "proceed", "ok confirm", "okay confirm",
                "xác nhận", "xác nhận thay đổi", "đồng ý", "áp dụng", "áp dụng thay đổi", "lưu thay đổi"
        ).contains(norm);
    }

    private static boolean isPureCancelMessage(String text) {
        String norm = normalizeShortCommand(text);
        return Set.of(
                "cancel", "cancel changes", "cancel it", "cancel please",
                "no", "no cancel", "no, cancel", "no thanks", "nope",
                "discard", "discard changes", "discard it",
                "never mind", "nevermind", "abort", "stop", "forget it",
                "don't apply", "do not apply", "reject",
                "hủy", "hủy bỏ", "hủy thay đổi", "không", "thôi", "không áp dụng"
        ).contains(norm);
    }

    private boolean isPendingPreviewRevisionFollowUp(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.US).trim();

        // Exclude profile / PR / coach info / read-only view queries
        if (isCoachInfoRequest(lower)
                || lower.contains("profile") || lower.contains("weight") || lower.contains("height")
                || containsWord(lower, "pr") || lower.contains("personal record")
                || containsWord(lower, "show") || containsWord(lower, "view")
                || containsWord(lower, "list") || lower.contains("xem ")
                || lower.contains("what am i training") || lower.contains("today's workout")) {
            return false;
        }
        // Exclude brand-new plan creation / replacement / add / remove commands (unless anaphoric move/day change)
        boolean startsNewDistinctAction = containsWord(lower, "create") || containsWord(lower, "generate")
                || containsWord(lower, "replace") || containsWord(lower, "regenerate")
                || containsWord(lower, "add") || containsWord(lower, "remove") || containsWord(lower, "delete")
                || lower.contains("thêm ") || lower.contains("xóa ") || lower.contains("tạo lịch");
        if (startsNewDistinctAction) {
            return false;
        }

        boolean hasPrescription = WorkoutPlanChangePreviewService.parseExercisePrescription(text).hasAny();
        boolean hasWeekday = UserAiWorkoutToolService.extractWeekday(text) != null;
        boolean hasAnaphoraOrInstead = containsWord(lower, "instead") || containsWord(lower, "actually")
                || containsWord(lower, "it") || containsWord(lower, "that")
                || lower.contains("thay vì") || lower.contains("đổi thành") || lower.contains("sang ");

        // E.g. "Make it 4 sets instead", "Make it 5 sets", "Change rest to 120s", "12 reps instead"
        if (hasPrescription && !containsWord(lower, "move")) {
            return true;
        }
        // E.g. "Move it to Wednesday instead", "Make it 4 sets and move it to Wednesday", "Actually Wednesday"
        if ((hasPrescription || hasWeekday) && hasAnaphoraOrInstead) {
            return true;
        }
        return false;
    }

    private void attachPreviewAssistantMessageIfPresent(Long userId, String metadataJson, Long messageId) {
        if (workoutPlanChangePreviewService == null || metadataJson == null || metadataJson.isBlank() || messageId == null) {
            return;
        }
        if (!metadataJson.contains(WorkoutPlanChangePreviewService.METADATA_TYPE_PREVIEW)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            if (WorkoutPlanChangePreviewService.METADATA_TYPE_PREVIEW.equals(root.path("type").asText())) {
                String previewId = root.path("previewId").asText(null);
                if (previewId == null || previewId.isBlank()) {
                    previewId = root.path("preview").path("previewId").asText(null);
                }
                if (previewId != null && !previewId.isBlank()) {
                    workoutPlanChangePreviewService.attachAssistantMessageId(userId, previewId, messageId);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private WorkoutIntentClassification reconcileWorkoutDecision(
            WorkoutIntentClassification preClassified,
            String userMessage,
            GeminiAssistantOutput assistantOutput,
            List<AiMessage> priorHistory
    ) {
        if (preClassified != null) {
            return preClassified;
        }
        if (assistantOutput == null || assistantOutput.getIntent() == null) {
            return null;
        }
        String intent = assistantOutput.getIntent().trim().toUpperCase(Locale.US);
        GeminiAssistantOutput.WorkoutPlanAction action = assistantOutput.getWorkoutPlanAction();
        String declaredTool = action != null ? action.getToolName() : null;
        boolean detailed = action != null && Boolean.TRUE.equals(action.getDetailed());
        String exerciseName = action != null ? action.getExerciseName() : null;
        UserAiWorkoutToolService.MoveDays moveDays = UserAiWorkoutToolService.extractMoveDays(userMessage);
        WorkoutDayOfWeek day = parseDayOfWeek(action != null ? action.getDayOfWeek() : null, userMessage);
        WorkoutDayOfWeek sourceDay = moveDays.sourceDay() != null
                ? moveDays.sourceDay()
                : parseOptionalEnumDay(action != null ? action.getSourceDayOfWeek() : null);
        WorkoutDayOfWeek moveTargetDay = moveDays.targetDay() != null ? moveDays.targetDay() : day;
        boolean isToday = day == null && action != null && "TODAY".equalsIgnoreCase(action.getDayOfWeek());
        boolean nextSession = day == null && action != null && "NEXT".equalsIgnoreCase(action.getDayOfWeek());

        if ("WORKOUT_PLAN_VIEW".equals(intent)) {
            String tool = detailed
                    ? UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE
                    : UserAiWorkoutToolService.TOOL_GET_MY_WORKOUT_PLAN;
            return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW", tool, true, null, null, null, false, false, detailed);
        }
        if ("WORKOUT_DAY_VIEW".equals(intent)) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, day, null, isToday, nextSession, true);
        }
        if ("EXERCISE_DETAILS_VIEW".equals(intent)) {
            return new WorkoutIntentClassification("EXERCISE_DETAILS_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_EXERCISE_DETAILS, true, null, null, exerciseName, false, false, true);
        }
        if ("LIST_EXERCISES".equals(intent)) {
            return new WorkoutIntentClassification("LIST_EXERCISES",
                    TOOL_LIST_EXERCISES, true, null, null, exerciseName, false, false, true);
        }
        if ("COACH_INFO".equals(intent)) {
            return new WorkoutIntentClassification("COACH_INFO",
                    TOOL_GET_MY_COACH_INFO, true, null, null, null, false, false, true);
        }
        if ("EXERCISE_RECOMMENDATION".equals(intent)) {
            return new WorkoutIntentClassification("EXERCISE_RECOMMENDATION",
                    TOOL_RECOMMEND_EXERCISES, true, null, day, exerciseName, false, false, true);
        }
        if ("WORKOUT_CONSULTATION".equals(intent)) {
            return new WorkoutIntentClassification("WORKOUT_CONSULTATION",
                    TOOL_CONSULT_WORKOUT_PLAN, true, null, day, exerciseName, false, false, true);
        }
        if ("WORKOUT_PLAN_UPDATE".equals(intent)) {
            String tool = (declaredTool != null && !UserAiWorkoutToolService.isReadOnlyTool(declaredTool))
                    ? declaredTool
                    : UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN;
            WorkoutDayOfWeek resolvedTarget = UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS.equals(tool)
                    ? moveTargetDay
                    : day;
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE", tool, false, sourceDay, resolvedTarget, exerciseName, false, false, true);
        }
        if ("WORKOUT_PLAN".equals(intent)) {
            if (declaredTool != null && UserAiWorkoutToolService.isReadOnlyTool(declaredTool)) {
                return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW", declaredTool, true, null, day, null, isToday, nextSession, detailed);
            }
            if (UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN.equals(declaredTool)) {
                return new WorkoutIntentClassification("WORKOUT_PLAN",
                        UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN, false, null, day, null, false, false, false);
            }
            return new WorkoutIntentClassification("WORKOUT_PLAN",
                    UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN, false, null, day, null, false, false, false);
        }
        return null;
    }

    private ActionResult executeClassifiedWorkoutTool(
            Long userId,
            Long conversationId,
            String userContent,
            WorkoutIntentClassification decision,
            GeminiAssistantOutput.WorkoutPlanAction planAction,
            List<AiMessage> priorHistory,
            WorkoutDayOfWeek selectedDayOfWeek,
            String timezone
    ) {
        boolean isVn = isVietnamese(userContent);
        if (decision.readOnly()) {
            try {
                WorkoutDayOfWeek viewDay = decision.targetDay() != null
                        ? decision.targetDay()
                        : ((decision.isToday() || decision.nextSession()) ? null : selectedDayOfWeek);
                UserAiWorkoutToolService.WorkoutToolResult result = switch (decision.toolName()) {
                    case TOOL_GET_MY_COACH_INFO -> executeGetMyCoachInfo(userId, isVn);
                    case TOOL_LIST_EXERCISES -> {
                        if (aiWorkoutRecommendationService != null) {
                            AiWorkoutRecommendationService.RecommendationOutcome listOut =
                                    aiWorkoutRecommendationService.listExercises(
                                            userId, userContent, priorHistory, decision.anotherFollowUp(), isVn);
                            yield new UserAiWorkoutToolService.WorkoutToolResult(
                                    TOOL_LIST_EXERCISES, true, listOut.intent(), true, listOut.reply(), listOut.metadataJson());
                        }
                        yield new UserAiWorkoutToolService.WorkoutToolResult(
                                TOOL_LIST_EXERCISES, true, "LIST_EXERCISES", false,
                                "Exercise directory is currently unavailable.", null);
                    }
                    case TOOL_RECOMMEND_EXERCISES -> {
                        if (aiWorkoutRecommendationService != null) {
                            WorkoutDayOfWeek focusDay = decision.targetDay() != null ? decision.targetDay() : selectedDayOfWeek;
                            boolean planContextRequested = !decision.anotherFollowUp()
                                    && aiWorkoutRecommendationService.planContextExplicitlyRequested(userContent);
                            AiWorkoutRecommendationService.RecommendationOutcome recOut =
                                    aiWorkoutRecommendationService.recommendExercises(
                                            userId, userContent, focusDay, planContextRequested,
                                            priorHistory, decision.anotherFollowUp(), isVn);
                            yield new UserAiWorkoutToolService.WorkoutToolResult(
                                    TOOL_RECOMMEND_EXERCISES, true, recOut.intent(), true, recOut.reply(), recOut.metadataJson());
                        }
                        yield new UserAiWorkoutToolService.WorkoutToolResult(
                                TOOL_RECOMMEND_EXERCISES, true, "EXERCISE_RECOMMENDATION", false,
                                "Recommendation engine is currently unavailable.", null);
                    }
                    case TOOL_CONSULT_WORKOUT_PLAN -> {
                        if (aiWorkoutRecommendationService != null) {
                            WorkoutDayOfWeek focusDay = decision.targetDay() != null ? decision.targetDay() : selectedDayOfWeek;
                            AiWorkoutRecommendationService.ConsultationOutcome conOut =
                                    aiWorkoutRecommendationService.consultWorkoutPlan(userId, userContent, focusDay, isVn);
                            yield new UserAiWorkoutToolService.WorkoutToolResult(
                                    TOOL_CONSULT_WORKOUT_PLAN, true, conOut.intent(), true, conOut.reply(), conOut.metadataJson());
                        }
                        yield new UserAiWorkoutToolService.WorkoutToolResult(
                                TOOL_CONSULT_WORKOUT_PLAN, true, "WORKOUT_CONSULTATION", false,
                                "Workout consultation is currently unavailable.", null);
                    }
                    case UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE ->
                            userAiWorkoutToolService.getWeeklyWorkoutSchedule(userId, decision.detailed(), isVn);
                    case UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS ->
                            userAiWorkoutToolService.getWorkoutDayDetails(
                                    userId, viewDay, decision.isToday(), decision.nextSession(),
                                    timezone, decision.detailed(), isVn);
                    case UserAiWorkoutToolService.TOOL_GET_EXERCISE_DETAILS ->
                            userAiWorkoutToolService.getExerciseDetails(userId, userContent, isVn);
                    default -> userAiWorkoutToolService.getMyWorkoutPlan(userId, decision.detailed(), isVn);
                };
                return new ActionResult(result.reply(), result.metadataJson(), result.intent());
            } catch (Exception ex) {
                log.error("Read-only workout retrieval failed for userId={}, tool={}: {}",
                        userId, decision.toolName(), ex.getMessage(), ex);
                String errReply = isVn
                        ? "Tôi gặp lỗi khi truy xuất lịch tập hiện tại của bạn. Không có thay đổi nào được thực hiện. Vui lòng thử lại sau."
                        : "I encountered an error while retrieving your current workout plan. No changes were made to your plans. Please try again shortly.";
                String errMeta = safeToJson(Map.of(
                        "type", decision.intent(),
                        "toolName", decision.toolName(),
                        "readOnly", true,
                        "error", true
                ));
                return new ActionResult(errReply, errMeta);
            }
        }

        // Write tools: full-plan creation generates an uncommitted AiWorkoutPlanPreviewResponse;
        // all workout plan modifications generate a validated Workout Plan Change Preview and NEVER mutate DB before explicit confirmation.
        String actionExerciseName = decision.exerciseName() != null
                ? decision.exerciseName()
                : (planAction != null ? planAction.getExerciseName() : null);
        if (TOOL_STORE_RECOMMENDATION_SELECTION.equals(decision.toolName())) {
            RecommendedSelectionContext selection = extractRecommendationSelectionFromMessage(userContent, priorHistory);
            if (selection == null || selection.selectedIds().isEmpty()) {
                String fallbackMsg = isVn
                        ? "Bạn muốn chọn bài tập nào từ danh sách gợi ý ở trên và thêm vào thứ mấy?"
                        : "Which recommended exercises would you like to select, and which weekday should I add them to?";
                return new ActionResult(fallbackMsg, null, "EXERCISE_RECOMMENDATION");
            }
            String namesJoined = selection.selectedNames().stream()
                    .map(n -> "**" + n + "**")
                    .collect(Collectors.joining(", "));
            String dayHint = selection.suggestedDay() != null
                    ? formatDayNameEnglish(selection.suggestedDay())
                    : "Wednesday";
            String msg = isVn
                    ? "Tuyệt vời! Tôi đã ghi nhận lựa chọn của bạn: " + namesJoined + ". Bạn muốn thêm các bài tập này vào thứ mấy (ví dụ: " + dayHint + ")?"
                    : "Great picks! I've noted " + namesJoined + ". Which weekday would you like to add them to (for example: " + dayHint + ")?";
            Map<String, Object> clarMeta = new LinkedHashMap<>();
            clarMeta.put("type", WorkoutPlanChangePreviewService.METADATA_TYPE_CLARIFICATION);
            clarMeta.put("pendingAction", "ADD_MULTI");
            clarMeta.put("selectedExerciseIds", selection.selectedIds());
            clarMeta.put("selectedExerciseNames", selection.selectedNames());
            if (selection.suggestedDay() != null) {
                clarMeta.put("targetDayOfWeek", selection.suggestedDay().name());
            }
            clarMeta.put("mutated", false);
            return new ActionResult(msg, safeToJson(clarMeta), "WORKOUT_PLAN_UPDATE");
        }

        if (TOOL_APPLY_SELECTED_RECOMMENDATIONS.equals(decision.toolName()) && workoutPlanChangePreviewService != null) {
            PendingWorkoutClarification pendingClar = findPendingWorkoutClarification(priorHistory);
            RecommendedSelectionContext selection = extractRecommendationSelectionFromMessage(userContent, priorHistory);
            List<Long> targetIds = (pendingClar != null && pendingClar.selectedExerciseIds() != null && !pendingClar.selectedExerciseIds().isEmpty())
                    ? pendingClar.selectedExerciseIds()
                    : (selection != null ? selection.selectedIds() : List.of());
            WorkoutDayOfWeek targetDay = decision.targetDay() != null
                    ? decision.targetDay()
                    : (pendingClar != null && pendingClar.targetDayOfWeek() != null
                        ? pendingClar.targetDayOfWeek()
                        : (selection != null && selection.suggestedDay() != null ? selection.suggestedDay() : selectedDayOfWeek));
            WorkoutPlanChangePreviewService.PrescriptionInput prescription =
                    WorkoutPlanChangePreviewService.parseExercisePrescription(userContent);
            UserAiWorkoutToolService.WorkoutToolResult res = workoutPlanChangePreviewService.proposeAddExercisesBatch(
                    userId, conversationId, targetIds, targetDay, prescription, userContent, isVn);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }

        if (UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN.equals(decision.toolName())) {
            return executeWorkoutPlanAction(userId, userContent, planAction, timezone);
        }
        if (UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN.equals(decision.toolName())) {
            String fitnessLevel = planAction != null ? planAction.getFitnessLevel() : "INTERMEDIATE";
            Integer weeks = planAction != null ? planAction.getDurationWeeks() : 4;
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.createWorkoutPlan(
                    userId, userContent, fitnessLevel, weeks, timezone, isVn, true);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }
        if (UserAiWorkoutToolService.TOOL_ASSIGN_WORKOUT_PLAN.equals(decision.toolName())) {
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.assignWorkoutPlan(
                    userId, null, userContent, isVn);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }

        if (UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN.equals(decision.toolName())
                && decision.targetDay() == null
                && UserAiWorkoutToolService.extractWeekday(userContent) == null
                && selectedDayOfWeek == null
                && !WorkoutPlanChangePreviewService.parseExercisePrescription(userContent).hasAny()) {
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.createWorkoutPlan(
                    userId, userContent, null, 4, timezone, isVn, true);
            return new ActionResult(res.reply(), res.metadataJson(), res.intent());
        }

        if (workoutPlanChangePreviewService != null) {
            UserAiWorkoutToolService.WorkoutToolResult previewRes = workoutPlanChangePreviewService.proposeChanges(
                    userId,
                    conversationId,
                    decision.toolName(),
                    decision.sourceDay(),
                    decision.targetDay(),
                    selectedDayOfWeek,
                    userContent,
                    actionExerciseName,
                    priorHistory,
                    isVn
            );
            return new ActionResult(previewRes.reply(), previewRes.metadataJson(), previewRes.intent());
        }

        return executeWorkoutPlanAction(userId, userContent, planAction, timezone);
    }

    private WorkoutIntentClassification classifyWorkoutIntent(String userMessage, List<AiMessage> priorHistory) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String lower = userMessage.toLowerCase(Locale.US).trim();

        // 0a. Check assigned coach information query BEFORE mentionsProfile ("Show my coach profile" contains "profile")
        if (isCoachInfoRequest(lower)) {
            return new WorkoutIntentClassification("COACH_INFO",
                    TOOL_GET_MY_COACH_INFO, true, null, null, null, false, false, true);
        }

        // Do not hijack explicit PR/profile/progress queries
        boolean mentionsProfile = lower.contains("profile") || lower.contains("hồ sơ")
                || lower.contains("weight") || lower.contains("cân nặng")
                || lower.contains("height") || lower.contains("chiều cao")
                || lower.contains("display name") || lower.contains("tên hiển thị")
                || lower.contains("primary goal") || lower.contains("mục tiêu tập luyện")
                || lower.contains("bio") || lower.contains("tiểu sử");
        if (mentionsProfile) {
            return null;
        }
        boolean mentionsPr = containsWord(lower, "pr") || lower.contains("personal record");
        if (mentionsPr) {
            return null;
        }

        WorkoutDayOfWeek weekday = UserAiWorkoutToolService.extractWeekday(userMessage);
        UserAiWorkoutToolService.MoveDays moveDays = UserAiWorkoutToolService.extractMoveDays(userMessage);
        String prevIntent = findLastAssistantIntent(priorHistory);
        WorkoutDayOfWeek prevDay = findLastAssistantDay(priorHistory);
        boolean hasWorkoutHistoryContext = prevIntent != null && prevIntent.startsWith("WORKOUT_");

        boolean mentionsPlanOrSchedule = lower.contains("workout plan")
                || lower.contains("training plan")
                || lower.contains("workout schedule")
                || lower.contains("weekly schedule")
                || lower.contains("weekly routine")
                || lower.contains("workout routine")
                || lower.contains("training split")
                || lower.contains("current plan")
                || lower.contains("my plan")
                || lower.contains("my schedule")
                || lower.contains("my routine")
                || lower.contains("plan") && (lower.contains("show") || lower.contains("create") || lower.contains("replace") || lower.contains("regenerate") || lower.contains("remove") || lower.contains("assign") || lower.contains("update") || lower.contains("my"))
                || lower.contains("routine") && (lower.contains("show") || lower.contains("create") || lower.contains("generate") || lower.contains("replace") || lower.contains("my"))
                || lower.contains("lịch tập")
                || lower.contains("kế hoạch tập");

        boolean mentionsTodayTraining = lower.contains("training today")
                || lower.contains("workout today")
                || lower.contains("today's workout")
                || lower.contains("what am i training")
                || lower.contains("hôm nay tôi tập gì")
                || lower.contains("hôm nay tập gì")
                || lower.contains("lịch tập hôm nay");

        boolean mentionsNextSession = lower.contains("next training session")
                || lower.contains("next workout")
                || lower.contains("next session")
                || lower.contains("buổi tập tiếp theo");

        boolean mentionsExerciseVerb = lower.contains("exercise") || lower.contains("exercises") || lower.contains("bài tập");
        boolean isViewVerb = containsWord(lower, "show") || containsWord(lower, "view")
                || containsWord(lower, "list") || containsWord(lower, "explain")
                || lower.contains("xem ");

        // Pending clarification follow-up (e.g. Turn 1: "Add Front Lever Raises" or "I like Squat and RDL", Turn 2: "Tuesday" / "Add them to Wednesday")
        boolean isAnotherRequest = isAnotherExerciseRequest(lower, priorHistory);
        PendingWorkoutClarification pendingClarification = findPendingWorkoutClarification(priorHistory);
        if (pendingClarification != null && weekday != null && !isViewVerb
                && !isAnotherRequest && !isListExercisesRequest(lower, weekday, mentionsPlanOrSchedule, mentionsTodayTraining, mentionsNextSession)
                && !isExerciseRecommendationRequest(lower) && !isWorkoutConsultationRequest(lower)) {
            if ("ADD_MULTI".equalsIgnoreCase(pendingClarification.pendingAction())
                    && pendingClarification.selectedExerciseIds() != null
                    && !pendingClarification.selectedExerciseIds().isEmpty()
                    && (isAnaphoraSelection(lower) || !mentionsAnyPublishedExercise(lower))) {
                return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                        TOOL_APPLY_SELECTED_RECOMMENDATIONS, false, null, weekday, null, false, false, true);
            }
            if ("ADD".equalsIgnoreCase(pendingClarification.pendingAction())
                    && !containsWord(lower, "remove") && !containsWord(lower, "delete")) {
                return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                        UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, false, null, weekday,
                        pendingClarification.exerciseName(), false, false, true);
            }
            if ("REMOVE".equalsIgnoreCase(pendingClarification.pendingAction())
                    && !containsWord(lower, "add")) {
                return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                        UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, null, weekday,
                        pendingClarification.exerciseName(), false, false, true);
            }
            if ("MOVE".equalsIgnoreCase(pendingClarification.pendingAction())) {
                WorkoutDayOfWeek clarifiedSource = pendingClarification.targetDayOfWeek() != null ? weekday : null;
                WorkoutDayOfWeek clarifiedTarget = pendingClarification.targetDayOfWeek() != null
                        ? pendingClarification.targetDayOfWeek() : weekday;
                return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                        UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS, false, clarifiedSource, clarifiedTarget,
                        pendingClarification.exerciseName(), false, false, true);
            }
            if ("UPDATE".equalsIgnoreCase(pendingClarification.pendingAction())) {
                return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                        WorkoutPlanChangePreviewService.TOOL_UPDATE_EXERCISE_IN_WORKOUT, false, null, weekday,
                        pendingClarification.exerciseName(), false, false, true);
            }
        }

        // 0. Check multi-turn selection follow-up from a recent EXERCISE_RECOMMENDATIONS card
        boolean hasOtherWriteVerb = containsWord(lower, "remove") || containsWord(lower, "delete")
                || containsWord(lower, "move") || containsWord(lower, "update")
                || containsWord(lower, "change") || containsWord(lower, "modify")
                || containsWord(lower, "reorder") || containsWord(lower, "replace");
        if (!isAnotherRequest
                && !isListExercisesRequest(lower, weekday, mentionsPlanOrSchedule, mentionsTodayTraining, mentionsNextSession)
                && !isExerciseRecommendationRequest(lower) && !isWorkoutConsultationRequest(lower)
                && !isViewVerb && !hasOtherWriteVerb) {
            RecommendedSelectionContext recFollowUp = extractRecommendationSelectionFromMessage(userMessage, priorHistory);
            if (recFollowUp != null && !recFollowUp.selectedIds().isEmpty()) {
                if (weekday != null) {
                    return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                            TOOL_APPLY_SELECTED_RECOMMENDATIONS, false, null, weekday, null, false, false, true);
                }
                return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                        TOOL_STORE_RECOMMENDATION_SELECTION, false, null, recFollowUp.suggestedDay(), null, false, false, true);
            }
        }

        // 0b. Check "Another exercise" follow-up, LIST_EXERCISES, EXERCISE_RECOMMENDATION & WORKOUT_CONSULTATION BEFORE WRITE/VIEW verbs
        if (isAnotherRequest) {
            String topicIntent = resolveAnotherFollowUpIntent(lower, priorHistory);
            String tool = "LIST_EXERCISES".equals(topicIntent) ? TOOL_LIST_EXERCISES : TOOL_RECOMMEND_EXERCISES;
            return new WorkoutIntentClassification(topicIntent,
                    tool, true, null, null, null, false, false, true, true);
        }
        if (isListExercisesRequest(lower, weekday, mentionsPlanOrSchedule, mentionsTodayTraining, mentionsNextSession)) {
            return new WorkoutIntentClassification("LIST_EXERCISES",
                    TOOL_LIST_EXERCISES, true, null, null, null, false, false, true);
        }
        if (isExerciseRecommendationRequest(lower)) {
            return new WorkoutIntentClassification("EXERCISE_RECOMMENDATION",
                    TOOL_RECOMMEND_EXERCISES, true, null, weekday, null, false, false, true);
        }
        if (isWorkoutConsultationRequest(lower)) {
            return new WorkoutIntentClassification("WORKOUT_CONSULTATION",
                    TOOL_CONSULT_WORKOUT_PLAN, true, null, weekday, null, false, false, true);
        }

        // 1. Check EXPLICIT WRITE intents first
        boolean hasReplaceVerb = lower.contains("replace") || lower.contains("regenerate")
                || lower.contains("redo") || lower.contains("rebuild")
                || lower.contains("thay thế") || lower.contains("tạo lại");
        if (hasReplaceVerb && (mentionsPlanOrSchedule || hasWorkoutHistoryContext || containsWord(lower, "it"))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN",
                    UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN, false, null, weekday, null, false, false, false);
        }

        boolean hasCreateVerb = lower.contains("create") || lower.contains("generate")
                || lower.contains("make me") || lower.contains("make a")
                || lower.contains("build me") || lower.contains("build a")
                || lower.contains("design a") || lower.contains("suggest a new")
                || lower.contains("tạo lịch") || lower.contains("lập lịch") || lower.contains("tạo một lịch");
        if (hasCreateVerb && (mentionsPlanOrSchedule || lower.contains("split") || lower.contains("schedule") || lower.contains("workout"))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN",
                    UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN, false, null, weekday, null, false, false, false);
        }

        boolean hasReorderCue = containsWord(lower, "reorder")
                || lower.contains("sắp xếp lại")
                || (containsWord(lower, "swap") && (lower.contains("order") || mentionsAnyPublishedExercise(lower)))
                || ((containsWord(lower, "put") || containsWord(lower, "move") || containsWord(lower, "place"))
                    && (lower.contains(" first") || lower.contains(" last")
                        || lower.contains(" to the top") || lower.contains(" to the bottom")
                        || lower.contains(" before ") || lower.contains(" after ")
                        || lower.contains("position ") || lower.contains("đầu tiên") || lower.contains("cuối cùng")));
        if (hasReorderCue && (weekday != null || mentionsExerciseVerb || hasWorkoutHistoryContext || mentionsAnyPublishedExercise(lower))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    WorkoutPlanChangePreviewService.TOOL_REORDER_EXERCISES_IN_WORKOUT, false, null, weekday, null, false, false, true);
        }

        boolean hasMoveVerb = containsWord(lower, "move") || containsWord(lower, "transfer")
                || containsWord(lower, "shift") || containsWord(lower, "relocate")
                || lower.contains("chuyển ") || lower.contains("dời ")
                || ((containsWord(lower, "switch") || containsWord(lower, "swap"))
                    && moveDays.sourceDay() != null && moveDays.targetDay() != null);
        if (hasMoveVerb && (moveDays.sourceDay() != null || moveDays.targetDay() != null || weekday != null
                || mentionsExerciseVerb || hasWorkoutHistoryContext || mentionsAnyPublishedExercise(lower))) {
            WorkoutDayOfWeek resolvedTarget = moveDays.targetDay() != null ? moveDays.targetDay() : weekday;
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_MOVE_EXERCISE_BETWEEN_DAYS, false,
                    moveDays.sourceDay(), resolvedTarget, null, false, false, true);
        }

        boolean hasAddVerb = containsWord(lower, "add") || containsWord(lower, "insert") || lower.contains("thêm ");
        if (hasAddVerb && (mentionsExerciseVerb || mentionsPlanOrSchedule || weekday != null
                || hasWorkoutHistoryContext || mentionsAnyPublishedExercise(lower))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, false, null, weekday, null, false, false, true);
        }

        boolean hasRemoveVerb = containsWord(lower, "remove") || containsWord(lower, "delete")
                || containsWord(lower, "drop") || lower.contains("xóa ") || lower.contains("bỏ ");
        if (hasRemoveVerb && (mentionsExerciseVerb || mentionsPlanOrSchedule || weekday != null
                || hasWorkoutHistoryContext || mentionsAnyPublishedExercise(lower))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, null, weekday, null, false, false, true);
        }

        boolean hasPrescriptionSpec = WorkoutPlanChangePreviewService.parseExercisePrescription(userMessage).hasAny();
        boolean hasUpdateExerciseVerb = containsWord(lower, "update") || containsWord(lower, "change")
                || containsWord(lower, "modify") || containsWord(lower, "set")
                || containsWord(lower, "make") || containsWord(lower, "increase")
                || containsWord(lower, "decrease") || containsWord(lower, "adjust")
                || containsWord(lower, "edit")
                || lower.contains("đổi ") || lower.contains("sửa ") || lower.contains("chỉnh ") || lower.contains("cập nhật ");
        if (hasUpdateExerciseVerb && hasPrescriptionSpec
                && (mentionsAnyPublishedExercise(lower) || weekday != null || hasWorkoutHistoryContext)) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    WorkoutPlanChangePreviewService.TOOL_UPDATE_EXERCISE_IN_WORKOUT, false, null, weekday, null, false, false, true);
        }

        boolean hasModifyVerb = containsWord(lower, "change") || containsWord(lower, "modify")
                || containsWord(lower, "update") || containsWord(lower, "switch")
                || containsWord(lower, "swap") || containsWord(lower, "edit")
                || lower.contains("đổi ") || lower.contains("sửa ") || lower.contains("chỉnh ");
        if (hasModifyVerb && (mentionsPlanOrSchedule || weekday != null || lower.contains("workout"))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN, false, null, weekday, null, false, false, true);
        }

        boolean hasAssignVerb = (containsWord(lower, "assign") || containsWord(lower, "activate") || lower.contains("kích hoạt"))
                && mentionsPlanOrSchedule;
        if (hasAssignVerb) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_ASSIGN_WORKOUT_PLAN, false, null, null, null, false, false, false);
        }

        // 2. Check EXPLICIT READ-ONLY intents
        boolean asksDetail = lower.contains("detail") || lower.contains("details")
                || lower.contains("full") || lower.contains("complete")
                || lower.contains("list all") || lower.contains("explain")
                || lower.contains("chi tiết") || lower.contains("đầy đủ");

        if (mentionsTodayTraining) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, null, null, true, false, true);
        }

        if (mentionsNextSession) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, null, null, false, true, true);
        }

        if (weekday != null) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, weekday, null, false, false, true);
        }

        if (mentionsPlanOrSchedule) {
            String tool = (asksDetail || lower.contains("schedule") || lower.contains("weekly")
                    || lower.contains("lịch") || lower.contains("list all"))
                    ? UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE
                    : UserAiWorkoutToolService.TOOL_GET_MY_WORKOUT_PLAN;
            return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW", tool, true, null, null, null, false, false,
                    asksDetail || lower.contains("list all") || lower.contains("weekly") || lower.contains("schedule"));
        }

        if (lower.contains("exercise details") || lower.contains("details for ")
                || lower.contains("show exercise ") || lower.contains("chi tiết bài tập")) {
            return new WorkoutIntentClassification("EXERCISE_DETAILS_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_EXERCISE_DETAILS, true, null, null, null, false, false, true);
        }

        // 3. Follow-up context resolution (e.g., "Show it in more detail", "Show it in detail")
        boolean isDetailFollowUp = (asksDetail || lower.contains("tell me more") || lower.contains("show it") || lower.contains("xem thêm"))
                && !lower.contains("progress") && !lower.contains("pr ");
        if (hasWorkoutHistoryContext && isDetailFollowUp) {
            if ("WORKOUT_DAY_VIEW".equals(prevIntent) && prevDay != null) {
                return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                        UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, prevDay, null, false, false, true);
            }
            return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE, true, null, null, null, false, false, true);
        }

        return null;
    }

    private boolean isCoachInfoRequest(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        if (containsWord(lower, "update") || containsWord(lower, "change") || containsWord(lower, "edit")) {
            return false;
        }
        return lower.contains("who is my coach")
                || lower.contains("who's my coach")
                || lower.contains("whos my coach")
                || lower.contains("my coach")
                || lower.contains("assigned coach")
                || lower.contains("coach information")
                || lower.contains("coach info")
                || lower.contains("coach profile")
                || lower.contains("coach's profile")
                || lower.contains("coach's bio")
                || lower.contains("coach details")
                || lower.contains("huấn luyện viên của tôi")
                || lower.contains("hlv của tôi")
                || lower.contains("ai là huấn luyện viên")
                || lower.contains("thông tin huấn luyện viên")
                || lower.contains("thông tin hlv")
                || lower.contains("hồ sơ huấn luyện viên");
    }

    private boolean isAnotherExerciseRequest(String lower, List<AiMessage> priorHistory) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        boolean mentionsPlanOrSchedule = lower.contains("workout plan") || lower.contains("training plan")
                || lower.contains("schedule") || lower.contains("routine") || lower.contains("split");
        if (mentionsPlanOrSchedule) {
            return false;
        }
        boolean mentionsExerciseNoun = lower.contains("exercise") || lower.contains("exercises")
                || lower.contains("movement") || lower.contains("movements")
                || lower.contains("bài tập");
        boolean hasExplicitAnotherCue = ((containsWord(lower, "another") || containsWord(lower, "different") || containsWord(lower, "other"))
                && (mentionsExerciseNoun || containsWord(lower, "one")))
                || lower.contains("more exercises")
                || lower.contains("other exercises")
                || lower.contains("different exercises")
                || lower.contains("bài tập khác")
                || lower.contains("bài khác");
        if (hasExplicitAnotherCue) {
            return true;
        }

        boolean hasRecentExerciseContext = hasActiveExerciseListOrRecHistory(priorHistory);
        if (!hasRecentExerciseContext) {
            return false;
        }
        return lower.contains("anything else")
                || lower.contains("what else")
                || lower.contains("something else")
                || lower.contains("any other")
                || containsWord(lower, "another")
                || containsWord(lower, "more")
                || lower.contains("suggest another")
                || lower.contains("recommend another")
                || lower.contains("give me another")
                || lower.contains("thêm nữa")
                || lower.contains("còn bài nào")
                || lower.contains("còn nữa không")
                || lower.contains("gợi ý thêm");
    }

    private boolean hasActiveExerciseListOrRecHistory(List<AiMessage> priorHistory) {
        if (priorHistory == null || priorHistory.isEmpty()) {
            return false;
        }
        for (int i = priorHistory.size() - 1; i >= 0; i--) {
            AiMessage m = priorHistory.get(i);
            if (m == null || !"assistant".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            String intent = m.getIntent() != null ? m.getIntent().trim().toUpperCase(Locale.US) : "";
            return "LIST_EXERCISES".equals(intent) || "EXERCISE_RECOMMENDATION".equals(intent);
        }
        return false;
    }

    private String resolveAnotherFollowUpIntent(String lower, List<AiMessage> priorHistory) {
        if (containsWord(lower, "recommend") || containsWord(lower, "suggest") || lower.contains("gợi ý")) {
            return "EXERCISE_RECOMMENDATION";
        }
        if (priorHistory != null) {
            for (int i = priorHistory.size() - 1; i >= 0; i--) {
                AiMessage m = priorHistory.get(i);
                if (m != null && "assistant".equalsIgnoreCase(m.getRole()) && m.getIntent() != null) {
                    String intent = m.getIntent().trim().toUpperCase(Locale.US);
                    if ("LIST_EXERCISES".equals(intent) || "EXERCISE_RECOMMENDATION".equals(intent)) {
                        return intent;
                    }
                }
            }
        }
        return "LIST_EXERCISES";
    }

    private boolean isListExercisesRequest(
            String lower,
            WorkoutDayOfWeek weekday,
            boolean mentionsPlanOrSchedule,
            boolean mentionsTodayTraining,
            boolean mentionsNextSession
    ) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        if (mentionsPlanOrSchedule || mentionsTodayTraining || mentionsNextSession || weekday != null) {
            return false;
        }
        if (lower.contains("exercise details") || lower.contains("details for ")
                || lower.contains("show exercise ") || lower.contains("chi tiết bài tập")) {
            return false;
        }
        // Keep explicit plan/workout creation or modification out of LIST_EXERCISES
        boolean hasPlanOrWriteCue = containsWord(lower, "add") || containsWord(lower, "remove")
                || containsWord(lower, "delete") || containsWord(lower, "move")
                || containsWord(lower, "replace") || containsWord(lower, "reorder")
                || containsWord(lower, "update") || containsWord(lower, "change")
                || lower.contains("should i add") || lower.contains("missing in my")
                || lower.contains("in my workout") || lower.contains("in my plan") || lower.contains("on my schedule")
                || lower.contains("thêm ") || lower.contains("xóa ") || lower.contains("bỏ ");
        if (hasPlanOrWriteCue) {
            return false;
        }
        boolean hasRecommendVerb = containsWord(lower, "recommend") || containsWord(lower, "recommendation")
                || containsWord(lower, "recommendations") || containsWord(lower, "suggest")
                || containsWord(lower, "suggestion") || containsWord(lower, "suggestions")
                || lower.contains("gợi ý") || lower.contains("đề xuất");
        if (hasRecommendVerb) {
            return false;
        }
        boolean mentionsPluralExerciseNoun = lower.contains("exercises") || lower.contains("movements") || lower.contains("bài tập");
        boolean mentionsAnyExerciseNoun = mentionsPluralExerciseNoun || lower.contains("exercise");
        if (!mentionsAnyExerciseNoun) {
            return false;
        }
        boolean hasListVerb = containsWord(lower, "list") || lower.contains("show me")
                || lower.contains("what exercises") || lower.contains("which exercises")
                || lower.contains("what are some") || lower.contains("what are good")
                || lower.contains("any exercises") || lower.contains("give me a list")
                || lower.contains("liệt kê");
        if (hasListVerb) {
            return true;
        }
        return mentionsPluralExerciseNoun
                && (lower.contains("for ") || (aiWorkoutRecommendationService != null
                        && aiWorkoutRecommendationService.hasExplicitExerciseCriteria(lower)))
                && !lower.contains("give me some") && !lower.contains("give me a few");
    }

    private UserAiWorkoutToolService.WorkoutToolResult executeGetMyCoachInfo(Long userId, boolean isVn) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Optional<CoachClientRelationship> activeRelOpt = coachClientRelationshipRepository != null
                ? coachClientRelationshipRepository.findFirstByClientIdAndStatusOrderByUpdatedAtDesc(
                        userId, CoachClientRelationshipStatus.ACTIVE)
                : Optional.empty();
        if (activeRelOpt.isEmpty() || activeRelOpt.get().getCoach() == null) {
            String msg = "You don't currently have an assigned coach.";
            return new UserAiWorkoutToolService.WorkoutToolResult(
                    TOOL_GET_MY_COACH_INFO, true, "COACH_INFO", true, msg, null);
        }

        User coach = activeRelOpt.get().getCoach();
        CoachProfile profile = coachProfileRepository != null
                ? coachProfileRepository.findByUserId(coach.getId()).orElse(null)
                : null;

        String displayName = (profile != null && isNonBlankText(profile.getDisplayName()))
                ? profile.getDisplayName().trim()
                : (isNonBlankText(coach.getFullName()) ? coach.getFullName().trim() : null);
        String fullName = isNonBlankText(coach.getFullName()) ? coach.getFullName().trim() : null;
        String avatarUrl = (profile != null && isNonBlankText(profile.getAvatarUrl()))
                ? profile.getAvatarUrl().trim()
                : (isNonBlankText(coach.getAvatarUrl()) ? coach.getAvatarUrl().trim() : null);

        StringBuilder sb = new StringBuilder();
        sb.append(isVn ? "**Thông Tin Huấn Luyện Viên**\n\n" : "**Assigned Coach Profile**\n\n");
        if (displayName != null) {
            sb.append("- **Name**: ").append(displayName).append("\n");
        }
        if (fullName != null && (displayName == null || !fullName.equalsIgnoreCase(displayName))) {
            sb.append("- **Full Name**: ").append(fullName).append("\n");
        }
        if (avatarUrl != null) {
            sb.append("- **Profile Picture**: ").append(avatarUrl).append("\n");
        }
        if (profile != null && Boolean.TRUE.equals(profile.getIsVerified())) {
            sb.append("- **Status**: Verified Coach\n");
        }
        if (profile != null && isNonBlankText(profile.getBio())) {
            sb.append("- **Bio**: ").append(profile.getBio().trim()).append("\n");
        }
        if (profile != null && isNonBlankText(profile.getSpecialties())) {
            sb.append("- **Specialties**: ").append(profile.getSpecialties().trim()).append("\n");
        }
        if (profile != null && profile.getYearsExperience() != null) {
            sb.append("- **Experience**: ").append(profile.getYearsExperience()).append(" years\n");
        }
        if (profile != null && isNonBlankText(profile.getCertifications())) {
            sb.append("- **Certifications**: ").append(profile.getCertifications().trim()).append("\n");
        }
        if (profile != null && isNonBlankText(profile.getTrainingPhilosophy())) {
            sb.append("- **Coaching Philosophy**: ").append(profile.getTrainingPhilosophy().trim()).append("\n");
        }
        if (profile != null && isNonBlankText(profile.getCoachingDescription())) {
            sb.append("- **Coaching Description**: ").append(profile.getCoachingDescription().trim()).append("\n");
        }
        if (profile != null && isNonBlankText(profile.getLanguages())) {
            sb.append("- **Languages**: ").append(profile.getLanguages().trim()).append("\n");
        }
        if (profile != null && isNonBlankText(profile.getSocialLinks())) {
            sb.append("- **Social Links**: ").append(profile.getSocialLinks().trim()).append("\n");
        }

        return new UserAiWorkoutToolService.WorkoutToolResult(
                TOOL_GET_MY_COACH_INFO, true, "COACH_INFO", true, sb.toString().trim(), null);
    }

    private static boolean isNonBlankText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isExerciseRecommendationRequest(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        // Full weekly plan generation queries belong to WORKOUT_PLAN
        boolean isWholePlanCreation = (lower.contains("workout plan") || lower.contains("training plan")
                || lower.contains("workout split") || lower.contains("new split") || lower.contains("new plan"))
                && !lower.contains("exercise") && !lower.contains("exercises") && !lower.contains("missing");
        if (isWholePlanCreation) {
            return false;
        }

        boolean mentionsExerciseNoun = lower.contains("exercise") || lower.contains("exercises")
                || lower.contains("movements") || lower.contains("bài tập");
        boolean hasRecommendCue = containsWord(lower, "recommend") || containsWord(lower, "recommendation")
                || containsWord(lower, "recommendations") || containsWord(lower, "suggest")
                || containsWord(lower, "suggestion") || containsWord(lower, "suggestions")
                || lower.contains("gợi ý") || lower.contains("đề xuất")
                || lower.contains("nên tập bài") || lower.contains("nên thêm bài");
        if (hasRecommendCue && (mentionsExerciseNoun || !lower.contains("plan"))) {
            return true;
        }
        if ((lower.contains("what exercises") || lower.contains("which exercises") || lower.contains("any exercises")
                || lower.contains("give me some") || lower.contains("give me a few"))
                && mentionsExerciseNoun) {
            return true;
        }
        if ((lower.contains("missing in my") || lower.contains("what am i missing") || lower.contains("what's missing"))
                && (mentionsExerciseNoun || lower.contains("routine") || lower.contains("plan") || lower.contains("split"))) {
            return true;
        }
        return lower.contains("exercises to improve") || lower.contains("exercises to help")
                || lower.contains("exercises for my");
    }

    private boolean isWorkoutConsultationRequest(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        boolean hasExplicitPlanBalance = lower.contains("balanced")
                || lower.contains("imbalance")
                || (containsWord(lower, "balance") && (lower.contains("routine") || lower.contains("plan")
                    || lower.contains("split") || lower.contains("muscle") || lower.contains("workout")
                    || lower.contains("push") || lower.contains("pull") || lower.contains("leg")));
        boolean isTrainingAdequacyQuestion = !lower.contains("what am i training")
                && (lower.contains("am i training") || lower.contains("am i working") || lower.contains("am i hitting"))
                && (lower.contains("enough") || lower.contains("too much") || lower.contains("too little") || lower.contains("properly"));
        boolean hasConsultationCue = hasExplicitPlanBalance
                || isTrainingAdequacyQuestion
                || lower.contains("enough recovery")
                || lower.contains("enough rest") || lower.contains("too high")
                || lower.contains("too much volume") || lower.contains("overtraining")
                || lower.contains("overloaded") || lower.contains("undertrained")
                || lower.contains("does my weekly")
                || lower.contains("does my routine") || lower.contains("evaluate my")
                || lower.contains("critique my") || lower.contains("review my")
                || lower.contains("có cân bằng") || lower.contains("đủ phục hồi")
                || lower.contains("quá tải") || lower.contains("đánh giá lịch");
        if (hasConsultationCue) {
            return true;
        }
        return (lower.contains("is my ") || lower.contains("are my "))
                && (lower.contains("enough") || lower.contains("balanced") || lower.contains("too much") || lower.contains("too high"));
    }

    private static boolean isAnaphoraSelection(String lower) {
        if (lower == null) return false;
        return containsWord(lower, "them") || containsWord(lower, "those") || containsWord(lower, "these")
                || containsWord(lower, "both") || containsWord(lower, "all")
                || lower.contains("first two") || lower.contains("first three") || lower.contains("first one")
                || lower.contains("top 2") || lower.contains("top 3") || lower.contains("selected");
    }

    private RecommendedSelectionContext extractRecommendationSelectionFromMessage(
            String userMessage,
            List<AiMessage> priorHistory
    ) {
        if (userMessage == null || userMessage.isBlank() || priorHistory == null || priorHistory.isEmpty()) {
            return null;
        }
        List<Long> recIds = new ArrayList<>();
        List<String> recNames = new ArrayList<>();
        WorkoutDayOfWeek suggestedDay = null;

        for (int i = priorHistory.size() - 1; i >= 0; i--) {
            AiMessage m = priorHistory.get(i);
            if (!"assistant".equalsIgnoreCase(m.getRole()) || m.getMetadata() == null) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(m.getMetadata());
                if ("EXERCISE_RECOMMENDATIONS".equals(root.path("type").asText())) {
                    if (root.hasNonNull("suggestedDayOfWeek")) {
                        suggestedDay = parseOptionalEnumDay(root.get("suggestedDayOfWeek").asText());
                    }
                    if (root.has("recommendations") && root.get("recommendations").isArray()) {
                        for (JsonNode item : root.get("recommendations")) {
                            if (item.hasNonNull("exerciseId") && item.hasNonNull("name")) {
                                recIds.add(item.get("exerciseId").asLong());
                                recNames.add(item.get("name").asText());
                            }
                        }
                    }
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (recIds.isEmpty()) {
            return null;
        }

        String lower = userMessage.toLowerCase(Locale.US);
        List<Long> matchedIds = new ArrayList<>();
        List<String> matchedNames = new ArrayList<>();

        for (int i = 0; i < recIds.size(); i++) {
            String name = recNames.get(i);
            if (name != null && !name.isBlank() && lower.contains(name.toLowerCase(Locale.US))) {
                matchedIds.add(recIds.get(i));
                matchedNames.add(name);
            }
        }

        if (matchedIds.isEmpty() && isAnaphoraSelection(lower)) {
            int takeCount;
            if (lower.contains("first two") || lower.contains("top 2") || containsWord(lower, "both")) {
                takeCount = Math.min(2, recIds.size());
            } else if (lower.contains("first three") || lower.contains("top 3")) {
                takeCount = Math.min(3, recIds.size());
            } else if (lower.contains("first one") || lower.contains("the first")) {
                takeCount = 1;
            } else {
                // "them", "those", "all" -> default up to 2 most recent or pending CLARIFICATION selection if present
                PendingWorkoutClarification clar = findPendingWorkoutClarification(priorHistory);
                if (clar != null && clar.selectedExerciseIds() != null && !clar.selectedExerciseIds().isEmpty()) {
                    return new RecommendedSelectionContext(clar.selectedExerciseIds(), clar.selectedExerciseNames(),
                            clar.targetDayOfWeek() != null ? clar.targetDayOfWeek() : suggestedDay);
                }
                takeCount = recIds.size();
            }
            for (int i = 0; i < takeCount; i++) {
                matchedIds.add(recIds.get(i));
                matchedNames.add(recNames.get(i));
            }
        }

        return matchedIds.isEmpty() ? null : new RecommendedSelectionContext(matchedIds, matchedNames, suggestedDay);
    }

    private static String formatDayNameEnglish(WorkoutDayOfWeek dow) {
        if (dow == null) return "Wednesday";
        String n = dow.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.US);
    }

    private static boolean containsWord(String lowerText, String word) {
        if (lowerText == null || word == null || word.isBlank()) {
            return false;
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])")
                .matcher(lowerText)
                .find();
    }

    private boolean mentionsAnyPublishedExercise(String lowerText) {
        if (lowerText == null || lowerText.isBlank()) {
            return false;
        }
        List<Exercise> exercises = exerciseRepository.findAll();
        if (exercises == null || exercises.isEmpty()) {
            return false;
        }
        return exercises.stream()
                .filter(e -> e != null && e.getName() != null && !e.getName().isBlank())
                .anyMatch(e -> lowerText.contains(e.getName().toLowerCase(Locale.US)));
    }

    private PendingWorkoutClarification findPendingWorkoutClarification(List<AiMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage m = history.get(i);
            if (!"assistant".equalsIgnoreCase(m.getRole())) {
                continue;
            }
            if (m.getMetadata() == null || m.getMetadata().isBlank()) {
                return null;
            }
            try {
                JsonNode root = objectMapper.readTree(m.getMetadata());
                    if ("WORKOUT_PLAN_CLARIFICATION".equals(root.path("type").asText())) {
                        String action = root.path("pendingAction").asText(null);
                        String exName = root.hasNonNull("exerciseName") ? root.get("exerciseName").asText() : null;
                        WorkoutDayOfWeek targetDay = root.hasNonNull("targetDayOfWeek")
                                ? parseOptionalEnumDay(root.get("targetDayOfWeek").asText())
                                : null;
                        List<Long> selectedIds = new ArrayList<>();
                        if (root.has("selectedExerciseIds") && root.get("selectedExerciseIds").isArray()) {
                            for (JsonNode n : root.get("selectedExerciseIds")) {
                                if (n.canConvertToLong()) selectedIds.add(n.asLong());
                            }
                        }
                        List<String> selectedNames = new ArrayList<>();
                        if (root.has("selectedExerciseNames") && root.get("selectedExerciseNames").isArray()) {
                            for (JsonNode n : root.get("selectedExerciseNames")) {
                                if (n.isTextual() && !n.asText().isBlank()) selectedNames.add(n.asText());
                            }
                        }
                        if (action != null && !action.isBlank()) {
                            return new PendingWorkoutClarification(action, exName, targetDay, selectedIds, selectedNames);
                        }
                    }
            } catch (Exception ignored) {
            }
            return null;
        }
        return null;
    }

    private String findLastAssistantIntent(List<AiMessage> history) {
        if (history == null) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage m = history.get(i);
            if ("assistant".equalsIgnoreCase(m.getRole()) && m.getIntent() != null) {
                return m.getIntent().trim().toUpperCase(Locale.US);
            }
        }
        return null;
    }

    private WorkoutDayOfWeek findLastAssistantDay(List<AiMessage> history) {
        if (history == null) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage m = history.get(i);
            if ("assistant".equalsIgnoreCase(m.getRole()) && m.getMetadata() != null) {
                try {
                    JsonNode root = objectMapper.readTree(m.getMetadata());
                    if (root.hasNonNull("dayOfWeek")) {
                        return WorkoutDayOfWeek.valueOf(root.get("dayOfWeek").asText().trim().toUpperCase(Locale.US));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private WorkoutDayOfWeek parseDayOfWeek(String rawDay, String userMessage) {
        // Explicit weekday in the user's message ALWAYS takes priority over inferred or model-supplied day.
        WorkoutDayOfWeek explicitFromMessage = UserAiWorkoutToolService.extractWeekday(userMessage);
        if (explicitFromMessage != null) {
            return explicitFromMessage;
        }
        return parseOptionalEnumDay(rawDay);
    }

    private WorkoutDayOfWeek parseOptionalEnumDay(String rawDay) {
        if (rawDay != null && !rawDay.isBlank()) {
            try {
                return WorkoutDayOfWeek.valueOf(rawDay.trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private GeminiAssistantOutput fallbackIntentEngine(
            String userMessage,
            List<PersonalRecord> recentPrs,
            List<AiMessage> priorHistory
    ) {
        String lower = userMessage.toLowerCase();

        // 0. Coach Information Check BEFORE profile update check (e.g. "Show my coach profile" contains "profile")
        if (isCoachInfoRequest(lower)) {
            return GeminiAssistantOutput.builder()
                    .intent("COACH_INFO")
                    .reply(null)
                    .workoutPlanAction(GeminiAssistantOutput.WorkoutPlanAction.builder()
                            .prompt(userMessage)
                            .toolName(TOOL_GET_MY_COACH_INFO)
                            .build())
                    .build();
        }

        // 1. Profile Update Pattern Checks (Vietnamese & English)
        Double extractedWeight = extractWeight(userMessage);
        Double extractedHeight = extractHeight(userMessage);
        String extractedGoal = extractGoal(userMessage);
        String extractedExp = extractExperienceLevel(userMessage);
        String extractedDisplayName = extractDisplayName(userMessage);
        String extractedBio = extractBio(userMessage);

        boolean isProfileUpdateKeyword = lower.contains("cập nhật hồ sơ") || lower.contains("update profile")
                || lower.contains("đổi hồ sơ") || lower.contains("change profile")
                || lower.contains("cân nặng") || lower.contains("chiều cao")
                || lower.contains("weight") || lower.contains("height")
                || lower.contains("mục tiêu tập luyện") || lower.contains("đổi mục tiêu");

        if (extractedWeight != null || extractedHeight != null || extractedGoal != null
                || extractedExp != null || extractedDisplayName != null || extractedBio != null
                || isProfileUpdateKeyword) {

            boolean isAmbiguous = (extractedWeight == null && extractedHeight == null
                    && extractedGoal == null && extractedExp == null
                    && extractedDisplayName == null && extractedBio == null);

            return GeminiAssistantOutput.builder()
                    .intent("PROFILE_UPDATE")
                    .reply(isAmbiguous ? (isVietnamese(userMessage)
                            ? "Bạn muốn cập nhật thông tin nào trong hồ sơ cá nhân?"
                            : "Which profile detail would you like to update?") : null)
                    .profileUpdateAction(GeminiAssistantOutput.ProfileUpdateAction.builder()
                            .weightKg(extractedWeight)
                            .heightCm(extractedHeight)
                            .primaryGoals(extractedGoal)
                            .experienceLevel(extractedExp)
                            .displayName(extractedDisplayName)
                            .bio(extractedBio)
                            .isAmbiguous(isAmbiguous)
                            .build())
                    .build();
        }

        // 2. PR Record Pattern Check
        Pattern prPattern = Pattern.compile("(?i)(pr|personal record|hit|did|recorded|logged)\\s+([a-zA-Z\\s]+?)\\s+(\\d+(\\.\\d+)?)\\s*(reps|rep|kg|lbs|s|sec|seconds)?");
        Matcher prMatcher = prPattern.matcher(userMessage);
        if (prMatcher.find() || lower.contains("new pr") || lower.contains("record pr")) {
            String exName = "Pull Ups";
            BigDecimal val = BigDecimal.valueOf(10);
            String unit = "reps";
            String prType = "MAX_REPS";

            if (prMatcher.find(0)) {
                String parsedExercise = prMatcher.group(2).trim();
                String parsedVal = prMatcher.group(3);
                String parsedUnit = prMatcher.group(5);

                if (!parsedExercise.isBlank()) exName = parsedExercise;
                try {
                    if (parsedVal != null) val = new BigDecimal(parsedVal);
                } catch (Exception ignored) {}
                if (parsedUnit != null) unit = parsedUnit.toLowerCase();
            }

            if (unit.contains("kg") || unit.contains("lb")) {
                prType = "MAX_WEIGHT";
            } else if (unit.contains("s") || unit.contains("sec")) {
                prType = "MAX_HOLD_TIME";
            }

            return GeminiAssistantOutput.builder()
                    .intent("PR_RECORD")
                    .reply("Recording your PR for " + exName + "!")
                    .prAction(GeminiAssistantOutput.PrAction.builder()
                            .exerciseName(exName)
                            .value(val)
                            .unit(unit)
                            .prType(prType)
                            .isAmbiguous(false)
                            .notes("Logged via Calisthenics AI Assistant")
                            .build())
                    .build();
        }

        // 3. Workout Plan Pattern Check (with explicit Read-Only vs Write separation)
        WorkoutIntentClassification classified = classifyWorkoutIntent(userMessage, priorHistory);
        if (classified != null) {
            return GeminiAssistantOutput.builder()
                    .intent(classified.intent())
                    .reply(null)
                    .workoutPlanAction(GeminiAssistantOutput.WorkoutPlanAction.builder()
                            .prompt(userMessage)
                            .fitnessLevel("INTERMEDIATE")
                            .durationWeeks(4)
                            .toolName(classified.toolName())
                            .dayOfWeek(classified.targetDay() != null ? classified.targetDay().name()
                                    : (classified.isToday() ? "TODAY" : (classified.nextSession() ? "NEXT" : null)))
                            .sourceDayOfWeek(classified.sourceDay() != null ? classified.sourceDay().name() : null)
                            .exerciseName(classified.exerciseName())
                            .detailed(classified.detailed())
                            .build())
                    .build();
        }
        if (lower.contains("plan") || lower.contains("routine") || lower.contains("split") || lower.contains("schedule")) {
            return GeminiAssistantOutput.builder()
                    .intent("WORKOUT_PLAN")
                    .reply("Generating your personalized calisthenics routine...")
                    .workoutPlanAction(GeminiAssistantOutput.WorkoutPlanAction.builder()
                            .prompt(userMessage)
                            .fitnessLevel("INTERMEDIATE")
                            .durationWeeks(4)
                            .toolName(UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN)
                            .build())
                    .build();
        }

        // 4. Progress Analysis Check
        if (lower.contains("progress") || lower.contains("plateau") || lower.contains("analyze") || lower.contains("history")) {
            return GeminiAssistantOutput.builder()
                    .intent("PROGRESS_ANALYSIS")
                    .reply("Analyzing your calisthenics progress and PR history...")
                    .progressAnalysisAction(GeminiAssistantOutput.ProgressAnalysisAction.builder()
                            .focusArea("all")
                            .build())
                    .build();
        }

        // 5. General Calisthenics Coaching Chat
        return GeminiAssistantOutput.builder()
                .intent("GENERAL_CHAT")
                .reply("### 💪 Calisthenics Coaching Insights\n\n"
                        + "Consistency and strict form are the bedrock of calisthenics mastery.\n\n"
                        + "- **Skill Training First**: Always train high-skill balance movements (handstands, planches, levers) when fresh before high-rep conditioning.\n"
                        + "- **Progressive Overload**: Adjust leverage, increase hold durations, or introduce strict tempo (e.g., 3s eccentrics).\n"
                        + "- **Recovery**: Tendons adapt slower than muscles. Ensure adequate rest between heavy pulling and pushing days.\n\n"
                        + "What specific movement or skill are you focusing on today?")
                .build();
    }

    private Double extractWeight(String message) {
        Pattern p1 = Pattern.compile("(?i)(?:cân\\s*nặng|nặng|weight)(?:\\s+(?:của\\s+tôi|là|thành|to|is|:))?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:kg|kilos|kilograms)?");
        Matcher m1 = p1.matcher(message);
        if (m1.find()) {
            try {
                return Double.parseDouble(m1.group(1));
            } catch (Exception ignored) {}
        }

        Pattern p2 = Pattern.compile("(?i)(?:set|update|change)\\s+weight\\s+(?:to\\s+)?(\\d+(?:\\.\\d+)?)");
        Matcher m2 = p2.matcher(message);
        if (m2.find()) {
            try {
                return Double.parseDouble(m2.group(1));
            } catch (Exception ignored) {}
        }

        if (message.toLowerCase().contains("cân") || message.toLowerCase().contains("weight")) {
            Pattern p3 = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:kg|kilos|kilograms)");
            Matcher m3 = p3.matcher(message);
            if (m3.find()) {
                try {
                    return Double.parseDouble(m3.group(1));
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Double extractHeight(String message) {
        Pattern p1 = Pattern.compile("(?i)(?:chiều\\s*cao|cao|height)(?:\\s+(?:của\\s+tôi|là|thành|to|is|:))?\\s*(\\d+(?:\\.\\d+)?)\\s*(cm|centimeters|m)?");
        Matcher m1 = p1.matcher(message);
        if (m1.find()) {
            try {
                double val = Double.parseDouble(m1.group(1));
                String unit = m1.group(2);
                if ("m".equalsIgnoreCase(unit) && val < 3.0) {
                    val = val * 100.0;
                }
                return val;
            } catch (Exception ignored) {}
        }

        Pattern p2 = Pattern.compile("(?i)(?:set|update|change)\\s+height\\s+(?:to\\s+)?(\\d+(?:\\.\\d+)?)");
        Matcher m2 = p2.matcher(message);
        if (m2.find()) {
            try {
                return Double.parseDouble(m2.group(1));
            } catch (Exception ignored) {}
        }

        if (message.toLowerCase().contains("cao") || message.toLowerCase().contains("height")) {
            Pattern p3 = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:cm|centimeters)");
            Matcher m3 = p3.matcher(message);
            if (m3.find()) {
                try {
                    return Double.parseDouble(m3.group(1));
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String extractGoal(String message) {
        Pattern p = Pattern.compile("(?i)(?:mục\\s*tiêu\\s*(?:tập\\s*luyện)?|primary\\s*goals?|goals?)\\s*(?:thành|của\\s+tôi\\s+là|là|to|is|:)\\s*([^,.!?\\n]+)");
        Matcher m = p.matcher(message);
        if (m.find()) {
            String g = m.group(1).trim();
            if (!g.isBlank()) return g;
        }
        return null;
    }

    private String extractExperienceLevel(String message) {
        Pattern p = Pattern.compile("(?i)(?:trình\\s*độ|cấp\\s*độ|experience\\s*level|fitness\\s*level)\\s*(?:thành|là|to|is|:)?\\s*(BEGINNER|INTERMEDIATE|ADVANCED|ELITE)");
        Matcher m = p.matcher(message);
        if (m.find()) {
            return m.group(1).trim().toUpperCase();
        }
        return null;
    }

    private String extractDisplayName(String message) {
        Pattern p = Pattern.compile("(?i)(?:tên\\s*hiển\\s*thị|display\\s*name|đổi\\s*tên\\s*thành|change\\s*name\\s*to)\\s*(?:thành|là|to|is|:)?\\s*([^,.!?\\n]+)");
        Matcher m = p.matcher(message);
        if (m.find()) {
            String n = m.group(1).trim();
            if (!n.isBlank()) return n;
        }
        return null;
    }

    private String extractBio(String message) {
        Pattern p = Pattern.compile("(?i)(?:tiểu\\s*sử|bio|description)\\s*(?:thành|là|to|is|:)?\\s*([^.!?\\n]+)");
        Matcher m = p.matcher(message);
        if (m.find()) {
            String b = m.group(1).trim();
            if (!b.isBlank()) return b;
        }
        return null;
    }

    private boolean isVietnamese(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("cập nhật") || lower.contains("hồ sơ") || lower.contains("cân nặng")
                || lower.contains("chiều cao") || lower.contains("mục tiêu") || lower.contains("tập luyện")
                || lower.contains("của tôi") || lower.contains("thành") || lower.contains("đổi")
                || lower.contains("xin chào") || lower.contains("trình độ") || lower.contains("bạn")
                || lower.contains("lịch tập") || lower.contains("hôm nay") || lower.contains("xem")
                || lower.contains("thứ ") || lower.contains("bài tập") || lower.contains("tạo")
                || lower.contains("thêm") || lower.contains("xóa") || lower.contains("chi tiết");
    }

    private String safeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private PersonalRecordType inferRecordType(String unit) {
        if (unit == null) return PersonalRecordType.MAX_REPS;
        String u = unit.toLowerCase();
        if (u.contains("kg") || u.contains("lb") || u.contains("pound") || u.contains("kilo")) {
            return PersonalRecordType.MAX_WEIGHT;
        }
        if (u.contains("s") || u.contains("sec") || u.contains("min") || u.contains("hold")) {
            return PersonalRecordType.MAX_HOLD;
        }
        return PersonalRecordType.MAX_REPS;
    }

    private String generateTitleFromMessage(String userMessage, String intent) {
        if (userMessage == null || userMessage.isBlank()) {
            return switch (intent) {
                case "PR_RECORD" -> "PR Record Log";
                case "LIST_EXERCISES" -> "Exercise Directory";
                case "COACH_INFO" -> "Coach Profile";
                case "EXERCISE_RECOMMENDATION" -> "Exercise Recommendations";
                case "WORKOUT_CONSULTATION" -> "Routine Consultation";
                case "WORKOUT_PLAN", "WORKOUT_PLAN_VIEW", "WORKOUT_DAY_VIEW", "WORKOUT_PLAN_UPDATE", "WORKOUT_PLAN_PREVIEW" -> "Workout Plan Session";
                case "PROGRESS_ANALYSIS" -> "Progress Analysis";
                case "PROFILE_UPDATE" -> "Profile Update";
                default -> "Calisthenics Coaching";
            };
        }
        String clean = userMessage.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        String[] words = clean.split("\\s+");
        if (words.length <= 4) {
            return clean;
        }
        return String.join(" ", Arrays.copyOfRange(words, 0, 4)) + "...";
    }

    private AiConversationSummaryResponse mapToSummary(AiConversation conversation) {
        Optional<AiMessage> lastMessage = aiMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId());
        String preview = lastMessage.map(AiMessage::getContent).orElse("No messages yet");
        if (preview.length() > 80) {
            preview = preview.substring(0, 77) + "...";
        }
        String lastRole = lastMessage.map(AiMessage::getRole).orElse(null);
        ZonedDateTime lastInteraction = lastMessage.map(AiMessage::getCreatedAt).orElse(conversation.getUpdatedAt());

        List<AiMessage> allMessages = aiMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());

        return AiConversationSummaryResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .lastMessagePreview(preview)
                .lastMessageRole(lastRole)
                .lastInteractionAt(lastInteraction)
                .createdAt(conversation.getCreatedAt())
                .messageCount(allMessages.size())
                .build();
    }

    private AiMessageResponse mapToMessageResponse(AiMessage message) {
        return mapToMessageResponse(message, null);
    }

    private AiMessageResponse mapToMessageResponse(AiMessage message, Long userId) {
        String metadata = message.getMetadata();
        if (userId != null
                && workoutPlanChangePreviewService != null
                && metadata != null
                && metadata.contains(WorkoutPlanChangePreviewService.METADATA_TYPE_PREVIEW)) {
            String hydrated = workoutPlanChangePreviewService.hydrateMessageMetadata(userId, metadata);
            if (hydrated != null) {
                metadata = hydrated;
            }
        }
        return AiMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .role(message.getRole())
                .content(message.getContent())
                .intent(message.getIntent())
                .metadata(metadata)
                .createdAt(message.getCreatedAt())
                .build();
    }
}
