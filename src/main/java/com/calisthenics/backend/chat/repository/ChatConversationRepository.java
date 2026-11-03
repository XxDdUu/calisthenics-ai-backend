package com.calisthenics.backend.chat.repository;

import com.calisthenics.backend.chat.entity.ChatConversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    @EntityGraph(attributePaths = {"coach", "client"})
    @Query("select c from ChatConversation c where c.coach.id = :userId or c.client.id = :userId order by c.lastMessageAt desc nulls last, c.updatedAt desc")
    List<ChatConversation> findByCoachIdOrClientIdOrderByLastMessageAtDesc(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"coach", "client"})
    Optional<ChatConversation> findByCoachIdAndClientId(Long coachId, Long clientId);

    @EntityGraph(attributePaths = {"coach", "client"})
    @Query("select c from ChatConversation c where c.id = :conversationId and (c.coach.id = :userId or c.client.id = :userId)")
    Optional<ChatConversation> findByIdAndParticipant(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
