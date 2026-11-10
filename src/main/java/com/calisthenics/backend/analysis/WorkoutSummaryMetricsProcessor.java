package com.calisthenics.backend.analysis;

import com.calisthenics.backend.analysis.dto.DetectedFormErrorDto;
import com.calisthenics.backend.analysis.dto.ExerciseFormMetricDto;
import com.calisthenics.backend.analysis.dto.RepHistoryDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class WorkoutSummaryMetricsProcessor {

    public record MetricDefinition(
            String canonicalKey,
            String label,
            String unit,
            List<String> aliases,
            String defaultUnavailableReason
    ) {}

    private static final Map<String, List<MetricDefinition>> EXERCISE_METRIC_CONFIGS = Map.ofEntries(
            Map.entry("PUSH_UP", List.of(
                    new MetricDefinition("body_alignment", "Body Alignment", "°", List.of("body_line"), "Shoulder-hip-ankle landmarks not visible"),
                    new MetricDefinition("elbow_positioning", "Elbow Positioning", "°", List.of("elbow_movement", "elbow_symmetry"), "Left/right elbow landmarks not visible"),
                    new MetricDefinition("range_of_motion", "Range of Motion", "°", List.of("elbow_angle", "rom"), "Elbow flexion angle not detected"),
                    new MetricDefinition("tempo_consistency", "Tempo Consistency", "s", List.of("tempo"), "Requires at least 2 completed repetitions"),
                    new MetricDefinition("rep_consistency", "Rep Consistency", "pts", List.of("consistency"), "Requires at least 2 completed repetitions")
            )),
            Map.entry("PIKE_PUSH_UP", List.of(
                    new MetricDefinition("body_alignment", "Body Alignment", "°", List.of("body_line", "torso_angle"), "Torso alignment landmarks not visible"),
                    new MetricDefinition("elbow_positioning", "Elbow Positioning", "°", List.of("elbow_movement"), "Elbow landmarks not visible"),
                    new MetricDefinition("range_of_motion", "Range of Motion", "°", List.of("elbow_angle", "rom"), "Elbow flexion depth not detected"),
                    new MetricDefinition("tempo_consistency", "Tempo Consistency", "s", List.of("tempo"), "Requires at least 2 completed repetitions"),
                    new MetricDefinition("rep_consistency", "Rep Consistency", "pts", List.of("consistency"), "Requires at least 2 completed repetitions")
            )),
            Map.entry("SQUAT", List.of(
                    new MetricDefinition("squat_depth", "Squat Depth", "°", List.of("knee_angle", "depth"), "Hip-knee-ankle landmarks not visible"),
                    new MetricDefinition("knee_alignment", "Knee Alignment", "°", List.of("knee_symmetry", "knee_valgus"), "Knee alignment landmarks not visible"),
                    new MetricDefinition("hip_positioning", "Hip Positioning", "°", List.of("hip_angle", "hip_position"), "Shoulder-hip-knee landmarks not visible"),
                    new MetricDefinition("torso_angle", "Torso Angle", "°", List.of("torso_position", "trunk_lean"), "Torso inclination landmarks not visible"),
                    new MetricDefinition("balance", "Balance", "%", List.of("center_of_mass", "sway"), "Hip-ankle balance tracking unavailable")
            )),
            Map.entry("PLANCHE", List.of(
                    new MetricDefinition("shoulder_lean", "Shoulder Lean", "°", List.of("shoulder_angle"), "Shoulder-wrist lean landmarks not visible"),
                    new MetricDefinition("hip_alignment", "Hip Alignment", "°", List.of("hip_elevation", "hip_angle"), "Shoulder-hip-knee landmarks not visible"),
                    new MetricDefinition("elbow_lockout", "Elbow Lockout", "°", List.of("arm_lockout", "elbow_extension"), "Elbow lockout landmarks not visible"),
                    new MetricDefinition("scapular_positioning", "Scapular Positioning", "", List.of("scapular_protraction"), "Unavailable without scapular depth landmarks"),
                    new MetricDefinition("body_horizontal_alignment", "Body Horizontal Alignment", "°", List.of("body_line", "horizontal_tilt"), "Body horizontal line not visible")
            )),
            Map.entry("FRONT_LEVER", List.of(
                    new MetricDefinition("body_horizontal_alignment", "Body Horizontal Alignment", "°", List.of("horizontal_tilt"), "Body horizontal line not visible"),
                    new MetricDefinition("hip_position", "Hip Position", "°", List.of("body_line", "hip_alignment"), "Shoulder-hip-ankle landmarks not visible"),
                    new MetricDefinition("shoulder_position", "Shoulder Position", "°", List.of("shoulder_angle"), "Shoulder landmarks not visible"),
                    new MetricDefinition("elbow_lockout", "Elbow Lockout", "°", List.of("elbow_extension", "arm_lockout"), "Elbow lockout landmarks not visible"),
                    new MetricDefinition("hold_consistency", "Hold Consistency", "%", List.of("stability"), "Requires sufficient valid hold frames")
            ))
    );

    public List<MetricDefinition> getMetricConfigForExercise(String exerciseType) {
        if (exerciseType == null) return Collections.emptyList();
        String normalized = exerciseType.toUpperCase(Locale.US).trim();
        if (normalized.contains("PLANCHE")) {
            return EXERCISE_METRIC_CONFIGS.get("PLANCHE");
        }
        if (normalized.contains("FRONT_LEVER")) {
            return EXERCISE_METRIC_CONFIGS.get("FRONT_LEVER");
        }
        return EXERCISE_METRIC_CONFIGS.getOrDefault(normalized, Collections.emptyList());
    }

    public List<ExerciseFormMetricDto> normalizeExerciseMetrics(
            String exerciseType,
            List<ExerciseFormMetricDto> submittedMetrics,
            List<RepHistoryDto> reps
    ) {
        List<MetricDefinition> config = getMetricConfigForExercise(exerciseType);
        Map<String, ExerciseFormMetricDto> submittedMap = new LinkedHashMap<>();
        if (submittedMetrics != null) {
            for (ExerciseFormMetricDto dto : submittedMetrics) {
                if (dto != null && dto.getName() != null) {
                    submittedMap.put(dto.getName().toLowerCase(Locale.US).trim(), dto);
                }
            }
        }

        // Fallback/computed metrics for tempo and rep consistency if Push-up reps were captured
        ExerciseFormMetricDto computedTempo = computeTempoConsistencyMetric(reps);
        ExerciseFormMetricDto computedRepConsistency = computeRepConsistencyMetric(reps);

        if (config.isEmpty()) {
            // For other exercises (Pull-Up, Dips, Handstand, L-Sit, etc.), pass through valid submitted metrics cleanly
            List<ExerciseFormMetricDto> genericList = new ArrayList<>();
            for (ExerciseFormMetricDto m : submittedMap.values()) {
                genericList.add(sanitizeMetric(m, null));
            }
            return genericList;
        }

        List<ExerciseFormMetricDto> result = new ArrayList<>();
        for (MetricDefinition def : config) {
            ExerciseFormMetricDto match = findMatch(submittedMap, def);

            if ("tempo_consistency".equals(def.canonicalKey()) && (match == null || isUnavailable(match)) && computedTempo != null) {
                match = computedTempo;
            } else if ("rep_consistency".equals(def.canonicalKey()) && (match == null || isUnavailable(match)) && computedRepConsistency != null) {
                match = computedRepConsistency;
            }

            if (match == null) {
                result.add(ExerciseFormMetricDto.builder()
                        .name(def.canonicalKey())
                        .label(def.label())
                        .value(null)
                        .unit(def.unit())
                        .score(null)
                        .confidence(0.0)
                        .status("UNAVAILABLE")
                        .unavailableReason(def.defaultUnavailableReason())
                        .build());
            } else {
                result.add(sanitizeMetric(match, def));
            }
        }
        return result;
    }

    private ExerciseFormMetricDto findMatch(Map<String, ExerciseFormMetricDto> submittedMap, MetricDefinition def) {
        if (submittedMap.containsKey(def.canonicalKey())) {
            return submittedMap.get(def.canonicalKey());
        }
        for (String alias : def.aliases()) {
            if (submittedMap.containsKey(alias)) {
                return submittedMap.get(alias);
            }
        }
        return null;
    }

    private boolean isUnavailable(ExerciseFormMetricDto dto) {
        if (dto == null || dto.getScore() == null) return true;
        String status = dto.getStatus() != null ? dto.getStatus().toUpperCase(Locale.US) : "";
        return "UNAVAILABLE".equals(status) || "INSUFFICIENT_DATA".equals(status) || (dto.getConfidence() != null && dto.getConfidence() < 0.25);
    }

    private ExerciseFormMetricDto sanitizeMetric(ExerciseFormMetricDto m, MetricDefinition def) {
        String key = def != null ? def.canonicalKey() : m.getName();
        String label = def != null ? def.label() : (m.getLabel() != null ? m.getLabel() : formatMetricName(m.getName()));
        String unit = (m.getUnit() != null && !m.getUnit().isBlank()) ? m.getUnit() : (def != null ? def.unit() : "");

        if (isUnavailable(m)) {
            return ExerciseFormMetricDto.builder()
                    .name(key)
                    .label(label)
                    .value(null)
                    .unit(unit)
                    .score(null)
                    .confidence(m.getConfidence() != null ? m.getConfidence() : 0.0)
                    .status("UNAVAILABLE")
                    .unavailableReason(m.getUnavailableReason() != null
                            ? m.getUnavailableReason()
                            : (def != null ? def.defaultUnavailableReason() : "Insufficient pose landmark visibility"))
                    .build();
        }

        double boundedScore = Math.max(0.0, Math.min(100.0, m.getScore()));
        String finalStatus = (m.getConfidence() != null && m.getConfidence() < 0.5) ? "LOW_CONFIDENCE" : "VALID";
        if ("LOW_CONFIDENCE".equalsIgnoreCase(m.getStatus())) {
            finalStatus = "LOW_CONFIDENCE";
        }

        return ExerciseFormMetricDto.builder()
                .name(key)
                .label(label)
                .value(m.getValue() != null ? round1(m.getValue()) : null)
                .unit(unit)
                .score(round1(boundedScore))
                .confidence(m.getConfidence() != null ? round2(m.getConfidence()) : 0.9)
                .status(finalStatus)
                .unavailableReason(null)
                .build();
    }

    private ExerciseFormMetricDto computeTempoConsistencyMetric(List<RepHistoryDto> reps) {
        if (reps == null || reps.size() < 2) return null;
        List<Double> durationsSec = reps.stream()
                .map(RepHistoryDto::getDurationMs)
                .filter(d -> d != null && d >= 200L)
                .map(d -> d / 1000.0)
                .toList();
        if (durationsSec.size() < 2) return null;

        double mean = durationsSec.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = durationsSec.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0.0);
        double std = Math.sqrt(variance);
        double cov = mean > 0.2 ? (std / mean) : 0.0;
        double score = Math.max(20.0, Math.min(100.0, 100.0 - cov * 120.0));

        return ExerciseFormMetricDto.builder()
                .name("tempo_consistency")
                .label("Tempo Consistency")
                .value(round1(mean))
                .unit("s/rep")
                .score(round1(score))
                .confidence(0.90)
                .status("VALID")
                .build();
    }

    private ExerciseFormMetricDto computeRepConsistencyMetric(List<RepHistoryDto> reps) {
        if (reps == null || reps.size() < 2) return null;
        List<Double> scores = reps.stream()
                .map(RepHistoryDto::getFormScore)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .toList();
        if (scores.size() < 2) return null;

        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = scores.stream().mapToDouble(s -> Math.pow(s - mean, 2)).average().orElse(0.0);
        double std = Math.sqrt(variance);
        double consistencyScore = Math.max(20.0, Math.min(100.0, 100.0 - std * 3.0));

        return ExerciseFormMetricDto.builder()
                .name("rep_consistency")
                .label("Rep Consistency")
                .value(round1(std))
                .unit("σ")
                .score(round1(consistencyScore))
                .confidence(0.92)
                .status("VALID")
                .build();
    }

    public List<DetectedFormErrorDto> deduplicateDetectedErrors(List<DetectedFormErrorDto> errors) {
        if (errors == null || errors.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, DetectedFormErrorDto> merged = new LinkedHashMap<>();
        for (DetectedFormErrorDto err : errors) {
            if (err == null || (err.getCode() == null && err.getErrorName() == null)) continue;
            String key = (err.getCode() != null && !err.getCode().isBlank())
                    ? err.getCode().toUpperCase(Locale.US).trim()
                    : err.getErrorName().toUpperCase(Locale.US).trim();

            DetectedFormErrorDto existing = merged.get(key);
            if (existing == null) {
                Set<Integer> repSet = err.getRepNumbers() != null ? new LinkedHashSet<>(err.getRepNumbers()) : new LinkedHashSet<>();
                List<Long> tsList = err.getTimestampsMs() != null ? new ArrayList<>(err.getTimestampsMs()) : new ArrayList<>();
                int occ = Math.max(1, err.getOccurrences() != null ? err.getOccurrences() : Math.max(repSet.size(), tsList.size()));
                merged.put(key, DetectedFormErrorDto.builder()
                        .code(key)
                        .errorName(err.getErrorName() != null ? err.getErrorName() : formatMetricName(key))
                        .occurrences(occ)
                        .severity(normalizeSeverity(err.getSeverity()))
                        .timestampsMs(tsList)
                        .repNumbers(new ArrayList<>(repSet))
                        .suggestedCorrection(err.getSuggestedCorrection())
                        .build());
            } else {
                Set<Integer> repSet = new LinkedHashSet<>(existing.getRepNumbers() != null ? existing.getRepNumbers() : List.of());
                if (err.getRepNumbers() != null) repSet.addAll(err.getRepNumbers());
                existing.setRepNumbers(new ArrayList<>(repSet));

                List<Long> tsList = new ArrayList<>(existing.getTimestampsMs() != null ? existing.getTimestampsMs() : List.of());
                if (err.getTimestampsMs() != null) {
                    for (Long t : err.getTimestampsMs()) {
                        if (!tsList.contains(t)) tsList.add(t);
                    }
                }
                existing.setTimestampsMs(tsList);
                int addedOcc = err.getOccurrences() != null ? err.getOccurrences() : 1;
                existing.setOccurrences(existing.getOccurrences() + addedOcc);
                existing.setSeverity(maxSeverity(existing.getSeverity(), normalizeSeverity(err.getSeverity())));
                if (existing.getSuggestedCorrection() == null) {
                    existing.setSuggestedCorrection(err.getSuggestedCorrection());
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingInt((DetectedFormErrorDto e) -> severityRank(e.getSeverity())).reversed()
                        .thenComparing(Comparator.comparingInt(DetectedFormErrorDto::getOccurrences).reversed()))
                .toList();
    }

    private String normalizeSeverity(String raw) {
        if (raw == null) return "MEDIUM";
        String upper = raw.toUpperCase(Locale.US).trim();
        return switch (upper) {
            case "ERROR", "HIGH" -> "HIGH";
            case "INFO", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private String maxSeverity(String a, String b) {
        return severityRank(a) >= severityRank(b) ? a : b;
    }

    private int severityRank(String severity) {
        if ("HIGH".equals(severity)) return 3;
        if ("MEDIUM".equals(severity)) return 2;
        return 1;
    }

    private String formatMetricName(String raw) {
        if (raw == null) return "";
        String[] parts = raw.replace("_", " ").toLowerCase(Locale.US).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private double round1(double val) {
        return BigDecimal.valueOf(val).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double round2(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
