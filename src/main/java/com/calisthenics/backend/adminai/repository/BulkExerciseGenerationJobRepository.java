package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.BulkExerciseGenerationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BulkExerciseGenerationJobRepository extends JpaRepository<BulkExerciseGenerationJob, Long> {

    Optional<BulkExerciseGenerationJob> findByIdAndAdminUserId(Long id, Long adminUserId);

    Optional<BulkExerciseGenerationJob> findByAdminUserIdAndIdempotencyKey(Long adminUserId, String idempotencyKey);

    Page<BulkExerciseGenerationJob> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId, Pageable pageable);
}
