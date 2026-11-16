package com.calisthenics.backend.videoanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {
    List<VideoAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<VideoAnalysis> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
