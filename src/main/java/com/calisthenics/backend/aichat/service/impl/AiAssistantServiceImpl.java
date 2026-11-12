package com.calisthenics.backend.aichat.service.impl;

import com.calisthenics.backend.aichat.dto.*;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aichat.service.AiAssistantService;
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
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
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
        List<PersonalRecord> recentPrs = personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId);
        List<WorkoutSession> recentSessions = workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(
                userId, WorkoutSessionStatus.COMPLETED);

        // 3. Prepare Multi-turn Conversation Context for Gemini
        List<AiMessage> history = aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(history); // chronological order

        List<GeminiRequestDto.Content> geminiContents = new ArrayList<>();
        for (AiMessage msg : history) {
            String geminiRole = "user".equalsIgnoreCase(msg.getRole()) ? "user" : "model";
            geminiContents.add(GeminiRequestDto.Content.builder()
                    .role(geminiRole)
                    .parts(List.of(new GeminiRequestDto.Part(msg.getContent())))
                    .build());
        }

        String systemPrompt = buildSystemPrompt(user, clientProfileOpt.orElse(null), recentPrs, recentSessions);

        // 4. Call Gemini Structured Intent Classifier & Assistant
        GeminiAssistantOutput assistantOutput = null;
        try {
            assistantOutput = geminiApiClient.callAssistant(systemPrompt, geminiContents).orElse(null);
        } catch (Exception ex) {
            log.warn("Gemini assistant call encountered error: {}. Falling back to rule-based engine.", ex.getMessage());
        }

        // 5. Rule-Based Fallback if Gemini is offline or unconfigured
        if (assistantOutput == null || assistantOutput.getIntent() == null) {
            assistantOutput = fallbackIntentEngine(userContent, recentPrs);
        }

        // 6. Action Execution based on Intent
        String detectedIntent = assistantOutput.getIntent();
        String replyContent = assistantOutput.getReply();
        String actionMetadataJson = null;

        if ("PR_RECORD".equalsIgnoreCase(detectedIntent)) {
            ActionResult prResult = executePrRecordAction(userId, userContent, assistantOutput.getPrAction());
            if (prResult.replyOverride != null) {
                replyContent = prResult.replyOverride;
            }
            actionMetadataJson = prResult.metadataJson;
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
        }

        if (replyContent == null || replyContent.isBlank()) {
            replyContent = "I'm your Calisthenics AI Assistant. Let me know if you want to create a workout plan, record a personal record, or analyze your training progress!";
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
            if (profile.getExperienceLevel() != null) sb.append("- Experience Level: ").append(profile.getExperienceLevel()).append("\n");
            if (profile.getPrimaryGoals() != null) sb.append("- Primary Goals: ").append(profile.getPrimaryGoals()).append("\n");
            if (profile.getTrainingInterests() != null) sb.append("- Training Interests: ").append(profile.getTrainingInterests()).append("\n");
            if (profile.getPreferredFrequency() != null) sb.append("- Preferred Frequency: ").append(profile.getPreferredFrequency()).append("\n");
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
        sb.append("2. 'WORKOUT_PLAN': The user requests a calisthenics workout plan, schedule, or split (e.g., 'Make me a 4-day plan for front lever', 'Suggest a beginner routine').\n");
        sb.append("   - Populate 'workout_plan_action' with prompt, fitness_level, and duration_weeks.\n");
        sb.append("3. 'PROGRESS_ANALYSIS': The user asks about their progress, plateaus, improvements, or training trends (e.g., 'How is my pull up progress?', 'Am I plateauing on dips?').\n");
        sb.append("   - Populate 'progress_analysis_action'. Provide data-backed insights.\n");
        sb.append("4. 'GENERAL_CHAT': Form tips, exercise breakdowns, mobility advice, recovery, or motivation.\n");
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

    private GeminiAssistantOutput fallbackIntentEngine(String userMessage, List<PersonalRecord> recentPrs) {
        String lower = userMessage.toLowerCase();

        // 1. PR Record Pattern Check
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

        // 2. Workout Plan Pattern Check
        if (lower.contains("plan") || lower.contains("routine") || lower.contains("split") || lower.contains("schedule")) {
            return GeminiAssistantOutput.builder()
                    .intent("WORKOUT_PLAN")
                    .reply("Generating your personalized calisthenics routine...")
                    .workoutPlanAction(GeminiAssistantOutput.WorkoutPlanAction.builder()
                            .prompt(userMessage)
                            .fitnessLevel("INTERMEDIATE")
                            .durationWeeks(4)
                            .build())
                    .build();
        }

        // 3. Progress Analysis Check
        if (lower.contains("progress") || lower.contains("plateau") || lower.contains("analyze") || lower.contains("history")) {
            return GeminiAssistantOutput.builder()
                    .intent("PROGRESS_ANALYSIS")
                    .reply("Analyzing your calisthenics progress and PR history...")
                    .progressAnalysisAction(GeminiAssistantOutput.ProgressAnalysisAction.builder()
                            .focusArea("all")
                            .build())
                    .build();
        }

        // 4. General Calisthenics Coaching Chat
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
                case "WORKOUT_PLAN" -> "Workout Plan Session";
                case "PROGRESS_ANALYSIS" -> "Progress Analysis";
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
