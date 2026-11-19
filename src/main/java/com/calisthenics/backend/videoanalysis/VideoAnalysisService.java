package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.analysis.AiServiceClient;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VideoAnalysisService {

    private static final String SCHEMA_VERSION_V1 = "1.0";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Set<String> FAILURE_STATUSES = Set.of(
            "NO_PERSON_DETECTED",
            "INSUFFICIENT_POSE_QUALITY",
            "INVALID_INPUT",
            "FAILED"
    );

    private static final Map<String, String> SUPPORTED_EXERCISE_ALIASES = Map.ofEntries(
            Map.entry("SQUAT", "SQUAT"),
            Map.entry("SQUATS", "SQUAT"),
            Map.entry("BODYWEIGHT_SQUAT", "SQUAT"),
            Map.entry("AIR_SQUAT", "SQUAT"),
            Map.entry("PUSH_UP", "PUSH_UP"),
            Map.entry("PUSHUP", "PUSH_UP"),
            Map.entry("PRESS_UP", "PUSH_UP"),
            Map.entry("STANDARD_PUSH_UP", "PUSH_UP"),
            Map.entry("PULL_UP", "PULL_UP"),
            Map.entry("PULLUP", "PULL_UP"),
            Map.entry("CHIN_UP", "PULL_UP"),
            Map.entry("CHINUP", "PULL_UP"),
            Map.entry("DIPS", "DIPS"),
            Map.entry("DIP", "DIPS"),
            Map.entry("PARALLEL_BAR_DIPS", "DIPS"),
            Map.entry("PLANCHE", "PLANCHE"),
            Map.entry("FULL_PLANCHE", "FULL_PLANCHE"),
            Map.entry("STRADDLE_PLANCHE", "STRADDLE_PLANCHE"),
            Map.entry("ADV_TUCK_PLANCHE", "ADV_TUCK_PLANCHE"),
            Map.entry("ADVANCED_TUCK_PLANCHE", "ADV_TUCK_PLANCHE"),
            Map.entry("TUCK_PLANCHE", "TUCK_PLANCHE"),
            Map.entry("FRONT_LEVER", "FRONT_LEVER"),
            Map.entry("FULL_FRONT_LEVER", "FULL_FRONT_LEVER"),
            Map.entry("STRADDLE_FRONT_LEVER", "STRADDLE_FRONT_LEVER"),
            Map.entry("ADV_TUCK_FRONT_LEVER", "ADV_TUCK_FRONT_LEVER"),
            Map.entry("ADVANCED_TUCK_FRONT_LEVER", "ADV_TUCK_FRONT_LEVER"),
            Map.entry("TUCK_FRONT_LEVER", "TUCK_FRONT_LEVER"),
            Map.entry("HANDSTAND", "HANDSTAND"),
            Map.entry("HANDSTAND_PUSHUP", "HANDSTAND_PUSHUP"),
            Map.entry("HANDSTAND_PUSH_UP", "HANDSTAND_PUSHUP"),
            Map.entry("HSPU", "HANDSTAND_PUSHUP"),
            Map.entry("PIKE_PUSH_UP", "PIKE_PUSH_UP"),
            Map.entry("PIKE_PUSHUP", "PIKE_PUSH_UP"),
            Map.entry("PIKE_PRESS", "PIKE_PUSH_UP"),
            Map.entry("L_SIT", "L_SIT"),
            Map.entry("LSIT", "L_SIT"),
            Map.entry("L_SIT_HOLD", "L_SIT"),
            Map.entry("TUCK_L_SIT", "L_SIT"),
            Map.entry("MUSCLE_UP", "MUSCLE_UP"),
            Map.entry("MUSCLEUP", "MUSCLE_UP")
    );

    private final VideoAnalysisRepository videoAnalysisRepository;
    private final VideoAnalysisKeypointRepository keypointRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final MinioAnalysisMediaStorageService minioStorageService;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;
    private final VideoAnalysisQuotaService videoAnalysisQuotaService;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    public VideoAnalysisService(
            VideoAnalysisRepository videoAnalysisRepository,
            VideoAnalysisKeypointRepository keypointRepository,
            UserRepository userRepository,
            ExerciseRepository exerciseRepository,
            MinioAnalysisMediaStorageService minioStorageService,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper,
            TaskExecutor taskExecutor
    ) {
        this(
                videoAnalysisRepository,
                keypointRepository,
                userRepository,
                exerciseRepository,
                minioStorageService,
                aiServiceClient,
                objectMapper,
                taskExecutor,
                null
        );
    }

    @Autowired
    public VideoAnalysisService(
            VideoAnalysisRepository videoAnalysisRepository,
            VideoAnalysisKeypointRepository keypointRepository,
            UserRepository userRepository,
            ExerciseRepository exerciseRepository,
            MinioAnalysisMediaStorageService minioStorageService,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper,
            TaskExecutor taskExecutor,
            @Autowired(required = false) VideoAnalysisQuotaService videoAnalysisQuotaService
    ) {
        this.videoAnalysisRepository = videoAnalysisRepository;
        this.keypointRepository = keypointRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.minioStorageService = minioStorageService;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.videoAnalysisQuotaService = videoAnalysisQuotaService;
    }

    public VideoAnalysisResponseDto initiateVideoAnalysis(Long userId, Long exerciseId, MultipartFile file) {
        return initiateVideoAnalysis(userId, exerciseId, null, null, false, file);
    }

    public VideoAnalysisResponseDto initiateVideoAnalysis(
            Long userId,
            Long exerciseId,
            String exerciseCode,
            String idempotencyKey,
            boolean sideView,
            MultipartFile file
    ) {
        validateVideoFile(file);

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey != null) {
            Optional<VideoAnalysis> existing = videoAnalysisRepository
                    .findByUserIdAndIdempotencyKey(userId, normalizedIdempotencyKey);
            if (existing.isPresent()) {
                return buildIdempotentReplayResponse(userId, existing.get());
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        ResolvedExercise resolvedExercise = validateAndResolveExercise(exerciseId, exerciseCode);

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read video file bytes", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read video file.");
        }

        QuotaEntitlementResponseDto reservedQuota = null;
        if (videoAnalysisQuotaService != null) {
            reservedQuota = videoAnalysisQuotaService.reserveVideoAnalysisQuota(userId);
        }

        String originalVideoKey = null;
        VideoAnalysis savedAnalysis;
        try {
            originalVideoKey = minioStorageService.store(file);

            VideoAnalysis analysis = VideoAnalysis.builder()
                    .user(user)
                    .exercise(resolvedExercise.exercise())
                    .schemaVersion(SCHEMA_VERSION_V1)
                    .exerciseCode(resolvedExercise.code())
                    .idempotencyKey(normalizedIdempotencyKey)
                    .originalVideoKey(originalVideoKey)
                    .status("PENDING")
                    .build();

            savedAnalysis = videoAnalysisRepository.save(analysis);
        } catch (DataIntegrityViolationException ex) {
            releaseReservedQuotaSafely(userId, reservedQuota);
            deleteMinioObjectSafely(originalVideoKey);
            if (normalizedIdempotencyKey != null) {
                Optional<VideoAnalysis> raced = videoAnalysisRepository
                        .findByUserIdAndIdempotencyKey(userId, normalizedIdempotencyKey);
                if (raced.isPresent()) {
                    return buildIdempotentReplayResponse(userId, raced.get());
                }
            }
            throw ex;
        } catch (RuntimeException ex) {
            releaseReservedQuotaSafely(userId, reservedQuota);
            deleteMinioObjectSafely(originalVideoKey);
            throw ex;
        }

        String uuidStr = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();
        Long analysisId = savedAnalysis.getId();
        String code = resolvedExercise.code();
        Runnable dispatchTask = () -> taskExecutor.execute(() -> processAnalysisAsync(
                analysisId,
                fileBytes,
                filename,
                code,
                uuidStr,
                sideView
        ));
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask.run();
                }
            });
        } else {
            dispatchTask.run();
        }

        VideoAnalysisResponseDto response = mapToResponse(savedAnalysis);
        response.setQuota(reservedQuota);
        return response;
    }

    public void processAnalysisAsync(Long analysisId, byte[] videoBytes, String filename, String exerciseCode, String uuidStr) {
        processAnalysisAsync(analysisId, videoBytes, filename, exerciseCode, uuidStr, false);
    }

    public void processAnalysisAsync(
            Long analysisId,
            byte[] videoBytes,
            String filename,
            String exerciseCode,
            String uuidStr,
            boolean sideView
    ) {
        log.info("Starting async video scoring for analysisId={} exercise={} sideView={}", analysisId, exerciseCode, sideView);
        try {
            updateStatus(analysisId, "PROCESSING", null);

            AiVideoScoringResponseDto aiResult = aiServiceClient.scoreVideo(
                    videoBytes,
                    filename,
                    exerciseCode,
                    10,
                    uuidStr,
                    sideView
            );

            if (aiResult == null) {
                updateStatus(analysisId, "FAILED", "AI service returned null response.");
                return;
            }

            String aiStatus = aiResult.getStatus() != null ? aiResult.getStatus().trim().toUpperCase(Locale.ROOT) : "";
            if (FAILURE_STATUSES.contains(aiStatus)) {
                recordFailureResult(analysisId, aiStatus, aiResult);
                return;
            }

            String annotatedKey = uuidStr + "_annot.mp4";
            byte[] annotatedBytes = aiServiceClient.downloadAnnotatedVideo(uuidStr);
            if (annotatedBytes != null && annotatedBytes.length > 0) {
                minioStorageService.storeBytes(annotatedBytes, "video/mp4", annotatedKey);
            } else {
                annotatedKey = null;
                log.warn("Annotated video was not retrieved from AI service for analysisId={}", analysisId);
            }

            completeAnalysis(analysisId, aiResult, annotatedKey);

        } catch (Throwable t) {
            log.error("Error during async video analysis processing for id={}", analysisId, t);
            String msg = (t.getMessage() != null && !t.getMessage().isBlank())
                    ? t.getMessage()
                    : (t.getClass().getSimpleName() + ": Unexpected analysis failure");
            persistFailureStatusSafely(analysisId, msg);
            if (t instanceof VirtualMachineError err) {
                throw err;
            }
        }
    }

    @Transactional
    public void completeAnalysis(Long analysisId, AiVideoScoringResponseDto aiResult, String annotatedKey) {
        runInRequiresNewTransaction(() -> completeAnalysisInternal(analysisId, aiResult, annotatedKey));
    }

    private void completeAnalysisInternal(Long analysisId, AiVideoScoringResponseDto aiResult, String annotatedKey) {
        VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) return;

        analysis.setAnnotatedVideoKey(annotatedKey);
        analysis.setOverallScore(aiResult.getScore() != null ? BigDecimal.valueOf(aiResult.getScore()) : null);
        analysis.setFormAccuracy(aiResult.getFormAccuracy() != null ? BigDecimal.valueOf(aiResult.getFormAccuracy()) : null);
        analysis.setStabilityScore(aiResult.getStability() != null ? BigDecimal.valueOf(aiResult.getStability()) : null);
        analysis.setAlignmentScore(aiResult.getAlignment() != null ? BigDecimal.valueOf(aiResult.getAlignment()) : null);
        analysis.setErrorMessage(null);

        applyV1Fields(analysis, aiResult);

        analysis.setStatus("COMPLETED");
        videoAnalysisRepository.save(analysis);

        if (aiResult.getKeypoints() != null && aiResult.getKeypoints().getFrames() != null) {
            List<VideoAnalysisKeypoint> keypointEntities = new ArrayList<>();
            for (AiVideoKeypointFrameDto frame : aiResult.getKeypoints().getFrames()) {
                try {
                    String kpJson = objectMapper.writeValueAsString(frame.getKeypoints());
                    keypointEntities.add(VideoAnalysisKeypoint.builder()
                            .videoAnalysis(analysis)
                            .frameIndex(frame.getFrameIndex() != null ? frame.getFrameIndex() : 0)
                            .timestampMs(frame.getTimestampMs() != null ? frame.getTimestampMs() : 0L)
                            .keypointsJson(kpJson)
                            .build());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize frame keypoints", e);
                }
            }
            keypointRepository.saveAll(keypointEntities);
            log.info("Saved {} keypoint frames for analysisId={}", keypointEntities.size(), analysisId);
        }
    }

    @Transactional
    public void updateStatus(Long analysisId, String status, String errorMessage) {
        runInRequiresNewTransaction(() -> {
            VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
            if (analysis != null) {
                analysis.setStatus(status);
                analysis.setErrorMessage(errorMessage);
                videoAnalysisRepository.save(analysis);
            }
        });
    }

    @Transactional
    public void updateStatusWithFeedback(Long analysisId, String status, String errorMessage, List<String> feedback) {
        runInRequiresNewTransaction(() -> {
            VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
            if (analysis != null) {
                analysis.setStatus(status);
                analysis.setErrorMessage(errorMessage);
                if (feedback != null && !feedback.isEmpty()) {
                    writeJsonSafely(feedback).ifPresent(analysis::setFeedbackJson);
                }
                videoAnalysisRepository.save(analysis);
            }
        });
    }

    @Transactional
    public void recordFailureResult(Long analysisId, String failureStatus, AiVideoScoringResponseDto aiResult) {
        runInRequiresNewTransaction(() -> recordFailureResultInternal(analysisId, failureStatus, aiResult));
    }

    private void recordFailureResultInternal(Long analysisId, String failureStatus, AiVideoScoringResponseDto aiResult) {
        VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) return;

        String errorMsg = aiResult.getErrorMessage();
        if (errorMsg == null || errorMsg.isBlank()) {
            if (aiResult.getFeedback() != null && !aiResult.getFeedback().isEmpty()) {
                errorMsg = String.join("; ", aiResult.getFeedback());
            } else {
                errorMsg = "Video analysis failed with status " + failureStatus + ".";
            }
        }

        analysis.setStatus(failureStatus);
        analysis.setErrorMessage(errorMsg);
        applyV1Fields(analysis, aiResult);
        videoAnalysisRepository.save(analysis);
    }

    private void persistFailureStatusSafely(Long analysisId, String errorMessage) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                boolean updated = runBooleanInRequiresNewTransaction(() -> {
                    VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
                    if (analysis == null) {
                        return false;
                    }
                    analysis.setStatus("FAILED");
                    analysis.setErrorMessage(errorMessage);
                    videoAnalysisRepository.save(analysis);
                    return true;
                });
                if (updated || attempt == 3) {
                    return;
                }
            } catch (Exception persistenceEx) {
                log.warn("Attempt {} to persist FAILED status for analysisId={} failed: {}",
                        attempt, analysisId, persistenceEx.getMessage());
            }
            try {
                Thread.sleep(25L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runInRequiresNewTransaction(Runnable action) {
        if (transactionManager == null) {
            action.run();
            return;
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> action.run());
    }

    private boolean runBooleanInRequiresNewTransaction(java.util.function.BooleanSupplier action) {
        if (transactionManager == null) {
            return action.getAsBoolean();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Boolean res = template.execute(status -> action.getAsBoolean());
        return Boolean.TRUE.equals(res);
    }

    @Transactional(readOnly = true)
    public VideoAnalysisResponseDto getAnalysisById(Long analysisId, Long currentUserId, boolean isAdmin) {
        VideoAnalysis analysis = findAndVerifyOwnership(analysisId, currentUserId, isAdmin);
        VideoAnalysisResponseDto dto = mapToResponse(analysis);
        if (videoAnalysisQuotaService != null && analysis.getUser() != null && analysis.getUser().getId().equals(currentUserId)) {
            dto.setQuota(videoAnalysisQuotaService.getQuotaStatusForUser(analysis.getUser()));
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public VideoAnalysisKeypointsResponseDto getAnalysisKeypoints(Long analysisId, Long currentUserId, boolean isAdmin) {
        VideoAnalysis analysis = findAndVerifyOwnership(analysisId, currentUserId, isAdmin);
        List<VideoAnalysisKeypoint> keypoints = keypointRepository.findByVideoAnalysisIdOrderByFrameIndexAsc(analysis.getId());

        List<AiVideoKeypointFrameDto> frames = new ArrayList<>();
        for (VideoAnalysisKeypoint kp : keypoints) {
            try {
                List<AiVideoKeypointItemDto> items = objectMapper.readValue(
                        kp.getKeypointsJson(),
                        new TypeReference<List<AiVideoKeypointItemDto>>() {}
                );
                frames.add(AiVideoKeypointFrameDto.builder()
                        .frameIndex(kp.getFrameIndex())
                        .timestampMs(kp.getTimestampMs())
                        .keypoints(items)
                        .build());
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize keypoint items for frameIndex={}", kp.getFrameIndex(), e);
            }
        }

        return VideoAnalysisKeypointsResponseDto.builder()
                .analysisId(analysis.getId())
                .format("COCO_17")
                .coordinateSystem("NORMALIZED")
                .totalFrames(frames.size())
                .frames(frames)
                .build();
    }

    @Transactional(readOnly = true)
    public List<VideoAnalysisResponseDto> getUserHistory(Long userId) {
        return videoAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VideoAnalysis findAndVerifyOwnership(Long analysisId, Long currentUserId, boolean isAdmin) {
        VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found."));

        if (!isAdmin && !analysis.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this analysis.");
        }

        return analysis;
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file cannot be empty.");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean isValidType = (contentType != null && contentType.startsWith("video/"))
                || (filename != null && filename.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|avi|mkv|webm)$"));
        if (!isValidType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be a supported video format (MP4, MOV, AVI, MKV, WebM).");
        }

        if (file.getSize() > 100L * 1024L * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file size exceeds maximum allowed 100MB.");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters."
            );
        }
        return trimmed;
    }

    private ResolvedExercise validateAndResolveExercise(Long exerciseId, String exerciseCode) {
        Exercise exercise = null;
        if (exerciseId != null) {
            exercise = exerciseRepository.findById(exerciseId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown exerciseId: " + exerciseId));
        }

        if (exerciseCode != null && !exerciseCode.isBlank()) {
            String normalizedInput = normalizeExerciseToken(exerciseCode);
            String canonicalCode = SUPPORTED_EXERCISE_ALIASES.get(normalizedInput);
            if (canonicalCode == null) {
                Optional<Exercise> byCode = exerciseRepository.findByCode(normalizedInput);
                if (byCode.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported exercise: " + exerciseCode);
                }
                if (exercise == null) {
                    exercise = byCode.get();
                }
                canonicalCode = byCode.get().getCode();
            } else if (exercise == null) {
                exercise = exerciseRepository.findByCode(canonicalCode).orElse(null);
            }
            return new ResolvedExercise(exercise, canonicalCode);
        }

        if (exercise != null) {
            String code = (exercise.getCode() != null && !exercise.getCode().isBlank())
                    ? normalizeExerciseToken(exercise.getCode())
                    : "PLANCHE";
            return new ResolvedExercise(exercise, code);
        }

        return new ResolvedExercise(null, "PLANCHE");
    }

    private String normalizeExerciseToken(String raw) {
        return raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private void applyV1Fields(VideoAnalysis analysis, AiVideoScoringResponseDto aiResult) {
        if (aiResult.getSchemaVersion() != null && !aiResult.getSchemaVersion().isBlank()) {
            analysis.setSchemaVersion(aiResult.getSchemaVersion().trim());
        } else if (analysis.getSchemaVersion() == null || analysis.getSchemaVersion().isBlank()) {
            analysis.setSchemaVersion(SCHEMA_VERSION_V1);
        }

        if (analysis.getExerciseCode() == null || analysis.getExerciseCode().isBlank()) {
            String fromResult = (aiResult.getExerciseCode() != null && !aiResult.getExerciseCode().isBlank())
                    ? aiResult.getExerciseCode()
                    : aiResult.getExercise();
            if (fromResult != null && !fromResult.isBlank()) {
                analysis.setExerciseCode(normalizeExerciseToken(fromResult));
            }
        }

        if (aiResult.getRepCount() != null) {
            analysis.setRepCount(aiResult.getRepCount());
        } else if (aiResult.getReps() != null) {
            analysis.setRepCount(aiResult.getReps().size());
        }

        if (aiResult.getValidReps() != null) {
            analysis.setValidReps(aiResult.getValidReps());
        } else if (aiResult.getReps() != null) {
            int valid = (int) aiResult.getReps().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getValid()) || "VALID".equalsIgnoreCase(r.getStatus()) || "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .count();
            analysis.setValidReps(valid);
        }

        if (aiResult.getIncompleteReps() != null) {
            analysis.setIncompleteReps(aiResult.getIncompleteReps());
        } else if (analysis.getRepCount() != null && analysis.getValidReps() != null) {
            analysis.setIncompleteReps(Math.max(0, analysis.getRepCount() - analysis.getValidReps()));
        }

        if (aiResult.getVideo() != null) {
            analysis.setDurationMs(aiResult.getVideo().getDurationMs());
            analysis.setWidth(aiResult.getVideo().getWidth());
            analysis.setHeight(aiResult.getVideo().getHeight());
            analysis.setFps(aiResult.getVideo().getFps() != null ? BigDecimal.valueOf(aiResult.getVideo().getFps()) : null);
        }

        if (aiResult.getMetrics() != null) {
            writeJsonSafely(aiResult.getMetrics()).ifPresent(analysis::setMetricsJson);
        }
        if (aiResult.getFeedback() != null) {
            writeJsonSafely(aiResult.getFeedback()).ifPresent(analysis::setFeedbackJson);
        }
        if (aiResult.getReps() != null) {
            writeJsonSafely(aiResult.getReps()).ifPresent(analysis::setRepsJson);
        }
        if (aiResult.getMovementMetrics() != null) {
            writeJsonSafely(aiResult.getMovementMetrics()).ifPresent(analysis::setMovementMetricsJson);
        }
        if (aiResult.getIssues() != null) {
            writeJsonSafely(aiResult.getIssues()).ifPresent(analysis::setIssuesJson);
        }
        if (aiResult.getRecommendations() != null) {
            writeJsonSafely(aiResult.getRecommendations()).ifPresent(analysis::setRecommendationsJson);
        }
        if (aiResult.getCameraQuality() != null) {
            writeJsonSafely(aiResult.getCameraQuality()).ifPresent(analysis::setCameraQualityJson);
        }
    }

    private VideoAnalysisResponseDto buildIdempotentReplayResponse(Long userId, VideoAnalysis existing) {
        VideoAnalysisResponseDto response = mapToResponse(existing);
        response.setIdempotentReplay(true);
        if (videoAnalysisQuotaService != null) {
            response.setQuota(videoAnalysisQuotaService.getQuotaStatus(userId));
        }
        return response;
    }

    private void releaseReservedQuotaSafely(Long userId, QuotaEntitlementResponseDto reservedQuota) {
        if (videoAnalysisQuotaService == null || reservedQuota == null || reservedQuota.getQuotaDate() == null) {
            return;
        }
        try {
            videoAnalysisQuotaService.releaseReservedQuota(userId, reservedQuota.getQuotaDate());
        } catch (Exception ex) {
            log.warn("Failed to release reserved quota for userId={} date={}: {}",
                    userId, reservedQuota.getQuotaDate(), ex.getMessage());
        }
    }

    private void deleteMinioObjectSafely(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            minioStorageService.delete(objectKey);
        } catch (Exception ex) {
            log.warn("Failed to clean up MinIO object {}: {}", objectKey, ex.getMessage());
        }
    }

    private Optional<String> writeJsonSafely(Object value) {
        try {
            return Optional.of(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JSON field for video analysis", e);
            return Optional.empty();
        }
    }

    private <T> T readJsonOrDefault(String json, TypeReference<T> typeRef, T defaultValue, Long analysisId, String fieldName) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse {} for id={}", fieldName, analysisId, e);
            return defaultValue;
        }
    }

    private VideoAnalysisResponseDto mapToResponse(VideoAnalysis analysis) {
        Long id = analysis.getId();
        Map<String, Object> metrics = readJsonOrDefault(
                analysis.getMetricsJson(), new TypeReference<>() {}, Collections.emptyMap(), id, "metricsJson");
        List<String> feedback = readJsonOrDefault(
                analysis.getFeedbackJson(), new TypeReference<>() {}, Collections.emptyList(), id, "feedbackJson");
        List<AiRepBreakdownDto> reps = readJsonOrDefault(
                analysis.getRepsJson(), new TypeReference<>() {}, Collections.emptyList(), id, "repsJson");
        Map<String, AiMovementMetricDto> movementMetrics = readJsonOrDefault(
                analysis.getMovementMetricsJson(), new TypeReference<>() {}, Collections.emptyMap(), id, "movementMetricsJson");
        List<AiStructuredIssueDto> issues = readJsonOrDefault(
                analysis.getIssuesJson(), new TypeReference<>() {}, Collections.emptyList(), id, "issuesJson");
        List<String> recommendations = readJsonOrDefault(
                analysis.getRecommendationsJson(), new TypeReference<>() {}, Collections.emptyList(), id, "recommendationsJson");
        AiCameraQualityDto cameraQuality = readJsonOrDefault(
                analysis.getCameraQualityJson(), new TypeReference<>() {}, null, id, "cameraQualityJson");

        String effectiveCode = analysis.getExerciseCode();
        if ((effectiveCode == null || effectiveCode.isBlank()) && analysis.getExercise() != null) {
            effectiveCode = analysis.getExercise().getCode();
        }

        String exerciseName = analysis.getExercise() != null
                ? analysis.getExercise().getName()
                : (effectiveCode != null && !effectiveCode.isBlank() ? formatExerciseName(effectiveCode) : "Custom Pose");

        return VideoAnalysisResponseDto.builder()
                .id(id)
                .userId(analysis.getUser().getId())
                .exerciseId(analysis.getExercise() != null ? analysis.getExercise().getId() : null)
                .exerciseCode(effectiveCode)
                .exerciseName(exerciseName)
                .schemaVersion(analysis.getSchemaVersion() != null ? analysis.getSchemaVersion() : SCHEMA_VERSION_V1)
                .status(analysis.getStatus())
                .overallScore(analysis.getOverallScore())
                .formAccuracy(analysis.getFormAccuracy())
                .stabilityScore(analysis.getStabilityScore())
                .alignmentScore(analysis.getAlignmentScore())
                .repCount(analysis.getRepCount())
                .validReps(analysis.getValidReps())
                .incompleteReps(analysis.getIncompleteReps())
                .durationMs(analysis.getDurationMs())
                .width(analysis.getWidth())
                .height(analysis.getHeight())
                .fps(analysis.getFps())
                .originalVideoUrl("/api/v1/video-analysis/" + id + "/original")
                .annotatedVideoUrl(analysis.getAnnotatedVideoKey() != null ? "/api/v1/video-analysis/" + id + "/annotated" : null)
                .keypointsUrl("/api/v1/video-analysis/" + id + "/keypoints")
                .metrics(metrics)
                .feedback(feedback)
                .reps(reps)
                .movementMetrics(movementMetrics)
                .issues(issues)
                .recommendations(recommendations)
                .cameraQuality(cameraQuality)
                .errorMessage(analysis.getErrorMessage())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    private String formatExerciseName(String code) {
        return switch (code) {
            case "SQUAT" -> "Bodyweight Squat";
            case "PUSH_UP" -> "Push-Up";
            case "PULL_UP" -> "Pull-Up";
            case "DIPS" -> "Parallel Bar Dips";
            case "HANDSTAND" -> "Handstand";
            case "HANDSTAND_PUSHUP" -> "Handstand Push-Up";
            case "PLANCHE" -> "Planche";
            case "FULL_PLANCHE" -> "Full Planche";
            case "STRADDLE_PLANCHE" -> "Straddle Planche";
            case "ADV_TUCK_PLANCHE" -> "Advanced Tuck Planche";
            case "TUCK_PLANCHE" -> "Tuck Planche";
            case "FRONT_LEVER" -> "Front Lever";
            case "FULL_FRONT_LEVER" -> "Full Front Lever";
            case "STRADDLE_FRONT_LEVER" -> "Straddle Front Lever";
            case "ADV_TUCK_FRONT_LEVER" -> "Advanced Tuck Front Lever";
            case "TUCK_FRONT_LEVER" -> "Tuck Front Lever";
            case "PIKE_PUSH_UP" -> "Pike Push-Up";
            case "L_SIT" -> "L-Sit";
            case "MUSCLE_UP" -> "Muscle-Up";
            default -> code;
        };
    }

    private record ResolvedExercise(Exercise exercise, String code) {}
}
