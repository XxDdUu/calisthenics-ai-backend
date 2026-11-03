package com.calisthenics.backend.profile.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "client_profiles",
        indexes = {
                @Index(name = "idx_client_profiles_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @Column(name = "training_experience", length = 500)
    private String trainingExperience;

    @Column(name = "primary_goals", length = 500)
    private String primaryGoals;

    @Column(name = "training_interests", length = 500)
    private String trainingInterests;

    @Column(name = "preferred_frequency", length = 50)
    private String preferredFrequency;

    @Column(name = "coaching_language", length = 100)
    private String coachingLanguage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
