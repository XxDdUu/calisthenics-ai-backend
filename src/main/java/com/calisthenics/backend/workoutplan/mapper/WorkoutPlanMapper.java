package com.calisthenics.backend.workoutplan.mapper;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanDayResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanExerciseResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class WorkoutPlanMapper {

    public WorkoutPlanResponse toResponse(WorkoutPlan plan) {
        return WorkoutPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .splitType(plan.getSplitType())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .days(plan.getDays().stream()
                        .map(this::toDayResponse)
                        .sorted(Comparator.comparing(day -> day.getDayOfWeek().getOrder()))
                        .toList())
                .build();
    }

    public WorkoutPlanDayResponse toDayResponse(WorkoutPlanDay day) {
        return WorkoutPlanDayResponse.builder()
                .id(day.getId())
                .dayOfWeek(day.getDayOfWeek())
                .sessionType(day.getSessionType())
                .name(day.getName())
                .exercises(day.getExercises().stream()
                        .map(this::toExerciseResponse)
                        .sorted(Comparator.comparing(WorkoutPlanExerciseResponse::getDisplayOrder))
                        .toList())
                .build();
    }

    private WorkoutPlanExerciseResponse toExerciseResponse(WorkoutPlanExercise assignment) {
        return WorkoutPlanExerciseResponse.builder()
                .id(assignment.getId())
                .exercise(toExerciseResponse(assignment.getExercise()))
                .sets(assignment.getSets())
                .reps(assignment.getReps())
                .durationSeconds(assignment.getDurationSeconds())
                .restSeconds(assignment.getRestSeconds())
                .displayOrder(assignment.getDisplayOrder())
                .notes(assignment.getNotes())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    private ExerciseResponse toExerciseResponse(Exercise exercise) {
        return ExerciseResponse.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .code(exercise.getCode())
                .category(exercise.getCategory())
                .difficulty(exercise.getDifficulty())
                .description(exercise.getDescription())
                .targetMuscles(exercise.getTargetMuscles())
                .instructions(exercise.getInstructions())
                .build();
    }
}
