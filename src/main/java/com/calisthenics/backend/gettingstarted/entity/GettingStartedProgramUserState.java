package com.calisthenics.backend.gettingstarted.entity;

import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "getting_started_program_user_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_program_user_state", columnNames = {"user_id", "program_id", "program_version"}),
        indexes = @Index(name = "idx_program_user_state_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedProgramUserState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private GettingStartedProgram program;

    @Column(name = "program_version", nullable = false)
    @Builder.Default
    private Integer programVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OnboardingStatus state = OnboardingStatus.NOT_SHOWN;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
