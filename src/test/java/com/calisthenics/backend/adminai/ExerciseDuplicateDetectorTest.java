package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.service.ExerciseDuplicateDetector;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExerciseDuplicateDetectorTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    private ExerciseDuplicateDetector detector;
    private ExerciseDuplicateDetector.CatalogSnapshot snapshot;

    @BeforeEach
    void setUp() {
        detector = new ExerciseDuplicateDetector(exerciseRepository);
        snapshot = new ExerciseDuplicateDetector.CatalogSnapshot();
        snapshot.register(1L, "Push-Up", "PUSH_UP", ExerciseCategory.PUSH, ExerciseTrainingType.CALISTHENICS, "Standard Push-Up, Bodyweight Push Up");
        snapshot.register(2L, "Pull-Up", "PULL_UP", ExerciseCategory.PULL, ExerciseTrainingType.CALISTHENICS, null);
        snapshot.register(3L, "Barbell Squat", "BARBELL_SQUAT", ExerciseCategory.LEGS, ExerciseTrainingType.GYM, null);
        snapshot.register(4L, "Bodyweight Squat", "BODYWEIGHT_SQUAT", ExerciseCategory.LEGS, ExerciseTrainingType.CALISTHENICS, "Air Squat");
        snapshot.register(5L, "Pistol Squat", "PISTOL_SQUAT", ExerciseCategory.LEGS, ExerciseTrainingType.CALISTHENICS, null);
        snapshot.register(6L, "Dumbbell Bicep Curl", "DUMBBELL_BICEP_CURL", ExerciseCategory.BICEPS, ExerciseTrainingType.GYM, null);
    }

    @Test
    void detectsFormattingAndPunctuationExactDuplicates() {
        var pushSpace = detector.checkCandidate(snapshot, "Push Up", null, null, ExerciseCategory.PUSH, ExerciseTrainingType.CALISTHENICS);
        var pullSpace = detector.checkCandidate(snapshot, "Pull Up", null, null, ExerciseCategory.PULL, ExerciseTrainingType.CALISTHENICS);
        var pushPlural = detector.checkCandidate(snapshot, "Push-ups", null, null, ExerciseCategory.PUSH, ExerciseTrainingType.CALISTHENICS);

        assertThat(pushSpace.duplicateOrUncertain()).isTrue();
        assertThat(pullSpace.duplicateOrUncertain()).isTrue();
        assertThat(pushPlural.duplicateOrUncertain()).isTrue();
    }

    @Test
    void detectsCanonicalAliasesAndPersistedAliases() {
        var backSquat = detector.checkCandidate(snapshot, "Barbell Back Squat", null, null, ExerciseCategory.LEGS, ExerciseTrainingType.GYM);
        var airSquat = detector.checkCandidate(snapshot, "Air Squat", null, null, ExerciseCategory.LEGS, ExerciseTrainingType.CALISTHENICS);
        var reverseAlias = detector.checkCandidate(snapshot, "Floor Press Up", null, "Push-Up, Classic Push Up", ExerciseCategory.PUSH, ExerciseTrainingType.CALISTHENICS);

        assertThat(backSquat.duplicateOrUncertain()).isTrue();
        assertThat(backSquat.matchedExerciseName()).isEqualTo("Barbell Squat");
        assertThat(airSquat.duplicateOrUncertain()).isTrue();
        assertThat(airSquat.matchedExerciseName()).isEqualTo("Bodyweight Squat");
        assertThat(reverseAlias.duplicateOrUncertain()).isTrue();
    }

    @Test
    void preservesLegitimatelyDifferentMovementsAndVariations() {
        var inclinePushUp = detector.checkCandidate(snapshot, "Incline Push-Up", null, null, ExerciseCategory.PUSH, ExerciseTrainingType.CALISTHENICS);
        var chinUp = detector.checkCandidate(snapshot, "Chin-Up", null, null, ExerciseCategory.PULL, ExerciseTrainingType.CALISTHENICS);
        var boxPistolSquat = detector.checkCandidate(snapshot, "Box Pistol Squat", null, null, ExerciseCategory.LEGS, ExerciseTrainingType.CALISTHENICS);
        var barbellCurl = detector.checkCandidate(snapshot, "Barbell Bicep Curl", null, null, ExerciseCategory.BICEPS, ExerciseTrainingType.GYM);

        assertThat(inclinePushUp.duplicateOrUncertain()).isFalse();
        assertThat(chinUp.duplicateOrUncertain()).isFalse();
        assertThat(boxPistolSquat.duplicateOrUncertain()).isFalse();
        assertThat(barbellCurl.duplicateOrUncertain()).isFalse();
    }

    @Test
    void catchesSemanticReorderingAndUncertainNearDuplicates() {
        var reorderedCurl = detector.checkCandidate(snapshot, "Bicep Curl Dumbbell", null, null, ExerciseCategory.BICEPS, ExerciseTrainingType.GYM);
        var flatPushUp = detector.checkCandidate(snapshot, "Floor Push-Up", null, null, ExerciseCategory.PUSH, ExerciseTrainingType.CALISTHENICS);

        assertThat(reorderedCurl.duplicateOrUncertain()).isTrue();
        assertThat(flatPushUp.duplicateOrUncertain()).isTrue();
    }
}
