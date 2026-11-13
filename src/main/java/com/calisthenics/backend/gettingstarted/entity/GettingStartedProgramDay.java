package com.calisthenics.backend.gettingstarted.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "getting_started_program_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_getting_started_day", columnNames = {"program_id", "day_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedProgramDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private GettingStartedProgram program;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "programDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<GettingStartedProgramExercise> exercises = new ArrayList<>();
}
