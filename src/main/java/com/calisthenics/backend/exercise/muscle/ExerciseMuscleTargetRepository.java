package com.calisthenics.backend.exercise.muscle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ExerciseMuscleTargetRepository extends JpaRepository<ExerciseMuscleTarget, Long> {

    List<ExerciseMuscleTarget> findByExerciseIdOrderByDisplayOrderAscIdAsc(Long exerciseId);

    List<ExerciseMuscleTarget> findByExerciseIdInOrderByDisplayOrderAscIdAsc(Collection<Long> exerciseIds);

    void deleteByExerciseId(Long exerciseId);
}
