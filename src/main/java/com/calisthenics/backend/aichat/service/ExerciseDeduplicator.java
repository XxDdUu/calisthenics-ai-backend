package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.exercise.Exercise;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes exercise names/aliases and enforces strict deduplication
 * across conversational exercise lists and recommendations.
 */
public final class ExerciseDeduplicator {

    private static final Pattern LIST_PREFIX_PATTERN = Pattern.compile("^\\s*(?:\\d+[.)]|[-*•])\\s*");
    private static final Pattern PARENTHESES_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern SET_REP_TAIL_PATTERN = Pattern.compile(
            "(?i)(?:\\s*[—–|-]\\s*\\d+\\s*(?:sets?|x|×).*$|\\b\\d+\\s*[x×]\\s*\\d+\\b|\\b\\d+\\s*(?:sets?|reps?|sec|secs|seconds?|s\\s*hold|rest)\\b)"
    );

    private static final Map<String, String> TOKEN_SYNONYMS = Map.ofEntries(
            Map.entry("db", "dumbbell"),
            Map.entry("dbs", "dumbbell"),
            Map.entry("bb", "barbell"),
            Map.entry("kb", "kettlebell"),
            Map.entry("bw", "bodyweight"),
            Map.entry("rdl", "romanian deadlift"),
            Map.entry("hspu", "handstand push up"),
            Map.entry("hspus", "handstand push up"),
            Map.entry("ohp", "overhead press"),
            Map.entry("sl", "single leg"),
            Map.entry("unilateral", "single leg"),
            Map.entry("pushup", "push up"),
            Map.entry("pushups", "push up"),
            Map.entry("pullup", "pull up"),
            Map.entry("pullups", "pull up"),
            Map.entry("chinup", "chin up"),
            Map.entry("chinups", "chin up"),
            Map.entry("squats", "squat"),
            Map.entry("lunges", "lunge"),
            Map.entry("curls", "curl"),
            Map.entry("raises", "raise"),
            Map.entry("extensions", "extension"),
            Map.entry("deadlifts", "deadlift"),
            Map.entry("presses", "press"),
            Map.entry("holds", "hold"),
            Map.entry("dips", "dip"),
            Map.entry("rows", "row"),
            Map.entry("bridges", "bridge"),
            Map.entry("jumps", "jump"),
            Map.entry("planks", "plank"),
            Map.entry("thrusts", "thrust"),
            Map.entry("calves", "calf"),
            Map.entry("leans", "lean"),
            Map.entry("ups", "up"),
            Map.entry("downs", "down")
    );

    private ExerciseDeduplicator() {}

    public record ParsedExerciseLine(String name, String description) {}

    public static final class DeduplicationState {
        private final Set<Long> excludedIds = new LinkedHashSet<>();
        private final Set<String> excludedNormalizedKeys = new LinkedHashSet<>();
        private final List<String> excludedDisplayNames = new ArrayList<>();

        public void recordExcluded(Long id, String name, String aliases, String code) {
            if (id != null && id > 0) {
                excludedIds.add(id);
            }
            addKey(name);
            addKey(code);
            if (aliases != null && !aliases.isBlank()) {
                for (String alias : aliases.split("[,;|]")) {
                    addKey(alias);
                }
            }
            if (name != null && !name.isBlank()) {
                String cleaned = stripFormatting(name);
                if (!cleaned.isBlank() && excludedDisplayNames.stream().noneMatch(n -> n.equalsIgnoreCase(cleaned))) {
                    excludedDisplayNames.add(cleaned);
                }
            }
        }

        public void recordExcludedCatalogExercise(Exercise ex) {
            if (ex == null) return;
            recordExcluded(ex.getId(), ex.getName(), ex.getAliases(), ex.getCode());
        }

