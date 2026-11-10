package com.calisthenics.backend.analysis;

import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredPerformanceSummary;
import com.calisthenics.backend.analysis.dto.*;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisSessionService {

    private final AnalysisSessionRepository sessionRepository;
    private final RepHistoryRepository repHistoryRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final GeminiApiClient geminiApiClient;
    private final WorkoutSummaryMetricsProcessor metricsProcessor;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalysisSessionResponseDto createSession(Long userId, CreateAnalysisSessionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String sid = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString().substring(0, 16);

        var existing = sessionRepository.findById(sid);
        if (existing.isPresent()) {
            AnalysisSession s = existing.get();
            if (!s.getUser().getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session ID already belongs to another user.");
            }
            return mapToDto(s, true);
        }

        String exerciseCode = request.getExerciseType().toUpperCase().trim();
        Long resolvedExerciseId = resolveExerciseId(request.getExerciseId(), exerciseCode);

        AnalysisSession session = AnalysisSession.builder()
                .id(sid)
                .user(user)
                .exerciseType(exerciseCode)
                .exerciseId(resolvedExerciseId)
                .cameraView(request.getCameraView() != null ? request.getCameraView().toUpperCase().trim() : "SIDE")
                .startedAt(Instant.now())
                .analysisStatus("IN_PROGRESS")
                .aiSummaryStatus("PENDING")
                .durationSeconds(BigDecimal.ZERO)
                .totalReps(0)
                .reps(new ArrayList<>())
                .build();

        AnalysisSession saved = sessionRepository.save(session);
        log.info("Created Live AnalysisSession id={} exercise={} exerciseId={} user={}",
                saved.getId(), exerciseCode, resolvedExerciseId, userId);
        return mapToDto(saved, true);
    }

    @Transactional
    public AnalysisSessionResponseDto completeSession(Long userId, String sessionId, CompleteAnalysisSessionRequest request) {
        AnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis session not found."));

        if (!session.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this analysis session.");
        }

        boolean isAlreadyCompleted = "COMPLETED".equalsIgnoreCase(session.getAnalysisStatus());
        if (isAlreadyCompleted && request.getReps() == null && request.getExerciseMetrics() == null) {
            log.info("AnalysisSession id={} already completed.", sessionId);
            return mapToDto(session, true);
        }

        Instant now = Instant.now();
        session.setCompletedAt(request.getCompletedAt() != null ? request.getCompletedAt() : now);

        if (session.getExerciseId() == null) {
            session.setExerciseId(resolveExerciseId(request.getExerciseId(), session.getExerciseType()));
        }

        if (request.getDurationSeconds() != null) {
            session.setDurationSeconds(request.getDurationSeconds());
        } else if (session.getStartedAt() != null) {
            long sec = session.getCompletedAt().getEpochSecond() - session.getStartedAt().getEpochSecond();
            session.setDurationSeconds(BigDecimal.valueOf(Math.max(0, sec)));
        }

        session.setTotalReps(request.getTotalReps() != null ? request.getTotalReps() : 0);
        session.setAverageFormScore(request.getAverageFormScore());
        session.setOverallScore(request.getOverallScore() != null ? request.getOverallScore() : request.getAverageFormScore());
        session.setBestFormScore(request.getBestFormScore());
        session.setLowestFormScore(request.getLowestFormScore());
        session.setAnalysisConfidence(request.getAnalysisConfidence());
        session.setPeakHoldDurationSeconds(request.getPeakHoldDurationSeconds());
        session.setCorrectReps(request.getCorrectReps() != null ? request.getCorrectReps() : 0);
        session.setIncorrectReps(request.getIncorrectReps() != null ? request.getIncorrectReps() : 0);
        session.setAverageKneeAngle(request.getAverageKneeAngle() != null ? request.getAverageKneeAngle() : request.getAverageJointAngle());
        session.setDeepestKneeAngle(request.getDeepestKneeAngle() != null ? request.getDeepestKneeAngle() : request.getDeepestJointAngle());
        session.setAverageJointAngle(request.getAverageJointAngle() != null ? request.getAverageJointAngle() : request.getAverageKneeAngle());
        session.setDeepestJointAngle(request.getDeepestJointAngle() != null ? request.getDeepestJointAngle() : request.getDeepestKneeAngle());
        session.setOriginalVideoUrl(request.getOriginalVideoUrl());
        session.setAnnotatedVideoUrl(request.getAnnotatedVideoUrl());
        session.setAnalysisStatus("COMPLETED");

        List<RepHistoryDto> incomingReps = request.getReps();
        if (incomingReps != null && !incomingReps.isEmpty()) {
            session.getReps().clear();
            BigDecimal minScore = request.getLowestFormScore();
            int correct = 0;
            int incorrect = 0;
            for (RepHistoryDto rDto : incomingReps) {
                BigDecimal jointAngle = rDto.getMinimumJointAngle() != null
                        ? rDto.getMinimumJointAngle()
                        : (rDto.getMinimumElbowAngle() != null ? rDto.getMinimumElbowAngle() : rDto.getMinimumKneeAngle());

                boolean repPassed = rDto.getFormScore() != null
                        && rDto.getFormScore().compareTo(BigDecimal.valueOf(70)) >= 0
                        && !"SHALLOW".equalsIgnoreCase(rDto.getDepthRating());
                String completionStatus = rDto.getCompletionStatus() != null
                        ? rDto.getCompletionStatus()
                        : (repPassed ? "SUCCESS" : "FAILED");

                RepHistory rep = RepHistory.builder()
                        .session(session)
                        .repNumber(rDto.getRepNumber() != null ? rDto.getRepNumber() : session.getReps().size() + 1)
                        .startedAt(rDto.getStartedAt())
                        .completedAt(rDto.getCompletedAt())
                        .durationMs(rDto.getDurationMs())
                        .timestampOffsetMs(rDto.getTimestampOffsetMs())
                        .minimumKneeAngle(rDto.getMinimumKneeAngle() != null ? rDto.getMinimumKneeAngle() : jointAngle)
                        .minimumJointAngle(jointAngle)
                        .formScore(rDto.getFormScore())
                        .depthRating(rDto.getDepthRating())
                        .completionStatus(completionStatus)
                        .feedback(rDto.getFeedback())
                        .detectedErrorsJson(toJson(rDto.getDetectedErrors()))
                        .repMetricsJson(toJson(rDto.getMetrics()))
                        .build();
                session.getReps().add(rep);

                if (rep.getFormScore() != null) {
                    if (minScore == null || rep.getFormScore().compareTo(minScore) < 0) {
                        minScore = rep.getFormScore();
                    }
                    if (repPassed) {
                        correct++;
                    } else {
                        incorrect++;
                    }
                }
            }
            if (session.getLowestFormScore() == null) {
                session.setLowestFormScore(minScore);
            }
            if (request.getCorrectReps() == null) {
                session.setCorrectReps(correct);
            }
            if (request.getIncorrectReps() == null) {
                session.setIncorrectReps(incorrect);
            }
        }

        // Normalize and persist exercise-specific form metrics
        List<RepHistoryDto> repsForProcessing = incomingReps != null ? incomingReps : mapRepEntitiesToDtos(session.getReps());
        List<ExerciseFormMetricDto> normalizedMetrics = metricsProcessor.normalizeExerciseMetrics(
                session.getExerciseType(),
                request.getExerciseMetrics(),
                repsForProcessing
        );
        session.setExerciseMetricsJson(toJson(normalizedMetrics));

        // Deduplicate and persist detected form errors
        List<DetectedFormErrorDto> deduplicatedErrors = metricsProcessor.deduplicateDetectedErrors(request.getDetectedErrors());
        session.setDetectedErrorsJson(toJson(deduplicatedErrors));

        if (session.getAiSummaryStatus() == null || session.getAiSummaryStatus().isBlank()) {
            session.setAiSummaryStatus("PENDING");
        }

        AnalysisSession saved = sessionRepository.save(session);
        log.info("Completed Live AnalysisSession id={} user={} totalReps={} overallScore={}",
                sessionId, userId, saved.getTotalReps(), saved.getOverallScore());
        return mapToDto(saved, true);
    }

    @Transactional
    public AiPerformanceSummaryDto generateOrGetAiSummary(Long userId, String sessionId) {
        AnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis session not found."));

        if (!session.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this analysis session.");
        }

        // Return cached summary if already generated
        if ("COMPLETED".equalsIgnoreCase(session.getAiSummaryStatus()) && session.getAiRecommendationsJson() != null) {
            AiPerformanceSummaryDto cached = fromJson(session.getAiRecommendationsJson(), AiPerformanceSummaryDto.class);
            if (cached != null) {
                log.info("Returning cached AI performance summary for session={}", sessionId);
                return cached;
            }
        }

        String systemPrompt = buildAiSummarySystemPrompt();
        String userPrompt = buildCompactSessionContextJson(session);

        Optional<GeminiApiClient.GeminiSummaryResult> geminiResult = geminiApiClient.generatePerformanceSummary(systemPrompt, userPrompt);
        if (geminiResult.isEmpty()) {
            session.setAiSummaryStatus("UNAVAILABLE");
            sessionRepository.save(session);
            log.warn("Gemini AI summary unavailable for session={}", sessionId);
            return AiPerformanceSummaryDto.builder()
                    .status("UNAVAILABLE")
                    .build();
        }

        GeminiApiClient.GeminiSummaryResult res = geminiResult.get();
        GeminiStructuredPerformanceSummary payload = res.summary();

        List<String> recommendations = payload.getRecommendations() != null
                ? payload.getRecommendations().stream().filter(Objects::nonNull).limit(3).toList()
                : Collections.emptyList();

        AiPerformanceSummaryDto dto = AiPerformanceSummaryDto.builder()
                .overallAssessment(payload.getOverallAssessment())
                .strengths(payload.getStrengths() != null ? payload.getStrengths() : Collections.emptyList())
                .weaknesses(payload.getWeaknesses() != null ? payload.getWeaknesses() : Collections.emptyList())
                .recommendations(recommendations)
                .nextSessionFocus(payload.getNextSessionFocus())
                .modelUsed(res.modelUsed())
                .inputTokens(res.inputTokens())
                .outputTokens(res.outputTokens())
                .latencyMs(res.latencyMs())
                .status("COMPLETED")
                .generatedAt(Instant.now())
                .build();

        session.setAiSummary(payload.getOverallAssessment());
        session.setAiRecommendationsJson(toJson(dto));
        session.setAiSummaryStatus("COMPLETED");
        sessionRepository.save(session);

        log.info("Generated and cached Gemini AI performance summary for session={} in {}ms", sessionId, res.latencyMs());
        return dto;
    }

    @Transactional
    public AnalysisSessionResponseDto recordRep(Long userId, String sessionId, RepHistoryDto repDto) {
        AnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis session not found."));

        if (!session.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this analysis session.");
        }

        int repNum = repDto.getRepNumber() != null ? repDto.getRepNumber() : session.getReps().size() + 1;
        boolean exists = session.getReps().stream().anyMatch(r -> r.getRepNumber().equals(repNum));
        if (exists) {
            log.info("Rep #{} already recorded for session {}, skipping duplicate insertion", repNum, sessionId);
            return mapToDto(session, true);
        }

        BigDecimal jointAngle = repDto.getMinimumJointAngle() != null
                ? repDto.getMinimumJointAngle()
                : (repDto.getMinimumElbowAngle() != null ? repDto.getMinimumElbowAngle() : repDto.getMinimumKneeAngle());

        boolean repPassed = repDto.getFormScore() != null
                && repDto.getFormScore().compareTo(BigDecimal.valueOf(70)) >= 0
                && !"SHALLOW".equalsIgnoreCase(repDto.getDepthRating());
        String completionStatus = repDto.getCompletionStatus() != null
                ? repDto.getCompletionStatus()
                : (repPassed ? "SUCCESS" : "FAILED");

        RepHistory rep = RepHistory.builder()
                .session(session)
                .repNumber(repNum)
                .startedAt(repDto.getStartedAt() != null ? repDto.getStartedAt() : Instant.now())
                .completedAt(repDto.getCompletedAt() != null ? repDto.getCompletedAt() : Instant.now())
                .durationMs(repDto.getDurationMs())
                .timestampOffsetMs(repDto.getTimestampOffsetMs())
                .minimumKneeAngle(repDto.getMinimumKneeAngle() != null ? repDto.getMinimumKneeAngle() : jointAngle)
                .minimumJointAngle(jointAngle)
                .formScore(repDto.getFormScore())
                .depthRating(repDto.getDepthRating())
                .completionStatus(completionStatus)
                .feedback(repDto.getFeedback())
                .detectedErrorsJson(toJson(repDto.getDetectedErrors()))
                .repMetricsJson(toJson(repDto.getMetrics()))
                .build();

        session.getReps().add(rep);
        session.setTotalReps(session.getReps().size());

        if (rep.getFormScore() != null) {
            if (session.getBestFormScore() == null || rep.getFormScore().compareTo(session.getBestFormScore()) > 0) {
                session.setBestFormScore(rep.getFormScore());
            }
            if (session.getLowestFormScore() == null || rep.getFormScore().compareTo(session.getLowestFormScore()) < 0) {
                session.setLowestFormScore(rep.getFormScore());
            }
            BigDecimal sumScore = session.getReps().stream()
                    .map(RepHistory::getFormScore)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = session.getReps().stream().filter(r -> r.getFormScore() != null).count();
            if (count > 0) {
                BigDecimal avg = sumScore.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
                session.setAverageFormScore(avg);
                if (session.getOverallScore() == null) {
                    session.setOverallScore(avg);
                }
            }
            if (repPassed) {
                session.setCorrectReps(session.getCorrectReps() != null ? session.getCorrectReps() + 1 : 1);
            } else {
                session.setIncorrectReps(session.getIncorrectReps() != null ? session.getIncorrectReps() + 1 : 1);
            }
        }

        AnalysisSession saved = sessionRepository.save(session);
        return mapToDto(saved, true);
    }

    @Transactional(readOnly = true)
    public List<AnalysisSessionResponseDto> getUserHistory(Long userId) {
        return getUserHistory(userId, null);
    }

    @Transactional(readOnly = true)
    public List<AnalysisSessionResponseDto> getUserHistory(Long userId, String exerciseType) {
        List<AnalysisSession> sessions;
        if (exerciseType != null && !exerciseType.isBlank() && !"ALL".equalsIgnoreCase(exerciseType.trim())) {
            sessions = sessionRepository.findByUserIdAndExerciseTypeOrderByCreatedAtDesc(userId, exerciseType.toUpperCase().trim());
        } else {
            sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return sessions.stream().map(s -> mapToDto(s, false)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AnalysisSessionResponseDto getSessionDetails(Long userId, String sessionId) {
        AnalysisSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis session not found."));

        if (!session.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this analysis session.");
        }

        return mapToDto(session, true);
    }

    @Transactional(readOnly = true)
    public AnalysisHistorySummaryDto getUserHistorySummary(Long userId) {
        Long totalSessions = sessionRepository.countSessionsByUserId(userId);
        Long totalReps = sessionRepository.sumRepsByUserId(userId);
        BigDecimal avgScore = sessionRepository.averageScoreByUserId(userId);
        BigDecimal maxScore = sessionRepository.maxScoreByUserId(userId);
        BigDecimal totalDuration = sessionRepository.sumDurationByUserId(userId);

        if (avgScore != null) {
            avgScore = avgScore.setScale(1, RoundingMode.HALF_UP);
        }
        if (maxScore != null) {
            maxScore = maxScore.setScale(1, RoundingMode.HALF_UP);
        }

        return AnalysisHistorySummaryDto.builder()
                .totalSessions(totalSessions != null ? totalSessions : 0L)
                .totalReps(totalReps != null ? totalReps : 0L)
                .averageFormScore(avgScore)
                .bestFormScore(maxScore)
                .trainingDurationSeconds(totalDuration != null ? totalDuration : BigDecimal.ZERO)
                .build();
    }

    private Long resolveExerciseId(Long providedExerciseId, String exerciseCode) {
        if (providedExerciseId != null) {
            return providedExerciseId;
        }
        if (exerciseCode == null || exerciseCode.isBlank()) {
            return null;
        }
        Optional<Exercise> found = exerciseRepository.findByCode(exerciseCode.toUpperCase().trim());
        if (found.isPresent()) {
            return found.get().getId();
        }
        if ("PLANCHE".equalsIgnoreCase(exerciseCode.trim())) {
            return exerciseRepository.findByCode("FULL_PLANCHE")
                    .or(() -> exerciseRepository.findByCode("TUCK_PLANCHE"))
                    .map(Exercise::getId)
                    .orElse(null);
        }
        return null;
    }

    private String buildAiSummarySystemPrompt() {
        return """
                You are an expert calisthenics and biomechanics coach analyzing a completed live camera analysis workout.
                You MUST obey these strict grounding rules:
                1. Use ONLY the factual metrics, repetitions, scores, and detectedErrors provided in the user JSON payload.
                2. NEVER invent repetitions, scores, joint angles, or form errors that are NOT present in the JSON payload.
                3. NEVER claim an error occurred unless it appears in `detectedErrors`.
                4. NEVER override or alter the numeric YOLO/rule-based scores.
                5. If `totalReps` is 0 or certain metrics have status `UNAVAILABLE`, honestly explain the lack of data or focus only on valid available measurements.
                6. Provide:
                   - overall_assessment: concise, accurate 2-3 sentence performance overview
                   - strengths: 1-3 actual observed strengths grounded in high scores or clean execution
                   - weaknesses: 0-3 actual weaknesses grounded in detectedErrors or low-scoring metrics (empty list if none)
                   - recommendations: up to 3 specific, actionable biomechanical corrections for improvement
                   - next_session_focus: 1 clear priority target for the next training session
                """;
    }

    private String buildCompactSessionContextJson(AnalysisSession session) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("exerciseType", session.getExerciseType());
        ctx.put("durationSeconds", session.getDurationSeconds());
        ctx.put("totalReps", session.getTotalReps());
        ctx.put("correctReps", session.getCorrectReps());
        ctx.put("incorrectReps", session.getIncorrectReps());
        ctx.put("overallScore", session.getOverallScore() != null ? session.getOverallScore() : session.getAverageFormScore());
        ctx.put("averageFormScore", session.getAverageFormScore());
        ctx.put("bestFormScore", session.getBestFormScore());
        ctx.put("lowestFormScore", session.getLowestFormScore());
        ctx.put("analysisConfidence", session.getAnalysisConfidence());
        if (session.getPeakHoldDurationSeconds() != null) {
            ctx.put("peakHoldDurationSeconds", session.getPeakHoldDurationSeconds());
        }

        List<ExerciseFormMetricDto> metrics = parseMetricsJson(session.getExerciseMetricsJson());
        if (!metrics.isEmpty()) {
            ctx.put("metrics", metrics.stream().map(m -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", m.getLabel());
                item.put("status", m.getStatus());
                if ("UNAVAILABLE".equals(m.getStatus())) {
                    item.put("reason", m.getUnavailableReason());
                } else {
                    item.put("score", m.getScore());
                    item.put("value", m.getValue());
                    item.put("unit", m.getUnit());
                }
                return item;
            }).toList());
        }

        List<DetectedFormErrorDto> errors = parseErrorsJson(session.getDetectedErrorsJson());
        ctx.put("detectedErrors", errors.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("errorName", e.getErrorName());
            item.put("occurrences", e.getOccurrences());
            item.put("severity", e.getSeverity());
            if (e.getRepNumbers() != null && !e.getRepNumbers().isEmpty()) {
                item.put("affectedReps", e.getRepNumbers());
            }
            return item;
        }).toList());

        return toJson(ctx);
    }

    private List<RepHistoryDto> mapRepEntitiesToDtos(List<RepHistory> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(r -> {
            BigDecimal jointAngle = r.getMinimumJointAngle() != null
                    ? r.getMinimumJointAngle()
                    : r.getMinimumKneeAngle();
            return RepHistoryDto.builder()
                    .id(r.getId())
                    .repNumber(r.getRepNumber())
                    .startedAt(r.getStartedAt())
                    .completedAt(r.getCompletedAt())
                    .durationMs(r.getDurationMs())
                    .timestampOffsetMs(r.getTimestampOffsetMs())
                    .minimumKneeAngle(r.getMinimumKneeAngle() != null ? r.getMinimumKneeAngle() : jointAngle)
                    .minimumJointAngle(jointAngle)
                    .minimumElbowAngle(jointAngle)
                    .formScore(r.getFormScore())
                    .depthRating(r.getDepthRating())
                    .completionStatus(r.getCompletionStatus())
                    .feedback(r.getFeedback())
                    .detectedErrors(parseStringListJson(r.getDetectedErrorsJson()))
                    .metrics(parseMapJson(r.getRepMetricsJson()))
                    .build();
        }).collect(Collectors.toList());
    }

    private AnalysisSessionResponseDto mapToDto(AnalysisSession session, boolean includeReps) {
        List<RepHistoryDto> repDtos = includeReps ? mapRepEntitiesToDtos(session.getReps()) : null;
        List<ExerciseFormMetricDto> metrics = parseMetricsJson(session.getExerciseMetricsJson());
        List<DetectedFormErrorDto> errors = parseErrorsJson(session.getDetectedErrorsJson());
        AiPerformanceSummaryDto aiSummaryDto = session.getAiRecommendationsJson() != null
                ? fromJson(session.getAiRecommendationsJson(), AiPerformanceSummaryDto.class)
                : null;

        return AnalysisSessionResponseDto.builder()
                .id(session.getId())
                .userId(session.getUser().getId())
                .exerciseType(session.getExerciseType())
                .exerciseId(session.getExerciseId())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .durationSeconds(session.getDurationSeconds())
                .totalReps(session.getTotalReps())
                .overallScore(session.getOverallScore() != null ? session.getOverallScore() : session.getAverageFormScore())
                .averageFormScore(session.getAverageFormScore())
                .bestFormScore(session.getBestFormScore())
                .lowestFormScore(session.getLowestFormScore())
                .analysisConfidence(session.getAnalysisConfidence())
                .peakHoldDurationSeconds(session.getPeakHoldDurationSeconds())
                .correctReps(session.getCorrectReps())
                .incorrectReps(session.getIncorrectReps())
                .averageKneeAngle(session.getAverageKneeAngle())
                .deepestKneeAngle(session.getDeepestKneeAngle())
                .averageJointAngle(session.getAverageJointAngle() != null ? session.getAverageJointAngle() : session.getAverageKneeAngle())
                .deepestJointAngle(session.getDeepestJointAngle() != null ? session.getDeepestJointAngle() : session.getDeepestKneeAngle())
                .analysisStatus(session.getAnalysisStatus())
                .cameraView(session.getCameraView())
                .createdAt(session.getCreatedAt())
                .exerciseMetrics(metrics)
                .detectedErrors(errors)
                .aiSummary(session.getAiSummary())
                .aiRecommendations(aiSummaryDto)
                .aiSummaryStatus(session.getAiSummaryStatus())
                .originalVideoUrl(session.getOriginalVideoUrl())
                .annotatedVideoUrl(session.getAnnotatedVideoUrl())
                .reps(repDtos)
                .build();
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize JSON field: {}", e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON to {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private List<ExerciseFormMetricDto> parseMetricsJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<DetectedFormErrorDto> parseErrorsJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> parseStringListJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Double> parseMapJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
