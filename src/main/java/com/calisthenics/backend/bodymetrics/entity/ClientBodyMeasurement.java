package com.calisthenics.backend.bodymetrics.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(
        name = "client_body_measurements",
        indexes = {
                @Index(name = "idx_client_body_measurements_client_time", columnList = "client_id, measured_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientBodyMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "body_fat_percentage", precision = 4, scale = 1)
    private BigDecimal bodyFatPercentage;

    @Column(name = "waist_cm", precision = 5, scale = 1)
    private BigDecimal waistCm;

    @Column(name = "chest_cm", precision = 5, scale = 1)
    private BigDecimal chestCm;

    @Column(name = "arm_cm", precision = 5, scale = 1)
    private BigDecimal armCm;

    @Column(name = "thigh_cm", precision = 5, scale = 1)
    private BigDecimal thighCm;

    @Column(name = "measured_at", nullable = false)
    private ZonedDateTime measuredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
