package com.calisthenics.backend.exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    Optional<Exercise> findByCode(String code);

    List<Exercise> findByCategory(ExerciseCategory category);

    List<Exercise> findByDifficulty(DifficultyLevel difficulty);

    boolean existsByCode(String code);
}
