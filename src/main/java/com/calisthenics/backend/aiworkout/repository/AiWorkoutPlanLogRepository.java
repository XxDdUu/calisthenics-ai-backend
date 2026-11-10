package com.calisthenics.backend.aiworkout.repository;

import com.calisthenics.backend.aiworkout.entity.AiWorkoutPlanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiWorkoutPlanLogRepository extends JpaRepository<AiWorkoutPlanLog, Long> {

    List<AiWorkoutPlanLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<AiWorkoutPlanLog> findByIdempotencyKey(String idempotencyKey);

    Optional<AiWorkoutPlanLog> findFirstByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(Long userId, String idempotencyKey);
}
