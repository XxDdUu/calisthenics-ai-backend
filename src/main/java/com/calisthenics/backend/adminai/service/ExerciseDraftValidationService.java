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
    private static final Pattern CATEGORY_TOKEN = Pattern.compile("\\b(PUSH|PULL|CORE|LEGS|LEG|SKILL|ISOMETRIC)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIFFICULTY_TOKEN = Pattern.compile("\\b(BEGINNER|INTERMEDIATE|ADVANCED|ELITE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_COUNT_WITH_NOUN = Pattern.compile(
            "\\b(\\d+)\\s+(?:more\\s+|new\\s+|additional\\s+|these\\s+|those\\s+)?(?:[a-z-]+\\s+){0,2}(?:exercises?|bài tập)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Word-boundary category patterns for current-turn intent extraction
    private static final Pattern LEGS_PATTERN = Pattern.compile("\\b(?:legs?|lower[- ]?body|quads?|quadriceps|hamstrings?|calves|glutes?|squats?|lunges?|chân|đùi)\\b", Pattern.CASE_INSENSITIVE);
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
            DifficultyLevel requiredDifficulty,
            int expectedCount
    ) {
        public boolean hasExplicitExercises() {
            return explicitExercises != null && !explicitExercises.isEmpty();
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

        return new ExerciseDraftRequestContract(
                List.copyOf(effectiveSpecs),
                requiredCategory,
                requiredDifficulty,
                resolvedCount
        );
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

        for (int i = 0; i < rawCandidates.size(); i++) {
            ExerciseDraftCandidateDto dto = rawCandidates.get(i);
            String label = dto != null && dto.getName() != null && !dto.getName().isBlank()
                    ? "'" + dto.getName().trim() + "'"
                    : "item #" + (i + 1);
            if (dto == null) {
                errors.add("Exercise item #" + (i + 1) + " is null.");
                continue;
            }
            ExerciseCategory parsedCategory = parseCategoryEnum(dto.getCategory());
            if (parsedCategory == null) {
                errors.add("Invalid or missing category '" + dto.getCategory() + "' for exercise " + label + ".");
            }
            DifficultyLevel parsedDifficulty = parseDifficultyEnum(dto.getDifficulty());
            if (parsedDifficulty == null) {
                errors.add("Invalid or missing difficulty '" + dto.getDifficulty() + "' for exercise " + label + ".");
            }

            String cleanName = dto.getName() != null ? dto.getName().trim() : "";
            String normalizedCode = normalizeSlug(
                    (dto.getCode() != null && !dto.getCode().isBlank()) ? dto.getCode() : cleanName
            );

            converted.add(ExerciseRecommendationPayload.builder()
                    .name(cleanName)
                    .code(normalizedCode)
                    .category(parsedCategory)
                    .difficulty(parsedDifficulty)
                    .description(safeTrim(dto.getDescription()))
                    .instructions(safeTrim(dto.getInstructions()))
                    .targetMuscles(safeTrim(dto.getTargetMuscles()))
                    .equipment(safeTrim(dto.getEquipment()))
                    .targetSkill(safeTrim(dto.getTargetSkill()))
                    .build());
        }

        if (!errors.isEmpty()) {
            return new ValidationOutcome(false, List.of(), errors);
        }

        return validatePayloads(contract, converted);
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

        for (int i = 0; i < candidates.size(); i++) {
            ExerciseRecommendationPayload p = candidates.get(i);
            String label = p != null && p.getName() != null && !p.getName().isBlank()
                    ? "'" + p.getName().trim() + "'"
                    : "item #" + (i + 1);
            if (p == null) {
                errors.add("Exercise candidate #" + (i + 1) + " is null.");
                continue;
            }
            if (p.getName() == null || p.getName().isBlank()) {
                errors.add("Exercise candidate #" + (i + 1) + " is missing a name.");
            } else if (p.getName().trim().length() > 100) {
                errors.add("Exercise name " + label + " exceeds 100 characters.");
            } else {
                String normName = normalizeExactName(p.getName());
                if (!seenNames.add(normName)) {
                    errors.add("Duplicate exercise name in batch: " + label + ".");
                }
            }

            String slug = normalizeSlug(p.getCode() != null && !p.getCode().isBlank() ? p.getCode() : p.getName());
            if (slug.isBlank()) {
                errors.add("Missing slug/code for exercise " + label + ".");
            } else {
                if (slug.length() > 50) {
                    slug = slug.substring(0, 50).replaceAll("_+$", "");
                }
                if (!seenCodes.add(slug)) {
                    errors.add("Duplicate exercise code '" + slug + "' in batch.");
                }
            }

            if (p.getCategory() == null) {
                errors.add("Missing valid category for exercise " + label + ".");
            } else if (contract.requiredCategory() != null && p.getCategory() != contract.requiredCategory()) {
                errors.add(String.format("Category mismatch for %s: expected %s, got %s.",
                        label, contract.requiredCategory(), p.getCategory()));
            }

            if (p.getDifficulty() == null) {
                errors.add("Missing valid difficulty for exercise " + label + ".");
            } else if (contract.requiredDifficulty() != null && p.getDifficulty() != contract.requiredDifficulty()) {
                errors.add(String.format("Difficulty mismatch for %s: expected %s, got %s.",
                        label, contract.requiredDifficulty(), p.getDifficulty()));
            }

            if (isBlank(p.getDescription())) {
                errors.add("Missing description for exercise " + label + ".");
            }
            if (isBlank(p.getInstructions())) {
                errors.add("Missing instructions for exercise " + label + ".");
            }
            if (isBlank(p.getTargetMuscles())) {
                errors.add("Missing target muscles for exercise " + label + ".");
            }
            if (isBlank(p.getEquipment())) {
                errors.add("Missing equipment requirement for exercise " + label + ".");
            }
        }

        // Strict explicit request matching (prioritize exact name matching over semantic substitution)
        if (contract.hasExplicitExercises()) {
            Map<String, ExerciseRecommendationPayload> candidateByExactNorm = new LinkedHashMap<>();
            for (ExerciseRecommendationPayload c : candidates) {
                if (c != null && c.getName() != null) {
                    candidateByExactNorm.putIfAbsent(normalizeExactName(c.getName()), c);
                }
            }

            Set<String> expectedNormNames = new LinkedHashSet<>();
            for (RequestedExerciseSpec spec : contract.explicitExercises()) {
                String expectedNorm = normalizeExactName(spec.name());
                expectedNormNames.add(expectedNorm);
                ExerciseRecommendationPayload matched = candidateByExactNorm.get(expectedNorm);
                if (matched == null) {
                    errors.add("Missing requested exercise '" + spec.name() + "'. Unrelated substitutions are rejected.");
                    continue;
                }
                if (spec.category() != null && matched.getCategory() != spec.category()) {
                    errors.add(String.format("Exercise '%s' category mismatch: expected %s, got %s.",
                            spec.name(), spec.category(), matched.getCategory()));
                }
                if (spec.difficulty() != null && matched.getDifficulty() != spec.difficulty()) {
                    errors.add(String.format("Exercise '%s' difficulty mismatch: expected %s, got %s.",
                            spec.name(), spec.difficulty(), matched.getDifficulty()));
                }
            }

            for (ExerciseRecommendationPayload c : candidates) {
                if (c != null && c.getName() != null && !expectedNormNames.contains(normalizeExactName(c.getName()))) {
                    errors.add("Unexpected/unrelated exercise '" + c.getName()
                            + "' rejected because it was not in the requested exercise list.");
                }
            }
        }

        if (!errors.isEmpty()) {
            return new ValidationOutcome(false, List.of(), errors);
        }

        List<ExerciseRecommendationPayload> normalized = new ArrayList<>();
        for (ExerciseRecommendationPayload p : candidates) {
            String slug = normalizeSlug(p.getCode() != null && !p.getCode().isBlank() ? p.getCode() : p.getName());
            if (slug.length() > 50) {
                slug = slug.substring(0, 50).replaceAll("_+$", "");
            }
            normalized.add(ExerciseRecommendationPayload.builder()
                    .name(p.getName().trim())
                    .code(slug)
                    .category(p.getCategory())
                    .difficulty(p.getDifficulty())
                    .description(p.getDescription().trim())
                    .instructions(p.getInstructions().trim())
                    .targetMuscles(p.getTargetMuscles().trim())
                    .equipment(p.getEquipment().trim())
                    .targetSkill(safeTrim(p.getTargetSkill()))
                    .progressionLevel(safeTrim(p.getProgressionLevel()))
                    .prerequisites(safeTrim(p.getPrerequisites()))
                    .whyAdd(safeTrim(p.getWhyAdd()))
                    .build());
        }

        return new ValidationOutcome(true, normalized, List.of());
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
        if (enumToken != null && (
                text.toLowerCase(Locale.US).contains("legs")
                        || text.toLowerCase(Locale.US).contains("core")
                        || text.toLowerCase(Locale.US).contains("push")
                        || text.toLowerCase(Locale.US).contains("pull")
                        || text.toLowerCase(Locale.US).contains("isometric")
                        || text.toLowerCase(Locale.US).contains("skill"))) {
            return enumToken;
        }
        if (LEGS_PATTERN.matcher(text).find()) return ExerciseCategory.LEGS;
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
        if (explicitCount != null && explicitCount > 0) {
            return Math.min(10, explicitCount);
        }
        if (prompt == null || prompt.isBlank()) {
            return 3;
        }
        // Check explicit "<N> ... exercises" phrasing first so list numbering ("1. ...", "2. ...") never overrides count
        Matcher explicitMatcher = EXPLICIT_COUNT_WITH_NOUN.matcher(prompt);
        if (explicitMatcher.find()) {
            try {
                int parsed = Integer.parseInt(explicitMatcher.group(1));
                if (parsed >= 1 && parsed <= 10) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
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
