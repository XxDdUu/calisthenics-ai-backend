package com.calisthenics.backend.exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long>, JpaSpecificationExecutor<Exercise> {

    Optional<Exercise> findByCode(String code);

    Optional<Exercise> findByNameIgnoreCase(String name);

    Optional<Exercise> findByIdAndActiveTrue(Long id);

    List<Exercise> findByActiveTrue();

    List<Exercise> findByActiveTrueOrderByNameAsc();

    List<Exercise> findByCategory(ExerciseCategory category);

    List<Exercise> findByDifficulty(DifficultyLevel difficulty);

    long countByStatus(ExerciseStatus status);

    long countByOrigin(ExerciseOrigin origin);

    long countByActiveTrue();

    long countByActiveFalse();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
