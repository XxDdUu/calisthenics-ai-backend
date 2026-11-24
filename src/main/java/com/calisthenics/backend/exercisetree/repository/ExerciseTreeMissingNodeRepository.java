package com.calisthenics.backend.exercisetree.repository;

import com.calisthenics.backend.exercisetree.entity.ExerciseTreeMissingNode;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExerciseTreeMissingNodeRepository extends JpaRepository<ExerciseTreeMissingNode, Long> {

    List<ExerciseTreeMissingNode> findByResolvedExerciseIdIsNullOrderByTrainingTypeAscSubcategoryKeyAscIdAsc();

    List<ExerciseTreeMissingNode> findByTrainingTypeAndResolvedExerciseIdIsNullOrderBySubcategoryKeyAscIdAsc(
            ExerciseTreeCategoryType trainingType
    );

    List<ExerciseTreeMissingNode> findByTrainingTypeAndSubcategoryKeyAndResolvedExerciseIdIsNullOrderByIdAsc(
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey
    );

    boolean existsByTrainingTypeAndSubcategoryKeyAndSuggestedNameIgnoreCase(
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey,
            String suggestedName
    );
}
