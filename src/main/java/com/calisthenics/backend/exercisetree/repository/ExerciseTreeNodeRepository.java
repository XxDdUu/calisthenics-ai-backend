package com.calisthenics.backend.exercisetree.repository;

import com.calisthenics.backend.exercisetree.entity.ExerciseTreeNode;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseTreeNodeRepository extends JpaRepository<ExerciseTreeNode, Long> {

    @EntityGraph(attributePaths = {"exercise"})
    List<ExerciseTreeNode> findAllByOrderByTrainingTypeAscSubcategoryKeyAscTreeTierAscBranchOrderAsc();

    @EntityGraph(attributePaths = {"exercise"})
    List<ExerciseTreeNode> findByTrainingTypeOrderBySubcategoryKeyAscTreeTierAscBranchOrderAsc(
            ExerciseTreeCategoryType trainingType
    );

    @EntityGraph(attributePaths = {"exercise"})
    List<ExerciseTreeNode> findByTrainingTypeAndSubcategoryKeyOrderByTreeTierAscBranchOrderAsc(
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey
    );

    @EntityGraph(attributePaths = {"exercise"})
    List<ExerciseTreeNode> findByExerciseId(Long exerciseId);

    Optional<ExerciseTreeNode> findByExerciseIdAndTrainingTypeAndSubcategoryKey(
            Long exerciseId,
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey
    );

    boolean existsByExerciseIdAndTrainingTypeAndSubcategoryKey(
            Long exerciseId,
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey
    );
}
