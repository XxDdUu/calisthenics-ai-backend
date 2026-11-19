package com.calisthenics.backend.exercise.muscle;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MuscleTaxonomyService {

    private record TargetPreset(List<MuscleId> primary, List<MuscleId> secondary) {
    }

    public record ResolvedMuscleTargets(
            List<String> primaryMuscleIds,
            List<String> secondaryMuscleIds,
            List<ExerciseMuscleTargetDto> muscleTargets
    ) {
        public static ResolvedMuscleTargets empty() {
            return new ResolvedMuscleTargets(List.of(), List.of(), List.of());
        }
    }

    private static final Map<String, TargetPreset> CODE_PRESETS = Map.ofEntries(
            Map.entry("PUSH_UP", new TargetPreset(
                    List.of(MuscleId.CHEST),
                    List.of(MuscleId.FRONT_DELTOIDS, MuscleId.TRICEPS, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            )),
            Map.entry("DIPS", new TargetPreset(
                    List.of(MuscleId.TRICEPS, MuscleId.CHEST),
                    List.of(MuscleId.FRONT_DELTOIDS)
            )),
            Map.entry("PIKE_PUSH_UP", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS),
                    List.of(MuscleId.LATERAL_DELTOIDS, MuscleId.TRICEPS, MuscleId.CHEST, MuscleId.UPPER_TRAPEZIUS, MuscleId.RECTUS_ABDOMINIS)
            )),
            Map.entry("PULL_UP", new TargetPreset(
                    List.of(MuscleId.LATISSIMUS_DORSI, MuscleId.TERES_MAJOR),
                    List.of(MuscleId.BICEPS, MuscleId.RHOMBOIDS, MuscleId.MIDDLE_TRAPEZIUS, MuscleId.LOWER_TRAPEZIUS, MuscleId.FOREARMS, MuscleId.RECTUS_ABDOMINIS)
            )),
            Map.entry("MUSCLE_UP", new TargetPreset(
                    List.of(MuscleId.LATISSIMUS_DORSI, MuscleId.TRICEPS, MuscleId.CHEST),
                    List.of(MuscleId.TERES_MAJOR, MuscleId.BICEPS, MuscleId.FRONT_DELTOIDS, MuscleId.FOREARMS, MuscleId.RECTUS_ABDOMINIS)
            )),
            Map.entry("SQUAT", new TargetPreset(
                    List.of(MuscleId.QUADRICEPS, MuscleId.GLUTES),
                    List.of(MuscleId.HAMSTRINGS, MuscleId.ADDUCTORS, MuscleId.CALVES, MuscleId.ERECTOR_SPINAE, MuscleId.RECTUS_ABDOMINIS)
            )),
            Map.entry("L_SIT", new TargetPreset(
                    List.of(MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS),
                    List.of(MuscleId.OBLIQUES, MuscleId.QUADRICEPS, MuscleId.TRICEPS, MuscleId.LATISSIMUS_DORSI, MuscleId.FOREARMS)
            )),
            Map.entry("HANDSTAND", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS, MuscleId.UPPER_TRAPEZIUS),
                    List.of(MuscleId.LATERAL_DELTOIDS, MuscleId.TRICEPS, MuscleId.FOREARMS, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS, MuscleId.ERECTOR_SPINAE)
            )),
            Map.entry("TUCK_PLANCHE", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS),
                    List.of(MuscleId.CHEST, MuscleId.TRICEPS, MuscleId.FOREARMS, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            )),
            Map.entry("ADV_TUCK_PLANCHE", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS),
                    List.of(MuscleId.CHEST, MuscleId.TRICEPS, MuscleId.FOREARMS, MuscleId.ERECTOR_SPINAE, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            )),
            Map.entry("STRADDLE_PLANCHE", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS),
                    List.of(MuscleId.CHEST, MuscleId.TRICEPS, MuscleId.FOREARMS, MuscleId.GLUTES, MuscleId.ERECTOR_SPINAE, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            )),
            Map.entry("FULL_PLANCHE", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS),
                    List.of(MuscleId.CHEST, MuscleId.TRICEPS, MuscleId.FOREARMS, MuscleId.GLUTES, MuscleId.ERECTOR_SPINAE, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            )),
            Map.entry("PLANCHE", new TargetPreset(
                    List.of(MuscleId.FRONT_DELTOIDS),
                    List.of(MuscleId.CHEST, MuscleId.TRICEPS, MuscleId.FOREARMS, MuscleId.ERECTOR_SPINAE, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            )),
            Map.entry("FRONT_LEVER", new TargetPreset(
                    List.of(MuscleId.LATISSIMUS_DORSI, MuscleId.TERES_MAJOR),
                    List.of(MuscleId.REAR_DELTOIDS, MuscleId.RHOMBOIDS, MuscleId.MIDDLE_TRAPEZIUS, MuscleId.LOWER_TRAPEZIUS, MuscleId.FOREARMS, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS, MuscleId.ERECTOR_SPINAE)
            )),
            Map.entry("FRONT_LEVER_RAISES", new TargetPreset(
                    List.of(MuscleId.LATISSIMUS_DORSI, MuscleId.TERES_MAJOR),
                    List.of(MuscleId.REAR_DELTOIDS, MuscleId.RHOMBOIDS, MuscleId.LOWER_TRAPEZIUS, MuscleId.FOREARMS, MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS)
            ))
    );

    private static final Map<ExerciseCategory, TargetPreset> CATEGORY_BASELINES;

    static {
        EnumMap<ExerciseCategory, TargetPreset> map = new EnumMap<>(ExerciseCategory.class);
        map.put(ExerciseCategory.PUSH, new TargetPreset(
                List.of(MuscleId.CHEST, MuscleId.FRONT_DELTOIDS),
                List.of(MuscleId.TRICEPS)
        ));
        map.put(ExerciseCategory.PULL, new TargetPreset(
                List.of(MuscleId.LATISSIMUS_DORSI),
                List.of(MuscleId.BICEPS, MuscleId.RHOMBOIDS)
        ));
        map.put(ExerciseCategory.CORE, new TargetPreset(
                List.of(MuscleId.RECTUS_ABDOMINIS, MuscleId.TRANSVERSE_ABDOMINIS),
                List.of(MuscleId.OBLIQUES)
        ));
        map.put(ExerciseCategory.LEGS, new TargetPreset(
                List.of(MuscleId.QUADRICEPS, MuscleId.GLUTES),
                List.of(MuscleId.HAMSTRINGS, MuscleId.CALVES)
        ));
        map.put(ExerciseCategory.CHEST, new TargetPreset(
                List.of(MuscleId.CHEST),
                List.of(MuscleId.FRONT_DELTOIDS, MuscleId.TRICEPS)
        ));
        map.put(ExerciseCategory.BACK, new TargetPreset(
                List.of(MuscleId.LATISSIMUS_DORSI, MuscleId.RHOMBOIDS),
                List.of(MuscleId.MIDDLE_TRAPEZIUS, MuscleId.BICEPS)
        ));
        map.put(ExerciseCategory.SHOULDERS, new TargetPreset(
                List.of(MuscleId.FRONT_DELTOIDS, MuscleId.LATERAL_DELTOIDS),
                List.of(MuscleId.REAR_DELTOIDS, MuscleId.UPPER_TRAPEZIUS)
        ));
        map.put(ExerciseCategory.BICEPS, new TargetPreset(
                List.of(MuscleId.BICEPS),
                List.of(MuscleId.FOREARMS)
        ));
        map.put(ExerciseCategory.TRICEPS, new TargetPreset(
                List.of(MuscleId.TRICEPS),
                List.of(MuscleId.FRONT_DELTOIDS)
        ));
        map.put(ExerciseCategory.GLUTES, new TargetPreset(
                List.of(MuscleId.GLUTES),
                List.of(MuscleId.HAMSTRINGS)
        ));
        map.put(ExerciseCategory.FULL_BODY, new TargetPreset(
                List.of(MuscleId.CHEST, MuscleId.LATISSIMUS_DORSI, MuscleId.QUADRICEPS, MuscleId.GLUTES, MuscleId.RECTUS_ABDOMINIS),
                List.of(MuscleId.FRONT_DELTOIDS, MuscleId.HAMSTRINGS)
        ));
        CATEGORY_BASELINES = Map.copyOf(map);
    }

    public List<MuscleGroupDto> getAllMuscles() {
        return MuscleId.orderedValues().stream()
                .map(MuscleId::toDto)
                .toList();
    }

    public void validateMuscleIds(Collection<String> rawIds) {
        parseAndValidate(rawIds);
    }

    public ResolvedMuscleTargets resolveTargets(Exercise exercise) {
        if (exercise == null) {
            return ResolvedMuscleTargets.empty();
        }

        // 1. Prefer explicitly persisted mappings
        List<ExerciseMuscleTarget> persisted = exercise.getMuscleTargetMappings();
        if (persisted != null && !persisted.isEmpty()) {
            List<ExerciseMuscleTarget> ordered = persisted.stream()
                    .filter(item -> item != null && item.getRole() != null)
                    .sorted(Comparator.comparingInt(ExerciseMuscleTarget::getDisplayOrder)
                            .thenComparing(ExerciseMuscleTarget::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            Set<MuscleId> primary = new LinkedHashSet<>();
            Set<MuscleId> secondary = new LinkedHashSet<>();
            for (ExerciseMuscleTarget mapping : ordered) {
                Optional<MuscleId> resolved = MuscleId.fromId(mapping.getMuscleId());
                if (resolved.isEmpty()) {
                    continue;
                }
                if (mapping.getRole() == MuscleRole.PRIMARY) {
                    primary.add(resolved.get());
                }
            }
            for (ExerciseMuscleTarget mapping : ordered) {
                Optional<MuscleId> resolved = MuscleId.fromId(mapping.getMuscleId());
                if (resolved.isEmpty()) {
                    continue;
                }
                if (mapping.getRole() == MuscleRole.SECONDARY && !primary.contains(resolved.get())) {
                    secondary.add(resolved.get());
                }
            }
            if (!primary.isEmpty() || !secondary.isEmpty()) {
                return buildResolved(primary, secondary);
            }
        }

        // 2. Canonical exercise code presets
        if (exercise.getCode() != null && !exercise.getCode().isBlank()) {
            TargetPreset preset = CODE_PRESETS.get(exercise.getCode().trim().toUpperCase(Locale.ROOT));
            if (preset != null) {
                return buildResolved(new LinkedHashSet<>(preset.primary()), new LinkedHashSet<>(preset.secondary()));
            }
        }

        // 3. Comprehensive anatomical alias matcher over legacy targetMuscles / secondaryMuscles
        Set<MuscleId> primary = new LinkedHashSet<>(MuscleId.resolveAliases(exercise.getTargetMuscles()));
        Set<MuscleId> secondary = new LinkedHashSet<>();
        for (MuscleId candidate : MuscleId.resolveAliases(exercise.getSecondaryMuscles())) {
            if (!primary.contains(candidate)) {
                secondary.add(candidate);
            }
        }
        if (!primary.isEmpty() || !secondary.isEmpty()) {
            return buildResolved(primary, secondary);
        }

        // 4. Safe category baseline only if no text matched
        if (exercise.getCategory() != null) {
            TargetPreset baseline = CATEGORY_BASELINES.get(exercise.getCategory());
            if (baseline != null) {
                return buildResolved(new LinkedHashSet<>(baseline.primary()), new LinkedHashSet<>(baseline.secondary()));
            }
        }

        return ResolvedMuscleTargets.empty();
    }

    public void syncExerciseMuscleTargets(
            Exercise exercise,
            List<String> primaryMuscles,
            List<String> secondaryMuscleIds
    ) {
        if (exercise == null) {
            return;
        }
        List<MuscleId> validatedPrimary = parseAndValidate(primaryMuscles);
        List<MuscleId> validatedSecondary = parseAndValidate(secondaryMuscleIds);

        Set<MuscleId> primarySet = new LinkedHashSet<>(validatedPrimary);
        Set<MuscleId> secondarySet = new LinkedHashSet<>();
        for (MuscleId m : validatedSecondary) {
            if (!primarySet.contains(m)) {
                secondarySet.add(m);
            }
        }

        applyTargetMappingsInPlace(exercise, primarySet, secondarySet);

        if ((exercise.getTargetMuscles() == null || exercise.getTargetMuscles().isBlank()) && !primarySet.isEmpty()) {
            exercise.setTargetMuscles(primarySet.stream()
                    .map(MuscleId::getDisplayName)
                    .collect(Collectors.joining(", ")));
        }
        if ((exercise.getSecondaryMuscles() == null || exercise.getSecondaryMuscles().isBlank()) && !secondarySet.isEmpty()) {
            exercise.setSecondaryMuscles(secondarySet.stream()
                    .map(MuscleId::getDisplayName)
                    .collect(Collectors.joining(", ")));
        }
    }

    public void resyncFromLegacyTextIfPersisted(Exercise exercise) {
        if (exercise == null || exercise.getMuscleTargetMappings() == null || exercise.getMuscleTargetMappings().isEmpty()) {
            return;
        }
        Set<MuscleId> primary = new LinkedHashSet<>(MuscleId.resolveAliases(exercise.getTargetMuscles()));
        Set<MuscleId> secondary = new LinkedHashSet<>();
        for (MuscleId candidate : MuscleId.resolveAliases(exercise.getSecondaryMuscles())) {
            if (!primary.contains(candidate)) {
                secondary.add(candidate);
            }
        }
        applyTargetMappingsInPlace(exercise, primary, secondary);
    }

    private void applyTargetMappingsInPlace(
            Exercise exercise,
            Set<MuscleId> primarySet,
            Set<MuscleId> secondarySet
    ) {
        if (exercise.getMuscleTargetMappings() == null) {
            exercise.setMuscleTargetMappings(new ArrayList<>());
        }
        List<ExerciseMuscleTarget> mappings = exercise.getMuscleTargetMappings();

        // Index existing mappings by canonical muscleId so updates mutate in-place
        // without triggering unique constraint collisions on Hibernate flush.
        Map<String, ExerciseMuscleTarget> existingByMuscle = new LinkedHashMap<>();
        for (ExerciseMuscleTarget existing : mappings) {
            if (existing != null && existing.getMuscleId() != null) {
                existingByMuscle.putIfAbsent(existing.getMuscleId(), existing);
            }
        }

        Map<String, MuscleRole> desiredRoles = new LinkedHashMap<>();
        for (MuscleId m : primarySet) {
            desiredRoles.put(m.getId(), MuscleRole.PRIMARY);
        }
        for (MuscleId m : secondarySet) {
            desiredRoles.putIfAbsent(m.getId(), MuscleRole.SECONDARY);
        }

        mappings.removeIf(item -> item == null || item.getMuscleId() == null || !desiredRoles.containsKey(item.getMuscleId()));

        int order = 0;
        for (Map.Entry<String, MuscleRole> entry : desiredRoles.entrySet()) {
            String muscleId = entry.getKey();
            MuscleRole role = entry.getValue();
            ExerciseMuscleTarget target = existingByMuscle.get(muscleId);
            if (target != null) {
                target.setRole(role);
                target.setDisplayOrder(order);
                target.setExercise(exercise);
            } else {
                target = ExerciseMuscleTarget.builder()
                        .exercise(exercise)
                        .muscleId(muscleId)
                        .role(role)
                        .displayOrder(order)
                        .build();
                mappings.add(target);
            }
            order++;
        }

        mappings.sort(Comparator.comparingInt(ExerciseMuscleTarget::getDisplayOrder));
    }

    private List<MuscleId> parseAndValidate(Collection<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<MuscleId> parsed = new ArrayList<>(rawIds.size());
        for (String rawId : rawIds) {
            if (rawId == null || rawId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Muscle ID must not be blank");
            }
            MuscleId muscle = MuscleId.fromId(rawId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Unknown canonical muscle ID: '" + rawId + "'"));
            parsed.add(muscle);
        }
        return parsed;
    }

    private ResolvedMuscleTargets buildResolved(Set<MuscleId> primary, Set<MuscleId> secondary) {
        List<String> primaryIds = new ArrayList<>(primary.size());
        List<String> secondaryIds = new ArrayList<>(secondary.size());
        List<ExerciseMuscleTargetDto> targetDtos = new ArrayList<>(primary.size() + secondary.size());

        int order = 0;
        for (MuscleId m : primary) {
            primaryIds.add(m.getId());
            targetDtos.add(m.toTargetDto(MuscleRole.PRIMARY, order++));
        }
        for (MuscleId m : secondary) {
            if (!primary.contains(m)) {
                secondaryIds.add(m.getId());
                targetDtos.add(m.toTargetDto(MuscleRole.SECONDARY, order++));
            }
        }
        return new ResolvedMuscleTargets(
                List.copyOf(primaryIds),
                List.copyOf(secondaryIds),
                List.copyOf(targetDtos)
        );
    }
}
