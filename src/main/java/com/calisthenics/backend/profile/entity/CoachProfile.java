package com.calisthenics.backend.profile.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "coach_profiles",
        indexes = {
                @Index(name = "idx_coach_profiles_user", columnList = "user_id"),
                @Index(name = "idx_coach_profiles_public", columnList = "is_public")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachProfile {

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

    @Column(length = 500)
    private String specialties;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "training_philosophy", length = 1000)
    private String trainingPhilosophy;

    @Column(name = "coaching_description", length = 2000)
    private String coachingDescription;

    @Column(length = 255)
    private String languages;

    @Column(length = 1000)
    private String certifications;

    @Column(name = "social_links", length = 1000)
    private String socialLinks;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @Column(name = "is_accepting_clients", nullable = false)
    @Builder.Default
    private Boolean isAcceptingClients = true;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
