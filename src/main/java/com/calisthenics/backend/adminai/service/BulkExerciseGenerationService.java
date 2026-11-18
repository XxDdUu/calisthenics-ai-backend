package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationItemResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationJobResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationRequest;
import com.calisthenics.backend.adminai.dto.data.GeminiBulkExerciseBatchOutput;
import com.calisthenics.backend.adminai.entity.AdminAiAuditLog;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiRecommendation;
import com.calisthenics.backend.adminai.entity.BulkExerciseGenerationItem;
import com.calisthenics.backend.adminai.entity.BulkExerciseGenerationJob;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.repository.BulkExerciseGenerationItemRepository;
import com.calisthenics.backend.adminai.repository.BulkExerciseGenerationJobRepository;
import com.calisthenics.backend.adminai.type.AdminAiIntent;
import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import com.calisthenics.backend.adminai.type.BulkExerciseGenerationStatus;
import com.calisthenics.backend.adminai.type.BulkExerciseItemOutcome;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates large-scale (50-200+) Admin AI exercise catalog generation across
 * Calisthenics and Gym disciplines using chunked isolated transactions,
 * deterministic multi-layer duplicate detection, safe resumption, and full untruncated reporting.
 */
@Slf4j
@Service
public class BulkExerciseGenerationService {

    private static final int DEFAULT_BATCH_SIZE = 20;
    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 25;
    private static final int MAX_GEMINI_AUGMENTATION_BATCHES = 12;

    private static final Pattern BULK_COUNT_PATTERN = Pattern.compile(
            "\\b(\\d{2,4})\\+?\\s*(?:more\\s+|new\\s+|unique\\s+|additional\\s+)?(?:[a-z-]+\\s+){0,3}(?:exercises?|movements?|bài tập)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WITH_COUNT_PATTERN = Pattern.compile(
            "(?:with|add|generate|create|expand[^0-9]{0,30})\\s*(\\d{2,4})\\+?\\s*(?:unique\\s+|new\\s+)?(?:exercises?|movements?|bài tập)?",
            Pattern.CASE_INSENSITIVE
    );

    public record ParsedBulkSpec(
            int requestedQuantity,
            ExerciseTrainingType trainingType,
            Set<ExerciseCategory> categories,
            String focusKeyword,
            int batchSize
    ) {}

    public record CandidateSpec(
            String name,
            ExerciseCategory category,
            ExerciseTrainingType trainingType,
            DifficultyLevel difficulty,
            String exerciseType,
            String targetMuscles,
            String secondaryMuscles,
            String equipment,
            Integer recommendedSets,
            String recommendedReps,
            Integer recommendedRestSeconds,
            String description,
            String instructions,
            String commonMistakes,
            String safetyConsiderations,
            String progressionNotes,
            String aliases
    ) {}

    public record JobLaunchResult(
            BulkExerciseGenerationJobResponse job,
            CompletableFuture<BulkExerciseGenerationJobResponse> completionFuture
    ) {}

    private final BulkExerciseGenerationJobRepository jobRepository;
    private final BulkExerciseGenerationItemRepository itemRepository;
    private final ExerciseRepository exerciseRepository;
    private final AdminAiRecommendationRepository recommendationRepository;
    private final AdminConversationService conversationService;
    private final AdminAiAuditLogRepository auditLogRepository;
    private final ExerciseDuplicateDetector duplicateDetector;
    private final BulkExerciseCatalogLibrary catalogLibrary;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate requiresNewTx;

    private final ConcurrentHashMap<Long, CompletableFuture<BulkExerciseGenerationJobResponse>> activeFutures =
            new ConcurrentHashMap<>();

