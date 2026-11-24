package com.calisthenics.backend.exercisetree.service;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.MasteryCriteriaType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;

import java.util.ArrayList;
import java.util.List;

/**
 * Curated, biomechanically validated progression tree placements, branching edges,
 * exercise-specific mastery criteria, and reported missing progression gaps.
 *
 * All entries reference existing server exercises strictly by exercise.code — never creating
 * or duplicating Exercise records.
 */
public final class ExerciseTreeCuratedCatalog {

    private ExerciseTreeCuratedCatalog() {}

    public record CategorySpec(
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey,
            String displayName,
            String description,
            int displayOrder
    ) {}

    public record NodeSpec(
            String exerciseCode,
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey,
            DifficultyLevel progressionDifficulty,
            boolean difficultyValidated,
            String branchKey,
            String branchLabel,
            int branchOrder,
            boolean primaryPlacement,
            MasteryCriteriaType masteryCriteriaType,
            Integer targetReps,
            Integer targetSets,
            Integer targetHoldSeconds,
            Double targetWeightKg,
            int minCompletedSessions,
            String masterySummary,
            boolean needsManualReview,
            String reviewNotes
    ) {}

    public record EdgeSpec(
            String sourceCode,
            String targetCode,
            ExerciseTreeCategoryType trainingType,
            ProgressionRelationType relationType,
            String branchLabel,
            String notes,
            int displayOrder
    ) {}

    public record MissingNodeSpec(
            ExerciseTreeCategoryType trainingType,
            String subcategoryKey,
            String branchKey,
            String suggestedName,
            DifficultyLevel targetDifficulty,
            String afterExerciseCode,
            String beforeExerciseCode,
            String reason
    ) {}

