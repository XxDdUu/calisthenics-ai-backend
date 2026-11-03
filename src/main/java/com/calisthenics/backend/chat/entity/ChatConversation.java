package com.calisthenics.backend.chat.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "chat_conversations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_conversation_pair", columnNames = {"coach_id", "client_id"})
        },
        indexes = {
                @Index(name = "idx_chat_conversation_coach", columnList = "coach_id, updated_at"),
                @Index(name = "idx_chat_conversation_client", columnList = "client_id, updated_at"),
                @Index(name = "idx_chat_conversation_last_message", columnList = "last_message_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(name = "coach_last_read_at")
    private ZonedDateTime coachLastReadAt;

    @Column(name = "client_last_read_at")
    private ZonedDateTime clientLastReadAt;

    @Column(name = "last_message_at")
    private ZonedDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
