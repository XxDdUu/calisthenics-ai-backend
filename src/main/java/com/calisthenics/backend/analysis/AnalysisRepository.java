package com.calisthenics.backend.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Analysis> findByUserIdAndExerciseIdOrderByCreatedAtDesc(Long userId, Long exerciseId);

    long countByExerciseId(Long exerciseId);
}
