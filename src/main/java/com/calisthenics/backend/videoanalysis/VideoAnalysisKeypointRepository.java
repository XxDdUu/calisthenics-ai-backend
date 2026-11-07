package com.calisthenics.backend.videoanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoAnalysisKeypointRepository extends JpaRepository<VideoAnalysisKeypoint, Long> {
    List<VideoAnalysisKeypoint> findByVideoAnalysisIdOrderByFrameIndexAsc(Long videoAnalysisId);
}
