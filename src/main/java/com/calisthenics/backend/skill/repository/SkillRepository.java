package com.calisthenics.backend.skill.repository;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.skill.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByActiveTrueOrderByTrainingTypeAscDisplayOrderAscNameAsc();

    List<Skill> findByTrainingTypeAndActiveTrueOrderByDisplayOrderAscNameAsc(ExerciseTrainingType trainingType);

    Optional<Skill> findByCodeIgnoreCase(String code);

    Optional<Skill> findByTrainingTypeAndNameIgnoreCase(ExerciseTrainingType trainingType, String name);
}
