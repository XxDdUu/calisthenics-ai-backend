package com.calisthenics.backend.adminai.entity;

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
        name = "admin_ai_conversations",
        indexes = {
                @Index(name = "idx_admin_ai_conv_admin_updated", columnList = "admin_user_id, updated_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAiConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Column(nullable = false, length = 255)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("createdAt ASC")
    private List<AdminAiMessage> messages = new ArrayList<>();
}
