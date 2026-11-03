package com.calisthenics.backend.workout;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.dto.CreateWorkoutSessionRequest;
import com.calisthenics.backend.workout.dto.WorkoutLogDto;
import com.calisthenics.backend.workout.dto.WorkoutSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private final WorkoutSessionRepository sessionRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkoutSessionResponse createSession(Long userId, CreateWorkoutSessionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        WorkoutSession session = WorkoutSession.builder()
                .user(user)
                .title(request.getTitle())
                .notes(request.getNotes())
                .startedAt(request.getStartedAt())
                .completedAt(request.getCompletedAt())
                .build();

        if (request.getLogs() != null && !request.getLogs().isEmpty()) {
            List<WorkoutLog> logs = new ArrayList<>();
            for (WorkoutLogDto logDto : request.getLogs()) {
                Exercise exercise = exerciseRepository.findById(logDto.getExerciseId())
                        .orElseThrow(() -> new RuntimeException("Exercise not found with id: " + logDto.getExerciseId()));

                WorkoutLog log = WorkoutLog.builder()
                        .workoutSession(session)
                        .exercise(exercise)
                        .sets(logDto.getSets())
                        .reps(logDto.getReps())
                        .weightKg(logDto.getWeightKg())
                        .holdTimeSeconds(logDto.getHoldTimeSeconds())
                        .notes(logDto.getNotes())
                        .build();

                logs.add(log);
            }
            session.setLogs(logs);
        }

        WorkoutSession saved = sessionRepository.save(session);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkoutSessionResponse> getUserSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkoutSessionResponse getSessionById(Long userId, Long id) {
        WorkoutSession session = sessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout session not found"));
        return mapToResponse(session);
    }

    @Transactional
    public void deleteSession(Long userId, Long id) {
        WorkoutSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workout session not found with id: " + id));

        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized deletion of workout session");
        }
        if (session.getWorkoutPlan() != null || !session.getExercises().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Plan-based workout history cannot be deleted through the legacy endpoint");
        }

        sessionRepository.delete(session);
    }

    private WorkoutSessionResponse mapToResponse(WorkoutSession session) {
        List<WorkoutLogDto> logDtos = session.getLogs().stream()
                .map(log -> WorkoutLogDto.builder()
                        .id(log.getId())
                        .exerciseId(log.getExercise().getId())
                        .exerciseName(log.getExercise().getName())
                        .exerciseCode(log.getExercise().getCode())
                        .sets(log.getSets())
                        .reps(log.getReps())
                        .weightKg(log.getWeightKg())
                        .holdTimeSeconds(log.getHoldTimeSeconds())
                        .notes(log.getNotes())
                        .build())
                .collect(Collectors.toList());

        return WorkoutSessionResponse.builder()
                .id(session.getId())
                .userId(session.getUser().getId())
                .title(session.getTitle())
                .notes(session.getNotes())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .createdAt(session.getCreatedAt())
                .logs(logDtos)
                .build();
    }
}
