package com.calisthenics.backend.adminai.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "admin_ai_chat_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_admin_ai_chat_idemp", columnNames = {"admin_user_id", "idempotency_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAiChatIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
