package com.calisthenics.backend.skill.repository;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.skill.entity.SkillCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillCategoryRepository extends JpaRepository<SkillCategoryEntity, Long> {

    List<SkillCategoryEntity> findByActiveTrueOrderByTrainingTypeAscDisplayOrderAsc();

    List<SkillCategoryEntity> findByTrainingTypeAndActiveTrueOrderByDisplayOrderAsc(ExerciseTrainingType trainingType);

    Optional<SkillCategoryEntity> findByTrainingTypeAndCategoryKeyIgnoreCase(ExerciseTrainingType trainingType, String categoryKey);
}
