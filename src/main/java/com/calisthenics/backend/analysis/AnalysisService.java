package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.AiImageAnalysisResponseDto;
import com.calisthenics.backend.analysis.dto.AiVideoAnalysisResponseDto;
import com.calisthenics.backend.analysis.dto.AnalysisResponse;
import com.calisthenics.backend.analysis.dto.ImageAnalysisResponse;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.storage.StorageService;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final StorageService storageService;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalysisResponse analyzeAndSaveImage(Long userId, Long exerciseId, MultipartFile file) {
        String storedFileName = storageService.store(file);
        String mediaUrl = "/api/v1/storage/files/" + storedFileName;

        AiImageAnalysisResponseDto aiResult = aiServiceClient.analyzeImage(file, true);

        return saveImageAnalysis(userId, exerciseId, mediaUrl, aiResult);
    }

    @Transactional
    public AnalysisResponse analyzeAndSaveVideo(Long userId, Long exerciseId, MultipartFile file) {
        String storedFileName = storageService.store(file);
        String mediaUrl = "/api/v1/storage/files/" + storedFileName;

        AiVideoAnalysisResponseDto aiResult = aiServiceClient.analyzeVideo(file, 5);

        return saveVideoAnalysis(userId, exerciseId, mediaUrl, file.getSize(), aiResult);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> getUserAnalysisHistory(Long userId) {
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysisById(Long analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Analysis record not found with id: " + analysisId));
        return mapToResponse(analysis);
    }

    private AnalysisResponse saveImageAnalysis(Long userId, Long exerciseId, String mediaUrl, AiImageAnalysisResponseDto aiResult) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Exercise exercise = null;
        if (exerciseId != null) {
            exercise = exerciseRepository.findById(exerciseId).orElse(null);
        }

        BigDecimal overallScore = null;
        BigDecimal elbowAngle = null;
        BigDecimal bodyLineAngle = null;
        BigDecimal shoulderLeanAngle = null;
        List<String> feedbackList = new ArrayList<>();
        String rawKeypointsJson = null;

        if (aiResult != null && aiResult.getPlancheAnalysis() != null) {
            var planche = aiResult.getPlancheAnalysis();
            overallScore = planche.getFormScore();

            if (planche.getJointAngles() != null) {
                var angles = planche.getJointAngles();
                elbowAngle = angles.getPrimaryElbowDeg();
                shoulderLeanAngle = angles.getPrimaryShoulderLeanDeg();
                bodyLineAngle = angles.getHipAngleDeg() != null ? angles.getHipAngleDeg() : angles.getBodyHorizontalTiltDeg();
            }

            if (planche.getFormErrors() != null) feedbackList.addAll(planche.getFormErrors());
            if (planche.getPositiveFeedback() != null) feedbackList.addAll(planche.getPositiveFeedback());
            if (planche.getActionableCues() != null) feedbackList.addAll(planche.getActionableCues());
        }

        if (aiResult != null && aiResult.getPoseKeypoints() != null && aiResult.getPoseKeypoints().getKeypoints() != null) {
            try {
                rawKeypointsJson = objectMapper.writeValueAsString(aiResult.getPoseKeypoints().getKeypoints());
            } catch (JsonProcessingException e) {
                log.error("Error serializing keypoints map to JSON", e);
            }
        }

        String formFeedback = feedbackList.isEmpty() ? null : String.join("; ", feedbackList);

        Analysis analysis = Analysis.builder()
                .user(user)
                .exercise(exercise)
                .mediaUrl(mediaUrl)
                .mediaType("IMAGE")
                .overallScore(overallScore)
                .holdDurationSeconds(null)
                .formFeedback(formFeedback)
                .elbowAngle(elbowAngle)
                .bodyLineAngle(bodyLineAngle)
                .shoulderLeanAngle(shoulderLeanAngle)
                .rawKeypointsJson(rawKeypointsJson)
                .build();

        Analysis saved = analysisRepository.save(analysis);
        return mapToResponse(saved);
    }

    private AnalysisResponse saveVideoAnalysis(Long userId, Long exerciseId, String mediaUrl, long fileSizeBytes, AiVideoAnalysisResponseDto aiResult) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Exercise exercise = null;
        if (exerciseId != null) {
            exercise = exerciseRepository.findById(exerciseId).orElse(null);
        }

        BigDecimal overallScore = null;
        BigDecimal holdDurationSeconds = null;
        List<String> feedbackList = new ArrayList<>();

        // Video metadata
        Long videoDurationMs = null;
        Integer videoWidth = null;
        Integer videoHeight = null;
        BigDecimal originalFps = null;
        Integer totalFrames = null;
        Integer analyzedFrames = null;
        BigDecimal samplingFps = null;
        BigDecimal processingTimeMs = null;

        // Biomechanical JSON blobs
        String componentScoresJson = null;
        String stabilityJson = null;
        String timelineJson = null;

        if (aiResult != null) {
            overallScore = aiResult.getAverageFormScore() != null ? aiResult.getAverageFormScore() : aiResult.getPeakFormScore();
            holdDurationSeconds = aiResult.getValidHoldDurationSec() != null ? aiResult.getValidHoldDurationSec() : aiResult.getTotalHoldDurationSec();

            if (aiResult.getSummaryFeedback() != null) feedbackList.addAll(aiResult.getSummaryFeedback());
            if (aiResult.getKeyRecommendations() != null) feedbackList.addAll(aiResult.getKeyRecommendations());

            processingTimeMs = aiResult.getProcessingTimeMs();

            // Extract video metadata from dedicated block if available, fall back to legacy flat fields
            if (aiResult.getVideoMetadata() != null) {
                var meta = aiResult.getVideoMetadata();
                videoDurationMs = meta.getDurationMs();
                videoWidth = meta.getWidth();
                videoHeight = meta.getHeight();
                originalFps = meta.getOriginalFps();
                totalFrames = meta.getTotalFrames();
                analyzedFrames = meta.getAnalyzedFrames();
                samplingFps = meta.getSamplingFps();
                // file_size_bytes comes from meta if available; we also have it from the actual MultipartFile
            } else {
                // Fallback to legacy flat fields
                if (aiResult.getDurationSec() != null) {
                    videoDurationMs = aiResult.getDurationSec().multiply(BigDecimal.valueOf(1000)).longValue();
                }
                originalFps = aiResult.getFps();
                totalFrames = aiResult.getTotalFrames();
            }

            // Serialize component scores map to JSON
            if (aiResult.getComponentScores() != null && !aiResult.getComponentScores().isEmpty()) {
                try {
                    componentScoresJson = objectMapper.writeValueAsString(aiResult.getComponentScores());
                } catch (JsonProcessingException e) {
                    log.error("Error serializing component scores to JSON", e);
                }
            }

            // Serialize stability metrics to JSON
            if (aiResult.getStability() != null) {
                try {
                    stabilityJson = objectMapper.writeValueAsString(aiResult.getStability());
                } catch (JsonProcessingException e) {
                    log.error("Error serializing stability metrics to JSON", e);
                }
            }

            // Serialize timeline to JSON (may be large; store as TEXT)
            if (aiResult.getTimeline() != null && !aiResult.getTimeline().isEmpty()) {
                try {
                    timelineJson = objectMapper.writeValueAsString(aiResult.getTimeline());
                } catch (JsonProcessingException e) {
                    log.error("Error serializing timeline to JSON", e);
                }
            }
        }

        String formFeedback = feedbackList.isEmpty() ? null : String.join("; ", feedbackList);

        Analysis analysis = Analysis.builder()
                .user(user)
                .exercise(exercise)
                .mediaUrl(mediaUrl)
                .mediaType("VIDEO")
                .overallScore(overallScore)
                .holdDurationSeconds(holdDurationSeconds)
                .formFeedback(formFeedback)
                .elbowAngle(null)
                .bodyLineAngle(null)
                .shoulderLeanAngle(null)
                .rawKeypointsJson(null)
                // Video metadata
                .videoDurationMs(videoDurationMs)
                .videoWidth(videoWidth)
                .videoHeight(videoHeight)
                .originalFps(originalFps)
                .totalFrames(totalFrames)
                .analyzedFrames(analyzedFrames)
                .samplingFps(samplingFps)
                .fileSizeBytes(fileSizeBytes)
                .processingTimeMs(processingTimeMs)
                // Biomechanical data blobs
                .componentScoresJson(componentScoresJson)
                .stabilityJson(stabilityJson)
                .timelineJson(timelineJson)
                .build();

        Analysis saved = analysisRepository.save(analysis);
        return mapToResponse(saved);
    }

    private AnalysisResponse mapToResponse(Analysis analysis) {
        AnalysisResponse response = AnalysisResponse.builder()
                .id(analysis.getId())
                .userId(analysis.getUser().getId())
                .exerciseId(analysis.getExercise() != null ? analysis.getExercise().getId() : null)
                .exerciseName(analysis.getExercise() != null ? analysis.getExercise().getName() : "Custom Pose")
                .mediaUrl(analysis.getMediaUrl())
                .mediaType(analysis.getMediaType())
                .overallScore(analysis.getOverallScore())
                .holdDurationSeconds("IMAGE".equals(analysis.getMediaType()) ? null : analysis.getHoldDurationSeconds())
                .formFeedback(analysis.getFormFeedback())
                .elbowAngle(analysis.getElbowAngle())
                .bodyLineAngle(analysis.getBodyLineAngle())
                .shoulderLeanAngle(analysis.getShoulderLeanAngle())
                .rawKeypointsJson(analysis.getRawKeypointsJson())
                .createdAt(analysis.getCreatedAt())
                // Video metadata
                .videoDurationMs(analysis.getVideoDurationMs())
                .videoWidth(analysis.getVideoWidth())
                .videoHeight(analysis.getVideoHeight())
                .originalFps(analysis.getOriginalFps())
                .totalFrames(analysis.getTotalFrames())
                .analyzedFrames(analysis.getAnalyzedFrames())
                .samplingFps(analysis.getSamplingFps())
                .fileSizeBytes(analysis.getFileSizeBytes())
                .processingTimeMs(analysis.getProcessingTimeMs())
                // Biomechanical JSON blobs
                .componentScoresJson(analysis.getComponentScoresJson())
                .stabilityJson(analysis.getStabilityJson())
                .timelineJson(analysis.getTimelineJson())
                .build();
        return "IMAGE".equals(analysis.getMediaType()) ? ImageAnalysisResponse.from(response) : response;
    }
}
