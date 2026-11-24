package com.calisthenics.backend.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutSessionExerciseRepository extends JpaRepository<WorkoutSessionExercise, Long> {

    long countByExerciseId(Long exerciseId);

    boolean existsByExerciseId(Long exerciseId);

    @Query("SELECT DISTINCT wse FROM WorkoutSessionExercise wse " +
           "JOIN FETCH wse.workoutSession ws " +
           "JOIN FETCH wse.exercise e " +
           "LEFT JOIN FETCH wse.sets s " +
           "WHERE ws.user.id = :userId AND ws.status = :status")
    List<WorkoutSessionExercise> findByUserIdAndSessionStatusWithSets(
            @Param("userId") Long userId,
            @Param("status") WorkoutSessionStatus status
    );
}
