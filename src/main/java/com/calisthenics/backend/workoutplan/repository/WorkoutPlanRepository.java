package com.calisthenics.backend.workoutplan.repository;

import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    List<WorkoutPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    Optional<WorkoutPlan> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    Optional<WorkoutPlan> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);
}
