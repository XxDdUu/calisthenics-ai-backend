package com.calisthenics.backend.aichat.repository;

import com.calisthenics.backend.aichat.entity.AiWorkoutPlanChangePreview;
import com.calisthenics.backend.aichat.type.WorkoutPlanPreviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiWorkoutPlanChangePreviewRepository extends JpaRepository<AiWorkoutPlanChangePreview, Long> {

    Optional<AiWorkoutPlanChangePreview> findByPreviewIdAndUserId(String previewId, Long userId);

    Optional<AiWorkoutPlanChangePreview> findByPreviewIdAndUserIdAndConversationId(
            String previewId, Long userId, Long conversationId);

    @Query("SELECT p FROM AiWorkoutPlanChangePreview p " +
            "WHERE p.previewId = :previewId AND p.userId = :userId AND p.conversationId = :conversationId")
    Optional<AiWorkoutPlanChangePreview> findByPreviewIdAndUserIdAndConversationIdForUpdate(
            @Param("previewId") String previewId,
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AiWorkoutPlanChangePreview p SET p.status = :nextStatus, p.resolvedAt = :resolvedAt, p.version = p.version + 1 " +
            "WHERE p.previewId = :previewId AND p.userId = :userId AND p.conversationId = :conversationId AND p.status = :expectedStatus")
    int transitionStatusAtomically(
            @Param("previewId") String previewId,
            @Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("expectedStatus") WorkoutPlanPreviewStatus expectedStatus,
            @Param("nextStatus") WorkoutPlanPreviewStatus nextStatus,
            @Param("resolvedAt") ZonedDateTime resolvedAt);

    Optional<AiWorkoutPlanChangePreview> findFirstByUserIdAndConversationIdAndStatusOrderByCreatedAtDesc(
            Long userId, Long conversationId, WorkoutPlanPreviewStatus status);

    List<AiWorkoutPlanChangePreview> findByUserIdAndConversationIdAndStatus(
            Long userId, Long conversationId, WorkoutPlanPreviewStatus status);

    List<AiWorkoutPlanChangePreview> findByPreviewIdInAndUserId(Collection<String> previewIds, Long userId);

    void deleteByConversationId(Long conversationId);
}
