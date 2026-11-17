package com.calisthenics.backend.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminProAuditLogRepository extends JpaRepository<AdminProAuditLog, Long> {

    Page<AdminProAuditLog> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId, Pageable pageable);

    Page<AdminProAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
