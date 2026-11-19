package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiPlannerListingCoachAndDeduplicationIntegrationTest {

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "bulk_exercise_generation_items",
            "bulk_exercise_generation_jobs",
            "admin_ai_chat_idempotency",
            "admin_ai_pending_actions",
            "admin_ai_audit_logs",
            "admin_ai_recommendations",
            "admin_ai_messages",
            "admin_ai_conversations",
            "admin_pro_audit_logs",
            "membership_entitlements",
            "getting_started_campaign_events",
            "getting_started_day_progress",
            "getting_started_day_completions",
            "shared_personal_records",
            "personal_records",
            "workout_logs",
            "workout_session_sets",
            "workout_session_exercises",
            "workout_sessions",
            "getting_started_enrollments",
            "getting_started_program_user_states",
            "getting_started_program_exercises",
            "getting_started_program_days",
            "getting_started_programs",
            "workout_plan_assignments",
            "workout_plan_exercises",
            "workout_plan_days",
            "workout_plans",
            "coach_client_relationships",
            "chat_messages",
            "chat_conversations",
            "user_presence",
            "ai_workout_plan_change_previews",
            "ai_messages",
            "ai_conversations",
            "ai_workout_plan_logs",
            "coach_review_events",
            "coach_reviews",
            "user_report_evidence",
            "user_report_actions",
            "user_reports",
            "notifications",
            "rep_histories",
            "analysis_sessions",
            "analyses",
            "video_analysis_keypoints",
            "video_analyses",
            "video_analysis_quota_usages",
            "profile_media_items",
            "client_body_measurements",
            "client_profiles",
            "coach_profiles",
            "exercise_muscle_targets",
            "exercise_media_items",
            "exercises",
            "password_reset_otps",
            "registration_otps",
            "users"
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientProfileRepository clientProfileRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private CoachClientRelationshipRepository coachClientRelationshipRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Autowired private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private User athleteWithCoach;
    private User athleteWithoutCoach;
    private User athleteWithSparseCoach;
    private User fullCoachUser;
    private User sparseCoachUser;

    private String tokenWithCoach;
    private String tokenWithoutCoach;
    private String tokenWithSparseCoach;

    private WorkoutPlan athletePlan;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();
        long ts = System.currentTimeMillis();

        athleteWithCoach = userRepository.save(User.builder()
                .email("athlete_coached_" + ts + "@test.com")
                .fullName("Athlete Private Name")
                .role(Role.ROLE_USER)
                .build());
        athleteWithoutCoach = userRepository.save(User.builder()
                .email("athlete_solo_" + ts + "@test.com")
                .fullName("Solo Athlete")
                .role(Role.ROLE_USER)
                .build());
        athleteWithSparseCoach = userRepository.save(User.builder()
                .email("athlete_sparse_" + ts + "@test.com")
                .fullName("Sparse Athlete")
                .role(Role.ROLE_USER)
                .build());

        fullCoachUser = userRepository.save(User.builder()
                .email("coach_full_" + ts + "@test.com")
                .fullName("Coach Marcus Vance")
                .avatarUrl("https://cdn.example.com/user-avatar.png")
                .role(Role.ROLE_USER)
                .build());
        sparseCoachUser = userRepository.save(User.builder()
                .email("coach_sparse_" + ts + "@test.com")
                .fullName("Coach Elena Minimal")
                .role(Role.ROLE_USER)
                .build());

        clientProfileRepository.save(ClientProfile.builder()
                .user(athleteWithCoach)
                .displayName("Athlete Secret Handle")
                .experienceLevel("INTERMEDIATE")
                .primaryGoals("Handstand Push-Up Mastery")
                .build());

        coachProfileRepository.save(CoachProfile.builder()
                .user(fullCoachUser)
                .displayName("Marcus Vance CSCS")
                .avatarUrl("https://cdn.example.com/coach-marcus.png")
                .bio("Former gymnastics specialist focused on strict bodyweight strength.")
                .specialties("Handstand Push-Ups, Planche, Lower-Body Mobility")
                .yearsExperience(9)
                .certifications("CSCS, USA Gymnastics Level 2")
                .trainingPhilosophy("Master strict joint alignment before chasing heavy leverage.")
                .coachingDescription("Weekly form reviews with structured progression blocks.")
                .languages("English, Spanish")
                .isPublic(false) // Even private coach profile must be accessible to active athlete!
                .isVerified(true)
                .build());

        coachProfileRepository.save(CoachProfile.builder()
                .user(sparseCoachUser)
                .bio("Calisthenics movement fundamentals.")
                .build());

        coachClientRelationshipRepository.save(CoachClientRelationship.builder()
                .coach(fullCoachUser)
                .client(athleteWithCoach)
                .requestedBy(athleteWithCoach)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        // Pending relationship for athleteWithoutCoach must NOT be treated as assigned active coach
        coachClientRelationshipRepository.save(CoachClientRelationship.builder()
                .coach(fullCoachUser)
                .client(athleteWithoutCoach)
                .requestedBy(athleteWithoutCoach)
                .status(CoachClientRelationshipStatus.PENDING)
                .build());

        coachClientRelationshipRepository.save(CoachClientRelationship.builder()
                .coach(sparseCoachUser)
                .client(athleteWithSparseCoach)
                .requestedBy(athleteWithSparseCoach)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        tokenWithCoach = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athleteWithCoach));
        tokenWithoutCoach = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athleteWithoutCoach));
        tokenWithSparseCoach = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athleteWithSparseCoach));

        seedExercises();
        athletePlan = buildActivePlanFor(athleteWithCoach);
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private void seedExercises() {
        // 7 distinct leg exercises so pagination + "another exercise" + exhaustion all work cleanly
        saveEx("Bulgarian Split Squat", "BULGARIAN_SPLIT_SQUAT", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Quadriceps, Glutes", "Hamstrings", "Unilateral squat strength for legs.");
        saveEx("Pistol Squat", "PISTOL_SQUAT", ExerciseCategory.LEGS, DifficultyLevel.ADVANCED,
                "Quadriceps, Glutes", "Calves", "Single-leg bodyweight squat progression.");
        saveEx("Single-Leg Romanian Deadlift", "SINGLE_LEG_RDL", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Hamstrings, Glutes", "Erector Spinae", "Posterior chain hinge for hamstrings.");
        saveEx("Nordic Hamstring Curl", "NORDIC_CURL", ExerciseCategory.LEGS, DifficultyLevel.ADVANCED,
                "Hamstrings", "Glutes, Calves", "Eccentric knee flexion for hamstrings.");
        saveEx("Standing Calf Raises", "STANDING_CALF_RAISES", ExerciseCategory.LEGS, DifficultyLevel.BEGINNER,
                "Calves", "Tibialis", "Direct ankle plantar flexion calf builder.");
        saveEx("Walking Lunges", "WALKING_LUNGES", ExerciseCategory.LEGS, DifficultyLevel.BEGINNER,
                "Quadriceps, Glutes", "Hamstrings", "Dynamic unilateral leg movement.");
        saveEx("Hip Thrust", "HIP_THRUST", ExerciseCategory.GLUTES, DifficultyLevel.BEGINNER,
                "Glutes", "Hamstrings", "Hip extension power for glutes and posterior legs.");

        // Push & HSPU exercises (used for HSPU recommendation + topic reset test)
        saveEx("Pike Push-Up", "PIKE_PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.INTERMEDIATE,
                "Front Deltoids, Triceps", "Upper Trapezius", "Vertical pushing progression toward handstand push-ups.");
        saveEx("Wall Handstand Push-Up", "WALL_HSPU", ExerciseCategory.PUSH, DifficultyLevel.ADVANCED,
                "Front Deltoids, Triceps", "Upper Trapezius", "Overhead vertical press against the wall for HSPU.");
        saveEx("Handstand Hold", "HANDSTAND", ExerciseCategory.SKILL, DifficultyLevel.INTERMEDIATE,
                "Front Deltoids, Core", "Triceps", "Overhead balance and shoulder stability.");
        saveEx("Push-Up", "PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.BEGINNER,
                "Chest, Front Deltoids", "Triceps", "Fundamental horizontal calisthenics press.");
    }

    private Exercise saveEx(
            String name, String code, ExerciseCategory category, DifficultyLevel diff,
            String primary, String secondary, String description
    ) {
        return exerciseRepository.save(Exercise.builder()
                .name(name)
                .code(code)
                .category(category)
                .difficulty(diff)
                .targetMuscles(primary)
                .secondaryMuscles(secondary)
                .description(description)
                .equipment("Floor")
                .recommendedSets(3)
                .recommendedReps("10")
                .recommendedRestSeconds(90)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());
    }

    private WorkoutPlan buildActivePlanFor(User owner) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name("Super Secret Plan Name")
                .splitType(WorkoutSplitType.UPPER_LOWER)
                .active(true)
                .build();
        Exercise pushUp = exerciseRepository.findByCode("PUSH_UP").orElseThrow();
        Exercise pistolSquat = exerciseRepository.findByCode("PISTOL_SQUAT").orElseThrow();

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean rest = dow != WorkoutDayOfWeek.MONDAY && dow != WorkoutDayOfWeek.WEDNESDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(rest)
                    .name(dow == WorkoutDayOfWeek.WEDNESDAY ? "Wednesday Leg Session" : "Rest Day")
                    .sessionType(dow == WorkoutDayOfWeek.WEDNESDAY ? WorkoutSessionType.LEGS
                            : (dow == WorkoutDayOfWeek.MONDAY ? WorkoutSessionType.PUSH : WorkoutSessionType.REST))
                    .build();
            if (dow == WorkoutDayOfWeek.MONDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pushUp).sets(4).reps(12).restSeconds(90).displayOrder(1).build());
            } else if (dow == WorkoutDayOfWeek.WEDNESDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pistolSquat).sets(4).reps(8).restSeconds(90).displayOrder(1).build());
            }
            plan.getDays().add(day);
        }
        return workoutPlanRepository.save(plan);
    }

    private Long createConversation(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Planner Test"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private AiMessageResponse sendMessage(String token, Long convId, String msg) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendAiMessageRequest(msg, "UTC"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(StandardCharsets.UTF_8), AiMessageResponse.class);
    }

    private List<String> wednesdayExercises() {
        WorkoutPlanDay wed = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(athletePlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.WEDNESDAY)
                .findFirst().orElseThrow();
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(wed.getId()).stream()
                .map(pe -> pe.getExercise().getName())
                .toList();
    }

    private static List<String> extractNumberedNames(String markdown) {
        List<String> names = new ArrayList<>();
        Pattern p = Pattern.compile("(?m)^\\d+\\.\\s+\\*{0,2}([^\\n*—]+?)\\*{0,2}(?:\\s+—.*)?$");
        Matcher m = p.matcher(markdown);
        while (m.find()) {
            names.add(m.group(1).trim());
        }
        return names;
    }

    @Test
    @DisplayName("LIST_EXERCISES & Informational RECOMMEND_EXERCISES strictly exclude plan analysis, gaps, weekly sets, weekdays, and follow-up questions")
    void exerciseListingAndInformationalRecommendationsObeyStrictRulesAndLeaveModificationIntact() throws Exception {
        Long convId = createConversation(tokenWithCoach);

        // 1. "List me some legs exercises"
        AiMessageResponse listLegs = sendMessage(tokenWithCoach, convId, "List me some legs exercises");
        assertThat(listLegs.getIntent()).isEqualTo("LIST_EXERCISES");
        assertThat(listLegs.getContent()).startsWith("**Leg Exercises**");
        assertThat(listLegs.getContent()).doesNotContain(
                "Super Secret Plan Name", "Current Plan Analyzed", "Gaps Detected",
                "effective weekly sets", "effective sets", "Wednesday", "Thursday",
                "Target Session", "choose a weekday", "Select any exercises", "which day"
        );
        List<String> firstNames = extractNumberedNames(listLegs.getContent());
        assertThat(firstNames).hasSize(5);

        // 2. "What exercises can I do for my legs?" -> LIST_EXERCISES
        AiMessageResponse whatLegs = sendMessage(tokenWithCoach, createConversation(tokenWithCoach), "What exercises can I do for my legs?");
        assertThat(whatLegs.getIntent()).isEqualTo("LIST_EXERCISES");
        assertThat(whatLegs.getContent()).startsWith("**Leg Exercises**");
        assertThat(whatLegs.getContent()).doesNotContain("Current Plan Analyzed", "Gaps Detected", "effective", "Wednesday", "weekday");

        // 3. "Recommend some exercises for handstand push-ups" -> Informational only, no plan/gaps/weekly sets/weekdays
        AiMessageResponse hspuRec = sendMessage(tokenWithCoach, createConversation(tokenWithCoach), "Recommend some exercises for handstand push-ups");
        assertThat(hspuRec.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(hspuRec.getContent()).contains("Pike Push-Up", "Wall Handstand Push-Up");
        assertThat(hspuRec.getContent()).doesNotContain(
                "Super Secret Plan Name", "Current Plan Analyzed", "Gaps Detected",
                "effective weekly sets", "Wednesday", "choose a weekday", "Select any exercises"
        );
        JsonNode hspuMeta = objectMapper.readTree(hspuRec.getMetadata());
        assertThat(hspuMeta.path("informationalOnly").asBoolean()).isTrue();
        assertThat(hspuMeta.path("undertrainedMuscles")).isEmpty();
        assertThat(hspuMeta.path("availablePlanDays")).isEmpty();
        assertThat(hspuMeta.path("suggestedDayOfWeek").isMissingNode() || hspuMeta.path("suggestedDayOfWeek").isNull()).isTrue();

        // 4. Existing MODIFY_WORKOUT preview flow still intact
        AiMessageResponse modPreview = sendMessage(tokenWithCoach, convId, "Add Bulgarian Split Squat to Wednesday.");
        assertThat(modPreview.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(wednesdayExercises()).containsExactly("Pistol Squat"); // Zero unintended DB mutation
    }

    @Test
    @DisplayName("COACH_INFO retrieves real assigned coach from DB, omits missing fields, rejects unassigned & unauthenticated")
    void coachInfoRetrievalEnforcesAuthAndReturnsRealCoachOrClearUnassignedMessage() throws Exception {
        // 1. Athlete WITH active coach & full profile
        Long coachConv = createConversation(tokenWithCoach);
        for (String query : List.of(
                "Show me my coach information.",
                "Who is my coach?",
                "Show my coach profile.",
                "What do you know about my coach?"
        )) {
            AiMessageResponse res = sendMessage(tokenWithCoach, coachConv, query);
            assertThat(res.getIntent()).isEqualTo("COACH_INFO");
            assertThat(res.getContent()).contains(
                    "Marcus Vance CSCS",
                    "Coach Marcus Vance",
                    "https://cdn.example.com/coach-marcus.png",
                    "CSCS, USA Gymnastics Level 2",
                    "9 years",
                    "Former gymnastics specialist",
                    "Master strict joint alignment"
            );
            assertThat(res.getContent()).doesNotContain(
                    "Calisthenics AI Assistant",
                    "Athlete Secret Handle",
                    "Athlete Private Name",
                    "Super Secret Plan Name"
            );
        }

        // 2. Athlete WITHOUT active assigned coach (only PENDING exists) -> exact clear message
        Long soloConv = createConversation(tokenWithoutCoach);
        AiMessageResponse soloRes = sendMessage(tokenWithoutCoach, soloConv, "Who is my coach?");
        assertThat(soloRes.getIntent()).isEqualTo("COACH_INFO");
        assertThat(soloRes.getContent()).isEqualTo("You don't currently have an assigned coach.");
        assertThat(soloRes.getContent()).doesNotContain("Marcus Vance", "Calisthenics AI Assistant");

        // 3. Athlete WITH sparse coach profile (missing bio optional fields, avatar, experience, certs, philosophy)
        Long sparseConv = createConversation(tokenWithSparseCoach);
        AiMessageResponse sparseRes = sendMessage(tokenWithSparseCoach, sparseConv, "Show my coach profile.");
        assertThat(sparseRes.getIntent()).isEqualTo("COACH_INFO");
        assertThat(sparseRes.getContent()).contains("Coach Elena Minimal", "Calisthenics movement fundamentals.");
        assertThat(sparseRes.getContent()).doesNotContain(
                "Certifications", "Coaching Philosophy", "Specialties", "Profile Picture", "null", "Calisthenics AI Assistant"
        );

        // 4. Unauthenticated request rejected cleanly (401/403)
        mockMvc.perform(post("/api/v1/ai/conversations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendAiMessageRequest("Who is my coach?", "UTC"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Another exercise follow-ups track full conversation history, retain focus, prevent duplicates, exhaust cleanly, and reset on new topic")
    void anotherExercisePreventsDuplicatesAcrossTurnsAndResetsOnNewTopic() throws Exception {
        Long convId = createConversation(tokenWithCoach);
        Set<String> allSeenLegNames = new HashSet<>();

        // Acceptance 1: Ask for leg exercises -> 5 exercises
        AiMessageResponse turn1 = sendMessage(tokenWithCoach, convId, "List me some leg exercises");
        assertThat(turn1.getIntent()).isEqualTo("LIST_EXERCISES");
        List<String> batch1 = extractNumberedNames(turn1.getContent());
        assertThat(batch1).hasSize(5);
        assertThat(allSeenLegNames.addAll(batch1)).isTrue();

        // Acceptance 2 & 3: Ask "Another exercise" -> 6th unseen leg exercise
        AiMessageResponse turn2 = sendMessage(tokenWithCoach, convId, "Another exercise");
        assertThat(turn2.getIntent()).isEqualTo("LIST_EXERCISES");
        assertThat(turn2.getContent()).doesNotContain("Wednesday", "Gaps Detected", "Current Plan Analyzed");
        List<String> batch2 = extractNumberedNames(turn2.getContent());
        assertThat(batch2).hasSize(1);
        assertThat(allSeenLegNames).doesNotContain(batch2.get(0));
        allSeenLegNames.addAll(batch2);

        // Acceptance 3 & 4: Ask "Suggest another one" -> 7th unseen leg exercise
        AiMessageResponse turn3 = sendMessage(tokenWithCoach, convId, "Give me a different leg exercise");
        List<String> batch3 = extractNumberedNames(turn3.getContent());
        assertThat(batch3).hasSize(1);
        assertThat(allSeenLegNames).doesNotContain(batch3.get(0));
        allSeenLegNames.addAll(batch3);
        assertThat(allSeenLegNames).hasSize(7); // All 7 leg exercises covered with 0 duplicates!

        // Acceptance 5: Exhausted -> clear no-more-results message and zero repeated exercises
        AiMessageResponse turn4 = sendMessage(tokenWithCoach, convId, "Anything else?");
        assertThat(turn4.getContent().toLowerCase()).contains("no more suitable");
        for (String seen : allSeenLegNames) {
            assertThat(turn4.getContent()).doesNotContain(seen);
        }

        // Genuinely new recommendation topic resets cleanly -> returns Push/HSPU exercises
        AiMessageResponse pushTurn = sendMessage(tokenWithCoach, convId, "List me some exercises for handstand push-ups");
        assertThat(pushTurn.getIntent()).isEqualTo("LIST_EXERCISES");
        assertThat(pushTurn.getContent()).contains("Pike Push-Up", "Wall Handstand Push-Up");

        // Zero DB workout plan modifications across entire flow
        assertThat(wednesdayExercises()).containsExactly("Pistol Squat");
    }
}
