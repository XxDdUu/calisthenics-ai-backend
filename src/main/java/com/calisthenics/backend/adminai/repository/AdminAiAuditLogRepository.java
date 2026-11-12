package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.AdminAiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAiAuditLogRepository extends JpaRepository<AdminAiAuditLog, Long> {

    List<AdminAiAuditLog> findTop50ByOrderByCreatedAtDesc();

    List<AdminAiAuditLog> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);
}
