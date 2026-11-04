package com.calisthenics.backend.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkoutSessionExerciseRepository extends JpaRepository<WorkoutSessionExercise, Long> {

    long countByExerciseId(Long exerciseId);

    boolean existsByExerciseId(Long exerciseId);
}
