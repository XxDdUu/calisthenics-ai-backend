package com.calisthenics.backend.workoutplan.repository;

import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanExerciseRepository extends JpaRepository<WorkoutPlanExercise, Long> {

    @EntityGraph(attributePaths = "exercise")
    List<WorkoutPlanExercise> findByWorkoutPlanDayIdOrderByDisplayOrderAsc(Long workoutPlanDayId);

    Optional<WorkoutPlanExercise> findByIdAndWorkoutPlanDayId(Long id, Long workoutPlanDayId);

    boolean existsByWorkoutPlanDayIdAndDisplayOrderAndIdNot(
            Long workoutPlanDayId, Integer displayOrder, Long id);

    boolean existsByWorkoutPlanDayIdAndDisplayOrder(Long workoutPlanDayId, Integer displayOrder);
}
