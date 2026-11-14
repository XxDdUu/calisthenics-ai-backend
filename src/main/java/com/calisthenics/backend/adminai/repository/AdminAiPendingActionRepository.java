package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.AdminAiPendingAction;
import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminAiPendingActionRepository extends JpaRepository<AdminAiPendingAction, Long> {

    Optional<AdminAiPendingAction> findByConfirmationTokenAndAdminUserId(String confirmationToken, Long adminUserId);

    Optional<AdminAiPendingAction> findFirstByAdminUserIdAndConversationIdAndStatusOrderByCreatedAtDesc(
            Long adminUserId,
            Long conversationId,
            AdminPendingActionStatus status
    );

    List<AdminAiPendingAction> findAllByAdminUserIdAndConversationIdAndStatus(
            Long adminUserId,
            Long conversationId,
            AdminPendingActionStatus status
    );
}
