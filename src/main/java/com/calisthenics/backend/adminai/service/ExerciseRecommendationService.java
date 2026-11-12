package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.AdminExerciseService;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.CreateAdminExerciseRequest;
import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseRecommendationService {

    private final ExerciseRepository exerciseRepository;
    private final AdminExerciseService adminExerciseService;

    private static final List<ExerciseRecommendationPayload> CANONICAL_PROGRESSION_LIBRARY = List.of(
            // Planche Progressions
            ExerciseRecommendationPayload.builder()
                    .name("Planche Lean")
                    .code("PLANCHE_LEAN")
                    .category(ExerciseCategory.ISOMETRIC)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Planche")
                    .progressionLevel("Step 1 - Foundation Lean")
                    .prerequisites("30s Hollow Body Hold, 20 Strict Push-Ups")
                    .targetMuscles("Anterior Deltoids, Scapular Protractors, Wrist Flexors, Core")
                    .equipment("Floor or Parallettes")
                    .description("Foundational straight-arm forward lean that conditions wrists, biceps tendons, and anterior deltoids for Planche.")
                    .instructions("Start in a plank with locked elbows, protract and depress the scapula, and shift shoulders forward past wrists while maintaining posterior pelvic tilt.")
                    .whyAdd("Bridges the gap between basic push-ups and Tuck Planche, reducing tendon overuse injuries for beginners.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Frog Stand")
                    .code("FROG_STAND")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Planche")
                    .progressionLevel("Step 2 - Assisted Balance & Straight-Arm Support")
                    .prerequisites("20s Planche Lean")
                    .targetMuscles("Anterior Deltoids, Wrists, Core, Scapula")
                    .equipment("Floor or Parallettes")
                    .description("Entry-level hand-balancing progression where knees rest on or outside the elbows to teach forward weight transfer.")
                    .instructions("Place hands shoulder-width apart, rest knees against upper triceps, lean forward with locked or slightly bent arms until feet lift off the floor.")
                    .whyAdd("Provides an accessible intermediate milestone before full unassisted Tuck Planche.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Tuck Planche")
                    .code("TUCK_PLANCHE")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Planche")
                    .progressionLevel("Step 3 - Primary Isometric Hold")
                    .prerequisites("25s Planche Lean, 20s Frog Stand")
                    .targetMuscles("Anterior Deltoids, Scapula, Triceps, Core")
                    .equipment("Parallettes or Floor")
                    .description("Core planche progression with knees tucked tightly to chest and hips elevated to shoulder height.")
                    .instructions("Lock elbows completely, protract scapula, lean shoulders over wrists, and lift knees into chest without bending arms.")
                    .whyAdd("Essential intermediate isometric hold for Planche mastery.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Advanced Tuck Planche")
                    .code("ADV_TUCK_PLANCHE")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Planche")
                    .progressionLevel("Step 4 - Extended Hip Leverage")
                    .prerequisites("15s Solid Tuck Planche")
                    .targetMuscles("Anterior Deltoids, Lower Back, Core, Triceps")
                    .equipment("Parallettes or Floor")
                    .description("Planche progression with flattened back and hips extended to 90 degrees.")
                    .instructions("From Tuck Planche, flatten the lumbar spine and push knees backward until hips form a 90-degree angle while keeping elbows locked.")
                    .whyAdd("Critical transition progression between Tuck Planche and Straddle Planche.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Straddle Planche")
                    .code("STRADDLE_PLANCHE")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Planche")
                    .progressionLevel("Step 5 - Full Extension Straddle")
                    .prerequisites("12s Advanced Tuck Planche")
                    .targetMuscles("Anterior Deltoids, Glutes, Core, Wrist Extensors")
                    .equipment("Parallettes or Floor")
                    .description("High-level planche hold with legs extended wide in a straddle to reduce lever length compared to Full Planche.")
                    .instructions("Lock elbows, protract scapula, and extend legs outward in a wide straddle parallel to the ground.")
                    .whyAdd("Required advanced progression before Full Planche.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Pseudo Planche Push-Up")
                    .code("PSEUDO_PLANCHE_PUSH_UP")
                    .category(ExerciseCategory.PUSH)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Planche")
                    .progressionLevel("Dynamic Bent-Arm Planche Strength")
                    .prerequisites("20s Planche Lean, 25 Push-Ups")
                    .targetMuscles("Anterior Deltoids, Pectorals, Triceps, Serratus Anterior, Core")
                    .equipment("Floor or Parallettes")
                    .description("Dynamic pushing exercise performed with hands positioned near the hips and pronounced forward shoulder lean.")
                    .instructions("Turn fingers outward or sideways, lean shoulders well past wrists, lower chest to floor keeping forward lean, and press back to protracted lockout.")
                    .whyAdd("Develops dynamic bent-arm pressing strength with specific anterior deltoid loading needed for Planche and Planche Push-ups.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Tuck Planche Push-Up")
                    .code("TUCK_PLANCHE_PUSH_UP")
                    .category(ExerciseCategory.PUSH)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Planche")
                    .progressionLevel("Advanced Dynamic Planche Push-Up")
                    .prerequisites("15s Tuck Planche, 12 Pseudo Planche Push-Ups")
                    .targetMuscles("Anterior Deltoids, Triceps, Pectorals, Core")
                    .equipment("Parallettes")
                    .description("Dynamic push-up performed entirely suspended in a Tuck Planche position without feet touching the ground.")
                    .instructions("Hold a stable Tuck Planche on parallettes, lower body under control until shoulders dip below elbows, and press back to locked-arm protraction.")
                    .whyAdd("Completes the dynamic Planche Push-up progression path for advanced athletes.")
                    .build(),

            // Front Lever Progressions
            ExerciseRecommendationPayload.builder()
                    .name("Tuck Front Lever")
                    .code("TUCK_FRONT_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Front Lever")
                    .progressionLevel("Step 1 - Entry Front Lever Hold")
                    .prerequisites("8 Strict Pull-Ups, 30s Scapular Pull Hang")
                    .targetMuscles("Lats, Scapular Depressors, Rear Deltoids, Core")
                    .equipment("Pull-Up Bar or Gymnastic Rings")
                    .description("Foundational Front Lever progression with knees tucked tightly to chest and torso horizontal.")
                    .instructions("Depress scapula, keep elbows completely straight, pull down on the bar with lats, and hold hips level with shoulders.")
                    .whyAdd("Essential entry progression so beginners and intermediates can build toward Front Lever systematically.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Advanced Tuck Front Lever")
                    .code("ADV_TUCK_FRONT_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Front Lever")
                    .progressionLevel("Step 2 - Flat-Back Tuck Hold")
                    .prerequisites("15s Tuck Front Lever")
                    .targetMuscles("Lats, Rear Deltoids, Core, Grip")
                    .equipment("Pull-Up Bar or Gymnastic Rings")
                    .description("Intermediate Front Lever hold with flattened back and knees extended to 90 degrees from hips.")
                    .instructions("Maintain straight arms and depressed scapula while extending knees away from chest until thighs are vertical.")
                    .whyAdd("Bridges the large leverage jump between Tuck Front Lever and Straddle/Full Front Lever.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("One-Leg Front Lever")
                    .code("ONE_LEG_FRONT_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Front Lever")
                    .progressionLevel("Step 3 - Asymmetric Leverage Hold")
                    .prerequisites("12s Advanced Tuck Front Lever")
                    .targetMuscles("Lats, Rear Deltoids, Glutes, Abdominals")
                    .equipment("Pull-Up Bar or Gymnastic Rings")
                    .description("Advanced progression with one leg fully extended inline with torso and the other leg tucked.")
                    .instructions("Hold a flat horizontal torso with one leg fully straight and alternate legs equally across sets.")
                    .whyAdd("Allows smooth progressive overload before attempting Straddle or Full Front Lever.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Straddle Front Lever")
                    .code("STRADDLE_FRONT_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Front Lever")
                    .progressionLevel("Step 4 - Straddle Hold")
                    .prerequisites("10s One-Leg Front Lever per side")
                    .targetMuscles("Lats, Rear Deltoids, Core, Glutes, Forearms")
                    .equipment("Pull-Up Bar or Gymnastic Rings")
                    .description("Penultimate Front Lever progression with both legs fully straight and abducted wide.")
                    .instructions("Keep arms locked, engage glutes and lats, and hold body completely horizontal with legs straddled wide.")
                    .whyAdd("Prepares athletes for the full horizontal lever by reducing moment arm by ~15-20%.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Front Lever Raises")
                    .code("FRONT_LEVER_RAISES")
                    .category(ExerciseCategory.PULL)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Front Lever")
                    .progressionLevel("Dynamic Straight-Arm Pulling")
                    .prerequisites("12s Tuck Front Lever, 10 Pull-Ups")
                    .targetMuscles("Lats, Teres Major, Posterior Deltoids, Core")
                    .equipment("Pull-Up Bar or Gymnastic Rings")
                    .description("Dynamic straight-arm pulling movement raising the body from dead hang through the horizontal Front Lever plane.")
                    .instructions("With locked elbows and depressed shoulders, pull body from active hang up to horizontal or inverted hang and lower with a 3-second eccentric.")
                    .whyAdd("Builds dynamic straight-arm lat strength required to break Front Lever plateaus.")
                    .build(),

            // Back Lever Progressions
            ExerciseRecommendationPayload.builder()
                    .name("German Hang")
                    .code("GERMAN_HANG")
                    .category(ExerciseCategory.ISOMETRIC)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Back Lever")
                    .progressionLevel("Step 1 - Shoulder Extension Conditioning")
                    .prerequisites("30s Dead Hang")
                    .targetMuscles("Anterior Deltoids, Biceps Tendons, Pectorals, Scapula")
                    .equipment("Gymnastic Rings or Pull-Up Bar")
                    .description("Loaded shoulder extension stretch hang that prepares connective tissue for Back Lever.")
                    .instructions("Rotate through an inverted hang into a bottom hang with arms behind the back, keeping elbows locked and shoulders gently protracted.")
                    .whyAdd("Crucial injury-prevention prerequisite for Back Lever and ring work.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Skin the Cat")
                    .code("SKIN_THE_CAT")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Back Lever")
                    .progressionLevel("Step 2 - Dynamic 360 Rotation")
                    .prerequisites("15s Comfortable German Hang")
                    .targetMuscles("Shoulders, Lats, Core, Hip Flexors, Biceps")
                    .equipment("Gymnastic Rings or Pull-Up Bar")
                    .description("Full-range straight-arm rotation from dead hang into German Hang and back.")
                    .instructions("Keep arms straight, tuck knees through arms into German Hang, pause 2 seconds, and pull back to starting hang.")
                    .whyAdd("Introduces active straight-arm shoulder extension strength before static Back Lever holds.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Tuck Back Lever")
                    .code("TUCK_BACK_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Back Lever")
                    .progressionLevel("Step 3 - First Horizontal Back Lever Hold")
                    .prerequisites("5 Controlled Skin the Cats")
                    .targetMuscles("Anterior Deltoids, Biceps, Pectorals, Lower Back")
                    .equipment("Gymnastic Rings or Pull-Up Bar")
                    .description("Horizontal face-down suspension hold with knees tucked to chest.")
                    .instructions("Lower from inverted tuck hang until torso is parallel to floor, protract scapula, lock elbows, and hold.")
                    .whyAdd("Establishes Back Lever skill coverage in the exercise library.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Straddle Back Lever")
                    .code("STRADDLE_BACK_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Back Lever")
                    .progressionLevel("Step 4 - Extended Straddle Hold")
                    .prerequisites("15s Tuck Back Lever")
                    .targetMuscles("Anterior Deltoids, Biceps, Erector Spinae, Glutes")
                    .equipment("Gymnastic Rings or Pull-Up Bar")
                    .description("Advanced horizontal Back Lever hold with legs extended in a wide straddle.")
                    .instructions("Maintain locked elbows and neutral neck while extending legs wide parallel to the ground.")
                    .whyAdd("Provides advanced progression toward Full Back Lever.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Full Back Lever")
                    .code("FULL_BACK_LEVER")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Back Lever")
                    .progressionLevel("Step 5 - Full Horizontal Back Lever")
                    .prerequisites("12s Straddle Back Lever")
                    .targetMuscles("Anterior Deltoids, Biceps, Pectorals, Glutes, Core")
                    .equipment("Gymnastic Rings or Pull-Up Bar")
                    .description("Complete horizontal face-down hold with straight body line from shoulders to toes.")
                    .instructions("Lock elbows, protract shoulders slightly, squeeze legs together, and hold body parallel to ground.")
                    .whyAdd("Completes the Back Lever progression family in the platform catalog.")
                    .build(),

            // Handstand Progressions
            ExerciseRecommendationPayload.builder()
                    .name("Chest-to-Wall Handstand")
                    .code("CHEST_TO_WALL_HANDSTAND")
                    .category(ExerciseCategory.ISOMETRIC)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Handstand")
                    .progressionLevel("Step 1 - Alignment & Endurance Foundation")
                    .prerequisites("45s Plank, 10 Pike Push-Ups")
                    .targetMuscles("Deltoids, Upper Traps, Core, Forearms")
                    .equipment("Wall")
                    .description("Wall-assisted handstand facing the wall to enforce strict hollow-body line and 180-degree shoulder elevation.")
                    .instructions("Walk feet up the wall until hands are 10-15cm from baseboard, push tall through shoulders, tuck ribs, and squeeze glutes.")
                    .whyAdd("Essential beginner drill for teaching proper stacked handstand alignment before freestanding work.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Wall Handstand Push-Up")
                    .code("WALL_HANDSTAND_PUSH_UP")
                    .category(ExerciseCategory.PUSH)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Handstand")
                    .progressionLevel("Step 3 - Vertical Overhead Pressing")
                    .prerequisites("60s Wall Handstand, 12 Pike Push-Ups")
                    .targetMuscles("Anterior & Lateral Deltoids, Triceps, Upper Chest, Core")
                    .equipment("Wall, Optional Parallettes")
                    .description("Full vertical pressing movement supported against a wall for maximal shoulder and triceps hypertrophy and strength.")
                    .instructions("From a wall handstand, lower head forward into a tripod position in front of hands, then press vertically back to locked-arm elevation.")
                    .whyAdd("Fills the gap between Pike Push-Up and elite Freestanding Handstand Push-Ups.")
                    .build(),

            // Muscle-Up Progressions
            ExerciseRecommendationPayload.builder()
                    .name("High Pull-Up")
                    .code("HIGH_PULL_UP")
                    .category(ExerciseCategory.PULL)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Muscle-up")
                    .progressionLevel("Step 1 - Explosive Pulling Power")
                    .prerequisites("10 Strict Pull-Ups")
                    .targetMuscles("Lats, Upper Back, Posterior Deltoids, Forearms")
                    .equipment("Pull-Up Bar")
                    .description("Explosive pull-up driving the bar down to the lower chest or upper abdominals.")
                    .instructions("Initiate with a slight hollow swing or dead hang and pull explosively so the lower sternum clears the bar.")
                    .whyAdd("Develops the specific pulling height and acceleration required to clear the Muscle-Up transition.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Straight Bar Dip")
                    .code("STRAIGHT_BAR_DIP")
                    .category(ExerciseCategory.PUSH)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Muscle-up")
                    .progressionLevel("Step 2 - Top Support & Lockout")
                    .prerequisites("12 Parallel Bar Dips")
                    .targetMuscles("Triceps, Lower Pectorals, Anterior Deltoids, Core")
                    .equipment("Pull-Up Bar")
                    .description("Dip performed on top of a single horizontal bar, replicating the second half of a bar Muscle-Up.")
                    .instructions("From top support on a pull-up bar, lean torso forward as you lower chest to the bar, then press to full lockout.")
                    .whyAdd("Prepares athletes for the top lockout of a bar Muscle-Up.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Ring Muscle-Up")
                    .code("RING_MUSCLE_UP")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Muscle-up")
                    .progressionLevel("Step 4 - Strict False-Grip Ring Transition")
                    .prerequisites("10 Ring Pull-Ups, 12 Ring Dips, 20s False Grip Hang")
                    .targetMuscles("Lats, Biceps, Triceps, Pectorals, Forearms")
                    .equipment("Gymnastic Rings")
                    .description("Strict gymnastic ring muscle-up utilizing a false grip and deep ring dip transition.")
                    .instructions("Maintain false grip, pull rings to sternum, trace ribs into a deep transition, and press out to rings-turned-out support.")
                    .whyAdd("Expands Muscle-Up training to gymnastic rings for strict upper-body strength.")
                    .build(),

            // Human Flag Progressions
            ExerciseRecommendationPayload.builder()
                    .name("Vertical Flag Hold")
                    .code("VERTICAL_FLAG_HOLD")
                    .category(ExerciseCategory.ISOMETRIC)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Human Flag")
                    .progressionLevel("Step 1 - Push/Pull Lateral Support")
                    .prerequisites("10 Pull-Ups, 15 Dips, 45s Side Plank")
                    .targetMuscles("Obliques, Deltoids, Lats, Triceps")
                    .equipment("Vertical Pole or Stall Bars")
                    .description("Entry Human Flag progression holding the body elevated at a 45-to-60 degree angle on a vertical pole.")
                    .instructions("Bottom arm pushes hard with locked elbow while top arm pulls; lift legs off floor into an angled hold.")
                    .whyAdd("Introduces the lateral push-pull mechanics required for Human Flag.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Tuck Human Flag")
                    .code("TUCK_HUMAN_FLAG")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Human Flag")
                    .progressionLevel("Step 2 - Horizontal Tuck Flag")
                    .prerequisites("20s Vertical Flag Hold")
                    .targetMuscles("Obliques, Quadratus Lumborum, Lats, Deltoids")
                    .equipment("Vertical Pole or Stall Bars")
                    .description("Horizontal Human Flag hold with knees tucked into the chest to shorten the lever.")
                    .instructions("Lock both arms, depress both shoulders, and hold torso horizontal with knees tucked.")
                    .whyAdd("Key intermediate progression for Human Flag skill development.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Full Human Flag")
                    .code("FULL_HUMAN_FLAG")
                    .category(ExerciseCategory.SKILL)
                    .difficulty(DifficultyLevel.ELITE)
                    .targetSkill("Human Flag")
                    .progressionLevel("Step 3 - Full Horizontal Lateral Hold")
                    .prerequisites("12s Tuck Human Flag")
                    .targetMuscles("Obliques, Lats, Deltoids, Triceps, Glutes")
                    .equipment("Vertical Pole or Stall Bars")
                    .description("Signature lateral calisthenics hold with the entire body suspended horizontally perpendicular to a vertical pole.")
                    .instructions("Push through bottom arm, pull with top arm, lock both elbows, and extend body into a straight horizontal line.")
                    .whyAdd("Adds elite lateral isometric skill coverage to the platform.")
                    .build(),

            // Core & Lower Body Progressions
            ExerciseRecommendationPayload.builder()
                    .name("Dragon Flag")
                    .code("DRAGON_FLAG")
                    .category(ExerciseCategory.CORE)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Core & Compression")
                    .progressionLevel("Advanced Anti-Extension Core")
                    .prerequisites("45s Hollow Body Hold, 15 Hanging Leg Raises")
                    .targetMuscles("Rectus Abdominis, Obliques, Lats, Glutes")
                    .equipment("Bench or Vertical Pole")
                    .description("Full-body straight-lever core exercise supported on the upper shoulders.")
                    .instructions("Grip bench behind head, keep entire body rigid from shoulders to toes, lower toward horizontal without piking hips, and raise back up.")
                    .whyAdd("Provides an advanced core progression that directly transfers to Front Lever and Planche body tension.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Pistol Squat")
                    .code("PISTOL_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Single-Leg Strength")
                    .progressionLevel("Unilateral Lower Body Mastery")
                    .prerequisites("25 Bodyweight Squats")
                    .targetMuscles("Quadriceps, Glutes, Hamstrings, Hip Flexors, Calves")
                    .equipment("Floor")
                    .description("Full-depth single-leg squat with the non-working leg extended forward off the floor.")
                    .instructions("Extend one leg straight in front, descend under control to full depth on the standing leg, and drive through heel to stand.")
                    .whyAdd("Addresses lower-body progression gaps beyond basic Bodyweight Squats.")
                    .build()
    );

    public List<String> getSupportedSkillFamilies() {
        return List.of("Planche", "Front Lever", "Back Lever", "Handstand", "Muscle-up", "Human Flag");
    }

    @Transactional(readOnly = true)
    public List<ExerciseRecommendationPayload> analyzeMissingExercises(String targetSkillFilter, DifficultyLevel difficultyFilter) {
        List<Exercise> existingCatalog = exerciseRepository.findAll();
        Set<String> existingNormalizedNames = existingCatalog.stream()
                .map(e -> normalizeIdentifier(e.getName()))
                .collect(Collectors.toSet());
        Set<String> existingNormalizedCodes = existingCatalog.stream()
                .map(e -> normalizeIdentifier(e.getCode()))
                .collect(Collectors.toSet());

        return CANONICAL_PROGRESSION_LIBRARY.stream()
                .filter(candidate -> !existingNormalizedNames.contains(normalizeIdentifier(candidate.getName())))
                .filter(candidate -> !existingNormalizedCodes.contains(normalizeIdentifier(candidate.getCode())))
                .filter(candidate -> {
                    if (targetSkillFilter == null || targetSkillFilter.isBlank()) {
                        return true;
                    }
                    String filterLower = targetSkillFilter.trim().toLowerCase();
                    return candidate.getTargetSkill().toLowerCase().contains(filterLower)
                            || candidate.getName().toLowerCase().contains(filterLower)
                            || candidate.getCategory().name().toLowerCase().contains(filterLower);
                })
                .filter(candidate -> difficultyFilter == null || candidate.getDifficulty() == difficultyFilter)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getMissingProgressionNamesForSkill(String skillName, List<Exercise> existingCatalog) {
        Set<String> existingNormalizedNames = existingCatalog.stream()
                .map(e -> normalizeIdentifier(e.getName()))
                .collect(Collectors.toSet());
        Set<String> existingNormalizedCodes = existingCatalog.stream()
                .map(e -> normalizeIdentifier(e.getCode()))
                .collect(Collectors.toSet());

        return CANONICAL_PROGRESSION_LIBRARY.stream()
                .filter(c -> c.getTargetSkill().equalsIgnoreCase(skillName))
                .filter(c -> !existingNormalizedNames.contains(normalizeIdentifier(c.getName()))
                        && !existingNormalizedCodes.contains(normalizeIdentifier(c.getCode())))
                .map(ExerciseRecommendationPayload::getName)
                .toList();
    }

    @Transactional
    public AdminExerciseResponse createExerciseFromRecommendation(
            ExerciseRecommendationPayload payload,
            boolean publishImmediately
    ) {
        String name = payload.getName() != null ? payload.getName().trim() : "Recommended Exercise";
        Optional<Exercise> existingByName = exerciseRepository.findByNameIgnoreCase(name);
        if (existingByName.isPresent()) {
            return adminExerciseService.getExercise(existingByName.get().getId());
        }

        CreateAdminExerciseRequest createReq = new CreateAdminExerciseRequest(
                name,
                payload.getCode(),
                payload.getCategory() != null ? payload.getCategory() : ExerciseCategory.SKILL,
                payload.getDifficulty() != null ? payload.getDifficulty() : DifficultyLevel.INTERMEDIATE,
                payload.getDescription(),
                payload.getTargetMuscles(),
                payload.getInstructions(),
                null,
                null,
                payload.getEquipment(),
                publishImmediately,
                List.of()
        );
        return adminExerciseService.createExercise(createReq);
    }

    public static String normalizeIdentifier(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "");
        if (normalized.endsWith("ups")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
