package com.calisthenics.backend.aichat.repository;

import com.calisthenics.backend.aichat.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<AiMessage> findTop15ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<AiMessage> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
