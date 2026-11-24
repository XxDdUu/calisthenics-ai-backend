package com.calisthenics.backend.exercisetree.service;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseService;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.exercisetree.dto.*;
import com.calisthenics.backend.exercisetree.entity.ExerciseProgressionEdge;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeCategory;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeMissingNode;
import com.calisthenics.backend.exercisetree.entity.ExerciseTreeNode;
import com.calisthenics.backend.exercisetree.repository.ExerciseProgressionEdgeRepository;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeCategoryRepository;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeMissingNodeRepository;
import com.calisthenics.backend.exercisetree.repository.ExerciseTreeNodeRepository;
import com.calisthenics.backend.exercisetree.type.ExerciseNodeProgressStatus;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseTreeService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseService exerciseService;
    private final MuscleTaxonomyService muscleTaxonomyService;
    private final ExerciseTreeCategoryRepository categoryRepository;
    private final ExerciseTreeNodeRepository nodeRepository;
    private final ExerciseProgressionEdgeRepository edgeRepository;
    private final ExerciseTreeMissingNodeRepository missingNodeRepository;
    private final ExerciseTreeSeederService seederService;
    private final ExerciseTreeProgressService progressService;

    @Transactional
    public ExerciseTreeCategoriesResponse getCategories() {
        seederService.ensureSeededIfEmpty();

        List<Exercise> publishedExercises = exerciseRepository.findAll().stream()
                .filter(this::isPublished)
                .toList();
        Set<Long> publishedIds = publishedExercises.stream().map(Exercise::getId).collect(Collectors.toSet());

        List<ExerciseTreeCategory> categories = categoryRepository.findByActiveTrueOrderByTrainingTypeAscDisplayOrderAsc();
        List<ExerciseTreeNode> allNodes = nodeRepository.findAllByOrderByTrainingTypeAscSubcategoryKeyAscTreeTierAscBranchOrderAsc().stream()
                .filter(n -> publishedIds.contains(n.getExercise().getId()))
                .toList();
        List<ExerciseTreeMissingNode> missingNodes = missingNodeRepository.findByResolvedExerciseIdIsNullOrderByTrainingTypeAscSubcategoryKeyAscIdAsc();

        List<ExerciseTreeCategorySummaryDto> calisthenicsCats = buildCategorySummaries(
                ExerciseTreeCategoryType.CALISTHENICS, categories, allNodes, missingNodes);
        List<ExerciseTreeCategorySummaryDto> gymCats = buildCategorySummaries(
                ExerciseTreeCategoryType.GYM, categories, allNodes, missingNodes);

        int calisthenicsCount = (int) allNodes.stream()
                .filter(n -> n.getTrainingType() == ExerciseTreeCategoryType.CALISTHENICS)
                .map(n -> n.getExercise().getId())
                .distinct()
                .count();
        int gymCount = (int) allNodes.stream()
                .filter(n -> n.getTrainingType() == ExerciseTreeCategoryType.GYM)
                .map(n -> n.getExercise().getId())
                .distinct()
                .count();

        return ExerciseTreeCategoriesResponse.builder()
                .totalServerExercises(publishedExercises.size())
                .calisthenicsExerciseCount(calisthenicsCount)
                .gymExerciseCount(gymCount)
                .calisthenicsCategories(calisthenicsCats)
                .gymCategories(gymCats)
                .build();
    }

    @Transactional
    public ExerciseTreeResponse getTree(
            ExerciseTreeCategoryType trainingType,
            String subcategoryRaw,
            DifficultyLevel difficultyFilter,
            String search,
            Long currentUserId
    ) {
        seederService.ensureSeededIfEmpty();
        ExerciseTreeCategoryType resolvedType = trainingType != null ? trainingType : ExerciseTreeCategoryType.CALISTHENICS;
        String normalizedSubcategory = normalizeSubcategoryKey(subcategoryRaw);

        List<Exercise> publishedExercises = exerciseRepository.findAll().stream()
                .filter(this::isPublished)
                .toList();
        Set<Long> publishedIds = publishedExercises.stream().map(Exercise::getId).collect(Collectors.toSet());

        List<ExerciseTreeCategory> categoriesForType = categoryRepository
                .findByTrainingTypeAndActiveTrueOrderByDisplayOrderAsc(resolvedType);
        Map<String, ExerciseTreeCategory> categoryByKey = categoriesForType.stream()
                .collect(Collectors.toMap(ExerciseTreeCategory::getSubcategoryKey, c -> c, (a, b) -> a, LinkedHashMap::new));

        if (normalizedSubcategory != null && !categoryByKey.containsKey(normalizedSubcategory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Unknown subcategory '" + subcategoryRaw + "' for category " + resolvedType);
        }

        List<ExerciseTreeNode> allNodesForType = nodeRepository
                .findByTrainingTypeOrderBySubcategoryKeyAscTreeTierAscBranchOrderAsc(resolvedType).stream()
                .filter(n -> publishedIds.contains(n.getExercise().getId()))
                .toList();

        List<ExerciseProgressionEdge> allEdgesForType = edgeRepository
                .findByTrainingTypeOrderByDisplayOrderAscIdAsc(resolvedType).stream()
                .filter(e -> publishedIds.contains(e.getSourceExercise().getId())
                        && publishedIds.contains(e.getTargetExercise().getId()))
                .toList();

        List<ExerciseTreeMissingNode> allMissingForType = missingNodeRepository
                .findByTrainingTypeAndResolvedExerciseIdIsNullOrderBySubcategoryKeyAscIdAsc(resolvedType);

        // Evaluate progress across ALL nodes & edges so cross-subcategory prerequisites resolve accurately
        Map<Long, ExerciseNodeProgressDto> progressMap = currentUserId != null
                ? progressService.evaluateUserProgress(
                        currentUserId,
                        nodeRepository.findAllByOrderByTrainingTypeAscSubcategoryKeyAscTreeTierAscBranchOrderAsc().stream()
                                .filter(n -> publishedIds.contains(n.getExercise().getId()))
                                .toList(),
                        edgeRepository.findAllByOrderByTrainingTypeAscDisplayOrderAscIdAsc())
                : Collections.emptyMap();

        String trimmedSearch = search != null && !search.isBlank() ? search.trim().toLowerCase(Locale.ROOT) : null;

        List<ExerciseTreeNode> filteredNodes = allNodesForType.stream()
                .filter(n -> normalizedSubcategory == null || normalizedSubcategory.equals(n.getSubcategoryKey()))
                .filter(n -> difficultyFilter == null || n.getProgressionDifficulty() == difficultyFilter)
                .filter(n -> trimmedSearch == null || matchesSearch(n, trimmedSearch))
                .toList();

        Set<Long> visibleExerciseIds = filteredNodes.stream()
                .map(n -> n.getExercise().getId())
                .collect(Collectors.toSet());

        List<ExerciseProgressionEdgeDto> visibleEdges = allEdgesForType.stream()
                .filter(e -> visibleExerciseIds.contains(e.getSourceExercise().getId())
                        && visibleExerciseIds.contains(e.getTargetExercise().getId()))
                .map(this::toEdgeDto)
                .toList();

        List<ExerciseTreeMissingNodeDto> visibleMissing = allMissingForType.stream()
                .filter(m -> normalizedSubcategory == null || normalizedSubcategory.equals(m.getSubcategoryKey()))
                .filter(m -> difficultyFilter == null || m.getTargetDifficulty() == difficultyFilter)
                .filter(m -> trimmedSearch == null
                        || (m.getSuggestedName() != null && m.getSuggestedName().toLowerCase(Locale.ROOT).contains(trimmedSearch)))
                .map(this::toMissingNodeDto)
                .toList();

        List<ExerciseTreeNodeDto> nodeDtos = filteredNodes.stream()
                .map(n -> toNodeDto(n, categoryByKey.get(n.getSubcategoryKey()), progressMap.get(n.getExercise().getId())))
                .toList();

        int treeExerciseCount = (int) allNodesForType.stream()
                .map(n -> n.getExercise().getId())
                .distinct()
                .count();

        ExerciseTreeCategory selectedCategory = normalizedSubcategory != null ? categoryByKey.get(normalizedSubcategory) : null;

        return ExerciseTreeResponse.builder()
                .trainingType(resolvedType)
                .subcategoryKey(normalizedSubcategory)
                .subcategoryDisplayName(selectedCategory != null ? selectedCategory.getDisplayName() : null)
                .totalServerExercises(publishedExercises.size())
                .treeExerciseCount(treeExerciseCount)
                .filteredExerciseCount(nodeDtos.size())
                .categories(buildCategorySummaries(resolvedType, categoriesForType, allNodesForType, allMissingForType))
                .nodes(nodeDtos)
                .edges(visibleEdges)
                .missingNodes(visibleMissing)
                .build();
    }

    @Transactional
    public ExerciseTreeResponse getSubcategoryTree(
            String categoryRaw,
            String subcategoryRaw,
            DifficultyLevel difficultyFilter,
            String search,
            Long currentUserId
    ) {
        ExerciseTreeCategoryType type;
        try {
            type = ExerciseTreeCategoryType.fromString(categoryRaw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category: " + categoryRaw);
        }
        return getTree(type, subcategoryRaw, difficultyFilter, search, currentUserId);
    }

    @Transactional
    public ExerciseTreeExerciseDetailResponse getExerciseDetail(
            Long exerciseId,
            ExerciseTreeCategoryType preferredCategory,
            Long currentUserId
    ) {
        seederService.ensureSeededIfEmpty();
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .filter(this::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found: " + exerciseId));

        ExerciseResponse baseResponse = exerciseService.mapToResponse(exercise);
        List<ExerciseTreeNode> allNodes = nodeRepository.findAllByOrderByTrainingTypeAscSubcategoryKeyAscTreeTierAscBranchOrderAsc();
        List<ExerciseProgressionEdge> allEdges = edgeRepository.findAllByOrderByTrainingTypeAscDisplayOrderAscIdAsc();

        Map<String, String> categoryDisplayMap = categoryRepository.findByActiveTrueOrderByTrainingTypeAscDisplayOrderAsc().stream()
                .collect(Collectors.toMap(
                        c -> c.getTrainingType().name() + ":" + c.getSubcategoryKey(),
                        ExerciseTreeCategory::getDisplayName,
                        (a, b) -> a
                ));

        List<ExerciseTreeNode> placements = allNodes.stream()
                .filter(n -> Objects.equals(n.getExercise().getId(), exerciseId))
                .toList();

        ExerciseTreeNode selectedNode = placements.stream()
                .filter(n -> preferredCategory == null || n.getTrainingType() == preferredCategory)
                .sorted(Comparator.comparing(ExerciseTreeNode::isPrimaryPlacement).reversed())
                .findFirst()
                .orElse(placements.isEmpty() ? null : placements.get(0));

        Map<Long, ExerciseNodeProgressDto> progressMap = currentUserId != null
                ? progressService.evaluateUserProgress(currentUserId, allNodes, allEdges)
                : Collections.emptyMap();

        // Index canonical node per exerciseId for link display metadata
        Map<Long, ExerciseTreeNode> canonicalNodeByExId = new HashMap<>();
        for (ExerciseTreeNode n : allNodes) {
            Long id = n.getExercise().getId();
            ExerciseTreeNode existing = canonicalNodeByExId.get(id);
            if (existing == null
                    || (preferredCategory != null && n.getTrainingType() == preferredCategory && existing.getTrainingType() != preferredCategory)
                    || (!existing.isPrimaryPlacement() && n.isPrimaryPlacement())) {
                canonicalNodeByExId.put(id, n);
            }
        }

        List<ExerciseProgressionLinkDto> previous = new ArrayList<>();
        List<ExerciseProgressionLinkDto> next = new ArrayList<>();
        List<ExerciseProgressionLinkDto> alternatives = new ArrayList<>();
        List<ExerciseProgressionLinkDto> regressions = new ArrayList<>();
        List<ExerciseProgressionLinkDto> relatedSkills = new ArrayList<>();
        List<ExerciseProgressionLinkDto> prerequisites = new ArrayList<>();

        for (ExerciseProgressionEdge edge : allEdges) {
            boolean isSource = Objects.equals(edge.getSourceExercise().getId(), exerciseId);
            boolean isTarget = Objects.equals(edge.getTargetExercise().getId(), exerciseId);
            if (!isSource && !isTarget) {
                continue;
            }
            Exercise other = isSource ? edge.getTargetExercise() : edge.getSourceExercise();
            if (!isPublished(other)) {
                continue;
            }
            ExerciseProgressionLinkDto link = toLinkDto(edge, other, canonicalNodeByExId.get(other.getId()), progressMap.get(other.getId()));
            switch (edge.getRelationType()) {
                case NEXT -> {
                    if (isSource) addIfAbsent(next, link);
                    else addIfAbsent(previous, link);
                }
                case PREREQUISITE -> {
                    if (isTarget) addIfAbsent(prerequisites, link);
                    else addIfAbsent(next, link);
                }
                case ALTERNATIVE -> addIfAbsent(alternatives, link);
                case REGRESSION -> {
                    if (isSource) addIfAbsent(regressions, link);
                    else addIfAbsent(next, link);
                }
                case RELATED_SKILL -> addIfAbsent(relatedSkills, link);
            }
        }

        // Also expose previous progressions as helpful regressions if no explicit regression edge exists
        if (regressions.isEmpty() && !previous.isEmpty()) {
            regressions.addAll(previous);
        }

        DifficultyLevel effectiveDifficulty = selectedNode != null
                ? selectedNode.getProgressionDifficulty()
                : exercise.getDifficulty();

        ExerciseTreeCategoryType effectiveTreeCategory = selectedNode != null
                ? selectedNode.getTrainingType()
                : (preferredCategory != null ? preferredCategory : ExerciseTreeCategoryType.CALISTHENICS);

        String subKey = selectedNode != null ? selectedNode.getSubcategoryKey() : null;
        String subDisplay = subKey != null
                ? categoryDisplayMap.getOrDefault(effectiveTreeCategory.name() + ":" + subKey, subKey)
                : null;

        List<ExercisePlacementDto> placementDtos = placements.stream()
                .map(p -> ExercisePlacementDto.builder()
                        .nodeId(p.getId())
                        .trainingType(p.getTrainingType())
                        .subcategoryKey(p.getSubcategoryKey())
                        .subcategoryDisplayName(categoryDisplayMap.getOrDefault(
                                p.getTrainingType().name() + ":" + p.getSubcategoryKey(), p.getSubcategoryKey()))
                        .progressionDifficulty(p.getProgressionDifficulty())
                        .branchKey(p.getBranchKey())
                        .branchLabel(p.getBranchLabel())
                        .primaryPlacement(p.isPrimaryPlacement())
                        .build())
                .toList();

        List<String> aiPrompts = buildAiPromptSuggestions(exercise, next, prerequisites, effectiveTreeCategory);

        return ExerciseTreeExerciseDetailResponse.builder()
                .nodeId(selectedNode != null ? selectedNode.getId() : null)
                .exerciseId(exercise.getId())
                .code(exercise.getCode())
                .name(exercise.getName())
                .exerciseTrainingType(exercise.getTrainingType())
                .exerciseCategory(exercise.getCategory())
                .treeCategory(effectiveTreeCategory)
                .subcategoryKey(subKey)
                .subcategoryDisplayName(subDisplay)
                .catalogDifficulty(exercise.getDifficulty())
                .progressionDifficulty(effectiveDifficulty)
                .difficultyValidated(selectedNode != null && selectedNode.isDifficultyValidated())
                .branchKey(selectedNode != null ? selectedNode.getBranchKey() : null)
                .branchLabel(selectedNode != null ? selectedNode.getBranchLabel() : null)
                .description(baseResponse.getDescription())
                .instructions(baseResponse.getInstructions())
                .equipment(baseResponse.getEquipment())
                .targetMuscles(baseResponse.getTargetMuscles())
                .secondaryMuscles(baseResponse.getSecondaryMuscles())
                .primaryMuscles(baseResponse.getPrimaryMuscles())
                .primaryMuscleIds(baseResponse.getPrimaryMuscleIds())
                .secondaryMuscleIds(baseResponse.getSecondaryMuscleIds())
                .muscleTargets(baseResponse.getMuscleTargets())
                .imageUrl(baseResponse.getImageUrl())
                .videoUrl(baseResponse.getVideoUrl())
                .mediaItems(baseResponse.getMediaItems())
                .recommendedSets(baseResponse.getRecommendedSets())
                .recommendedReps(baseResponse.getRecommendedReps())
                .recommendedRestSeconds(baseResponse.getRecommendedRestSeconds())
                .commonMistakes(baseResponse.getCommonMistakes())
                .safetyConsiderations(baseResponse.getSafetyConsiderations())
                .progressionNotes(baseResponse.getProgressionNotes())
                .videoAnalysisSupported(baseResponse.getVideoAnalysisSupported())
                .masteryCriteria(selectedNode != null ? toMasteryDto(selectedNode) : null)
                .progress(progressMap.get(exerciseId))
                .previousProgressions(previous)
                .nextProgressions(next)
                .alternativeProgressions(alternatives)
                .regressions(regressions)
                .relatedSkills(relatedSkills)
                .prerequisites(prerequisites)
                .placements(placementDtos)
                .aiPromptSuggestions(aiPrompts)
                .needsManualReview(selectedNode != null && selectedNode.isNeedsManualReview())
                .reviewNotes(selectedNode != null ? selectedNode.getReviewNotes() : null)
                .build();
    }

    @Transactional
    public ExerciseTreeUserProgressResponse getUserProgress(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        seederService.ensureSeededIfEmpty();

        Set<Long> publishedIds = exerciseRepository.findAll().stream()
                .filter(this::isPublished)
                .map(Exercise::getId)
                .collect(Collectors.toSet());

        List<ExerciseTreeNode> allNodes = nodeRepository.findAllByOrderByTrainingTypeAscSubcategoryKeyAscTreeTierAscBranchOrderAsc().stream()
                .filter(n -> publishedIds.contains(n.getExercise().getId()))
                .toList();
        List<ExerciseProgressionEdge> allEdges = edgeRepository.findAllByOrderByTrainingTypeAscDisplayOrderAscIdAsc();

        Map<Long, ExerciseNodeProgressDto> progressMap = progressService.evaluateUserProgress(userId, allNodes, allEdges);

        int mastered = 0, inProgress = 0, available = 0, locked = 0;
        for (ExerciseNodeProgressDto dto : progressMap.values()) {
            switch (dto.getStatus()) {
                case MASTERED -> mastered++;
                case IN_PROGRESS -> inProgress++;
                case AVAILABLE -> available++;
                case LOCKED -> locked++;
            }
        }

        return ExerciseTreeUserProgressResponse.builder()
                .userId(userId)
                .totalTrackedExercises(progressMap.size())
                .masteredCount(mastered)
                .inProgressCount(inProgress)
                .availableCount(available)
                .lockedCount(locked)
                .progressByExerciseId(progressMap)
                .nodes(new ArrayList<>(progressMap.values()))
                .build();
    }

    /**
     * Generates factual progression tree context for the AI Workout Planner / AI Coach
     * using strictly real server exercise IDs and relationships.
     */
    @Transactional(readOnly = true)
    public Optional<String> buildAiProgressionContextForExercise(Long exerciseId) {
        if (exerciseId == null) {
            return Optional.empty();
        }
        Optional<Exercise> exerciseOpt = exerciseRepository.findById(exerciseId).filter(this::isPublished);
        if (exerciseOpt.isEmpty()) {
            return Optional.empty();
        }
        Exercise ex = exerciseOpt.get();
        List<ExerciseTreeNode> placements = nodeRepository.findByExerciseId(exerciseId);
        List<ExerciseProgressionEdge> edges = edgeRepository.findBySourceExerciseIdOrTargetExerciseId(exerciseId, exerciseId);

        StringBuilder sb = new StringBuilder();
        sb.append("Focused Exercise Progression Tree Context (STRICT SERVER CATALOG DATA):\n");
        sb.append("- Exercise: ").append(ex.getName())
                .append(" [exerciseId=").append(ex.getId())
                .append(", code=").append(ex.getCode())
                .append(", trainingType=").append(ex.getTrainingType())
                .append(", catalogDifficulty=").append(ex.getDifficulty()).append("]\n");
        if (!placements.isEmpty()) {
            ExerciseTreeNode primary = placements.get(0);
            sb.append("- Tree Placement: ").append(primary.getTrainingType())
                    .append(" / ").append(primary.getSubcategoryKey())
                    .append(" (Branch: ").append(primary.getBranchLabel())
                    .append(", Progression Level: ").append(primary.getProgressionDifficulty()).append(")\n");
            if (primary.getMasterySummary() != null) {
                sb.append("- Mastery Criteria: ").append(primary.getMasterySummary()).append("\n");
            }
        }
        if (ex.getTargetMuscles() != null && !ex.getTargetMuscles().isBlank()) {
            sb.append("- Target Muscles: ").append(ex.getTargetMuscles()).append("\n");
        }
        if (ex.getEquipment() != null && !ex.getEquipment().isBlank()) {
            sb.append("- Equipment: ").append(ex.getEquipment()).append("\n");
        }

        List<String> prevList = new ArrayList<>();
        List<String> nextList = new ArrayList<>();
        List<String> prereqList = new ArrayList<>();
        List<String> altList = new ArrayList<>();
        List<String> relatedList = new ArrayList<>();

        for (ExerciseProgressionEdge edge : edges) {
            boolean isSource = Objects.equals(edge.getSourceExercise().getId(), exerciseId);
            Exercise other = isSource ? edge.getTargetExercise() : edge.getSourceExercise();
            if (!isPublished(other)) continue;
            String label = other.getName() + " (exerciseId=" + other.getId() + ", code=" + other.getCode() + ")";
            switch (edge.getRelationType()) {
                case NEXT -> {
                    if (isSource) nextList.add(label);
                    else prevList.add(label);
                }
                case PREREQUISITE -> {
                    if (!isSource) prereqList.add(label);
                    else nextList.add(label);
                }
                case ALTERNATIVE -> altList.add(label);
                case REGRESSION -> {
                    if (isSource) prevList.add(label);
                }
                case RELATED_SKILL -> relatedList.add(label);
            }
        }
        if (!prereqList.isEmpty()) sb.append("- Prerequisites: ").append(String.join("; ", prereqList)).append("\n");
        if (!prevList.isEmpty()) sb.append("- Previous/Regressions: ").append(String.join("; ", prevList)).append("\n");
        if (!nextList.isEmpty()) sb.append("- Next Progressions: ").append(String.join("; ", nextList)).append("\n");
        if (!altList.isEmpty()) sb.append("- Alternatives: ").append(String.join("; ", altList)).append("\n");
        if (!relatedList.isEmpty()) sb.append("- Related Skills: ").append(String.join("; ", relatedList)).append("\n");
        sb.append("- CRITICAL RULE: When recommending exercises for this progression, ONLY reference real exercises and exerciseIds from the server catalog above or via catalog tools. NEVER invent nonexistent exercise IDs.\n");
        return Optional.of(sb.toString());
    }

    // ==================== ADMIN AUDIT & MANAGEMENT ====================

    @Transactional
    public AdminExerciseTreeAuditResponse getAuditReport() {
        seederService.ensureSeededIfEmpty();

        List<Exercise> allExercises = exerciseRepository.findAll();
        List<ExerciseTreeNode> allNodes = nodeRepository.findAllByOrderByTrainingTypeAscSubcategoryKeyAscTreeTierAscBranchOrderAsc();
        List<ExerciseProgressionEdge> allEdges = edgeRepository.findAllByOrderByTrainingTypeAscDisplayOrderAscIdAsc();
        List<ExerciseTreeMissingNode> missingNodes = missingNodeRepository.findByResolvedExerciseIdIsNullOrderByTrainingTypeAscSubcategoryKeyAscIdAsc();

        Map<Long, List<ExerciseTreeNode>> nodesByExId = allNodes.stream()
                .collect(Collectors.groupingBy(n -> n.getExercise().getId()));
        Set<Long> exercisesWithEdges = new HashSet<>();
        for (ExerciseProgressionEdge edge : allEdges) {
            exercisesWithEdges.add(edge.getSourceExercise().getId());
            exercisesWithEdges.add(edge.getTargetExercise().getId());
        }

        int publishedCount = 0;
        int classifiedCount = 0;
        List<AdminExerciseTreeAuditResponse.AuditExerciseItem> missingMetadata = new ArrayList<>();
        List<AdminExerciseTreeAuditResponse.AuditExerciseItem> withoutRelationships = new ArrayList<>();
        List<AdminExerciseTreeAuditResponse.AuditExerciseItem> manualReview = new ArrayList<>();

        for (Exercise ex : allExercises) {
            if (isPublished(ex)) {
                publishedCount++;
            }
            List<ExerciseTreeNode> exNodes = nodesByExId.getOrDefault(ex.getId(), Collections.emptyList());
            ExerciseTreeNode primary = exNodes.stream()
                    .filter(ExerciseTreeNode::isPrimaryPlacement)
                    .findFirst()
                    .orElse(exNodes.isEmpty() ? null : exNodes.get(0));
            if (primary != null) {
                classifiedCount++;
            }

            List<String> missingFields = detectMissingMetadataFields(ex);
            if (!missingFields.isEmpty()) {
                missingMetadata.add(toAuditItem(ex, primary, missingFields));
            }
            if (!exercisesWithEdges.contains(ex.getId())) {
                withoutRelationships.add(toAuditItem(ex, primary, List.of()));
            }
            if (primary == null || primary.isNeedsManualReview() || !primary.isDifficultyValidated()) {
                manualReview.add(toAuditItem(ex, primary, missingFields));
            }
        }

        return AdminExerciseTreeAuditResponse.builder()
                .totalExistingExercises(allExercises.size())
                .publishedExercises(publishedCount)
                .nonPublishedOrInactiveExercises(allExercises.size() - publishedCount)
                .exercisesClassified(classifiedCount)
                .exercisesUnclassified(allExercises.size() - classifiedCount)
                .exercisesMissingMetadataCount(missingMetadata.size())
                .exercisesWithoutRelationshipsCount(withoutRelationships.size())
                .exercisesRequiringManualReviewCount(manualReview.size())
                .totalProgressionEdges(allEdges.size())
                .missingProgressionNodesCount(missingNodes.size())
                .exercisesMissingMetadata(missingMetadata)
                .exercisesWithoutRelationships(withoutRelationships)
                .exercisesRequiringManualReview(manualReview)
                .missingProgressionNodes(missingNodes.stream().map(this::toMissingNodeDto).toList())
                .build();
    }

    @Transactional
    public ExerciseTreeNodeDto updateNode(Long nodeId, UpdateExerciseTreeNodeRequest request) {
        ExerciseTreeNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tree node not found: " + nodeId));

        if (request.getSubcategoryKey() != null && !request.getSubcategoryKey().isBlank()) {
            String normalized = normalizeSubcategoryKey(request.getSubcategoryKey());
            categoryRepository.findByTrainingTypeAndSubcategoryKey(node.getTrainingType(), normalized)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid subcategory " + normalized + " for " + node.getTrainingType()));
            node.setSubcategoryKey(normalized);
        }
        if (request.getProgressionDifficulty() != null) {
            node.setProgressionDifficulty(request.getProgressionDifficulty());
            node.setTreeTier(ExerciseTreeNode.tierForDifficulty(request.getProgressionDifficulty()));
        }
        if (request.getDifficultyValidated() != null) {
            node.setDifficultyValidated(request.getDifficultyValidated());
        }
        // Only sync to Exercise.difficulty when admin explicitly validates & requests catalog sync
        if (Boolean.TRUE.equals(request.getSyncCatalogDifficulty()) && node.isDifficultyValidated()) {
            Exercise exercise = node.getExercise();
            exercise.setDifficulty(node.getProgressionDifficulty());
            exerciseRepository.save(exercise);
        }
        if (request.getBranchKey() != null && !request.getBranchKey().isBlank()) {
            node.setBranchKey(request.getBranchKey().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getBranchLabel() != null && !request.getBranchLabel().isBlank()) {
            node.setBranchLabel(request.getBranchLabel().trim());
        }
        if (request.getBranchOrder() != null) {
            node.setBranchOrder(request.getBranchOrder());
        }
        if (request.getMasteryCriteriaType() != null) {
            node.setMasteryCriteriaType(request.getMasteryCriteriaType());
        }
        if (request.getMasteryTargetReps() != null) {
            node.setMasteryTargetReps(request.getMasteryTargetReps());
        }
        if (request.getMasteryTargetSets() != null) {
            node.setMasteryTargetSets(request.getMasteryTargetSets());
        }
        if (request.getMasteryTargetHoldSeconds() != null) {
            node.setMasteryTargetHoldSeconds(request.getMasteryTargetHoldSeconds());
        }
        if (request.getMasteryTargetWeightKg() != null) {
            node.setMasteryTargetWeightKg(request.getMasteryTargetWeightKg());
        }
        if (request.getMasteryMinCompletedSessions() != null) {
            node.setMasteryMinCompletedSessions(request.getMasteryMinCompletedSessions());
        }
        if (request.getMasterySummary() != null) {
            node.setMasterySummary(request.getMasterySummary().trim());
        }
        if (request.getNeedsManualReview() != null) {
            node.setNeedsManualReview(request.getNeedsManualReview());
        }
        if (request.getReviewNotes() != null) {
            node.setReviewNotes(request.getReviewNotes().trim());
        }

        ExerciseTreeNode saved = nodeRepository.save(node);
        ExerciseTreeCategory cat = categoryRepository
                .findByTrainingTypeAndSubcategoryKey(saved.getTrainingType(), saved.getSubcategoryKey())
                .orElse(null);
        return toNodeDto(saved, cat, null);
    }

    @Transactional
    public ExerciseProgressionEdgeDto createEdge(CreateExerciseProgressionEdgeRequest request) {
        if (Objects.equals(request.getSourceExerciseId(), request.getTargetExerciseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target exercise must differ");
        }
        Exercise source = exerciseRepository.findById(request.getSourceExerciseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source exercise not found: " + request.getSourceExerciseId()));
        Exercise target = exerciseRepository.findById(request.getTargetExerciseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target exercise not found: " + request.getTargetExerciseId()));

        if (edgeRepository.existsBySourceExerciseIdAndTargetExerciseIdAndTrainingTypeAndRelationType(
                source.getId(), target.getId(), request.getTrainingType(), request.getRelationType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Progression relationship already exists");
        }

        ExerciseProgressionEdge saved = edgeRepository.save(ExerciseProgressionEdge.builder()
                .sourceExercise(source)
                .targetExercise(target)
                .trainingType(request.getTrainingType())
                .relationType(request.getRelationType())
                .branchLabel(request.getBranchLabel())
                .notes(request.getNotes())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 100)
                .build());

        return toEdgeDto(saved);
    }

    @Transactional
    public void deleteEdge(Long edgeId) {
        if (!edgeRepository.existsById(edgeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Progression edge not found: " + edgeId);
        }
        edgeRepository.deleteById(edgeId);
    }

    @Transactional
    public ExerciseTreeMissingNodeDto createMissingNode(CreateMissingProgressionNodeRequest request) {
        String subKey = normalizeSubcategoryKey(request.getSubcategoryKey());
        if (missingNodeRepository.existsByTrainingTypeAndSubcategoryKeyAndSuggestedNameIgnoreCase(
                request.getTrainingType(), subKey, request.getSuggestedName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Missing progression report already exists");
        }
        ExerciseTreeMissingNode saved = missingNodeRepository.save(ExerciseTreeMissingNode.builder()
                .trainingType(request.getTrainingType())
                .subcategoryKey(subKey)
                .branchKey(request.getBranchKey().trim().toUpperCase(Locale.ROOT))
                .suggestedName(request.getSuggestedName().trim())
                .targetDifficulty(request.getTargetDifficulty())
                .afterExerciseCode(request.getAfterExerciseCode())
                .beforeExerciseCode(request.getBeforeExerciseCode())
                .reason(request.getReason())
                .build());
        return toMissingNodeDto(saved);
    }

    @Transactional
    public void deleteMissingNode(Long id) {
        if (!missingNodeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing progression report not found: " + id);
        }
        missingNodeRepository.deleteById(id);
    }

    @Transactional
    public ExerciseTreeSeederService.SeedSummary syncCatalog() {
        return seederService.seedOrSyncCatalog();
    }

    // ==================== HELPERS ====================

    private boolean isPublished(Exercise exercise) {
        return exercise != null && exercise.isActive() && exercise.getStatus() == ExerciseStatus.PUBLISHED;
    }

    private String normalizeSubcategoryKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private boolean matchesSearch(ExerciseTreeNode node, String q) {
        Exercise ex = node.getExercise();
        if (ex.getName() != null && ex.getName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (ex.getCode() != null && ex.getCode().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (ex.getTargetMuscles() != null && ex.getTargetMuscles().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (ex.getEquipment() != null && ex.getEquipment().toLowerCase(Locale.ROOT).contains(q)) return true;
        return node.getBranchLabel() != null && node.getBranchLabel().toLowerCase(Locale.ROOT).contains(q);
    }

    private List<ExerciseTreeCategorySummaryDto> buildCategorySummaries(
            ExerciseTreeCategoryType type,
            List<ExerciseTreeCategory> categories,
            List<ExerciseTreeNode> nodes,
            List<ExerciseTreeMissingNode> missingNodes
    ) {
        return categories.stream()
                .filter(c -> c.getTrainingType() == type)
                .map(c -> {
                    List<ExerciseTreeNode> catNodes = nodes.stream()
                            .filter(n -> n.getTrainingType() == type && c.getSubcategoryKey().equals(n.getSubcategoryKey()))
                            .toList();
                    Map<String, Integer> diffCounts = new LinkedHashMap<>();
                    for (DifficultyLevel d : DifficultyLevel.values()) {
                        diffCounts.put(d.name(), 0);
                    }
                    for (ExerciseTreeNode n : catNodes) {
                        String key = n.getProgressionDifficulty().name();
                        diffCounts.put(key, diffCounts.getOrDefault(key, 0) + 1);
                    }
                    int missingCount = (int) missingNodes.stream()
                            .filter(m -> m.getTrainingType() == type && c.getSubcategoryKey().equals(m.getSubcategoryKey()))
                            .count();
                    return ExerciseTreeCategorySummaryDto.builder()
                            .trainingType(type)
                            .subcategoryKey(c.getSubcategoryKey())
                            .displayName(c.getDisplayName())
                            .description(c.getDescription())
                            .displayOrder(c.getDisplayOrder())
                            .exerciseCount(catNodes.size())
                            .missingNodeCount(missingCount)
                            .difficultyCounts(diffCounts)
                            .build();
                })
                .toList();
    }

    private ExerciseTreeNodeDto toNodeDto(
            ExerciseTreeNode node,
            ExerciseTreeCategory category,
            ExerciseNodeProgressDto progress
    ) {
        Exercise ex = node.getExercise();
        MuscleTaxonomyService.ResolvedMuscleTargets resolvedMuscles = muscleTaxonomyService.resolveTargets(ex);
        boolean hasMedia = (ex.getImageUrl() != null && !ex.getImageUrl().isBlank())
                || (ex.getVideoUrl() != null && !ex.getVideoUrl().isBlank())
                || (ex.getMediaItems() != null && !ex.getMediaItems().isEmpty());

        return ExerciseTreeNodeDto.builder()
                .nodeId(node.getId())
                .exerciseId(ex.getId())
                .code(ex.getCode())
                .name(ex.getName())
                .trainingType(node.getTrainingType())
                .subcategoryKey(node.getSubcategoryKey())
                .subcategoryDisplayName(category != null ? category.getDisplayName() : node.getSubcategoryKey())
                .progressionDifficulty(node.getProgressionDifficulty())
                .catalogDifficulty(ex.getDifficulty())
                .difficultyValidated(node.isDifficultyValidated())
                .treeTier(node.getTreeTier())
                .branchKey(node.getBranchKey())
                .branchLabel(node.getBranchLabel())
                .branchOrder(node.getBranchOrder())
                .primaryPlacement(node.isPrimaryPlacement())
                .equipment(ex.getEquipment())
                .targetMuscles(ex.getTargetMuscles())
                .primaryMuscleIds(resolvedMuscles.primaryMuscleIds())
                .secondaryMuscleIds(resolvedMuscles.secondaryMuscleIds())
                .imageUrl(ex.getImageUrl())
                .videoUrl(ex.getVideoUrl())
                .hasMedia(hasMedia)
                .recommendedSets(ex.getRecommendedSets())
                .recommendedReps(ex.getRecommendedReps())
                .recommendedRestSeconds(ex.getRecommendedRestSeconds())
                .masteryCriteria(toMasteryDto(node))
                .progress(progress)
                .needsManualReview(node.isNeedsManualReview())
                .reviewNotes(node.getReviewNotes())
                .build();
    }

    private MasteryCriteriaDto toMasteryDto(ExerciseTreeNode node) {
        return MasteryCriteriaDto.builder()
                .criteriaType(node.getMasteryCriteriaType())
                .targetReps(node.getMasteryTargetReps())
                .targetSets(node.getMasteryTargetSets())
                .targetHoldSeconds(node.getMasteryTargetHoldSeconds())
                .targetWeightKg(node.getMasteryTargetWeightKg())
                .minCompletedSessions(node.getMasteryMinCompletedSessions())
                .summary(node.getMasterySummary())
                .build();
    }

    private ExerciseProgressionEdgeDto toEdgeDto(ExerciseProgressionEdge edge) {
        return ExerciseProgressionEdgeDto.builder()
                .edgeId(edge.getId())
                .sourceExerciseId(edge.getSourceExercise().getId())
                .sourceExerciseCode(edge.getSourceExercise().getCode())
                .sourceExerciseName(edge.getSourceExercise().getName())
                .targetExerciseId(edge.getTargetExercise().getId())
                .targetExerciseCode(edge.getTargetExercise().getCode())
                .targetExerciseName(edge.getTargetExercise().getName())
                .trainingType(edge.getTrainingType())
                .relationType(edge.getRelationType())
                .branchLabel(edge.getBranchLabel())
                .notes(edge.getNotes())
                .displayOrder(edge.getDisplayOrder())
                .build();
    }

    private ExerciseProgressionLinkDto toLinkDto(
            ExerciseProgressionEdge edge,
            Exercise other,
            ExerciseTreeNode otherNode,
            ExerciseNodeProgressDto otherProgress
    ) {
        return ExerciseProgressionLinkDto.builder()
                .edgeId(edge.getId())
                .exerciseId(other.getId())
                .code(other.getCode())
                .name(other.getName())
                .difficulty(otherNode != null ? otherNode.getProgressionDifficulty() : other.getDifficulty())
                .trainingType(edge.getTrainingType())
                .subcategoryKey(otherNode != null ? otherNode.getSubcategoryKey() : null)
                .relationType(edge.getRelationType())
                .branchLabel(edge.getBranchLabel())
                .notes(edge.getNotes())
                .progressStatus(otherProgress != null ? otherProgress.getStatus() : null)
                .build();
    }

    private ExerciseTreeMissingNodeDto toMissingNodeDto(ExerciseTreeMissingNode m) {
        return ExerciseTreeMissingNodeDto.builder()
                .id(m.getId())
                .trainingType(m.getTrainingType())
                .subcategoryKey(m.getSubcategoryKey())
                .branchKey(m.getBranchKey())
                .suggestedName(m.getSuggestedName())
                .targetDifficulty(m.getTargetDifficulty())
                .afterExerciseCode(m.getAfterExerciseCode())
                .beforeExerciseCode(m.getBeforeExerciseCode())
                .reason(m.getReason())
                .resolvedExerciseId(m.getResolvedExerciseId())
                .build();
    }

    private void addIfAbsent(List<ExerciseProgressionLinkDto> list, ExerciseProgressionLinkDto candidate) {
        boolean exists = list.stream().anyMatch(item -> Objects.equals(item.getExerciseId(), candidate.getExerciseId()));
        if (!exists) {
            list.add(candidate);
        }
    }

    private List<String> buildAiPromptSuggestions(
            Exercise exercise,
            List<ExerciseProgressionLinkDto> next,
            List<ExerciseProgressionLinkDto> prerequisites,
            ExerciseTreeCategoryType treeType
    ) {
        List<String> prompts = new ArrayList<>();
        String name = exercise.getName();
        if (!next.isEmpty()) {
            prompts.add("How can I progress from " + name + " to " + next.get(0).getName() + "?");
        } else {
            prompts.add("How can I progress and master " + name + "?");
        }
        if (!prerequisites.isEmpty()) {
            prompts.add("What should I train before unlocking " + name + "?");
        } else {
            prompts.add("What should I train before unlocking the next level after " + name + "?");
        }
        prompts.add("Create a workout to improve " + name + ".");
        if (treeType == ExerciseTreeCategoryType.CALISTHENICS) {
            prompts.add("Can I combine " + name + " with gym training?");
        } else {
            prompts.add("Can I combine " + name + " with calisthenics skill training?");
        }
        return prompts;
    }

    private List<String> detectMissingMetadataFields(Exercise ex) {
        List<String> missing = new ArrayList<>();
        if (ex.getDescription() == null || ex.getDescription().isBlank()) missing.add("description");
        if (ex.getInstructions() == null || ex.getInstructions().isBlank()) missing.add("instructions");
        if (ex.getEquipment() == null || ex.getEquipment().isBlank()) missing.add("equipment");
        if (ex.getRecommendedSets() == null || ex.getRecommendedReps() == null || ex.getRecommendedReps().isBlank()) {
            missing.add("recommendedSets/Reps");
        }
        boolean hasMedia = (ex.getImageUrl() != null && !ex.getImageUrl().isBlank())
                || (ex.getVideoUrl() != null && !ex.getVideoUrl().isBlank())
                || (ex.getMediaItems() != null && !ex.getMediaItems().isEmpty());
        if (!hasMedia) missing.add("media");
        return missing;
    }

    private AdminExerciseTreeAuditResponse.AuditExerciseItem toAuditItem(
            Exercise ex,
            ExerciseTreeNode primary,
            List<String> missingFields
    ) {
        return AdminExerciseTreeAuditResponse.AuditExerciseItem.builder()
                .exerciseId(ex.getId())
                .nodeId(primary != null ? primary.getId() : null)
                .code(ex.getCode())
                .name(ex.getName())
                .status(ex.getStatus())
                .active(ex.isActive())
                .catalogDifficulty(ex.getDifficulty())
                .progressionDifficulty(primary != null ? primary.getProgressionDifficulty() : null)
                .trainingType(primary != null ? primary.getTrainingType().name() : (ex.getTrainingType() != null ? ex.getTrainingType().name() : null))
                .subcategoryKey(primary != null ? primary.getSubcategoryKey() : null)
                .missingFields(missingFields)
                .reviewNotes(primary != null ? primary.getReviewNotes() : "Not yet classified into an exercise tree")
                .build();
    }
}
