package com.calisthenics.backend.exercisetree.repository;

import com.calisthenics.backend.exercisetree.entity.ExerciseProgressionEdge;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseProgressionEdgeRepository extends JpaRepository<ExerciseProgressionEdge, Long> {

    @EntityGraph(attributePaths = {"sourceExercise", "targetExercise"})
    List<ExerciseProgressionEdge> findAllByOrderByTrainingTypeAscDisplayOrderAscIdAsc();

    @EntityGraph(attributePaths = {"sourceExercise", "targetExercise"})
    List<ExerciseProgressionEdge> findByTrainingTypeOrderByDisplayOrderAscIdAsc(ExerciseTreeCategoryType trainingType);

    @EntityGraph(attributePaths = {"sourceExercise", "targetExercise"})
    List<ExerciseProgressionEdge> findBySourceExerciseIdOrTargetExerciseId(Long sourceExerciseId, Long targetExerciseId);

    boolean existsBySourceExerciseIdAndTargetExerciseIdAndTrainingTypeAndRelationType(
            Long sourceExerciseId,
            Long targetExerciseId,
            ExerciseTreeCategoryType trainingType,
            ProgressionRelationType relationType
    );
}
