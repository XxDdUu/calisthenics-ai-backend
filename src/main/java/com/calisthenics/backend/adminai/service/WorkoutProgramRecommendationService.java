package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.WorkoutProgramRecommendationPayload;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkoutProgramRecommendationService {

    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanRepository workoutPlanRepository;

    @Transactional(readOnly = true)
    public List<WorkoutProgramRecommendationPayload> generateProgramRecommendations(String focusQuery, String requestedLevel) {
        List<Exercise> catalog = exerciseRepository.findByActiveTrue();
        if (catalog.isEmpty()) {
            catalog = exerciseRepository.findAll();
        }

        String focus = focusQuery != null ? focusQuery.toLowerCase() : "";
        String level = requestedLevel != null && !requestedLevel.isBlank()
                ? requestedLevel.toUpperCase()
                : "INTERMEDIATE";

        List<WorkoutProgramRecommendationPayload> candidates = new ArrayList<>();
        candidates.add(buildPlancheProgram(catalog, "BEGINNER".equals(level) ? "BEGINNER" : "INTERMEDIATE"));
        candidates.add(buildFrontLeverProgram(catalog));
        candidates.add(buildHandstandFoundationProgram(catalog));
        candidates.add(buildWeightedCalisthenicsProgram(catalog));
        candidates.add(buildGeneralStrengthProgram(catalog));

        if (!focus.isBlank()) {
            List<WorkoutProgramRecommendationPayload> filtered = candidates.stream()
                    .filter(p -> p.getProgramName().toLowerCase().contains(focus)
                            || p.getRationale().toLowerCase().contains(focus)
                            || p.getTargetAudience().toLowerCase().contains(focus))
                    .toList();
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        return candidates.subList(0, Math.min(3, candidates.size()));
    }

    @Transactional
    public WorkoutPlan createDraftWorkoutPlanFromRecommendation(
            WorkoutProgramRecommendationPayload payload,
            User approvingAdmin
    ) {
        List<Exercise> catalog = exerciseRepository.findAll();
        WorkoutSplitType splitType = resolveSplitType(payload.getSplitType());

        WorkoutPlan draftPlan = WorkoutPlan.builder()
                .user(approvingAdmin)
                .name(payload.getProgramName() != null ? payload.getProgramName() : "Admin AI Draft Program")
                .description(buildDraftDescription(payload))
                .splitType(splitType)
                .active(false) // Mandatory: Never auto-publish or activate without explicit admin action
                .aiGenerated(true)
                .aiPrompt("Admin AI Agent Recommendation: " + payload.getProgramName())
                .build();

        Map<WorkoutDayOfWeek, WorkoutProgramRecommendationPayload.ProgramDayDto> dayMap = new EnumMap<>(WorkoutDayOfWeek.class);
        if (payload.getDays() != null) {
            for (WorkoutProgramRecommendationPayload.ProgramDayDto dayDto : payload.getDays()) {
                try {
                    WorkoutDayOfWeek dow = WorkoutDayOfWeek.valueOf(dayDto.getDayOfWeek().toUpperCase());
                    dayMap.put(dow, dayDto);
                } catch (Exception ignored) {
                }
            }
        }

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            WorkoutProgramRecommendationPayload.ProgramDayDto dayDto = dayMap.get(dow);
            boolean isRest = dayDto == null || dayDto.isRestDay() || dayDto.getExercises() == null || dayDto.getExercises().isEmpty();
            String dayTitle = dayDto != null && dayDto.getTitle() != null ? dayDto.getTitle() : (isRest ? "Rest & Recovery" : dow.name() + " Session");

            WorkoutPlanDay planDay = WorkoutPlanDay.builder()
                    .workoutPlan(draftPlan)
                    .dayOfWeek(dow)
                    .sessionType(isRest ? WorkoutSessionType.REST : WorkoutSessionType.CUSTOM)
                    .name(dayTitle)
                    .description(isRest ? "Active recovery, mobility, and connective tissue regeneration" : dayTitle)
                    .restDay(isRest)
                    .displayOrder(dow.getOrder())
                    .build();

            if (!isRest && dayDto.getExercises() != null) {
                int order = 0;
                Set<Long> usedExerciseIdsOnDay = new HashSet<>();
                for (WorkoutProgramRecommendationPayload.ProgramExerciseDto exDto : dayDto.getExercises()) {
                    Exercise resolved = resolveExerciseFromCatalog(exDto.getExerciseId(), exDto.getExerciseName(), catalog);
                    if (resolved == null || (resolved.getId() != null && !usedExerciseIdsOnDay.add(resolved.getId()))) {
                        continue;
                    }
                    WorkoutPlanExercise wpe = WorkoutPlanExercise.builder()
                            .workoutPlanDay(planDay)
                            .exercise(resolved)
                            .sets(Math.max(1, exDto.getSets()))
                            .reps(exDto.getReps())
                            .durationSeconds(exDto.getHoldSeconds())
                            .restSeconds(Math.max(30, exDto.getRestSeconds()))
                            .displayOrder(order++)
                            .notes(exDto.getNotes())
                            .build();
                    planDay.getExercises().add(wpe);
                }
                if (planDay.getExercises().isEmpty()) {
                    planDay.setRestDay(true);
                    planDay.setSessionType(WorkoutSessionType.REST);
                }
            }
            draftPlan.getDays().add(planDay);
        }

        return workoutPlanRepository.save(draftPlan);
    }

    private WorkoutProgramRecommendationPayload buildPlancheProgram(List<Exercise> catalog, String level) {
        Exercise tuckPlanche = findBestMatch(catalog, "Tuck Planche", "Planche", ExerciseCategory.SKILL);
        Exercise pushUp = findBestMatch(catalog, "Push-Up", "Pike Push-Up", ExerciseCategory.PUSH);
        Exercise dips = findBestMatch(catalog, "Parallel Bar Dips", "Dips", ExerciseCategory.PUSH);
        Exercise lSit = findBestMatch(catalog, "L-Sit", "Handstand", ExerciseCategory.CORE);
        Exercise pullUp = findBestMatch(catalog, "Pull-Up", "Front Lever", ExerciseCategory.PULL);

        List<WorkoutProgramRecommendationPayload.ProgramDayDto> days = List.of(
                day("MONDAY", "Planche Isometrics + Straight-Arm Push", false, List.of(
                        ex(tuckPlanche, "Tuck Planche / Planche Lean", 5, null, 12, 120, "Full scapular protraction, locked elbows (>170°)"),
                        ex(dips, "Parallel Bar Dips", 4, 8, null, 90, "2s pause at bottom, controlled lockout"),
                        ex(pushUp, "Pseudo Planche Push-Ups", 4, 10, null, 90, "Lean shoulders past wrists throughout each rep"),
                        ex(lSit, "L-Sit Compression Hold", 4, null, 15, 75, "Depress shoulders and lock quads")
                )),
                day("TUESDAY", "Structural Pulling & Scapular Balance", false, List.of(
                        ex(pullUp, "Strict Pull-Up", 4, 8, null, 90, "Chest to bar, 3s eccentric"),
                        ex(lSit, "Hollow Body Core Hold", 3, null, 30, 60, "Posterior pelvic tilt maintained")
                )),
                day("WEDNESDAY", "Rest & Wrist Mobility", true, List.of()),
                day("THURSDAY", "Planche Volume & Bent-Arm Pressing", false, List.of(
                        ex(tuckPlanche, "Tuck Planche Cluster Sets", 5, null, 10, 120, "Focus on hip elevation to shoulder line"),
                        ex(pushUp, "Pike Push-Ups", 4, 8, null, 90, "Tripod head trajectory"),
                        ex(dips, "Parallel Bar Dips", 3, 10, null, 90, "Full range of motion")
                )),
                day("FRIDAY", "Pulling & Posterior Chain", false, List.of(
                        ex(pullUp, "Pull-Up Volume", 4, 8, null, 90, "Strict dead hang start"),
                        ex(lSit, "L-Sit Hold", 4, null, 15, 60, "Straight knees")
                )),
                day("SATURDAY", "Skill Technique & Conditioning", false, List.of(
                        ex(tuckPlanche, "Planche Lean Holds", 4, null, 20, 90, "Measure lean distance consistently"),
                        ex(pushUp, "Push-Up Tempo", 3, 15, null, 60, "Protracted lockout on every rep")
                )),
                day("SUNDAY", "Full Recovery", true, List.of())
        );

        return WorkoutProgramRecommendationPayload.builder()
                .programName("Beginner Planche Program")
                .targetAudience("Athletes able to perform 15+ strict push-ups and 8+ dips seeking structured Planche progression")
                .trainingLevel(level)
                .weeklyFrequency(5)
                .durationWeeks(6)
                .splitType("UPPER_LOWER")
                .progressionCriteria("Advance to the next progression when the athlete completes 5x15s holds with locked elbows and full scapular protraction across two consecutive sessions.")
                .requiredEquipment("Parallettes, Pull-Up Bar, Parallel Bars, Floor")
                .safetyConsiderations("Mandatory 8-minute wrist warm-up before every session. Avoid training straight-arm planche holds on consecutive days to protect biceps tendons and wrist extensors.")
                .rationale("Platform analytics show high athlete interest in Planche skills, and structured straight-arm/bent-arm periodization prevents tendonitis while accelerating progression.")
                .days(days)
                .build();
    }

    private WorkoutProgramRecommendationPayload buildFrontLeverProgram(List<Exercise> catalog) {
        Exercise frontLever = findBestMatch(catalog, "Front Lever", "Tuck Front Lever", ExerciseCategory.SKILL);
        Exercise pullUp = findBestMatch(catalog, "Pull-Up", "Muscle-Up", ExerciseCategory.PULL);
        Exercise lSit = findBestMatch(catalog, "L-Sit", "Planche", ExerciseCategory.CORE);
        Exercise dips = findBestMatch(catalog, "Parallel Bar Dips", "Push-Up", ExerciseCategory.PUSH);

        List<WorkoutProgramRecommendationPayload.ProgramDayDto> days = List.of(
                day("MONDAY", "Front Lever Static Holds + Weighted/Strict Pull", false, List.of(
                        ex(frontLever, "Front Lever Progression Hold", 5, null, 12, 120, "Depress scapula, straight arms, horizontal torso"),
                        ex(pullUp, "Strict Pull-Ups", 4, 8, null, 90, "Pull to upper chest, 3s lowering phase"),
                        ex(lSit, "L-Sit Core Tension", 4, null, 15, 75, "Anti-extension core activation")
                )),
                day("TUESDAY", "Antagonist Push & Shoulder Stability", false, List.of(
                        ex(dips, "Parallel Bar Dips", 4, 10, null, 90, "Controlled tempo"),
                        ex(lSit, "L-Sit Hold", 3, null, 15, 60, "Scapular depression")
                )),
                day("WEDNESDAY", "Rest & Lat/Thoracic Mobility", true, List.of()),
                day("THURSDAY", "Front Lever Dynamic Pulling & Core", false, List.of(
                        ex(frontLever, "Front Lever Eccentrics / Raises", 4, null, 10, 120, "Maintain straight arms through the horizontal plane"),
                        ex(pullUp, "Explosive Chest-to-Bar Pull-Ups", 4, 6, null, 120, "Maximal acceleration from dead hang"),
                        ex(lSit, "L-Sit Hold", 4, null, 15, 75, "Full abdominal compression")
                )),
                day("FRIDAY", "Rest Day", true, List.of()),
                day("SATURDAY", "Front Lever Accumulation & Accessory Volume", false, List.of(
                        ex(frontLever, "Front Lever Progression Holds", 5, null, 10, 120, "Strict hip alignment"),
                        ex(pullUp, "Pull-Up Volume Sets", 4, 10, null, 90, "Clean chin-over-bar lockout"),
                        ex(dips, "Parallel Bar Dips", 3, 12, null, 75, "Structural antagonist balance")
                )),
                day("SUNDAY", "Full Recovery", true, List.of())
        );

        return WorkoutProgramRecommendationPayload.builder()
                .programName("Intermediate Front Lever Program")
                .targetAudience("Intermediate athletes with 8-10 strict pull-ups aiming to unlock Advanced Tuck, Straddle, or Full Front Lever")
                .trainingLevel("INTERMEDIATE")
                .weeklyFrequency(4)
                .durationWeeks(6)
                .splitType("CUSTOM")
                .progressionCriteria("Increase static hold targets by 2 seconds per week or progress from Tuck -> Advanced Tuck -> Straddle once 5 sets of 15 seconds are achieved with flat lumbar posture.")
                .requiredEquipment("Pull-Up Bar, Gymnastic Rings (optional), Parallel Bars")
                .safetyConsiderations("Maintain active scapular depression during all hangs; terminate a set immediately if elbows bend or shoulders elevate toward ears.")
                .rationale("Addresses the high demand for pulling skill progressions and pairs static holds with dynamic pulling volume for optimal lat and core adaptation.")
                .days(days)
                .build();
    }

    private WorkoutProgramRecommendationPayload buildHandstandFoundationProgram(List<Exercise> catalog) {
        Exercise handstand = findBestMatch(catalog, "Handstand", "Pike Push-Up", ExerciseCategory.SKILL);
        Exercise pikePushUp = findBestMatch(catalog, "Pike Push-Up", "Push-Up", ExerciseCategory.PUSH);
        Exercise lSit = findBestMatch(catalog, "L-Sit", "Handstand", ExerciseCategory.CORE);
        Exercise pullUp = findBestMatch(catalog, "Pull-Up", "Parallel Bar Dips", ExerciseCategory.PULL);

        List<WorkoutProgramRecommendationPayload.ProgramDayDto> days = List.of(
                day("MONDAY", "Handstand Alignment & Overhead Pressing", false, List.of(
                        ex(handstand, "Handstand Balance / Wall Hold", 5, null, 25, 90, "Stack wrists, shoulders, hips, and ankles in 180° line"),
                        ex(pikePushUp, "Pike Push-Up", 4, 8, null, 90, "Lower head forward of hands to form tripod"),
                        ex(lSit, "L-Sit Hold", 4, null, 15, 60, "Posterior pelvic tilt & core bracing")
                )),
                day("TUESDAY", "Rest Day", true, List.of()),
                day("WEDNESDAY", "Freestanding Balance & Scapular Elevation", false, List.of(
                        ex(handstand, "Handstand Rebalance Drills", 6, null, 20, 90, "Use finger pressure to correct over-balance"),
                        ex(pikePushUp, "Pike Push-Up Tempo", 4, 8, null, 90, "3s eccentric, pause 1s at bottom"),
                        ex(pullUp, "Strict Pull-Up", 4, 8, null, 90, "Shoulder health & pulling balance")
                )),
                day("THURSDAY", "Rest Day", true, List.of()),
                day("FRIDAY", "Handstand Endurance & Core Line", false, List.of(
                        ex(handstand, "Handstand Endurance Sets", 5, null, 30, 90, "Push tall through floor, ribcage tucked"),
                        ex(pikePushUp, "Pike Push-Up", 4, 10, null, 90, "Full overhead scapular elevation at top"),
                        ex(lSit, "L-Sit Hold", 4, null, 20, 60, "Core endurance")
                )),
                day("SATURDAY", "Optional Light Balance Practice", true, List.of()),
                day("SUNDAY", "Full Recovery", true, List.of())
        );

        return WorkoutProgramRecommendationPayload.builder()
                .programName("Handstand Foundation Program")
                .targetAudience("Beginners and intermediates building consistent wall and freestanding handstand alignment and overhead pressing strength")
                .trainingLevel("BEGINNER")
                .weeklyFrequency(3)
                .durationWeeks(4)
                .splitType("FULL_BODY")
                .progressionCriteria("Progress from 30s Chest-to-Wall holds to Heel Pulls and 15s+ freestanding holds once shoulder angle remains 180° without lumbar arch.")
                .requiredEquipment("Floor, Wall")
                .safetyConsiderations("Warm up wrists in extension and flexion before inversion. Practice safe bail-out (pirouette bail) before freestanding attempts away from the wall.")
                .rationale("Handstand is one of the most popular foundational skills on the platform and requires dedicated balance-first programming with low neural fatigue.")
                .days(days)
                .build();
    }

    private WorkoutProgramRecommendationPayload buildWeightedCalisthenicsProgram(List<Exercise> catalog) {
        Exercise pullUp = findBestMatch(catalog, "Pull-Up", "Muscle-Up", ExerciseCategory.PULL);
        Exercise dips = findBestMatch(catalog, "Parallel Bar Dips", "Push-Up", ExerciseCategory.PUSH);
        Exercise muscleUp = findBestMatch(catalog, "Muscle-Up", "Pull-Up", ExerciseCategory.PULL);
        Exercise squat = findBestMatch(catalog, "Bodyweight Squat", "L-Sit", ExerciseCategory.LEGS);
        Exercise pushUp = findBestMatch(catalog, "Push-Up", "Pike Push-Up", ExerciseCategory.PUSH);

        List<WorkoutProgramRecommendationPayload.ProgramDayDto> days = List.of(
                day("MONDAY", "Heavy Push Strength", false, List.of(
                        ex(dips, "Weighted / Strict Parallel Bar Dips", 5, 5, null, 150, "Progressive load, full depth to 90° elbow flexion"),
                        ex(pushUp, "Strict Push-Ups", 4, 12, null, 90, "Explosive concentric"),
                        ex(squat, "Squat Volume", 4, 15, null, 90, "Full knee and hip extension")
                )),
                day("TUESDAY", "Heavy Pull & Explosive Transition", false, List.of(
                        ex(muscleUp, "Muscle-Up", 4, 3, null, 150, "Explosive pull, clean bar transition"),
                        ex(pullUp, "Weighted / Strict Pull-Ups", 5, 5, null, 150, "Dead hang to chin over bar")
                )),
                day("WEDNESDAY", "Rest & Recovery", true, List.of()),
                day("THURSDAY", "Hypertrophy Push & Core", false, List.of(
                        ex(dips, "Parallel Bar Dips", 4, 10, null, 90, "Controlled eccentric"),
                        ex(pushUp, "Push-Ups", 4, 15, null, 75, "Full scapular protraction")
                )),
                day("FRIDAY", "Hypertrophy Pull & Legs", false, List.of(
                        ex(pullUp, "Pull-Ups", 4, 10, null, 90, "Strict form without kipping"),
                        ex(squat, "Bodyweight Squat", 4, 20, null, 75, "Deep range of motion")
                )),
                day("SATURDAY", "Rest Day", true, List.of()),
                day("SUNDAY", "Full Recovery", true, List.of())
        );

        return WorkoutProgramRecommendationPayload.builder()
                .programName("Weighted Calisthenics & Explosive Strength Program")
                .targetAudience("Intermediate-to-advanced athletes seeking maximal pulling/pushing strength and Muscle-Up power")
                .trainingLevel("ADVANCED")
                .weeklyFrequency(4)
                .durationWeeks(6)
                .splitType("UPPER_LOWER")
                .progressionCriteria("Add 1.25kg-2.5kg or 1 rep per set once all 5x5 sets are completed with strict tempo and RIR >= 1.")
                .requiredEquipment("Pull-Up Bar, Parallel Dip Bars, Dip Belt / Weight Plates (optional)")
                .safetyConsiderations("Do not add external load until 12+ strict bodyweight Pull-Ups and 15+ Dips are mastered with pain-free shoulder and elbow joints.")
                .rationale("Provides structured strength progression for athletes tracking MAX_WEIGHT and MAX_REPS personal records.")
                .days(days)
                .build();
    }

    private WorkoutProgramRecommendationPayload buildGeneralStrengthProgram(List<Exercise> catalog) {
        Exercise pushUp = findBestMatch(catalog, "Push-Up", "Parallel Bar Dips", ExerciseCategory.PUSH);
        Exercise pullUp = findBestMatch(catalog, "Pull-Up", "Front Lever", ExerciseCategory.PULL);
        Exercise dips = findBestMatch(catalog, "Parallel Bar Dips", "Push-Up", ExerciseCategory.PUSH);
        Exercise squat = findBestMatch(catalog, "Bodyweight Squat", "Push-Up", ExerciseCategory.LEGS);
        Exercise lSit = findBestMatch(catalog, "L-Sit", "Handstand", ExerciseCategory.CORE);

        List<WorkoutProgramRecommendationPayload.ProgramDayDto> days = List.of(
                day("MONDAY", "Full Body Foundation A", false, List.of(
                        ex(pullUp, "Pull-Up", 4, 8, null, 90, "Full range from dead hang"),
                        ex(dips, "Parallel Bar Dips", 4, 10, null, 90, "Controlled descent"),
                        ex(squat, "Bodyweight Squat", 4, 15, null, 75, "Below parallel depth"),
                        ex(lSit, "L-Sit Hold", 3, null, 15, 60, "Core compression")
                )),
                day("TUESDAY", "Rest Day", true, List.of()),
                day("WEDNESDAY", "Full Body Foundation B", false, List.of(
                        ex(pushUp, "Push-Up", 4, 15, null, 75, "Straight body line"),
                        ex(pullUp, "Pull-Up", 4, 8, null, 90, "Scapular depression before pull"),
                        ex(squat, "Bodyweight Squat", 4, 15, null, 75, "Controlled tempo"),
                        ex(lSit, "L-Sit Hold", 3, null, 15, 60, "Locked elbows")
                )),
                day("THURSDAY", "Rest Day", true, List.of()),
                day("FRIDAY", "Full Body Conditioning C", false, List.of(
                        ex(dips, "Parallel Bar Dips", 4, 10, null, 90, "Full lockout"),
                        ex(pullUp, "Pull-Up", 4, 8, null, 90, "Strict form"),
                        ex(pushUp, "Push-Up", 3, 15, null, 60, "Clean reps"),
                        ex(squat, "Bodyweight Squat", 4, 20, null, 60, "Lower body endurance")
                )),
                day("SATURDAY", "Active Mobility", true, List.of()),
                day("SUNDAY", "Full Recovery", true, List.of())
        );

        return WorkoutProgramRecommendationPayload.builder()
                .programName("General Strength & Conditioning Program")
                .targetAudience("All-around calisthenics athletes looking to improve push, pull, core, and lower-body work capacity")
                .trainingLevel("BEGINNER")
                .weeklyFrequency(3)
                .durationWeeks(4)
                .splitType("FULL_BODY")
                .progressionCriteria("Increase repetitions by 1-2 per set each week; once top rep targets are met across all sets, reduce rest intervals by 15s or advance to intermediate variations.")
                .requiredEquipment("Pull-Up Bar, Parallel Bars, Floor")
                .safetyConsiderations("Prioritize strict joint alignment over rep speed; leave 1-2 repetitions in reserve on compound sets to ensure consistent recovery across 3 weekly full-body sessions.")
                .rationale("Balanced full-body template designed to maximize workout completion rates and improve user retention.")
                .days(days)
                .build();
    }

    private WorkoutProgramRecommendationPayload.ProgramDayDto day(
            String dayOfWeek,
            String title,
            boolean restDay,
            List<WorkoutProgramRecommendationPayload.ProgramExerciseDto> exercises
    ) {
        return WorkoutProgramRecommendationPayload.ProgramDayDto.builder()
                .dayOfWeek(dayOfWeek)
                .title(title)
                .restDay(restDay)
                .exercises(exercises)
                .build();
    }

    private WorkoutProgramRecommendationPayload.ProgramExerciseDto ex(
            Exercise catalogExercise,
            String fallbackName,
            int sets,
            Integer reps,
            Integer holdSeconds,
            int restSeconds,
            String notes
    ) {
        return WorkoutProgramRecommendationPayload.ProgramExerciseDto.builder()
                .exerciseId(catalogExercise != null ? catalogExercise.getId() : null)
                .exerciseName(catalogExercise != null ? catalogExercise.getName() : fallbackName)
                .sets(sets)
                .reps(reps)
                .holdSeconds(holdSeconds)
                .restSeconds(restSeconds)
                .notes(notes)
                .build();
    }

    private Exercise findBestMatch(List<Exercise> catalog, String preferredName, String secondaryName, ExerciseCategory fallbackCategory) {
        for (Exercise e : catalog) {
            if (e.getName() != null && e.getName().equalsIgnoreCase(preferredName)) {
                return e;
            }
        }
        for (Exercise e : catalog) {
            if (e.getName() != null && e.getName().toLowerCase().contains(preferredName.toLowerCase())) {
                return e;
            }
        }
        if (secondaryName != null) {
            for (Exercise e : catalog) {
                if (e.getName() != null && e.getName().toLowerCase().contains(secondaryName.toLowerCase())) {
                    return e;
                }
            }
        }
        for (Exercise e : catalog) {
            if (e.getCategory() == fallbackCategory) {
                return e;
            }
        }
        return catalog.isEmpty() ? null : catalog.get(0);
    }

    private Exercise resolveExerciseFromCatalog(Long exerciseId, String exerciseName, List<Exercise> catalog) {
        if (exerciseId != null) {
            for (Exercise e : catalog) {
                if (exerciseId.equals(e.getId())) {
                    return e;
                }
            }
        }
        if (exerciseName != null && !exerciseName.isBlank()) {
            String lower = exerciseName.trim().toLowerCase();
            for (Exercise e : catalog) {
                if (e.getName() != null && e.getName().equalsIgnoreCase(exerciseName.trim())) {
                    return e;
                }
            }
            for (Exercise e : catalog) {
                if (e.getName() != null && (lower.contains(e.getName().toLowerCase()) || e.getName().toLowerCase().contains(lower))) {
                    return e;
                }
            }
        }
        return catalog.isEmpty() ? null : catalog.get(0);
    }

    private WorkoutSplitType resolveSplitType(String raw) {
        if (raw == null || raw.isBlank()) {
            return WorkoutSplitType.CUSTOM;
        }
        try {
            return WorkoutSplitType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return WorkoutSplitType.CUSTOM;
        }
    }

    private String buildDraftDescription(WorkoutProgramRecommendationPayload p) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DRAFT - ADMIN APPROVAL WORKFLOW] ");
        if (p.getTargetAudience() != null) {
            sb.append("Target Audience: ").append(p.getTargetAudience()).append(". ");
        }
        if (p.getProgressionCriteria() != null) {
            sb.append("Progression: ").append(p.getProgressionCriteria()).append(" ");
        }
        if (p.getRequiredEquipment() != null) {
            sb.append("Equipment: ").append(p.getRequiredEquipment()).append(". ");
        }
        if (p.getSafetyConsiderations() != null) {
            sb.append("Safety: ").append(p.getSafetyConsiderations());
        }
        return sb.toString().trim();
    }
}