    public static List<CategorySpec> categories() {
        return List.of(
                // CALISTHENICS (6 categories)
                new CategorySpec(ExerciseTreeCategoryType.CALISTHENICS, "PUSH", "Push",
                        "Horizontal, dip, ring, and straight-arm pushing progressions from fundamentals to planche pressing.", 1),
                new CategorySpec(ExerciseTreeCategoryType.CALISTHENICS, "PULL", "Pull",
                        "Vertical pulling, explosive high pulls, muscle-ups, lever pulls, and one-arm chin-up progressions.", 2),
                new CategorySpec(ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", "Static Skills",
                        "Isometric straight-arm levers and holds: Planche, Front Lever, Back Lever, and Human Flag.", 3),
                new CategorySpec(ExerciseTreeCategoryType.CALISTHENICS, "CORE", "Core",
                        "Compression holds (L-Sit to V-Sit), hanging leg raises, rollouts, and Dragon Flag progressions.", 4),
                new CategorySpec(ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", "Handstand",
                        "Wrist/shoulder prep, freestanding balance, pike pressing, and handstand push-up strength.", 5),
                new CategorySpec(ExerciseTreeCategoryType.CALISTHENICS, "LEGS", "Legs",
                        "Bodyweight single-leg strength (Pistol & Shrimp squats), Nordic curls, lower mobility, and splits.", 6),

                // GYM (9 categories)
                new CategorySpec(ExerciseTreeCategoryType.GYM, "CHEST", "Chest",
                        "Machine and barbell horizontal and incline pressing strength patterns.", 1),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "BACK", "Back",
                        "Vertical pulling, horizontal rowing, weighted pull-ups, and scapular health.", 2),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "SHOULDERS", "Shoulders",
                        "Overhead pressing, lateral deltoid isolation, and shoulder mobility preparation.", 3),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "BICEPS", "Biceps",
                        "Direct elbow-flexion strength and hypertrophy work.", 4),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "TRICEPS", "Triceps",
                        "Direct elbow-extension strength supporting pressing lockouts.", 5),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "QUADRICEPS", "Quadriceps",
                        "Free-weight squats, machine squats, sled drives, ankle prep, and lower-leg accessories.", 6),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "HAMSTRINGS", "Hamstrings",
                        "Hip hinges, Romanian/stiff-leg deadlifts, good mornings, GHD raises, and knee-flexion curls.", 7),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "GLUTES", "Glutes",
                        "Glute-dominant deadlifts, sumo/box squats, step-ups, lunges, and Bulgarian split squats.", 8),
                new CategorySpec(ExerciseTreeCategoryType.GYM, "CORE", "Core",
                        "Loaded spinal flexion, hanging leg raises, anti-extension bracing, and anti-rotation control.", 9)
        );
    }

    public static List<NodeSpec> nodes() {
        List<NodeSpec> list = new ArrayList<>();

        // ==================== CALISTHENICS: PUSH ====================
        list.add(repNode("PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.BEGINNER, true,
                "PUSH_UP_BRANCH", "Push-Up Path", 1, true, 15, 3, 3, "3x15 strict chest-to-floor push-ups across 3 sessions", false, null));
        list.add(repNode("DIAMOND_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.INTERMEDIATE, true,
                "PUSH_UP_BRANCH", "Push-Up Path", 2, true, 12, 3, 3, "3x12 strict diamond push-ups across 3 sessions", false, null));
        list.add(repNode("ARCHER_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.ADVANCED, true,
                "PUSH_UP_BRANCH", "Push-Up Path", 3, true, 8, 3, 3, "3x8 per side controlled archer push-ups across 3 sessions", false, null));

        list.add(repNode("PSEUDO_PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.INTERMEDIATE, true,
                "PLANCHE_PUSH_BRANCH", "Planche Pressing", 1, true, 10, 3, 3, "3x10 deep-lean pseudo planche push-ups across 3 sessions", false, null));
        list.add(repNode("TUCK_PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.ADVANCED, true,
                "PLANCHE_PUSH_BRANCH", "Planche Pressing", 2, true, 6, 3, 4, "3x6 full-ROM tuck planche push-ups across 4 sessions", false, null));
        list.add(repNode("PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.ELITE, false,
                "PLANCHE_PUSH_BRANCH", "Planche Pressing", 3, true, 5, 3, 4, "3x5 strict planche push-ups across 4 sessions", true,
                "Catalog difficulty is ADVANCED; progression classified ELITE for full planche pressing. Missing description & instructions in catalog."));

        list.add(repNode("DIPS", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.BEGINNER, true,
                "DIP_BRANCH", "Dip & Ring Strength", 1, true, 12, 3, 3, "3x12 full-depth parallel bar dips across 3 sessions", false, null));
        list.add(repNode("STRAIGHT_BAR_DIP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.INTERMEDIATE, true,
                "DIP_BRANCH", "Dip & Ring Strength", 2, true, 10, 3, 3, "3x10 chest-to-bar straight bar dips across 3 sessions", false, null));
        list.add(repNode("GYMNASTIC_RING_DIP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.ADVANCED, true,
                "DIP_BRANCH", "Dip & Ring Strength", 3, true, 8, 3, 3, "3x8 turned-out ring dips across 3 sessions", false, null));

        // Secondary Push placements linking vertical pushing to Handstand
        list.add(repNode("PIKE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.INTERMEDIATE, true,
                "OVERHEAD_PUSH_BRANCH", "Overhead Push", 1, false, 10, 3, 3, "3x10 strict pike push-ups across 3 sessions", false, null));
        list.add(repNode("WALL_HANDSTAND_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "PUSH", DifficultyLevel.ADVANCED, true,
                "OVERHEAD_PUSH_BRANCH", "Overhead Push", 2, false, 6, 3, 4, "3x6 full-ROM wall HSPU across 4 sessions", false, null));

        // ==================== CALISTHENICS: PULL ====================
        list.add(repNode("PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.INTERMEDIATE, true,
                "VERTICAL_PULL", "Vertical Pulling", 1, true, 10, 3, 3, "3x10 strict dead-hang pull-ups across 3 sessions", false, null));
        list.add(repNode("HIGH_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.INTERMEDIATE, true,
                "MUSCLE_UP_PATH", "Muscle-Up Path", 1, true, 6, 3, 3, "3x6 explosive sternum-to-bar high pull-ups across 3 sessions", false, null));
        list.add(weightedNode("WEIGHTED_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.ADVANCED, true,
                "VERTICAL_PULL", "Vertical Pulling", 2, true, 5, 3, 20.0, 4, "3x5 weighted pull-ups (+20kg or consistent progressive load) across 4 sessions", false, null));
        list.add(repNode("ARCHER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.ADVANCED, true,
                "VERTICAL_PULL", "Vertical Pulling", 3, true, 6, 3, 4, "3x6 per side controlled archer pull-ups across 4 sessions", false, null));
        list.add(repNode("ONE_ARM_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.ELITE, true,
                "VERTICAL_PULL", "Vertical Pulling", 4, true, 3, 3, 4, "3x3 strict one-arm chin-ups across 4 sessions", false, null));

        list.add(repNode("MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.ADVANCED, true,
                "MUSCLE_UP_PATH", "Muscle-Up Path", 2, true, 5, 3, 4, "3x5 clean bar muscle-ups across 4 sessions", false, null));
        list.add(repNode("RING_MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.ADVANCED, true,
                "MUSCLE_UP_PATH", "Muscle-Up Path", 3, true, 4, 3, 4, "3x4 strict false-grip ring muscle-ups across 4 sessions", false, null));

        list.add(repNode("FRONT_LEVER_RAISES", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.INTERMEDIATE, true,
                "LEVER_PULL", "Lever Pulling", 1, true, 6, 3, 3, "3x6 controlled front lever raises across 3 sessions", false, null));
        list.add(repNode("FRONT_LEVER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, "PULL", DifficultyLevel.ELITE, false,
                "LEVER_PULL", "Lever Pulling", 2, true, 4, 3, 4, "3x4 strict front lever pull-ups across 4 sessions", true,
                "Catalog difficulty is ADVANCED; progression classified ELITE for horizontal lever pull-ups. Missing description & instructions in catalog."));

        // ==================== CALISTHENICS: STATIC_SKILLS ====================
        // Planche Branch
        list.add(holdNode("FROG_STAND", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.BEGINNER, true,
                "PLANCHE", "Planche", 1, true, 20, 3, 3, "3x20s balanced frog stand holds across 3 sessions", false, null));
        list.add(holdNode("PLANCHE_LEAN", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.BEGINNER, true,
                "PLANCHE", "Planche", 2, true, 20, 4, 3, "4x20s protracted planche lean holds across 3 sessions", false, null));
        list.add(holdNode("TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.INTERMEDIATE, true,
                "PLANCHE", "Planche", 3, true, 15, 4, 3, "4x15s straight-arm tuck planche holds across 3 sessions", false, null));
        list.add(holdNode("ADV_TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, true,
                "PLANCHE", "Planche", 4, true, 12, 4, 4, "4x12s flat-back advanced tuck planche holds across 4 sessions", false, null));
        list.add(holdNode("STRADDLE_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, true,
                "PLANCHE", "Planche", 5, true, 8, 4, 4, "4x8s clean straddle planche holds across 4 sessions", false, null));
        list.add(holdNode("FULL_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ELITE, true,
                "PLANCHE", "Planche", 6, true, 5, 4, 5, "4x5s locked-arm full planche holds across 5 sessions", false, null));
        list.add(holdNode("PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, false,
                "PLANCHE", "Planche", 7, true, 6, 3, 4, "3x6s planche hold across 4 sessions", true,
                "Overlaps with FULL_PLANCHE (id=4). Preserved without mutation; review whether to keep as general planche milestone."));

        // Front Lever Branch
        list.add(holdNode("TUCK_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.INTERMEDIATE, false,
                "FRONT_LEVER", "Front Lever", 1, true, 15, 4, 3, "4x15s horizontal tuck front lever holds across 3 sessions", true,
                "Catalog difficulty is BEGINNER; biomechanically INTERMEDIATE straight-arm pulling skill. Preserved catalog field for admin review."));
        list.add(holdNode("ADV_TUCK_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.INTERMEDIATE, true,
                "FRONT_LEVER", "Front Lever", 2, true, 12, 4, 3, "4x12s advanced tuck front lever holds across 3 sessions", false, null));
        list.add(holdNode("ONE_LEG_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, true,
                "FRONT_LEVER", "Front Lever", 3, true, 10, 4, 4, "4x10s one-leg front lever holds across 4 sessions", false, null));
        list.add(holdNode("STRADDLE_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, true,
                "FRONT_LEVER", "Front Lever", 4, true, 8, 4, 4, "4x8s straddle front lever holds across 4 sessions", false, null));
        list.add(holdNode("FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ELITE, false,
                "FRONT_LEVER", "Front Lever", 5, true, 6, 4, 5, "4x6s full horizontal front lever holds across 5 sessions", true,
                "Catalog difficulty is ADVANCED; full straight-body Front Lever classified ELITE in progression tree for admin validation."));

        // Back Lever Branch
        list.add(holdNode("GERMAN_HANG", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.BEGINNER, true,
                "BACK_LEVER", "Back Lever", 1, true, 20, 3, 3, "3x20s pain-free German hang holds across 3 sessions", false, null));
        list.add(repNode("SKIN_THE_CAT", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.BEGINNER, true,
                "BACK_LEVER", "Back Lever", 2, true, 5, 3, 3, "3x5 slow controlled skin-the-cat reps across 3 sessions", false, null));
        list.add(holdNode("TUCK_BACK_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.INTERMEDIATE, true,
                "BACK_LEVER", "Back Lever", 3, true, 15, 4, 3, "4x15s tuck back lever holds across 3 sessions", false, null));
        list.add(holdNode("FULL_BACK_LEVER", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, true,
                "BACK_LEVER", "Back Lever", 4, true, 10, 4, 4, "4x10s straight-body full back lever holds across 4 sessions", false, null));

        // Human Flag Branch
        list.add(holdNode("VERTICAL_FLAG_HOLD", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.INTERMEDIATE, true,
                "HUMAN_FLAG", "Human Flag", 1, true, 12, 3, 3, "3x12s per side vertical flag holds across 3 sessions", false, null));
        list.add(holdNode("TUCK_HUMAN_FLAG", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ADVANCED, true,
                "HUMAN_FLAG", "Human Flag", 2, true, 8, 3, 4, "3x8s per side tuck human flag holds across 4 sessions", false, null));
        list.add(holdNode("FULL_HUMAN_FLAG", ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", DifficultyLevel.ELITE, true,
                "HUMAN_FLAG", "Human Flag", 3, true, 5, 3, 5, "3x5s full human flag holds across 5 sessions", false, null));

        // ==================== CALISTHENICS: HANDSTAND ====================
        list.add(repNode("WRIST_MOBILITY", ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", DifficultyLevel.BEGINNER, true,
                "HANDSTAND_BALANCE", "Handstand Prep & Balance", 1, true, 12, 2, 3, "Complete 3 wrist conditioning sessions (2x12 reps)", false, null));
        list.add(repNode("SHOULDER_MOBILITY", ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", DifficultyLevel.BEGINNER, true,
                "HANDSTAND_BALANCE", "Handstand Prep & Balance", 2, true, 15, 2, 3, "Complete 3 overhead shoulder mobility sessions (2x15 reps)", false, null));
        list.add(holdNode("FROG_STAND", ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", DifficultyLevel.BEGINNER, true,
                "HANDSTAND_BALANCE", "Handstand Prep & Balance", 3, false, 20, 3, 3, "3x20s balanced frog stand holds across 3 sessions", false, null));
        list.add(holdNode("HANDSTAND", ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", DifficultyLevel.INTERMEDIATE, true,
                "HANDSTAND_BALANCE", "Handstand Prep & Balance", 4, true, 30, 3, 4, "3x30s controlled freestanding handstand holds across 4 sessions", false, null));
        list.add(repNode("PIKE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", DifficultyLevel.INTERMEDIATE, true,
                "HANDSTAND_STRENGTH", "Handstand Pressing", 1, true, 10, 3, 3, "3x10 strict pike push-ups across 3 sessions", false, null));
        list.add(repNode("WALL_HANDSTAND_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", DifficultyLevel.ADVANCED, true,
                "HANDSTAND_STRENGTH", "Handstand Pressing", 2, true, 6, 3, 4, "3x6 full-ROM wall handstand push-ups across 4 sessions", true,
                "V59 links this exercise to Freestanding HSPU skill level; kept at ADVANCED wall HSPU while Freestanding HSPU is reported as missing."));

        // ==================== CALISTHENICS: CORE ====================
        list.add(holdNode("L_SIT", ExerciseTreeCategoryType.CALISTHENICS, "CORE", DifficultyLevel.INTERMEDIATE, true,
                "COMPRESSION_HOLDS", "Compression Holds", 1, true, 20, 3, 3, "3x20s locked-knee L-sit holds across 3 sessions", false, null));
        list.add(holdNode("V_SIT", ExerciseTreeCategoryType.CALISTHENICS, "CORE", DifficultyLevel.ELITE, true,
                "COMPRESSION_HOLDS", "Compression Holds", 2, true, 10, 3, 5, "3x10s high-angle V-sit holds across 5 sessions", false, null));
        list.add(repNode("AB_WHEEL", ExerciseTreeCategoryType.CALISTHENICS, "CORE", DifficultyLevel.INTERMEDIATE, true,
                "DYNAMIC_CORE", "Anti-Extension & Hanging", 1, true, 12, 3, 3, "3x12 controlled ab wheel rollouts across 3 sessions", false, null));
        list.add(repNode("HANGING_LEG_RAISE", ExerciseTreeCategoryType.CALISTHENICS, "CORE", DifficultyLevel.INTERMEDIATE, true,
                "DYNAMIC_CORE", "Anti-Extension & Hanging", 2, true, 12, 3, 3, "3x12 strict hanging leg raises across 3 sessions", true,
                "Near-duplicate of HANGING_LEG_RAISE_STRICT_BAR (id=39). Linked as alternative for admin review."));
        list.add(repNode("HANGING_LEG_RAISE_STRICT_BAR", ExerciseTreeCategoryType.CALISTHENICS, "CORE", DifficultyLevel.INTERMEDIATE, true,
                "DYNAMIC_CORE", "Anti-Extension & Hanging", 3, true, 10, 3, 3, "3x10 strict toes-to-bar raises across 3 sessions", true,
                "Near-duplicate of HANGING_LEG_RAISE (id=159). Linked as alternative for admin review."));
        list.add(repNode("DRAGON_FLAG", ExerciseTreeCategoryType.CALISTHENICS, "CORE", DifficultyLevel.ADVANCED, true,
                "DYNAMIC_CORE", "Anti-Extension & Hanging", 4, true, 6, 3, 4, "3x6 straight-body dragon flags across 4 sessions", false, null));

        // ==================== CALISTHENICS: LEGS ====================
        list.add(repNode("SQUAT", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.BEGINNER, true,
                "SINGLE_LEG_SQUAT", "Squat to Single-Leg", 1, true, 25, 3, 3, "3x25 full-depth bodyweight squats across 3 sessions", false, null));
        list.add(repNode("PISTOL_SQUAT", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.ADVANCED, true,
                "SINGLE_LEG_SQUAT", "Squat to Single-Leg", 2, true, 8, 3, 4, "3x8 per leg strict pistol squats across 4 sessions", false, null));
        list.add(repNode("SHRIMP_SQUAT", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.ADVANCED, true,
                "SINGLE_LEG_SQUAT", "Squat to Single-Leg", 3, true, 8, 3, 4, "3x8 per leg controlled shrimp squats across 4 sessions", false, null));
        list.add(repNode("NORDIC_HAMSTRING_CURL", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.ADVANCED, true,
                "POSTERIOR_CHAIN", "Posterior Chain", 1, true, 6, 3, 4, "3x6 controlled eccentric/concentric Nordic curls across 4 sessions", false, null));
        list.add(repNode("GYM_HIP_ANKLE_MOBILITY", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.BEGINNER, true,
                "LOWER_MOBILITY", "Mobility & Splits", 1, true, 12, 2, 3, "Complete 3 ankle mobilization sessions (2x12 per side)", false, null));
        list.add(repNode("HIP_MOBILITY", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.BEGINNER, true,
                "LOWER_MOBILITY", "Mobility & Splits", 2, true, 12, 2, 3, "Complete 3 90/90 hip switch sessions (2x12 reps)", false, null));
        list.add(holdNode("MIDDLE_SPLIT", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.INTERMEDIATE, true,
                "LOWER_MOBILITY", "Mobility & Splits", 3, true, 45, 3, 4, "3x45s active middle split frog slides across 4 sessions", false, null));
        list.add(holdNode("FRONT_SPLIT", ExerciseTreeCategoryType.CALISTHENICS, "LEGS", DifficultyLevel.ADVANCED, true,
                "LOWER_MOBILITY", "Mobility & Splits", 4, true, 45, 3, 4, "3x45s per side active front split slider holds across 4 sessions", false, null));

        // ==================== GYM: CHEST ====================
        list.add(gymRepNode("MACHINE_CHEST_PRESS", "CHEST", DifficultyLevel.BEGINNER, "HORIZONTAL_PRESS", "Pressing Strength", 1, 12, 3, 3));
        list.add(gymRepNode("BENCH_PRESS", "CHEST", DifficultyLevel.INTERMEDIATE, "HORIZONTAL_PRESS", "Pressing Strength", 2, 8, 4, 4));
        list.add(gymRepNode("INCLINE_BENCH_PRESS", "CHEST", DifficultyLevel.INTERMEDIATE, "HORIZONTAL_PRESS", "Pressing Strength", 3, 8, 4, 4));

        // ==================== GYM: BACK ====================
        list.add(gymRepNode("LAT_PULLDOWN", "BACK", DifficultyLevel.BEGINNER, "VERTICAL_PULL", "Vertical Pulling", 1, 12, 3, 3));
        list.add(weightedNode("WEIGHTED_PULL_UP", ExerciseTreeCategoryType.GYM, "BACK", DifficultyLevel.ADVANCED, true,
                "VERTICAL_PULL", "Vertical Pulling", 2, true, 6, 4, 15.0, 4, "4x6 weighted pull-ups with consistent progressive load across 4 sessions", false, null));
        list.add(gymRepNode("SEATED_CABLE_ROW", "BACK", DifficultyLevel.BEGINNER, "HORIZONTAL_ROW", "Horizontal Rowing", 1, 12, 3, 3));
        list.add(gymRepNode("BARBELL_ROW", "BACK", DifficultyLevel.INTERMEDIATE, "HORIZONTAL_ROW", "Horizontal Rowing", 2, 8, 4, 4));
        list.add(gymRepNode("FACE_PULL", "BACK", DifficultyLevel.BEGINNER, "SCAPULAR_HEALTH", "Upper-Back & Scapular Health", 1, 15, 3, 3));

        // ==================== GYM: SHOULDERS ====================
        list.add(gymRepNode("GYM_SHOULDER_MOBILITY", "SHOULDERS", DifficultyLevel.BEGINNER, "SHOULDER_PREP", "Mobility & Isolation", 1, 12, 2, 3));
        list.add(gymRepNode("LATERAL_RAISE", "SHOULDERS", DifficultyLevel.BEGINNER, "SHOULDER_PREP", "Mobility & Isolation", 2, 15, 3, 3));
        list.add(gymRepNode("OVERHEAD_PRESS", "SHOULDERS", DifficultyLevel.INTERMEDIATE, "OVERHEAD_STRENGTH", "Overhead Pressing", 1, 8, 4, 4));

        // ==================== GYM: BICEPS ====================
        list.add(gymRepNode("BICEP_CURL", "BICEPS", DifficultyLevel.BEGINNER, "ELBOW_FLEXION", "Biceps Strength", 1, 12, 3, 3));

        // ==================== GYM: TRICEPS ====================
        list.add(gymRepNode("TRICEPS_EXTENSION", "TRICEPS", DifficultyLevel.BEGINNER, "ELBOW_EXTENSION", "Triceps Strength", 1, 12, 3, 3));

        // ==================== GYM: CORE ====================
        list.add(gymRepNode("CABLE_CRUNCH_KNEELING_ROPE", "CORE", DifficultyLevel.BEGINNER, "LOADED_FLEXION", "Loaded Spinal Flexion", 1, 15, 3, 3));
        list.add(gymRepNode("WEIGHTED_DECLINE_CRUNCH", "CORE", DifficultyLevel.INTERMEDIATE, "LOADED_FLEXION", "Loaded Spinal Flexion", 2, 12, 3, 3));
        list.add(gymRepNode("DECLINE_BENCH_WEIGHTED_SIT_UP", "CORE", DifficultyLevel.INTERMEDIATE, "LOADED_FLEXION", "Loaded Spinal Flexion", 3, 12, 3, 3));

        list.add(gymRepNode("CABLE_STANDING_HIP_FLEXOR_MARCH_RESISTED", "CORE", DifficultyLevel.BEGINNER, "HANGING_FLEXION", "Hip Flexion & Hanging", 1, 12, 3, 3));
        list.add(gymRepNode("HANGING_WEIGHTED_CAPTAIN_S_CHAIR_LEG_RAISE", "CORE", DifficultyLevel.INTERMEDIATE, "HANGING_FLEXION", "Hip Flexion & Hanging", 2, 10, 3, 3));
        list.add(repNode("HANGING_LEG_RAISE", ExerciseTreeCategoryType.GYM, "CORE", DifficultyLevel.INTERMEDIATE, true,
                "HANGING_FLEXION", "Hip Flexion & Hanging", 3, true, 12, 3, 3, "3x12 strict hanging leg raises across 3 sessions", true,
                "Near-duplicate of HANGING_LEG_RAISE_STRICT_BAR (id=39). Linked as alternative for admin review."));
        list.add(repNode("HANGING_LEG_RAISE_STRICT_BAR", ExerciseTreeCategoryType.GYM, "CORE", DifficultyLevel.INTERMEDIATE, true,
                "HANGING_FLEXION", "Hip Flexion & Hanging", 4, true, 10, 3, 3, "3x10 strict bar hanging leg raises across 3 sessions", true,
                "Near-duplicate of HANGING_LEG_RAISE (id=159). Linked as alternative for admin review."));

        list.add(holdNode("HOLLOW_FOREARM_PLANK_HOLD", ExerciseTreeCategoryType.GYM, "CORE", DifficultyLevel.BEGINNER, true,
                "ANTI_EXTENSION", "Bracing & Anti-Extension", 1, true, 45, 3, 3, "3x45s hollow forearm plank holds across 3 sessions", false, null));
        list.add(gymRepNode("WEIGHTED_PLANK_PLATE_TRANSFER", "CORE", DifficultyLevel.INTERMEDIATE, "ANTI_EXTENSION", "Bracing & Anti-Extension", 2, 10, 3, 3));
        list.add(gymRepNode("AB_WHEEL", "CORE", DifficultyLevel.INTERMEDIATE, "ANTI_EXTENSION", "Bracing & Anti-Extension", 3, 12, 3, 3));

        list.add(gymRepNode("PALLOF_ANTI_ROTATION_CABLE_PRESS", "CORE", DifficultyLevel.BEGINNER, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 1, 12, 3, 3));
        list.add(gymRepNode("KNEELING_CABLE_WOODCHOPPER", "CORE", DifficultyLevel.BEGINNER, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 2, 12, 3, 3));
        list.add(gymRepNode("RUSSIAN_KETTLEBELL_TWIST", "CORE", DifficultyLevel.BEGINNER, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 3, 16, 3, 3));
        list.add(gymRepNode("ROMAN_CHAIR_BACK_SIDE_RAISE", "CORE", DifficultyLevel.BEGINNER, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 4, 12, 3, 3));
        list.add(gymRepNode("LANDMINE_ANTI_ROTATION_BARBELL_RAINBOWS", "CORE", DifficultyLevel.INTERMEDIATE, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 5, 10, 3, 3));
        list.add(gymRepNode("KETTLEBELL_WINDMILL", "CORE", DifficultyLevel.INTERMEDIATE, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 6, 8, 3, 3));
        list.add(gymRepNode("WEIGHTED_SIDE_PLANK_HIP_DIP", "CORE", DifficultyLevel.INTERMEDIATE, "ROTATION_OBLIQUES", "Anti-Rotation & Obliques", 7, 12, 3, 3));

        // ==================== GYM: QUADRICEPS ====================
        // Free-weight squats
        list.add(gymRepNode("GOBLET_SQUAT", "QUADRICEPS", DifficultyLevel.BEGINNER, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 1, 12, 3, 3));
        list.add(gymRepNode("BARBELL_LANDMINE_SQUAT", "QUADRICEPS", DifficultyLevel.BEGINNER, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 2, 12, 3, 3));
        list.add(gymRepNode("CABLE_PULL_THROUGH_SQUAT_FRONT_CABLE_SQUAT", "QUADRICEPS", DifficultyLevel.BEGINNER, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 3, 12, 3, 3));
        list.add(gymRepNode("BARBELL_BACK_SQUAT", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 4, 8, 4, 4));
        list.add(gymRepNode("BARBELL_FRONT_SQUAT", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 5, 8, 4, 4));
        list.add(gymRepNode("KETTLEBELL_FRONT_SQUAT_DOUBLE_RACK", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 6, 10, 3, 3));
        list.add(gymRepNode("HEEL_ELEVATED_DUMBBELL_FRONT_SQUAT", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 7, 10, 3, 3));
        list.add(gymRepNode("DUMBBELL_GOBLET_CYCLIST_SQUAT", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 8, 12, 3, 3));
        list.add(gymRepNode("BARBELL_PAUSE_SQUAT", "QUADRICEPS", DifficultyLevel.ADVANCED, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 9, 5, 4, 4));
        list.add(gymRepNode("PIN_SQUAT_CONCENTRIC_START", "QUADRICEPS", DifficultyLevel.ADVANCED, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 10, 5, 4, 4));
        list.add(gymRepNode("SAFETY_SQUAT_BAR_SQUAT", "QUADRICEPS", DifficultyLevel.ADVANCED, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 11, 6, 4, 4));
        list.add(gymRepNode("HATFIELD_BARBELL_SQUAT_HAND_SUPPORTED", "QUADRICEPS", DifficultyLevel.ADVANCED, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 12, 6, 4, 4));
        list.add(gymRepNode("ZERCHER_SQUAT", "QUADRICEPS", DifficultyLevel.ADVANCED, "FREE_WEIGHT_SQUAT", "Free-Weight Squats", 13, 6, 4, 4));

        // Machine squats & isolation
        list.add(gymRepNode("LEG_EXTENSION_MACHINE", "QUADRICEPS", DifficultyLevel.BEGINNER, "MACHINE_QUAD", "Machine Squats & Extensions", 1, 12, 3, 3));
        list.add(gymRepNode("LEG_PRESS_45_DEGREE", "QUADRICEPS", DifficultyLevel.BEGINNER, "MACHINE_QUAD", "Machine Squats & Extensions", 2, 12, 3, 3));
        list.add(gymRepNode("SMITH_MACHINE_SQUAT", "QUADRICEPS", DifficultyLevel.BEGINNER, "MACHINE_QUAD", "Machine Squats & Extensions", 3, 10, 3, 3));
        list.add(gymRepNode("SINGLE_LEG_EXTENSION_MACHINE", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 4, 12, 3, 3));
        list.add(gymRepNode("NARROW_LOW_LEG_PRESS", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 5, 10, 4, 3));
        list.add(gymRepNode("SINGLE_LEG_MACHINE_LEG_PRESS", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 6, 10, 3, 3));
        list.add(gymRepNode("HACK_SQUAT_MACHINE", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 7, 10, 4, 3));
        list.add(gymRepNode("PENDULUM_SQUAT", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 8, 10, 4, 3));
        list.add(gymRepNode("V_SQUAT_MACHINE_FACING_PAD", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 9, 10, 4, 3));
        list.add(gymRepNode("BELT_SQUAT_MACHINE", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "MACHINE_QUAD", "Machine Squats & Extensions", 10, 10, 4, 3));
        list.add(gymRepNode("SINGLE_LEG_MACHINE_HACK_SQUAT", "QUADRICEPS", DifficultyLevel.ADVANCED, "MACHINE_QUAD", "Machine Squats & Extensions", 11, 8, 3, 4));

        // Sled & ankle prep
        list.add(gymRepNode("GYM_HIP_ANKLE_MOBILITY", "QUADRICEPS", DifficultyLevel.BEGINNER, "SLED_AND_ANKLE", "Sled Quad Drive & Ankle Prep", 1, 12, 2, 3));
        list.add(gymRepNode("BACKWARD_SLED_DRAG", "QUADRICEPS", DifficultyLevel.BEGINNER, "SLED_AND_ANKLE", "Sled Quad Drive & Ankle Prep", 2, 4, 3, 3));
        list.add(gymRepNode("SLED_BACKWARD_MARCH", "QUADRICEPS", DifficultyLevel.BEGINNER, "SLED_AND_ANKLE", "Sled Quad Drive & Ankle Prep", 3, 4, 3, 3));
        list.add(gymRepNode("SLED_PUSH_PROWLER_DRIVE", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "SLED_AND_ANKLE", "Sled Quad Drive & Ankle Prep", 4, 4, 3, 3));

        // Lower-leg / Calves & Tibialis support branch
        list.add(gymRepNode("STANDING_MACHINE_CALF_RAISE", "QUADRICEPS", DifficultyLevel.BEGINNER, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 1, 15, 3, 3));
        list.add(gymRepNode("SEATED_MACHINE_CALF_RAISE", "QUADRICEPS", DifficultyLevel.BEGINNER, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 2, 15, 3, 3));
        list.add(gymRepNode("LEG_PRESS_CALF_RAISE", "QUADRICEPS", DifficultyLevel.BEGINNER, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 3, 15, 3, 3));
        list.add(gymRepNode("SMITH_MACHINE_CALF_RAISE_ON_BLOCK", "QUADRICEPS", DifficultyLevel.BEGINNER, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 4, 15, 3, 3));
        list.add(gymRepNode("DUMBBELL_SEATED_CALF_RAISE_KNEES_OVER_TOES", "QUADRICEPS", DifficultyLevel.BEGINNER, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 5, 15, 3, 3));
        list.add(gymRepNode("WEIGHTED_KETTLEBELL_CALF_RAISE_ON_STEP", "QUADRICEPS", DifficultyLevel.BEGINNER, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 6, 15, 3, 3));
        list.add(repNode("SEATED_REVERSE_BARBELL_CALF_RAISE_TIBIALIS", ExerciseTreeCategoryType.GYM, "QUADRICEPS", DifficultyLevel.BEGINNER, true,
                "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 7, true, 15, 3, 3, "3x15 controlled tibialis raises across 3 sessions", true,
                "Catalog primaryMuscleIds lists ['quadriceps', 'glutes'] instead of tibialis/lower-leg. Flagged for metadata cleanup."));
        list.add(gymRepNode("DONKEY_CALF_RAISE_MACHINE", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 8, 12, 4, 3));
        list.add(gymRepNode("PLATE_LOADED_STANDING_CALF_MACHINE_SINGLE_LEG", "QUADRICEPS", DifficultyLevel.INTERMEDIATE, "LOWER_LEG_CALVES", "Calves & Lower-Leg Accessories", 9, 12, 3, 3));

        // ==================== GYM: HAMSTRINGS ====================
        list.add(gymRepNode("DUMBBELL_ROMANIAN_DEADLIFT", "HAMSTRINGS", DifficultyLevel.BEGINNER, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 1, 10, 3, 3));
        list.add(gymRepNode("TRAP_BAR_ROMANIAN_DEADLIFT", "HAMSTRINGS", DifficultyLevel.BEGINNER, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 2, 10, 3, 3));
        list.add(gymRepNode("BARBELL_ROMANIAN_DEADLIFT", "HAMSTRINGS", DifficultyLevel.INTERMEDIATE, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 3, 8, 4, 4));
        list.add(gymRepNode("DUMBBELL_STIFF_LEGGED_DEADLIFT", "HAMSTRINGS", DifficultyLevel.INTERMEDIATE, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 4, 10, 3, 3));
        list.add(gymRepNode("STAGGERED_STANCE_DUMBBELL_RDL", "HAMSTRINGS", DifficultyLevel.INTERMEDIATE, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 5, 10, 3, 3));
        list.add(gymRepNode("LANDMINE_SINGLE_LEG_ROMANIAN_DEADLIFT", "HAMSTRINGS", DifficultyLevel.INTERMEDIATE, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 6, 8, 3, 3));
        list.add(gymRepNode("PAUSED_ROMANIAN_DEADLIFT", "HAMSTRINGS", DifficultyLevel.ADVANCED, "HIP_HINGE_RDL", "Romanian & Stiff-Leg Hinges", 7, 6, 4, 4));

        list.add(gymRepNode("BARBELL_GOOD_MORNING", "HAMSTRINGS", DifficultyLevel.ADVANCED, "POSTERIOR_STRENGTH", "Good Mornings & GHD", 1, 8, 3, 4));
        list.add(gymRepNode("BARBELL_ZERCHER_GOOD_MORNING", "HAMSTRINGS", DifficultyLevel.ADVANCED, "POSTERIOR_STRENGTH", "Good Mornings & GHD", 2, 8, 3, 4));
        list.add(gymRepNode("WEIGHTED_SWISS_BALL_HAMSTRING_CURL", "HAMSTRINGS", DifficultyLevel.INTERMEDIATE, "POSTERIOR_STRENGTH", "Good Mornings & GHD", 3, 12, 3, 3));
        list.add(gymRepNode("GLUTE_HAM_DEVELOPER_GHD_RAISE", "HAMSTRINGS", DifficultyLevel.ADVANCED, "POSTERIOR_STRENGTH", "Good Mornings & GHD", 4, 8, 3, 4));

        list.add(gymRepNode("SEATED_LEG_CURL_MACHINE", "HAMSTRINGS", DifficultyLevel.BEGINNER, "KNEE_FLEXION_CURLS", "Hamstring Curl Machines", 1, 12, 3, 3));
        list.add(gymRepNode("LYING_LEG_CURL_MACHINE", "HAMSTRINGS", DifficultyLevel.BEGINNER, "KNEE_FLEXION_CURLS", "Hamstring Curl Machines", 2, 12, 3, 3));
        list.add(gymRepNode("STANDING_SINGLE_LEG_CURL_MACHINE", "HAMSTRINGS", DifficultyLevel.INTERMEDIATE, "KNEE_FLEXION_CURLS", "Hamstring Curl Machines", 3, 12, 3, 3));

        // ==================== GYM: GLUTES ====================
        list.add(gymRepNode("TRAP_BAR_DEADLIFT", "GLUTES", DifficultyLevel.BEGINNER, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 1, 8, 3, 3));
        list.add(gymRepNode("CABLE_DONKEY_LEG_PRESS_LOW_STACK", "GLUTES", DifficultyLevel.BEGINNER, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 2, 12, 3, 3));
        list.add(gymRepNode("DUMBBELL_SUMO_SQUAT", "GLUTES", DifficultyLevel.BEGINNER, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 3, 12, 3, 3));
        list.add(gymRepNode("KETTLEBELL_GORILLA_SUMO_SQUAT", "GLUTES", DifficultyLevel.BEGINNER, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 4, 12, 3, 3));
        list.add(gymRepNode("SMITH_MACHINE_SUMO_SQUAT", "GLUTES", DifficultyLevel.BEGINNER, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 5, 10, 3, 3));
        list.add(gymRepNode("SEATED_HIP_ADDUCTION_MACHINE", "GLUTES", DifficultyLevel.BEGINNER, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 6, 15, 3, 3));
        list.add(gymRepNode("BARBELL_SUMO_DEADLIFT", "GLUTES", DifficultyLevel.INTERMEDIATE, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 7, 6, 4, 4));
        list.add(gymRepNode("V_SQUAT_REVERSE_HACK_SQUAT_GLUTE_FOCUS", "GLUTES", DifficultyLevel.INTERMEDIATE, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 8, 10, 4, 3));
        list.add(gymRepNode("BARBELL_BOX_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 9, 6, 4, 4));
        list.add(gymRepNode("BARBELL_PIN_BOX_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 10, 6, 4, 4));
        list.add(gymRepNode("KETTLEBELL_COSSACK_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "GLUTE_HINGE_POWER", "Deadlifts, Sumo & Box Squats", 11, 8, 3, 3));

        list.add(gymRepNode("DUMBBELL_STEP_UP", "GLUTES", DifficultyLevel.BEGINNER, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 1, 10, 3, 3));
        list.add(gymRepNode("SMITH_MACHINE_REVERSE_LUNGE", "GLUTES", DifficultyLevel.BEGINNER, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 2, 10, 3, 3));
        list.add(gymRepNode("DUMBBELL_SINGLE_LEG_BOX_STEP_DOWN", "GLUTES", DifficultyLevel.INTERMEDIATE, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 3, 10, 3, 3));
        list.add(gymRepNode("DUMBBELL_WALKING_LUNGE", "GLUTES", DifficultyLevel.INTERMEDIATE, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 4, 12, 3, 3));
        list.add(gymRepNode("BARBELL_REVERSE_LUNGE", "GLUTES", DifficultyLevel.INTERMEDIATE, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 5, 8, 3, 4));
        list.add(gymRepNode("BARBELL_LANDMINE_REVERSE_LUNGE", "GLUTES", DifficultyLevel.INTERMEDIATE, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 6, 10, 3, 3));
        list.add(gymRepNode("KETTLEBELL_SUITCASE_REVERSE_LUNGE", "GLUTES", DifficultyLevel.INTERMEDIATE, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 7, 10, 3, 3));
        list.add(gymRepNode("BARBELL_DEFICIT_DEAD_STOP_LUNGE", "GLUTES", DifficultyLevel.ADVANCED, "UNILATERAL_LUNGE", "Step-Ups & Lunges", 8, 6, 4, 4));

        list.add(gymRepNode("CABLE_SPLIT_SQUAT", "GLUTES", DifficultyLevel.BEGINNER, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 1, 12, 3, 3));
        list.add(gymRepNode("SMITH_MACHINE_SPLIT_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 2, 10, 3, 3));
        list.add(gymRepNode("DUMBBELL_SUITCASE_SQUAT_SPLIT_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 3, 10, 3, 3));
        list.add(gymRepNode("DUMBBELL_FRONT_FOOT_ELEVATED_SPLIT_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 4, 10, 3, 3));
        list.add(gymRepNode("BARBELL_FRONT_FOOT_ELEVATED_SPLIT_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 5, 8, 4, 4));
        list.add(gymRepNode("SMITH_MACHINE_BULGARIAN_SPLIT_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 6, 10, 3, 3));
        list.add(gymRepNode("DUMBBELL_BULGARIAN_SPLIT_SQUAT", "GLUTES", DifficultyLevel.INTERMEDIATE, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 7, 10, 3, 3));
        list.add(gymRepNode("KETTLEBELL_FRONT_RACK_BULGARIAN_SPLIT_SQUAT", "GLUTES", DifficultyLevel.ADVANCED, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 8, 8, 3, 4));
        list.add(gymRepNode("BARBELL_ZERCHER_SPLIT_SQUAT", "GLUTES", DifficultyLevel.ADVANCED, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 9, 6, 4, 4));
        list.add(gymRepNode("DUMBBELL_WEIGHTED_SPLIT_SQUAT_JUMP", "GLUTES", DifficultyLevel.ADVANCED, "SPLIT_SQUAT_BULGARIAN", "Split & Bulgarian Squats", 10, 6, 3, 4));

        return list;
    }

    public static List<EdgeSpec> edges() {
        List<EdgeSpec> e = new ArrayList<>();
        int order = 1;

        // ==================== CALISTHENICS EDGES ====================
        // Push-Up branching
        e.add(new EdgeSpec("PUSH_UP", "DIAMOND_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Push-Up Path", "Narrow base increases triceps & inner chest demand", order++));
        e.add(new EdgeSpec("DIAMOND_PUSH_UP", "ARCHER_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Push-Up Path", "Shifts load unilaterally toward single-arm pressing", order++));
        e.add(new EdgeSpec("PUSH_UP", "PSEUDO_PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche Pressing", "Forward shoulder lean builds anterior deltoid & straight-arm carryover", order++));
        e.add(new EdgeSpec("PSEUDO_PLANCHE_PUSH_UP", "TUCK_PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche Pressing", "Removes lower-body support for bent-arm planche pressing", order++));
        e.add(new EdgeSpec("TUCK_PLANCHE_PUSH_UP", "PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche Pressing", "Extends body lever toward full planche push-up", order++));
        e.add(new EdgeSpec("PUSH_UP", "PIKE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Overhead Push", "Elevates hips to shift pressing vertically overhead", order++));
        e.add(new EdgeSpec("TUCK_PLANCHE", "TUCK_PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.PREREQUISITE, "Planche Pressing", "Isometric tuck planche stability required before pressing", order++));
        e.add(new EdgeSpec("STRADDLE_PLANCHE", "PLANCHE_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.PREREQUISITE, "Planche Pressing", "High-level static planche leverage supports dynamic planche push-ups", order++));
        e.add(new EdgeSpec("DIAMOND_PUSH_UP", "PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.REGRESSION, "Push-Up Path", "Standard shoulder-width regression", order++));

        // Dip branching
        e.add(new EdgeSpec("DIPS", "STRAIGHT_BAR_DIP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Dip & Ring Strength", "Builds forward chest-over-bar pressing needed for Muscle-Up lockout", order++));
        e.add(new EdgeSpec("DIPS", "GYMNASTIC_RING_DIP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Dip & Ring Strength", "Adds ring instability and turnout shoulder strength", order++));
        e.add(new EdgeSpec("STRAIGHT_BAR_DIP", "GYMNASTIC_RING_DIP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.ALTERNATIVE, "Dip & Ring Strength", "Parallel advanced dip variation", order++));

        // Pull & Muscle-Up multi-branch convergence
        e.add(new EdgeSpec("PULL_UP", "HIGH_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Muscle-Up Path", "Develops explosive pulling height past sternum", order++));
        e.add(new EdgeSpec("PULL_UP", "WEIGHTED_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Vertical Pulling", "Adds external load for maximal pulling strength", order++));
        e.add(new EdgeSpec("WEIGHTED_PULL_UP", "ARCHER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Vertical Pulling", "Transfers bilateral strength into unilateral pulling", order++));
        e.add(new EdgeSpec("HIGH_PULL_UP", "ARCHER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.ALTERNATIVE, "Vertical Pulling", "Alternative explosive-to-unilateral stepping stone", order++));
        e.add(new EdgeSpec("ARCHER_PULL_UP", "ONE_ARM_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Vertical Pulling", "Removes assisting arm toward strict one-arm chin-up", order++));

        // Muscle-up: Pulling strength branch + Straight Bar Dip branch -> Muscle-Up -> Ring Muscle-Up
        e.add(new EdgeSpec("HIGH_PULL_UP", "MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Muscle-Up Path", "Explosive pull clears bar height for transition", order++));
        e.add(new EdgeSpec("PULL_UP", "MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.PREREQUISITE, "Muscle-Up Path", "Foundational strict pulling base required before muscle-up", order++));
        e.add(new EdgeSpec("STRAIGHT_BAR_DIP", "MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.PREREQUISITE, "Muscle-Up Path", "Straight bar dip strength required to press out of transition", order++));
        e.add(new EdgeSpec("MUSCLE_UP", "RING_MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Muscle-Up Path", "Transfers bar muscle-up to strict false-grip ring transition", order++));
        e.add(new EdgeSpec("GYMNASTIC_RING_DIP", "RING_MUSCLE_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.PREREQUISITE, "Muscle-Up Path", "Ring dip stability required for ring muscle-up lockout", order++));

        // Lever Pulling
        e.add(new EdgeSpec("PULL_UP", "FRONT_LEVER_RAISES", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Lever Pulling", "Straight-arm scapular depression & dynamic lever raises", order++));
        e.add(new EdgeSpec("FRONT_LEVER_RAISES", "FRONT_LEVER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Lever Pulling", "Horizontal pulling while holding lever alignment", order++));
        e.add(new EdgeSpec("TUCK_FRONT_LEVER", "FRONT_LEVER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.PREREQUISITE, "Lever Pulling", "Static front lever base needed before dynamic lever pulls", order++));
        e.add(new EdgeSpec("FRONT_LEVER", "FRONT_LEVER_PULL_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Lever Pulling", "Paired static and dynamic mastery of the Front Lever", order++));

        // Static Skills: Planche
        e.add(new EdgeSpec("WRIST_MOBILITY", "PLANCHE_LEAN", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Planche", "Prepares wrists for high-angle planche lean loading", order++));
        e.add(new EdgeSpec("FROG_STAND", "TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche", "Lifts knees off elbows into straight-arm tuck support", order++));
        e.add(new EdgeSpec("PLANCHE_LEAN", "TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche", "Builds protraction & straight-arm lean required to float feet", order++));
        e.add(new EdgeSpec("PSEUDO_PLANCHE_PUSH_UP", "TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Planche", "Dynamic bent-arm strength complementing tuck planche holds", order++));
        e.add(new EdgeSpec("TUCK_PLANCHE", "ADV_TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche", "Opens hip angle and flattens back", order++));
        e.add(new EdgeSpec("ADV_TUCK_PLANCHE", "STRADDLE_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche", "Extends legs into wide straddle leverage", order++));
        e.add(new EdgeSpec("STRADDLE_PLANCHE", "FULL_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Planche", "Closes legs together for maximum lever arm", order++));
        e.add(new EdgeSpec("STRADDLE_PLANCHE", "PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Planche", "Catalog general Planche skill milestone", order++));
        e.add(new EdgeSpec("PLANCHE", "FULL_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Planche", "Overlapping full planche reference entry", order++));
        e.add(new EdgeSpec("TUCK_PLANCHE", "PLANCHE_LEAN", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.REGRESSION, "Planche", "Regress to lean drills to refine scapular protraction", order++));

        // Static Skills: Front Lever
        e.add(new EdgeSpec("PULL_UP", "TUCK_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Front Lever", "Vertical pulling strength prepares lats for lever work", order++));
        e.add(new EdgeSpec("TUCK_FRONT_LEVER", "ADV_TUCK_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Front Lever", "Extends knees past hips to increase torque", order++));
        e.add(new EdgeSpec("ADV_TUCK_FRONT_LEVER", "ONE_LEG_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Front Lever", "Extends one leg fully while keeping opposite tucked", order++));
        e.add(new EdgeSpec("ONE_LEG_FRONT_LEVER", "STRADDLE_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Front Lever", "Extends both legs into straddle lever", order++));
        e.add(new EdgeSpec("STRADDLE_FRONT_LEVER", "FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Front Lever", "Closes legs into full straight-body Front Lever", order++));
        e.add(new EdgeSpec("FRONT_LEVER_RAISES", "ADV_TUCK_FRONT_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Front Lever", "Dynamic lever raises reinforce straight-arm lat recruitment", order++));

        // Static Skills: Back Lever
        e.add(new EdgeSpec("SHOULDER_MOBILITY", "GERMAN_HANG", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Back Lever", "Shoulder extension mobility prepares safe German hang", order++));
        e.add(new EdgeSpec("GERMAN_HANG", "SKIN_THE_CAT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Back Lever", "Adds active rotational control through full shoulder extension", order++));
        e.add(new EdgeSpec("SKIN_THE_CAT", "TUCK_BACK_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Back Lever", "Holds horizontal tucked lever parallel to ground", order++));
        e.add(new EdgeSpec("TUCK_BACK_LEVER", "FULL_BACK_LEVER", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Back Lever", "Extends full body while conditioning distal biceps tendon", order++));

        // Static Skills: Human Flag
        e.add(new EdgeSpec("VERTICAL_FLAG_HOLD", "TUCK_HUMAN_FLAG", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Human Flag", "Lowers body toward horizontal with tucked knees", order++));
        e.add(new EdgeSpec("TUCK_HUMAN_FLAG", "FULL_HUMAN_FLAG", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Human Flag", "Extends full body horizontally against side-lever load", order++));
        e.add(new EdgeSpec("PULL_UP", "VERTICAL_FLAG_HOLD", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Human Flag", "Top-arm pulling stability assists lateral flag support", order++));

        // Handstand
        e.add(new EdgeSpec("WRIST_MOBILITY", "FROG_STAND", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Handstand Prep & Balance", "Wrist conditioning prepares weight-bearing balance", order++));
        e.add(new EdgeSpec("SHOULDER_MOBILITY", "HANDSTAND", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Handstand Prep & Balance", "Open overhead shoulder angle enables stacked handstand line", order++));
        e.add(new EdgeSpec("FROG_STAND", "HANDSTAND", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Handstand Prep & Balance", "Develops hand balancing pressure before full inversion", order++));
        e.add(new EdgeSpec("PIKE_PUSH_UP", "WALL_HANDSTAND_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Handstand Pressing", "Transfers pike vertical pressing to full inverted wall HSPU", order++));
        e.add(new EdgeSpec("HANDSTAND", "WALL_HANDSTAND_PUSH_UP", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Handstand Pressing", "Inverted balance control pairs with wall HSPU strength", order++));

        // Core
        e.add(new EdgeSpec("L_SIT", "V_SIT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Compression Holds", "Deepens active pike compression and tricep/posterior shoulder push", order++));
        e.add(new EdgeSpec("AB_WHEEL", "DRAGON_FLAG", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Anti-Extension & Hanging", "Transfers anti-extension bracing into full-lever dragon flag", order++));
        e.add(new EdgeSpec("HANGING_LEG_RAISE", "DRAGON_FLAG", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Anti-Extension & Hanging", "Builds strict anterior core strength for dragon flag control", order++));
        e.add(new EdgeSpec("HANGING_LEG_RAISE", "HANGING_LEG_RAISE_STRICT_BAR", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.ALTERNATIVE, "Anti-Extension & Hanging", "Strict bar toes-to-bar variant", order++));
        e.add(new EdgeSpec("HANGING_LEG_RAISE_STRICT_BAR", "L_SIT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Compression Holds", "Active hip flexor compression directly transfers to L-sit", order++));
        e.add(new EdgeSpec("L_SIT", "TUCK_PLANCHE", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Compression Holds", "Scapular depression and core compression aid planche transition", order++));

        // Calisthenics Legs & Mobility
        e.add(new EdgeSpec("GYM_HIP_ANKLE_MOBILITY", "SQUAT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Squat to Single-Leg", "Ankle dorsiflexion improves deep squat mechanics", order++));
        e.add(new EdgeSpec("SQUAT", "PISTOL_SQUAT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Squat to Single-Leg", "Full single-leg squat requiring balance and ankle mobility", order++));
        e.add(new EdgeSpec("SQUAT", "SHRIMP_SQUAT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Squat to Single-Leg", "Single-leg quad/glute dominant squat variation", order++));
        e.add(new EdgeSpec("PISTOL_SQUAT", "SHRIMP_SQUAT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.ALTERNATIVE, "Squat to Single-Leg", "Alternative single-leg bodyweight squat progression", order++));
        e.add(new EdgeSpec("SQUAT", "NORDIC_HAMSTRING_CURL", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Posterior Chain", "Advanced eccentric/concentric bodyweight hamstring strength", order++));
        e.add(new EdgeSpec("HIP_MOBILITY", "MIDDLE_SPLIT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Mobility & Splits", "Opens hip rotation and adductor range toward middle split", order++));
        e.add(new EdgeSpec("MIDDLE_SPLIT", "FRONT_SPLIT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.NEXT, "Mobility & Splits", "Expands sagittal hamstring and hip-flexor flexibility", order++));
        e.add(new EdgeSpec("FRONT_SPLIT", "V_SIT", ExerciseTreeCategoryType.CALISTHENICS, ProgressionRelationType.RELATED_SKILL, "Mobility & Splits", "Hamstring flexibility directly unlocks high V-sit compression", order++));

        // ==================== GYM EDGES ====================
        // Chest
        e.add(new EdgeSpec("MACHINE_CHEST_PRESS", "BENCH_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Pressing Strength", "Moves from guided machine press to free-weight barbell stabilization", order++));
        e.add(new EdgeSpec("BENCH_PRESS", "INCLINE_BENCH_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Pressing Strength", "Upper-pectoral & anterior deltoid pressing angle variation", order++));
        e.add(new EdgeSpec("MACHINE_CHEST_PRESS", "INCLINE_BENCH_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Pressing Strength", "Incline free-weight barbell progression", order++));

        // Back
        e.add(new EdgeSpec("LAT_PULLDOWN", "WEIGHTED_PULL_UP", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Vertical Pulling", "Progresses vertical lat pulling to weighted closed-chain pull-ups", order++));
        e.add(new EdgeSpec("SEATED_CABLE_ROW", "BARBELL_ROW", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Horizontal Rowing", "Progresses supported cable row to free-weight hinged barbell row", order++));
        e.add(new EdgeSpec("FACE_PULL", "BARBELL_ROW", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Upper-Back & Scapular Health", "Rear delt & external rotation support heavy rowing posture", order++));

        // Shoulders, Biceps, Triceps
        e.add(new EdgeSpec("GYM_SHOULDER_MOBILITY", "OVERHEAD_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Overhead Pressing", "Prepares thoracic & glenohumeral mobility for overhead pressing", order++));
        e.add(new EdgeSpec("LATERAL_RAISE", "OVERHEAD_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Overhead Pressing", "Medial deltoid isolation pairs with compound overhead press", order++));
        e.add(new EdgeSpec("BICEP_CURL", "WEIGHTED_PULL_UP", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Biceps Strength", "Elbow-flexor strength carries over to weighted vertical pulls", order++));
        e.add(new EdgeSpec("TRICEPS_EXTENSION", "BENCH_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Triceps Strength", "Triceps lockout strength supports heavy bench & overhead pressing", order++));

        // Gym Core
        e.add(new EdgeSpec("CABLE_CRUNCH_KNEELING_ROPE", "WEIGHTED_DECLINE_CRUNCH", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Loaded Spinal Flexion", "Increases gravity resistance on loaded trunk flexion", order++));
        e.add(new EdgeSpec("WEIGHTED_DECLINE_CRUNCH", "DECLINE_BENCH_WEIGHTED_SIT_UP", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Loaded Spinal Flexion", "Full-ROM decline weighted flexion alternative", order++));
        e.add(new EdgeSpec("CABLE_STANDING_HIP_FLEXOR_MARCH_RESISTED", "HANGING_WEIGHTED_CAPTAIN_S_CHAIR_LEG_RAISE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Hip Flexion & Hanging", "Progresses resisted hip flexion to supported hanging leg raises", order++));
        e.add(new EdgeSpec("HANGING_WEIGHTED_CAPTAIN_S_CHAIR_LEG_RAISE", "HANGING_LEG_RAISE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Hip Flexion & Hanging", "Removes back pad for full hanging bar stability", order++));
        e.add(new EdgeSpec("HANGING_LEG_RAISE", "HANGING_LEG_RAISE_STRICT_BAR", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Hip Flexion & Hanging", "Strict toes-to-bar hanging variant", order++));
        e.add(new EdgeSpec("HOLLOW_FOREARM_PLANK_HOLD", "WEIGHTED_PLANK_PLATE_TRANSFER", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Bracing & Anti-Extension", "Adds anti-rotation reaching under isometric plank tension", order++));
        e.add(new EdgeSpec("WEIGHTED_PLANK_PLATE_TRANSFER", "AB_WHEEL", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Bracing & Anti-Extension", "Dynamic long-lever anti-extension rollout", order++));
        e.add(new EdgeSpec("PALLOF_ANTI_ROTATION_CABLE_PRESS", "LANDMINE_ANTI_ROTATION_BARBELL_RAINBOWS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Anti-Rotation & Obliques", "Dynamic rotational deceleration with loaded barbell", order++));
        e.add(new EdgeSpec("KNEELING_CABLE_WOODCHOPPER", "RUSSIAN_KETTLEBELL_TWIST", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Anti-Rotation & Obliques", "Free-weight rotational core alternative", order++));
        e.add(new EdgeSpec("KNEELING_CABLE_WOODCHOPPER", "KETTLEBELL_WINDMILL", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Anti-Rotation & Obliques", "Overhead loaded lateral hinge & oblique stabilization", order++));
        e.add(new EdgeSpec("ROMAN_CHAIR_BACK_SIDE_RAISE", "WEIGHTED_SIDE_PLANK_HIP_DIP", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Anti-Rotation & Obliques", "Loaded lateral trunk stability progression", order++));

        // Gym Quadriceps
        e.add(new EdgeSpec("GYM_HIP_ANKLE_MOBILITY", "GOBLET_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Free-Weight Squats", "Improves ankle dorsiflexion for upright squat depth", order++));
        e.add(new EdgeSpec("GOBLET_SQUAT", "BARBELL_BACK_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Primary bilateral barbell strength progression", order++));
        e.add(new EdgeSpec("GOBLET_SQUAT", "BARBELL_FRONT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Anterior-loaded upright quad-dominant squat", order++));
        e.add(new EdgeSpec("GOBLET_SQUAT", "HEEL_ELEVATED_DUMBBELL_FRONT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Increases knee flexion for maximal quad bias", order++));
        e.add(new EdgeSpec("HEEL_ELEVATED_DUMBBELL_FRONT_SQUAT", "DUMBBELL_GOBLET_CYCLIST_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Free-Weight Squats", "Heel-elevated quad-focused alternative", order++));
        e.add(new EdgeSpec("BARBELL_LANDMINE_SQUAT", "CABLE_PULL_THROUGH_SQUAT_FRONT_CABLE_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Free-Weight Squats", "Counterbalanced anterior-loaded squat alternative", order++));
        e.add(new EdgeSpec("BARBELL_LANDMINE_SQUAT", "KETTLEBELL_FRONT_SQUAT_DOUBLE_RACK", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Progresses to free double-kettlebell rack loading", order++));
        e.add(new EdgeSpec("BARBELL_BACK_SQUAT", "BARBELL_PAUSE_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Eliminates stretch reflex to build bottom-position strength", order++));
        e.add(new EdgeSpec("BARBELL_BACK_SQUAT", "PIN_SQUAT_CONCENTRIC_START", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Dead-stop concentric drive from rack pins", order++));
        e.add(new EdgeSpec("BARBELL_BACK_SQUAT", "SAFETY_SQUAT_BAR_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Cambered bar increases upper-back & quad demand", order++));
        e.add(new EdgeSpec("SAFETY_SQUAT_BAR_SQUAT", "HATFIELD_BARBELL_SQUAT_HAND_SUPPORTED", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Hand-supported overload SSB variation", order++));
        e.add(new EdgeSpec("BARBELL_FRONT_SQUAT", "ZERCHER_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Free-Weight Squats", "Crook-of-elbow anterior load for extreme core & quad strength", order++));
        e.add(new EdgeSpec("BARBELL_BACK_SQUAT", "BELT_SQUAT_MACHINE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Machine Squats & Extensions", "Spine-sparing heavy bilateral squat alternative", order++));

        e.add(new EdgeSpec("LEG_EXTENSION_MACHINE", "SINGLE_LEG_EXTENSION_MACHINE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Machine Squats & Extensions", "Unilateral quad isolation to resolve left-right imbalances", order++));
        e.add(new EdgeSpec("LEG_PRESS_45_DEGREE", "NARROW_LOW_LEG_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Machine Squats & Extensions", "Low-narrow foot placement maximizes knee flexion", order++));
        e.add(new EdgeSpec("NARROW_LOW_LEG_PRESS", "SINGLE_LEG_MACHINE_LEG_PRESS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Machine Squats & Extensions", "Unilateral leg press alternative", order++));
        e.add(new EdgeSpec("SMITH_MACHINE_SQUAT", "HACK_SQUAT_MACHINE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Machine Squats & Extensions", "Deep quad-biased fixed-path machine squat", order++));
        e.add(new EdgeSpec("HACK_SQUAT_MACHINE", "PENDULUM_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Machine Squats & Extensions", "Arced-path deep knee-flexion machine squat", order++));
        e.add(new EdgeSpec("HACK_SQUAT_MACHINE", "V_SQUAT_MACHINE_FACING_PAD", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Machine Squats & Extensions", "V-squat quad-oriented machine alternative", order++));
        e.add(new EdgeSpec("HACK_SQUAT_MACHINE", "SINGLE_LEG_MACHINE_HACK_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Machine Squats & Extensions", "Unilateral machine squat overload", order++));

        e.add(new EdgeSpec("BACKWARD_SLED_DRAG", "SLED_BACKWARD_MARCH", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Sled Quad Drive & Ankle Prep", "Heavy-resistance marching sled variation", order++));
        e.add(new EdgeSpec("BACKWARD_SLED_DRAG", "SLED_PUSH_PROWLER_DRIVE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Sled Quad Drive & Ankle Prep", "Forward athletic prowler drive", order++));

        e.add(new EdgeSpec("STANDING_MACHINE_CALF_RAISE", "SEATED_MACHINE_CALF_RAISE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Calves & Lower-Leg Accessories", "Bent-knee soleus-biased calf variation", order++));
        e.add(new EdgeSpec("STANDING_MACHINE_CALF_RAISE", "LEG_PRESS_CALF_RAISE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Calves & Lower-Leg Accessories", "Straight-leg sled calf variation", order++));
        e.add(new EdgeSpec("STANDING_MACHINE_CALF_RAISE", "SMITH_MACHINE_CALF_RAISE_ON_BLOCK", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Calves & Lower-Leg Accessories", "Bar-guided extended-ROM calf raise", order++));
        e.add(new EdgeSpec("SEATED_MACHINE_CALF_RAISE", "DUMBBELL_SEATED_CALF_RAISE_KNEES_OVER_TOES", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Calves & Lower-Leg Accessories", "Free-weight soleus knee-over-toe variation", order++));
        e.add(new EdgeSpec("STANDING_MACHINE_CALF_RAISE", "WEIGHTED_KETTLEBELL_CALF_RAISE_ON_STEP", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Calves & Lower-Leg Accessories", "Free-weight step calf raise", order++));
        e.add(new EdgeSpec("STANDING_MACHINE_CALF_RAISE", "DONKEY_CALF_RAISE_MACHINE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Calves & Lower-Leg Accessories", "Hip-flexed gastrocnemius stretch overload", order++));
        e.add(new EdgeSpec("STANDING_MACHINE_CALF_RAISE", "PLATE_LOADED_STANDING_CALF_MACHINE_SINGLE_LEG", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Calves & Lower-Leg Accessories", "Unilateral calf strength progression", order++));
        e.add(new EdgeSpec("SEATED_REVERSE_BARBELL_CALF_RAISE_TIBIALIS", "STANDING_MACHINE_CALF_RAISE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Calves & Lower-Leg Accessories", "Antagonist anterior tibialis balance work", order++));

        // Gym Hamstrings
        e.add(new EdgeSpec("DUMBBELL_ROMANIAN_DEADLIFT", "TRAP_BAR_ROMANIAN_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Romanian & Stiff-Leg Hinges", "Neutral-grip hex bar hinge alternative", order++));
        e.add(new EdgeSpec("DUMBBELL_ROMANIAN_DEADLIFT", "BARBELL_ROMANIAN_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Romanian & Stiff-Leg Hinges", "Primary heavy barbell hip hinge", order++));
        e.add(new EdgeSpec("DUMBBELL_ROMANIAN_DEADLIFT", "DUMBBELL_STIFF_LEGGED_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Romanian & Stiff-Leg Hinges", "Extended-knee hamstring stretch emphasis", order++));
        e.add(new EdgeSpec("DUMBBELL_ROMANIAN_DEADLIFT", "STAGGERED_STANCE_DUMBBELL_RDL", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Romanian & Stiff-Leg Hinges", "B-stance unilateral hip hinge progression", order++));
        e.add(new EdgeSpec("STAGGERED_STANCE_DUMBBELL_RDL", "LANDMINE_SINGLE_LEG_ROMANIAN_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Romanian & Stiff-Leg Hinges", "Guided single-leg landmine hinge alternative", order++));
        e.add(new EdgeSpec("BARBELL_ROMANIAN_DEADLIFT", "PAUSED_ROMANIAN_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Romanian & Stiff-Leg Hinges", "Isometric pause in stretched hamstring position", order++));
        e.add(new EdgeSpec("BARBELL_ROMANIAN_DEADLIFT", "BARBELL_GOOD_MORNING", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Good Mornings & GHD", "Long-lever posterior-chain barbell hinge", order++));
        e.add(new EdgeSpec("BARBELL_GOOD_MORNING", "BARBELL_ZERCHER_GOOD_MORNING", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Good Mornings & GHD", "Anterior-cradled good morning variation", order++));
        e.add(new EdgeSpec("SEATED_LEG_CURL_MACHINE", "LYING_LEG_CURL_MACHINE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Hamstring Curl Machines", "Prone hip-extended hamstring curl alternative", order++));
        e.add(new EdgeSpec("SEATED_LEG_CURL_MACHINE", "STANDING_SINGLE_LEG_CURL_MACHINE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Hamstring Curl Machines", "Unilateral standing knee-flexion curl", order++));
        e.add(new EdgeSpec("LYING_LEG_CURL_MACHINE", "WEIGHTED_SWISS_BALL_HAMSTRING_CURL", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Good Mornings & GHD", "Closed-chain hip-bridge hamstring curl", order++));
        e.add(new EdgeSpec("WEIGHTED_SWISS_BALL_HAMSTRING_CURL", "GLUTE_HAM_DEVELOPER_GHD_RAISE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Good Mornings & GHD", "High-tension simultaneous hip & knee hamstring extension", order++));

        // Gym Glutes
        e.add(new EdgeSpec("TRAP_BAR_DEADLIFT", "BARBELL_SUMO_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Deadlifts, Sumo & Box Squats", "Wide-stance barbell pull emphasizing glutes & adductors", order++));
        e.add(new EdgeSpec("CABLE_DONKEY_LEG_PRESS_LOW_STACK", "V_SQUAT_REVERSE_HACK_SQUAT_GLUTE_FOCUS", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Deadlifts, Sumo & Box Squats", "Heavy hip-dominant reverse-hack glute loading", order++));
        e.add(new EdgeSpec("DUMBBELL_SUMO_SQUAT", "KETTLEBELL_GORILLA_SUMO_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Deadlifts, Sumo & Box Squats", "Double-kettlebell wide-stance sumo alternative", order++));
        e.add(new EdgeSpec("DUMBBELL_SUMO_SQUAT", "SMITH_MACHINE_SUMO_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Deadlifts, Sumo & Box Squats", "Guided-track sumo squat alternative", order++));
        e.add(new EdgeSpec("DUMBBELL_SUMO_SQUAT", "KETTLEBELL_COSSACK_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Deadlifts, Sumo & Box Squats", "Lateral single-leg hip mobility & adductor/glute strength", order++));
        e.add(new EdgeSpec("SEATED_HIP_ADDUCTION_MACHINE", "BARBELL_SUMO_DEADLIFT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.RELATED_SKILL, "Deadlifts, Sumo & Box Squats", "Adductor strength supports wide-stance hip extension", order++));
        e.add(new EdgeSpec("TRAP_BAR_DEADLIFT", "BARBELL_BOX_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Deadlifts, Sumo & Box Squats", "Posterior-chain sit-back box squat", order++));
        e.add(new EdgeSpec("BARBELL_BOX_SQUAT", "BARBELL_PIN_BOX_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Deadlifts, Sumo & Box Squats", "Dead-stop pin-assisted box squat variation", order++));

        e.add(new EdgeSpec("DUMBBELL_STEP_UP", "DUMBBELL_SINGLE_LEG_BOX_STEP_DOWN", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Step-Ups & Lunges", "Eccentric-focused single-leg glute & knee control", order++));
        e.add(new EdgeSpec("SMITH_MACHINE_REVERSE_LUNGE", "DUMBBELL_WALKING_LUNGE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Step-Ups & Lunges", "Dynamic free-weight walking lunge progression", order++));
        e.add(new EdgeSpec("SMITH_MACHINE_REVERSE_LUNGE", "BARBELL_REVERSE_LUNGE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Step-Ups & Lunges", "Heavy barbell-loaded reverse lunge", order++));
        e.add(new EdgeSpec("BARBELL_REVERSE_LUNGE", "BARBELL_LANDMINE_REVERSE_LUNGE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Step-Ups & Lunges", "Angled landmine arc reverse lunge alternative", order++));
        e.add(new EdgeSpec("BARBELL_REVERSE_LUNGE", "KETTLEBELL_SUITCASE_REVERSE_LUNGE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Step-Ups & Lunges", "Offset anti-lateral-flexion kettlebell lunge alternative", order++));
        e.add(new EdgeSpec("BARBELL_REVERSE_LUNGE", "BARBELL_DEFICIT_DEAD_STOP_LUNGE", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Step-Ups & Lunges", "Extended-ROM deficit dead-stop barbell lunge", order++));

        e.add(new EdgeSpec("CABLE_SPLIT_SQUAT", "SMITH_MACHINE_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Split & Bulgarian Squats", "Heavier guided split squat loading", order++));
        e.add(new EdgeSpec("SMITH_MACHINE_SPLIT_SQUAT", "DUMBBELL_SUITCASE_SQUAT_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Split & Bulgarian Squats", "Asymmetric core-challenging split squat alternative", order++));
        e.add(new EdgeSpec("SMITH_MACHINE_SPLIT_SQUAT", "DUMBBELL_FRONT_FOOT_ELEVATED_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Split & Bulgarian Squats", "Deep hip flexion FFE split squat", order++));
        e.add(new EdgeSpec("DUMBBELL_FRONT_FOOT_ELEVATED_SPLIT_SQUAT", "BARBELL_FRONT_FOOT_ELEVATED_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Split & Bulgarian Squats", "Barbell-loaded FFE split squat alternative", order++));
        e.add(new EdgeSpec("DUMBBELL_FRONT_FOOT_ELEVATED_SPLIT_SQUAT", "DUMBBELL_WEIGHTED_SPLIT_SQUAT_JUMP", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Split & Bulgarian Squats", "Explosive power-oriented split squat jump", order++));
        e.add(new EdgeSpec("CABLE_SPLIT_SQUAT", "SMITH_MACHINE_BULGARIAN_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Split & Bulgarian Squats", "Rear-foot-elevated glute/quad overload", order++));
        e.add(new EdgeSpec("SMITH_MACHINE_BULGARIAN_SPLIT_SQUAT", "DUMBBELL_BULGARIAN_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Split & Bulgarian Squats", "Free-weight dumbbell Bulgarian split squat", order++));
        e.add(new EdgeSpec("DUMBBELL_BULGARIAN_SPLIT_SQUAT", "KETTLEBELL_FRONT_RACK_BULGARIAN_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.NEXT, "Split & Bulgarian Squats", "Double-rack upright torso challenge", order++));
        e.add(new EdgeSpec("KETTLEBELL_FRONT_RACK_BULGARIAN_SPLIT_SQUAT", "BARBELL_ZERCHER_SPLIT_SQUAT", ExerciseTreeCategoryType.GYM, ProgressionRelationType.ALTERNATIVE, "Split & Bulgarian Squats", "Heavy anterior-loaded Zercher split squat", order++));

        return e;
    }

    public static List<MissingNodeSpec> missingNodes() {
        return List.of(
                // Calisthenics Pull
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "PULL", "VERTICAL_PULL",
                        "Australian Pull-Up (Inverted Row)", DifficultyLevel.BEGINNER, null, "PULL_UP",
                        "Foundational horizontal pulling step before strict pull-ups is absent from the server catalog."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "PULL", "VERTICAL_PULL",
                        "Assisted / Negative Pull-Up", DifficultyLevel.BEGINNER, null, "PULL_UP",
                        "Bridge between beginner horizontal rows and strict unassisted Pull-Up is missing."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "PULL", "MUSCLE_UP_PATH",
                        "Chest-to-Bar Pull-Up", DifficultyLevel.INTERMEDIATE, "PULL_UP", "HIGH_PULL_UP",
                        "Intermediate pulling height bridge between Pull-Up and High Pull-Up is missing."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "PULL", "MUSCLE_UP_PATH",
                        "Muscle-Up Negative / Transition Drill", DifficultyLevel.INTERMEDIATE, "HIGH_PULL_UP", "MUSCLE_UP",
                        "Dedicated bar transition exercise before strict Muscle-Up is missing."),

                // Calisthenics Push
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "PUSH", "PUSH_UP_BRANCH",
                        "Incline Push-Up", DifficultyLevel.BEGINNER, null, "PUSH_UP",
                        "Beginner regression prior to floor Push-Up is absent from the catalog."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "PUSH", "PUSH_UP_BRANCH",
                        "One-Arm Push-Up", DifficultyLevel.ELITE, "ARCHER_PUSH_UP", null,
                        "Elite unilateral pushing pinnacle after Archer Push-Up is absent."),

                // Calisthenics Handstand
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", "HANDSTAND_BALANCE",
                        "Chest-to-Wall Handstand Hold", DifficultyLevel.BEGINNER, "FROG_STAND", "HANDSTAND",
                        "Wall-supported line drill between Frog Stand and Freestanding Handstand is missing."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "HANDSTAND", "HANDSTAND_STRENGTH",
                        "Freestanding Handstand Push-Up", DifficultyLevel.ELITE, "WALL_HANDSTAND_PUSH_UP", null,
                        "True freestanding HSPU is absent; only WALL_HANDSTAND_PUSH_UP exists in catalog."),

                // Calisthenics Static Skills
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", "BACK_LEVER",
                        "Straddle Back Lever", DifficultyLevel.ADVANCED, "TUCK_BACK_LEVER", "FULL_BACK_LEVER",
                        "Intermediate leverage bridge between Tuck Back Lever and Full Back Lever is absent."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "STATIC_SKILLS", "HUMAN_FLAG",
                        "Straddle Human Flag", DifficultyLevel.ADVANCED, "TUCK_HUMAN_FLAG", "FULL_HUMAN_FLAG",
                        "Bridge progression between Tuck Human Flag and Full Human Flag is absent."),

                // Calisthenics Core
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "CORE", "COMPRESSION_HOLDS",
                        "Hollow Body Hold", DifficultyLevel.BEGINNER, null, "L_SIT",
                        "Foundational beginner core bracing progression for Calisthenics Core is absent."),
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "CORE", "COMPRESSION_HOLDS",
                        "Tuck L-Sit", DifficultyLevel.BEGINNER, null, "L_SIT",
                        "Beginner progression before full straight-leg L-Sit is absent."),

                // Calisthenics Legs
                new MissingNodeSpec(ExerciseTreeCategoryType.CALISTHENICS, "LEGS", "SINGLE_LEG_SQUAT",
                        "Assisted Pistol Squat", DifficultyLevel.INTERMEDIATE, "SQUAT", "PISTOL_SQUAT",
                        "Intermediate bridge between Bodyweight Squat and full Pistol Squat is absent."),

                // Gym sparse categories
                new MissingNodeSpec(ExerciseTreeCategoryType.GYM, "BICEPS", "ELBOW_FLEXION",
                        "Incline Dumbbell Curl", DifficultyLevel.INTERMEDIATE, "BICEP_CURL", null,
                        "Gym Biceps currently contains only 1 exercise (Standing Barbell Bicep Curl)."),
                new MissingNodeSpec(ExerciseTreeCategoryType.GYM, "TRICEPS", "ELBOW_EXTENSION",
                        "Overhead Triceps Extension / Close-Grip Bench", DifficultyLevel.INTERMEDIATE, "TRICEPS_EXTENSION", null,
                        "Gym Triceps currently contains only 1 exercise (Cable Triceps Pushdown).")
        );
    }

    private static NodeSpec repNode(
            String code,
            ExerciseTreeCategoryType type,
            String subcategory,
            DifficultyLevel difficulty,
            boolean validated,
            String branchKey,
            String branchLabel,
            int branchOrder,
            boolean primary,
            int targetReps,
            int targetSets,
            int minSessions,
            String summary,
            boolean manualReview,
            String reviewNotes
    ) {
        return new NodeSpec(
                code, type, subcategory, difficulty, validated, branchKey, branchLabel, branchOrder, primary,
                MasteryCriteriaType.REPS, targetReps, targetSets, null, null, minSessions, summary, manualReview, reviewNotes
        );
    }

    private static NodeSpec holdNode(
            String code,
            ExerciseTreeCategoryType type,
            String subcategory,
            DifficultyLevel difficulty,
            boolean validated,
            String branchKey,
            String branchLabel,
            int branchOrder,
            boolean primary,
            int targetHoldSeconds,
            int targetSets,
            int minSessions,
            String summary,
            boolean manualReview,
            String reviewNotes
    ) {
        return new NodeSpec(
                code, type, subcategory, difficulty, validated, branchKey, branchLabel, branchOrder, primary,
                MasteryCriteriaType.HOLD_SECONDS, null, targetSets, targetHoldSeconds, null, minSessions, summary, manualReview, reviewNotes
        );
    }

    private static NodeSpec weightedNode(
            String code,
            ExerciseTreeCategoryType type,
            String subcategory,
            DifficultyLevel difficulty,
            boolean validated,
            String branchKey,
            String branchLabel,
            int branchOrder,
            boolean primary,
            int targetReps,
            int targetSets,
            double targetWeightKg,
            int minSessions,
            String summary,
            boolean manualReview,
            String reviewNotes
    ) {
        return new NodeSpec(
                code, type, subcategory, difficulty, validated, branchKey, branchLabel, branchOrder, primary,
                MasteryCriteriaType.WEIGHTED_LOAD, targetReps, targetSets, null, targetWeightKg, minSessions, summary, manualReview, reviewNotes
        );
    }

    private static NodeSpec gymRepNode(
            String code,
            String subcategory,
            DifficultyLevel difficulty,
            String branchKey,
            String branchLabel,
            int branchOrder,
            int targetReps,
            int targetSets,
            int minSessions
    ) {
        String summary = targetSets + "x" + targetReps + " controlled working sets across " + minSessions + " completed workouts";
        return new NodeSpec(
                code, ExerciseTreeCategoryType.GYM, subcategory, difficulty, true, branchKey, branchLabel, branchOrder, true,
                MasteryCriteriaType.REPS, targetReps, targetSets, null, null, minSessions, summary, false, null
        );
    }
}
