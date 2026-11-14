package com.calisthenics.backend.gettingstarted.service.impl;

import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanValidator;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedDayRequest;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedExerciseRequest;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedExerciseOperationRequest;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramDay;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramExercise;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedCampaignEventType;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedCampaignEventRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedDayCompletionRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedEnrollmentRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.UserRepository;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Shared validation, audience eligibility, operation mutation, and campaign analytics
 * helpers for Getting Started – 3 Days programs.
 */
@Component
public class GettingStartedCampaignHelper {

    public static final int EXPECTED_DAYS = 3;
    private static final int MAX_EXERCISES_PER_DAY = 10;

    public List<String> collectValidationErrors(AdminGettingStartedProgramRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Program request payload is required.");
            return errors;
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            errors.add("Program title is required.");
        } else if (request.getTitle().trim().length() > 150) {
            errors.add("Program title cannot exceed 150 characters.");
        }

        List<AdminGettingStartedDayRequest> days = request.getDays();
        if (days == null || days.size() != EXPECTED_DAYS) {
            errors.add("Getting Started program must have exactly 3 days (Day 1, Day 2, and Day 3).");
            return errors;
        }

        Set<Integer> seenDays = new HashSet<>();
        for (AdminGettingStartedDayRequest day : days) {
            if (day == null || day.getDayNumber() == null || day.getDayNumber() < 1 || day.getDayNumber() > EXPECTED_DAYS) {
                errors.add("Each day must specify a valid dayNumber (1, 2, or 3).");
                continue;
            }
            if (!seenDays.add(day.getDayNumber())) {
                errors.add("Duplicate dayNumber found: Day " + day.getDayNumber());
            }
            if (day.getTitle() == null || day.getTitle().isBlank()) {
                errors.add("Day " + day.getDayNumber() + " title is required.");
            }
            List<AdminGettingStartedExerciseRequest> exercises = day.getExercises();
            if (exercises == null || exercises.isEmpty()) {
                errors.add("Day " + day.getDayNumber() + " must contain at least 1 exercise.");
                continue;
            }
            if (exercises.size() > MAX_EXERCISES_PER_DAY) {
                errors.add("Day " + day.getDayNumber() + " cannot contain more than " + MAX_EXERCISES_PER_DAY + " exercises.");
            }
            for (int i = 0; i < exercises.size(); i++) {
                validateExerciseRequest(day.getDayNumber(), i + 1, exercises.get(i), errors);
            }
        }
        return errors;
    }

    public List<String> collectValidationErrors(GettingStartedProgram program) {
        if (program == null) {
            return List.of("Getting Started program not found.");
        }
        AdminGettingStartedProgramRequest snapshot = toAdminRequest(program);
        return collectValidationErrors(snapshot);
    }

    private void validateExerciseRequest(int dayNumber, int ordinal, AdminGettingStartedExerciseRequest ex, List<String> errors) {
        String label = "Day " + dayNumber + " exercise #" + ordinal;
        if (ex == null) {
            errors.add(label + " must not be null.");
            return;
        }
        if (ex.getExerciseName() == null || ex.getExerciseName().isBlank()) {
            errors.add(label + " requires a non-blank exerciseName.");
        } else if (ex.getExerciseName().trim().length() > 150) {
            errors.add(label + " exerciseName cannot exceed 150 characters.");
        }
        if (ex.getSets() == null || ex.getSets() < 1 || ex.getSets() > 10) {
            errors.add(label + " sets must be between 1 and 10.");
        }
        boolean hasReps = ex.getReps() != null && ex.getReps() > 0;
        boolean hasDuration = ex.getDurationSeconds() != null && ex.getDurationSeconds() > 0;
        if (!hasReps && !hasDuration) {
            errors.add(label + " must specify positive reps or durationSeconds.");
        }
        if (ex.getReps() != null && (ex.getReps() < 1 || ex.getReps() > 100)) {
            errors.add(label + " reps must be between 1 and 100.");
        }
        if (ex.getDurationSeconds() != null && (ex.getDurationSeconds() < 1 || ex.getDurationSeconds() > 300)) {
            errors.add(label + " durationSeconds must be between 1 and 300.");
        }
        if (ex.getRestSeconds() != null && (ex.getRestSeconds() < 0 || ex.getRestSeconds() > 600)) {
            errors.add(label + " restSeconds must be between 0 and 600.");
        }
    }

    public AdminGettingStartedProgramRequest toAdminRequest(GettingStartedProgram program) {
        List<AdminGettingStartedDayRequest> days = new ArrayList<>();
        if (program.getDays() != null) {
            for (GettingStartedProgramDay day : program.getDays()) {
                List<AdminGettingStartedExerciseRequest> exReqs = new ArrayList<>();
                if (day.getExercises() != null) {
                    List<GettingStartedProgramExercise> sorted = day.getExercises().stream()
                            .sorted(Comparator.comparingInt(e -> e.getDisplayOrder() != null ? e.getDisplayOrder() : 0))
                            .toList();
                    for (int i = 0; i < sorted.size(); i++) {
                        GettingStartedProgramExercise ex = sorted.get(i);
                        exReqs.add(AdminGettingStartedExerciseRequest.builder()
                                .exerciseId(ex.getExercise() != null ? ex.getExercise().getId() : null)
                                .exerciseName(ex.getExerciseName())
                                .sets(ex.getSets())
                                .reps(ex.getReps())
                                .durationSeconds(ex.getDurationSeconds())
                                .restSeconds(ex.getRestSeconds())
                                .instructions(ex.getInstructions())
                                .displayOrder(ex.getDisplayOrder() != null ? ex.getDisplayOrder() : i)
                                .build());
                    }
                }
                days.add(AdminGettingStartedDayRequest.builder()
                        .dayNumber(day.getDayNumber())
                        .title(day.getTitle())
                        .description(day.getDescription())
                        .exercises(exReqs)
                        .build());
            }
        }
        return AdminGettingStartedProgramRequest.builder()
                .title(program.getTitle())
                .description(program.getDescription())
                .targetAudience(program.getTargetAudience() != null ? program.getTargetAudience() : TargetAudience.ALL_ATHLETES)
                .days(days)
                .build();
    }

    public AdminGettingStartedProgramRequest applyOperations(
            GettingStartedProgram existing,
            List<GettingStartedExerciseOperationRequest> operations,
            ExerciseRepository exerciseRepository,
            String newTitle,
            String newDescription,
            TargetAudience newAudience
    ) {
        AdminGettingStartedProgramRequest base = toAdminRequest(existing);
        if (newTitle != null && !newTitle.isBlank()) {
            base.setTitle(newTitle.trim());
        }
        if (newDescription != null) {
            base.setDescription(newDescription.trim());
        }
        if (newAudience != null) {
            base.setTargetAudience(newAudience);
        }
        if (operations == null || operations.isEmpty()) {
            return base;
        }

        Map<Integer, AdminGettingStartedDayRequest> dayByNum = new TreeMap<>();
        for (AdminGettingStartedDayRequest day : base.getDays()) {
            dayByNum.put(day.getDayNumber(), day);
        }

        for (GettingStartedExerciseOperationRequest op : operations) {
            if (op == null || op.getDayNumber() == null) {
                throw new IllegalArgumentException("Each edit operation requires dayNumber (1, 2, or 3).");
            }
            AdminGettingStartedDayRequest day = dayByNum.get(op.getDayNumber());
            if (day == null) {
                throw new IllegalArgumentException("Invalid dayNumber: " + op.getDayNumber());
            }
            String opType = op.getOperation() != null ? op.getOperation().trim().toUpperCase(Locale.US) : "";
            switch (opType) {
                case "UPDATE_DAY" -> {
                    if (op.getDayTitle() != null && !op.getDayTitle().isBlank()) {
                        day.setTitle(op.getDayTitle().trim());
                    }
                    if (op.getDayDescription() != null) {
                        day.setDescription(op.getDayDescription().trim());
                    }
                }
                case "ADD_EXERCISE" -> applyAddExercise(day, op, exerciseRepository);
                case "REMOVE_EXERCISE" -> applyRemoveExercise(day, op);
                case "UPDATE_EXERCISE" -> applyUpdateExercise(day, op, exerciseRepository);
                case "REORDER_EXERCISES" -> applyReorderExercises(day, op);
                default -> throw new IllegalArgumentException("Unsupported edit operation: " + op.getOperation());
            }
            reindexExercises(day.getExercises());
        }

        base.setDays(new ArrayList<>(dayByNum.values()));
        return base;
    }

    private void applyAddExercise(
            AdminGettingStartedDayRequest day,
            GettingStartedExerciseOperationRequest op,
            ExerciseRepository exerciseRepository
    ) {
        Exercise resolved = resolveCatalogExercise(op.getExerciseId(), op.getExerciseName(), exerciseRepository);
        String name = resolved != null ? resolved.getName()
                : (op.getExerciseName() != null ? op.getExerciseName().trim() : null);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ADD_EXERCISE requires exerciseId or exerciseName.");
        }

        boolean isStatic = resolved != null && AiWorkoutPlanValidator.isStaticHoldExercise(resolved);
        Integer sets = op.getSets() != null ? op.getSets() : 3;
        Integer reps = op.getReps();
        Integer duration = op.getDurationSeconds();
        if (reps == null && duration == null) {
            if (isStatic) {
                duration = 20;
            } else {
                reps = 10;
            }
        }
        int rest = op.getRestSeconds() != null ? op.getRestSeconds() : 60;

        AdminGettingStartedExerciseRequest added = AdminGettingStartedExerciseRequest.builder()
                .exerciseId(resolved != null ? resolved.getId() : op.getExerciseId())
                .exerciseName(name)
                .sets(sets)
                .reps(reps)
                .durationSeconds(duration)
                .restSeconds(rest)
                .instructions(op.getInstructions())
                .displayOrder(day.getExercises().size())
                .build();

        if (op.getDisplayOrder() != null && op.getDisplayOrder() >= 0 && op.getDisplayOrder() < day.getExercises().size()) {
            day.getExercises().add(op.getDisplayOrder(), added);
        } else {
            day.getExercises().add(added);
        }
    }

    private void applyRemoveExercise(AdminGettingStartedDayRequest day, GettingStartedExerciseOperationRequest op) {
        int idx = findExerciseIndex(day.getExercises(), op.getExerciseId(),
                op.getTargetExerciseName() != null ? op.getTargetExerciseName() : op.getExerciseName(),
                op.getDisplayOrder());
        if (idx < 0) {
            throw new IllegalArgumentException("Target exercise not found on Day " + day.getDayNumber() + " to remove.");
        }
        day.getExercises().remove(idx);
    }

    private void applyUpdateExercise(
            AdminGettingStartedDayRequest day,
            GettingStartedExerciseOperationRequest op,
            ExerciseRepository exerciseRepository
    ) {
        String lookupName = op.getTargetExerciseName() != null ? op.getTargetExerciseName() : op.getExerciseName();
        int idx = findExerciseIndex(day.getExercises(), op.getExerciseId(), lookupName, op.getDisplayOrder());
        if (idx < 0) {
            throw new IllegalArgumentException("Target exercise not found on Day " + day.getDayNumber() + " to update.");
        }
        AdminGettingStartedExerciseRequest target = day.getExercises().get(idx);
        if (op.getExerciseId() != null && (target.getExerciseId() == null || !op.getExerciseId().equals(target.getExerciseId()))) {
            Exercise resolved = resolveCatalogExercise(op.getExerciseId(), op.getExerciseName(), exerciseRepository);
            if (resolved != null) {
                target.setExerciseId(resolved.getId());
                target.setExerciseName(resolved.getName());
            } else {
                target.setExerciseId(op.getExerciseId());
            }
        } else if (op.getExerciseName() != null && !op.getExerciseName().isBlank() && op.getTargetExerciseName() != null) {
            Exercise resolved = resolveCatalogExercise(null, op.getExerciseName(), exerciseRepository);
            target.setExerciseId(resolved != null ? resolved.getId() : target.getExerciseId());
            target.setExerciseName(resolved != null ? resolved.getName() : op.getExerciseName().trim());
        }
        if (op.getSets() != null) {
            target.setSets(op.getSets());
        }
        if (op.getReps() != null) {
            target.setReps(op.getReps());
            if (op.getDurationSeconds() == null) {
                target.setDurationSeconds(null);
            }
        }
        if (op.getDurationSeconds() != null) {
            target.setDurationSeconds(op.getDurationSeconds());
            if (op.getReps() == null) {
                target.setReps(null);
            }
        }
        if (op.getRestSeconds() != null) {
            target.setRestSeconds(op.getRestSeconds());
        }
        if (op.getInstructions() != null) {
            target.setInstructions(op.getInstructions().trim());
        }
        if (op.getNewOrder() != null && op.getNewOrder() >= 0 && op.getNewOrder() < day.getExercises().size()) {
            day.getExercises().remove(idx);
            day.getExercises().add(op.getNewOrder(), target);
        }
    }

    private void applyReorderExercises(AdminGettingStartedDayRequest day, GettingStartedExerciseOperationRequest op) {
        List<AdminGettingStartedExerciseRequest> current = day.getExercises();
        if (op.getOrderedExerciseNames() != null && !op.getOrderedExerciseNames().isEmpty()) {
            List<AdminGettingStartedExerciseRequest> reordered = new ArrayList<>();
            List<AdminGettingStartedExerciseRequest> remaining = new ArrayList<>(current);
            for (String wantedName : op.getOrderedExerciseNames()) {
                int matchIdx = findExerciseIndex(remaining, null, wantedName, null);
                if (matchIdx >= 0) {
                    reordered.add(remaining.remove(matchIdx));
                }
            }
            reordered.addAll(remaining);
            current.clear();
            current.addAll(reordered);
            return;
        }
        if (op.getOrderedExerciseIds() != null && !op.getOrderedExerciseIds().isEmpty()) {
            List<AdminGettingStartedExerciseRequest> reordered = new ArrayList<>();
            List<AdminGettingStartedExerciseRequest> remaining = new ArrayList<>(current);
            for (Long wantedId : op.getOrderedExerciseIds()) {
                int matchIdx = findExerciseIndex(remaining, wantedId, null, null);
                if (matchIdx >= 0) {
                    reordered.add(remaining.remove(matchIdx));
                }
            }
            reordered.addAll(remaining);
            current.clear();
            current.addAll(reordered);
            return;
        }
        String lookupName = op.getTargetExerciseName() != null ? op.getTargetExerciseName() : op.getExerciseName();
        int idx = findExerciseIndex(current, op.getExerciseId(), lookupName, op.getDisplayOrder());
        if (idx < 0 || op.getNewOrder() == null) {
            throw new IllegalArgumentException("REORDER_EXERCISES requires orderedExerciseNames/Ids or a target exercise with newOrder.");
        }
        int boundedTarget = Math.max(0, Math.min(op.getNewOrder(), current.size() - 1));
        AdminGettingStartedExerciseRequest item = current.remove(idx);
        current.add(boundedTarget, item);
    }

    private int findExerciseIndex(
            List<AdminGettingStartedExerciseRequest> list,
            Long exerciseId,
            String exerciseName,
            Integer displayOrder
    ) {
        if (exerciseId != null) {
            for (int i = 0; i < list.size(); i++) {
                if (exerciseId.equals(list.get(i).getExerciseId())) {
                    return i;
                }
            }
        }
        if (exerciseName != null && !exerciseName.isBlank()) {
            String trimmed = exerciseName.trim();
            for (int i = 0; i < list.size(); i++) {
                if (trimmed.equalsIgnoreCase(list.get(i).getExerciseName())) {
                    return i;
                }
            }
            String lower = trimmed.toLowerCase(Locale.US);
            for (int i = 0; i < list.size(); i++) {
                String candidate = list.get(i).getExerciseName();
                if (candidate != null && candidate.toLowerCase(Locale.US).contains(lower)) {
                    return i;
                }
            }
        }
        if (displayOrder != null && displayOrder >= 0 && displayOrder < list.size()) {
            return displayOrder;
        }
        return -1;
    }

    private Exercise resolveCatalogExercise(Long exerciseId, String exerciseName, ExerciseRepository exerciseRepository) {
        if (exerciseRepository == null) {
            return null;
        }
        if (exerciseId != null) {
            Optional<Exercise> byId = exerciseRepository.findByIdAndActiveTrue(exerciseId)
                    .or(() -> exerciseRepository.findById(exerciseId));
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (exerciseName != null && !exerciseName.isBlank()) {
            Optional<Exercise> exact = exerciseRepository.findByNameIgnoreCase(exerciseName.trim());
            if (exact.isPresent()) {
                return exact.get();
            }
            String lower = exerciseName.trim().toLowerCase(Locale.US);
            return exerciseRepository.findByActiveTrue().stream()
                    .filter(ex -> ex.getName() != null && ex.getName().toLowerCase(Locale.US).contains(lower))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void reindexExercises(List<AdminGettingStartedExerciseRequest> exercises) {
        for (int i = 0; i < exercises.size(); i++) {
            exercises.get(i).setDisplayOrder(i);
        }
    }

    public TargetAudience parseTargetAudience(String raw) {
        if (raw == null || raw.isBlank()) {
            return TargetAudience.ALL_ATHLETES;
        }
        String normalized = raw.trim().toUpperCase(Locale.US)
                .replace('-', '_')
                .replace('–', '_')
                .replace(' ', '_');
        if (normalized.equals("ALL_ATHLETES") || normalized.contains("ALL_ATHLETE") || normalized.equals("ALL") || normalized.contains("EVERYONE")) {
            return TargetAudience.ALL_ATHLETES;
        }
        if (normalized.equals("LAST_14_DAYS") || normalized.contains("14")) {
            return TargetAudience.LAST_14_DAYS;
        }
        if (normalized.equals("LAST_30_DAYS") || normalized.contains("30")) {
            return TargetAudience.LAST_30_DAYS;
        }
        throw new IllegalArgumentException("Unsupported target audience: '" + raw + "'. Allowed values: ALL_ATHLETES, LAST_30_DAYS, LAST_14_DAYS.");
    }

    public String audienceLabel(TargetAudience audience) {
        TargetAudience resolved = audience != null ? audience : TargetAudience.ALL_ATHLETES;
        return switch (resolved) {
            case ALL_ATHLETES -> "ALL ATHLETES";
            case LAST_30_DAYS -> "NEW MEMBERS – LAST 30 DAYS";
            case LAST_14_DAYS -> "NEW MEMBERS – LAST 14 DAYS";
        };
    }

    public long countEligibleAthletes(TargetAudience audience, UserRepository userRepository) {
        TargetAudience resolved = audience != null ? audience : TargetAudience.ALL_ATHLETES;
        ZonedDateTime now = ZonedDateTime.now();
        return switch (resolved) {
            case ALL_ATHLETES -> userRepository.countByRole(Role.ROLE_USER);
            case LAST_30_DAYS -> userRepository.countByRoleAndCreatedAtGreaterThanEqual(Role.ROLE_USER, now.minusDays(30));
            case LAST_14_DAYS -> userRepository.countByRoleAndCreatedAtGreaterThanEqual(Role.ROLE_USER, now.minusDays(14));
        };
    }

    public GettingStartedCampaignAnalyticsResponse buildAnalyticsResponse(
            GettingStartedProgram program,
            UserRepository userRepository,
            GettingStartedCampaignEventRepository eventRepository,
            GettingStartedEnrollmentRepository enrollmentRepository,
            GettingStartedDayCompletionRepository dayCompletionRepository
    ) {
        TargetAudience audience = program.getTargetAudience() != null ? program.getTargetAudience() : TargetAudience.ALL_ATHLETES;
        long eligible = countEligibleAthletes(audience, userRepository);
        long impressions = eventRepository != null
                ? eventRepository.countByProgramIdAndEventType(program.getId(), GettingStartedCampaignEventType.MODAL_IMPRESSION)
                : 0L;
        long dismissals = eventRepository != null
                ? eventRepository.countByProgramIdAndEventType(program.getId(), GettingStartedCampaignEventType.MODAL_DISMISSAL)
                : 0L;
        long detailViews = eventRepository != null
                ? eventRepository.countByProgramIdAndEventType(program.getId(), GettingStartedCampaignEventType.PROGRAM_DETAIL_VIEW)
                : 0L;
        long enrollments = enrollmentRepository.countByProgramId(program.getId());
        long day1 = dayCompletionRepository.countByEnrollmentProgramIdAndDayNumber(program.getId(), 1);
        long day2 = dayCompletionRepository.countByEnrollmentProgramIdAndDayNumber(program.getId(), 2);
        long day3 = dayCompletionRepository.countByEnrollmentProgramIdAndDayNumber(program.getId(), 3);
        long completedEnrollments = enrollmentRepository.countByProgramIdAndStatus(
                program.getId(), GettingStartedEnrollmentStatus.COMPLETED);

        return GettingStartedCampaignAnalyticsResponse.builder()
                .programId(program.getId())
                .programTitle(program.getTitle())
                .published(program.isPublished())
                .version(program.getVersion())
                .targetAudience(audience)
                .audienceLabel(audienceLabel(audience))
                .eligibleAthleteCount(eligible)
                .modalImpressions(impressions)
                .modalDismissals(dismissals)
                .programDetailViews(detailViews)
                .enrollments(enrollments)
                .day1Completions(day1)
                .day2Completions(day2)
                .day3Completions(day3)
                .day1CompletionRate(ratePercent(day1, enrollments))
                .day2CompletionRate(ratePercent(day2, enrollments))
                .day3CompletionRate(ratePercent(day3, enrollments))
                .completedEnrollments(completedEnrollments)
                .overallCompletionRate(ratePercent(completedEnrollments, enrollments))
                .build();
    }

    private double ratePercent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        double pct = (numerator * 100.0) / denominator;
        return Math.min(100.0, Math.round(pct * 100.0) / 100.0);
    }
}
