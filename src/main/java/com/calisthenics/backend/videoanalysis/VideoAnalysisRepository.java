package com.calisthenics.backend.videoanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoAnalysisRepository extends JpaRepository<VideoAnalysis, Long> {
    List<VideoAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
}
