package com.calisthenics.backend.exercisetree;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercisetree.dto.AdminExerciseTreeAuditResponse;
import com.calisthenics.backend.exercisetree.dto.CreateExerciseProgressionEdgeRequest;
import com.calisthenics.backend.exercisetree.dto.UpdateExerciseTreeNodeRequest;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeNode;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeNodeRepository;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeSeederService;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeService;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExerciseTreeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExerciseTreeNodeRepository nodeRepository;

    @Autowired
    private ExerciseTreeSeederService seederService;

    @Autowired
    private ExerciseTreeService exerciseTreeService;

    @Autowired
    private ObjectMapper objectMapper;

    private Exercise plancheLean;
    private Exercise tuckPlanche;
    private Exercise advTuckPlanche;
    private Exercise straddlePlanche;
    private Exercise fullPlanche;
    private Exercise pullUp;
    private Exercise highPullUp;
    private Exercise straightBarDip;
    private Exercise muscleUp;
    private Exercise machineChestPress;
    private Exercise benchPress;
    private Exercise weightedPullUp;
    private Exercise draftExercise;

    @BeforeEach
    void setUp() {
        plancheLean = saveIfMissing("PLANCHE_LEAN", "Planche Lean", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.ISOMETRIC, DifficultyLevel.BEGINNER, ExerciseStatus.PUBLISHED);
        tuckPlanche = saveIfMissing("TUCK_PLANCHE", "Tuck Planche", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.SKILL, DifficultyLevel.INTERMEDIATE, ExerciseStatus.PUBLISHED);
        advTuckPlanche = saveIfMissing("ADV_TUCK_PLANCHE", "Advanced Tuck Planche", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.SKILL, DifficultyLevel.ADVANCED, ExerciseStatus.PUBLISHED);
        straddlePlanche = saveIfMissing("STRADDLE_PLANCHE", "Straddle Planche", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.SKILL, DifficultyLevel.ADVANCED, ExerciseStatus.PUBLISHED);
        fullPlanche = saveIfMissing("FULL_PLANCHE", "Full Planche", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.SKILL, DifficultyLevel.ELITE, ExerciseStatus.PUBLISHED);

        pullUp = saveIfMissing("PULL_UP", "Pull-Up", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE, ExerciseStatus.PUBLISHED);
        highPullUp = saveIfMissing("HIGH_PULL_UP", "High Pull-Up", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE, ExerciseStatus.PUBLISHED);
        straightBarDip = saveIfMissing("STRAIGHT_BAR_DIP", "Straight Bar Dip", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.PUSH, DifficultyLevel.INTERMEDIATE, ExerciseStatus.PUBLISHED);
        muscleUp = saveIfMissing("MUSCLE_UP", "Muscle-Up", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.PULL, DifficultyLevel.ADVANCED, ExerciseStatus.PUBLISHED);

        machineChestPress = saveIfMissing("MACHINE_CHEST_PRESS", "Seated Machine Chest Press", ExerciseTrainingType.GYM, ExerciseCategory.CHEST, DifficultyLevel.BEGINNER, ExerciseStatus.PUBLISHED);
        benchPress = saveIfMissing("BENCH_PRESS", "Barbell Bench Press", ExerciseTrainingType.GYM, ExerciseCategory.CHEST, DifficultyLevel.INTERMEDIATE, ExerciseStatus.PUBLISHED);
        weightedPullUp = saveIfMissing("WEIGHTED_PULL_UP", "Weighted Pull-Up", ExerciseTrainingType.BOTH, ExerciseCategory.PULL, DifficultyLevel.ADVANCED, ExerciseStatus.PUBLISHED);
        draftExercise = saveIfMissing("DRAFT_UNPUBLISHED_DRILL", "Draft Unpublished Drill", ExerciseTrainingType.CALISTHENICS, ExerciseCategory.PUSH, DifficultyLevel.BEGINNER, ExerciseStatus.DRAFT);
    }

    @Test
    void seederNeverCreatesDuplicatesOrMutatesExistingExercisesAndPreservesIds() {
        Map<String, Long> beforeIdsByCode = exerciseRepository.findAll().stream()
                .collect(Collectors.toMap(Exercise::getCode, Exercise::getId));
        long countBefore = exerciseRepository.count();

        seederService.seedOrSyncCatalog();
        // Run twice to verify full idempotency
        seederService.seedOrSyncCatalog();

        long countAfter = exerciseRepository.count();
        Map<String, Long> afterIdsByCode = exerciseRepository.findAll().stream()
                .collect(Collectors.toMap(Exercise::getCode, Exercise::getId));

        assertThat(countAfter).isEqualTo(countBefore);
        assertThat(afterIdsByCode).isEqualTo(beforeIdsByCode);
        // BOTH trainingType exercise appears in both Calisthenics and Gym trees with the same exerciseId
        List<ExerciseTreeNode> weightedNodes = nodeRepository.findByExerciseId(weightedPullUp.getId());
        assertThat(weightedNodes).extracting(ExerciseTreeNode::getTrainingType)
                .containsExactlyInAnyOrder(ExerciseTreeCategoryType.CALISTHENICS, ExerciseTreeCategoryType.GYM);
    }

    @Test
    void categoriesAndTreeEndpointsReturnRealServerExercisesBranchingEdgesAndMissingNodeReports() throws Exception {
        seederService.seedOrSyncCatalog();
        long expectedPublished = exerciseRepository.findAll().stream()
                .filter(e -> e.isActive() && e.getStatus() == ExerciseStatus.PUBLISHED)
                .count();

        mockMvc.perform(get("/api/v1/exercise-trees/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calisthenicsCategories.length()").value(6))
                .andExpect(jsonPath("$.gymCategories.length()").value(9))
                .andExpect(jsonPath("$.totalServerExercises").value((int) expectedPublished));

        // Alias /api/exercise-trees also works
        mockMvc.perform(get("/api/exercise-trees").param("category", "CALISTHENICS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingType").value("CALISTHENICS"))
                .andExpect(jsonPath("$.nodes[?(@.exerciseId == " + fullPlanche.getId() + ")].progressionDifficulty").value("ELITE"))
                .andExpect(jsonPath("$.nodes[?(@.exerciseId == " + draftExercise.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.edges[?(@.sourceExerciseId == " + plancheLean.getId() + " && @.targetExerciseId == " + tuckPlanche.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.missingNodes").isNotEmpty());

        // Subcategory route & difficulty filter
        mockMvc.perform(get("/api/v1/exercise-trees/CALISTHENICS/STATIC_SKILLS")
                        .param("difficulty", "ELITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subcategoryKey").value("STATIC_SKILLS"))
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.nodes[0].exerciseId").value(fullPlanche.getId()));

        // Gym route & search filter
        mockMvc.perform(get("/api/v1/exercise-trees/GYM/CHEST").param("search", "Bench"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.nodes[0].exerciseId").value(benchPress.getId()));
    }

    @Test
    void exerciseDetailReturnsMultiBranchPrerequisitesNextPreviousAndAiContextWithRealIds() throws Exception {
        seederService.seedOrSyncCatalog();

        // Muscle-Up converges Pull-Up (PREREQUISITE) + Straight Bar Dip (PREREQUISITE) + High Pull-Up (NEXT -> previous)
        mockMvc.perform(get("/api/v1/exercise-trees/exercises/{id}", muscleUp.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(muscleUp.getId()))
                .andExpect(jsonPath("$.previousProgressions[?(@.exerciseId == " + highPullUp.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.prerequisites[?(@.exerciseId == " + pullUp.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.prerequisites[?(@.exerciseId == " + straightBarDip.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.aiPromptSuggestions").isNotEmpty());

        String aiContext = exerciseTreeService.buildAiProgressionContextForExercise(muscleUp.getId()).orElseThrow();
        assertThat(aiContext).contains("exerciseId=" + muscleUp.getId());
        assertThat(aiContext).contains("exerciseId=" + pullUp.getId());
        assertThat(aiContext).contains("exerciseId=" + straightBarDip.getId());
        assertThat(aiContext).contains("NEVER invent nonexistent exercise IDs");
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void normalUserIsForbiddenFromAdminExerciseTreeAuditAndMutations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exercise-trees/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminAuditReportsAllDbExercisesAndNodeUpdatesRespectCatalogDifficultySafeguard() throws Exception {
        seederService.seedOrSyncCatalog();
        long expectedTotal = exerciseRepository.count();
        long expectedPublished = exerciseRepository.findAll().stream()
                .filter(e -> e.isActive() && e.getStatus() == ExerciseStatus.PUBLISHED)
                .count();
        long expectedNonPublished = expectedTotal - expectedPublished;

        mockMvc.perform(get("/api/v1/admin/exercise-trees/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExistingExercises").value((int) expectedTotal))
                .andExpect(jsonPath("$.publishedExercises").value((int) expectedPublished))
                .andExpect(jsonPath("$.nonPublishedOrInactiveExercises").value((int) expectedNonPublished))
                .andExpect(jsonPath("$.exercisesClassified").value((int) expectedTotal))
                .andExpect(jsonPath("$.missingProgressionNodesCount").isNumber());

        AdminExerciseTreeAuditResponse audit = exerciseTreeService.getAuditReport();
        assertThat(audit.getExercisesMissingMetadataCount()).isGreaterThan(0);

        ExerciseTreeNode plancheNode = nodeRepository.findByExerciseIdAndTrainingTypeAndSubcategoryKey(
                straddlePlanche.getId(), ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS").orElseThrow();

        // Update progression difficulty WITHOUT syncCatalogDifficulty -> catalog Exercise.difficulty stays untouched
        UpdateExerciseTreeNodeRequest reqNoSync = UpdateExerciseTreeNodeRequest.builder()
                .progressionDifficulty(DifficultyLevel.ELITE)
                .difficultyValidated(true)
                .syncCatalogDifficulty(false)
                .needsManualReview(false)
                .reviewNotes("Validated by coach")
                .build();

        mockMvc.perform(put("/api/v1/admin/exercise-trees/nodes/{nodeId}", plancheNode.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNoSync)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressionDifficulty").value("ELITE"))
                .andExpect(jsonPath("$.catalogDifficulty").value("ADVANCED"));

        assertThat(exerciseRepository.findById(straddlePlanche.getId()).orElseThrow().getDifficulty())
                .isEqualTo(DifficultyLevel.ADVANCED);

        // Create and delete custom edge
        CreateExerciseProgressionEdgeRequest edgeReq = CreateExerciseProgressionEdgeRequest.builder()
                .sourceExerciseId(machineChestPress.getId())
                .targetExerciseId(muscleUp.getId())
                .trainingType(ExerciseTreeCategoryType.GYM)
                .relationType(ProgressionRelationType.RELATED_SKILL)
                .branchLabel("Cross-Training")
                .build();

        String createdJson = mockMvc.perform(post("/api/v1/admin/exercise-trees/edges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edgeReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long edgeId = objectMapper.readTree(createdJson).get("edgeId").asLong();

        mockMvc.perform(delete("/api/v1/admin/exercise-trees/edges/{edgeId}", edgeId))
                .andExpect(status().isNoContent());
    }

    private Exercise saveIfMissing(
            String code,
            String name,
            ExerciseTrainingType trainingType,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            ExerciseStatus status
    ) {
        return exerciseRepository.findByCode(code).orElseGet(() -> exerciseRepository.save(Exercise.builder()
                .code(code)
                .name(name)
                .trainingType(trainingType)
                .category(category)
                .difficulty(difficulty)
                .status(status)
                .origin(ExerciseOrigin.MANUAL)
                .active(status == ExerciseStatus.PUBLISHED)
                .targetMuscles("Chest, Shoulders")
                .build()));
    }
}
