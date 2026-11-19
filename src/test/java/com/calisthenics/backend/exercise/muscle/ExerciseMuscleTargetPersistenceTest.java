package com.calisthenics.backend.exercise.muscle;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MuscleTaxonomyService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExerciseMuscleTargetPersistenceTest {

    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private ExerciseMuscleTargetRepository targetRepository;
    @Autowired
    private MuscleTaxonomyService taxonomyService;

    @Test
    void syncExerciseMuscleTargets_PersistsAndUpdatesRoleSwapsInPlaceWithoutConstraintViolations() {
        Exercise exercise = Exercise.builder()
                .name("Ring Dip")
                .code("RING_DIP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .active(true)
                .build();
        taxonomyService.syncExerciseMuscleTargets(
                exercise,
                List.of("chest", "triceps"),
                List.of("front_deltoids")
        );
        Exercise saved = exerciseRepository.saveAndFlush(exercise);

        List<ExerciseMuscleTarget> initial = targetRepository
                .findByExerciseIdOrderByDisplayOrderAscIdAsc(saved.getId());
        assertThat(initial).hasSize(3);

        // Swap chest from PRIMARY -> SECONDARY, promote front_deltoids to PRIMARY, drop triceps
        taxonomyService.syncExerciseMuscleTargets(
                saved,
                List.of("front_deltoids"),
                List.of("chest")
        );
        exerciseRepository.saveAndFlush(saved);

        List<ExerciseMuscleTarget> updated = targetRepository
                .findByExerciseIdOrderByDisplayOrderAscIdAsc(saved.getId());
        assertThat(updated).hasSize(2);
        assertThat(updated.get(0).getMuscleId()).isEqualTo("front_deltoids");
        assertThat(updated.get(0).getRole()).isEqualTo(MuscleRole.PRIMARY);
        assertThat(updated.get(1).getMuscleId()).isEqualTo("chest");
        assertThat(updated.get(1).getRole()).isEqualTo(MuscleRole.SECONDARY);
    }
}
