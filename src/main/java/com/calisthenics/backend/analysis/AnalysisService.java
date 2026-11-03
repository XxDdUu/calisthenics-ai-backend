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
        String mediaUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/storage/files/")
                .path(storedFileName)
                .toUriString();

        AiImageAnalysisResponseDto aiResult = aiServiceClient.analyzeImage(file, true);

        return saveImageAnalysis(userId, exerciseId, mediaUrl, aiResult);
    }

    @Transactional
    public AnalysisResponse analyzeAndSaveVideo(Long userId, Long exerciseId, MultipartFile file) {
        String storedFileName = storageService.store(file);
        String mediaUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/storage/files/")
                .path(storedFileName)
                .toUriString();

        AiVideoAnalysisResponseDto aiResult = aiServiceClient.analyzeVideo(file, 5);

        return saveVideoAnalysis(userId, exerciseId, mediaUrl, aiResult);
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

    private AnalysisResponse saveVideoAnalysis(Long userId, Long exerciseId, String mediaUrl, AiVideoAnalysisResponseDto aiResult) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        Exercise exercise = null;
        if (exerciseId != null) {
            exercise = exerciseRepository.findById(exerciseId).orElse(null);
        }

        BigDecimal overallScore = null;
        BigDecimal holdDurationSeconds = null;
        List<String> feedbackList = new ArrayList<>();

        if (aiResult != null) {
            overallScore = aiResult.getAverageFormScore() != null ? aiResult.getAverageFormScore() : aiResult.getPeakFormScore();
            holdDurationSeconds = aiResult.getValidHoldDurationSec() != null ? aiResult.getValidHoldDurationSec() : aiResult.getTotalHoldDurationSec();

            if (aiResult.getSummaryFeedback() != null) feedbackList.addAll(aiResult.getSummaryFeedback());
            if (aiResult.getKeyRecommendations() != null) feedbackList.addAll(aiResult.getKeyRecommendations());
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
                .build();
        return "IMAGE".equals(analysis.getMediaType()) ? ImageAnalysisResponse.from(response) : response;
    }
}
