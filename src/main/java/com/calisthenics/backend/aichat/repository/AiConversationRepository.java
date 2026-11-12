package com.calisthenics.backend.aichat.repository;

import com.calisthenics.backend.aichat.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<AiConversation> findByIdAndUserId(Long id, Long userId);

    Optional<AiConversation> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    @Query("SELECT c FROM AiConversation c LEFT JOIN FETCH c.messages WHERE c.id = :id AND c.user.id = :userId")
    Optional<AiConversation> findByIdAndUserIdWithMessages(@Param("id") Long id, @Param("userId") Long userId);
}
