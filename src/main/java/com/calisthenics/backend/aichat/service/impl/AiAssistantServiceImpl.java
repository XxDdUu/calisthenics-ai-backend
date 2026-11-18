package com.calisthenics.backend.aichat.service.impl;

import com.calisthenics.backend.aichat.dto.*;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aichat.service.AiAssistantService;
import com.calisthenics.backend.aichat.service.UserAiWorkoutToolService;
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
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
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
                .map(this::mapToMessageResponse)
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

        aiMessageRepository.deleteByConversationId(conversationId);
        aiConversationRepository.delete(conversation);
        aiConversationRepository.flush();
        log.info("Deleted AI Conversation id={} for user={}", conversationId, userId);
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

        // 3. Prepare Multi-turn Conversation Context for Gemini
        List<AiMessage> history = aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history); // chronological order
        List<AiMessage> priorHistory = history.stream()
                .filter(m -> userMessage.getId() == null || !Objects.equals(m.getId(), userMessage.getId()))
                .filter(m -> !("user".equalsIgnoreCase(m.getRole()) && userContent.equals(m.getContent()) && m == history.get(history.size() - 1)))
                .toList();

        WorkoutIntentClassification preClassifiedWorkout = classifyWorkoutIntent(userContent, priorHistory);

        List<GeminiRequestDto.Content> geminiContents = new ArrayList<>();
        for (AiMessage msg : history) {
            String geminiRole = "user".equalsIgnoreCase(msg.getRole()) ? "user" : "model";
            geminiContents.add(GeminiRequestDto.Content.builder()
                    .role(geminiRole)
                    .parts(List.of(new GeminiRequestDto.Part(msg.getContent())))
                    .build());
        }

        String systemPrompt = buildSystemPrompt(user, clientProfileOpt.orElse(null), latestWeight, recentPrs, recentSessions);

        // 4. Call Gemini Structured Intent Classifier & Assistant (skip unnecessary write bias when deterministic read-only intent applies)
        GeminiAssistantOutput assistantOutput = null;
        if (preClassifiedWorkout == null || !preClassifiedWorkout.readOnly()) {
            try {
                assistantOutput = geminiApiClient.callAssistant(systemPrompt, geminiContents).orElse(null);
            } catch (Exception ex) {
                log.warn("Gemini assistant call encountered error: {}. Falling back to rule-based engine.", ex.getMessage());
            }
        }

        // 5. Rule-Based Fallback if Gemini is offline or skipped for read-only safety
        if (assistantOutput == null || assistantOutput.getIntent() == null) {
            assistantOutput = fallbackIntentEngine(userContent, recentPrs, priorHistory);
        }

        // Guardrail: enforce deterministic read-only/write workout classification over generic WORKOUT_PLAN
        WorkoutIntentClassification finalWorkoutDecision = reconcileWorkoutDecision(
                preClassifiedWorkout, userContent, assistantOutput, priorHistory);

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
                    userContent,
                    finalWorkoutDecision,
                    assistantOutput.getWorkoutPlanAction(),
                    priorHistory,
                    request.getTimezone()
            );
            if (planResult.replyOverride != null) {
                replyContent = planResult.replyOverride;
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

        // 8. Auto-update Conversation Title if still "New Conversation"
        if ("New Conversation".equalsIgnoreCase(conversation.getTitle())) {
            conversation.setTitle(generateTitleFromMessage(userContent, detectedIntent));
        }
        conversation.setUpdatedAt(ZonedDateTime.now());
        aiConversationRepository.save(conversation);

        log.info("AI message processed in conversation={}. Intent={}, HasMetadata={}",
                conversationId, detectedIntent, actionMetadataJson != null);

        return mapToMessageResponse(savedAssistantMessage);
    }

    private String buildSystemPrompt(
            User user,
            ClientProfile profile,
            Double latestWeight,
            List<PersonalRecord> recentPrs,
            List<WorkoutSession> recentSessions
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the Calisthenics AI Assistant, an elite, motivational, and scientifically rigorous calisthenics personal coach.\n");
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
        sb.append("3. 'WORKOUT_PLAN' / 'WORKOUT_PLAN_UPDATE' (WRITE ONLY ON EXPLICIT REQUEST): The user explicitly asks to create, generate, replace, regenerate, change, add exercises to, or remove exercises from a workout plan.\n");
        sb.append("   - Populate 'workout_plan_action' with tool_name, prompt, fitness_level, duration_weeks, day_of_week, and detailed.\n");
        sb.append("4. 'PROGRESS_ANALYSIS': The user asks about their progress, plateaus, improvements, or training trends (e.g., 'How is my pull up progress?', 'Am I plateauing on dips?').\n");
        sb.append("   - Populate 'progress_analysis_action'. Provide data-backed insights.\n");
        sb.append("5. 'PROFILE_UPDATE': The user requests to update their personal/athlete profile details (e.g. 'Cập nhật cân nặng của tôi thành 76kg', 'Đổi mục tiêu tập luyện thành Straddle Planche', 'My height is 178 cm', 'Update my bio to Calisthenics athlete', 'Change display name to Alex').\n");
        sb.append("   - Supported editable fields: display_name, full_name, bio, height_cm, weight_kg, experience_level, training_experience, primary_goals, training_interests, preferred_frequency, coaching_language, gender.\n");
        sb.append("   - If unambiguous and has specific field and value, set 'is_ambiguous=false' and populate 'profile_update_action'.\n");
        sb.append("   - If ambiguous or missing specific values (e.g. 'update my profile' without specifying what), set 'is_ambiguous=true' and ask what they want to change in 'clarification_question'.\n");
        sb.append("6. 'GENERAL_CHAT': Form tips, exercise breakdowns, mobility advice, recovery, or motivation.\n");
        sb.append("   - Provide structured, rich Markdown responses (headers, bold, bullet points).\n");
        return sb.toString();
    }

    private record ActionResult(String replyOverride, String metadataJson) {}

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
            WorkoutDayOfWeek targetDay,
            boolean isToday,
            boolean nextSession,
            boolean detailed
    ) {}

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
        WorkoutDayOfWeek day = parseDayOfWeek(action != null ? action.getDayOfWeek() : null, userMessage);
        boolean isToday = action != null && "TODAY".equalsIgnoreCase(action.getDayOfWeek());
        boolean nextSession = action != null && "NEXT".equalsIgnoreCase(action.getDayOfWeek());

        if ("WORKOUT_PLAN_VIEW".equals(intent)) {
            String tool = detailed
                    ? UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE
                    : UserAiWorkoutToolService.TOOL_GET_MY_WORKOUT_PLAN;
            return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW", tool, true, null, false, false, detailed);
        }
        if ("WORKOUT_DAY_VIEW".equals(intent)) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, day, isToday, nextSession, true);
        }
        if ("EXERCISE_DETAILS_VIEW".equals(intent)) {
            return new WorkoutIntentClassification("EXERCISE_DETAILS_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_EXERCISE_DETAILS, true, null, false, false, true);
        }
        if ("WORKOUT_PLAN_UPDATE".equals(intent)) {
            String tool = (declaredTool != null && !UserAiWorkoutToolService.isReadOnlyTool(declaredTool))
                    ? declaredTool
                    : UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN;
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE", tool, false, day, false, false, true);
        }
        if ("WORKOUT_PLAN".equals(intent)) {
            if (declaredTool != null && UserAiWorkoutToolService.isReadOnlyTool(declaredTool)) {
                return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW", declaredTool, true, day, isToday, nextSession, detailed);
            }
            if (UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN.equals(declaredTool)) {
                return new WorkoutIntentClassification("WORKOUT_PLAN",
                        UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN, false, day, false, false, false);
            }
            return new WorkoutIntentClassification("WORKOUT_PLAN",
                    UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN, false, day, false, false, false);
        }
        return null;
    }

    private ActionResult executeClassifiedWorkoutTool(
            Long userId,
            String userContent,
            WorkoutIntentClassification decision,
            GeminiAssistantOutput.WorkoutPlanAction planAction,
            List<AiMessage> priorHistory,
            String timezone
    ) {
        boolean isVn = isVietnamese(userContent);
        if (decision.readOnly()) {
            try {
                UserAiWorkoutToolService.WorkoutToolResult result = switch (decision.toolName()) {
                    case UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE ->
                            userAiWorkoutToolService.getWeeklyWorkoutSchedule(userId, decision.detailed(), isVn);
                    case UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS ->
                            userAiWorkoutToolService.getWorkoutDayDetails(
                                    userId, decision.targetDay(), decision.isToday(), decision.nextSession(),
                                    timezone, decision.detailed(), isVn);
                    case UserAiWorkoutToolService.TOOL_GET_EXERCISE_DETAILS ->
                            userAiWorkoutToolService.getExerciseDetails(userId, userContent, isVn);
                    default -> userAiWorkoutToolService.getMyWorkoutPlan(userId, decision.detailed(), isVn);
                };
                return new ActionResult(result.reply(), result.metadataJson());
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

        // Write tools
        if (UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN.equals(decision.toolName())) {
            return executeWorkoutPlanAction(userId, userContent, planAction, timezone);
        }
        if (UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN.equals(decision.toolName())) {
            String fitnessLevel = planAction != null ? planAction.getFitnessLevel() : "INTERMEDIATE";
            Integer weeks = planAction != null ? planAction.getDurationWeeks() : 4;
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.createWorkoutPlan(
                    userId, userContent, fitnessLevel, weeks, timezone, isVn, true);
            return new ActionResult(res.reply(), res.metadataJson());
        }
        if (UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN.equals(decision.toolName())) {
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.updateWorkoutPlan(
                    userId, decision.targetDay(), userContent, timezone, isVn);
            return new ActionResult(res.reply(), res.metadataJson());
        }
        if (UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT.equals(decision.toolName())) {
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.addExerciseToWorkout(
                    userId, decision.targetDay(), userContent, timezone, isVn);
            return new ActionResult(res.reply(), res.metadataJson());
        }
        if (UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT.equals(decision.toolName())) {
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.removeExerciseFromWorkout(
                    userId, decision.targetDay(), userContent, priorHistory, isVn);
            return new ActionResult(res.reply(), res.metadataJson());
        }
        if (UserAiWorkoutToolService.TOOL_ASSIGN_WORKOUT_PLAN.equals(decision.toolName())) {
            UserAiWorkoutToolService.WorkoutToolResult res = userAiWorkoutToolService.assignWorkoutPlan(
                    userId, null, userContent, isVn);
            return new ActionResult(res.reply(), res.metadataJson());
        }
        return executeWorkoutPlanAction(userId, userContent, planAction, timezone);
    }

    private WorkoutIntentClassification classifyWorkoutIntent(String userMessage, List<AiMessage> priorHistory) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String lower = userMessage.toLowerCase(Locale.US).trim();

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

        WorkoutDayOfWeek weekday = UserAiWorkoutToolService.extractWeekday(userMessage);
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

        // 1. Check EXPLICIT WRITE intents first
        boolean hasReplaceVerb = lower.contains("replace") || lower.contains("regenerate")
                || lower.contains("redo") || lower.contains("rebuild")
                || lower.contains("thay thế") || lower.contains("tạo lại");
        if (hasReplaceVerb && (mentionsPlanOrSchedule || hasWorkoutHistoryContext || lower.contains("it"))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN",
                    UserAiWorkoutToolService.TOOL_REGENERATE_WORKOUT_PLAN, false, weekday, false, false, false);
        }

        boolean hasCreateVerb = lower.contains("create") || lower.contains("generate")
                || lower.contains("make me") || lower.contains("make a")
                || lower.contains("build me") || lower.contains("build a")
                || lower.contains("design a") || lower.contains("suggest a new")
                || lower.contains("tạo lịch") || lower.contains("lập lịch") || lower.contains("tạo một lịch");
        if (hasCreateVerb && (mentionsPlanOrSchedule || lower.contains("split") || lower.contains("schedule") || lower.contains("workout"))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN",
                    UserAiWorkoutToolService.TOOL_CREATE_WORKOUT_PLAN, false, weekday, false, false, false);
        }

        boolean hasAddVerb = lower.startsWith("add ") || lower.contains(" add ") || lower.contains("thêm ");
        if (hasAddVerb && (mentionsExerciseVerb || mentionsPlanOrSchedule || weekday != null || hasWorkoutHistoryContext)) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_ADD_EXERCISE_TO_WORKOUT, false, weekday, false, false, true);
        }

        boolean hasRemoveVerb = lower.contains("remove ") || lower.contains("delete ")
                || lower.contains("drop ") || lower.contains("xóa ") || lower.contains("bỏ ");
        if (hasRemoveVerb && (mentionsExerciseVerb || mentionsPlanOrSchedule || weekday != null || hasWorkoutHistoryContext)) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_REMOVE_EXERCISE_FROM_WORKOUT, false, weekday, false, false, true);
        }

        boolean hasModifyVerb = lower.contains("change ") || lower.contains("modify ")
                || lower.contains("update ") || lower.contains("switch ")
                || lower.contains("swap ") || lower.contains("edit ")
                || lower.contains("đổi ") || lower.contains("sửa ") || lower.contains("chỉnh ");
        if (hasModifyVerb && (mentionsPlanOrSchedule || weekday != null || lower.contains("workout"))) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_UPDATE_WORKOUT_PLAN, false, weekday, false, false, true);
        }

        boolean hasAssignVerb = (lower.contains("assign ") || lower.contains("activate ") || lower.contains("kích hoạt"))
                && mentionsPlanOrSchedule;
        if (hasAssignVerb) {
            return new WorkoutIntentClassification("WORKOUT_PLAN_UPDATE",
                    UserAiWorkoutToolService.TOOL_ASSIGN_WORKOUT_PLAN, false, null, false, false, false);
        }

        // 2. Check EXPLICIT READ-ONLY intents
        boolean asksDetail = lower.contains("detail") || lower.contains("details")
                || lower.contains("full") || lower.contains("complete")
                || lower.contains("list all") || lower.contains("explain")
                || lower.contains("chi tiết") || lower.contains("đầy đủ");

        if (mentionsTodayTraining) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, true, false, true);
        }

        if (mentionsNextSession) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, null, false, true, true);
        }

        if (weekday != null) {
            return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, weekday, false, false, true);
        }

        if (mentionsPlanOrSchedule) {
            String tool = (asksDetail || lower.contains("schedule") || lower.contains("weekly")
                    || lower.contains("lịch") || lower.contains("list all"))
                    ? UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE
                    : UserAiWorkoutToolService.TOOL_GET_MY_WORKOUT_PLAN;
            return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW", tool, true, null, false, false,
                    asksDetail || lower.contains("list all") || lower.contains("weekly") || lower.contains("schedule"));
        }

        if (lower.contains("exercise details") || lower.contains("details for ")
                || lower.contains("show exercise ") || lower.contains("chi tiết bài tập")) {
            return new WorkoutIntentClassification("EXERCISE_DETAILS_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_EXERCISE_DETAILS, true, null, false, false, true);
        }

        // 3. Follow-up context resolution (e.g., "Show it in more detail", "Show it in detail")
        boolean isDetailFollowUp = (asksDetail || lower.contains("tell me more") || lower.contains("show it") || lower.contains("xem thêm"))
                && !lower.contains("progress") && !lower.contains("pr ");
        if (hasWorkoutHistoryContext && isDetailFollowUp) {
            if ("WORKOUT_DAY_VIEW".equals(prevIntent) && prevDay != null) {
                return new WorkoutIntentClassification("WORKOUT_DAY_VIEW",
                        UserAiWorkoutToolService.TOOL_GET_WORKOUT_DAY_DETAILS, true, prevDay, false, false, true);
            }
            return new WorkoutIntentClassification("WORKOUT_PLAN_VIEW",
                    UserAiWorkoutToolService.TOOL_GET_WEEKLY_WORKOUT_SCHEDULE, true, null, false, false, true);
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
        if (rawDay != null && !rawDay.isBlank()) {
            try {
                return WorkoutDayOfWeek.valueOf(rawDay.trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UserAiWorkoutToolService.extractWeekday(userMessage);
    }

    private GeminiAssistantOutput fallbackIntentEngine(
            String userMessage,
            List<PersonalRecord> recentPrs,
            List<AiMessage> priorHistory
    ) {
        String lower = userMessage.toLowerCase();

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
                case "WORKOUT_PLAN", "WORKOUT_PLAN_VIEW", "WORKOUT_DAY_VIEW", "WORKOUT_PLAN_UPDATE" -> "Workout Plan Session";
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
        return AiMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .role(message.getRole())
                .content(message.getContent())
                .intent(message.getIntent())
                .metadata(message.getMetadata())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
