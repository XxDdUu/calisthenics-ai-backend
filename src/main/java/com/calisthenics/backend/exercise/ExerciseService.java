package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.CreateExerciseRequest;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;

    @Transactional(readOnly = true)
    public List<ExerciseResponse> getAllExercises() {
        return exerciseRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExerciseResponse getExerciseById(Long id) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exercise not found with id: " + id));
        return mapToResponse(exercise);
    }

    @Transactional(readOnly = true)
    public ExerciseResponse getExerciseByCode(String code) {
        Exercise exercise = exerciseRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Exercise not found with code: " + code));
        return mapToResponse(exercise);
    }

    @Transactional
    public ExerciseResponse createExercise(CreateExerciseRequest request) {
        if (exerciseRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Exercise with code " + request.getCode() + " already exists");
        }

        Exercise exercise = Exercise.builder()
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .category(request.getCategory())
                .difficulty(request.getDifficulty())
                .description(request.getDescription())
                .targetMuscles(request.getTargetMuscles())
                .instructions(request.getInstructions())
                .build();

        Exercise saved = exerciseRepository.save(exercise);
        return mapToResponse(saved);
    }

    public ExerciseResponse mapToResponse(Exercise exercise) {
        return ExerciseResponse.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .code(exercise.getCode())
                .category(exercise.getCategory())
                .difficulty(exercise.getDifficulty())
                .description(exercise.getDescription())
                .targetMuscles(exercise.getTargetMuscles())
                .instructions(exercise.getInstructions())
                .imageUrl(exercise.getImageUrl())
                .videoUrl(exercise.getVideoUrl())
                .equipment(exercise.getEquipment())
                .active(exercise.isActive())
                .mediaItems(ExerciseMediaMapper.toResponses(exercise))
                .build();
    }
}
