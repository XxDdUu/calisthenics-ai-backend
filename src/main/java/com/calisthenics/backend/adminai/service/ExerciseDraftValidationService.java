package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses explicit/contextual exercise draft requests and strictly validates
 * AI/tool-supplied exercise candidates before any database write is permitted.
 */
@Service
public class ExerciseDraftValidationService {

    private static final Pattern NUMBERED_OR_BULLET_LINE = Pattern.compile("^\\s*(?:(\\d+)[.)]|[-*•])\\s+(.+?)\\s*$");
    private static final Pattern SEPARATOR_SPLIT = Pattern.compile("\\s*(?:\\u2014|\\u2013|\\||·|\\s-\\s)\\s*");
    private static final Pattern TRAILING_PAREN = Pattern.compile("^(.*?)\\s*\\(([^()]+)\\)\\s*$");
    private static final Pattern CODE_TOKEN = Pattern.compile("^[A-Z0-9_]{3,50}$");
    private static final Pattern CATEGORY_TOKEN = Pattern.compile(
            "\\b(PUSH|PULL|CORE|LEGS|LEG|SKILL|ISOMETRIC|FLEXIBILITY|MOBILITY|CHEST|BACK|SHOULDERS|BICEPS|TRICEPS|GLUTES|FULL_BODY|CARDIO)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UPPERCASE_BACK_TOKEN = Pattern.compile("\\bBACK\\b");
    private static final Pattern DIFFICULTY_TOKEN = Pattern.compile("\\b(BEGINNER|INTERMEDIATE|ADVANCED|ELITE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_COUNT_WITH_NOUN = Pattern.compile(
            "\\b(\\d+)\\s+(?:more\\s+|new\\s+|additional\\s+|these\\s+|those\\s+)?(?:[a-z-]+\\s+){0,2}(?:exercises?|bài tập)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Word-boundary category patterns for current-turn intent extraction
    private static final Pattern LEGS_PATTERN = Pattern.compile("\\b(?:legs?|lower[- ]?body|quads?|quadriceps|hamstrings?|calves|squats?|lunges?|chân|đùi)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GLUTES_PATTERN = Pattern.compile("\\b(?:glutes?|glute\\s+bridges?|hip\\s+thrusts?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLEXIBILITY_PATTERN = Pattern.compile("\\b(?:flexibility|stretching|splits?|pike\\s+stretch|pancake)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILITY_PATTERN = Pattern.compile("\\b(?:mobility|joint\\s+prep|car|cars|thoracic\\s+rotation)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARDIO_PATTERN = Pattern.compile("\\b(?:cardio|hiit|conditioning|burpees?|jump\\s+rope|sprints?|endurance)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_BODY_PATTERN = Pattern.compile("\\b(?:full[- ]?body|total[- ]?body)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEST_PATTERN = Pattern.compile("\\b(?:chest\\s+(?:exercises?|workouts?|day)|pectorals?|pec\\s+fly|bench\\s+press)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACK_PATTERN = Pattern.compile("\\b(?:back\\s+(?:exercises?|workouts?|day)|lat\\s+pulldown|deadlifts?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOULDERS_PATTERN = Pattern.compile("\\b(?:shoulders?|deltoids?|delts?|overhead\\s+press|lateral\\s+raise)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BICEPS_PATTERN = Pattern.compile("\\b(?:biceps?|bicep\\s+curls?|hammer\\s+curls?|preacher\\s+curls?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRICEPS_PATTERN = Pattern.compile("\\b(?:triceps?|tricep\\s+extensions?|skull\\s+crushers?|pushdowns?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUSH_PATTERN = Pattern.compile("\\b(?:push|chest|triceps?|shoulders?|dips?|push[- ]?ups?|đẩy|ngực)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PULL_PATTERN = Pattern.compile("\\b(?:pull|back|biceps?|rows?|chin[- ]?ups?|pull[- ]?ups?|kéo|lưng)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CORE_PATTERN = Pattern.compile("\\b(?:core|abs|abdominal|abdominals|obliques?|planks?|hollow|compression|bụng)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISOMETRIC_PATTERN = Pattern.compile("\\b(?:isometric|isometrics|static\\s+hold|tĩnh)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_PATTERN = Pattern.compile("\\b(?:skill|skills|planche|front\\s+lever|back\\s+lever|handstand|human\\s+flag|muscle[- ]?ups?)\\b", Pattern.CASE_INSENSITIVE);

    public record RequestedExerciseSpec(
            String name,
            ExerciseCategory category,
            DifficultyLevel difficulty
    ) {}

    public record ExerciseDraftRequestContract(
            List<RequestedExerciseSpec> explicitExercises,
            ExerciseCategory requiredCategory,
            Set<ExerciseCategory> allowedCategories,
            String skillFocus,
            DifficultyLevel requiredDifficulty,
            int expectedCount
    ) {
        public ExerciseDraftRequestContract(
                List<RequestedExerciseSpec> explicitExercises,
                ExerciseCategory requiredCategory,
                DifficultyLevel requiredDifficulty,
                int expectedCount
        ) {
            this(
                    explicitExercises,
                    requiredCategory,
                    requiredCategory != null ? Set.of(requiredCategory) : Set.of(),
                    null,
                    requiredDifficulty,
                    expectedCount
            );
        }

        public boolean hasExplicitExercises() {
            return explicitExercises != null && !explicitExercises.isEmpty();
        }

        public boolean isCategoryAllowed(ExerciseCategory candidateCategory) {
            if (candidateCategory == null) {
                return false;
            }
            if (allowedCategories != null && !allowedCategories.isEmpty()) {
                return allowedCategories.contains(candidateCategory);
            }
            return requiredCategory == null || requiredCategory == candidateCategory;
        }

        public String describeAllowedCategories() {
            if (allowedCategories != null && !allowedCategories.isEmpty()) {
                if (allowedCategories.size() == 1) {
                    return allowedCategories.iterator().next().name();
                }
                return allowedCategories.stream().map(ExerciseCategory::name).sorted().reduce((a, b) -> a + ", " + b).orElse("");
            }
            return requiredCategory != null ? requiredCategory.name() : "ANY";
        }
    }

    public record ValidationOutcome(
            boolean valid,
            List<ExerciseRecommendationPayload> validatedPayloads,
            List<String> errors
    ) {
        public String errorSummary() {
            return String.join("; ", errors);
        }
    }

    public record ItemizedValidationOutcome(
            int parsedCount,
            List<ExerciseRecommendationPayload> validPayloads,
            List<String> rejectionReasons
    ) {}

    private record SkillProgressionPolicy(
            String skillFocus,
            ExerciseCategory primaryCategory,
            Set<ExerciseCategory> allowedCategories
    ) {}

    public ExerciseDraftRequestContract parseRequestContract(
            String userPrompt,
            String recentRecommendationContext,
            ExerciseCategory explicitCategory,
            String skillFilter,
            DifficultyLevel explicitDifficulty,
            Integer explicitCount
    ) {
        List<RequestedExerciseSpec> promptSpecs = extractRequestedExercises(userPrompt);
        ExerciseCategory promptCategory = detectCategoryFromText(userPrompt);

        List<RequestedExerciseSpec> effectiveSpecs = promptSpecs;
        if (effectiveSpecs.isEmpty()
                && hasAnaphoricReference(userPrompt)
                && recentRecommendationContext != null
                && !recentRecommendationContext.isBlank()) {
            List<RequestedExerciseSpec> contextSpecs = extractRequestedExercises(recentRecommendationContext);
            // Anti-stale guard: only adopt context exercises if their category matches current prompt category (or prompt didn't switch category)
            if (!contextSpecs.isEmpty() && isCompatibleWithPromptCategory(contextSpecs, promptCategory)) {
                effectiveSpecs = contextSpecs;
            }
        }

        // Priority for requiredCategory:
        // 1. Category explicitly stated in userPrompt (e.g., "exercises of legs")
        // 2. Explicit category passed by caller
        // 3. Single shared category across all explicitly listed exercises
        // 4. Category implied by skillFilter ONLY when userPrompt had no category and no explicit exercise list
        ExerciseCategory requiredCategory = promptCategory;
        if (requiredCategory == null) {
            requiredCategory = explicitCategory;
        }
        if (requiredCategory == null && !effectiveSpecs.isEmpty()) {
            Set<ExerciseCategory> specCategories = new LinkedHashSet<>();
            for (RequestedExerciseSpec s : effectiveSpecs) {
                if (s.category() != null) {
                    specCategories.add(s.category());
                } else {
                    ExerciseCategory inferred = detectCategoryFromText(s.name());
                    if (inferred != null) {
                        specCategories.add(inferred);
                    }
                }
            }
            if (specCategories.size() == 1) {
                requiredCategory = specCategories.iterator().next();
            }
        }
        if (requiredCategory == null && effectiveSpecs.isEmpty() && skillFilter != null && !skillFilter.isBlank()) {
            requiredCategory = detectCategoryFromText(skillFilter);
        }

        // Only enforce a single uniform batch difficulty when no per-exercise explicit specs dictate individual difficulties
        DifficultyLevel requiredDifficulty = effectiveSpecs.isEmpty() ? explicitDifficulty : null;

        int resolvedCount;
        if (!effectiveSpecs.isEmpty()) {
            resolvedCount = effectiveSpecs.size();
        } else {
            resolvedCount = resolveRequestedCount(userPrompt, explicitCount);
        }

        SkillProgressionPolicy skillPolicy = (effectiveSpecs.isEmpty() && explicitCategory == null)
                ? detectSkillProgressionPolicy(userPrompt, skillFilter)
                : null;
        Set<ExerciseCategory> allowedCategories;
        String skillFocus = null;
        if (skillPolicy != null && (promptCategory == null || promptCategory == skillPolicy.primaryCategory())) {
            allowedCategories = skillPolicy.allowedCategories();
            requiredCategory = skillPolicy.primaryCategory();
            skillFocus = skillPolicy.skillFocus();
        } else {
            allowedCategories = requiredCategory != null ? Set.of(requiredCategory) : Set.of();
        }

        return new ExerciseDraftRequestContract(
                List.copyOf(effectiveSpecs),
                requiredCategory,
                allowedCategories,
                skillFocus,
                requiredDifficulty,
                resolvedCount
        );
    }

    /**
     * Validates each candidate independently without discarding valid items if count != expectedCount
     * or if a subset of candidates contains missing fields or mismatched categories.
     */
    public ItemizedValidationOutcome validateRawCandidatesItemized(
            ExerciseDraftRequestContract contract,
            List<ExerciseDraftCandidateDto> rawCandidates
    ) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return new ItemizedValidationOutcome(0, List.of(), List.of());
        }

        List<ExerciseRecommendationPayload> valid = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> expectedNormNames = collectExpectedNormNames(contract);

        for (int i = 0; i < rawCandidates.size(); i++) {
            ExerciseDraftCandidateDto dto = rawCandidates.get(i);
            String label = dto != null && dto.getName() != null && !dto.getName().isBlank()
                    ? "'" + dto.getName().trim() + "'"
                    : "item #" + (i + 1);
            List<String> itemErrors = new ArrayList<>();
            ExerciseRecommendationPayload payload = convertAndValidateDto(dto, i, contract, expectedNormNames, itemErrors);
            if (!itemErrors.isEmpty() || payload == null) {
                rejectionReasons.addAll(itemErrors);
                continue;
            }
            String normName = normalizeExactName(payload.getName());
            if (!seenNames.add(normName)) {
                rejectionReasons.add("Duplicate exercise name in batch: " + label + ".");
                continue;
            }
            if (!seenCodes.add(payload.getCode())) {
                rejectionReasons.add("Duplicate exercise code '" + payload.getCode() + "' in batch.");
                continue;
            }
            valid.add(payload);
        }

        return new ItemizedValidationOutcome(rawCandidates.size(), valid, rejectionReasons);
    }

    public ValidationOutcome validateRawCandidates(
            ExerciseDraftRequestContract contract,
            List<ExerciseDraftCandidateDto> rawCandidates
    ) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return new ValidationOutcome(false, List.of(), List.of("AI tool did not provide any exercise draft candidates."));
        }

        List<String> errors = new ArrayList<>();
        List<ExerciseRecommendationPayload> converted = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> expectedNormNames = collectExpectedNormNames(contract);

        for (int i = 0; i < rawCandidates.size(); i++) {
            ExerciseDraftCandidateDto dto = rawCandidates.get(i);
            String label = dto != null && dto.getName() != null && !dto.getName().isBlank()
                    ? "'" + dto.getName().trim() + "'"
                    : "item #" + (i + 1);
            List<String> itemErrors = new ArrayList<>();
            ExerciseRecommendationPayload payload = convertAndValidateDto(dto, i, contract, expectedNormNames, itemErrors);
            errors.addAll(itemErrors);
            if (payload != null) {
                String normName = normalizeExactName(payload.getName());
                if (!seenNames.add(normName)) {
                    errors.add("Duplicate exercise name in batch: " + label + ".");
                }
                if (!payload.getCode().isBlank() && !seenCodes.add(payload.getCode())) {
                    errors.add("Duplicate exercise code '" + payload.getCode() + "' in batch.");
                }
                converted.add(payload);
            }
        }

        if (contract.expectedCount() > 0 && rawCandidates.size() != contract.expectedCount()) {
            errors.add(0, String.format("Requested %d exercise(s), but received %d.",
                    contract.expectedCount(), rawCandidates.size()));
        }

        validateExplicitMatchCoverage(contract, converted, errors);

        if (!errors.isEmpty()) {
            return new ValidationOutcome(false, List.of(), errors);
        }
        return new ValidationOutcome(true, converted, List.of());
    }

    public ValidationOutcome validatePayloads(
            ExerciseDraftRequestContract contract,
            List<ExerciseRecommendationPayload> candidates
    ) {
        List<String> errors = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            errors.add("No exercise candidates were provided for validation.");
            return new ValidationOutcome(false, List.of(), errors);
        }

        if (contract.expectedCount() > 0 && candidates.size() != contract.expectedCount()) {
            errors.add(String.format("Requested %d exercise(s), but received %d.",
                    contract.expectedCount(), candidates.size()));
        }

        Set<String> seenNames = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> expectedNormNames = collectExpectedNormNames(contract);
        List<ExerciseRecommendationPayload> normalized = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            ExerciseRecommendationPayload p = candidates.get(i);
            List<String> itemErrors = new ArrayList<>();
            ExerciseRecommendationPayload validated = validateAndNormalizePayload(p, i, contract, expectedNormNames, itemErrors);
            errors.addAll(itemErrors);
            if (validated != null) {
                String label = "'" + validated.getName() + "'";
                String normName = normalizeExactName(validated.getName());
                if (!seenNames.add(normName)) {
                    errors.add("Duplicate exercise name in batch: " + label + ".");
                }
                if (!validated.getCode().isBlank() && !seenCodes.add(validated.getCode())) {
                    errors.add("Duplicate exercise code '" + validated.getCode() + "' in batch.");
                }
                normalized.add(validated);
            }
        }

        validateExplicitMatchCoverage(contract, normalized, errors);

        if (!errors.isEmpty()) {
            return new ValidationOutcome(false, List.of(), errors);
        }

        return new ValidationOutcome(true, normalized, List.of());
    }

    private ExerciseRecommendationPayload convertAndValidateDto(
            ExerciseDraftCandidateDto dto,
            int index,
            ExerciseDraftRequestContract contract,
            Set<String> expectedNormNames,
            List<String> itemErrors
    ) {
        String label = dto != null && dto.getName() != null && !dto.getName().isBlank()
                ? "'" + dto.getName().trim() + "'"
                : "item #" + (index + 1);
        if (dto == null) {
            itemErrors.add("Exercise item #" + (index + 1) + " is null.");
            return null;
        }
        ExerciseCategory parsedCategory = parseCategoryEnum(dto.getCategory());
        if (parsedCategory == null) {
            itemErrors.add("Invalid or missing category '" + dto.getCategory() + "' for exercise " + label + ".");
        }
        DifficultyLevel parsedDifficulty = parseDifficultyEnum(dto.getDifficulty());
        if (parsedDifficulty == null) {
            itemErrors.add("Invalid or missing difficulty '" + dto.getDifficulty() + "' for exercise " + label + ".");
        }

        String cleanName = dto.getName() != null ? dto.getName().trim() : "";
        String normalizedCode = normalizeSlug(
                (dto.getCode() != null && !dto.getCode().isBlank()) ? dto.getCode() : cleanName
        );

        ExerciseRecommendationPayload payload = ExerciseRecommendationPayload.builder()
                .name(cleanName)
                .code(normalizedCode)
                .category(parsedCategory)
                .difficulty(parsedDifficulty)
                .description(safeTrim(dto.getDescription()))
                .instructions(safeTrim(dto.getInstructions()))
                .targetMuscles(safeTrim(dto.getTargetMuscles()))
                .equipment(safeTrim(dto.getEquipment()))
                .targetSkill(safeTrim(dto.getTargetSkill()))
                .trainingType(parseTrainingTypeOrInfer(dto.getTrainingType(), dto.getEquipment(), cleanName))
                .aliases(safeTrim(dto.getAliases()))
                .build();

        return validateAndNormalizePayload(payload, index, contract, expectedNormNames, itemErrors);
    }

    private ExerciseRecommendationPayload validateAndNormalizePayload(
            ExerciseRecommendationPayload p,
            int index,
            ExerciseDraftRequestContract contract,
            Set<String> expectedNormNames,
            List<String> itemErrors
    ) {
        String label = p != null && p.getName() != null && !p.getName().isBlank()
                ? "'" + p.getName().trim() + "'"
                : "item #" + (index + 1);
        if (p == null) {
            itemErrors.add("Exercise candidate #" + (index + 1) + " is null.");
            return null;
        }
        if (p.getName() == null || p.getName().isBlank()) {
            itemErrors.add("Exercise candidate #" + (index + 1) + " is missing a name.");
        } else if (p.getName().trim().length() > 100) {
            itemErrors.add("Exercise name " + label + " exceeds 100 characters.");
        }

        String slug = normalizeSlug(p.getCode() != null && !p.getCode().isBlank() ? p.getCode() : p.getName());
        if (slug.isBlank()) {
            itemErrors.add("Missing slug/code for exercise " + label + ".");
        } else if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("_+$", "");
        }

        if (p.getCategory() == null) {
            if (itemErrors.stream().noneMatch(e -> e.contains("Invalid or missing category"))) {
                itemErrors.add("Missing valid category for exercise " + label + ".");
            }
        } else if (!contract.isCategoryAllowed(p.getCategory())) {
            itemErrors.add(String.format("Category mismatch for %s: expected %s, got %s.",
                    label, contract.describeAllowedCategories(), p.getCategory()));
        }

        if (p.getDifficulty() == null) {
            if (itemErrors.stream().noneMatch(e -> e.contains("Invalid or missing difficulty"))) {
                itemErrors.add("Missing valid difficulty for exercise " + label + ".");
            }
        } else if (contract.requiredDifficulty() != null && p.getDifficulty() != contract.requiredDifficulty()) {
            itemErrors.add(String.format("Difficulty mismatch for %s: expected %s, got %s.",
                    label, contract.requiredDifficulty(), p.getDifficulty()));
        }

        if (isBlank(p.getDescription())) {
            itemErrors.add("Missing description for exercise " + label + ".");
        }
        if (isBlank(p.getInstructions())) {
            itemErrors.add("Missing instructions for exercise " + label + ".");
        }
        if (isBlank(p.getTargetMuscles())) {
            itemErrors.add("Missing target muscles for exercise " + label + ".");
        }
        if (isBlank(p.getEquipment())) {
            itemErrors.add("Missing equipment requirement for exercise " + label + ".");
        }

        if (contract.hasExplicitExercises() && p.getName() != null && !p.getName().isBlank()) {
            String norm = normalizeExactName(p.getName());
            if (!expectedNormNames.contains(norm)) {
                itemErrors.add("Unexpected/unrelated exercise '" + p.getName()
                        + "' rejected because it was not in the requested exercise list.");
            } else {
                for (RequestedExerciseSpec spec : contract.explicitExercises()) {
                    if (normalizeExactName(spec.name()).equals(norm)) {
                        if (spec.category() != null && p.getCategory() != null && p.getCategory() != spec.category()) {
                            itemErrors.add(String.format("Exercise '%s' category mismatch: expected %s, got %s.",
                                    spec.name(), spec.category(), p.getCategory()));
                        }
                        if (spec.difficulty() != null && p.getDifficulty() != spec.difficulty()) {
                            itemErrors.add(String.format("Exercise '%s' difficulty mismatch: expected %s, got %s.",
                                    spec.name(), spec.difficulty(), p.getDifficulty()));
                        }
                        break;
                    }
                }
            }
        }

        if (!itemErrors.isEmpty()) {
            return null;
        }

        String targetSkill = safeTrim(p.getTargetSkill());
        if ((targetSkill == null || targetSkill.isBlank()) && contract.skillFocus() != null) {
            targetSkill = contract.skillFocus();
        }

        return ExerciseRecommendationPayload.builder()
                .name(p.getName().trim())
                .code(slug)
                .category(p.getCategory())
                .difficulty(p.getDifficulty())
                .description(p.getDescription().trim())
                .instructions(p.getInstructions().trim())
                .targetMuscles(p.getTargetMuscles().trim())
                .equipment(p.getEquipment().trim())
                .targetSkill(targetSkill)
                .trainingType(p.getTrainingType() != null
                        ? p.getTrainingType()
                        : parseTrainingTypeOrInfer(null, p.getEquipment(), p.getName()))
                .aliases(safeTrim(p.getAliases()))
                .progressionLevel(safeTrim(p.getProgressionLevel()))
                .prerequisites(safeTrim(p.getPrerequisites()))
                .whyAdd(safeTrim(p.getWhyAdd()))
                .build();
    }

    private static com.calisthenics.backend.exercise.ExerciseTrainingType parseTrainingTypeOrInfer(
            String rawType,
            String equipment,
            String name
    ) {
        if (rawType != null && !rawType.isBlank()) {
            String upper = rawType.trim().toUpperCase(Locale.US);
            for (com.calisthenics.backend.exercise.ExerciseTrainingType value
                    : com.calisthenics.backend.exercise.ExerciseTrainingType.values()) {
                if (value.name().equals(upper)) {
                    return value;
                }
            }
        }
        String combined = ((equipment != null ? equipment : "") + " " + (name != null ? name : ""))
                .toLowerCase(Locale.US);
        if (combined.contains("barbell") || combined.contains("dumbbell") || combined.contains("cable")
                || combined.contains("machine") || combined.contains("smith") || combined.contains("leg press")
                || combined.contains("leg extension") || combined.contains("leg curl")
                || combined.contains("lat pulldown")) {
            return com.calisthenics.backend.exercise.ExerciseTrainingType.GYM;
        }
        return com.calisthenics.backend.exercise.ExerciseTrainingType.CALISTHENICS;
    }

    private Set<String> collectExpectedNormNames(ExerciseDraftRequestContract contract) {
        if (!contract.hasExplicitExercises()) {
            return Set.of();
        }
        Set<String> expected = new LinkedHashSet<>();
        for (RequestedExerciseSpec spec : contract.explicitExercises()) {
            expected.add(normalizeExactName(spec.name()));
        }
        return expected;
    }

    private void validateExplicitMatchCoverage(
            ExerciseDraftRequestContract contract,
            List<ExerciseRecommendationPayload> candidates,
            List<String> errors
    ) {
        if (!contract.hasExplicitExercises()) {
            return;
        }
        Map<String, ExerciseRecommendationPayload> candidateByExactNorm = new LinkedHashMap<>();
        for (ExerciseRecommendationPayload c : candidates) {
            if (c != null && c.getName() != null) {
                candidateByExactNorm.putIfAbsent(normalizeExactName(c.getName()), c);
            }
        }
        for (RequestedExerciseSpec spec : contract.explicitExercises()) {
            String expectedNorm = normalizeExactName(spec.name());
            if (!candidateByExactNorm.containsKey(expectedNorm)) {
                errors.add("Missing requested exercise '" + spec.name() + "'. Unrelated substitutions are rejected.");
            }
        }
    }

    private SkillProgressionPolicy detectSkillProgressionPolicy(String prompt, String skillFilter) {
        String source = (prompt != null && !prompt.isBlank()) ? prompt : skillFilter;
        if (source == null || source.isBlank()) {
            return null;
        }
        String lower = source.toLowerCase(Locale.US);
        if (lower.contains("handstand push") || lower.matches(".*\\bhspu\\b.*")) {
            return new SkillProgressionPolicy(
                    "Handstand Push-Up",
                    ExerciseCategory.PUSH,
                    Set.of(ExerciseCategory.PUSH, ExerciseCategory.SKILL, ExerciseCategory.SHOULDERS, ExerciseCategory.ISOMETRIC)
            );
        }
        if (lower.contains("handstand")) {
            return new SkillProgressionPolicy(
                    "Handstand",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.PUSH, ExerciseCategory.SHOULDERS, ExerciseCategory.ISOMETRIC)
            );
        }
        if (lower.contains("planche")) {
            return new SkillProgressionPolicy(
                    "Planche",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.PUSH, ExerciseCategory.SHOULDERS, ExerciseCategory.ISOMETRIC, ExerciseCategory.CORE)
            );
        }
        if (lower.contains("front lever")) {
            return new SkillProgressionPolicy(
                    "Front Lever",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.PULL, ExerciseCategory.ISOMETRIC, ExerciseCategory.CORE, ExerciseCategory.BACK)
            );
        }
        if (lower.contains("back lever")) {
            return new SkillProgressionPolicy(
                    "Back Lever",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.PULL, ExerciseCategory.ISOMETRIC, ExerciseCategory.SHOULDERS)
            );
        }
        if (lower.matches(".*\\bmuscle[- ]?ups?\\b.*")) {
            return new SkillProgressionPolicy(
                    "Muscle-Up",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.PULL, ExerciseCategory.PUSH)
            );
        }
        if (lower.contains("human flag")) {
            return new SkillProgressionPolicy(
                    "Human Flag",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.ISOMETRIC, ExerciseCategory.CORE, ExerciseCategory.SHOULDERS, ExerciseCategory.PULL, ExerciseCategory.PUSH)
            );
        }
        if (lower.matches(".*\\b[lv][- ]?sit\\b.*")) {
            return new SkillProgressionPolicy(
                    "L-Sit",
                    ExerciseCategory.SKILL,
                    Set.of(ExerciseCategory.SKILL, ExerciseCategory.CORE, ExerciseCategory.ISOMETRIC)
            );
        }
        return null;
    }

