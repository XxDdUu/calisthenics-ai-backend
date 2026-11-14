package com.calisthenics.backend.gettingstarted.entity;

import com.calisthenics.backend.gettingstarted.enums.GettingStartedCampaignEventType;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "getting_started_campaign_events",
        indexes = {
                @Index(name = "idx_gs_campaign_events_program_type", columnList = "program_id, event_type"),
                @Index(name = "idx_gs_campaign_events_prog_user_type", columnList = "program_id, user_id, event_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedCampaignEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private GettingStartedProgram program;

    @Column(name = "program_version", nullable = false)
    @Builder.Default
    private Integer programVersion = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private GettingStartedCampaignEventType eventType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
