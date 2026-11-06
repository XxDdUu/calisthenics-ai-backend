package com.calisthenics.backend.workoutplan.mapper;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseMediaMapper;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanDayResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanExerciseResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.StreamSupport;

@Component
public class WorkoutPlanMapper {

    public WorkoutPlanResponse toResponse(WorkoutPlan plan) {
        return toResponse(plan, plan.getDays());
    }

    public WorkoutPlanResponse toResponse(WorkoutPlan plan, Iterable<WorkoutPlanDay> days) {
        return WorkoutPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .splitType(plan.getSplitType())
                .active(plan.isActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .days(StreamSupport.stream(days.spliterator(), false)
                        .map(this::toDayResponse)
                        .sorted(Comparator.comparing(WorkoutPlanDayResponse::getDisplayOrder))
                        .toList())
                .build();
    }

    public WorkoutPlanDayResponse toDayResponse(WorkoutPlanDay day) {
        return WorkoutPlanDayResponse.builder()
                .id(day.getId())
                .dayOfWeek(day.getDayOfWeek())
                .sessionType(day.getSessionType())
                .workoutType(day.getSessionType())
                .name(day.getName())
                .customWorkoutName(day.getName())
                .workoutName(displayName(day))
                .description(day.getDescription())
                .restDay(day.isRestDay())
                .displayOrder(day.getDisplayOrder())
                .exercises(day.getExercises().stream()
                        .map(this::toExerciseResponse)
                        .sorted(Comparator.comparing(WorkoutPlanExerciseResponse::getDisplayOrder))
                        .toList())
                .build();
    }

    private String displayName(WorkoutPlanDay day) {
        if (day.isRestDay() || day.getSessionType() == WorkoutSessionType.REST) {
            return "Rest Day";
        }
        if (day.getName() != null && !day.getName().isBlank()) {
            return day.getName();
        }
        return day.getSessionType() == null ? null : day.getSessionType().name().replace('_', ' ');
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
                .imageUrl(exercise.getImageUrl())
                .videoUrl(exercise.getVideoUrl())
                .equipment(exercise.getEquipment())
                .active(exercise.isActive())
                .mediaItems(ExerciseMediaMapper.toResponses(exercise))
                .build();
    }
}
