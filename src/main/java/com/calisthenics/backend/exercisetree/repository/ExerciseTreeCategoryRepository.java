package com.calisthenics.backend.exercisetree.repository;

import com.calisthenics.backend.exercisetree.entity.ExerciseTreeCategory;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseTreeCategoryRepository extends JpaRepository<ExerciseTreeCategory, Long> {

    List<ExerciseTreeCategory> findByActiveTrueOrderByTrainingTypeAscDisplayOrderAsc();

    List<ExerciseTreeCategory> findByTrainingTypeAndActiveTrueOrderByDisplayOrderAsc(ExerciseTreeCategoryType trainingType);

    Optional<ExerciseTreeCategory> findByTrainingTypeAndSubcategoryKey(
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey
    );
}
