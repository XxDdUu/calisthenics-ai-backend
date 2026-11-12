package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.*;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aichat.service.impl.AiAssistantServiceImpl;
import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.analytics.dto.CreatePersonalRecordRequest;
import com.calisthenics.backend.analytics.dto.PersonalRecordResponse;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private AiConversationRepository aiConversationRepository;

    @Mock
    private AiMessageRepository aiMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClientProfileRepository clientProfileRepository;

    @Mock
    private PersonalRecordRepository personalRecordRepository;

    @Mock
    private PersonalRecordService personalRecordService;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private AiWorkoutPlanService aiWorkoutPlanService;

    @Mock
    private GeminiApiClient geminiApiClient;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiAssistantServiceImpl aiAssistantService;

    private User testUser;
    private AiConversation testConversation;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("athlete@test.com")
                .fullName("Athlete User")
                .role(Role.ROLE_USER)
                .build();

        testConversation = AiConversation.builder()
                .id(10L)
                .user(testUser)
                .title("New Conversation")
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createConversation creates and returns summary")
    void testCreateConversation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(aiConversationRepository.save(any(AiConversation.class))).thenAnswer(inv -> {
            AiConversation c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(ZonedDateTime.now());
            c.setUpdatedAt(ZonedDateTime.now());
            return c;
        });

        AiConversationSummaryResponse summary = aiAssistantService.createConversation(1L, new CreateAiConversationRequest("My Training Plan"));

        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo(10L);
        assertThat(summary.getTitle()).isEqualTo("My Training Plan");
    }

    @Test
    @DisplayName("deleteConversation deletes only when owned by user")
    void testDeleteConversation_Success() {
        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testConversation));

        aiAssistantService.deleteConversation(1L, 10L);

        verify(aiConversationRepository).delete(testConversation);
    }

    @Test
    @DisplayName("deleteConversation throws 404 when conversation belongs to someone else")
    void testDeleteConversation_NotOwned_Throws() {
        when(aiConversationRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiAssistantService.deleteConversation(2L, 10L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("sendMessage processes PR_RECORD intent and creates PR via PersonalRecordService")
    void testSendMessage_PrRecordIntent() {
        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testConversation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(clientProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(personalRecordRepository.findByUserIdOrderByAchievedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(eq(1L), any())).thenReturn(Collections.emptyList());
        when(aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(10L)).thenReturn(new ArrayList<>());

        when(aiMessageRepository.save(any(AiMessage.class))).thenAnswer(inv -> {
            AiMessage m = inv.getArgument(0);
            m.setId(101L);
            m.setCreatedAt(ZonedDateTime.now());
            return m;
        });

        // Mock Gemini returning PR_RECORD intent
        GeminiAssistantOutput geminiOut = GeminiAssistantOutput.builder()
                .intent("PR_RECORD")
                .reply("Great job on your pull ups!")
                .prAction(GeminiAssistantOutput.PrAction.builder()
                        .exerciseName("Pull Ups")
                        .value(BigDecimal.valueOf(15))
                        .unit("reps")
                        .prType("MAX_REPS")
                        .isAmbiguous(false)
                        .build())
                .build();
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(geminiOut));

        PersonalRecordResponse prResponse = PersonalRecordResponse.builder()
                .id(50L)
                .exerciseName("Pull Ups")
                .recordType("MAX_REPS")
                .recordValue(BigDecimal.valueOf(15))
                .unit("reps")
                .build();
        when(personalRecordService.createPR(eq(1L), any(CreatePersonalRecordRequest.class))).thenReturn(prResponse);

        AiMessageResponse response = aiAssistantService.sendMessage(1L, 10L, new SendAiMessageRequest("I just did 15 pull ups", null));

        assertThat(response).isNotNull();
        assertThat(response.getIntent()).isEqualTo("PR_RECORD");
        assertThat(response.getMetadata()).contains("PR_RECORDED");
        assertThat(response.getContent()).contains("Pull Ups");

        verify(personalRecordService).createPR(eq(1L), argThat(req ->
                req.getExerciseName().equals("Pull Ups") && req.getValue().compareTo(BigDecimal.valueOf(15)) == 0
        ));
    }

    @Test
    @DisplayName("sendMessage processes WORKOUT_PLAN intent and generates preview via AiWorkoutPlanService")
    void testSendMessage_WorkoutPlanIntent() {
        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testConversation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(clientProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(personalRecordRepository.findByUserIdOrderByAchievedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(eq(1L), any())).thenReturn(Collections.emptyList());
        when(aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(10L)).thenReturn(new ArrayList<>());

        when(aiMessageRepository.save(any(AiMessage.class))).thenAnswer(inv -> {
            AiMessage m = inv.getArgument(0);
            m.setId(102L);
            m.setCreatedAt(ZonedDateTime.now());
            return m;
        });

        // Mock Gemini returning WORKOUT_PLAN intent
        GeminiAssistantOutput geminiOut = GeminiAssistantOutput.builder()
                .intent("WORKOUT_PLAN")
                .reply("Here is your custom 4-day planche routine")
                .workoutPlanAction(GeminiAssistantOutput.WorkoutPlanAction.builder()
                        .prompt("4-day planche routine")
                        .fitnessLevel("INTERMEDIATE")
                        .durationWeeks(4)
                        .build())
                .build();
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(geminiOut));

        AiWorkoutPlanPreviewResponse preview = AiWorkoutPlanPreviewResponse.builder()
                .planName("Intermediate Planche Mastery")
                .durationWeeks(4)
                .splitType(WorkoutSplitType.CUSTOM)
                .description("Focus on isometric holds and push strength")
                .build();
        when(aiWorkoutPlanService.generatePlan(eq(1L), any(), any())).thenReturn(preview);

        AiMessageResponse response = aiAssistantService.sendMessage(1L, 10L, new SendAiMessageRequest("Make me a 4-day planche routine", null));

        assertThat(response).isNotNull();
        assertThat(response.getIntent()).isEqualTo("WORKOUT_PLAN");
        assertThat(response.getMetadata()).contains("WORKOUT_PLAN_PREVIEW");
        assertThat(response.getMetadata()).contains("Intermediate Planche Mastery");

        verify(aiWorkoutPlanService).generatePlan(eq(1L), any(), any());
    }

    @Test
    @DisplayName("sendMessage processes PROGRESS_ANALYSIS intent and aggregates PR history")
    void testSendMessage_ProgressAnalysisIntent() {
        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testConversation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(clientProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        PersonalRecord pr1 = PersonalRecord.builder()
                .id(1L)
                .user(testUser)
                .customExerciseName("Dips")
                .recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(40))
                .unit("kg")
                .achievedAt(ZonedDateTime.now())
                .build();
        when(personalRecordRepository.findByUserIdOrderByAchievedAtDesc(1L)).thenReturn(List.of(pr1));
        when(workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(eq(1L), any())).thenReturn(Collections.emptyList());
        when(aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(10L)).thenReturn(new ArrayList<>());

        when(aiMessageRepository.save(any(AiMessage.class))).thenAnswer(inv -> {
            AiMessage m = inv.getArgument(0);
            m.setId(103L);
            m.setCreatedAt(ZonedDateTime.now());
            return m;
        });

        GeminiAssistantOutput geminiOut = GeminiAssistantOutput.builder()
                .intent("PROGRESS_ANALYSIS")
                .reply("You have made solid progress in weighted dips!")
                .progressAnalysisAction(GeminiAssistantOutput.ProgressAnalysisAction.builder()
                        .exerciseName("Dips")
                        .focusArea("strength")
                        .build())
                .build();
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(geminiOut));

        AiMessageResponse response = aiAssistantService.sendMessage(1L, 10L, new SendAiMessageRequest("How is my dip progress?", null));

        assertThat(response).isNotNull();
        assertThat(response.getIntent()).isEqualTo("PROGRESS_ANALYSIS");
        assertThat(response.getMetadata()).contains("PROGRESS_SUMMARY");
    }

    @Test
    @DisplayName("sendMessage handles GENERAL_CHAT coaching advice in Markdown format")
    void testSendMessage_GeneralChat() {
        when(aiConversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(testConversation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(clientProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(personalRecordRepository.findByUserIdOrderByAchievedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(eq(1L), any())).thenReturn(Collections.emptyList());
        when(aiMessageRepository.findTop15ByConversationIdOrderByCreatedAtDesc(10L)).thenReturn(new ArrayList<>());

        when(aiMessageRepository.save(any(AiMessage.class))).thenAnswer(inv -> {
            AiMessage m = inv.getArgument(0);
            m.setId(104L);
            m.setCreatedAt(ZonedDateTime.now());
            return m;
        });

        GeminiAssistantOutput geminiOut = GeminiAssistantOutput.builder()
                .intent("GENERAL_CHAT")
                .reply("### Handstand Balance Cues\n\n- Grip the floor firmly with your fingers.\n- Keep your shoulders elevated.\n- Squeeze your glutes.")
                .build();
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(geminiOut));

        AiMessageResponse response = aiAssistantService.sendMessage(1L, 10L, new SendAiMessageRequest("Give me tips for handstand balance", null));

        assertThat(response).isNotNull();
        assertThat(response.getIntent()).isEqualTo("GENERAL_CHAT");
        assertThat(response.getContent()).contains("### Handstand Balance Cues");
        assertThat(response.getMetadata()).isNull();
    }
}
