package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;
import com.calisthenics.backend.adminai.service.ExerciseDraftValidationService;
import com.calisthenics.backend.adminai.service.ExerciseDraftValidationService.ExerciseDraftRequestContract;
import com.calisthenics.backend.adminai.service.ExerciseDraftValidationService.RequestedExerciseSpec;
import com.calisthenics.backend.adminai.service.ExerciseDraftValidationService.ValidationOutcome;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExerciseDraftValidationServiceTest {

    private final ExerciseDraftValidationService service = new ExerciseDraftValidationService();

    @Test
    void parsesExplicitLegExerciseListPreservingNegativeSuffixAndPerItemDifficulty() {
        String prompt = """
                Give me more recommended exercises of legs. Add these 3 exercises:
                1. Box Pistol Squat — LEGS — INTERMEDIATE
                2. Nordic Hamstring Curl (Negative) — LEGS — ADVANCED
                3. Cossack Squat — LEGS — INTERMEDIATE
                """;

        ExerciseDraftRequestContract contract = service.parseRequestContract(
                prompt,
                null,
                null,
                "Front Lever", // Stale skill filter must be ignored when prompt specifies legs + explicit exercises
                DifficultyLevel.BEGINNER, // Stale uniform difficulty ignored when per-item specs exist
                null
        );

        assertThat(contract.requiredCategory()).isEqualTo(ExerciseCategory.LEGS);
        assertThat(contract.expectedCount()).isEqualTo(3);
        assertThat(contract.requiredDifficulty()).isNull();
        assertThat(contract.explicitExercises()).containsExactly(
                new RequestedExerciseSpec("Box Pistol Squat", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE),
                new RequestedExerciseSpec("Nordic Hamstring Curl (Negative)", ExerciseCategory.LEGS, DifficultyLevel.ADVANCED),
                new RequestedExerciseSpec("Cossack Squat", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE)
        );
    }

    @Test
    void genericCategoryRequestDoesNotConfuseNumberingWithExplicitNames() {
        ExerciseDraftRequestContract contract = service.parseRequestContract(
                "Add 3 leg exercises",
                null,
                ExerciseCategory.LEGS,
                null,
                null,
                3
        );

        assertThat(contract.hasExplicitExercises()).isFalse();
        assertThat(contract.requiredCategory()).isEqualTo(ExerciseCategory.LEGS);
        assertThat(contract.expectedCount()).isEqualTo(3);
    }

    @Test
    void rejectsFrontLeverSubstitutionForLegRequest() {
        String prompt = """
                Give me more recommended exercises of legs. Add these 3 exercises:
                1. Box Pistol Squat — LEGS — INTERMEDIATE
                2. Nordic Hamstring Curl (Negative) — LEGS — ADVANCED
                3. Cossack Squat — LEGS — INTERMEDIATE
                """;
        ExerciseDraftRequestContract contract = service.parseRequestContract(prompt, null, null, null, null, null);

        List<ExerciseDraftCandidateDto> wrongCandidates = List.of(
                ExerciseDraftCandidateDto.builder()
                        .name("Straddle Front Lever").code("STRADDLE_FRONT_LEVER")
                        .category("SKILL").difficulty("ADVANCED")
                        .description("Front lever hold").instructions("Hold horizontal")
                        .targetMuscles("Lats").equipment("Pull-Up Bar").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Front Lever Raises").code("FRONT_LEVER_RAISES")
                        .category("PULL").difficulty("INTERMEDIATE")
                        .description("Raises").instructions("Raise straight arms")
                        .targetMuscles("Lats").equipment("Pull-Up Bar").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("German Hang").code("GERMAN_HANG")
                        .category("ISOMETRIC").difficulty("BEGINNER")
                        .description("Shoulder stretch hang").instructions("Hang in extension")
                        .targetMuscles("Shoulders").equipment("Rings").build()
        );

        ValidationOutcome outcome = service.validateRawCandidates(contract, wrongCandidates);
        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.validatedPayloads()).isEmpty();
        assertThat(outcome.errorSummary())
                .contains("Category mismatch")
                .contains("Missing requested exercise 'Box Pistol Squat'")
                .contains("Unexpected/unrelated exercise 'Straddle Front Lever'");
    }

    @Test
    void antiStaleGuardRejectsPriorLegsContextWhenPromptSwitchesToCore() {
        String priorLegsRecs = """
                1. Box Pistol Squat — LEGS — INTERMEDIATE
                2. Nordic Hamstring Curl (Negative) — LEGS — ADVANCED
                3. Cossack Squat — LEGS — INTERMEDIATE
                """;

        ExerciseDraftRequestContract contract = service.parseRequestContract(
                "Add 2 core exercises",
                priorLegsRecs,
                null,
                null,
                null,
                2
        );

        assertThat(contract.requiredCategory()).isEqualTo(ExerciseCategory.CORE);
        assertThat(contract.hasExplicitExercises()).isFalse();
        assertThat(contract.expectedCount()).isEqualTo(2);
    }

    @Test
    void rejectsWrongDifficultyOrInvalidEnumOrMissingRequiredFields() {
        ExerciseDraftRequestContract contract = service.parseRequestContract(
                """
                Add these 3 exercises:
                1. Box Pistol Squat — LEGS — INTERMEDIATE
                2. Nordic Hamstring Curl (Negative) — LEGS — ADVANCED
                3. Cossack Squat — LEGS — INTERMEDIATE
                """,
                null, null, null, null, null
        );

        List<ExerciseDraftCandidateDto> mismatchedDifficulty = List.of(
                ExerciseDraftCandidateDto.builder()
                        .name("Box Pistol Squat").code("BOX_PISTOL_SQUAT")
                        .category("LEGS").difficulty("INTERMEDIATE")
                        .description("Desc").instructions("Instr").targetMuscles("Quads").equipment("Box").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Nordic Hamstring Curl (Negative)").code("NORDIC_HAMSTRING_CURL_NEGATIVE")
                        .category("LEGS").difficulty("BEGINNER") // Wrong difficulty (requested ADVANCED)
                        .description("Desc").instructions("Instr").targetMuscles("Hamstrings").equipment("Strap").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Cossack Squat").code("COSSACK_SQUAT")
                        .category("LEGS").difficulty("INTERMEDIATE")
                        .description("").instructions("Instr").targetMuscles("Quads").equipment("Floor").build() // Missing description
        );

        ValidationOutcome outcome = service.validateRawCandidates(contract, mismatchedDifficulty);
        assertThat(outcome.valid()).isFalse();
        assertThat(outcome.errorSummary())
                .contains("difficulty mismatch")
                .contains("Missing description");
    }

    @Test
    void handstandPushUpSkillContractAllowsVerticalPushSkillShouldersIsometricAndItemizedKeepsValidSubset() {
        ExerciseDraftRequestContract contract = service.parseRequestContract(
                "Generate 10 exercises for Handstand Push Up",
                null, null, null, null, 10
        );

        assertThat(contract.expectedCount()).isEqualTo(10);
        assertThat(contract.skillFocus()).isEqualTo("Handstand Push-Up");
        assertThat(contract.allowedCategories()).containsExactlyInAnyOrder(
                ExerciseCategory.PUSH,
                ExerciseCategory.SKILL,
                ExerciseCategory.SHOULDERS,
                ExerciseCategory.ISOMETRIC
        );

        List<ExerciseDraftCandidateDto> mixed = List.of(
                ExerciseDraftCandidateDto.builder()
                        .name("Wall Handstand Hold").code("WALL_HANDSTAND_HOLD")
                        .category("ISOMETRIC").difficulty("BEGINNER")
                        .description("Static chest-to-wall balance hold").instructions("Hold stacked shoulders")
                        .targetMuscles("Anterior Delts, Core").equipment("Wall").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Wall Handstand Push-Up").code("WALL_HSPU")
                        .category("PUSH").difficulty("ADVANCED")
                        .description("Vertical wall press").instructions("Lower head and press up")
                        .targetMuscles("Deltoids, Triceps").equipment("Wall").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Broken Item").code("BROKEN_ITEM")
                        .category("PUSH").difficulty("BEGINNER")
                        .description("").instructions("Missing description").targetMuscles("Shoulders").equipment("Floor").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Barbell Squat").code("BARBELL_SQUAT")
                        .category("LEGS").difficulty("BEGINNER")
                        .description("Lower body squat").instructions("Squat low").targetMuscles("Quads").equipment("Barbell").build()
        );

        ExerciseDraftValidationService.ItemizedValidationOutcome itemized =
                service.validateRawCandidatesItemized(contract, mixed);
        assertThat(itemized.parsedCount()).isEqualTo(4);
        assertThat(itemized.validPayloads()).extracting(p -> p.getName())
                .containsExactly("Wall Handstand Hold", "Wall Handstand Push-Up");
        assertThat(itemized.rejectionReasons()).hasSize(2);
    }
}
