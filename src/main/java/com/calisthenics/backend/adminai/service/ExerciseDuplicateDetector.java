package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic multi-layer duplicate detector protecting the exercise catalog
 * against exact, formatted, slug/code, alias, and semantic movement duplicates.
 */
@Service
@RequiredArgsConstructor
public class ExerciseDuplicateDetector {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern SEPARATOR_LIST = Pattern.compile("[,|;/]+");

    private static final Map<String, String> PLURAL_TAIL_MAP = Map.ofEntries(
            Map.entry("ups", "up"),
            Map.entry("squats", "squat"),
            Map.entry("lunges", "lunge"),
            Map.entry("rows", "row"),
            Map.entry("curls", "curl"),
            Map.entry("presses", "press"),
            Map.entry("raises", "raise"),
            Map.entry("bridges", "bridge"),
            Map.entry("dips", "dip"),
            Map.entry("planks", "plank"),
            Map.entry("holds", "hold"),
            Map.entry("crunches", "crunch"),
            Map.entry("twists", "twist"),
            Map.entry("extensions", "extension"),
            Map.entry("flyes", "fly"),
            Map.entry("flies", "fly"),
            Map.entry("pulldowns", "pulldown"),
            Map.entry("thrusts", "thrust"),
            Map.entry("steps", "step"),
            Map.entry("stepups", "stepup"),
            Map.entry("jumps", "jump"),
            Map.entry("shrugs", "shrug"),
            Map.entry("swings", "swing"),
            Map.entry("kicks", "kick"),
            Map.entry("kickbacks", "kickback"),
            Map.entry("taps", "tap"),
            Map.entry("walks", "walk"),
            Map.entry("drills", "drill"),
            Map.entry("stretches", "stretch"),
            Map.entry("rotations", "rotation"),
            Map.entry("circles", "circle"),
            Map.entry("lifts", "lift"),
            Map.entry("pulls", "pull"),
            Map.entry("downs", "down"),
            Map.entry("backs", "back"),
            Map.entry("slides", "slide"),
            Map.entry("levers", "lever"),
            Map.entry("flags", "flag"),
            Map.entry("sits", "sit"),
            Map.entry("carries", "carry"),
            Map.entry("pushes", "push"),
            Map.entry("burpees", "burpee"),
            Map.entry("climbers", "climber"),
            Map.entry("jumpsquats", "jumpsquat"),
            Map.entry("rollouts", "rollout"),
            Map.entry("deadlifts", "deadlift")
    );

    private static final Set<String> OPTIONAL_FILLER_TOKENS = Set.of(
            "standard", "regular", "classic", "basic", "conventional", "strict", "control", "controlled"
    );

    private static final Set<String> EQUIPMENT_TOKENS = Set.of(
            "barbell", "dumbbell", "dumbbells", "kettlebell", "kettlebells", "cable", "machine",
            "smith", "ez", "trap", "hex", "band", "banded", "resistance", "ring", "rings",
            "parallettes", "parallette", "trx", "suspension", "box", "bench", "landmine",
            "medicine", "ball", "swiss", "stability", "bosu", "ab", "wheel", "rope", "sled",
            "prowler", "plate", "dip", "belt", "wall", "table", "floor", "bar", "weighted"
    );