    public List<RequestedExerciseSpec> extractRequestedExercises(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Support inline numbered lists pasted on a single line (e.g., "Add: 1. A — LEGS 2. B — LEGS")
        boolean hasInlineNumberedList = text.matches("(?s).*\\b1[.)]\\s+.*\\b2[.)]\\s+.*");
        String prepared = hasInlineNumberedList
                ? text.replaceAll("(?:[:;,]\\s*|\\s+)(?=\\d+[.)]\\s+[\\p{L}*`\"])", "\n")
                : text;
        List<RequestedExerciseSpec> results = new ArrayList<>();
        String[] lines = prepared.split("\\r?\\n");
        boolean foundTopLevelNumbered = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            // If indented bullet inside a numbered list (e.g., "   - **Category / Difficulty**: LEGS · INTERMEDIATE"),
            // enrich the previous exercise entry rather than creating a fake exercise.
            boolean isIndentedBullet = rawLine.matches("^\\s+[-*•]\\s+.*");
            if (isIndentedBullet && !results.isEmpty()) {
                RequestedExerciseSpec prev = results.get(results.size() - 1);
                ExerciseCategory subCat = prev.category() != null ? prev.category() : extractCategoryToken(line);
                DifficultyLevel subDiff = prev.difficulty() != null ? prev.difficulty() : extractDifficultyToken(line);
                if (subCat != prev.category() || subDiff != prev.difficulty()) {
                    results.set(results.size() - 1, new RequestedExerciseSpec(prev.name(), subCat, subDiff));
                }
                continue;
            }

            Matcher lineMatcher = NUMBERED_OR_BULLET_LINE.matcher(line);
            if (!lineMatcher.matches()) {
                continue;
            }
            boolean isNumbered = lineMatcher.group(1) != null;
            if (isNumbered) {
                foundTopLevelNumbered = true;
            } else if (foundTopLevelNumbered) {
                // Skip non-indented metadata bullets inside numbered recommendation lists
                if (line.toLowerCase(Locale.US).contains("category")
                        || line.toLowerCase(Locale.US).contains("difficulty")
                        || line.toLowerCase(Locale.US).contains("muscles")
                        || line.toLowerCase(Locale.US).contains("equipment")
                        || line.toLowerCase(Locale.US).contains("prerequisites")
                        || line.toLowerCase(Locale.US).contains("why add")) {
                    continue;
                }
            }

            RequestedExerciseSpec parsed = parseSingleExerciseLine(lineMatcher.group(2));
            if (parsed != null) {
                results.add(parsed);
            }
        }

