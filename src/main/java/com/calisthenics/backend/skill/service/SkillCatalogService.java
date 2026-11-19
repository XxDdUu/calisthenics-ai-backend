package com.calisthenics.backend.skill.service;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseMediaMapper;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.skill.dto.SkillCatalogResponse;
import com.calisthenics.backend.skill.dto.SkillCategoryDto;
import com.calisthenics.backend.skill.dto.SkillDetailResponse;
import com.calisthenics.backend.skill.dto.SkillExerciseDto;
import com.calisthenics.backend.skill.dto.SkillProgressionLevelDetailDto;
import com.calisthenics.backend.skill.dto.SkillProgressionLevelSummaryDto;
import com.calisthenics.backend.skill.dto.SkillReferenceDto;
import com.calisthenics.backend.skill.dto.SkillSummaryDto;
import com.calisthenics.backend.skill.dto.TrainingTypeSummaryDto;
import com.calisthenics.backend.skill.entity.Skill;
import com.calisthenics.backend.skill.entity.SkillCategoryEntity;
import com.calisthenics.backend.skill.entity.SkillCategoryMembership;
import com.calisthenics.backend.skill.entity.SkillExerciseLink;
import com.calisthenics.backend.skill.entity.SkillProgressionLevel;
import com.calisthenics.backend.skill.entity.SkillRelationType;
import com.calisthenics.backend.skill.repository.SkillCategoryMembershipRepository;
import com.calisthenics.backend.skill.repository.SkillCategoryRepository;
import com.calisthenics.backend.skill.repository.SkillExerciseLinkRepository;
import com.calisthenics.backend.skill.repository.SkillProgressionLevelRepository;
import com.calisthenics.backend.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SkillCatalogService {

    private static final List<String> GYM_EQUIPMENT_MARKERS = List.of(
            "barbell", "dumbbell", "cable", "machine", "smith", "leg press", "leg extension",
            "leg curl", "lat pulldown", "pec deck", "hack squat", "ez bar", "kettlebell", "sled"
    );

    private static final Map<String, String> LEGACY_CODE_TO_SKILL_CODE = Map.ofEntries(
            Map.entry("PLANCHE", "CAL_PLANCHE"),
            Map.entry("FRONT_LEVER", "CAL_FRONT_LEVER"),
            Map.entry("BACK_LEVER", "CAL_BACK_LEVER"),
            Map.entry("HANDSTAND", "CAL_HANDSTAND"),
            Map.entry("HUMAN_FLAG", "CAL_HUMAN_FLAG"),
            Map.entry("MUSCLE_UP", "CAL_MUSCLE_UP"),
            Map.entry("PIKE_PUSH_UP", "CAL_HSPU"),
            Map.entry("HSPU", "CAL_HSPU"),
            Map.entry("PUSH_UP", "CAL_PUSH_UP_PROGRESSIONS"),
            Map.entry("DIPS", "CAL_DIP_PROGRESSIONS"),
            Map.entry("PULL_UP", "CAL_ONE_ARM_PULL_UP"),
            Map.entry("ONE_ARM_PULL_UP", "CAL_ONE_ARM_PULL_UP"),
            Map.entry("L_SIT", "CAL_L_SIT"),
            Map.entry("V_SIT", "CAL_V_SIT"),
            Map.entry("DRAGON_FLAG", "CAL_DRAGON_FLAG"),
            Map.entry("PISTOL_SQUAT", "CAL_PISTOL_SQUAT"),
            Map.entry("SHRIMP_SQUAT", "CAL_SHRIMP_SQUAT"),
            Map.entry("NORDIC_CURL", "CAL_NORDIC_CURL"),
            Map.entry("SQUAT", "CAL_PISTOL_SQUAT"),
            Map.entry("BENCH_PRESS", "GYM_BENCH_PRESS"),
            Map.entry("INCLINE_BENCH_PRESS", "GYM_INCLINE_BENCH_PRESS"),
            Map.entry("OVERHEAD_PRESS", "GYM_OVERHEAD_PRESS"),
            Map.entry("MACHINE_CHEST_PRESS", "GYM_MACHINE_CHEST_PRESS"),
            Map.entry("BARBELL_ROW", "GYM_BARBELL_ROW"),
            Map.entry("LAT_PULLDOWN", "GYM_LAT_PULLDOWN"),
            Map.entry("SEATED_CABLE_ROW", "GYM_SEATED_CABLE_ROW"),
            Map.entry("WEIGHTED_PULL_UP", "GYM_WEIGHTED_PULL_UP"),
            Map.entry("BARBELL_SQUAT", "GYM_BARBELL_SQUAT"),
            Map.entry("ROMANIAN_DEADLIFT", "GYM_ROMANIAN_DEADLIFT"),
            Map.entry("LEG_PRESS", "GYM_LEG_PRESS"),
            Map.entry("LEG_EXTENSION", "GYM_LEG_EXTENSION"),
            Map.entry("LEG_CURL", "GYM_LEG_CURL"),
            Map.entry("CALF_RAISE", "GYM_CALF_RAISE"),
            Map.entry("CABLE_CRUNCH", "GYM_CABLE_CRUNCH"),
            Map.entry("HANGING_LEG_RAISE", "GYM_HANGING_LEG_RAISE"),
            Map.entry("AB_WHEEL", "GYM_AB_WHEEL"),
            Map.entry("LATERAL_RAISE", "GYM_LATERAL_RAISE"),
            Map.entry("BICEP_CURL", "GYM_BICEP_CURL"),
            Map.entry("TRICEPS_EXTENSION", "GYM_TRICEPS_EXTENSION"),
            Map.entry("FACE_PULL", "GYM_FACE_PULL")
    );

    private static final Map<String, String> SKILL_TO_LIVE_ANALYZER_CODE = Map.of(
            "CAL_PLANCHE", "PLANCHE",
            "CAL_FRONT_LEVER", "FRONT_LEVER",
            "CAL_HANDSTAND", "HANDSTAND",
            "CAL_ONE_ARM_PULL_UP", "PULL_UP",
            "CAL_DIP_PROGRESSIONS", "DIPS",
            "CAL_MUSCLE_UP", "MUSCLE_UP",
            "CAL_PUSH_UP_PROGRESSIONS", "PUSH_UP",
            "CAL_PISTOL_SQUAT", "SQUAT",
            "CAL_HSPU", "PIKE_PUSH_UP",
            "CAL_L_SIT", "L_SIT"
    );

    private final SkillCategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final SkillCategoryMembershipRepository membershipRepository;
    private final SkillProgressionLevelRepository progressionLevelRepository;
    private final SkillExerciseLinkRepository exerciseLinkRepository;
    private final ExerciseRepository exerciseRepository;
    private final MuscleTaxonomyService muscleTaxonomyService;

    @Transactional(readOnly = true)
    public SkillCatalogResponse getCatalog(
            ExerciseTrainingType trainingTypeFilter,
            String categoryFilter,
            String searchQuery,
            DifficultyLevel difficultyFilter
    ) {
        CatalogSnapshot snapshot = buildSnapshot();
        ExerciseTrainingType normalizedTypeFilter = normalizeTrainingTypeFilter(trainingTypeFilter);
        String normalizedCategory = normalizeKey(categoryFilter);
        String normalizedSearch = normalizeSearch(searchQuery);

        List<TrainingTypeSummaryDto> trainingTypes = buildTrainingTypeSummaries(snapshot);

        List<SkillCategoryDto> categoryDtos = snapshot.categories.stream()
                .filter(cat -> normalizedTypeFilter == null || cat.getTrainingType() == normalizedTypeFilter)
                .map(cat -> toCategoryDto(cat, snapshot))
                .toList();

        List<SkillSummaryDto> skillDtos = snapshot.skills.stream()
                .filter(skill -> normalizedTypeFilter == null || skill.getTrainingType() == normalizedTypeFilter)
                .filter(skill -> matchesCategoryFilter(skill, normalizedCategory, snapshot))
                .filter(skill -> matchesDifficultyFilter(skill, difficultyFilter, snapshot))
                .filter(skill -> matchesSearchQuery(skill, normalizedSearch, snapshot))
                .map(skill -> toSkillSummaryDto(skill, snapshot))
                .toList();

        Set<Long> uniqueExerciseIds = new HashSet<>();
        for (SkillSummaryDto dto : skillDtos) {
            for (ResolvedExerciseLink link : snapshot.linksBySkillId.getOrDefault(dto.getId(), List.of())) {
                uniqueExerciseIds.add(link.exercise().getId());
            }
        }

        return SkillCatalogResponse.builder()
                .trainingTypes(trainingTypes)
                .categories(categoryDtos)
                .skills(skillDtos)
                .totalCategories(categoryDtos.size())
                .totalSkills(skillDtos.size())
                .totalLinkedExercises(uniqueExerciseIds.size())
                .build();
    }

    @Transactional(readOnly = true)
    public List<SkillCategoryDto> getCategories(ExerciseTrainingType trainingTypeFilter) {
        CatalogSnapshot snapshot = buildSnapshot();
        ExerciseTrainingType normalizedTypeFilter = normalizeTrainingTypeFilter(trainingTypeFilter);
        return snapshot.categories.stream()
                .filter(cat -> normalizedTypeFilter == null || cat.getTrainingType() == normalizedTypeFilter)
                .map(cat -> toCategoryDto(cat, snapshot))
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillDetailResponse getSkillDetail(
            String codeOrId,
            DifficultyLevel difficultyFilter,
            String searchQuery
    ) {
        CatalogSnapshot snapshot = buildSnapshot();
        Skill skill = resolveSkill(codeOrId, snapshot)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + codeOrId));

        String normalizedSearch = normalizeSearch(searchQuery);
        List<SkillProgressionLevel> levels = snapshot.levelsBySkillId.getOrDefault(skill.getId(), List.of());
        List<ResolvedExerciseLink> allSkillLinks = snapshot.linksBySkillId.getOrDefault(skill.getId(), List.of());

        List<SkillExerciseDto> filteredExercises = allSkillLinks.stream()
                .filter(link -> difficultyFilter == null || link.exercise().getDifficulty() == difficultyFilter)
                .filter(link -> matchesExerciseSearch(link.exercise(), link.progressionLevel(), normalizedSearch))
                .map(link -> toSkillExerciseDto(link, snapshot))
                .sorted(skillExerciseComparator())
                .toList();

        Map<Long, List<SkillExerciseDto>> exercisesByLevelId = new LinkedHashMap<>();
        for (SkillExerciseDto exDto : filteredExercises) {
            Long levelKey = resolveLevelIdForDto(exDto, levels);
            exercisesByLevelId.computeIfAbsent(levelKey, ignored -> new ArrayList<>()).add(exDto);
        }

        List<SkillProgressionLevelDetailDto> levelDetails = new ArrayList<>();
        for (SkillProgressionLevel level : levels) {
            if (difficultyFilter != null && level.getDifficulty() != difficultyFilter) {
                List<SkillExerciseDto> levelMatches = exercisesByLevelId.getOrDefault(level.getId(), List.of());
                if (levelMatches.isEmpty()) {
                    continue;
                }
            }
            List<SkillExerciseDto> levelExercises = exercisesByLevelId.getOrDefault(level.getId(), List.of());
            levelDetails.add(SkillProgressionLevelDetailDto.builder()
                    .id(level.getId())
                    .levelNumber(level.getLevelNumber())
                    .code(level.getCode())
                    .title(level.getTitle())
                    .difficulty(level.getDifficulty())
                    .targetCriteria(level.getTargetCriteria())
                    .description(level.getDescription())
                    .exercises(levelExercises)
                    .build());
        }

        List<String> categoryKeys = snapshot.categoryKeysBySkillId.getOrDefault(
                skill.getId(),
                List.of(skill.getCategoryKey())
        );

        String analyzerCode = resolveSkillLiveAnalysisCode(skill);

        return SkillDetailResponse.builder()
                .id(skill.getId())
                .code(skill.getCode())
                .name(skill.getName())
                .trainingType(skill.getTrainingType())
                .categoryKey(skill.getCategoryKey())
                .categoryDisplayName(resolveCategoryDisplayName(skill.getTrainingType(), skill.getCategoryKey(), snapshot))
                .categoryKeys(categoryKeys)
                .subcategory(skill.getSubcategory())
                .description(skill.getDescription())
                .videoAnalysisSupported(analyzerCode != null)
                .liveAnalysisExerciseCode(analyzerCode)
                .progressionLevels(levelDetails)
                .relatedExercises(filteredExercises)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SkillReferenceDto> getSkillsByExerciseId(Long exerciseId) {
        if (exerciseId == null) {
            return List.of();
        }
        CatalogSnapshot snapshot = buildSnapshot();
        return List.copyOf(snapshot.skillRefsByExerciseId.getOrDefault(exerciseId, List.of()));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<SkillReferenceDto>> getSkillReferencesByExerciseIds(Collection<Long> exerciseIds) {
        if (exerciseIds == null || exerciseIds.isEmpty()) {
            return Map.of();
        }
        CatalogSnapshot snapshot = buildSnapshot();
        Map<Long, List<SkillReferenceDto>> result = new HashMap<>();
        for (Long id : exerciseIds) {
            List<SkillReferenceDto> refs = snapshot.skillRefsByExerciseId.get(id);
            result.put(id, refs == null ? List.of() : List.copyOf(refs));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<SkillSummaryDto> getActiveSkillSummaries(ExerciseTrainingType trainingTypeFilter, String categoryFilter) {
        CatalogSnapshot snapshot = buildSnapshot();
        ExerciseTrainingType normalizedTypeFilter = normalizeTrainingTypeFilter(trainingTypeFilter);
        String normalizedCategory = normalizeKey(categoryFilter);
        return snapshot.skills.stream()
                .filter(skill -> normalizedTypeFilter == null || skill.getTrainingType() == normalizedTypeFilter)
                .filter(skill -> matchesCategoryFilter(skill, normalizedCategory, snapshot))
                .map(skill -> toSkillSummaryDto(skill, snapshot))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Exercise> getCompatibleExercisesForSkill(SkillSummaryDto skillSummary, List<Exercise> allExercises) {
        if (skillSummary == null || allExercises == null) {
            return List.of();
        }
        CatalogSnapshot snapshot = buildSnapshot();
        Set<Long> linkedIds = new HashSet<>();
        for (ResolvedExerciseLink link : snapshot.linksBySkillId.getOrDefault(skillSummary.getId(), List.of())) {
            linkedIds.add(link.exercise().getId());
        }
        return allExercises.stream()
                .filter(ex -> isExerciseCompatibleWithTrainingType(ex, skillSummary.getTrainingType()))
                .filter(ex -> linkedIds.contains(ex.getId()))
                .toList();
    }

    public static boolean isExerciseCompatibleWithTrainingType(Exercise exercise, ExerciseTrainingType targetType) {
        if (exercise == null || targetType == null || targetType == ExerciseTrainingType.BOTH) {
            return false;
        }
        ExerciseTrainingType effectiveType = resolveEffectiveTrainingType(exercise);
        if (effectiveType == ExerciseTrainingType.BOTH) {
            return true;
        }
        return effectiveType == targetType;
    }

    public static ExerciseTrainingType resolveEffectiveTrainingType(Exercise exercise) {
        if (exercise == null) {
            return ExerciseTrainingType.CALISTHENICS;
        }
        if (exercise.getTrainingType() != null) {
            return exercise.getTrainingType();
        }
        return containsGymEquipmentMarker(exercise) ? ExerciseTrainingType.GYM : ExerciseTrainingType.CALISTHENICS;
    }

    public static String resolveAnalysisExerciseCode(Exercise exercise) {
        if (exercise == null) {
            return null;
        }
        if (resolveEffectiveTrainingType(exercise) == ExerciseTrainingType.GYM) {
            return null;
        }
        String code = normalizeCode(exercise.getCode());
        String name = normalizeLower(exercise.getName());
        if (code.equals("PLANCHE") || code.contains("PLANCHE") || name.contains("planche") || name.contains("frog stand") || name.contains("maltese")) {
            if (code.contains("PUSH_UP") || name.contains("push-up") || name.contains("push up")) {
                return "PUSH_UP";
            }
            return "PLANCHE";
        }
        if (code.equals("PIKE_PUSH_UP") || code.contains("PIKE_PUSH_UP") || code.contains("HSPU")
                || name.contains("pike push") || name.contains("handstand push")) {
            return "PIKE_PUSH_UP";
        }
        if (code.equals("HANDSTAND") || code.contains("HANDSTAND") || name.contains("handstand")) {
            return "HANDSTAND";
        }
        if (code.equals("FRONT_LEVER") || code.contains("FRONT_LEVER") || name.contains("front lever")) {
            return "FRONT_LEVER";
        }
        if (code.equals("MUSCLE_UP") || code.contains("MUSCLE_UP") || name.contains("muscle-up") || name.contains("muscle up")) {
            return "MUSCLE_UP";
        }
        if (code.equals("PULL_UP") || code.contains("PULL_UP") || code.contains("CHIN_UP")
                || name.contains("pull-up") || name.contains("pull up") || name.contains("chin-up")) {
            if (containsGymEquipmentMarker(exercise) && !code.equals("WEIGHTED_PULL_UP")) {
                return null;
            }
            return "PULL_UP";
        }
        if (code.equals("DIPS") || code.contains("DIP") || name.contains("dip")) {
            return "DIPS";
        }
        if (code.equals("PUSH_UP") || code.contains("PUSH_UP") || name.contains("push-up") || name.contains("push up")) {
            return "PUSH_UP";
        }
        if (code.equals("L_SIT") || code.contains("L_SIT") || name.contains("l-sit") || name.contains("l sit")) {
            return "L_SIT";
        }
        if (code.equals("SQUAT") || code.equals("PISTOL_SQUAT") || code.equals("SHRIMP_SQUAT")
                || (!containsGymEquipmentMarker(exercise) && (name.equals("squat") || name.contains("pistol squat") || name.contains("shrimp squat")))) {
            return "SQUAT";
        }
        return null;
    }

    public static String resolveSkillLiveAnalysisCode(Skill skill) {
        if (skill == null || skill.getTrainingType() != ExerciseTrainingType.CALISTHENICS) {
            return null;
        }
        return SKILL_TO_LIVE_ANALYZER_CODE.get(skill.getCode());
    }

    private static boolean containsGymEquipmentMarker(Exercise exercise) {
        String combined = (normalizeLower(exercise.getEquipment()) + " "
                + normalizeLower(exercise.getName()) + " "
                + normalizeLower(exercise.getAliases())).trim();
        for (String marker : GYM_EQUIPMENT_MARKERS) {
            if (combined.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private CatalogSnapshot buildSnapshot() {
        List<SkillCategoryEntity> categories = categoryRepository.findByActiveTrueOrderByTrainingTypeAscDisplayOrderAsc();
        List<Skill> skills = skillRepository.findByActiveTrueOrderByTrainingTypeAscDisplayOrderAscNameAsc();
        List<SkillCategoryMembership> memberships = membershipRepository.findAllActiveWithSkillOrderByDisplay();
        List<SkillProgressionLevel> allLevels = progressionLevelRepository.findAllByOrderBySkillIdAscLevelNumberAsc();
        List<SkillExerciseLink> persistedLinks = exerciseLinkRepository.findAllActiveWithDetails();
        List<Exercise> activeExercises = exerciseRepository.findAll().stream()
                .filter(ex -> ex.isActive() && ex.getStatus() != ExerciseStatus.DRAFT)
                .toList();

        Map<String, SkillCategoryEntity> categoryByTypeAndKey = new HashMap<>();
        for (SkillCategoryEntity category : categories) {
            categoryByTypeAndKey.put(typeCatKey(category.getTrainingType(), category.getCategoryKey()), category);
        }

        Map<Long, List<String>> categoryKeysBySkillId = new HashMap<>();
        for (SkillCategoryMembership membership : memberships) {
            categoryKeysBySkillId
                    .computeIfAbsent(membership.getSkill().getId(), ignored -> new ArrayList<>())
                    .add(normalizeKey(membership.getCategoryKey()));
        }
        for (Skill skill : skills) {
            categoryKeysBySkillId.computeIfAbsent(skill.getId(), ignored -> new ArrayList<>());
            List<String> keys = categoryKeysBySkillId.get(skill.getId());
            String primaryCat = normalizeKey(skill.getCategoryKey());
            if (!keys.contains(primaryCat)) {
                keys.add(0, primaryCat);
            }
        }

        Map<Long, List<SkillProgressionLevel>> levelsBySkillId = new HashMap<>();
        for (SkillProgressionLevel level : allLevels) {
            levelsBySkillId.computeIfAbsent(level.getSkill().getId(), ignored -> new ArrayList<>()).add(level);
        }

        Map<Long, List<ResolvedExerciseLink>> linksBySkillId = new HashMap<>();
        Map<Long, Set<Long>> linkedExerciseIdsPerSkill = new HashMap<>();
        Set<String> exerciseLinkedTrainingTypes = new HashSet<>();

        for (SkillExerciseLink link : persistedLinks) {
            Exercise exercise = link.getExercise();
            Skill skill = link.getSkill();
            if (!isExerciseVisibleInCatalog(exercise) || !isExerciseCompatibleWithTrainingType(exercise, skill.getTrainingType())) {
                continue;
            }
            Set<Long> seenForSkill = linkedExerciseIdsPerSkill.computeIfAbsent(skill.getId(), ignored -> new HashSet<>());
            if (seenForSkill.add(exercise.getId())) {
                linksBySkillId.computeIfAbsent(skill.getId(), ignored -> new ArrayList<>())
                        .add(new ResolvedExerciseLink(
                                skill,
                                link.getProgressionLevel(),
                                exercise,
                                link.getRelationType(),
                                link.getDisplayOrder()
                        ));
                exerciseLinkedTrainingTypes.add(exercise.getId() + ":" + skill.getTrainingType().name());
            }
        }

        // Deterministically attach published active exercises that have no persisted link yet
        // within their compatible trainingType(s), without duplicating exercises or crossing disciplines.
        Map<ExerciseTrainingType, List<Skill>> skillsByTrainingType = new EnumMap<>(ExerciseTrainingType.class);
        for (Skill skill : skills) {
            skillsByTrainingType.computeIfAbsent(skill.getTrainingType(), ignored -> new ArrayList<>()).add(skill);
        }

        for (Exercise exercise : activeExercises) {
            for (ExerciseTrainingType targetType : List.of(ExerciseTrainingType.CALISTHENICS, ExerciseTrainingType.GYM)) {
                if (!isExerciseCompatibleWithTrainingType(exercise, targetType)) {
                    continue;
                }
                String pairKey = exercise.getId() + ":" + targetType.name();
                if (exerciseLinkedTrainingTypes.contains(pairKey)) {
                    continue;
                }
                List<Skill> candidates = skillsByTrainingType.getOrDefault(targetType, List.of());
                Skill matchedSkill = matchSkillForUnlinkedExercise(exercise, targetType, candidates);
                if (matchedSkill != null) {
                    Set<Long> seenForSkill = linkedExerciseIdsPerSkill.computeIfAbsent(matchedSkill.getId(), ignored -> new HashSet<>());
                    if (seenForSkill.add(exercise.getId())) {
                        SkillProgressionLevel matchedLevel = matchLevelForExercise(
                                exercise,
                                levelsBySkillId.getOrDefault(matchedSkill.getId(), List.of())
                        );
                        linksBySkillId.computeIfAbsent(matchedSkill.getId(), ignored -> new ArrayList<>())
                                .add(new ResolvedExerciseLink(
                                        matchedSkill,
                                        matchedLevel,
                                        exercise,
                                        SkillRelationType.PRIMARY,
                                        100
                                ));
                        exerciseLinkedTrainingTypes.add(pairKey);
                    }
                }
            }
        }

        // Build reverse index: exerciseId -> SkillReferenceDto list
        Map<Long, List<SkillReferenceDto>> skillRefsByExerciseId = new HashMap<>();
        for (Skill skill : skills) {
            String catDisplay = resolveCategoryDisplayName(skill.getTrainingType(), skill.getCategoryKey(), categoryByTypeAndKey);
            for (ResolvedExerciseLink link : linksBySkillId.getOrDefault(skill.getId(), List.of())) {
                skillRefsByExerciseId.computeIfAbsent(link.exercise().getId(), ignored -> new ArrayList<>())
                        .add(SkillReferenceDto.builder()
                                .skillId(skill.getId())
                                .skillCode(skill.getCode())
                                .skillName(skill.getName())
                                .trainingType(skill.getTrainingType())
                                .categoryKey(skill.getCategoryKey())
                                .categoryDisplayName(catDisplay)
                                .subcategory(skill.getSubcategory())
                                .relationType(link.relationType())
                                .progressionLevelNumber(link.progressionLevel() != null ? link.progressionLevel().getLevelNumber() : null)
                                .progressionLevelTitle(link.progressionLevel() != null ? link.progressionLevel().getTitle() : null)
                                .build());
            }
        }

        // Deduplicate & sort links per skill
        for (Map.Entry<Long, List<ResolvedExerciseLink>> entry : linksBySkillId.entrySet()) {
            entry.getValue().sort(resolvedLinkComparator());
        }

        return new CatalogSnapshot(
                categories,
                skills,
                categoryByTypeAndKey,
                categoryKeysBySkillId,
                levelsBySkillId,
                linksBySkillId,
                skillRefsByExerciseId
        );
    }

    private Skill matchSkillForUnlinkedExercise(Exercise exercise, ExerciseTrainingType targetType, List<Skill> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        String text = (normalizeLower(exercise.getName()) + " "
                + normalizeLower(exercise.getCode()) + " "
                + normalizeLower(exercise.getAliases()) + " "
                + normalizeLower(exercise.getDescription())).trim();

        Skill bestSkill = null;
        int bestScore = 0;

        for (Skill skill : candidates) {
            int score = 0;
            String skillName = normalizeLower(skill.getName());
            String skillCode = normalizeLower(skill.getCode())
                    .replace("cal_", "")
                    .replace("gym_", "")
                    .replace("_", " ");
            if (text.contains(skillName) || text.contains(skillCode)) {
                score += 100;
            }
            score += keywordBoost(text, skill.getCode(), targetType);
            if (exercise.getCategory() != null && categoryRoughlyMatches(exercise.getCategory(), skill.getCategoryKey())) {
                score += 12;
            }
            if (score > bestScore) {
                bestScore = score;
                bestSkill = skill;
            }
        }
        if (bestScore >= 12) {
            return bestSkill;
        }
        // Fallback by broad category inside same trainingType
        if (exercise.getCategory() != null) {
            for (Skill skill : candidates) {
                if (categoryRoughlyMatches(exercise.getCategory(), skill.getCategoryKey())) {
                    return skill;
                }
            }
        }
        return null;
    }

    private int keywordBoost(String text, String skillCode, ExerciseTrainingType targetType) {
        if (targetType == ExerciseTrainingType.CALISTHENICS) {
            return switch (skillCode) {
                case "CAL_PLANCHE" -> containsAny(text, "planche", "maltese", "frog stand", "crow", "zanetti") ? 80 : 0;
                case "CAL_HSPU" -> containsAny(text, "pike push", "hspu", "handstand push", "wall push") ? 80 : 0;
                case "CAL_PUSH_UP_PROGRESSIONS" -> containsAny(text, "push-up", "push up", "pushup") ? 70 : 0;
                case "CAL_DIP_PROGRESSIONS" -> containsAny(text, "dip", "korean dip", "support hold") ? 75 : 0;
                case "CAL_FRONT_LEVER" -> containsAny(text, "front lever", "ice cream maker", "tuck row", "victorian") ? 80 : 0;
                case "CAL_BACK_LEVER" -> containsAny(text, "back lever", "german hang", "skin the cat", "hefesto") ? 80 : 0;
                case "CAL_ONE_ARM_PULL_UP" -> containsAny(text, "one-arm pull", "one arm pull", "pull-up", "pull up", "chin-up", "chin up", "archer pull", "typewriter", "scapular pull", "australian") ? 70 : 0;
                case "CAL_MUSCLE_UP" -> containsAny(text, "muscle-up", "muscle up", "false grip") ? 85 : 0;
                case "CAL_PISTOL_SQUAT" -> containsAny(text, "pistol", "sissy squat", "bodyweight squat", "bulgarian") ? 70 : 0;
                case "CAL_SHRIMP_SQUAT" -> containsAny(text, "shrimp squat", "skater squat", "split squat", "lunge") ? 75 : 0;
                case "CAL_NORDIC_CURL" -> containsAny(text, "nordic", "hamstring curl", "glute bridge", "razor curl") ? 75 : 0;
                case "CAL_L_SIT" -> containsAny(text, "l-sit", "l sit", "knee raise", "hollow body", "dead bug") ? 75 : 0;
                case "CAL_V_SIT" -> containsAny(text, "v-sit", "v sit", "manna", "v-up", "toes-to-bar", "pike lift") ? 75 : 0;
                case "CAL_DRAGON_FLAG" -> containsAny(text, "dragon flag", "reverse crunch") ? 80 : 0;
                case "CAL_FRONT_LEVER_CORE" -> containsAny(text, "wiper", "plank", "ab", "core") ? 45 : 0;
                case "CAL_HANDSTAND" -> containsAny(text, "handstand", "wall walk", "shoulder tap") ? 80 : 0;
                case "CAL_HUMAN_FLAG" -> containsAny(text, "human flag", "side plank", "copenhagen", "flag") ? 80 : 0;
                case "CAL_SHOULDER_MOBILITY" -> containsAny(text, "dislocate", "wall slide", "butcher", "thoracic") ? 75 : 0;
                case "CAL_WRIST_MOBILITY" -> containsAny(text, "wrist", "knuckle", "forearm") ? 80 : 0;
                case "CAL_HIP_MOBILITY" -> containsAny(text, "90/90", "pigeon", "couch stretch", "hip", "cossack", "ankle") ? 75 : 0;
                case "CAL_MIDDLE_SPLIT" -> containsAny(text, "middle split", "pancake", "straddle", "horse stance", "adductor") ? 80 : 0;
                case "CAL_FRONT_SPLIT" -> containsAny(text, "front split", "hamstring stretch", "jefferson curl", "pike stretch") ? 80 : 0;
                default -> 0;
            };
        }
        return switch (skillCode) {
            case "GYM_BENCH_PRESS" -> (containsAny(text, "bench press", "floor press") && !text.contains("incline")) ? 80 : 0;
            case "GYM_INCLINE_BENCH_PRESS" -> containsAny(text, "incline", "landmine press") ? 80 : 0;
            case "GYM_OVERHEAD_PRESS" -> containsAny(text, "overhead press", "shoulder press", "military press", "arnold", "push press", "ohp") ? 80 : 0;
            case "GYM_MACHINE_CHEST_PRESS" -> containsAny(text, "chest press", "pec deck", "cable fly", "chest fly", "dip machine") ? 80 : 0;
            case "GYM_BARBELL_ROW" -> containsAny(text, "barbell row", "pendlay", "t-bar", "meadows", "seal row", "dumbbell row") ? 80 : 0;
            case "GYM_LAT_PULLDOWN" -> containsAny(text, "pulldown", "pullover") ? 80 : 0;
            case "GYM_SEATED_CABLE_ROW" -> containsAny(text, "cable row", "seated row", "chest-supported row", "machine row") ? 80 : 0;
            case "GYM_WEIGHTED_PULL_UP" -> containsAny(text, "weighted pull", "weighted chin", "assisted pull", "shrug") ? 75 : 0;
            case "GYM_BARBELL_SQUAT" -> containsAny(text, "squat") && !containsAny(text, "hack", "pendulum") ? 70 : 0;
            case "GYM_ROMANIAN_DEADLIFT" -> containsAny(text, "romanian", "rdl", "deadlift", "good morning", "hip thrust") ? 80 : 0;
            case "GYM_LEG_PRESS" -> containsAny(text, "leg press", "hack squat", "pendulum squat", "belt squat") ? 80 : 0;
            case "GYM_LEG_EXTENSION" -> containsAny(text, "leg extension") ? 85 : 0;
            case "GYM_LEG_CURL" -> containsAny(text, "leg curl", "ghd") ? 85 : 0;
            case "GYM_CALF_RAISE" -> containsAny(text, "calf") ? 85 : 0;
            case "GYM_CABLE_CRUNCH" -> containsAny(text, "cable crunch", "pallof", "woodchop", "decline crunch") ? 80 : 0;
            case "GYM_HANGING_LEG_RAISE" -> containsAny(text, "hanging leg", "captain", "toes to bar") ? 80 : 0;
            case "GYM_AB_WHEEL" -> containsAny(text, "ab wheel", "rollout", "weighted plank") ? 80 : 0;
            case "GYM_LATERAL_RAISE" -> containsAny(text, "lateral raise", "rear delt", "reverse fly", "upright row", "front raise") ? 80 : 0;
            case "GYM_BICEP_CURL" -> containsAny(text, "curl") && !containsAny(text, "leg", "nordic", "hamstring", "jefferson", "wrist") ? 80 : 0;
            case "GYM_TRICEPS_EXTENSION" -> containsAny(text, "triceps", "tricep", "pushdown", "skull crusher", "jm press") ? 80 : 0;
            case "GYM_FACE_PULL" -> containsAny(text, "face pull", "external rotation", "band pull-apart", "y-raise") ? 80 : 0;
            case "GYM_SHOULDER_MOBILITY" -> containsAny(text, "halo", "thoracic", "pullover", "shoulder mobility") ? 75 : 0;
            case "GYM_HIP_ANKLE_MOBILITY" -> containsAny(text, "ankle", "hip mobility", "jefferson curl", "cossack") ? 75 : 0;
            default -> 0;
        };
    }

    private SkillProgressionLevel matchLevelForExercise(Exercise exercise, List<SkillProgressionLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return null;
        }
        DifficultyLevel exDifficulty = exercise.getDifficulty() != null ? exercise.getDifficulty() : DifficultyLevel.INTERMEDIATE;
        for (SkillProgressionLevel level : levels) {
            if (level.getDifficulty() == exDifficulty) {
                return level;
            }
        }
        return switch (exDifficulty) {
            case BEGINNER -> levels.get(0);
            case INTERMEDIATE -> levels.get(Math.min(1, levels.size() - 1));
            case ADVANCED -> levels.get(Math.max(0, levels.size() - 2));
            case ELITE -> levels.get(levels.size() - 1);
        };
    }

    private boolean categoryRoughlyMatches(ExerciseCategory category, String skillCategoryKey) {
        if (category == null || skillCategoryKey == null) {
            return false;
        }
        return switch (category) {
            case PUSH, CHEST -> skillCategoryKey.equals("PUSH");
            case PULL, BACK -> skillCategoryKey.equals("PULL");
            case LEGS, GLUTES -> skillCategoryKey.equals("LEGS");
            case CORE -> skillCategoryKey.equals("CORE");
            case SKILL, ISOMETRIC -> skillCategoryKey.equals("SKILLS");
            case FLEXIBILITY, MOBILITY -> skillCategoryKey.equals("MOBILITY");
            case SHOULDERS, BICEPS, TRICEPS -> skillCategoryKey.equals("SHOULDERS_AND_ARMS") || skillCategoryKey.equals("PUSH") || skillCategoryKey.equals("PULL");
            case FULL_BODY -> skillCategoryKey.equals("SKILLS") || skillCategoryKey.equals("LEGS");
            case CARDIO -> skillCategoryKey.equals("CORE");
        };
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Skill> resolveSkill(String codeOrId, CatalogSnapshot snapshot) {
        if (codeOrId == null || codeOrId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = codeOrId.trim();
        try {
            long id = Long.parseLong(trimmed);
            for (Skill skill : snapshot.skills) {
                if (skill.getId().equals(id)) {
                    return Optional.of(skill);
                }
            }
        } catch (NumberFormatException ignored) {
            // Treat as code or name
        }

        String normalizedCode = normalizeCode(trimmed);
        for (Skill skill : snapshot.skills) {
            if (skill.getCode().equalsIgnoreCase(trimmed) || skill.getCode().equalsIgnoreCase(normalizedCode)) {
                return Optional.of(skill);
            }
        }
        String mappedSkillCode = LEGACY_CODE_TO_SKILL_CODE.get(normalizedCode);
        if (mappedSkillCode != null) {
            for (Skill skill : snapshot.skills) {
                if (skill.getCode().equalsIgnoreCase(mappedSkillCode)) {
                    return Optional.of(skill);
                }
            }
        }
        for (Skill skill : snapshot.skills) {
            if (skill.getName().equalsIgnoreCase(trimmed)
                    || skill.getCode().equalsIgnoreCase("CAL_" + normalizedCode)
                    || skill.getCode().equalsIgnoreCase("GYM_" + normalizedCode)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    private List<TrainingTypeSummaryDto> buildTrainingTypeSummaries(CatalogSnapshot snapshot) {
        List<TrainingTypeSummaryDto> summaries = new ArrayList<>();
        for (ExerciseTrainingType type : List.of(ExerciseTrainingType.CALISTHENICS, ExerciseTrainingType.GYM)) {
            int catCount = (int) snapshot.categories.stream().filter(c -> c.getTrainingType() == type).count();
            List<Skill> typeSkills = snapshot.skills.stream().filter(s -> s.getTrainingType() == type).toList();
            Set<Long> exerciseIds = new HashSet<>();
            for (Skill s : typeSkills) {
                for (ResolvedExerciseLink link : snapshot.linksBySkillId.getOrDefault(s.getId(), List.of())) {
                    exerciseIds.add(link.exercise().getId());
                }
            }
            summaries.add(TrainingTypeSummaryDto.builder()
                    .trainingType(type)
                    .displayName(type == ExerciseTrainingType.CALISTHENICS ? "Calisthenics" : "Gym / Weight Training")
                    .description(type == ExerciseTrainingType.CALISTHENICS
                            ? "Bodyweight mastery, straight-arm strength, unilateral legs, compression, and joint mobility."
                            : "Free-weight, cable, and machine strength & hypertrophy progressions separated by movement pattern.")
                    .categoryCount(catCount)
                    .skillCount(typeSkills.size())
                    .exerciseCount(exerciseIds.size())
                    .build());
        }
        return summaries;
    }

    private SkillCategoryDto toCategoryDto(SkillCategoryEntity cat, CatalogSnapshot snapshot) {
        String key = normalizeKey(cat.getCategoryKey());
        List<Skill> catSkills = snapshot.skills.stream()
                .filter(s -> s.getTrainingType() == cat.getTrainingType())
                .filter(s -> snapshot.categoryKeysBySkillId.getOrDefault(s.getId(), List.of()).contains(key))
                .toList();
        Set<Long> exIds = new HashSet<>();
        for (Skill s : catSkills) {
            for (ResolvedExerciseLink link : snapshot.linksBySkillId.getOrDefault(s.getId(), List.of())) {
                exIds.add(link.exercise().getId());
            }
        }
        return SkillCategoryDto.builder()
                .id(cat.getId())
                .code(cat.getCode())
                .trainingType(cat.getTrainingType())
                .categoryKey(cat.getCategoryKey())
                .displayName(cat.getDisplayName())
                .description(cat.getDescription())
                .displayOrder(cat.getDisplayOrder())
                .skillCount(catSkills.size())
                .exerciseCount(exIds.size())
                .build();
    }

    private SkillSummaryDto toSkillSummaryDto(Skill skill, CatalogSnapshot snapshot) {
        List<SkillProgressionLevel> levels = snapshot.levelsBySkillId.getOrDefault(skill.getId(), List.of());
        List<ResolvedExerciseLink> links = snapshot.linksBySkillId.getOrDefault(skill.getId(), List.of());

        Map<Long, Integer> countByLevelId = new HashMap<>();
        Set<DifficultyLevel> difficulties = new LinkedHashSet<>();
        for (SkillProgressionLevel level : levels) {
            difficulties.add(level.getDifficulty());
        }
        for (ResolvedExerciseLink link : links) {
            if (link.exercise().getDifficulty() != null) {
                difficulties.add(link.exercise().getDifficulty());
            }
            Long levelId = link.progressionLevel() != null
                    ? link.progressionLevel().getId()
                    : (levels.isEmpty() ? null : matchLevelForExercise(link.exercise(), levels).getId());
            if (levelId != null) {
                countByLevelId.merge(levelId, 1, Integer::sum);
            }
        }

        List<SkillProgressionLevelSummaryDto> levelSummaries = levels.stream()
                .map(level -> SkillProgressionLevelSummaryDto.builder()
                        .id(level.getId())
                        .levelNumber(level.getLevelNumber())
                        .code(level.getCode())
                        .title(level.getTitle())
                        .difficulty(level.getDifficulty())
                        .targetCriteria(level.getTargetCriteria())
                        .description(level.getDescription())
                        .exerciseCount(countByLevelId.getOrDefault(level.getId(), 0))
                        .build())
                .toList();

        String analyzerCode = resolveSkillLiveAnalysisCode(skill);
        List<String> categoryKeys = snapshot.categoryKeysBySkillId.getOrDefault(
                skill.getId(),
                List.of(skill.getCategoryKey())
        );

        return SkillSummaryDto.builder()
                .id(skill.getId())
                .code(skill.getCode())
                .name(skill.getName())
                .trainingType(skill.getTrainingType())
                .categoryKey(skill.getCategoryKey())
                .categoryDisplayName(resolveCategoryDisplayName(skill.getTrainingType(), skill.getCategoryKey(), snapshot))
                .categoryKeys(categoryKeys)
                .subcategory(skill.getSubcategory())
                .description(skill.getDescription())
                .displayOrder(skill.getDisplayOrder())
                .progressionLevelCount(levels.size())
                .relatedExerciseCount(links.size())
                .videoAnalysisSupported(analyzerCode != null)
                .liveAnalysisExerciseCode(analyzerCode)
                .availableDifficulties(new ArrayList<>(difficulties))
                .progressionLevels(levelSummaries)
                .build();
    }

    private SkillExerciseDto toSkillExerciseDto(ResolvedExerciseLink link, CatalogSnapshot snapshot) {
        Exercise exercise = link.exercise();
        SkillProgressionLevel level = link.progressionLevel();
        if (level == null) {
            level = matchLevelForExercise(exercise, snapshot.levelsBySkillId.getOrDefault(link.skill().getId(), List.of()));
        }
        MuscleTaxonomyService.ResolvedMuscleTargets resolvedMuscles = muscleTaxonomyService.resolveTargets(exercise);
        String analysisCode = resolveAnalysisExerciseCode(exercise);
        List<SkillReferenceDto> refs = snapshot.skillRefsByExerciseId.getOrDefault(exercise.getId(), List.of());
        List<String> skillCodes = refs.stream()
                .filter(ref -> isExerciseCompatibleWithTrainingType(exercise, ref.getTrainingType()))
                .map(SkillReferenceDto::getSkillCode)
                .distinct()
                .toList();
        List<String> skillNames = refs.stream()
                .filter(ref -> isExerciseCompatibleWithTrainingType(exercise, ref.getTrainingType()))
                .map(SkillReferenceDto::getSkillName)
                .distinct()
                .toList();

        return SkillExerciseDto.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .code(exercise.getCode())
                .category(exercise.getCategory())
                .trainingType(resolveEffectiveTrainingType(exercise))
                .subcategory(link.skill().getSubcategory())
                .difficulty(exercise.getDifficulty())
                .description(exercise.getDescription())
                .equipment(exercise.getEquipment())
                .instructions(exercise.getInstructions())
                .targetMuscles(exercise.getTargetMuscles())
                .secondaryMuscles(exercise.getSecondaryMuscles())
                .primaryMuscleIds(resolvedMuscles.primaryMuscleIds())
                .secondaryMuscleIds(resolvedMuscles.secondaryMuscleIds())
                .muscleTargets(resolvedMuscles.muscleTargets())
                .mediaItems(ExerciseMediaMapper.toResponses(exercise))
                .recommendedSets(exercise.getRecommendedSets())
                .recommendedReps(exercise.getRecommendedReps())
                .recommendedRestSeconds(exercise.getRecommendedRestSeconds())
                .commonMistakes(exercise.getCommonMistakes())
                .safetyConsiderations(exercise.getSafetyConsiderations())
                .progressionNotes(exercise.getProgressionNotes())
                .relationType(link.relationType())
                .progressionLevelNumber(level != null ? level.getLevelNumber() : null)
                .progressionLevelCode(level != null ? level.getCode() : null)
                .progressionLevelTitle(level != null ? level.getTitle() : null)
                .videoAnalysisSupported(analysisCode != null)
                .analysisExerciseCode(analysisCode)
                .supportedSkillCodes(skillCodes)
                .supportedSkillNames(skillNames)
                .build();
    }

    private boolean matchesCategoryFilter(Skill skill, String normalizedCategory, CatalogSnapshot snapshot) {
        if (normalizedCategory == null) {
            return true;
        }
        return snapshot.categoryKeysBySkillId
                .getOrDefault(skill.getId(), List.of(skill.getCategoryKey()))
                .contains(normalizedCategory);
    }

    private boolean matchesDifficultyFilter(Skill skill, DifficultyLevel difficultyFilter, CatalogSnapshot snapshot) {
        if (difficultyFilter == null) {
            return true;
        }
        for (SkillProgressionLevel level : snapshot.levelsBySkillId.getOrDefault(skill.getId(), List.of())) {
            if (level.getDifficulty() == difficultyFilter) {
                return true;
            }
        }
        for (ResolvedExerciseLink link : snapshot.linksBySkillId.getOrDefault(skill.getId(), List.of())) {
            if (link.exercise().getDifficulty() == difficultyFilter) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSearchQuery(Skill skill, String normalizedSearch, CatalogSnapshot snapshot) {
        if (normalizedSearch == null) {
            return true;
        }
        if (containsSearch(skill.getName(), normalizedSearch)
                || containsSearch(skill.getCode(), normalizedSearch)
                || containsSearch(skill.getSubcategory(), normalizedSearch)
                || containsSearch(skill.getCategoryKey(), normalizedSearch)
                || containsSearch(skill.getDescription(), normalizedSearch)) {
            return true;
        }
        for (SkillProgressionLevel level : snapshot.levelsBySkillId.getOrDefault(skill.getId(), List.of())) {
            if (containsSearch(level.getTitle(), normalizedSearch) || containsSearch(level.getDescription(), normalizedSearch)) {
                return true;
            }
        }
        for (ResolvedExerciseLink link : snapshot.linksBySkillId.getOrDefault(skill.getId(), List.of())) {
            if (matchesExerciseSearch(link.exercise(), link.progressionLevel(), normalizedSearch)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExerciseSearch(Exercise exercise, SkillProgressionLevel level, String normalizedSearch) {
        if (normalizedSearch == null) {
            return true;
        }
        return containsSearch(exercise.getName(), normalizedSearch)
                || containsSearch(exercise.getCode(), normalizedSearch)
                || containsSearch(exercise.getAliases(), normalizedSearch)
                || containsSearch(exercise.getTargetMuscles(), normalizedSearch)
                || containsSearch(exercise.getEquipment(), normalizedSearch)
                || containsSearch(exercise.getDescription(), normalizedSearch)
                || (level != null && containsSearch(level.getTitle(), normalizedSearch));
    }

    private static boolean isExerciseVisibleInCatalog(Exercise exercise) {
        return exercise != null && exercise.isActive() && exercise.getStatus() != ExerciseStatus.DRAFT;
    }

    private static ExerciseTrainingType normalizeTrainingTypeFilter(ExerciseTrainingType trainingTypeFilter) {
        if (trainingTypeFilter == null || trainingTypeFilter == ExerciseTrainingType.BOTH) {
            return null;
        }
        return trainingTypeFilter;
    }

    private static String resolveCategoryDisplayName(
            ExerciseTrainingType type,
            String categoryKey,
            CatalogSnapshot snapshot
    ) {
        return resolveCategoryDisplayName(type, categoryKey, snapshot.categoryByTypeAndKey);
    }

    private static String resolveCategoryDisplayName(
            ExerciseTrainingType type,
            String categoryKey,
            Map<String, SkillCategoryEntity> categoryByTypeAndKey
    ) {
        SkillCategoryEntity cat = categoryByTypeAndKey.get(typeCatKey(type, categoryKey));
        if (cat != null) {
            return cat.getDisplayName();
        }
        return categoryKey;
    }

    private static Long resolveLevelIdForDto(SkillExerciseDto dto, List<SkillProgressionLevel> levels) {
        if (dto.getProgressionLevelNumber() != null) {
            for (SkillProgressionLevel level : levels) {
                if (level.getLevelNumber() == dto.getProgressionLevelNumber()) {
                    return level.getId();
                }
            }
        }
        return levels.isEmpty() ? null : levels.get(0).getId();
    }

    private static Comparator<SkillExerciseDto> skillExerciseComparator() {
        return Comparator
                .comparing((SkillExerciseDto dto) -> dto.getProgressionLevelNumber() == null ? Integer.MAX_VALUE : dto.getProgressionLevelNumber())
                .thenComparingInt(dto -> relationSortRank(dto.getRelationType()))
                .thenComparingInt(dto -> dto.getDifficulty() == null ? 99 : dto.getDifficulty().ordinal())
                .thenComparing(dto -> dto.getName() == null ? "" : dto.getName());
    }

    private static Comparator<ResolvedExerciseLink> resolvedLinkComparator() {
        return Comparator
                .comparingInt((ResolvedExerciseLink l) -> l.progressionLevel() == null ? Integer.MAX_VALUE : l.progressionLevel().getLevelNumber())
                .thenComparingInt(l -> relationSortRank(l.relationType()))
                .thenComparingInt(ResolvedExerciseLink::displayOrder)
                .thenComparing(l -> l.exercise().getName() == null ? "" : l.exercise().getName());
    }

    private static int relationSortRank(SkillRelationType relationType) {
        if (relationType == null) {
            return 3;
        }
        return switch (relationType) {
            case PRIMARY -> 0;
            case PREREQUISITE -> 1;
            case SUPPORTING -> 2;
        };
    }

    private static String typeCatKey(ExerciseTrainingType type, String categoryKey) {
        return (type != null ? type.name() : "") + ":" + normalizeKey(categoryKey);
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String upper = key.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (upper.equals("SKILL")) {
            return "SKILLS";
        }
        if (upper.equals("SHOULDERS_ARMS")) {
            return "SHOULDERS_AND_ARMS";
        }
        return upper;
    }

    private static String normalizeSearch(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsSearch(String field, String normalizedSearch) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private record ResolvedExerciseLink(
            Skill skill,
            SkillProgressionLevel progressionLevel,
            Exercise exercise,
            SkillRelationType relationType,
            int displayOrder
    ) {
    }

    private record CatalogSnapshot(
            List<SkillCategoryEntity> categories,
            List<Skill> skills,
            Map<String, SkillCategoryEntity> categoryByTypeAndKey,
            Map<Long, List<String>> categoryKeysBySkillId,
            Map<Long, List<SkillProgressionLevel>> levelsBySkillId,
            Map<Long, List<ResolvedExerciseLink>> linksBySkillId,
            Map<Long, List<SkillReferenceDto>> skillRefsByExerciseId
    ) {
    }
}
