package com.calisthenics.backend.aichat.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "ai_conversations",
        indexes = {
                @Index(name = "idx_ai_conversations_user_updated", columnList = "user_id, updated_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "conversation")
    @Builder.Default
    @OrderBy("createdAt ASC")
    private List<AiMessage> messages = new ArrayList<>();
}