    public BulkExerciseGenerationService(
            BulkExerciseGenerationJobRepository jobRepository,
            BulkExerciseGenerationItemRepository itemRepository,
            ExerciseRepository exerciseRepository,
            AdminAiRecommendationRepository recommendationRepository,
            AdminConversationService conversationService,
            AdminAiAuditLogRepository auditLogRepository,
            ExerciseDuplicateDetector duplicateDetector,
            BulkExerciseCatalogLibrary catalogLibrary,
            GeminiApiClient geminiApiClient,
            ObjectMapper objectMapper,
            @Qualifier("taskExecutor") TaskExecutor taskExecutor,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.exerciseRepository = exerciseRepository;
        this.recommendationRepository = recommendationRepository;
        this.conversationService = conversationService;
        this.auditLogRepository = auditLogRepository;
        this.duplicateDetector = duplicateDetector;
        this.catalogLibrary = catalogLibrary;
        this.geminiApiClient = geminiApiClient;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static boolean isBulkExerciseGenerationPrompt(String lower) {
        if (lower == null || lower.isBlank()
                || AdminToolRouter.isGettingStartedOrCampaignPrompt(lower)
                || AdminToolRouter.isProManagementPrompt(lower)) {
            return false;
        }
        boolean mentionsExercise = lower.contains("exercise") || lower.contains("catalog") || lower.contains("bài tập");
        boolean hasGenerateVerb = lower.contains("generate") || lower.contains("create")
                || lower.contains("add") || lower.contains("expand") || lower.contains("populate")
                || lower.contains("build") || lower.contains("tạo");
        if (!mentionsExercise || !hasGenerateVerb) {
            return false;
        }
        if (lower.matches("(?s).*\\b1[.)]\\s+.*")) {
            // Explicit numbered 1..N specification list of specific named items -> handled by strict validator
            return false;
        }
        Integer extractedCount = extractRequestedQuantity(lower);
        if (extractedCount != null && extractedCount >= 15) {
            return true;
        }
        return lower.contains("bulk")
                || lower.contains("expand the entire")
                || lower.contains("expand exercise catalog")
                || lower.contains("large batch")
                || (lower.contains("both calisthenics and gym"));
    }

    public static Integer extractRequestedQuantity(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        Matcher m1 = BULK_COUNT_PATTERN.matcher(prompt);
        if (m1.find()) {
            return parsePositiveInt(m1.group(1));
        }
        Matcher m2 = WITH_COUNT_PATTERN.matcher(prompt);
        if (m2.find()) {
            return parsePositiveInt(m2.group(1));
        }
        return null;
    }

    private static Integer parsePositiveInt(String raw) {
        try {
            int val = Integer.parseInt(raw.trim());
            return val > 0 ? val : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public ParsedBulkSpec parseSpecification(String prompt, BulkExerciseGenerationRequest request) {
        Integer explicitQty = request != null ? request.getQuantity() : null;
        Integer promptQty = extractRequestedQuantity(prompt);
        int requestedQty = (explicitQty != null && explicitQty > 0)
                ? explicitQty
                : (promptQty != null ? promptQty : 50);

        String lower = prompt != null ? prompt.toLowerCase(Locale.US) : "";
        ExerciseTrainingType trainingType = request != null ? request.getTrainingType() : null;
        if (trainingType == null) {
            boolean mentionsCalisthenics = lower.contains("calisthenics") || lower.contains("bodyweight");
            boolean mentionsGym = lower.contains("gym") || lower.contains("weight training")
                    || lower.contains("barbell") || lower.contains("dumbbell") || lower.contains("weights");
            if ((mentionsCalisthenics && mentionsGym)
                    || lower.contains("both calisthenics and gym")
                    || lower.contains("both disciplines")
                    || lower.contains("entire exercise catalog")) {
                trainingType = ExerciseTrainingType.BOTH;
            } else if (mentionsGym) {
                trainingType = ExerciseTrainingType.GYM;
            } else if (mentionsCalisthenics) {
                trainingType = ExerciseTrainingType.CALISTHENICS;
            } else {
                trainingType = ExerciseTrainingType.BOTH;
            }
        }

        Set<ExerciseCategory> categories = new LinkedHashSet<>();
        if (request != null && request.getCategories() != null && !request.getCategories().isEmpty()) {
            categories.addAll(request.getCategories());
        } else if (!lower.isBlank() && !lower.contains("entire exercise catalog")) {
            if (lower.contains("leg") || lower.contains("lower body") || lower.contains("squat")) categories.add(ExerciseCategory.LEGS);
            if (lower.contains("glute")) categories.add(ExerciseCategory.GLUTES);
            if (lower.contains("push") && !lower.contains("push-up")) categories.add(ExerciseCategory.PUSH);
            if (lower.contains("pull") && !lower.contains("pull-up")) categories.add(ExerciseCategory.PULL);
            if (lower.contains("chest") || lower.contains("pec")) categories.add(ExerciseCategory.CHEST);
            if (lower.contains("back") && !lower.contains("lower back")) categories.add(ExerciseCategory.BACK);
            if (lower.contains("shoulder") || lower.contains("delt")) categories.add(ExerciseCategory.SHOULDERS);
            if (lower.contains("bicep")) categories.add(ExerciseCategory.BICEPS);
            if (lower.contains("tricep")) categories.add(ExerciseCategory.TRICEPS);
            if (lower.contains("core") || lower.contains("abs") || lower.contains("abdominal")) categories.add(ExerciseCategory.CORE);
            if (lower.contains("skill")) categories.add(ExerciseCategory.SKILL);
            if (lower.contains("isometric") || lower.contains("static hold")) categories.add(ExerciseCategory.ISOMETRIC);
            if (lower.contains("flexibility") || lower.contains("stretch")) categories.add(ExerciseCategory.FLEXIBILITY);
            if (lower.contains("mobility")) categories.add(ExerciseCategory.MOBILITY);
            if (lower.contains("full body") || lower.contains("full-body")) categories.add(ExerciseCategory.FULL_BODY);
            if (lower.contains("cardio") || lower.contains("conditioning") || lower.contains("hiit")) categories.add(ExerciseCategory.CARDIO);
        }

        int batchSize = DEFAULT_BATCH_SIZE;
        if (request != null && request.getBatchSize() != null) {
            batchSize = Math.max(MIN_BATCH_SIZE, Math.min(MAX_BATCH_SIZE, request.getBatchSize()));
        }

        String focus = (request != null && request.getFocus() != null && !request.getFocus().isBlank())
                ? request.getFocus().trim()
                : null;
        return new ParsedBulkSpec(requestedQty, trainingType, categories, focus, batchSize);
    }

    public JobLaunchResult launchAsyncJob(User admin, BulkExerciseGenerationRequest request) {
        String promptText = (request.getPrompt() != null && !request.getPrompt().isBlank())
                ? request.getPrompt().trim()
                : buildDefaultPrompt(request);
        String cleanIdemp = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey().trim()
                : null;

        if (cleanIdemp != null) {
            Optional<BulkExerciseGenerationJob> existing =
                    jobRepository.findByAdminUserIdAndIdempotencyKey(admin.getId(), cleanIdemp);
            if (existing.isPresent()) {
                BulkExerciseGenerationJob job = existing.get();
                CompletableFuture<BulkExerciseGenerationJobResponse> inFlight = activeFutures.get(job.getId());
                if (inFlight != null) {
                    return new JobLaunchResult(toJobResponse(job), inFlight);
                }
                return new JobLaunchResult(toJobResponse(job), CompletableFuture.completedFuture(toJobResponse(job)));
            }
        }

        ParsedBulkSpec spec = parseSpecification(promptText, request);
        BulkExerciseGenerationJob createdJob = requiresNewTx.execute(status -> {
            if (cleanIdemp != null) {
                Optional<BulkExerciseGenerationJob> dup =
                        jobRepository.findByAdminUserIdAndIdempotencyKey(admin.getId(), cleanIdemp);
                if (dup.isPresent()) {
                    return dup.get();
                }
            }
            int estimatedBatches = Math.max(1, (int) Math.ceil(spec.requestedQuantity() / (double) spec.batchSize()));
            String catFilter = spec.categories().isEmpty()
                    ? null
                    : String.join(",", spec.categories().stream().map(Enum::name).sorted().toList());
            BulkExerciseGenerationJob entity = BulkExerciseGenerationJob.builder()
                    .jobUid("BULK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US))
                    .adminUser(admin)
                    .idempotencyKey(cleanIdemp)
                    .conversationId(request.getConversationId())
                    .prompt(promptText)
                    .trainingTypeFilter(spec.trainingType().name())
                    .categoriesFilter(catFilter)
                    .requestedQuantity(spec.requestedQuantity())
                    .batchSize(spec.batchSize())
                    .totalBatchesEstimated(estimatedBatches)
                    .status(BulkExerciseGenerationStatus.PENDING)
                    .build();
            return jobRepository.save(entity);
        });

        if (createdJob.getStatus() == BulkExerciseGenerationStatus.COMPLETED) {
            BulkExerciseGenerationJobResponse resp = toJobResponse(createdJob);
            return new JobLaunchResult(resp, CompletableFuture.completedFuture(resp));
        }

        CompletableFuture<BulkExerciseGenerationJobResponse> future = scheduleJobExecution(createdJob.getId(), null);
        return new JobLaunchResult(toJobResponse(createdJob), future);
    }

    public BulkExerciseGenerationJobResponse launchAndAwaitIfFast(
            User admin,
            BulkExerciseGenerationRequest request,
            long maxWaitMs
    ) {
        JobLaunchResult launched = launchAsyncJob(admin, request);
        if (maxWaitMs > 0L) {
            try {
                BulkExerciseGenerationJobResponse waited = launched.completionFuture().get(maxWaitMs, TimeUnit.MILLISECONDS);
                log.info("Bulk job {} future completed cleanly inside wait: status={}, saved={}",
                        launched.job().getId(), waited != null ? waited.getStatus() : null,
                        waited != null ? waited.getSavedCount() : -1);
                if (waited != null) {
                    return waited;
                }
            } catch (Exception waitEx) {
                log.warn("Bulk job {} await did not complete cleanly ({}): {}",
                        launched.job().getId(), waitEx.getClass().getSimpleName(), waitEx.getMessage());
            }
        }
        return requiresNewTx.execute(status -> jobRepository.findById(launched.job().getId())
                .map(this::toJobResponse)
                .orElse(launched.job()));
    }

    public BulkExerciseGenerationJobResponse resumeJob(User admin, Long jobId) {
        return resumeJobWithOptionalBatchCap(admin, jobId, null, 6000L);
    }

    public BulkExerciseGenerationJobResponse resumeJobWithOptionalBatchCap(
            User admin,
            Long jobId,
            Integer maxBatchesThisRun,
            long maxWaitMs
    ) {
        BulkExerciseGenerationJob job = jobRepository.findByIdAndAdminUserId(jobId, admin.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk generation job not found"));

        if (job.getStatus() == BulkExerciseGenerationStatus.COMPLETED
                && job.getSavedCount() >= job.getRequestedQuantity()) {
            return toJobResponse(job);
        }
        CompletableFuture<BulkExerciseGenerationJobResponse> inFlight = activeFutures.get(job.getId());
        if (inFlight != null && !inFlight.isDone()) {
            if (maxWaitMs > 0L) {
                try {
                    return inFlight.get(maxWaitMs, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                }
            }
            return getJob(admin, jobId);
        }

        requiresNewTx.executeWithoutResult(status -> {
            BulkExerciseGenerationJob locked = jobRepository.findById(jobId).orElseThrow();
            locked.setErrorMessage(null);
            if (locked.getStatus() != BulkExerciseGenerationStatus.COMPLETED) {
                locked.setStatus(BulkExerciseGenerationStatus.PENDING);
            }
            jobRepository.save(locked);
        });

        CompletableFuture<BulkExerciseGenerationJobResponse> future = scheduleJobExecution(jobId, maxBatchesThisRun);
        if (maxWaitMs > 0L) {
            try {
                return future.get(maxWaitMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        }
        return getJob(admin, jobId);
    }

    public BulkExerciseGenerationJobResponse executeWithBatchCapForRecoveryTest(
            User admin,
            BulkExerciseGenerationRequest request,
            int maxBatchesFirstRun
    ) {
        String promptText = (request.getPrompt() != null && !request.getPrompt().isBlank())
                ? request.getPrompt().trim()
                : buildDefaultPrompt(request);
        ParsedBulkSpec spec = parseSpecification(promptText, request);

        BulkExerciseGenerationJob created = requiresNewTx.execute(status -> {
            int estimatedBatches = Math.max(1, (int) Math.ceil(spec.requestedQuantity() / (double) spec.batchSize()));
            String catFilter = spec.categories().isEmpty()
                    ? null
                    : String.join(",", spec.categories().stream().map(Enum::name).sorted().toList());
            return jobRepository.save(BulkExerciseGenerationJob.builder()
                    .jobUid("BULK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US))
                    .adminUser(admin)
                    .idempotencyKey(request.getIdempotencyKey())
                    .conversationId(request.getConversationId())
                    .prompt(promptText)
                    .trainingTypeFilter(spec.trainingType().name())
                    .categoriesFilter(catFilter)
                    .requestedQuantity(spec.requestedQuantity())
                    .batchSize(spec.batchSize())
                    .totalBatchesEstimated(estimatedBatches)
                    .status(BulkExerciseGenerationStatus.PENDING)
                    .build());
        });

        processJobInternal(created.getId(), maxBatchesFirstRun);
        return getJob(admin, created.getId());
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public BulkExerciseGenerationJobResponse getJob(User admin, Long jobId) {
        BulkExerciseGenerationJob job = jobRepository.findByIdAndAdminUserId(jobId, admin.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk generation job not found"));
        log.debug("getJob({}, {}) loaded status={}, saved={}, summaryPosted={}",
                admin.getId(), jobId, job.getStatus(), job.getSavedCount(), job.isSummaryPosted());
        return toJobResponse(job);
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public AdminPageResponse<BulkExerciseGenerationJobResponse> listJobs(User admin, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        return AdminPageResponse.from(
                jobRepository.findByAdminUserIdOrderByCreatedAtDesc(admin.getId(), PageRequest.of(safePage, safeSize))
                        .map(this::toJobResponse)
        );
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public AdminPageResponse<BulkExerciseGenerationItemResponse> getJobItems(
            User admin,
            Long jobId,
            BulkExerciseItemOutcome outcomeFilter,
            int page,
            int size
    ) {
        jobRepository.findByIdAndAdminUserId(jobId, admin.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk generation job not found"));
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize);
        if (outcomeFilter != null) {
            return AdminPageResponse.from(
                    itemRepository.findByJobIdAndOutcomeOrderByIdAsc(jobId, outcomeFilter, pageable)
                            .map(this::toItemResponse)
            );
        }
        return AdminPageResponse.from(
                itemRepository.findByJobIdOrderByIdAsc(jobId, pageable)
                        .map(this::toItemResponse)
        );
    }

    public String buildCompleteUntruncatedJobSummary(Long jobId) {
        BulkExerciseGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bulk generation job not found"));
        List<BulkExerciseGenerationItem> savedItems = itemRepository
                .findByJobIdAndOutcomeOrderBySequenceNumberAscIdAsc(jobId, BulkExerciseItemOutcome.SAVED);
        List<BulkExerciseGenerationItem> skippedItems = itemRepository
                .findByJobIdAndOutcomeOrderBySequenceNumberAscIdAsc(jobId, BulkExerciseItemOutcome.DUPLICATE_SKIPPED);

        StringBuilder sb = new StringBuilder();
        sb.append("### Bulk Exercise Catalog Generation Summary (`").append(job.getJobUid()).append("`)\n\n");
        sb.append("- **Status**: `").append(job.getStatus()).append("`\n");
        sb.append("- **Discipline**: `").append(job.getTrainingTypeFilter()).append("`");
        if (job.getCategoriesFilter() != null && !job.getCategoriesFilter().isBlank()) {
            sb.append(" · **Categories**: `").append(job.getCategoriesFilter()).append("`");
        }
        sb.append("\n");
        sb.append("- **Requested**: **").append(job.getRequestedQuantity()).append("**")
                .append(" · **Saved to Draft**: **").append(job.getSavedCount()).append("**")
                .append(" · **Duplicates Skipped**: **").append(job.getDuplicateCount()).append("**")
                .append(" · **Failed**: **").append(job.getFailedCount()).append("**\n");
        sb.append("- **Publication State**: All **").append(job.getSavedCount())
                .append("** saved exercises were created as `status = DRAFT`, `origin = AI_RECOMMENDED`, `active = false` (hidden from public athletes until Admin review/publish).\n");

        if (job.getSavedCount() < job.getRequestedQuantity()) {
            sb.append("- **Shortfall Note**: ").append(job.getErrorMessage() != null && !job.getErrorMessage().isBlank()
                    ? job.getErrorMessage()
                    : "Candidate pool exhausted before reaching target count after skipping duplicates.").append("\n");
        }

        if (!skippedItems.isEmpty()) {
            sb.append("\n#### Skipped Duplicate / Review Candidates (").append(skippedItems.size()).append(")\n");
            int previewCount = Math.min(20, skippedItems.size());
            for (int i = 0; i < previewCount; i++) {
                BulkExerciseGenerationItem skip = skippedItems.get(i);
                sb.append("- **").append(skip.getExerciseName()).append("**: ")
                        .append(skip.getReason() != null ? skip.getReason() : "Duplicate detected")
                        .append("\n");
            }
            if (skippedItems.size() > previewCount) {
                sb.append("- ... and **").append(skippedItems.size() - previewCount)
                        .append("** more skipped duplicates (open the Job Inspector to page through all ").append(skippedItems.size()).append(" skipped items).\n");
            }
        }

        if (savedItems.isEmpty()) {
            sb.append("\nNo new unique exercises were added to draft because all candidates already existed in the catalog or failed validation.\n");
        } else {
            sb.append("\n#### Complete List of Saved Draft Exercises (1..").append(savedItems.size()).append(")\n\n");
            for (int idx = 0; idx < savedItems.size(); idx++) {
                BulkExerciseGenerationItem item = savedItems.get(idx);
                int num = item.getSequenceNumber() != null ? item.getSequenceNumber() : (idx + 1);
                sb.append(num).append(". **").append(item.getExerciseName()).append("**")
                        .append(" (ID #").append(item.getExerciseId())
                        .append(" · `").append(item.getExerciseCode()).append("`")
                        .append(" · ").append(item.getTrainingType())
                        .append(" · ").append(item.getCategory())
                        .append(" · ").append(item.getDifficulty())
                        .append(")\n");
            }
        }

        return sb.toString();
    }

    private CompletableFuture<BulkExerciseGenerationJobResponse> scheduleJobExecution(
            Long jobId,
            Integer maxBatchesThisRun
    ) {
        CompletableFuture<BulkExerciseGenerationJobResponse> future = new CompletableFuture<>();
        activeFutures.put(jobId, future);
        taskExecutor.execute(() -> {
            try {
                BulkExerciseGenerationJobResponse result = processJobInternal(jobId, maxBatchesThisRun);
                future.complete(result);
            } catch (Throwable ex) {
                log.error("Bulk exercise generation job {} crashed unexpectedly: {}", jobId, ex.getMessage(), ex);
                markJobFailedSafe(jobId, ex.getMessage());
                future.complete(jobRepository.findById(jobId).map(this::toJobResponse).orElse(null));
            } finally {
                activeFutures.remove(jobId, future);
            }
        });
        return future;
    }

    private BulkExerciseGenerationJobResponse processJobInternal(Long jobId, Integer maxBatchesThisRun) {
        BulkExerciseGenerationJob jobMeta = requiresNewTx.execute(status -> {
            BulkExerciseGenerationJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(BulkExerciseGenerationStatus.RUNNING);
            if (job.getStartedAt() == null) {
                job.setStartedAt(ZonedDateTime.now());
            }
            return jobRepository.save(job);
        });

        ExerciseDuplicateDetector.CatalogSnapshot snapshot = duplicateDetector.loadCatalogSnapshot();
        Set<String> reservedCodes = new HashSet<>();
        exerciseRepository.findAll().forEach(ex -> {
            if (ex.getCode() != null) {
                reservedCodes.add(ex.getCode());
            }
        });

        Set<String> alreadyEvaluatedNames = new HashSet<>();
        for (BulkExerciseGenerationItem item : itemRepository.findByJobIdOrderByIdAsc(jobId, PageRequest.of(0, 5000)).getContent()) {
            alreadyEvaluatedNames.add(ExerciseDuplicateDetector.normalizeName(item.getExerciseName()));
        }

        ExerciseTrainingType trainingType = parseTrainingType(jobMeta.getTrainingTypeFilter());
        Set<ExerciseCategory> categories = parseCategoriesCsv(jobMeta.getCategoriesFilter());
        int batchSize = Math.max(MIN_BATCH_SIZE, Math.min(MAX_BATCH_SIZE, jobMeta.getBatchSize()));

        List<CandidateSpec> candidatePool = buildInterleavedCuratedCandidates(
                trainingType, categories, jobMeta.getPrompt(), alreadyEvaluatedNames
        );

        int poolCursor = 0;
        int batchesRunNow = 0;
        int failedBatches = 0;
        boolean interruptedByCap = false;
        int geminiBatchesUsed = 0;

        while (true) {
            BulkExerciseGenerationJob current = jobRepository.findById(jobId).orElseThrow();
            if (current.getSavedCount() >= current.getRequestedQuantity()) {
                break;
            }
            if (maxBatchesThisRun != null && batchesRunNow >= maxBatchesThisRun) {
                interruptedByCap = true;
                break;
            }

            List<CandidateSpec> nextChunk = new ArrayList<>();
            int remainingNeeded = current.getRequestedQuantity() - current.getSavedCount();
            // Pull slightly over batchSize only when duplicates might occur, bounded cleanly
            int pullBudget = Math.min(batchSize, Math.max(remainingNeeded, 1));
            while (nextChunk.size() < pullBudget && poolCursor < candidatePool.size()) {
                CandidateSpec cand = candidatePool.get(poolCursor++);
                String norm = ExerciseDuplicateDetector.normalizeName(cand.name());
                if (alreadyEvaluatedNames.add(norm)) {
                    nextChunk.add(cand);
                }
            }

            if (nextChunk.isEmpty() && geminiApiClient.isConfigured()
                    && geminiBatchesUsed < MAX_GEMINI_AUGMENTATION_BATCHES) {
                geminiBatchesUsed++;
                int askCount = Math.min(batchSize, Math.max(remainingNeeded, 10));
                List<CandidateSpec> aiBatch = fetchGeminiAugmentationBatch(
                        jobMeta.getPrompt(), trainingType, categories, askCount, snapshot, alreadyEvaluatedNames
                );
                for (CandidateSpec aiCand : aiBatch) {
                    String norm = ExerciseDuplicateDetector.normalizeName(aiCand.name());
                    if (alreadyEvaluatedNames.add(norm)) {
                        nextChunk.add(aiCand);
                    }
                }
            }

            if (nextChunk.isEmpty()) {
                break;
            }

            batchesRunNow++;
            final int batchNumber = current.getProcessedBatches() + 1;
            try {
                executeBatchTransaction(jobId, batchNumber, nextChunk, snapshot, reservedCodes);
            } catch (Exception batchEx) {
                failedBatches++;
                log.warn("Bulk job {} batch {} failed without rolling back earlier commits: {}",
                        jobId, batchNumber, batchEx.getMessage());
                final String errMsg = "Batch " + batchNumber + " encountered an error: " + batchEx.getMessage();
                requiresNewTx.executeWithoutResult(status -> {
                    BulkExerciseGenerationJob j = jobRepository.findById(jobId).orElseThrow();
                    j.setProcessedBatches(j.getProcessedBatches() + 1);
                    j.setFailedCount(j.getFailedCount() + nextChunk.size());
                    j.setErrorMessage(errMsg);
                    jobRepository.save(j);
                });
            }
        }

        final boolean wasCapped = interruptedByCap;
        final int totalFailedBatches = failedBatches;
        BulkExerciseGenerationJob finalized = requiresNewTx.execute(status -> {
            BulkExerciseGenerationJob finalJob = jobRepository.findById(jobId).orElseThrow();
            if (wasCapped && finalJob.getSavedCount() < finalJob.getRequestedQuantity()) {
                finalJob.setStatus(BulkExerciseGenerationStatus.PARTIAL);
                finalJob.setErrorMessage("Job paused/interrupted after " + finalJob.getProcessedBatches()
                        + " batch(es) with " + finalJob.getSavedCount() + " exercises saved. Resume to finish.");
            } else if (finalJob.getSavedCount() == 0 && totalFailedBatches > 0) {
                finalJob.setStatus(BulkExerciseGenerationStatus.FAILED);
                finalJob.setCompletedAt(ZonedDateTime.now());
            } else if (totalFailedBatches > 0) {
                finalJob.setStatus(BulkExerciseGenerationStatus.PARTIAL);
                finalJob.setCompletedAt(ZonedDateTime.now());
            } else {
                finalJob.setStatus(BulkExerciseGenerationStatus.COMPLETED);
                finalJob.setCompletedAt(ZonedDateTime.now());
                if (finalJob.getSavedCount() < finalJob.getRequestedQuantity()) {
                    finalJob.setErrorMessage(String.format(
                            "Saved %d of %d requested unique exercises; %d duplicates skipped and no additional unique non-duplicate candidates remained.",
                            finalJob.getSavedCount(), finalJob.getRequestedQuantity(), finalJob.getDuplicateCount()
                    ));
                }
            }
            BulkExerciseGenerationJob savedJob = jobRepository.save(finalJob);
            auditLogRepository.save(AdminAiAuditLog.builder()
                    .adminUser(savedJob.getAdminUser())
                    .action("BULK_GENERATE_EXERCISES")
                    .targetType("BULK_GENERATION_JOB")
                    .targetId(savedJob.getId())
                    .details(String.format("uid=%s status=%s requested=%d saved=%d duplicates=%d failed=%d",
                            savedJob.getJobUid(), savedJob.getStatus(), savedJob.getRequestedQuantity(),
                            savedJob.getSavedCount(), savedJob.getDuplicateCount(), savedJob.getFailedCount()))
                    .build());
            return savedJob;
        });

        if (!wasCapped && finalized.getConversationId() != null && !finalized.isSummaryPosted()) {
            postSummaryToConversation(finalized.getId());
        }

        return requiresNewTx.execute(status -> jobRepository.findById(jobId)
                .map(this::toJobResponse)
                .orElse(toJobResponse(finalized)));
    }

    private void executeBatchTransaction(
            Long jobId,
            int batchNumber,
            List<CandidateSpec> batchCandidates,
            ExerciseDuplicateDetector.CatalogSnapshot snapshot,
            Set<String> reservedCodes
    ) {
        requiresNewTx.executeWithoutResult(status -> {
            BulkExerciseGenerationJob job = jobRepository.findById(jobId).orElseThrow();
            int nextSeq = itemRepository.findMaxSavedSequenceNumber(jobId);

            int batchGenerated = 0;
            int batchSaved = 0;
            int batchDuplicates = 0;
            int batchFailed = 0;

            for (CandidateSpec cand : batchCandidates) {
                if (job.getSavedCount() + batchSaved >= job.getRequestedQuantity()) {
                    break;
                }
                batchGenerated++;
                String cleanName = sanitizeName(cand.name());
                ExerciseCategory cat = cand.category() != null ? cand.category() : ExerciseCategory.SKILL;
                ExerciseTrainingType tType = resolveTrainingTypeForSave(job, cand.trainingType(), cat);
                DifficultyLevel diff = cand.difficulty() != null ? cand.difficulty() : DifficultyLevel.INTERMEDIATE;
                String baseSlug = ExerciseDraftValidationService.normalizeSlug(cleanName);

                ExerciseDuplicateDetector.DuplicateCheckResult dupCheck = duplicateDetector.checkCandidate(
                        snapshot, cleanName, baseSlug, cand.aliases(), cat, tType
                );
                if (dupCheck.duplicateOrUncertain()) {
                    batchDuplicates++;
                    itemRepository.save(BulkExerciseGenerationItem.builder()
                            .job(job)
                            .exerciseName(truncate(cleanName, 120))
                            .exerciseCode(truncate(baseSlug, 64))
                            .trainingType(tType.name())
                            .category(cat.name())
                            .difficulty(diff.name())
                            .outcome(BulkExerciseItemOutcome.DUPLICATE_SKIPPED)
                            .reason(dupCheck.reason())
                            .batchNumber(batchNumber)
                            .build());
                    continue;
                }

                String uniqueCode = ensureUniqueCode(baseSlug, reservedCodes);
                try {
                    Exercise entity = Exercise.builder()
                            .name(cleanName)
                            .code(uniqueCode)
                            .category(cat)
                            .trainingType(tType)
                            .difficulty(diff)
                            .description(nonBlankOr(cand.description(), cleanName + " (" + cat + ")"))
                            .targetMuscles(nonBlankOr(cand.targetMuscles(), "Full-Body"))
                            .secondaryMuscles(cand.secondaryMuscles())
                            .exerciseType(nonBlankOr(cand.exerciseType(), "COMPOUND"))
                            .recommendedSets(cand.recommendedSets() != null ? cand.recommendedSets() : 3)
                            .recommendedReps(nonBlankOr(cand.recommendedReps(), "8-12"))
                            .recommendedRestSeconds(cand.recommendedRestSeconds() != null ? cand.recommendedRestSeconds() : 60)
                            .commonMistakes(cand.commonMistakes())
                            .safetyConsiderations(cand.safetyConsiderations())
                            .progressionNotes(cand.progressionNotes())
                            .aliases(cand.aliases())
                            .instructions(nonBlankOr(cand.instructions(), "Perform " + cleanName + " with controlled tempo and strict alignment."))
                            .equipment(nonBlankOr(cand.equipment(), tType == ExerciseTrainingType.GYM ? "Gym Equipment" : "Bodyweight / Floor"))
                            .active(false)
                            .status(ExerciseStatus.DRAFT)
                            .origin(ExerciseOrigin.AI_RECOMMENDED)
                            .build();

                    Exercise saved = exerciseRepository.saveAndFlush(entity);
                    nextSeq++;
                    batchSaved++;

                    snapshot.register(saved.getId(), saved.getName(), saved.getCode(),
                            saved.getCategory(), saved.getTrainingType(), saved.getAliases());

                    saveRecommendationAudit(job, saved);

                    itemRepository.save(BulkExerciseGenerationItem.builder()
                            .job(job)
                            .sequenceNumber(nextSeq)
                            .exerciseId(saved.getId())
                            .exerciseName(truncate(saved.getName(), 120))
                            .exerciseCode(saved.getCode())
                            .trainingType(saved.getTrainingType().name())
                            .category(saved.getCategory().name())
                            .difficulty(saved.getDifficulty().name())
                            .outcome(BulkExerciseItemOutcome.SAVED)
                            .reason("Persisted to DRAFT + AI_RECOMMENDED")
                            .batchNumber(batchNumber)
                            .build());
                } catch (DataIntegrityViolationException ex) {
                    batchDuplicates++;
                    itemRepository.save(BulkExerciseGenerationItem.builder()
                            .job(job)
                            .exerciseName(truncate(cleanName, 120))
                            .exerciseCode(truncate(uniqueCode, 64))
                            .trainingType(tType.name())
                            .category(cat.name())
                            .difficulty(diff.name())
                            .outcome(BulkExerciseItemOutcome.DUPLICATE_SKIPPED)
                            .reason("Database uniqueness constraint collision prevented duplicate: " + cleanName)
                            .batchNumber(batchNumber)
                            .build());
                } catch (Exception ex) {
                    batchFailed++;
                    itemRepository.save(BulkExerciseGenerationItem.builder()
                            .job(job)
                            .exerciseName(truncate(cleanName, 120))
                            .exerciseCode(truncate(uniqueCode, 64))
                            .trainingType(tType.name())
                            .category(cat.name())
                            .difficulty(diff.name())
                            .outcome(BulkExerciseItemOutcome.FAILED)
                            .reason(ex.getMessage())
                            .batchNumber(batchNumber)
                            .build());
                }
            }

            job.setProcessedBatches(batchNumber);
            job.setGeneratedCount(job.getGeneratedCount() + batchGenerated);
            job.setSavedCount(job.getSavedCount() + batchSaved);
            job.setDuplicateCount(job.getDuplicateCount() + batchDuplicates);
            job.setFailedCount(job.getFailedCount() + batchFailed);
            jobRepository.save(job);
        });
    }

    private void saveRecommendationAudit(BulkExerciseGenerationJob job, Exercise saved) {
        try {
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "name", saved.getName(),
                    "code", saved.getCode(),
                    "category", saved.getCategory().name(),
                    "trainingType", saved.getTrainingType().name(),
                    "difficulty", saved.getDifficulty().name()
            ));
            AdminAiConversation conv = job.getConversationId() != null
                    ? conversationService.findOwnedConversation(job.getAdminUser().getId(), job.getConversationId()).orElse(null)
                    : null;
            recommendationRepository.save(AdminAiRecommendation.builder()
                    .recommendationType(AdminRecommendationType.EXERCISE)
                    .status(AdminRecommendationStatus.APPROVED)
                    .priority(AdminRecommendationPriority.MEDIUM)
                    .title("AI Bulk Draft Exercise: " + saved.getName())
                    .summary(truncate(saved.getDescription(), 500))
                    .rationale("Generated by Bulk Job " + job.getJobUid())
                    .payloadJson(payloadJson)
                    .conversation(conv)
                    .createdByAdmin(job.getAdminUser())
                    .reviewedByAdmin(job.getAdminUser())
                    .reviewedAt(ZonedDateTime.now())
                    .reviewNotes("BULK_JOB:" + job.getJobUid())
                    .createdResourceId(saved.getId())
                    .createdResourceType("EXERCISE")
                    .build());
        } catch (Exception ex) {
            log.debug("Non-fatal error recording recommendation audit for exercise {}: {}", saved.getId(), ex.getMessage());
        }
    }

    private void postSummaryToConversation(Long jobId) {
        requiresNewTx.executeWithoutResult(status -> {
            BulkExerciseGenerationJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getConversationId() == null || job.isSummaryPosted()) {
                return;
            }
            Optional<AdminAiConversation> convOpt = conversationService
                    .findOwnedConversation(job.getAdminUser().getId(), job.getConversationId());
            if (convOpt.isEmpty()) {
                return;
            }
            String fullSummary = buildCompleteUntruncatedJobSummary(jobId);
            String metaJson = null;
            try {
                metaJson = objectMapper.writeValueAsString(Map.of(
                        "intent", AdminAiIntent.BULK_EXERCISE_GENERATION.name(),
                        "bulkJobId", job.getId(),
                        "bulkJobUid", job.getJobUid(),
                        "savedCount", job.getSavedCount(),
                        "duplicateCount", job.getDuplicateCount()
                ));
            } catch (Exception ignored) {
            }
            conversationService.saveMessage(
                    convOpt.get(),
                    "assistant",
                    fullSummary,
                    AdminAiIntent.BULK_EXERCISE_GENERATION.name(),
                    metaJson
            );
            job.setSummaryPosted(true);
            jobRepository.save(job);
        });
    }

    private void markJobFailedSafe(Long jobId, String errorMsg) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                BulkExerciseGenerationJob job = jobRepository.findById(jobId).orElse(null);
                if (job != null) {
                    job.setStatus(job.getSavedCount() > 0
                            ? BulkExerciseGenerationStatus.PARTIAL
                            : BulkExerciseGenerationStatus.FAILED);
                    job.setErrorMessage(errorMsg != null ? errorMsg : "Unexpected processing failure");
                    job.setCompletedAt(ZonedDateTime.now());
                    jobRepository.save(job);
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to persist failure status for bulk job {}: {}", jobId, ex.getMessage());
        }
    }

    private List<CandidateSpec> buildInterleavedCuratedCandidates(
            ExerciseTrainingType trainingType,
            Set<ExerciseCategory> categories,
            String prompt,
            Set<String> alreadyEvaluatedNames
    ) {
        String focus = extractFocusKeyword(prompt);
        List<BulkExerciseCatalogLibrary.BulkExerciseTemplate> matched =
                catalogLibrary.selectCandidates(trainingType, categories, focus);

        // Interleave trainingType+category pairs round-robin so mixed/BOTH requests stay evenly balanced across disciplines and categories
        Map<String, Deque<CandidateSpec>> buckets = new LinkedHashMap<>();
        for (BulkExerciseCatalogLibrary.BulkExerciseTemplate tpl : matched) {
            String norm = ExerciseDuplicateDetector.normalizeName(tpl.name());
            if (alreadyEvaluatedNames.contains(norm)) {
                continue;
            }
            CandidateSpec cand = new CandidateSpec(
                    tpl.name(),
                    tpl.category(),
                    tpl.trainingType(),
                    tpl.difficulty(),
                    tpl.exerciseType(),
                    tpl.targetMuscles(),
                    tpl.secondaryMuscles(),
                    tpl.equipment(),
                    tpl.recommendedSets(),
                    tpl.recommendedReps(),
                    tpl.recommendedRestSeconds(),
                    tpl.buildDescription(),
                    tpl.buildInstructions(),
                    tpl.buildCommonMistakes(),
                    tpl.buildSafetyConsiderations(),
                    tpl.buildProgressionNotes(),
                    tpl.aliases()
            );
            String bucketKey = cand.trainingType().name() + ":" + cand.category().name();
            buckets.computeIfAbsent(bucketKey, k -> new ArrayDeque<>()).addLast(cand);
        }

        List<CandidateSpec> ordered = new ArrayList<>(matched.size());
        boolean added = true;
        while (added) {
            added = false;
            for (Deque<CandidateSpec> queue : buckets.values()) {
                if (!queue.isEmpty()) {
                    ordered.add(queue.pollFirst());
                    added = true;
                }
            }
        }
        return ordered;
    }

    private List<CandidateSpec> fetchGeminiAugmentationBatch(
            String prompt,
            ExerciseTrainingType trainingType,
            Set<ExerciseCategory> categories,
            int count,
            ExerciseDuplicateDetector.CatalogSnapshot snapshot,
            Set<String> alreadyEvaluatedNames
    ) {
        try {
            String sysPrompt = "You are a master Calisthenics & Strength Coach designing unique non-duplicate exercises. " +
                    "Return JSON {exercises:[...]} with genuine anatomically sound exercises distinct from common variations already listed. " +
                    "Every name <= 90 chars, specific muscles, secondary muscles, equipment, sets, reps, rest seconds, mistakes, safety, progression notes, and aliases.";
            String userMsg = "Prompt: " + prompt +
                    "\nDiscipline filter: " + trainingType +
                    "\nCategory filter: " + (categories.isEmpty() ? "ANY" : categories) +
                    "\nGenerate up to " + count + " completely NEW unique exercises not duplicating: " +
                    String.join(", ", alreadyEvaluatedNames.stream().limit(80).toList()) +
                    " (existing catalog size=" + snapshot.size() + ").";
            Optional<GeminiBulkExerciseBatchOutput> out = geminiApiClient.generateBulkExerciseBatch(sysPrompt, userMsg);
            if (out.isEmpty() || out.get().getExercises() == null) {
                return List.of();
            }
            List<CandidateSpec> list = new ArrayList<>();
            for (GeminiBulkExerciseBatchOutput.BulkGeneratedCandidateDto dto : out.get().getExercises()) {
                if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
                    continue;
                }
                ExerciseCategory parsedCat = ExerciseDraftValidationService.parseCategoryEnum(dto.getCategory());
                if (parsedCat == null) {
                    parsedCat = !categories.isEmpty() ? categories.iterator().next() : ExerciseCategory.SKILL;
                } else if (!categories.isEmpty() && !categories.contains(parsedCat)) {
                    parsedCat = categories.iterator().next();
                }
                ExerciseTrainingType parsedType = parseTrainingType(dto.getTrainingType());
                if (trainingType != ExerciseTrainingType.BOTH) {
                    parsedType = trainingType;
                }
                DifficultyLevel parsedDiff = ExerciseDraftValidationService.parseDifficultyEnum(dto.getDifficulty());
                list.add(new CandidateSpec(
                        dto.getName().trim(),
                        parsedCat,
                        parsedType,
                        parsedDiff != null ? parsedDiff : DifficultyLevel.INTERMEDIATE,
                        nonBlankOr(dto.getExerciseType(), "COMPOUND"),
                        nonBlankOr(dto.getTargetMuscles(), "Primary Movers"),
                        dto.getSecondaryMuscles(),
                        nonBlankOr(dto.getEquipment(), parsedType == ExerciseTrainingType.GYM ? "Gym Equipment" : "Bodyweight"),
                        dto.getRecommendedSets() != null ? dto.getRecommendedSets() : 3,
                        nonBlankOr(dto.getRecommendedReps(), "8-12"),
                        dto.getRecommendedRestSeconds() != null ? dto.getRecommendedRestSeconds() : 60,
                        nonBlankOr(dto.getDescription(), dto.getName().trim() + " (" + parsedCat + ")"),
                        nonBlankOr(dto.getInstructions(), "Perform " + dto.getName().trim() + " cleanly through full range of motion."),
                        dto.getCommonMistakes(),
                        dto.getSafetyConsiderations(),
                        dto.getProgressionNotes(),
                        dto.getAliases()
                ));
            }
            return list;
        } catch (Exception ex) {
            log.warn("Optional Gemini bulk augmentation batch failed cleanly: {}", ex.getMessage());
            return List.of();
        }
    }

    private BulkExerciseGenerationJobResponse toJobResponse(BulkExerciseGenerationJob job) {
        int requested = Math.max(1, job.getRequestedQuantity());
        int progress = job.getStatus() == BulkExerciseGenerationStatus.COMPLETED
                ? 100
                : Math.min(99, (int) Math.round((job.getSavedCount() * 100.0) / requested));
        boolean canResume = (job.getStatus() == BulkExerciseGenerationStatus.FAILED
                || job.getStatus() == BulkExerciseGenerationStatus.PARTIAL
                || job.getStatus() == BulkExerciseGenerationStatus.PENDING
                || (job.getStatus() == BulkExerciseGenerationStatus.RUNNING && !activeFutures.containsKey(job.getId())))
                && job.getSavedCount() < job.getRequestedQuantity();

        List<String> skippedSamples = itemRepository
                .findByJobIdAndOutcomeOrderByIdAsc(job.getId(), BulkExerciseItemOutcome.DUPLICATE_SKIPPED, PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(i -> i.getExerciseName() + (i.getReason() != null ? " — " + i.getReason() : ""))
                .toList();

        String statusMsg = switch (job.getStatus()) {
            case PENDING -> "Queued bulk generation for " + job.getRequestedQuantity() + " exercises...";
            case RUNNING -> String.format("Generating batch %d/%d (%d/%d saved, %d duplicates skipped)...",
                    Math.max(1, job.getProcessedBatches()), Math.max(1, job.getTotalBatchesEstimated()),
                    job.getSavedCount(), job.getRequestedQuantity(), job.getDuplicateCount());
            case COMPLETED -> String.format("Completed: %d unique exercises saved to DRAFT (%d duplicates skipped).",
                    job.getSavedCount(), job.getDuplicateCount());
            case PARTIAL -> String.format("Partial completion: %d of %d saved (%d duplicates skipped). Ready to resume.",
                    job.getSavedCount(), job.getRequestedQuantity(), job.getDuplicateCount());
            case FAILED -> "Generation failed" + (job.getErrorMessage() != null ? ": " + job.getErrorMessage() : ".");
            case CANCELLED -> "Cancelled by administrator.";
        };

        return BulkExerciseGenerationJobResponse.builder()
                .id(job.getId())
                .jobUid(job.getJobUid())
                .conversationId(job.getConversationId())
                .prompt(job.getPrompt())
                .trainingTypeFilter(job.getTrainingTypeFilter())
                .categoriesFilter(job.getCategoriesFilter())
                .requestedQuantity(job.getRequestedQuantity())
                .batchSize(job.getBatchSize())
                .generatedCount(job.getGeneratedCount())
                .duplicateCount(job.getDuplicateCount())
                .savedCount(job.getSavedCount())
                .failedCount(job.getFailedCount())
                .processedBatches(job.getProcessedBatches())
                .totalBatchesEstimated(job.getTotalBatchesEstimated())
                .progressPercent(progress)
                .status(job.getStatus().name())
                .errorMessage(job.getErrorMessage())
                .summaryPosted(job.isSummaryPosted())
                .canResume(canResume)
                .statusMessage(statusMsg)
                .skippedDuplicateSamples(skippedSamples)
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private BulkExerciseGenerationItemResponse toItemResponse(BulkExerciseGenerationItem item) {
        return BulkExerciseGenerationItemResponse.builder()
                .id(item.getId())
                .sequenceNumber(item.getSequenceNumber())
                .exerciseId(item.getExerciseId())
                .exerciseName(item.getExerciseName())
                .exerciseCode(item.getExerciseCode())
                .trainingType(item.getTrainingType())
                .category(item.getCategory())
                .difficulty(item.getDifficulty())
                .outcome(item.getOutcome().name())
                .reason(item.getReason())
                .batchNumber(item.getBatchNumber())
                .createdAt(item.getCreatedAt())
                .build();
    }

    private ExerciseTrainingType parseTrainingType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ExerciseTrainingType.BOTH;
        }
        try {
            return ExerciseTrainingType.valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return ExerciseTrainingType.BOTH;
        }
    }

    private ExerciseTrainingType resolveTrainingTypeForSave(
            BulkExerciseGenerationJob job,
            ExerciseTrainingType candType,
            ExerciseCategory category
    ) {
        ExerciseTrainingType jobFilter = parseTrainingType(job.getTrainingTypeFilter());
        if (jobFilter == ExerciseTrainingType.CALISTHENICS) {
            return ExerciseTrainingType.CALISTHENICS;
        }
        if (jobFilter == ExerciseTrainingType.GYM) {
            return ExerciseTrainingType.GYM;
        }
        if (candType != null) {
            return candType;
        }
        return switch (category) {
            case CHEST, BACK, SHOULDERS, BICEPS, TRICEPS -> ExerciseTrainingType.GYM;
            default -> ExerciseTrainingType.CALISTHENICS;
        };
    }

    private Set<ExerciseCategory> parseCategoriesCsv(String csv) {
        Set<ExerciseCategory> set = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return set;
        }
        for (String token : csv.split(",")) {
            ExerciseCategory c = ExerciseDraftValidationService.parseCategoryEnum(token);
            if (c != null) {
                set.add(c);
            }
        }
        return set;
    }

    private String ensureUniqueCode(String baseCode, Set<String> reservedCodes) {
        String clean = (baseCode == null || baseCode.isBlank()) ? "AI_EXERCISE" : baseCode;
        if (clean.length() > 50) {
            clean = clean.substring(0, 50).replaceAll("_+$", "");
        }
        String candidate = clean;
        int suffix = 2;
        while (reservedCodes.contains(candidate) || exerciseRepository.existsByCode(candidate)) {
            String suffixStr = "_" + suffix++;
            int maxLen = Math.max(1, 50 - suffixStr.length());
            String base = clean.length() > maxLen ? clean.substring(0, maxLen).replaceAll("_+$", "") : clean;
            candidate = base + suffixStr;
        }
        reservedCodes.add(candidate);
        return candidate;
    }

    private String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Untitled AI Exercise";
        }
        String clean = raw.trim().replaceAll("\\s+", " ");
        return clean.length() > 100 ? clean.substring(0, 100).trim() : clean;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String nonBlankOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v.trim() : fallback;
    }

    private String extractFocusKeyword(String prompt) {
        if (prompt == null) return null;
        String lower = prompt.toLowerCase(Locale.US);
        if (lower.contains("hamstring")) return "hamstring";
        if (lower.contains("quad")) return "quad";
        if (lower.contains("glute")) return "glute";
        if (lower.contains("planche")) return "planche";
        if (lower.contains("handstand")) return "handstand";
        if (lower.contains("lever")) return "lever";
        return null;
    }

    private String buildDefaultPrompt(BulkExerciseGenerationRequest request) {
        int qty = request.getQuantity() != null ? request.getQuantity() : 50;
        String type = request.getTrainingType() != null ? request.getTrainingType().name().toLowerCase(Locale.US) : "mixed";
        String cats = (request.getCategories() != null && !request.getCategories().isEmpty())
                ? String.join(", ", request.getCategories().stream().map(Enum::name).toList())
                : "all categories";
        return "Generate " + qty + " " + type + " exercises (" + cats + ")";
    }
}
