package com.calisthenics.backend.analysis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, String> {

    Optional<AnalysisSession> findByIdAndUserId(String id, Long userId);

    List<AnalysisSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<AnalysisSession> findByUserIdAndExerciseTypeOrderByCreatedAtDesc(Long userId, String exerciseType);

    Page<AnalysisSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM AnalysisSession s WHERE s.user.id = :userId")
    Long countSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.totalReps), 0) FROM AnalysisSession s WHERE s.user.id = :userId")
    Long sumRepsByUserId(@Param("userId") Long userId);

    @Query("SELECT AVG(s.averageFormScore) FROM AnalysisSession s WHERE s.user.id = :userId AND s.averageFormScore IS NOT NULL")
    BigDecimal averageScoreByUserId(@Param("userId") Long userId);

    @Query("SELECT MAX(s.bestFormScore) FROM AnalysisSession s WHERE s.user.id = :userId AND s.bestFormScore IS NOT NULL")
    BigDecimal maxScoreByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.durationSeconds), 0) FROM AnalysisSession s WHERE s.user.id = :userId")
    BigDecimal sumDurationByUserId(@Param("userId") Long userId);
}
