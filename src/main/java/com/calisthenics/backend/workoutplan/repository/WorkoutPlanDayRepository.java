package com.calisthenics.backend.workoutplan.repository;

import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkoutPlanDayRepository extends JpaRepository<WorkoutPlanDay, Long> {

    Optional<WorkoutPlanDay> findByIdAndWorkoutPlanId(Long id, Long workoutPlanId);

    java.util.List<WorkoutPlanDay> findByWorkoutPlanIdOrderByDisplayOrderAsc(Long workoutPlanId);
}
