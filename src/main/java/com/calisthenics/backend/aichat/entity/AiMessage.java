package com.calisthenics.backend.aichat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "ai_messages",
        indexes = {
                @Index(name = "idx_ai_messages_conversation_created", columnList = "conversation_id, created_at ASC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;

    @Column(nullable = false, length = 32)
    private String role; // "user", "assistant", "system"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 64)
    private String intent;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
