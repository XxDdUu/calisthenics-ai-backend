package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminAiConversationRepository extends JpaRepository<AdminAiConversation, Long> {

    List<AdminAiConversation> findByAdminUserIdOrderByUpdatedAtDesc(Long adminUserId);

    Optional<AdminAiConversation> findFirstByAdminUserIdOrderByUpdatedAtDesc(Long adminUserId);

    Optional<AdminAiConversation> findByIdAndAdminUserId(Long id, Long adminUserId);
}
