package com.calisthenics.backend.skill;

import com.calisthenics.backend.adminai.dto.AdminTrainingAnalyticsResponse;
import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.adminai.service.ExerciseRecommendationService;
import com.calisthenics.backend.adminai.service.TrainingAnalyticsService;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.aichat.service.AiWorkoutRecommendationService;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.skill.dto.SkillDetailResponse;
import com.calisthenics.backend.skill.entity.Skill;
import com.calisthenics.backend.skill.entity.SkillExerciseLink;
import com.calisthenics.backend.skill.entity.SkillRelationType;
import com.calisthenics.backend.skill.repository.SkillExerciseLinkRepository;
import com.calisthenics.backend.skill.repository.SkillRepository;
import com.calisthenics.backend.skill.service.SkillCatalogBootstrapInitializer;
import com.calisthenics.backend.skill.service.SkillCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SkillCatalogControllerAndAiIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SkillCatalogService skillCatalogService;

    @Autowired
    private SkillCatalogBootstrapInitializer skillCatalogBootstrapInitializer;

    @BeforeEach
    void setUpCatalog() {
        skillCatalogBootstrapInitializer.seedFullCatalogWithBaselineExercises();
    }

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillExerciseLinkRepository skillExerciseLinkRepository;

    @Autowired
    private ExerciseRecommendationService exerciseRecommendationService;

    @Autowired
    private AiWorkoutRecommendationService aiWorkoutRecommendationService;

    @Autowired
    private TrainingAnalyticsService adminTrainingAnalyticsService;

    @Test
    void skillCatalogEndpointsServeBackendCategoriesAndSkillsSeparatedByTrainingType() throws Exception {
        mockMvc.perform(get("/api/v1/skills/catalog").param("trainingType", "CALISTHENICS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(6))
                .andExpect(jsonPath("$.categories[0].displayName").value("Push"))
                .andExpect(jsonPath("$.skills[?(@.trainingType != 'CALISTHENICS')]").isEmpty())
                .andExpect(jsonPath("$.skills[?(@.code == 'CAL_PLANCHE')].videoAnalysisSupported").value(true));

        mockMvc.perform(get("/api/v1/skills/catalog").param("trainingType", "GYM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(6))
                .andExpect(jsonPath("$.categories[?(@.categoryKey == 'SHOULDERS_AND_ARMS')].displayName").value("Shoulders & Arms"))
                .andExpect(jsonPath("$.skills[?(@.trainingType != 'GYM')]").isEmpty())
                .andExpect(jsonPath("$.skills[?(@.code == 'GYM_BENCH_PRESS')].videoAnalysisSupported").value(false));

        // Multi-category lookup: Muscle-up appears under both PULL and SKILLS
        mockMvc.perform(get("/api/v1/skills/catalog")
                        .param("trainingType", "CALISTHENICS")
                        .param("category", "SKILLS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[?(@.code == 'CAL_MUSCLE_UP')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/skills/catalog")
                        .param("trainingType", "CALISTHENICS")
                        .param("category", "PULL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[?(@.code == 'CAL_MUSCLE_UP')]").isNotEmpty());

        // Legacy code alias resolves seamlessly
        mockMvc.perform(get("/api/v1/skills/PLANCHE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CAL_PLANCHE"))
                .andExpect(jsonPath("$.progressionLevels.length()").value(5));
    }

    @Test
    void skillCatalogStrictlyPreventsGymExercisesFromAppearingInsideCalisthenicsSkillsEvenWithBadLink() {
        Exercise gymPress = exerciseRepository.save(Exercise.builder()
                .name("Cable Chest Fly Test")
                .code("CABLE_CHEST_FLY_TEST")
                .category(ExerciseCategory.CHEST)
                .difficulty(DifficultyLevel.BEGINNER)
                .trainingType(ExerciseTrainingType.GYM)
                .equipment("Cable Machine")
                .description("Cable machine fly")
                .instructions("Pull handles together")
                .active(true)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .build());

        Skill planche = skillRepository.findByCodeIgnoreCase("CAL_PLANCHE").orElseThrow();
        skillExerciseLinkRepository.save(SkillExerciseLink.builder()
                .skill(planche)
                .exercise(gymPress)
                .relationType(SkillRelationType.PRIMARY)
                .displayOrder(99)
                .build());

        SkillDetailResponse detail = skillCatalogService.getSkillDetail("CAL_PLANCHE", null, null);
        assertThat(detail.getRelatedExercises())
                .extracting("code")
                .doesNotContain("CABLE_CHEST_FLY_TEST");

        // Conversely, unlinked Calisthenics movement attaches only to Calisthenics, never to Gym
        Exercise calRing = exerciseRepository.save(Exercise.builder()
                .name("Ring Maltese Press Test")
                .code("RING_MALTESE_PRESS_TEST")
                .category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.ELITE)
                .trainingType(ExerciseTrainingType.CALISTHENICS)
                .equipment("Gymnastic Rings")
                .description("Advanced straight-arm ring maltese strength hold")
                .instructions("Hold body horizontal with wide ring support")
                .active(true)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .build());

        SkillDetailResponse benchDetail = skillCatalogService.getSkillDetail("GYM_BENCH_PRESS", null, null);
        assertThat(benchDetail.getRelatedExercises())
                .extracting("id")
                .doesNotContain(calRing.getId());
    }

    @Test
    void aiRecommendationAndAnalyticsEnforceTrainingTypeBoundaries() {
        List<ExerciseRecommendationPayload> calRecs = exerciseRecommendationService
                .analyzeMissingExercises(null, null, ExerciseTrainingType.CALISTHENICS, null);
        assertThat(calRecs).noneMatch(p -> p.getTrainingType() == ExerciseTrainingType.GYM);

        List<ExerciseRecommendationPayload> gymRecs = exerciseRecommendationService
                .analyzeMissingExercises(null, null, ExerciseTrainingType.GYM, null);
        assertThat(gymRecs).isNotEmpty();
        assertThat(gymRecs).allMatch(p -> p.getTrainingType() == ExerciseTrainingType.GYM);

        // AI Chat recommendation query for Planche excludes GYM exercises
        AiWorkoutRecommendationService.RecommendationOutcome plancheRecs = aiWorkoutRecommendationService
                .listExercises(null, "List planche progression exercises", List.of(), false, false);
        assertThat(plancheRecs.reply()).doesNotContain("Barbell Bench Press");
        assertThat(plancheRecs.reply()).doesNotContain("Lat Pulldown");

        // AI Chat recommendation query for Gym excludes Calisthenics-only skills like Planche / Front Lever
        AiWorkoutRecommendationService.RecommendationOutcome gymRecsChat = aiWorkoutRecommendationService
                .listExercises(null, "Give me barbell bench press and lat pulldown gym exercises", List.of(), false, false);
        assertThat(gymRecsChat.reply()).doesNotContain("Planche");
        assertThat(gymRecsChat.reply()).doesNotContain("Front Lever");

        // Admin analytics exposes both Calisthenics and Gym skills with distinct trainingType
        AdminTrainingAnalyticsResponse gymAnalytics = adminTrainingAnalyticsService
                .getTrainingAnalytics(AdminAnalyticsRange.LAST_30_DAYS, null, null, ExerciseTrainingType.GYM);
        assertThat(gymAnalytics.getSkillFocusBreakdown()).isNotEmpty();
        assertThat(gymAnalytics.getSkillFocusBreakdown())
                .allMatch(stat -> "GYM".equals(stat.getTrainingType()));
    }
}
