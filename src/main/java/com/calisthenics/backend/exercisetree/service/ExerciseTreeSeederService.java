package com.calisthenics.backend.exercisetree.service;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercisetree.entity.ExerciseProgressionEdge;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeCategory;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeMissingNode;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeNode;
import com.calisthenics.backend.exercisetree.repository.ExerciseProgressionEdgeRepository;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeCategoryRepository;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeMissingNodeRepository;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeNodeRepository;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.MasteryCriteriaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Idempotently populates Exercise Progression Tree metadata, curated branching relationships,
 * dynamic fallback classification for uncurated catalog entries (e.g., draft or newly added exercises),
 * and missing-node reports — strictly preserving all existing Exercise rows and IDs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Order(50)
public class ExerciseTreeSeederService implements ApplicationRunner {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseTreeCategoryRepository categoryRepository;
    private final ExerciseTreeNodeRepository nodeRepository;
    private final ExerciseProgressionEdgeRepository edgeRepository;
    private final ExerciseTreeMissingNodeRepository missingNodeRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedOrSyncCatalog();
        } catch (Exception ex) {
            log.warn("ExerciseTreeSeederService startup sync skipped or deferred: {}", ex.getMessage());
        }
    }

    @Transactional
    public void ensureSeededIfEmpty() {
        if (categoryRepository.count() == 0 || (nodeRepository.count() == 0 && exerciseRepository.count() > 0)) {
            seedOrSyncCatalog();
        }
    }

    @Transactional
    public SeedSummary seedOrSyncCatalog() {
        int categoriesCreated = seedCategories();
        List<Exercise> allExercises = exerciseRepository.findAll();
        if (allExercises.isEmpty()) {
            return new SeedSummary(categoriesCreated, 0, 0, seedMissingNodes());
        }

        Map<String, Exercise> byCode = new HashMap<>();
        for (Exercise ex : allExercises) {
            if (ex.getCode() != null && !ex.getCode().isBlank()) {
                byCode.put(ex.getCode().trim().toUpperCase(Locale.ROOT), ex);
            }
        }

        int nodesCreated = 0;
        Set<Long> placedExerciseIds = new HashSet<>();

        // 1. Curated placements matched strictly by existing exercise.code
        for (ExerciseTreeCuratedCatalog.NodeSpec spec : ExerciseTreeCuratedCatalog.nodes()) {
            Exercise exercise = byCode.get(spec.exerciseCode().toUpperCase(Locale.ROOT));
            if (exercise == null) {
                continue;
            }
            placedExerciseIds.add(exercise.getId());
            if (!nodeRepository.existsByExerciseIdAndTrainingTypeAndSubcategoryKey(
                    exercise.getId(), spec.trainingType(), spec.subcategoryKey())) {
                ExerciseTreeNode node = ExerciseTreeNode.builder()
                        .exercise(exercise)
                        .trainingType(spec.trainingType())
                        .subcategoryKey(spec.subcategoryKey())
                        .progressionDifficulty(spec.progressionDifficulty())
                        .difficultyValidated(spec.difficultyValidated())
                        .treeTier(ExerciseTreeNode.tierForDifficulty(spec.progressionDifficulty()))
                        .branchKey(spec.branchKey())
                        .branchLabel(spec.branchLabel())
                        .branchOrder(spec.branchOrder())
                        .primaryPlacement(spec.primaryPlacement())
                        .masteryCriteriaType(spec.masteryCriteriaType())
                        .masteryTargetReps(spec.targetReps())
                        .masteryTargetSets(spec.targetSets())
                        .masteryTargetHoldSeconds(spec.targetHoldSeconds())
                        .masteryTargetWeightKg(spec.targetWeightKg())
                        .masteryMinCompletedSessions(spec.minCompletedSessions())
                        .masterySummary(spec.masterySummary())
                        .needsManualReview(spec.needsManualReview())
                        .reviewNotes(spec.reviewNotes())
                        .build();
                nodeRepository.save(node);
                nodesCreated++;
            }
        }

        // 2. Dynamic fallback classification for any remaining existing exercises (drafts, archived, future exercises)
        for (Exercise exercise : allExercises) {
            if (placedExerciseIds.contains(exercise.getId())) {
                continue;
            }
            if (!nodeRepository.findByExerciseId(exercise.getId()).isEmpty()) {
                continue;
            }
            List<ExerciseTreeCategoryType> targetTrees = resolveTargetTrees(exercise.getTrainingType());
            for (ExerciseTreeCategoryType treeType : targetTrees) {
                String subcategory = inferSubcategory(exercise, treeType);
                if (nodeRepository.existsByExerciseIdAndTrainingTypeAndSubcategoryKey(
                        exercise.getId(), treeType, subcategory)) {
                    continue;
                }
                DifficultyLevel diff = exercise.getDifficulty() != null ? exercise.getDifficulty() : DifficultyLevel.BEGINNER;
                boolean isIsometric = exercise.getCategory() == ExerciseCategory.ISOMETRIC
                        || (exercise.getExerciseType() != null && exercise.getExerciseType().toUpperCase(Locale.ROOT).contains("ISOMETRIC"));
                MasteryCriteriaType criteriaType = isIsometric ? MasteryCriteriaType.HOLD_SECONDS : MasteryCriteriaType.REPS;
                Integer targetReps = isIsometric ? null : 10;
                Integer targetHold = isIsometric ? 20 : null;
                String summary = isIsometric
                        ? "3x20s controlled holds across 3 completed workouts (auto-classified)"
                        : "3x10 clean reps across 3 completed workouts (auto-classified)";

                ExerciseTreeNode fallbackNode = ExerciseTreeNode.builder()
                        .exercise(exercise)
                        .trainingType(treeType)
                        .subcategoryKey(subcategory)
                        .progressionDifficulty(diff)
                        .difficultyValidated(false)
                        .treeTier(ExerciseTreeNode.tierForDifficulty(diff))
                        .branchKey("GENERAL_" + subcategory)
                        .branchLabel("General " + formatLabel(subcategory))
                        .branchOrder(99)
                        .primaryPlacement(true)
                        .masteryCriteriaType(criteriaType)
                        .masteryTargetReps(targetReps)
                        .masteryTargetSets(3)
                        .masteryTargetHoldSeconds(targetHold)
                        .masteryMinCompletedSessions(3)
                        .masterySummary(summary)
                        .needsManualReview(true)
                        .reviewNotes("Auto-classified from catalog metadata; verify subcategory, progression difficulty, and edges.")
                        .build();
                nodeRepository.save(fallbackNode);
                nodesCreated++;
            }
        }

        // 3. Curated branching progression edges (only between exercises that actually exist)
        int edgesCreated = 0;
        for (ExerciseTreeCuratedCatalog.EdgeSpec edgeSpec : ExerciseTreeCuratedCatalog.edges()) {
            Exercise source = byCode.get(edgeSpec.sourceCode().toUpperCase(Locale.ROOT));
            Exercise target = byCode.get(edgeSpec.targetCode().toUpperCase(Locale.ROOT));
            if (source == null || target == null || Objects.equals(source.getId(), target.getId())) {
                continue;
            }
            if (!edgeRepository.existsBySourceExerciseIdAndTargetExerciseIdAndTrainingTypeAndRelationType(
                    source.getId(), target.getId(), edgeSpec.trainingType(), edgeSpec.relationType())) {
                ExerciseProgressionEdge edge = ExerciseProgressionEdge.builder()
                        .sourceExercise(source)
                        .targetExercise(target)
                        .trainingType(edgeSpec.trainingType())
                        .relationType(edgeSpec.relationType())
                        .branchLabel(edgeSpec.branchLabel())
                        .notes(edgeSpec.notes())
                        .displayOrder(edgeSpec.displayOrder())
                        .build();
                edgeRepository.save(edge);
                edgesCreated++;
            }
        }

        int missingCreated = seedMissingNodes();
        if (categoriesCreated > 0 || nodesCreated > 0 || edgesCreated > 0 || missingCreated > 0) {
            log.info("ExerciseTreeSeederService synced: categories={}, nodes={}, edges={}, missingReports={}",
                    categoriesCreated, nodesCreated, edgesCreated, missingCreated);
        }
        return new SeedSummary(categoriesCreated, nodesCreated, edgesCreated, missingCreated);
    }

    private int seedCategories() {
        int created = 0;
        for (ExerciseTreeCuratedCatalog.CategorySpec spec : ExerciseTreeCuratedCatalog.categories()) {
            if (categoryRepository.findByTrainingTypeAndSubcategoryKey(spec.trainingType(), spec.subcategoryKey()).isEmpty()) {
                categoryRepository.save(ExerciseTreeCategory.builder()
                        .trainingType(spec.trainingType())
                        .subcategoryKey(spec.subcategoryKey())
                        .displayName(spec.displayName())
                        .description(spec.description())
                        .displayOrder(spec.displayOrder())
                        .active(true)
                        .build());
                created++;
            }
        }
        return created;
    }

    private int seedMissingNodes() {
        int created = 0;
        for (ExerciseTreeCuratedCatalog.MissingNodeSpec spec : ExerciseTreeCuratedCatalog.missingNodes()) {
            if (!missingNodeRepository.existsByTrainingTypeAndSubcategoryKeyAndSuggestedNameIgnoreCase(
                    spec.trainingType(), spec.subcategoryKey(), spec.suggestedName())) {
                missingNodeRepository.save(ExerciseTreeMissingNode.builder()
                        .trainingType(spec.trainingType())
                        .subcategoryKey(spec.subcategoryKey())
                        .branchKey(spec.branchKey())
                        .suggestedName(spec.suggestedName())
                        .targetDifficulty(spec.targetDifficulty())
                        .afterExerciseCode(spec.afterExerciseCode())
                        .beforeExerciseCode(spec.beforeExerciseCode())
                        .reason(spec.reason())
                        .build());
                created++;
            }
        }
        return created;
    }

    private List<ExerciseTreeCategoryType> resolveTargetTrees(ExerciseTrainingType trainingType) {
        if (trainingType == null) {
            return List.of(ExerciseTreeCategoryType.CALISTHENICS);
        }
        return switch (trainingType) {
            case CALISTHENICS -> List.of(ExerciseTreeCategoryType.CALISTHENICS);
            case GYM -> List.of(ExerciseTreeCategoryType.GYM);
            case BOTH -> List.of(ExerciseTreeCategoryType.CALISTHENICS, ExerciseTreeCategoryType.GYM);
        };
    }

    private String inferSubcategory(Exercise exercise, ExerciseTreeCategoryType treeType) {
        String nameUpper = exercise.getName() != null ? exercise.getName().toUpperCase(Locale.ROOT) : "";
        String musclesUpper = exercise.getTargetMuscles() != null ? exercise.getTargetMuscles().toUpperCase(Locale.ROOT) : "";
        ExerciseCategory cat = exercise.getCategory();

        if (treeType == ExerciseTreeCategoryType.CALISTHENICS) {
            if (nameUpper.contains("HANDSTAND") || nameUpper.contains("PIKE PUSH")) {
                return "HANDSTAND";
            }
            if (cat == ExerciseCategory.SKILL || cat == ExerciseCategory.ISOMETRIC) {
                return "STATIC_SKILLS";
            }
            if (cat == ExerciseCategory.PULL || cat == ExerciseCategory.BACK || cat == ExerciseCategory.BICEPS) {
                return "PULL";
            }
            if (cat == ExerciseCategory.CORE) {
                return "CORE";
            }
            if (cat == ExerciseCategory.LEGS || cat == ExerciseCategory.FLEXIBILITY) {
                return "LEGS";
            }
            return "PUSH";
        }

        // GYM tree inference
        if (cat == ExerciseCategory.CHEST) return "CHEST";
        if (cat == ExerciseCategory.BACK || cat == ExerciseCategory.PULL) return "BACK";
        if (cat == ExerciseCategory.SHOULDERS) return "SHOULDERS";
        if (cat == ExerciseCategory.BICEPS) return "BICEPS";
        if (cat == ExerciseCategory.TRICEPS) return "TRICEPS";
        if (cat == ExerciseCategory.CORE) return "CORE";
        if (cat == ExerciseCategory.LEGS) {
            if (musclesUpper.contains("HAMSTRING") || nameUpper.contains("DEADLIFT") || nameUpper.contains("CURL") || nameUpper.contains("GOOD MORNING")) {
                return "HAMSTRINGS";
            }
            if (musclesUpper.contains("GLUTE") || nameUpper.contains("LUNGE") || nameUpper.contains("HIP THRUST") || nameUpper.contains("STEP") || nameUpper.contains("BULGARIAN")) {
                return "GLUTES";
            }
            return "QUADRICEPS";
        }
        if (musclesUpper.contains("CHEST") || musclesUpper.contains("PEC")) return "CHEST";
        if (musclesUpper.contains("BACK") || musclesUpper.contains("LAT")) return "BACK";
        if (musclesUpper.contains("SHOULDER") || musclesUpper.contains("DELT")) return "SHOULDERS";
        if (musclesUpper.contains("BICEP")) return "BICEPS";
        if (musclesUpper.contains("TRICEP")) return "TRICEPS";
        if (musclesUpper.contains("CORE") || musclesUpper.contains("AB")) return "CORE";
        return "QUADRICEPS";
    }

    private String formatLabel(String key) {
        return Arrays.stream(key.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ROOT))
                .reduce((a, b) -> a + " " + b)
                .orElse(key);
    }

    public record SeedSummary(int categoriesCreated, int nodesCreated, int edgesCreated, int missingNodesCreated) {}
}