        return results;
    }

    private RequestedExerciseSpec parseSingleExerciseLine(String rawItem) {
        if (rawItem == null || rawItem.isBlank()) {
            return null;
        }
        // Strip leading markdown wrappers cleanly while inspecting separated metadata tokens
        String[] segments = SEPARATOR_SPLIT.split(rawItem.trim());
        String candidateName = stripMarkdown(segments[0]).trim();

        ExerciseCategory foundCategory = null;
        DifficultyLevel foundDifficulty = null;

        // Inspect trailing segments after separator (e.g., "LEGS — INTERMEDIATE")
        for (int i = 1; i < segments.length; i++) {
            String seg = stripMarkdown(segments[i]);
            if (foundCategory == null) {
                foundCategory = extractCategoryToken(seg);
            }
            if (foundDifficulty == null) {
                foundDifficulty = extractDifficultyToken(seg);
            }
        }

        // Strip trailing parenthetical metadata ONLY if it contains category/difficulty/code (preserve "(Negative)", "(Weighted)", etc.)
        Matcher parenMatcher = TRAILING_PAREN.matcher(candidateName);
        if (parenMatcher.matches()) {
            String inside = stripMarkdown(parenMatcher.group(2)).trim();
            ExerciseCategory parenCat = extractCategoryToken(inside);
            DifficultyLevel parenDiff = extractDifficultyToken(inside);
            boolean isCodeOnly = CODE_TOKEN.matcher(inside).matches() && inside.contains("_");
            if (parenCat != null || parenDiff != null || isCodeOnly) {
                if (foundCategory == null) foundCategory = parenCat;
                if (foundDifficulty == null) foundDifficulty = parenDiff;
                candidateName = stripMarkdown(parenMatcher.group(1)).trim();
            }
        }

        // Remove accidental trailing/leading formatting
        candidateName = candidateName.replaceAll("^[\"'`\\*]+|[\"'`\\*]+$", "").trim();
        if (candidateName.isBlank() || candidateName.length() > 100) {
            return null;
        }
        String lowerName = candidateName.toLowerCase(Locale.US);
        // Reject generic plural phrases or instruction sentences that are not exercise names
        if (lowerName.matches(".*\\b(exercises|bài tập|recommendations|movements)\\b.*")
                && foundCategory == null && foundDifficulty == null) {
            return null;
        }
        return new RequestedExerciseSpec(candidateName, foundCategory, foundDifficulty);
    }

    private boolean hasAnaphoricReference(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase(Locale.US);
        return lower.matches("(?s).*\\b(these|those|them|above|suggested|recommended|những bài tập đó|các bài tập đó|này)\\b.*");
    }

    private boolean isCompatibleWithPromptCategory(List<RequestedExerciseSpec> specs, ExerciseCategory promptCategory) {
        if (promptCategory == null) {
            return true;
        }
        return specs.stream().anyMatch(s -> s.category() == promptCategory || detectCategoryFromText(s.name()) == promptCategory)
                && specs.stream().noneMatch(s -> s.category() != null && s.category() != promptCategory);
    }

    public static ExerciseCategory detectCategoryFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        ExerciseCategory enumToken = extractCategoryToken(text);
        if (enumToken != null && enumToken != ExerciseCategory.BACK) {
            if (text.contains(enumToken.name())) {
                return enumToken;
            }
        } else if (UPPERCASE_BACK_TOKEN.matcher(text).find()) {
            return ExerciseCategory.BACK;
        }
        if (LEGS_PATTERN.matcher(text).find()) return ExerciseCategory.LEGS;
        if (FLEXIBILITY_PATTERN.matcher(text).find()) return ExerciseCategory.FLEXIBILITY;
        if (MOBILITY_PATTERN.matcher(text).find()) return ExerciseCategory.MOBILITY;
        if (FULL_BODY_PATTERN.matcher(text).find()) return ExerciseCategory.FULL_BODY;
        if (CARDIO_PATTERN.matcher(text).find()) return ExerciseCategory.CARDIO;
        if (CHEST_PATTERN.matcher(text).find()) return ExerciseCategory.CHEST;
        if (BACK_PATTERN.matcher(text).find()) return ExerciseCategory.BACK;
        if (SHOULDERS_PATTERN.matcher(text).find()) return ExerciseCategory.SHOULDERS;
        if (BICEPS_PATTERN.matcher(text).find()) return ExerciseCategory.BICEPS;
        if (TRICEPS_PATTERN.matcher(text).find()) return ExerciseCategory.TRICEPS;
        if (GLUTES_PATTERN.matcher(text).find()) return ExerciseCategory.GLUTES;
        if (PUSH_PATTERN.matcher(text).find()) return ExerciseCategory.PUSH;
        if (PULL_PATTERN.matcher(text).find()) return ExerciseCategory.PULL;
        if (CORE_PATTERN.matcher(text).find()) return ExerciseCategory.CORE;
        if (ISOMETRIC_PATTERN.matcher(text).find()) return ExerciseCategory.ISOMETRIC;
        if (SKILL_PATTERN.matcher(text).find()) return ExerciseCategory.SKILL;
        return enumToken;
    }

    public static ExerciseCategory extractCategoryToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = CATEGORY_TOKEN.matcher(text);
        if (!m.find()) {
            return null;
        }
        String token = m.group(1).toUpperCase(Locale.US);
        if ("LEG".equals(token)) {
            return ExerciseCategory.LEGS;
        }
        return parseCategoryEnum(token);
    }

    public static DifficultyLevel extractDifficultyToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = DIFFICULTY_TOKEN.matcher(text);
        if (!m.find()) {
            return null;
        }
        return parseDifficultyEnum(m.group(1));
    }

    public static ExerciseCategory parseCategoryEnum(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.trim().toUpperCase(Locale.US);
        if ("LEG".equals(clean)) {
            return ExerciseCategory.LEGS;
        }
        try {
            return ExerciseCategory.valueOf(clean);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static DifficultyLevel parseDifficultyEnum(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DifficultyLevel.valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static int resolveRequestedCount(String prompt, Integer explicitCount) {
        // Explicit numeric count written by the admin in the current prompt always takes precedence
        // over an underestimated AI-planner requested_count field (e.g. prompt=10 vs AI requested_count=3).
        if (prompt != null && !prompt.isBlank()) {
            Matcher explicitMatcher = EXPLICIT_COUNT_WITH_NOUN.matcher(prompt);
            if (explicitMatcher.find()) {
                try {
                    int parsed = Integer.parseInt(explicitMatcher.group(1));
                    if (parsed >= 1 && parsed <= 14) {
                        return parsed;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (explicitCount != null && explicitCount > 0) {
            return Math.min(14, explicitCount);
        }
        if (prompt == null || prompt.isBlank()) {
            return 3;
        }
        String withoutListOrdinals = prompt.replaceAll("(?m)^\\s*\\d+[.)]\\s+", " ");
        String lower = " " + withoutListOrdinals.toLowerCase(Locale.US) + " ";
        if (lower.contains(" three ") || lower.contains(" 3 ") || lower.contains(" ba ")) return 3;
        if (lower.contains(" two ") || lower.contains(" 2 ") || lower.contains(" hai ")) return 2;
        if (lower.contains(" four ") || lower.contains(" 4 ") || lower.contains(" bốn ")) return 4;
        if (lower.contains(" five ") || lower.contains(" 5 ") || lower.contains(" năm ")) return 5;
        if (lower.contains(" one ") || lower.contains(" a new ") || lower.contains(" một ")) return 1;
        return 3;
    }

    public static String normalizeSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String slug = input.trim().toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("_+$", "");
        }
        return slug;
    }

    public static String normalizeExactName(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static String stripMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\*\\*|__|`|~~", "").trim();
    }

    private static String safeTrim(String text) {
        return text == null ? null : text.trim();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
