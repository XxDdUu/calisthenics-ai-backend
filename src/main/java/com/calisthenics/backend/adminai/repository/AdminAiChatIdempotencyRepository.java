package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.AdminAiChatIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminAiChatIdempotencyRepository extends JpaRepository<AdminAiChatIdempotency, Long> {

    Optional<AdminAiChatIdempotency> findByAdminUserIdAndIdempotencyKey(Long adminUserId, String idempotencyKey);
}
