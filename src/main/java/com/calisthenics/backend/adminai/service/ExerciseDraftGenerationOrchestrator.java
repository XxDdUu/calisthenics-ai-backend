package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftBatchResultResponse;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;
import com.calisthenics.backend.adminai.dto.data.GeminiAdminToolPlan;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiExerciseBatchOutcome;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Orchestrates exact-count AI exercise draft generation with itemized validation,
 * multi-layer duplicate exclusion, iterative deficit refilling, structured diagnostics,
 * and atomic database persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseDraftGenerationOrchestrator {

    private static final int MAX_EXCLUSION_CATALOG_NAMES = 60;
    private static final int MAX_REPORTED_REASONS = 8;

    private final GeminiApiClient geminiApiClient;
    private final ExerciseDraftValidationService validationService;
    private final ExerciseDuplicateDetector duplicateDetector;
    private final ExerciseRecommendationService exerciseRecommendationService;
    private final ExerciseRepository exerciseRepository;
    private final AppProperties appProperties;

    public ExerciseDraftBatchResultResponse generateExactDraftBatch(
            User admin,
            Long conversationId,
            String userPrompt,
            String recentRecommendationContext,
            GeminiAdminToolPlan toolPlan,
            GeminiApiClient.GeminiCallDiagnostics initialDiagnostics
    ) {
        ExerciseCategory promptCategory = ExerciseDraftValidationService.detectCategoryFromText(userPrompt);
        ExerciseCategory toolCategory = (promptCategory == null && toolPlan != null)
                ? ExerciseDraftValidationService.parseCategoryEnum(toolPlan.getExerciseCategory())
                : null;
        String skillFilter = toolPlan != null ? toolPlan.getSkillKeyword() : null;
        DifficultyLevel explicitDifficulty = toolPlan != null
                ? ExerciseDraftValidationService.parseDifficultyEnum(toolPlan.getExperienceLevel())
                : null;
        Integer explicitCount = toolPlan != null ? toolPlan.getRequestedCount() : null;

        ExerciseDraftValidationService.ExerciseDraftRequestContract contract =
                validationService.parseRequestContract(
                        userPrompt,
                        recentRecommendationContext,
                        toolCategory,
                        skillFilter,
                        explicitDifficulty,
                        explicitCount
                );

        String categoryLabel = resolveCategoryLabel(contract, skillFilter);

        // Idempotent retry check within same conversation / recent window
        Optional<ExerciseDraftBatchResultResponse> retryMatch =
                exerciseRecommendationService.findExistingRetryBatch(admin, conversationId, userPrompt, contract, categoryLabel);
        if (retryMatch.isPresent()) {
            return retryMatch.get();
        }

        List<ExerciseDraftCandidateDto> initialCandidates = toolPlan != null ? toolPlan.getExerciseDrafts() : null;
        boolean initialHasCandidates = initialCandidates != null && !initialCandidates.isEmpty();
        boolean plannerFailed = initialDiagnostics != null
                && initialDiagnostics.status() != GeminiExerciseBatchOutcome.Status.OK
                && initialDiagnostics.status() != GeminiExerciseBatchOutcome.Status.NOT_CONFIGURED;
        boolean hasConcreteFocus = contract.hasExplicitExercises()
                || contract.requiredCategory() != null
                || contract.skillFocus() != null
                || (skillFilter != null && !skillFilter.isBlank());
        boolean aiMode = hasConcreteFocus && (geminiApiClient.isConfigured() || initialHasCandidates || plannerFailed);

        // Offline / deterministic mode or vague anaphoric request without target category/skill
        if (!aiMode) {
            return exerciseRecommendationService.createExerciseDrafts(
                    admin,
                    conversationId,
                    userPrompt,
                    recentRecommendationContext,
                    toolCategory,
                    skillFilter,
                    explicitDifficulty,
                    explicitCount,
                    initialHasCandidates ? initialCandidates : null
            );
        }

        int targetCount = Math.max(1, contract.expectedCount());
        ExerciseDuplicateDetector.CatalogSnapshot catalogSnapshot = duplicateDetector.loadCatalogSnapshot();

        List<ExerciseRecommendationPayload> collected = new ArrayList<>();
        List<String> excludedNames = new ArrayList<>(selectRelevantCatalogExclusions(contract));
        Set<String> reasonSet = new LinkedHashSet<>();

        int totalRawChars = initialDiagnostics != null ? initialDiagnostics.rawResponseLength() : 0;
        int totalParsed = 0;
        int duplicateCount = 0;
        int rejectedCount = 0;
        int retryAttempts = 0;
        GeminiExerciseBatchOutcome.Status lastAiFailureStatus = plannerFailed ? initialDiagnostics.status() : null;

        if (plannerFailed && initialDiagnostics != null) {
            reasonSet.add(describeAiDiagnosticFailure(initialDiagnostics.status(), initialDiagnostics.errorDetail()));
        }

        if (initialHasCandidates) {
            AttemptIngestionResult firstResult = ingestCandidates(
                    contract, initialCandidates, targetCount, catalogSnapshot, collected, excludedNames, reasonSet);
            totalParsed += firstResult.parsedCount();
            duplicateCount += firstResult.duplicateCount();
            rejectedCount += firstResult.rejectedCount();
            if (collected.isEmpty() && contract.hasExplicitExercises() && firstResult.explicitCatalogMatches() > 0
                    && firstResult.rejectedCount() == 0) {
                // Explicit request to add already-existing catalog exercises -> delegate so existing drafts are returned cleanly.
                return exerciseRecommendationService.createExerciseDrafts(
                        admin, conversationId, userPrompt, recentRecommendationContext,
                        toolCategory, skillFilter, explicitDifficulty, explicitCount, initialCandidates
                );
            }
        }

        int maxRetries = Math.max(0, appProperties.getGemini().getExerciseGenerationMaxRetries());
        while (collected.size() < targetCount && retryAttempts < maxRetries) {
            int remaining = targetCount - collected.size();
            retryAttempts++;

            String refillSystemPrompt = buildRefillSystemPrompt(contract, remaining);
            String refillUserPrompt = buildRefillUserPrompt(userPrompt, contract, remaining, excludedNames, reasonSet, lastAiFailureStatus);

            GeminiExerciseBatchOutcome batchOutcome = geminiApiClient.generateExerciseDraftBatch(refillSystemPrompt, refillUserPrompt);
            if (batchOutcome != null) {
                totalRawChars += batchOutcome.rawResponseLength();
                if (batchOutcome.status() == GeminiExerciseBatchOutcome.Status.NOT_CONFIGURED) {
                    lastAiFailureStatus = GeminiExerciseBatchOutcome.Status.NOT_CONFIGURED;
                    break;
                }
                if (batchOutcome.status() != GeminiExerciseBatchOutcome.Status.OK) {
                    lastAiFailureStatus = batchOutcome.status();
                    reasonSet.add(describeAiDiagnosticFailure(batchOutcome.status(), batchOutcome.errorDetail()));
                }
                if (batchOutcome.isTruncated()) {
                    reasonSet.add("Gemini output reached MAX_TOKENS (" + batchOutcome.rawResponseLength() + " chars); retrying for remaining items.");
                }
                if (!batchOutcome.drafts().isEmpty()) {
                    AttemptIngestionResult refillResult = ingestCandidates(
                            contract, batchOutcome.drafts(), targetCount, catalogSnapshot, collected, excludedNames, reasonSet);
                    totalParsed += refillResult.parsedCount();
                    duplicateCount += refillResult.duplicateCount();
                    rejectedCount += refillResult.rejectedCount();
                } else if (batchOutcome.status() == GeminiExerciseBatchOutcome.Status.OK) {
                    reasonSet.add("AI returned 0 candidates on refill attempt #" + retryAttempts + ".");
                }
            } else {
                // Mock compatibility fallback when tests stub planAdminTools instead of generateExerciseDraftBatch
                List<GeminiRequestDto.Content> contents = List.of(GeminiRequestDto.Content.builder()
                        .role("user")
                        .parts(List.of(new GeminiRequestDto.Part(refillUserPrompt)))
                        .build());
                GeminiAdminToolPlan fallbackPlan = geminiApiClient.planAdminTools(refillSystemPrompt, contents).orElse(null);
                GeminiApiClient.GeminiCallDiagnostics diag = geminiApiClient.getLastCallDiagnostics();
                if (diag != null) {
                    totalRawChars += diag.rawResponseLength();
                    if (diag.status() != GeminiExerciseBatchOutcome.Status.OK
                            && diag.status() != GeminiExerciseBatchOutcome.Status.NOT_CONFIGURED) {
                        lastAiFailureStatus = diag.status();
                        reasonSet.add(describeAiDiagnosticFailure(diag.status(), diag.errorDetail()));
                    }
                }
                List<ExerciseDraftCandidateDto> fallbackCandidates = fallbackPlan != null ? fallbackPlan.getExerciseDrafts() : null;
                if (fallbackCandidates != null && !fallbackCandidates.isEmpty()) {
                    AttemptIngestionResult refillResult = ingestCandidates(
                            contract, fallbackCandidates, targetCount, catalogSnapshot, collected, excludedNames, reasonSet);
                    totalParsed += refillResult.parsedCount();
                    duplicateCount += refillResult.duplicateCount();
                    rejectedCount += refillResult.rejectedCount();
                } else if (fallbackPlan == null && diag == null && lastAiFailureStatus == null) {
                    reasonSet.add("AI returned empty response on refill attempt #" + retryAttempts + ".");
                }
            }
        }

        List<String> cappedReasons = reasonSet.stream().limit(MAX_REPORTED_REASONS).toList();
        boolean reachedExactCount = collected.size() >= targetCount;

        log.info(
                "Admin AI exercise generation completed: requestedCount={}, rawResponseLength={}, parsedCount={}, validCount={}, duplicateCount={}, rejectedCount={}, retryAttempts={}, finalWillSaveCount={}, reasons={}",
                targetCount,
                totalRawChars,
                totalParsed,
                collected.size(),
                duplicateCount,
                rejectedCount,
                retryAttempts,
                reachedExactCount ? targetCount : 0,
                cappedReasons
        );

        if (reachedExactCount) {
            List<ExerciseRecommendationPayload> exactBatch = new ArrayList<>(collected.subList(0, targetCount));
            ExerciseDraftBatchResultResponse persistedResult = exerciseRecommendationService.persistExactDraftBatch(
                    admin,
                    conversationId,
                    userPrompt,
                    contract,
                    categoryLabel,
                    exactBatch,
                    duplicateCount,
                    rejectedCount,
                    retryAttempts
            );
            return persistedResult.normalizeCollections();
        }

        String failureCode = resolveFailureReasonCode(collected.size(), lastAiFailureStatus, rejectedCount, duplicateCount);
        String errorMessage = buildPartialFailureMessage(
                collected.size(), targetCount, lastAiFailureStatus, rejectedCount, cappedReasons);

        return ExerciseDraftBatchResultResponse.builder()
                .success(false)
                .validationFailed(true)
                .failureReasonCode(failureCode)
                .errorMessage(errorMessage)
                .validationErrors(cappedReasons)
                .category(categoryLabel)
                .requestedCount(targetCount)
                .createdCount(0)
                .validCount(collected.size())
                .duplicateCount(duplicateCount)
                .rejectedCount(rejectedCount)
                .retryCount(retryAttempts)
                .createdExercises(List.of())
                .existingDrafts(List.of())
                .message(errorMessage)
                .build()
                .normalizeCollections();
    }

    private AttemptIngestionResult ingestCandidates(
            ExerciseDraftValidationService.ExerciseDraftRequestContract contract,
            List<ExerciseDraftCandidateDto> rawCandidates,
            int targetCount,
            ExerciseDuplicateDetector.CatalogSnapshot catalogSnapshot,
            List<ExerciseRecommendationPayload> collected,
            List<String> excludedNames,
            Set<String> reasonSet
    ) {
        ExerciseDraftValidationService.ItemizedValidationOutcome itemized =
                validationService.validateRawCandidatesItemized(contract, rawCandidates);

        int parsedCount = itemized.parsedCount();
        int rejectedCount = Math.max(0, parsedCount - itemized.validPayloads().size());
        reasonSet.addAll(itemized.rejectionReasons());

        int duplicateCount = 0;
        int explicitCatalogMatches = 0;

        for (ExerciseRecommendationPayload candidate : itemized.validPayloads()) {
            if (collected.size() >= targetCount) {
                break;
            }
            ExerciseDuplicateDetector.DuplicateCheckResult dupCheck = duplicateDetector.checkCandidate(
                    catalogSnapshot,
                    candidate.getName(),
                    candidate.getCode(),
                    null,
                    candidate.getCategory(),
                    com.calisthenics.backend.exercise.ExerciseTrainingType.CALISTHENICS
            );
            if (dupCheck.duplicateOrUncertain()) {
                duplicateCount++;
                if (dupCheck.matchedExerciseId() != null) {
                    explicitCatalogMatches++;
                }
                reasonSet.add("Duplicate skipped ('" + candidate.getName() + "'): " + dupCheck.reason());
                excludedNames.add(candidate.getName());
                continue;
            }

            collected.add(candidate);
            excludedNames.add(candidate.getName());
            catalogSnapshot.register(
                    null,
                    candidate.getName(),
                    candidate.getCode(),
                    candidate.getCategory(),
                    com.calisthenics.backend.exercise.ExerciseTrainingType.CALISTHENICS,
                    null
            );
        }

        return new AttemptIngestionResult(parsedCount, duplicateCount, rejectedCount, explicitCatalogMatches);
    }

    private List<String> selectRelevantCatalogExclusions(ExerciseDraftValidationService.ExerciseDraftRequestContract contract) {
        List<Exercise> all = exerciseRepository.findAll();
        if (all.isEmpty()) {
            return List.of();
        }
        String skillLower = contract.skillFocus() != null ? contract.skillFocus().toLowerCase(Locale.US) : "";
        List<String> prioritized = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Exercise ex : all) {
            if (ex.getName() == null || ex.getName().isBlank()) {
                continue;
            }
            boolean categoryMatch = contract.isCategoryAllowed(ex.getCategory());
            boolean skillMatch = !skillLower.isBlank() && ex.getName().toLowerCase(Locale.US).contains(skillLower.split("[ -]")[0]);
            if (categoryMatch || skillMatch) {
                prioritized.add(ex.getName());
            } else {
                others.add(ex.getName());
            }
        }
        List<String> merged = new ArrayList<>(prioritized);
        if (merged.size() < MAX_EXCLUSION_CATALOG_NAMES) {
            for (String o : others) {
                if (merged.size() >= MAX_EXCLUSION_CATALOG_NAMES) {
                    break;
                }
                merged.add(o);
            }
        }
        return merged.size() > MAX_EXCLUSION_CATALOG_NAMES
                ? merged.subList(0, MAX_EXCLUSION_CATALOG_NAMES)
                : merged;
    }

    private String buildRefillSystemPrompt(
            ExerciseDraftValidationService.ExerciseDraftRequestContract contract,
            int remainingCount
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an elite calisthenics exercise database architect.\n")
                .append("You MUST return valid JSON matching the exact schema containing ONLY the `exercise_drafts` array.\n")
                .append("CRITICAL REQUIREMENTS:\n")
                .append("1. Generate EXACTLY ").append(remainingCount).append(" unique exercise(s) in `exercise_drafts` — not fewer, not more.\n")
                .append("2. Every item MUST populate ALL required fields: name, code (UPPER_SNAKE_CASE <= 50 chars), category, difficulty, description (1-2 concise technical sentences), instructions (2-4 concise steps), target_muscles, equipment, target_skill.\n")
                .append("3. NEVER emit duplicates, plural variants, or trivial renames of exercises already in the exclusion list or within this response.\n");
        if (contract.allowedCategories() != null && !contract.allowedCategories().isEmpty()) {
            sb.append("4. Every exercise `category` MUST be one of: ").append(contract.describeAllowedCategories()).append(".\n");
        }
        if (contract.requiredDifficulty() != null) {
            sb.append("5. Every exercise `difficulty` MUST be ").append(contract.requiredDifficulty().name()).append(".\n");
        }
        if (contract.skillFocus() != null) {
            sb.append("6. All exercises must form legitimate, progressive calisthenics progressions/regressions/variations supporting the target skill: ")
                    .append(contract.skillFocus()).append(".");
            if (contract.skillFocus().toLowerCase(Locale.US).contains("handstand")) {
                sb.append(" Examples for Handstand Push-Up include: Pike Push-Up, Elevated Pike Push-Up, Wall Plank Lean, Chest-to-Wall Handstand Hold, Wall Handstand Shoulder Taps, Negative Wall Handstand Push-Up, Wall Handstand Push-Up, Deficit Wall Handstand Push-Up, Parallettes Handstand Push-Up, Freestanding Handstand Push-Up (excluding any already listed below).");
            }
            sb.append("\n");
        }
        if (contract.hasExplicitExercises()) {
            sb.append("7. Exact requested exercise specifications:\n");
            for (ExerciseDraftValidationService.RequestedExerciseSpec spec : contract.explicitExercises()) {
                sb.append("   - ").append(spec.name());
                if (spec.category() != null) sb.append(" | category=").append(spec.category().name());
                if (spec.difficulty() != null) sb.append(" | difficulty=").append(spec.difficulty().name());
                sb.append("\n");
            }
        }
        sb.append("Keep descriptions and instructions crisp and high-signal so JSON output never truncates.");
        return sb.toString();
    }

    private String buildRefillUserPrompt(
            String originalPrompt,
            ExerciseDraftValidationService.ExerciseDraftRequestContract contract,
            int remainingCount,
            List<String> excludedNames,
            Set<String> priorReasons,
            GeminiExerciseBatchOutcome.Status priorFailure
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Original admin request: ").append(originalPrompt).append("\n\n")
                .append("Generate EXACTLY ").append(remainingCount).append(" additional unique, schema-complete exercise(s)");
        if (contract.skillFocus() != null) {
            sb.append(" for skill progression: ").append(contract.skillFocus());
        } else if (contract.requiredCategory() != null) {
            sb.append(" in category: ").append(contract.requiredCategory().name());
        }
        sb.append(".\n");

        if (excludedNames != null && !excludedNames.isEmpty()) {
            List<String> distinctExcluded = excludedNames.stream().distinct().limit(90).toList();
            sb.append("\nDO NOT repeat or closely rename any of these existing/already-collected exercises:\n")
                    .append(String.join(", ", distinctExcluded)).append("\n");
        }

        if (priorFailure != null && priorFailure != GeminiExerciseBatchOutcome.Status.OK) {
            sb.append("\nNote from previous attempt: ").append(priorFailure.name())
                    .append(" occurred. Output clean, strictly valid, concise JSON.\n");
        }
        if (!priorReasons.isEmpty()) {
            sb.append("\nIssues from previous attempt to avoid:\n");
            priorReasons.stream().limit(4).forEach(r -> sb.append("- ").append(r).append("\n"));
        }
        return sb.toString();
    }

    private String resolveCategoryLabel(
            ExerciseDraftValidationService.ExerciseDraftRequestContract contract,
            String skillFilter
    ) {
        if (contract.requiredCategory() != null) {
            return contract.requiredCategory().name();
        }
        if (contract.skillFocus() != null) {
            return contract.skillFocus();
        }
        return (skillFilter != null && !skillFilter.isBlank()) ? skillFilter.trim() : "UNSPECIFIED";
    }

    private String describeAiDiagnosticFailure(GeminiExerciseBatchOutcome.Status status, String detail) {
        return switch (status) {
            case API_TIMEOUT -> "AI request timed out while waiting for generated exercises.";
            case MAX_TOKENS_TRUNCATED -> "AI response reached MAX_TOKENS before finishing the exercise list.";
            case MALFORMED_JSON -> "AI returned malformed JSON that could not be deserialized.";
            case HTTP_ERROR -> "AI service returned an HTTP error (" + safeSummary(detail) + ").";
            case EMPTY_RESPONSE -> "AI service returned an empty response.";
            case NOT_CONFIGURED -> "AI provider is not configured.";
            default -> "AI generation failed (" + safeSummary(detail) + ").";
        };
    }

    private String resolveFailureReasonCode(
            int validCount,
            GeminiExerciseBatchOutcome.Status lastAiStatus,
            int rejectedCount,
            int duplicateCount
    ) {
        if (lastAiStatus != null && validCount == 0 && rejectedCount == 0) {
            return lastAiStatus.name();
        }
        if (validCount > 0) {
            return "PARTIAL_COUNT";
        }
        if (rejectedCount > 0) {
            return "VALIDATION_FAILED";
        }
        if (duplicateCount > 0) {
            return "ALL_DUPLICATES";
        }
        return lastAiStatus != null ? lastAiStatus.name() : "INSUFFICIENT_VALID_EXERCISES";
    }

    private String buildPartialFailureMessage(
            int validCount,
            int targetCount,
            GeminiExerciseBatchOutcome.Status lastAiStatus,
            int rejectedCount,
            List<String> reasons
    ) {
        if (validCount == 0 && rejectedCount > 0 && !reasons.isEmpty()) {
            return String.format("Generated 0/%d valid exercises. No exercises were saved. Validation failed: %s",
                    targetCount, String.join("; ", reasons));
        }
        if (validCount == 0 && lastAiStatus != null) {
            return switch (lastAiStatus) {
                case API_TIMEOUT -> String.format(
                        "Generated 0/%d valid exercises (AI request timed out). No exercises were saved. Please retry.",
                        targetCount);
                case MAX_TOKENS_TRUNCATED -> String.format(
                        "Generated 0/%d valid exercises (AI response was truncated before completing the list). No exercises were saved. Please retry.",
                        targetCount);
                case MALFORMED_JSON -> String.format(
                        "Generated 0/%d valid exercises (AI returned invalid JSON). No exercises were saved. Please retry.",
                        targetCount);
                default -> String.format(
                        "Generated 0/%d valid exercises. No exercises were saved. Please retry.",
                        targetCount);
            };
        }
        return String.format("Generated %d/%d valid exercises. No exercises were saved. Please retry.",
                validCount, targetCount);
    }

    private static String safeSummary(String text) {
        if (text == null || text.isBlank()) {
            return "unknown error";
        }
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 120 ? singleLine.substring(0, 120) + "..." : singleLine;
    }

    private record AttemptIngestionResult(
            int parsedCount,
            int duplicateCount,
            int rejectedCount,
            int explicitCatalogMatches
    ) {}
}
