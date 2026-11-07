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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalysisService {

    private final VideoAnalysisRepository videoAnalysisRepository;
    private final VideoAnalysisKeypointRepository keypointRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final MinioAnalysisMediaStorageService minioStorageService;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public VideoAnalysisResponseDto initiateVideoAnalysis(Long userId, Long exerciseId, MultipartFile file) {
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        Exercise exercise = null;
        if (exerciseId != null) {
            exercise = exerciseRepository.findById(exerciseId).orElse(null);
        }

        // 1. Upload original video to MinIO
        String originalVideoKey = minioStorageService.store(file);
        String uuidStr = UUID.randomUUID().toString();

        // 2. Create VideoAnalysis entity in PENDING status
        VideoAnalysis analysis = VideoAnalysis.builder()
                .user(user)
                .exercise(exercise)
                .originalVideoKey(originalVideoKey)
                .status("PENDING")
                .build();
        VideoAnalysis savedAnalysis = videoAnalysisRepository.save(analysis);

        // Read file bytes before async thread boundary
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read video file bytes", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read video file.");
        }

        String exerciseCode = exercise != null ? exercise.getCode() : "PLANCHE";

        // 3. Launch Async processing
        taskExecutor.execute(() -> processAnalysisAsync(savedAnalysis.getId(), fileBytes, filename, exerciseCode, uuidStr));

        return mapToResponse(savedAnalysis);
    }

    public void processAnalysisAsync(Long analysisId, byte[] videoBytes, String filename, String exerciseCode, String uuidStr) {
        log.info("Starting async video scoring for analysisId={}", analysisId);
        try {
            updateStatus(analysisId, "PROCESSING", null);

            // Send video to FastAPI AI Service
            AiVideoScoringResponseDto aiResult = aiServiceClient.scoreVideo(videoBytes, filename, exerciseCode, 10, uuidStr);

            if (aiResult == null) {
                updateStatus(analysisId, "FAILED", "AI service returned null response.");
                return;
            }

            if ("NO_PERSON_DETECTED".equalsIgnoreCase(aiResult.getStatus())
                    || "INSUFFICIENT_POSE_QUALITY".equalsIgnoreCase(aiResult.getStatus())
                    || "FAILED".equalsIgnoreCase(aiResult.getStatus())) {
                String feedbackMsg = aiResult.getFeedback() != null && !aiResult.getFeedback().isEmpty()
                        ? String.join("; ", aiResult.getFeedback())
                        : "No human pose detected in video.";
                updateStatusWithFeedback(analysisId, aiResult.getStatus(), feedbackMsg, aiResult.getFeedback());
                return;
            }

            // Download annotated video from AI service and store in MinIO
            String annotatedKey = uuidStr + "_annot.mp4";
            byte[] annotatedBytes = aiServiceClient.downloadAnnotatedVideo(uuidStr);
            if (annotatedBytes != null && annotatedBytes.length > 0) {
                minioStorageService.storeBytes(annotatedBytes, "video/mp4", annotatedKey);
            } else {
                annotatedKey = null;
                log.warn("Annotated video was not retrieved from AI service for analysisId={}", analysisId);
            }

            // Store analysis results and keypoint frames
            completeAnalysis(analysisId, aiResult, annotatedKey);

        } catch (Exception e) {
            log.error("Error during async video analysis processing for id={}", analysisId, e);
            updateStatus(analysisId, "FAILED", e.getMessage() != null ? e.getMessage() : "Unexpected analysis failure");
        }
    }

    @Transactional
    public void completeAnalysis(Long analysisId, AiVideoScoringResponseDto aiResult, String annotatedKey) {
        VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) return;

        analysis.setAnnotatedVideoKey(annotatedKey);
        analysis.setOverallScore(aiResult.getScore() != null ? BigDecimal.valueOf(aiResult.getScore()) : BigDecimal.ZERO);
        analysis.setFormAccuracy(aiResult.getFormAccuracy() != null ? BigDecimal.valueOf(aiResult.getFormAccuracy()) : BigDecimal.ZERO);
        analysis.setStabilityScore(aiResult.getStability() != null ? BigDecimal.valueOf(aiResult.getStability()) : BigDecimal.ZERO);
        analysis.setAlignmentScore(aiResult.getAlignment() != null ? BigDecimal.valueOf(aiResult.getAlignment()) : BigDecimal.ZERO);

        if (aiResult.getVideo() != null) {
            analysis.setDurationMs(aiResult.getVideo().getDurationMs());
            analysis.setWidth(aiResult.getVideo().getWidth());
            analysis.setHeight(aiResult.getVideo().getHeight());
            analysis.setFps(aiResult.getVideo().getFps() != null ? BigDecimal.valueOf(aiResult.getVideo().getFps()) : null);
        }

        try {
            if (aiResult.getMetrics() != null) {
                analysis.setMetricsJson(objectMapper.writeValueAsString(aiResult.getMetrics()));
            }
            if (aiResult.getFeedback() != null) {
                analysis.setFeedbackJson(objectMapper.writeValueAsString(aiResult.getFeedback()));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metrics/feedback to JSON", e);
        }

        analysis.setStatus("COMPLETED");
        videoAnalysisRepository.save(analysis);

        // Save keypoints batch
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
        VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis != null) {
            analysis.setStatus(status);
            analysis.setErrorMessage(errorMessage);
            videoAnalysisRepository.save(analysis);
        }
    }

    @Transactional
    public void updateStatusWithFeedback(Long analysisId, String status, String errorMessage, List<String> feedback) {
        VideoAnalysis analysis = videoAnalysisRepository.findById(analysisId).orElse(null);
        if (analysis != null) {
            analysis.setStatus(status);
            analysis.setErrorMessage(errorMessage);
            if (feedback != null && !feedback.isEmpty()) {
                try {
                    analysis.setFeedbackJson(objectMapper.writeValueAsString(feedback));
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize feedback list", e);
                }
            }
            videoAnalysisRepository.save(analysis);
        }
    }

    @Transactional(readOnly = true)
    public VideoAnalysisResponseDto getAnalysisById(Long analysisId, Long currentUserId, boolean isAdmin) {
        VideoAnalysis analysis = findAndVerifyOwnership(analysisId, currentUserId, isAdmin);
        return mapToResponse(analysis);
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

    private VideoAnalysisResponseDto mapToResponse(VideoAnalysis analysis) {
        Map<String, Object> metrics = Collections.emptyMap();
        List<String> feedback = Collections.emptyList();

        if (analysis.getMetricsJson() != null && !analysis.getMetricsJson().isBlank()) {
            try {
                metrics = objectMapper.readValue(analysis.getMetricsJson(), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to parse metricsJson for id={}", analysis.getId(), e);
            }
        }

        if (analysis.getFeedbackJson() != null && !analysis.getFeedbackJson().isBlank()) {
            try {
                feedback = objectMapper.readValue(analysis.getFeedbackJson(), new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to parse feedbackJson for id={}", analysis.getId(), e);
            }
        }

        Long id = analysis.getId();
        return VideoAnalysisResponseDto.builder()
                .id(id)
                .userId(analysis.getUser().getId())
                .exerciseId(analysis.getExercise() != null ? analysis.getExercise().getId() : null)
                .exerciseName(analysis.getExercise() != null ? analysis.getExercise().getName() : "Custom Pose")
                .status(analysis.getStatus())
                .overallScore(analysis.getOverallScore())
                .formAccuracy(analysis.getFormAccuracy())
                .stabilityScore(analysis.getStabilityScore())
                .alignmentScore(analysis.getAlignmentScore())
                .durationMs(analysis.getDurationMs())
                .width(analysis.getWidth())
                .height(analysis.getHeight())
                .fps(analysis.getFps())
                .originalVideoUrl("/api/v1/video-analysis/" + id + "/original")
                .annotatedVideoUrl(analysis.getAnnotatedVideoKey() != null ? "/api/v1/video-analysis/" + id + "/annotated" : null)
                .keypointsUrl("/api/v1/video-analysis/" + id + "/keypoints")
                .metrics(metrics)
                .feedback(feedback)
                .errorMessage(analysis.getErrorMessage())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }
}