        public boolean isExcluded(Exercise ex) {
            if (ex == null) return true;
            if (ex.getId() != null && ex.getId() > 0 && excludedIds.contains(ex.getId())) {
                return true;
            }
            for (String key : canonicalKeysForExercise(ex)) {
                if (excludedNormalizedKeys.contains(key)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isExcludedName(String rawName, List<Exercise> catalog) {
            String key = normalizeName(rawName);
            if (key.isBlank() || excludedNormalizedKeys.contains(key)) {
                return true;
            }
            Exercise matched = matchCatalogExercise(rawName, catalog);
            if (matched != null) {
                return isExcluded(matched);
            }
            return false;
        }

        public boolean isEmpty() {
            return excludedIds.isEmpty() && excludedNormalizedKeys.isEmpty();
        }

        public Set<Long> excludedIds() {
            return Collections.unmodifiableSet(excludedIds);
        }

        public Set<String> excludedNormalizedKeys() {
            return Collections.unmodifiableSet(excludedNormalizedKeys);
        }

        public List<String> excludedDisplayNames() {
            return Collections.unmodifiableList(excludedDisplayNames);
        }

        public void addNormalizedKeyOnly(String key) {
            if (key != null && !key.isBlank()) {
                excludedNormalizedKeys.add(key);
            }
        }

        private void addKey(String raw) {
            String key = normalizeName(raw);
            if (!key.isBlank()) {
                excludedNormalizedKeys.add(key);
            }
        }
    }

    public static String stripFormatting(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = LIST_PREFIX_PATTERN.matcher(s).replaceFirst("");
        s = s.replace("**", "").replace("__", "").replace("`", "").trim();
        int emDash = s.indexOf(" — ");
        if (emDash > 0) {
            s = s.substring(0, emDash);
        }
        int enDash = s.indexOf(" – ");
        if (enDash > 0) {
            s = s.substring(0, enDash);
        }
        int hyphenSep = s.indexOf(" - ");
        if (hyphenSep > 0) {
            s = s.substring(0, hyphenSep);
        }
        int colonSep = s.indexOf(": ");
        if (colonSep > 0) {
            s = s.substring(0, colonSep);
        }
        return s.trim();
    }

    public static String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }
        String cleaned = stripFormatting(rawName);
        cleaned = PARENTHESES_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = SET_REP_TAIL_PATTERN.matcher(cleaned).replaceAll(" ");
        String lower = cleaned.toLowerCase(Locale.US)
                .replaceAll("\\b1[\\s-]?leg(?:ged)?\\b", " single leg ")
                .replaceAll("\\bone[\\s-]?leg(?:ged)?\\b", " single leg ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (lower.isEmpty()) {
            return "";
        }

        String[] rawTokens = lower.split("\\s+");
        List<String> expanded = new ArrayList<>(rawTokens.length);
        for (String token : rawTokens) {
            String replacement = TOKEN_SYNONYMS.getOrDefault(token, token);
            for (String sub : replacement.split("\\s+")) {
                if (!sub.isBlank()) {
                    expanded.add(sub);
                }
            }
        }
        return String.join(" ", expanded).trim();
    }

    public static Set<String> canonicalKeysForExercise(Exercise ex) {
        if (ex == null) return Set.of();
        Set<String> keys = new LinkedHashSet<>();
        addNonBlankKey(keys, ex.getName());
        addNonBlankKey(keys, ex.getCode() != null ? ex.getCode().replace('_', ' ') : null);
        if (ex.getAliases() != null && !ex.getAliases().isBlank()) {
            for (String alias : ex.getAliases().split("[,;|]")) {
                addNonBlankKey(keys, alias);
            }
        }
        return keys;
    }

    public static Exercise matchCatalogExercise(String rawName, List<Exercise> catalog) {
        if (rawName == null || rawName.isBlank() || catalog == null || catalog.isEmpty()) {
            return null;
        }
        String targetKey = normalizeName(rawName);
        if (targetKey.isEmpty()) {
            return null;
        }
        for (Exercise ex : catalog) {
            if (ex == null) continue;
            if (canonicalKeysForExercise(ex).contains(targetKey)) {
                return ex;
            }
        }
        return null;
    }

    public static List<ParsedExerciseLine> parseExerciseLines(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return List.of();
        }
        List<ParsedExerciseLine> parsed = new ArrayList<>();
        String[] lines = rawReply.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            boolean isListEntry = trimmed.matches("^\\d+[.)]\\s+.*")
                    || trimmed.startsWith("- ")
                    || trimmed.startsWith("* ")
                    || trimmed.startsWith("• ");
            if (!isListEntry) {
                continue;
            }
            String body = LIST_PREFIX_PATTERN.matcher(trimmed).replaceFirst("").trim();
            String name = body;
            String desc = null;
            for (String sep : List.of(" — ", " – ", " - ", ": ")) {
                int idx = body.indexOf(sep);
                if (idx > 0) {
                    name = body.substring(0, idx).trim();
                    desc = body.substring(idx + sep.length()).trim();
                    break;
                }
            }
            name = name.replace("**", "").replace("__", "").replace("`", "").trim();
            if (desc != null) {
                desc = desc.replace("**", "").replace("__", "").trim();
                if (desc.isBlank()) desc = null;
            }
            if (!name.isBlank()) {
                parsed.add(new ParsedExerciseLine(name, desc));
            }
        }
        return parsed;
    }

    private static void addNonBlankKey(Set<String> keys, String raw) {
        String k = normalizeName(raw);
        if (!k.isBlank()) {
            keys.add(k);
        }
    }
}
