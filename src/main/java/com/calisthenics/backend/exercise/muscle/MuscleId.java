package com.calisthenics.backend.exercise.muscle;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Getter
public enum MuscleId {

    CHEST(
            "chest",
            "Chest",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("chest_left", "chest_right"),
            1
    ),
    FRONT_DELTOIDS(
            "front_deltoids",
            "Front Deltoids",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("front_deltoids_left", "front_deltoids_right"),
            2
    ),
    LATERAL_DELTOIDS(
            "lateral_deltoids",
            "Lateral Deltoids",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT, MuscleBodyView.BACK),
            List.of("lateral_deltoids_left", "lateral_deltoids_right"),
            3
    ),
    REAR_DELTOIDS(
            "rear_deltoids",
            "Rear Deltoids",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("rear_deltoids_left", "rear_deltoids_right"),
            4
    ),
    BICEPS(
            "biceps",
            "Biceps",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("biceps_left", "biceps_right"),
            5
    ),
    TRICEPS(
            "triceps",
            "Triceps",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("triceps_left", "triceps_right"),
            6
    ),
    FOREARMS(
            "forearms",
            "Forearms",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT, MuscleBodyView.BACK),
            List.of("forearms_left", "forearms_right"),
            7
    ),
    UPPER_TRAPEZIUS(
            "upper_trapezius",
            "Upper Trapezius",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.FRONT, MuscleBodyView.BACK),
            List.of("upper_trapezius_left", "upper_trapezius_right"),
            8
    ),
    MIDDLE_TRAPEZIUS(
            "middle_trapezius",
            "Middle Trapezius",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("middle_trapezius_left", "middle_trapezius_right"),
            9
    ),
    LOWER_TRAPEZIUS(
            "lower_trapezius",
            "Lower Trapezius",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("lower_trapezius_left", "lower_trapezius_right"),
            10
    ),
    LATISSIMUS_DORSI(
            "latissimus_dorsi",
            "Latissimus Dorsi",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.FRONT, MuscleBodyView.BACK),
            List.of("latissimus_dorsi_left", "latissimus_dorsi_right"),
            11
    ),
    TERES_MAJOR(
            "teres_major",
            "Teres Major",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("teres_major_left", "teres_major_right"),
            12
    ),
    RHOMBOIDS(
            "rhomboids",
            "Rhomboids",
            MuscleRegion.UPPER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("rhomboids_left", "rhomboids_right"),
            13
    ),
    ERECTOR_SPINAE(
            "erector_spinae",
            "Erector Spinae",
            MuscleRegion.CORE,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("erector_spinae_left", "erector_spinae_right"),
            14
    ),
    RECTUS_ABDOMINIS(
            "rectus_abdominis",
            "Rectus Abdominis",
            MuscleRegion.CORE,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("rectus_abdominis_left", "rectus_abdominis_right"),
            15
    ),
    OBLIQUES(
            "obliques",
            "Obliques",
            MuscleRegion.CORE,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("obliques_left", "obliques_right"),
            16
    ),
    TRANSVERSE_ABDOMINIS(
            "transverse_abdominis",
            "Transverse Abdominis",
            MuscleRegion.CORE,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("transverse_abdominis_left", "transverse_abdominis_right"),
            17
    ),
    GLUTES(
            "glutes",
            "Glutes",
            MuscleRegion.LOWER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("glutes_left", "glutes_right"),
            18
    ),
    QUADRICEPS(
            "quadriceps",
            "Quadriceps",
            MuscleRegion.LOWER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("quadriceps_left", "quadriceps_right"),
            19
    ),
    HAMSTRINGS(
            "hamstrings",
            "Hamstrings",
            MuscleRegion.LOWER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.BACK),
            List.of("hamstrings_left", "hamstrings_right"),
            20
    ),
    ADDUCTORS(
            "adductors",
            "Hip Adductors",
            MuscleRegion.LOWER_BODY,
            MuscleBodyView.FRONT,
            List.of(MuscleBodyView.FRONT),
            List.of("adductors_left", "adductors_right"),
            21
    ),
    CALVES(
            "calves",
            "Calves",
            MuscleRegion.LOWER_BODY,
            MuscleBodyView.BACK,
            List.of(MuscleBodyView.FRONT, MuscleBodyView.BACK),
            List.of("calves_left", "calves_right"),
            22
    );

    private final String id;
    private final String displayName;
    private final MuscleRegion region;
    private final MuscleBodyView primaryView;
    private final List<MuscleBodyView> views;
    private final List<String> svgElementIds;
    private final int displayOrder;

    private static final Map<String, MuscleId> BY_ID;
    private static final Map<String, List<MuscleId>> EXACT_ALIAS_MAP;
    private static final List<Map.Entry<String, List<MuscleId>>> ORDERED_PHRASE_ALIASES;
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[,;/\\n|+&()]+|\\s+and\\s+", Pattern.CASE_INSENSITIVE);

    static {
        Map<String, MuscleId> idMap = new LinkedHashMap<>();
        for (MuscleId value : values()) {
            idMap.put(value.id, value);
        }
        BY_ID = Collections.unmodifiableMap(idMap);

        Map<String, List<MuscleId>> aliases = new LinkedHashMap<>();
        for (MuscleId value : values()) {
            aliases.put(normalizePhrase(value.id), List.of(value));
            aliases.put(normalizePhrase(value.displayName), List.of(value));
        }

        // Chest
        registerAlias(aliases, List.of(CHEST),
                "pectorals", "pectoral", "pecs", "pec", "pectoralis major", "pectoralis minor",
                "upper chest", "lower chest", "mid chest", "inner chest", "clavicular chest", "sternal chest");

        // Front Deltoids
        registerAlias(aliases, List.of(FRONT_DELTOIDS),
                "anterior deltoids", "anterior deltoid", "anterior delts", "anterior delt",
                "front deltoid", "front delts", "front delt", "front shoulder");

        // Lateral Deltoids
        registerAlias(aliases, List.of(LATERAL_DELTOIDS),
                "lateral deltoid", "lateral delts", "lateral delt",
                "side deltoids", "side deltoid", "side delts", "side delt",
                "medial deltoids", "medial deltoid", "medial delts",
                "middle deltoids", "middle deltoid", "middle delts");

        // Rear Deltoids
        registerAlias(aliases, List.of(REAR_DELTOIDS),
                "posterior deltoids", "posterior deltoid", "posterior delts", "posterior delt",
                "rear deltoid", "rear delts", "rear delt", "rear shoulder", "rotator cuff");

        // Generic Shoulders / Delts
        registerAlias(aliases, List.of(FRONT_DELTOIDS, LATERAL_DELTOIDS),
                "shoulders", "shoulder", "deltoids", "deltoid", "delts", "delt");

        // Arms & Forearms
        registerAlias(aliases, List.of(BICEPS),
                "bicep", "biceps brachii", "brachialis", "arms");
        registerAlias(aliases, List.of(TRICEPS),
                "tricep", "triceps brachii", "back of arms");
        registerAlias(aliases, List.of(FOREARMS),
                "forearm", "grip", "grips", "grip strength", "brachioradialis",
                "wrist flexors", "wrist extensors", "wrists", "wrist");

        // Trapezius
        registerAlias(aliases, List.of(UPPER_TRAPEZIUS),
                "upper traps", "upper trap");
        registerAlias(aliases, List.of(MIDDLE_TRAPEZIUS),
                "mid trapezius", "middle traps", "mid traps", "middle trap", "mid trap");
        registerAlias(aliases, List.of(LOWER_TRAPEZIUS),
                "lower traps", "lower trap", "low traps");
        registerAlias(aliases, List.of(UPPER_TRAPEZIUS, MIDDLE_TRAPEZIUS),
                "traps", "trap", "trapezius");

        // Back / Lats / Rhomboids / Teres Major
        registerAlias(aliases, List.of(LATISSIMUS_DORSI),
                "lats", "lat", "latissimus", "wings");
        registerAlias(aliases, List.of(TERES_MAJOR),
                "teres", "upper lats");
        registerAlias(aliases, List.of(RHOMBOIDS),
                "rhomboid", "scapula", "scapular", "scapular retractors", "scapular stabilizers");
        registerAlias(aliases, List.of(RHOMBOIDS, MIDDLE_TRAPEZIUS),
                "upper back", "mid back");
        registerAlias(aliases, List.of(LATISSIMUS_DORSI, RHOMBOIDS, MIDDLE_TRAPEZIUS),
                "back", "explosive pulling");

        // Core
        registerAlias(aliases, List.of(ERECTOR_SPINAE),
                "lower back", "low back", "spinal erectors", "lumbar", "erectors");
        registerAlias(aliases, List.of(RECTUS_ABDOMINIS),
                "abs", "abdominals", "abdominal", "upper abs", "lower abs", "six pack", "hip flexors");
        registerAlias(aliases, List.of(OBLIQUES),
                "oblique", "side abs", "external obliques", "internal obliques");
        registerAlias(aliases, List.of(TRANSVERSE_ABDOMINIS),
                "tva", "deep core", "lower core", "transverse abs", "transversus abdominis");
        registerAlias(aliases, List.of(RECTUS_ABDOMINIS, TRANSVERSE_ABDOMINIS),
                "core", "midsection", "trunk");

        // Lower Body
        registerAlias(aliases, List.of(GLUTES),
                "glute", "gluteus maximus", "gluteus medius", "gluteus minimus", "gluteals", "buttocks", "hips");
        registerAlias(aliases, List.of(QUADRICEPS),
                "quads", "quad", "quadriceps femoris", "rectus femoris", "vastus lateralis", "vastus medialis", "vastus intermedius", "front thighs", "thighs");
        registerAlias(aliases, List.of(HAMSTRINGS),
                "hamstring", "hams", "biceps femoris", "semitendinosus", "semimembranosus", "rear thighs", "posterior thigh", "back thighs");
        registerAlias(aliases, List.of(HAMSTRINGS, GLUTES, ERECTOR_SPINAE),
                "posterior chain");
        registerAlias(aliases, List.of(ADDUCTORS),
                "adductor", "inner thighs", "inner thigh", "groin");
        registerAlias(aliases, List.of(CALVES),
                "calf", "gastrocnemius", "soleus", "lower legs", "shins");

        // Full body fallback phrase
        registerAlias(aliases, List.of(CHEST, LATISSIMUS_DORSI, FRONT_DELTOIDS, RECTUS_ABDOMINIS, QUADRICEPS, GLUTES),
                "full body");

        EXACT_ALIAS_MAP = Collections.unmodifiableMap(aliases);

        List<Map.Entry<String, List<MuscleId>>> sortedEntries = new ArrayList<>(aliases.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        ORDERED_PHRASE_ALIASES = Collections.unmodifiableList(sortedEntries);
    }

    MuscleId(
            String id,
            String displayName,
            MuscleRegion region,
            MuscleBodyView primaryView,
            List<MuscleBodyView> views,
            List<String> svgElementIds,
            int displayOrder
    ) {
        this.id = id;
        this.displayName = displayName;
        this.region = region;
        this.primaryView = primaryView;
        this.views = views;
        this.svgElementIds = svgElementIds;
        this.displayOrder = displayOrder;
    }

    public MuscleGroupDto toDto() {
        return MuscleGroupDto.builder()
                .id(id)
                .displayName(displayName)
                .region(region)
                .primaryView(primaryView)
                .views(views)
                .svgElementIds(svgElementIds)
                .displayOrder(displayOrder)
                .build();
    }

    public ExerciseMuscleTargetDto toTargetDto(MuscleRole role, int targetDisplayOrder) {
        return ExerciseMuscleTargetDto.builder()
                .muscleId(id)
                .displayName(displayName)
                .role(role)
                .region(region)
                .primaryView(primaryView)
                .views(views)
                .svgElementIds(svgElementIds)
                .displayOrder(targetDisplayOrder)
                .build();
    }

    public static Optional<MuscleId> fromId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ID.get(rawId.trim().toLowerCase(Locale.ROOT)));
    }

    public static List<MuscleId> resolveAliases(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        Set<MuscleId> resolved = new LinkedHashSet<>();
        String[] tokens = TOKEN_SPLITTER.split(rawText);
        for (String token : tokens) {
            String normalized = normalizePhrase(token);
            if (normalized.isEmpty()) {
                continue;
            }
            List<MuscleId> exact = EXACT_ALIAS_MAP.get(normalized);
            if (exact != null) {
                resolved.addAll(exact);
                continue;
            }
            // Fallback: scan longest multi-word aliases at word boundaries within descriptive token
            String padded = " " + normalized + " ";
            for (Map.Entry<String, List<MuscleId>> entry : ORDERED_PHRASE_ALIASES) {
                String phrase = entry.getKey();
                if (phrase.length() < 3) {
                    continue;
                }
                if (padded.contains(" " + phrase + " ")) {
                    resolved.addAll(entry.getValue());
                    padded = padded.replace(" " + phrase + " ", " ");
                }
            }
        }
        return List.copyOf(resolved);
    }

    public static List<MuscleId> orderedValues() {
        return Arrays.asList(values());
    }

    private static void registerAlias(Map<String, List<MuscleId>> map, List<MuscleId> targets, String... phrases) {
        for (String phrase : phrases) {
            map.put(normalizePhrase(phrase), targets);
        }
    }

    private static String normalizePhrase(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
