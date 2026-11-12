package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.AdminAiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminAiMessageRepository extends JpaRepository<AdminAiMessage, Long> {

    List<AdminAiMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<AdminAiMessage> findTop20ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<AdminAiMessage> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
