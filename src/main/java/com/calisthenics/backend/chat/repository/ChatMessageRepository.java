package com.calisthenics.backend.chat.repository;

import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender"})
    @Query("select m from ChatMessage m where m.conversation.id = :conversationId order by m.createdAt asc, m.id asc")
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(@Param("conversationId") Long conversationId, Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    @Query("select m from ChatMessage m where m.conversation.id = :conversationId order by m.createdAt desc, m.id desc")
    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId, Pageable pageable);

    Optional<ChatMessage> findTopByConversationOrderByCreatedAtDesc(ChatConversation conversation);

    @EntityGraph(attributePaths = {"sender"})
    Optional<ChatMessage> findByConversationIdAndClientMessageId(Long conversationId, String clientMessageId);

    @Query("select count(m) from ChatMessage m where m.conversation.id = :conversationId and m.sender.id <> :userId and m.createdAt > :readAt")
    long countUnreadMessagesAfter(@Param("conversationId") Long conversationId,
                                  @Param("userId") Long userId,
                                  @Param("readAt") ZonedDateTime readAt);

    @Query("select count(m) from ChatMessage m where m.conversation.id = :conversationId and m.sender.id <> :userId")
    long countAllMessagesNotFromUser(@Param("conversationId") Long conversationId,
                                     @Param("userId") Long userId);

    default long countUnreadMessages(Long conversationId, Long userId, ZonedDateTime readAt) {
        if (readAt == null) {
            return countAllMessagesNotFromUser(conversationId, userId);
        }
        return countUnreadMessagesAfter(conversationId, userId, readAt);
    }
}