    private static final Set<String> DISTINCT_ANGLE_AND_VARIANT_MODIFIERS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "incline", "decline", "flat", "close", "wide", "narrow", "diamond", "triangle",
            "pike", "archer", "pseudo", "planche", "hindu", "dive", "bomber", "explosive",
            "plyo", "plyometric", "clapping", "knuckle", "fingertip", "spiderman", "typewriter",
            "assisted", "negative", "eccentric", "paused", "pause", "tempo", "isometric", "hold",
            "single", "one", "unilateral", "alternating", "bulgarian", "cossack", "sissy",
            "shrimp", "pistol", "dragon", "goblet", "front", "overhead", "zercher", "hack",
            "jefferson", "sumo", "romanian", "stiff", "nordic", "reverse", "seated", "standing",
            "kneeling", "half", "hanging", "lying", "bent", "chest", "supported", "preacher",
            "concentration", "hammer", "zottman", "spider", "arnold", "lateral", "rear",
            "upright", "face", "tuck", "straddle", "full", "adv", "advanced", "commando",
            "mixed", "neutral", "chin", "pull", "scapular", "scapula", "hollow", "v", "l",
            "diagonal", "split", "walking", "curtsy", "skater", "step", "deficit",
            "elevated", "heel", "calf", "tibialis", "donkey", "frog", "clamshell",
            "fire", "hydrant", "monster", "kickstand", "cyclist", "pendulum", "belt", "safety",
            "kipping", "muscle", "ring", "russian", "pallof", "dead", "bird", "bicycle",
            "windmill", "turkish", "farmer", "suitcase", "waiter",
            "thruster", "clean", "snatch", "jerk", "sprint", "bear", "crab", "inchworm",
            "duck", "broad", "tuckjump", "boxjump", "weighted", "90",
            "scott", "pendlay", "meadows", "kroc", "yates", "t", "seal", "gorilla",
            "facepull", "pullover", "crossbody", "bradford", "z",
            "floor", "sliding", "slider", "towel", "valslide", "harop", "matrix", "skin"
    )));

    private static final Map<String, String> CANONICAL_ALIAS_MAP = Map.ofEntries(
            Map.entry("barbell back squat", "barbell squat"),
            Map.entry("back squat", "barbell squat"),
            Map.entry("barbell squat", "barbell squat"),
            Map.entry("bodyweight squat", "air squat"),
            Map.entry("air squat", "air squat"),
            Map.entry("bodyweight push up", "push up"),
            Map.entry("floor push up", "push up"),
            Map.entry("overhand pull up", "pull up"),
            Map.entry("pronated pull up", "pull up"),
            Map.entry("underhand pull up", "chin up"),
            Map.entry("supinated pull up", "chin up"),
            Map.entry("australian pull up", "inverted row"),
            Map.entry("bodyweight row", "inverted row"),
            Map.entry("single leg squat", "pistol squat"),
            Map.entry("pistol squat", "pistol squat"),
            Map.entry("rdl", "romanian deadlift"),
            Map.entry("barbell rdl", "barbell romanian deadlift"),
            Map.entry("dumbbell rdl", "dumbbell romanian deadlift"),
            Map.entry("ohp", "barbell overhead press"),
            Map.entry("military press", "barbell overhead press"),
            Map.entry("barbell military press", "barbell overhead press"),
            Map.entry("standing barbell overhead press", "barbell overhead press"),
            Map.entry("flat barbell bench press", "barbell bench press"),
            Map.entry("barbell flat bench press", "barbell bench press"),
            Map.entry("l sit hold", "l sit"),
            Map.entry("floor l sit", "l sit"),
            Map.entry("hollow body hold", "hollow hold"),
            Map.entry("hollow hold", "hollow hold"),
            Map.entry("hip bridge", "glute bridge"),
            Map.entry("bodyweight glute bridge", "glute bridge"),
            Map.entry("skullcrusher", "skull crusher"),
            Map.entry("skull crusher", "skull crusher"),
            Map.entry("ez bar skullcrusher", "ez bar skull crusher"),
            Map.entry("tricep pushdown", "cable triceps pushdown"),
            Map.entry("cable tricep pushdown", "cable triceps pushdown"),
            Map.entry("triceps pushdown", "cable triceps pushdown")
    );

    public enum MatchType {
        UNIQUE,
        EXACT_NAME,
        CODE_COLLISION,
        ALIAS_MATCH,
        SEMANTIC_DUPLICATE,
        UNCERTAIN_REVIEW
    }

    public record DuplicateCheckResult(
            boolean duplicateOrUncertain,
            MatchType matchType,
            Long matchedExerciseId,
            String matchedExerciseName,
            String reason
    ) {
        public static DuplicateCheckResult unique() {
            return new DuplicateCheckResult(false, MatchType.UNIQUE, null, null, null);
        }
    }

    public record IndexedExercise(
            Long id,
            String name,
            String code,
            String normalizedName,
            String canonicalKey,
            ExerciseCategory category,
            ExerciseTrainingType trainingType,
            Set<String> aliasesNormalized,
            MovementSignature signature
    ) {}

    public record MovementSignature(
            String canonicalName,
            Set<String> equipmentTokens,
            Set<String> differentiatingModifiers,
            Set<String> coreTokens
    ) {}

    public static final class CatalogSnapshot {
        private final Map<String, IndexedExercise> byNormalizedName = new LinkedHashMap<>();
        private final Map<String, IndexedExercise> byCanonicalKey = new LinkedHashMap<>();
        private final Map<String, IndexedExercise> byCode = new LinkedHashMap<>();
        private final Map<String, IndexedExercise> byAlias = new LinkedHashMap<>();
        private final Map<String, IndexedExercise> byStrictSignatureKey = new LinkedHashMap<>();
        private final List<IndexedExercise> allEntries = new ArrayList<>();

        public void register(
                Long id,
                String name,
                String code,
                ExerciseCategory category,
                ExerciseTrainingType trainingType,
                String rawAliases
        ) {
            if (name == null || name.isBlank()) {
                return;
            }
            String norm = normalizeName(name);
            String canonical = canonicalize(norm);
            String cleanCode = ExerciseDraftValidationService.normalizeSlug(code != null && !code.isBlank() ? code : name);
            Set<String> aliasSet = parseNormalizedAliases(rawAliases);
            aliasSet.add(norm);
            aliasSet.add(canonical);
            MovementSignature signature = buildSignature(norm, canonical);

            IndexedExercise entry = new IndexedExercise(
                    id, name.trim(), cleanCode, norm, canonical, category, trainingType, aliasSet, signature
            );
            allEntries.add(entry);
            byNormalizedName.putIfAbsent(norm, entry);
            byCanonicalKey.putIfAbsent(canonical, entry);
            if (!cleanCode.isBlank()) {
                byCode.putIfAbsent(cleanCode, entry);
            }
            for (String alias : aliasSet) {
                byAlias.putIfAbsent(alias, entry);
                byCanonicalKey.putIfAbsent(canonicalize(alias), entry);
            }
            String strictSigKey = strictSignatureKey(signature);
            if (!strictSigKey.isBlank()) {
                byStrictSignatureKey.putIfAbsent(strictSigKey, entry);
            }
        }

        public int size() {
            return allEntries.size();
        }
    }

    private final ExerciseRepository exerciseRepository;

    public CatalogSnapshot loadCatalogSnapshot() {
        CatalogSnapshot snapshot = new CatalogSnapshot();
        for (Exercise existing : exerciseRepository.findAll()) {
            snapshot.register(
                    existing.getId(),
                    existing.getName(),
                    existing.getCode(),
                    existing.getCategory(),
                    existing.getTrainingType(),
                    existing.getAliases()
            );
        }
        return snapshot;
    }

    public DuplicateCheckResult checkCandidate(
            CatalogSnapshot snapshot,
            String candidateName,
            String candidateCode,
            String candidateAliases,
            ExerciseCategory candidateCategory,
            ExerciseTrainingType candidateTrainingType
    ) {
        if (candidateName == null || candidateName.isBlank()) {
            return new DuplicateCheckResult(true, MatchType.UNCERTAIN_REVIEW, null, null, "Blank exercise name");
        }
        String norm = normalizeName(candidateName);
        String canonical = canonicalize(norm);
        String resolvedCode = ExerciseDraftValidationService.normalizeSlug(
                candidateCode != null && !candidateCode.isBlank() ? candidateCode : candidateName
        );

        // Layer 1: Exact normalized name
        IndexedExercise exact = snapshot.byNormalizedName.get(norm);
        if (exact != null) {
            return new DuplicateCheckResult(true, MatchType.EXACT_NAME, exact.id(), exact.name(),
                    "Exact normalized name match with '" + exact.name() + "'");
        }

        // Layer 2: Exact slug / code collision (only if name also matches or shares normalized slug)
        IndexedExercise codeMatch = snapshot.byCode.get(resolvedCode);
        if (codeMatch != null) {
            return new DuplicateCheckResult(true, MatchType.CODE_COLLISION, codeMatch.id(), codeMatch.name(),
                    "Code/slug collision (" + resolvedCode + ") with '" + codeMatch.name() + "'");
        }

        // Layer 3: Persisted aliases & canonical movement alias dictionary
        IndexedExercise canonicalMatch = snapshot.byCanonicalKey.get(canonical);
        if (canonicalMatch != null) {
            return new DuplicateCheckResult(true, MatchType.ALIAS_MATCH, canonicalMatch.id(), canonicalMatch.name(),
                    "Canonical alias match with '" + canonicalMatch.name() + "'");
        }
        IndexedExercise aliasMatch = snapshot.byAlias.get(norm);
        if (aliasMatch != null) {
            return new DuplicateCheckResult(true, MatchType.ALIAS_MATCH, aliasMatch.id(), aliasMatch.name(),
                    "Alias match with '" + aliasMatch.name() + "'");
        }
        for (String candidateAlias : parseNormalizedAliases(candidateAliases)) {
            IndexedExercise reverseAlias = snapshot.byNormalizedName.get(candidateAlias);
            if (reverseAlias == null) {
                reverseAlias = snapshot.byAlias.get(candidateAlias);
            }
            if (reverseAlias == null) {
                reverseAlias = snapshot.byCanonicalKey.get(canonicalize(candidateAlias));
            }
            if (reverseAlias != null) {
                return new DuplicateCheckResult(true, MatchType.ALIAS_MATCH, reverseAlias.id(), reverseAlias.name(),
                        "Candidate alias '" + candidateAlias + "' matches '" + reverseAlias.name() + "'");
            }
        }

        // Layer 4: Exact movement signature (reordered identical equipment + modifiers + core tokens)
        MovementSignature candSig = buildSignature(norm, canonical);
        String strictSigKey = strictSignatureKey(candSig);
        IndexedExercise sigMatch = snapshot.byStrictSignatureKey.get(strictSigKey);
        if (sigMatch != null) {
            return new DuplicateCheckResult(true, MatchType.SEMANTIC_DUPLICATE, sigMatch.id(), sigMatch.name(),
                    "Identical biomechanical movement signature as '" + sigMatch.name() + "'");
        }

        // Layer 5: Semantic near-duplicate / uncertain overlap (preserve distinct modifiers & distinct core movements like pull vs chin)
        for (IndexedExercise existing : snapshot.allEntries) {
            MovementSignature existingSig = existing.signature();
            if (!Objects.equals(candSig.equipmentTokens(), existingSig.equipmentTokens())) {
                continue;
            }
            if (!Objects.equals(candSig.differentiatingModifiers(), existingSig.differentiatingModifiers())) {
                continue;
            }
            double jaccard = jaccardSimilarity(candSig.coreTokens(), existingSig.coreTokens());
            if (jaccard >= 0.999) {
                return new DuplicateCheckResult(true, MatchType.SEMANTIC_DUPLICATE, existing.id(), existing.name(),
                        "Semantic movement duplicate of '" + existing.name() + "'");
            }
            // High overlap within same movement pattern & compatible category, no differing equipment or modifier -> skip for review
            if (jaccard >= 0.72 && compatibleCategories(candidateCategory, existing.category())) {
                // Ensure distinct pull vs chin or push-down vs curl are not flagged falsely when core root differs
                if (!sharesCriticalMovementHead(candSig.coreTokens(), existingSig.coreTokens())) {
                    continue;
                }
                return new DuplicateCheckResult(true, MatchType.UNCERTAIN_REVIEW, existing.id(), existing.name(),
                        "Potential semantic duplicate of '" + existing.name() + "' skipped for admin review");
            }
        }

        return DuplicateCheckResult.unique();
    }

    public static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String ascii = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US);
        String cleaned = NON_ALNUM.matcher(ascii).replaceAll(" ");
        String collapsed = MULTI_SPACE.matcher(cleaned).replaceAll(" ").trim();
        if (collapsed.isBlank()) {
            return "";
        }
        String[] parts = collapsed.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String tok = normalizeToken(parts[i]);
            if (tok.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(tok);
        }
        return sb.toString();
    }

    private static String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String mapped = PLURAL_TAIL_MAP.get(raw);
        if (mapped != null) {
            return mapped;
        }
        // Handle singularization of trailing plural nouns only if length > 4 and doesn't end in "ss"
        if (raw.length() > 4 && raw.endsWith("s") && !raw.endsWith("ss") && !raw.endsWith("us")
                && !raw.equals("cossack") && !raw.equals("press") && !raw.equals("triceps") && !raw.equals("biceps")) {
            return raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    public static String canonicalize(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return "";
        }
        String direct = CANONICAL_ALIAS_MAP.get(normalized);
        if (direct != null) {
            return direct;
        }
        String[] tokens = normalized.split(" ");
        List<String> filtered = new ArrayList<>();
        for (String t : tokens) {
            if (!OPTIONAL_FILLER_TOKENS.contains(t)) {
                filtered.add(t);
            }
        }
        String joined = String.join(" ", filtered);
        return CANONICAL_ALIAS_MAP.getOrDefault(joined, joined);
    }

    private static Set<String> parseNormalizedAliases(String rawAliases) {
        Set<String> set = new LinkedHashSet<>();
        if (rawAliases == null || rawAliases.isBlank()) {
            return set;
        }
        for (String part : SEPARATOR_LIST.split(rawAliases)) {
            String norm = normalizeName(part);
            if (!norm.isBlank()) {
                set.add(norm);
                set.add(canonicalize(norm));
            }
        }
        return set;
    }

    private static MovementSignature buildSignature(String normalized, String canonical) {
        String source = (canonical != null && !canonical.isBlank()) ? canonical : normalized;
        Set<String> equipment = new TreeSet<>();
        Set<String> modifiers = new TreeSet<>();
        Set<String> core = new TreeSet<>();

        for (String token : source.split(" ")) {
            if (token.isBlank() || OPTIONAL_FILLER_TOKENS.contains(token)) {
                continue;
            }
            if (EQUIPMENT_TOKENS.contains(token)) {
                equipment.add(token);
            }
            if (DISTINCT_ANGLE_AND_VARIANT_MODIFIERS.contains(token)) {
                modifiers.add(token);
            }
            if (!EQUIPMENT_TOKENS.contains(token) && !DISTINCT_ANGLE_AND_VARIANT_MODIFIERS.contains(token)) {
                core.add(token);
            }
        }
        if (core.isEmpty() && !modifiers.isEmpty()) {
            // e.g. "Pull-Up" or "Chin-Up": keep differentiating movement action in core too
            core.addAll(modifiers);
        } else {
            // Keep distinguishing heads ("chin", "pull", "push", "squat", "lunge", "row", "press", "curl") in core if present
            for (String m : modifiers) {
                if (m.equals("chin") || m.equals("pull") || m.equals("hold")) {
                    core.add(m);
                }
            }
        }
        return new MovementSignature(source, equipment, modifiers, core);
    }

    private static String strictSignatureKey(MovementSignature sig) {
        return String.join(",", sig.equipmentTokens())
                + "|" + String.join(",", sig.differentiatingModifiers())
                + "|" + String.join(",", sig.coreTokens());
    }

    private static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String item : a) {
            if (b.contains(item)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private static boolean compatibleCategories(ExerciseCategory a, ExerciseCategory b) {
        if (a == null || b == null || a == b) {
            return true;
        }
        return (a == ExerciseCategory.PUSH && b == ExerciseCategory.CHEST)
                || (a == ExerciseCategory.CHEST && b == ExerciseCategory.PUSH)
                || (a == ExerciseCategory.PULL && b == ExerciseCategory.BACK)
                || (a == ExerciseCategory.BACK && b == ExerciseCategory.PULL)
                || (a == ExerciseCategory.LEGS && b == ExerciseCategory.GLUTES)
                || (a == ExerciseCategory.GLUTES && b == ExerciseCategory.LEGS)
                || (a == ExerciseCategory.FLEXIBILITY && b == ExerciseCategory.MOBILITY)
                || (a == ExerciseCategory.MOBILITY && b == ExerciseCategory.FLEXIBILITY);
    }

    private static boolean sharesCriticalMovementHead(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return !intersection.isEmpty();
    }
}
