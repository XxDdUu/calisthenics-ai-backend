package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.AdminExerciseService;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.CreateAdminExerciseRequest;
import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftBatchResultResponse;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiRecommendation;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseRecommendationService {

    private static final String DRAFT_BATCH_PREFIX = "AI_DRAFT_BATCH:";

    private final ExerciseRepository exerciseRepository;
    private final AdminExerciseService adminExerciseService;
    private final AdminAiRecommendationRepository recommendationRepository;
    private final AdminAiConversationRepository conversationRepository;
    private final ExerciseDraftValidationService validationService;
    private final ObjectMapper objectMapper;

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
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Bulgarian Split Squat")
                    .code("BULGARIAN_SPLIT_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Single-Leg Strength")
                    .progressionLevel("Split-Stance Strength & Balance")
                    .prerequisites("15 Bodyweight Squats, 10 Lunges per leg")
                    .targetMuscles("Quadriceps, Glutes, Hip Stabilizers")
                    .equipment("Bench or Low Box")
                    .description("Rear-foot-elevated unilateral squat building single-leg hypertrophy, balance, and knee stability.")
                    .instructions("Place top of rear foot on bench behind you, lower back knee vertically under control until front thigh is parallel or below, and drive up through front heel.")
                    .whyAdd("Bridges bilateral squats and advanced unilateral work like Pistol Squats.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Cossack Squat")
                    .code("COSSACK_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Lower Body Mobility & Strength")
                    .progressionLevel("Lateral Squat & Adductor Strength")
                    .prerequisites("20 Deep Bodyweight Squats")
                    .targetMuscles("Quadriceps, Adductors, Glutes, Hamstrings, Ankles")
                    .equipment("Floor")
                    .description("Deep lateral single-leg squat shifting weight fully over one heel while the opposite leg stays straight.")
                    .instructions("Take a wide stance, sit hips back and down over one leg keeping the working heel flat and trailing toes pointed upward, then switch sides.")
                    .whyAdd("Develops frontal-plane leg strength alongside hip and ankle mobility.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Assisted Pistol Squat")
                    .code("ASSISTED_PISTOL_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Single-Leg Strength")
                    .progressionLevel("Step 2 - Counterbalance or Support Pistol")
                    .prerequisites("12 Bulgarian Split Squats per side")
                    .targetMuscles("Quadriceps, Glutes, Core, Ankle Dorsiflexors")
                    .equipment("Doorframe, Pole, or Suspension Straps")
                    .description("Support-assisted single-leg pistol squat allowing strict control through the bottom sticking point.")
                    .instructions("Lightly hold a vertical support in front of chest, extend free leg forward, descend slowly to full depth, and stand using minimal arm assistance.")
                    .whyAdd("Teaches full pistol squat mechanics before unassisted reps.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Shrimp Squat")
                    .code("SHRIMP_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Single-Leg Strength")
                    .progressionLevel("Quad-Dominant Unilateral Squat")
                    .prerequisites("12 Bulgarian Split Squats per leg")
                    .targetMuscles("Quadriceps, Glutes, Balance Stabilizers")
                    .equipment("Floor")
                    .description("Single-leg squat with the rear foot tucked behind the glutes rather than extended forward.")
                    .instructions("Hold non-working ankle behind your glutes or hover it behind you, hinge slightly forward, lower back knee gently to floor, and press back up.")
                    .whyAdd("Builds heavy single-leg quadriceps strength with less hamstring flexibility demand than Pistol Squats.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Nordic Hamstring Curl")
                    .code("NORDIC_HAMSTRING_CURL")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Posterior Chain Strength")
                    .progressionLevel("Eccentric & Concentric Knee Flexion")
                    .prerequisites("15 Glute Bridges, 12 Single-Leg Glute Bridges")
                    .targetMuscles("Hamstrings, Glutes, Calves, Core")
                    .equipment("Partner, Strap, or Anchored Bar")
                    .description("High-tension bodyweight knee-flexion movement focusing on eccentric hamstring strength and resilience.")
                    .instructions("Kneel with ankles anchored securely, maintain a straight torso-to-thigh line, lower chest toward floor as slowly as possible using hamstrings, and push lightly off hands to reset.")
                    .whyAdd("Provides unmatched bodyweight posterior-chain overload and hamstring injury protection.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Single-Leg Calf Raise")
                    .code("SINGLE_LEG_CALF_RAISE")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Lower Body Strength")
                    .progressionLevel("Ankle & Calf Conditioning")
                    .prerequisites("20 Two-Leg Calf Raises")
                    .targetMuscles("Gastrocnemius, Soleus, Ankle Stabilizers")
                    .equipment("Step or Floor")
                    .description("Unilateral standing calf raise through a full ankle plantar-to-dorsiflexion range of motion.")
                    .instructions("Stand on the edge of a step on one foot, lower heel slowly to a deep stretch for 2 seconds, pause, and drive smoothly onto the ball of the foot.")
                    .whyAdd("Strengthens calves and Achilles tendons for pistol squats and plyometrics.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Sissy Squat")
                    .code("SISSY_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Quadriceps Strength")
                    .progressionLevel("Isolated Knee Extension Strength")
                    .prerequisites("25 Bodyweight Squats")
                    .targetMuscles("Quadriceps, Rectus Femoris, Core, Hip Flexors")
                    .equipment("Floor or Support Pole")
                    .description("Lean-back knee-dominant bodyweight squat isolating the quadriceps through deep knee flexion.")
                    .instructions("Stand tall, rise onto balls of feet, drive knees forward while leaning torso straight back in one line with thighs, pause low, and extend knees strongly.")
                    .whyAdd("Delivers deep bodyweight quadriceps stretch and tendon strength without external weights.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Single-Leg Glute Bridge")
                    .code("SINGLE_LEG_GLUTE_BRIDGE")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Posterior Chain Strength")
                    .progressionLevel("Unilateral Hip Extension")
                    .prerequisites("20 Two-Leg Glute Bridges")
                    .targetMuscles("Gluteus Maximus, Hamstrings, Core")
                    .equipment("Floor")
                    .description("Unilateral floor hip thrust isolating glute drive and pelvic stability.")
                    .instructions("Lie supine with one foot planted flat and opposite leg extended upward, squeeze glutes to drive hips to full extension, pause 1 second, and lower smoothly.")
                    .whyAdd("Builds foundational posterior chain activation alongside squat patterns.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Explosive Jump Squat")
                    .code("EXPLOSIVE_JUMP_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Lower Body Power")
                    .progressionLevel("Plyometric Squat Power")
                    .prerequisites("20 Strict Bodyweight Squats")
                    .targetMuscles("Quadriceps, Glutes, Calves, Hip Extensors")
                    .equipment("Floor")
                    .description("Dynamic triple-extension squat jump developing explosive lower-body power and landing mechanics.")
                    .instructions("Descend to a quarter-or-half squat, explode vertically off the ground, and absorb landing softly with aligned knees.")
                    .whyAdd("Adds athletic power training to bodyweight leg programs.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Reverse Nordic Curl")
                    .code("REVERSE_NORDIC_CURL")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Quadriceps Mobility & Strength")
                    .progressionLevel("Eccentric Quad Extension")
                    .prerequisites("20 Bodyweight Squats")
                    .targetMuscles("Rectus Femoris, Vastus Lateralis, Hip Flexors, Core")
                    .equipment("Mat or Soft Floor")
                    .description("Kneeling eccentric quadriceps exercise lowering the rigid torso backward toward the heels.")
                    .instructions("Kneel tall with glutes and abs braced, lean straight backward from the knees as far as controlled without arching lower back, then pull back upright.")
                    .whyAdd("Strengthens end-range knee flexion and rectus femoris flexibility simultaneously.")
                    .build(),

            // Additional Core / Push / Pull exercises
            ExerciseRecommendationPayload.builder()
                    .name("Hollow Body Hold")
                    .code("HOLLOW_BODY_HOLD")
                    .category(ExerciseCategory.CORE)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Core & Compression")
                    .progressionLevel("Fundamental Anti-Extension Line")
                    .prerequisites("30s Plank")
                    .targetMuscles("Rectus Abdominis, Transverse Abdominis, Hip Flexors")
                    .equipment("Floor")
                    .description("Gymnastics foundational core hold maintaining posterior pelvic tilt with arms and legs extended.")
                    .instructions("Press lower back firmly into the floor, lift shoulder blades and straight legs slightly off the ground, and breathe steadily without losing lumbar contact.")
                    .whyAdd("Foundation for Handstand, Planche, Front Lever, and Hanging Leg Raises.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Hanging Leg Raise")
                    .code("HANGING_LEG_RAISE")
                    .category(ExerciseCategory.CORE)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Core & Compression")
                    .progressionLevel("Strict Bar Compression")
                    .prerequisites("30s Dead Hang, 12 Knee Raises")
                    .targetMuscles("Lower Abdominals, Hip Flexors, Lats, Grip")
                    .equipment("Pull-Up Bar")
                    .description("Strict straight-leg raise from dead hang lifting toes all the way to the bar without swinging.")
                    .instructions("Start from a still dead hang, depress shoulders slightly, raise straight legs smoothly until shins tap the bar, and lower under control.")
                    .whyAdd("Develops active pike compression and grip endurance.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Archer Push-Up")
                    .code("ARCHER_PUSH_UP")
                    .category(ExerciseCategory.PUSH)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("One-Arm Push-Up Progression")
                    .progressionLevel("Unilateral Horizontal Pressing")
                    .prerequisites("25 Strict Push-Ups, 10 Diamond Push-Ups")
                    .targetMuscles("Pectorals, Anterior Deltoids, Triceps, Core")
                    .equipment("Floor")
                    .description("Wide-stance pushing progression shifting weight heavily onto one pressing arm while the assisting arm stays straight.")
                    .instructions("Adopt a wide hand stance with fingers angled out, lower chest toward one hand while extending opposite arm straight sideways, press back to center, and alternate.")
                    .whyAdd("Builds single-arm pressing strength without weights.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Diamond Push-Up")
                    .code("DIAMOND_PUSH_UP")
                    .category(ExerciseCategory.PUSH)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Upper Body Pushing")
                    .progressionLevel("Close-Grip Pressing")
                    .prerequisites("12 Standard Push-Ups")
                    .targetMuscles("Triceps, Inner Chest, Anterior Deltoids, Core")
                    .equipment("Floor")
                    .description("Close-grip push-up variation emphasizing triceps lockout and shoulder stability.")
                    .instructions("Place hands together beneath sternum forming a triangle with thumbs and index fingers, keep elbows tracking cleanly back, lower chest to hands, and press up.")
                    .whyAdd("Essential hypertrophy and lockout progression after basic push-ups.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Australian Pull-Up")
                    .code("AUSTRALIAN_PULL_UP")
                    .category(ExerciseCategory.PULL)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .targetSkill("Upper Body Pulling")
                    .progressionLevel("Horizontal Row Foundation")
                    .prerequisites("20s Dead Hang")
                    .targetMuscles("Mid-Back, Lats, Rhomboids, Posterior Deltoids, Biceps")
                    .equipment("Low Bar or Gymnastic Rings")
                    .description("Horizontal bodyweight row scalable by body angle for beginners building pulling volume and scapular control.")
                    .instructions("Keep body straight in a plank beneath a chest-high bar, pull chest cleanly to bar squeezing shoulder blades together, pause, and lower to straight arms.")
                    .whyAdd("Key beginner horizontal pull before strict vertical pull-ups.")
                    .build(),
            ExerciseRecommendationPayload.builder()
                    .name("Archer Pull-Up")
                    .code("ARCHER_PULL_UP")
                    .category(ExerciseCategory.PULL)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("One-Arm Pull-Up Progression")
                    .progressionLevel("Unilateral Vertical Pulling")
                    .prerequisites("15 Strict Pull-Ups")
                    .targetMuscles("Lats, Biceps, Brachialis, Forearms, Rear Deltoids")
                    .equipment("Pull-Up Bar")
                    .description("Wide-grip pulling movement driving one shoulder toward the hand while keeping the assisting arm relatively straight.")
                    .instructions("Grip wide on bar, pull chest dynamically toward one hand while extending opposite arm toward straight, lower slowly, and alternate sides.")
                    .whyAdd("Essential bridge between weighted/high-rep pull-ups and One-Arm Pull-Ups.")
                    .build()
    );

    private static final Map<String, ExerciseRecommendationPayload> EXPLICIT_EXERCISE_TEMPLATES = Map.ofEntries(
            Map.entry("boxpistolsquat", ExerciseRecommendationPayload.builder()
                    .name("Box Pistol Squat")
                    .code("BOX_PISTOL_SQUAT")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.INTERMEDIATE)
                    .targetSkill("Single-Leg Strength")
                    .progressionLevel("Step 2 - Box-Controlled Pistol Progression")
                    .prerequisites("12 Bulgarian Split Squats per leg, 20 Bodyweight Squats")
                    .targetMuscles("Quadriceps, Glutes, Hamstrings, Hip Stabilizers, Core")
                    .equipment("Plyo Box or Bench")
                    .description("Controlled single-leg pistol squat lowering the hips to a box or bench before driving smoothly back up to full standing lockout.")
                    .instructions("Stand in front of a sturdy knee-height box, extend one leg straight forward, sit hips back and down under a 2-second eccentric until glutes lightly tap the box without relaxing, and press through the planted heel to stand.")
                    .whyAdd("Bridges split squats and full floor pistol squats by standardizing depth and bottom-position control.")
                    .build()),
            Map.entry("nordichamstringcurlnegative", ExerciseRecommendationPayload.builder()
                    .name("Nordic Hamstring Curl (Negative)")
                    .code("NORDIC_HAMSTRING_CURL_NEGATIVE")
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.ADVANCED)
                    .targetSkill("Posterior Chain Strength")
                    .progressionLevel("Eccentric Knee Flexion Progression")
                    .prerequisites("12 Single-Leg Glute Bridges, 15 Sliding Leg Curls")
                    .targetMuscles("Hamstrings, Glutes, Gastrocnemius, Core")
                    .equipment("Partner, Strap, or Anchored Bar")
                    .description("High-tension eccentric Nordic hamstring curl focusing on a maximally slow, controlled lowering phase with neutral hips.")
                    .instructions("Kneel upright on a mat with ankles anchored securely, brace abs and squeeze glutes to keep hips straight, lower torso forward toward the floor over a 4-to-5 second negative using hamstring tension, catch softly with hands, and press lightly back to start.")
                    .whyAdd("Builds extreme eccentric hamstring resilience and posterior-chain strength before full concentric Nordic curls.")
                    .build())
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
                    String filterLower = targetSkillFilter.trim().toLowerCase(Locale.US);
                    ExerciseCategory categoryHint = ExerciseDraftValidationService.detectCategoryFromText(filterLower);
                    return (candidate.getTargetSkill() != null && candidate.getTargetSkill().toLowerCase(Locale.US).contains(filterLower))
                            || candidate.getName().toLowerCase(Locale.US).contains(filterLower)
                            || candidate.getCategory().name().toLowerCase(Locale.US).contains(filterLower)
                            || (categoryHint != null && candidate.getCategory() == categoryHint);
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
                List.of(),
                publishImmediately ? ExerciseStatus.PUBLISHED : ExerciseStatus.DRAFT,
                ExerciseOrigin.AI_RECOMMENDED
        );
        return adminExerciseService.createExercise(createReq);
    }

    @Transactional
    public ExerciseDraftBatchResultResponse createExerciseDrafts(
            User admin,
            Long conversationId,
            String userPrompt,
            ExerciseCategory explicitCategory,
            String skillFilter,
            DifficultyLevel difficultyFilter,
            Integer requestedCount
    ) {
        return createExerciseDrafts(
                admin,
                conversationId,
                userPrompt,
                null,
                explicitCategory,
                skillFilter,
                difficultyFilter,
                requestedCount,
                null
        );
    }

    @Transactional
    public ExerciseDraftBatchResultResponse createExerciseDrafts(
            User admin,
            Long conversationId,
            String userPrompt,
            String recentRecommendationContext,
            ExerciseCategory explicitCategory,
            String skillFilter,
            DifficultyLevel difficultyFilter,
            Integer requestedCount,
            List<ExerciseDraftCandidateDto> providedCandidates
    ) {
        ExerciseDraftValidationService.ExerciseDraftRequestContract contract =
                validationService.parseRequestContract(
                        userPrompt,
                        recentRecommendationContext,
                        explicitCategory,
                        skillFilter,
                        difficultyFilter,
                        requestedCount
                );

        ExerciseCategory targetCategory = contract.requiredCategory();
        String categoryLabel = targetCategory != null
                ? targetCategory.name()
                : (skillFilter != null && !skillFilter.isBlank() ? skillFilter.trim() : "UNSPECIFIED");
        int targetCount = contract.expectedCount();

        if (!contract.hasExplicitExercises()
                && targetCategory == null
                && (skillFilter == null || skillFilter.isBlank())
                && (providedCandidates == null || providedCandidates.isEmpty())) {
            return validationFailure(
                    categoryLabel,
                    targetCount,
                    List.of("Missing target exercise category or explicit exercise names in the request.")
            );
        }

        String signature = buildDraftBatchSignature(userPrompt, contract);
        AdminAiConversation conversation = conversationId != null
                ? conversationRepository.findById(conversationId).orElse(null)
                : null;

        // Idempotent retry protection within the same conversation or within 60 seconds for the same admin
        List<AdminExerciseResponse> priorRetryMatches = findExistingRetryBatchDrafts(admin, conversationId, signature);
        if (!priorRetryMatches.isEmpty()) {
            return ExerciseDraftBatchResultResponse.builder()
                    .success(true)
                    .category(categoryLabel)
                    .requestedCount(targetCount)
                    .createdCount(0)
                    .createdExercises(List.of())
                    .existingDrafts(priorRetryMatches)
                    .message("Prevented duplicate creation on retry; returning " + priorRetryMatches.size()
                            + " already saved draft exercise(s).")
                    .build();
        }

        List<Exercise> existingCatalog = exerciseRepository.findAll();
        Map<String, Exercise> existingByNormName = new LinkedHashMap<>();
        Set<String> existingCodes = new HashSet<>();
        for (Exercise existing : existingCatalog) {
            existingByNormName.putIfAbsent(ExerciseDraftValidationService.normalizeExactName(existing.getName()), existing);
            if (existing.getCode() != null) {
                existingCodes.add(existing.getCode().trim().toUpperCase(Locale.US));
            }
        }

        List<ExerciseRecommendationPayload> validatedPayloads;

        if (providedCandidates != null && !providedCandidates.isEmpty()) {
            // Strictly validate AI-supplied or tool-supplied exercise drafts BEFORE any DB write
            ExerciseDraftValidationService.ValidationOutcome outcome =
                    validationService.validateRawCandidates(contract, providedCandidates);
            if (!outcome.valid()) {
                return validationFailure(categoryLabel, targetCount, outcome.errors());
            }
            validatedPayloads = outcome.validatedPayloads();
        } else if (contract.hasExplicitExercises()) {
            // Prioritize exact requested exercise specifications over semantic/canonical substitution
            List<ExerciseRecommendationPayload> resolvedExplicit = new ArrayList<>();
            for (ExerciseDraftValidationService.RequestedExerciseSpec spec : contract.explicitExercises()) {
                resolvedExplicit.add(resolveExplicitExercisePayload(spec, targetCategory));
            }
            ExerciseDraftValidationService.ValidationOutcome outcome =
                    validationService.validatePayloads(contract, resolvedExplicit);
            if (!outcome.valid()) {
                return validationFailure(categoryLabel, targetCount, outcome.errors());
            }
            validatedPayloads = outcome.validatedPayloads();
        } else {
            // Canonical recommendation pool strictly filtered by validated category and uniform difficulty
            List<ExerciseRecommendationPayload> matchingPool = CANONICAL_PROGRESSION_LIBRARY.stream()
                    .filter(c -> !existingByNormName.containsKey(ExerciseDraftValidationService.normalizeExactName(c.getName()))
                            && !existingCodes.contains(ExerciseDraftValidationService.normalizeSlug(c.getCode())))
                    .filter(c -> matchesCategoryOrSkill(c, targetCategory, skillFilter))
                    .filter(c -> contract.requiredDifficulty() == null || c.getDifficulty() == contract.requiredDifficulty())
                    .toList();

            List<ExerciseRecommendationPayload> selected = matchingPool.stream()
                    .limit(targetCount)
                    .toList();

            if (selected.isEmpty()) {
                List<AdminExerciseResponse> existingCategoryDrafts = existingCatalog.stream()
                        .filter(e -> e.getStatus() == ExerciseStatus.DRAFT && e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED)
                        .filter(e -> targetCategory == null || e.getCategory() == targetCategory)
                        .sorted(Comparator.comparing(Exercise::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(targetCount)
                        .map(e -> adminExerciseService.getExercise(e.getId()))
                        .toList();
                if (!existingCategoryDrafts.isEmpty()) {
                    return ExerciseDraftBatchResultResponse.builder()
                            .success(true)
                            .category(categoryLabel)
                            .requestedCount(targetCount)
                            .createdCount(0)
                            .createdExercises(List.of())
                            .existingDrafts(existingCategoryDrafts)
                            .message("All matching canonical " + categoryLabel
                                    + " exercises already exist in the catalog, including "
                                    + existingCategoryDrafts.size() + " AI-recommended draft(s).")
                            .build();
                }
                return ExerciseDraftBatchResultResponse.builder()
                        .success(false)
                        .errorMessage("No new unique " + categoryLabel
                                + " exercises remain to add without duplicating existing catalog entries.")
                        .category(categoryLabel)
                        .requestedCount(targetCount)
                        .createdCount(0)
                        .build();
            }

            ExerciseDraftValidationService.ValidationOutcome outcome =
                    validationService.validatePayloads(contract, selected);
            if (!outcome.valid()) {
                return validationFailure(categoryLabel, targetCount, outcome.errors());
            }
            validatedPayloads = outcome.validatedPayloads();
        }

        // Separate any already-existing catalog items from newly requested ones without creating duplicates
        List<AdminExerciseResponse> alreadyExisting = new ArrayList<>();
        List<ExerciseRecommendationPayload> toPersist = new ArrayList<>();
        for (ExerciseRecommendationPayload candidate : validatedPayloads) {
            Exercise existingMatch = existingByNormName.get(
                    ExerciseDraftValidationService.normalizeExactName(candidate.getName()));
            if (existingMatch != null) {
                alreadyExisting.add(adminExerciseService.getExercise(existingMatch.getId()));
            } else {
                toPersist.add(candidate);
            }
        }

        if (toPersist.isEmpty() && !alreadyExisting.isEmpty()) {
            return ExerciseDraftBatchResultResponse.builder()
                    .success(true)
                    .category(categoryLabel)
                    .requestedCount(targetCount)
                    .createdCount(0)
                    .createdExercises(List.of())
                    .existingDrafts(alreadyExisting)
                    .message("Requested exercise(s) already exist in the catalog (" + alreadyExisting.size()
                            + " existing entry/entries returned without creating duplicates).")
                    .build();
        }

        List<AdminExerciseResponse> createdExercises = new ArrayList<>();
        try {
            Set<String> reservedCodes = new HashSet<>(existingCodes);
            for (ExerciseRecommendationPayload payload : toPersist) {
                String uniqueCode = ensureUniqueCode(payload.getCode(), reservedCodes);
                CreateAdminExerciseRequest createReq = new CreateAdminExerciseRequest(
                        payload.getName().trim(),
                        uniqueCode,
                        payload.getCategory(),
                        payload.getDifficulty(),
                        payload.getDescription(),
                        payload.getTargetMuscles(),
                        payload.getInstructions(),
                        null,
                        null,
                        payload.getEquipment(),
                        false,
                        List.of(),
                        ExerciseStatus.DRAFT,
                        ExerciseOrigin.AI_RECOMMENDED
                );
                AdminExerciseResponse saved = adminExerciseService.createExercise(createReq);
                if (saved == null || saved.id() == null || !exerciseRepository.existsById(saved.id())) {
                    throw new IllegalStateException("Persistence verification failed for exercise: " + payload.getName());
                }
                if (saved.status() != ExerciseStatus.DRAFT
                        || saved.origin() != ExerciseOrigin.AI_RECOMMENDED
                        || saved.category() != payload.getCategory()
                        || saved.difficulty() != payload.getDifficulty()) {
                    throw new IllegalStateException("Persisted record mismatch for exercise: " + payload.getName());
                }
                createdExercises.add(saved);
                saveRecommendationTrace(admin, conversation, payload, saved.id(), signature);
            }
        } catch (Exception ex) {
            log.error("Failed while persisting AI exercise drafts: {}", ex.getMessage(), ex);
            return ExerciseDraftBatchResultResponse.builder()
                    .success(false)
                    .errorMessage("Failed to save AI exercise drafts to database: " + ex.getMessage())
                    .category(categoryLabel)
                    .requestedCount(targetCount)
                    .createdCount(0)
                    .createdExercises(List.of())
                    .existingDrafts(alreadyExisting)
                    .build();
        }

        return ExerciseDraftBatchResultResponse.builder()
                .success(!createdExercises.isEmpty())
                .errorMessage(createdExercises.isEmpty() ? "No exercises were persisted." : null)
                .category(categoryLabel)
                .requestedCount(targetCount)
                .createdCount(createdExercises.size())
                .createdExercises(createdExercises)
                .existingDrafts(alreadyExisting)
                .message("Saved " + createdExercises.size() + " AI-recommended exercise draft(s) in " + categoryLabel + ".")
                .build();
    }

    private ExerciseDraftBatchResultResponse validationFailure(
            String categoryLabel,
            int requestedCount,
            List<String> errors
    ) {
        String joined = String.join("; ", errors);
        log.warn("Rejected AI exercise draft creation batch before persistence [category={}]: {}", categoryLabel, joined);
        return ExerciseDraftBatchResultResponse.builder()
                .success(false)
                .validationFailed(true)
                .validationErrors(errors)
                .errorMessage("Validation failed — no exercises were saved: " + joined)
                .category(categoryLabel)
                .requestedCount(requestedCount)
                .createdCount(0)
                .createdExercises(List.of())
                .build();
    }

    private ExerciseRecommendationPayload resolveExplicitExercisePayload(
            ExerciseDraftValidationService.RequestedExerciseSpec spec,
            ExerciseCategory fallbackCategory
    ) {
        String cleanName = spec.name().trim();
        String exactNorm = ExerciseDraftValidationService.normalizeExactName(cleanName);
        ExerciseCategory resolvedCat = spec.category() != null
                ? spec.category()
                : (fallbackCategory != null ? fallbackCategory : ExerciseDraftValidationService.detectCategoryFromText(cleanName));
        DifficultyLevel resolvedDiff = spec.difficulty() != null
                ? spec.difficulty()
                : DifficultyLevel.INTERMEDIATE;

        // 1. Exact normalized match in canonical library (never substitutes a different exercise name)
        Optional<ExerciseRecommendationPayload> exactCanonical = CANONICAL_PROGRESSION_LIBRARY.stream()
                .filter(c -> ExerciseDraftValidationService.normalizeExactName(c.getName()).equals(exactNorm))
                .findFirst();
        if (exactCanonical.isPresent()) {
            ExerciseRecommendationPayload base = exactCanonical.get();
            return ExerciseRecommendationPayload.builder()
                    .name(cleanName)
                    .code(base.getCode())
                    .category(resolvedCat != null ? resolvedCat : base.getCategory())
                    .difficulty(resolvedDiff)
                    .targetSkill(base.getTargetSkill())
                    .progressionLevel(base.getProgressionLevel())
                    .prerequisites(base.getPrerequisites())
                    .targetMuscles(base.getTargetMuscles())
                    .equipment(base.getEquipment())
                    .description(base.getDescription())
                    .instructions(base.getInstructions())
                    .whyAdd(base.getWhyAdd())
                    .build();
        }

        // 2. Explicit progression template (e.g. Box Pistol Squat, Nordic Hamstring Curl (Negative))
        ExerciseRecommendationPayload template = EXPLICIT_EXERCISE_TEMPLATES.get(exactNorm);
        if (template != null) {
            return ExerciseRecommendationPayload.builder()
                    .name(cleanName)
                    .code(template.getCode())
                    .category(resolvedCat != null ? resolvedCat : template.getCategory())
                    .difficulty(resolvedDiff)
                    .targetSkill(template.getTargetSkill())
                    .progressionLevel(template.getProgressionLevel())
                    .prerequisites(template.getPrerequisites())
                    .targetMuscles(template.getTargetMuscles())
                    .equipment(template.getEquipment())
                    .description(template.getDescription())
                    .instructions(template.getInstructions())
                    .whyAdd(template.getWhyAdd())
                    .build();
        }

        // 3. Synthesize rich biomechanical metadata for custom/explicit exercise requested by Admin
        return synthesizeExplicitExercisePayload(cleanName, resolvedCat, resolvedDiff);
    }

    private ExerciseRecommendationPayload synthesizeExplicitExercisePayload(
            String name,
            ExerciseCategory category,
            DifficultyLevel difficulty
    ) {
        String slug = ExerciseDraftValidationService.normalizeSlug(name);
        ExerciseCategory safeCategory = category != null ? category : ExerciseCategory.SKILL;
        String muscles = switch (safeCategory) {
            case LEGS -> "Quadriceps, Glutes, Hamstrings, Calves, Core";
            case GLUTES -> "Gluteus Maximus, Gluteus Medius, Hamstrings, Core";
            case PUSH, CHEST -> "Pectorals, Anterior Deltoids, Triceps, Serratus Anterior, Core";
            case PULL, BACK -> "Latissimus Dorsi, Rhomboids, Trapezius, Biceps, Posterior Deltoids, Forearms";
            case SHOULDERS -> "Anterior, Lateral, and Posterior Deltoids, Upper Trapezius, Rotator Cuff";
            case BICEPS -> "Biceps Brachii, Brachialis, Brachioradialis, Forearms";
            case TRICEPS -> "Triceps Brachii (Long, Lateral, Medial Heads), Anconeus";
            case CORE -> "Rectus Abdominis, Transverse Abdominis, Obliques, Hip Flexors";
            case ISOMETRIC -> "Primary Joint Stabilizers, Scapular Stabilizers, Core";
            case SKILL -> "Shoulders, Core, Scapular Stabilizers, Full-Body Tension";
            case FLEXIBILITY -> "Target Muscle Belly, Tendons, Joint Capsule, Postural Stabilizers";
            case MOBILITY -> "Joint Rotators, Deep Stabilizers, Connective Tissue, Core";
            case FULL_BODY -> "Quadriceps, Glutes, Back, Pectorals, Shoulders, Core";
            case CARDIO -> "Cardiorespiratory System, Lower Body, Core, Work Capacity";
        };
        String equipment = switch (safeCategory) {
            case LEGS, GLUTES -> (name.toLowerCase(Locale.US).contains("box") || name.toLowerCase(Locale.US).contains("step"))
                    ? "Plyo Box or Bench"
                    : (name.toLowerCase(Locale.US).contains("nordic")
                            ? "Partner, Strap, or Anchored Bar"
                            : (name.toLowerCase(Locale.US).contains("barbell") || name.toLowerCase(Locale.US).contains("dumbbell")
                                    ? "Barbell / Dumbbells / Squat Rack"
                                    : "Bodyweight / Floor"));
            case PULL, BACK -> "Pull-Up Bar, Gymnastic Rings, or Cable/Row Station";
            case PUSH, CHEST -> "Floor, Parallettes, Bench, or Dumbbells/Barbell";
            case SHOULDERS, BICEPS, TRICEPS -> "Bodyweight / Rings / Dumbbells / Barbell";
            case CORE -> "Floor or Pull-Up Bar";
            case ISOMETRIC, SKILL -> "Floor, Parallettes, or Pull-Up Bar";
            case FLEXIBILITY, MOBILITY -> "Exercise Mat / Wall / Strap / Resistance Band";
            case FULL_BODY, CARDIO -> "Bodyweight / Jump Rope / Sled / Kettlebell";
        };
        String description = String.format("%s %s calisthenics movement targeting %s.",
                difficulty.name().charAt(0) + difficulty.name().substring(1).toLowerCase(Locale.US),
                safeCategory.name(),
                muscles);
        String instructions = String.format("Warm up relevant joints thoroughly. Perform %s with strict alignment, controlled tempo, full range of motion, and braced core stability throughout every set.",
                name);

        return ExerciseRecommendationPayload.builder()
                .name(name)
                .code(slug)
                .category(safeCategory)
                .difficulty(difficulty)
                .targetSkill(safeCategory.name() + " Development")
                .targetMuscles(muscles)
                .equipment(equipment)
                .description(description)
                .instructions(instructions)
                .whyAdd("Added directly from validated Admin AI draft specification.")
                .build();
    }

    private String ensureUniqueCode(String baseCode, Set<String> reservedCodes) {
        String clean = ExerciseDraftValidationService.normalizeSlug(baseCode);
        if (clean.isBlank()) {
            clean = "AI_EXERCISE";
        }
        String candidate = clean;
        int suffix = 2;
        while (reservedCodes.contains(candidate) || exerciseRepository.existsByCode(candidate)) {
            String suffixStr = "_" + suffix++;
            int maxBaseLen = Math.max(1, 50 - suffixStr.length());
            String trimmedBase = clean.length() > maxBaseLen ? clean.substring(0, maxBaseLen).replaceAll("_+$", "") : clean;
            candidate = trimmedBase + suffixStr;
        }
        reservedCodes.add(candidate);
        return candidate;
    }

    private List<AdminExerciseResponse> findExistingRetryBatchDrafts(
            User admin,
            Long conversationId,
            String signature
    ) {
        String marker = DRAFT_BATCH_PREFIX + signature;
        List<AdminAiRecommendation> candidates = new ArrayList<>();
        if (conversationId != null) {
            candidates.addAll(recommendationRepository.findByConversationIdOrderByCreatedAtAsc(conversationId));
        } else if (admin != null) {
            ZonedDateTime cutoff = ZonedDateTime.now().minusSeconds(60);
            recommendationRepository.findByRecommendationTypeOrderByCreatedAtDesc(AdminRecommendationType.EXERCISE).stream()
                    .filter(r -> r.getCreatedByAdmin() != null && Objects.equals(r.getCreatedByAdmin().getId(), admin.getId()))
                    .filter(r -> r.getCreatedAt() == null || !r.getCreatedAt().isBefore(cutoff))
                    .forEach(candidates::add);
        }

        List<Long> matchedIds = candidates.stream()
                .filter(r -> marker.equals(r.getReviewNotes())
                        && "EXERCISE".equalsIgnoreCase(r.getCreatedResourceType())
                        && r.getCreatedResourceId() != null)
                .map(AdminAiRecommendation::getCreatedResourceId)
                .distinct()
                .toList();

        if (matchedIds.isEmpty()) {
            return List.of();
        }
        return exerciseRepository.findAllById(matchedIds).stream()
                .map(e -> adminExerciseService.getExercise(e.getId()))
                .toList();
    }

    private void saveRecommendationTrace(
            User admin,
            AdminAiConversation conversation,
            ExerciseRecommendationPayload payload,
            Long createdExerciseId,
            String signature
    ) {
        String payloadJson = "{}";
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
        }
        AdminAiRecommendation entity = AdminAiRecommendation.builder()
                .recommendationType(AdminRecommendationType.EXERCISE)
                .status(AdminRecommendationStatus.APPROVED)
                .priority(AdminRecommendationPriority.MEDIUM)
                .title("AI Draft Exercise: " + payload.getName())
                .summary(payload.getDescription())
                .rationale(payload.getWhyAdd())
                .payloadJson(payloadJson)
                .conversation(conversation)
                .createdByAdmin(admin)
                .reviewedByAdmin(admin)
                .reviewedAt(ZonedDateTime.now())
                .reviewNotes(DRAFT_BATCH_PREFIX + signature)
                .createdResourceId(createdExerciseId)
                .createdResourceType("EXERCISE")
                .build();
        recommendationRepository.save(entity);
    }

    private boolean matchesCategoryOrSkill(
            ExerciseRecommendationPayload candidate,
            ExerciseCategory targetCategory,
            String skillFilter
    ) {
        if (targetCategory != null) {
            return candidate.getCategory() == targetCategory;
        }
        if (skillFilter != null && !skillFilter.isBlank()) {
            String filterLower = skillFilter.trim().toLowerCase(Locale.US);
            return (candidate.getTargetSkill() != null && candidate.getTargetSkill().toLowerCase(Locale.US).contains(filterLower))
                    || candidate.getName().toLowerCase(Locale.US).contains(filterLower)
                    || candidate.getCategory().name().toLowerCase(Locale.US).contains(filterLower);
        }
        return false;
    }

    public static ExerciseCategory detectCategoryFromText(String prompt, String skillFilter) {
        ExerciseCategory fromPrompt = ExerciseDraftValidationService.detectCategoryFromText(prompt);
        if (fromPrompt != null) {
            return fromPrompt;
        }
        return ExerciseDraftValidationService.detectCategoryFromText(skillFilter);
    }

    public static int resolveRequestedCount(String prompt, Integer explicitCount) {
        return ExerciseDraftValidationService.resolveRequestedCount(prompt, explicitCount);
    }

    private String buildDraftBatchSignature(
            String userPrompt,
            ExerciseDraftValidationService.ExerciseDraftRequestContract contract
    ) {
        String normPrompt = userPrompt == null ? "" : userPrompt.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
        String explicitKey = contract.explicitExercises().stream()
                .map(s -> ExerciseDraftValidationService.normalizeExactName(s.name())
                        + ":" + (s.category() != null ? s.category().name() : "")
                        + ":" + (s.difficulty() != null ? s.difficulty().name() : ""))
                .collect(Collectors.joining(","));
        return (contract.requiredCategory() != null ? contract.requiredCategory().name() : "ANY")
                + "|" + (contract.requiredDifficulty() != null ? contract.requiredDifficulty().name() : "ANY")
                + "|" + contract.expectedCount()
                + "|" + shortSha256(normPrompt + "|" + explicitKey);
    }

    private String shortSha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
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
