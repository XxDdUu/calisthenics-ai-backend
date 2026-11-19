package com.calisthenics.backend.exercise.muscle;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MuscleTaxonomyServiceTest {

    private final MuscleTaxonomyService service = new MuscleTaxonomyService();

    @Test
    void getAllMuscles_ReturnsAll22CanonicalMusclesInExactOrder() {
        List<MuscleGroupDto> muscles = service.getAllMuscles();

        assertThat(muscles).hasSize(22);
        assertThat(muscles).extracting(MuscleGroupDto::getId).containsExactly(
                "chest",
                "front_deltoids",
                "lateral_deltoids",
                "rear_deltoids",
                "biceps",
                "triceps",
                "forearms",
                "upper_trapezius",
                "middle_trapezius",
                "lower_trapezius",
                "latissimus_dorsi",
                "teres_major",
                "rhomboids",
                "erector_spinae",
                "rectus_abdominis",
                "obliques",
                "transverse_abdominis",
                "glutes",
                "quadriceps",
                "hamstrings",
                "adductors",
                "calves"
        );
        assertThat(muscles.get(0).getSvgElementIds()).containsExactly("chest_left", "chest_right");
        assertThat(muscles.get(2).getViews()).containsExactly(MuscleBodyView.FRONT, MuscleBodyView.BACK);
        assertThat(muscles.get(20).getDisplayName()).isEqualTo("Hip Adductors");
        assertThat(muscles.get(21).getDisplayOrder()).isEqualTo(22);
    }

    @Test
    void validateMuscleIds_RejectsUnknownOrBlankIds() {
        service.validateMuscleIds(List.of("chest", "front_deltoids", "calves"));

        ResponseStatusException unknown = assertThrows(
                ResponseStatusException.class,
                () -> service.validateMuscleIds(List.of("chest", "pectoral_minor"))
        );
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseStatusException blank = assertThrows(
                ResponseStatusException.class,
                () -> service.validateMuscleIds(List.of("   "))
        );
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resolveTargets_PrefersPersistedMappingsAndEnforcesDisjointPrimaryPriority() {
        Exercise exercise = Exercise.builder()
                .id(1L)
                .code("PUSH_UP")
                .name("Push-Up")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .build();

        exercise.setMuscleTargetMappings(List.of(
                ExerciseMuscleTarget.builder().id(1L).exercise(exercise).muscleId("triceps").role(MuscleRole.PRIMARY).displayOrder(0).build(),
                ExerciseMuscleTarget.builder().id(2L).exercise(exercise).muscleId("triceps").role(MuscleRole.SECONDARY).displayOrder(1).build(),
                ExerciseMuscleTarget.builder().id(3L).exercise(exercise).muscleId("front_deltoids").role(MuscleRole.SECONDARY).displayOrder(2).build()
        ));

        MuscleTaxonomyService.ResolvedMuscleTargets resolved = service.resolveTargets(exercise);

        assertThat(resolved.primaryMuscleIds()).containsExactly("triceps");
        assertThat(resolved.secondaryMuscleIds()).containsExactly("front_deltoids");
        assertThat(resolved.muscleTargets()).hasSize(2);
        assertThat(resolved.muscleTargets().get(0).getRole()).isEqualTo(MuscleRole.PRIMARY);
        assertThat(resolved.muscleTargets().get(1).getRole()).isEqualTo(MuscleRole.SECONDARY);
    }

    @Test
    void resolveTargets_ResolvesAll15CanonicalCodePresets() {
        List<String> canonicalCodes = List.of(
                "PUSH_UP", "DIPS", "PIKE_PUSH_UP", "PULL_UP", "MUSCLE_UP", "SQUAT", "L_SIT",
                "HANDSTAND", "TUCK_PLANCHE", "ADV_TUCK_PLANCHE", "STRADDLE_PLANCHE",
                "FULL_PLANCHE", "PLANCHE", "FRONT_LEVER", "FRONT_LEVER_RAISES"
        );

        for (String code : canonicalCodes) {
            Exercise exercise = Exercise.builder()
                    .name(code)
                    .code(code)
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .build();

            MuscleTaxonomyService.ResolvedMuscleTargets resolved = service.resolveTargets(exercise);

            assertThat(resolved.primaryMuscleIds()).as("primary for %s", code).isNotEmpty();
            assertThat(resolved.secondaryMuscleIds()).as("secondary for %s", code).isNotEmpty();
            assertThat(resolved.primaryMuscleIds()).doesNotContainAnyElementsOf(resolved.secondaryMuscleIds());
            for (String id : resolved.primaryMuscleIds()) {
                assertThat(MuscleId.fromId(id)).isPresent();
            }
            for (String id : resolved.secondaryMuscleIds()) {
                assertThat(MuscleId.fromId(id)).isPresent();
            }
        }
    }

    @Test
    void resolveTargets_ParsesLegacyAnatomicalAliasesAndEnforcesDisjointness() {
        Exercise custom = Exercise.builder()
                .name("Ring Fly")
                .code("RING_FLY")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .targetMuscles("Pectorals, Anterior Deltoids, Core")
                .secondaryMuscles("Chest, Posterior Deltoids, Grip, Gastrocnemius, Soleus, Quads, Gluteus Maximus, Lower Back")
                .build();

        MuscleTaxonomyService.ResolvedMuscleTargets resolved = service.resolveTargets(custom);

        assertThat(resolved.primaryMuscleIds()).containsExactly(
                "chest", "front_deltoids", "rectus_abdominis", "transverse_abdominis"
        );
        // Chest appeared in both -> must remain PRIMARY only
        assertThat(resolved.secondaryMuscleIds()).containsExactly(
                "rear_deltoids", "forearms", "calves", "quadriceps", "glutes", "erector_spinae"
        );
    }

    @Test
    void resolveTargets_AppliesCategoryBaselineOnlyWhenNoTextMatched() {
        Exercise unmappedPull = Exercise.builder()
                .name("Custom Pull")
                .code("CUSTOM_PULL")
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.BEGINNER)
                .build();

        MuscleTaxonomyService.ResolvedMuscleTargets pullResolved = service.resolveTargets(unmappedPull);
        assertThat(pullResolved.primaryMuscleIds()).containsExactly("latissimus_dorsi");
        assertThat(pullResolved.secondaryMuscleIds()).containsExactly("biceps", "rhomboids");

        Exercise unmappedSkill = Exercise.builder()
                .name("Unmapped Skill")
                .code("UNMAPPED_SKILL")
                .category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.ELITE)
                .build();

        MuscleTaxonomyService.ResolvedMuscleTargets skillResolved = service.resolveTargets(unmappedSkill);
        assertThat(skillResolved.primaryMuscleIds()).isEmpty();
        assertThat(skillResolved.secondaryMuscleIds()).isEmpty();
        assertThat(skillResolved.muscleTargets()).isEmpty();
    }

    @Test
    void syncExerciseMuscleTargets_DeduplicatesEnforcesPrimaryPrecedenceAndBackfillsBlankStrings() {
        Exercise exercise = Exercise.builder()
                .name("Archer Push-Up")
                .code("ARCHER_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .build();

        service.syncExerciseMuscleTargets(
                exercise,
                List.of("chest", "front_deltoids", "chest"),
                List.of("front_deltoids", "triceps", "triceps", "obliques")
        );

        assertThat(exercise.getMuscleTargetMappings())
                .extracting(ExerciseMuscleTarget::getMuscleId)
                .containsExactly("chest", "front_deltoids", "triceps", "obliques");
        assertThat(exercise.getMuscleTargetMappings())
                .extracting(ExerciseMuscleTarget::getRole)
                .containsExactly(MuscleRole.PRIMARY, MuscleRole.PRIMARY, MuscleRole.SECONDARY, MuscleRole.SECONDARY);
        assertThat(exercise.getTargetMuscles()).isEqualTo("Chest, Front Deltoids");
        assertThat(exercise.getSecondaryMuscles()).isEqualTo("Triceps, Obliques");

        // Re-sync in place swapping triceps to primary and removing obliques
        service.syncExerciseMuscleTargets(
                exercise,
                List.of("triceps"),
                List.of("chest")
        );

        assertThat(exercise.getMuscleTargetMappings())
                .extracting(ExerciseMuscleTarget::getMuscleId)
                .containsExactly("triceps", "chest");
        assertThat(exercise.getMuscleTargetMappings())
                .extracting(ExerciseMuscleTarget::getRole)
                .containsExactly(MuscleRole.PRIMARY, MuscleRole.SECONDARY);
        // Existing non-blank legacy strings are preserved
        assertThat(exercise.getTargetMuscles()).isEqualTo("Chest, Front Deltoids");
    }
}
