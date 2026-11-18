package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.BulkExerciseGenerationItem;
import com.calisthenics.backend.adminai.type.BulkExerciseItemOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BulkExerciseGenerationItemRepository extends JpaRepository<BulkExerciseGenerationItem, Long> {

    Page<BulkExerciseGenerationItem> findByJobIdAndOutcomeOrderByIdAsc(
            Long jobId,
            BulkExerciseItemOutcome outcome,
            Pageable pageable
    );

    Page<BulkExerciseGenerationItem> findByJobIdOrderByIdAsc(Long jobId, Pageable pageable);

    List<BulkExerciseGenerationItem> findByJobIdAndOutcomeOrderBySequenceNumberAscIdAsc(
            Long jobId,
            BulkExerciseItemOutcome outcome
    );

    @Query("SELECT COALESCE(MAX(i.sequenceNumber), 0) FROM BulkExerciseGenerationItem i WHERE i.job.id = :jobId AND i.outcome = com.calisthenics.backend.adminai.type.BulkExerciseItemOutcome.SAVED")
    int findMaxSavedSequenceNumber(@Param("jobId") Long jobId);
}
