package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.AdminTrainingAnalyticsResponse;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.*;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service("adminTrainingAnalyticsService")
@RequiredArgsConstructor
public class TrainingAnalyticsService {

    private final PlatformAnalyticsService platformAnalyticsService;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutSessionExerciseRepository workoutSessionExerciseRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutPlanAssignmentRepository workoutPlanAssignmentRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final ExerciseRecommendationService exerciseRecommendationService;

    @Transactional(readOnly = true)
    public AdminTrainingAnalyticsResponse getTrainingAnalytics(
            AdminAnalyticsRange requestedRange,
            LocalDate customStart,
            LocalDate customEnd
    ) {
        PlatformAnalyticsService.DateWindow window =
                platformAnalyticsService.resolveDateWindow(requestedRange, customStart, customEnd);
        LocalDate startDate = window.startDate();
        LocalDate endDate = window.endDate();
        LocalDate today = LocalDate.now();

        List<Exercise> allExercises = exerciseRepository.findAll();
        Map<Long, Exercise> exerciseById = allExercises.stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(Exercise::getId, e -> e));

        // 1. Exercise Library Distribution
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (ExerciseCategory cat : ExerciseCategory.values()) {
            byCategory.put(cat.name(), 0L);
        }
        Map<String, Long> byDifficulty = new LinkedHashMap<>();
        for (DifficultyLevel diff : DifficultyLevel.values()) {
            byDifficulty.put(diff.name(), 0L);
        }
        for (Exercise ex : allExercises) {
            if (ex.getCategory() != null) {
                byCategory.merge(ex.getCategory().name(), 1L, Long::sum);
            }
            if (ex.getDifficulty() != null) {
                byDifficulty.merge(ex.getDifficulty().name(), 1L, Long::sum);
            }
        }

        // 2. Aggregate Exercise Usage from Sessions, Plans, and PRs
        List<WorkoutSessionExercise> allSessionExercises = workoutSessionExerciseRepository.findAll();
        List<WorkoutPlanExercise> allPlanExercises = workoutPlanExerciseRepository.findAll();
        List<PersonalRecord> allPrs = personalRecordRepository.findAll();

        Map<Long, Long> sessionTotalByExId = new HashMap<>();
        Map<Long, Long> sessionCompletedByExId = new HashMap<>();
        for (WorkoutSessionExercise wse : allSessionExercises) {
            if (wse.getExercise() != null && wse.getExercise().getId() != null) {
                Long exId = wse.getExercise().getId();
                sessionTotalByExId.merge(exId, 1L, Long::sum);
                if (wse.getStatus() == WorkoutSessionExerciseStatus.COMPLETED) {
                    sessionCompletedByExId.merge(exId, 1L, Long::sum);
                }
            }
        }

        Map<Long, Long> planCountByExId = new HashMap<>();
        for (WorkoutPlanExercise wpe : allPlanExercises) {
            if (wpe.getExercise() != null && wpe.getExercise().getId() != null) {
                planCountByExId.merge(wpe.getExercise().getId(), 1L, Long::sum);
            }
        }

        Map<Long, Long> prCountByExId = new HashMap<>();
        for (PersonalRecord pr : allPrs) {
            if (pr.getExercise() != null && pr.getExercise().getId() != null) {
                prCountByExId.merge(pr.getExercise().getId(), 1L, Long::sum);
            }
        }

        List<AdminTrainingAnalyticsResponse.ExerciseUsageStatDto> allUsageStats = new ArrayList<>();
        for (Exercise ex : allExercises) {
            Long exId = ex.getId();
            long sessTotal = sessionTotalByExId.getOrDefault(exId, 0L);
            long sessCompleted = sessionCompletedByExId.getOrDefault(exId, 0L);
            long planRefs = planCountByExId.getOrDefault(exId, 0L);
            long prs = prCountByExId.getOrDefault(exId, 0L);
            double compRate = sessTotal > 0 ? round2((sessCompleted * 100.0) / sessTotal) : 0.0;

            allUsageStats.add(AdminTrainingAnalyticsResponse.ExerciseUsageStatDto.builder()
                    .exerciseId(exId)
                    .exerciseName(ex.getName())
                    .category(ex.getCategory() != null ? ex.getCategory().name() : "UNKNOWN")
                    .difficulty(ex.getDifficulty() != null ? ex.getDifficulty().name() : "UNKNOWN")
                    .sessionPerformances(sessTotal)
                    .completedPerformances(sessCompleted)
                    .planAssignmentsCount(planRefs)
                    .prCount(prs)
                    .completionRatePercent(compRate)
                    .build());
        }

        List<AdminTrainingAnalyticsResponse.ExerciseUsageStatDto> mostPerformed = allUsageStats.stream()
                .sorted(Comparator
                        .comparingLong(AdminTrainingAnalyticsResponse.ExerciseUsageStatDto::getSessionPerformances).reversed()
                        .thenComparing(Comparator.comparingLong(AdminTrainingAnalyticsResponse.ExerciseUsageStatDto::getPlanAssignmentsCount).reversed())
                        .thenComparing(Comparator.comparingLong(AdminTrainingAnalyticsResponse.ExerciseUsageStatDto::getPrCount).reversed()))
                .limit(10)
                .toList();

        List<AdminTrainingAnalyticsResponse.ExerciseUsageStatDto> highestCompletion = allUsageStats.stream()
                .filter(s -> s.getSessionPerformances() > 0)
                .sorted(Comparator
                        .comparingDouble(AdminTrainingAnalyticsResponse.ExerciseUsageStatDto::getCompletionRatePercent).reversed()
                        .thenComparing(Comparator.comparingLong(AdminTrainingAnalyticsResponse.ExerciseUsageStatDto::getCompletedPerformances).reversed()))
                .limit(10)
                .toList();

        // 3. Skill Focus Breakdown
        List<ClientProfile> clientProfiles = clientProfileRepository.findAll();
        List<AdminTrainingAnalyticsResponse.SkillFocusStatDto> skillBreakdown = new ArrayList<>();
        for (String skill : exerciseRecommendationService.getSupportedSkillFamilies()) {
            String skillKey = skill.toLowerCase().replace("-", " ");
            List<Exercise> matchingExercises = allExercises.stream()
                    .filter(e -> matchesSkill(e, skillKey))
                    .toList();
            Set<Long> matchingIds = matchingExercises.stream().map(Exercise::getId).collect(Collectors.toSet());

            long planRefs = matchingIds.stream().mapToLong(id -> planCountByExId.getOrDefault(id, 0L)).sum();
            long sessPerfs = matchingIds.stream().mapToLong(id -> sessionTotalByExId.getOrDefault(id, 0L)).sum();
            long prs = matchingIds.stream().mapToLong(id -> prCountByExId.getOrDefault(id, 0L)).sum();
            long interestedUsers = clientProfiles.stream()
                    .filter(cp -> containsSkillText(cp.getTrainingInterests(), skillKey)
                            || containsSkillText(cp.getPrimaryGoals(), skillKey))
                    .count();

            List<String> missingProgressions =
                    exerciseRecommendationService.getMissingProgressionNamesForSkill(skill, allExercises);

            skillBreakdown.add(AdminTrainingAnalyticsResponse.SkillFocusStatDto.builder()
                    .skillName(skill)
                    .existingExerciseCount(matchingExercises.size())
                    .planReferencesCount(planRefs)
                    .sessionPerformancesCount(sessPerfs)
                    .prCount(prs)
                    .interestedUsersCount(interestedUsers)
                    .missingProgressions(missingProgressions)
                    .build());
        }

        // 4. Common Training Goals
        Map<String, Long> goalCounts = new LinkedHashMap<>();
        for (ClientProfile cp : clientProfiles) {
            extractTokens(cp.getPrimaryGoals()).forEach(goal -> goalCounts.merge(goal, 1L, Long::sum));
            extractTokens(cp.getTrainingInterests()).forEach(interest -> goalCounts.merge(interest, 1L, Long::sum));
        }
        long goalDenominator = Math.max(1L, clientProfiles.size());
        List<AdminTrainingAnalyticsResponse.GoalStatDto> commonGoals = goalCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> AdminTrainingAnalyticsResponse.GoalStatDto.builder()
                        .goal(e.getKey())
                        .userCount(e.getValue())
                        .percentage(round2((e.getValue() * 100.0) / goalDenominator))
                        .build())
                .toList();

        // 5. Inactive Users Summary & Workout Sessions Analysis
        List<User> allUsers = userRepository.findAll();
        List<WorkoutSession> allSessions = workoutSessionRepository.findAll();

        Map<Long, LocalDate> lastCompletedWorkoutByUserId = new HashMap<>();
        for (WorkoutSession s : allSessions) {
            if (s.getStatus() == WorkoutSessionStatus.COMPLETED && s.getUser() != null && s.getUser().getId() != null) {
                LocalDate d = s.getWorkoutDate() != null
                        ? s.getWorkoutDate()
                        : (s.getCompletedAt() != null ? s.getCompletedAt().toLocalDate() : toLocalDate(s.getCreatedAt()));
                if (d != null) {
                    lastCompletedWorkoutByUserId.merge(
                            s.getUser().getId(),
                            d,
                            (oldDate, newDate) -> newDate.isAfter(oldDate) ? newDate : oldDate
                    );
                }
            }
        }

        long nonAdminUsersCount = 0L;
        long inactive14Count = 0L;
        long inactive30Count = 0L;
        long neverCompletedCount = 0L;
        List<AdminTrainingAnalyticsResponse.InactiveUserItemDto> inactiveSamples = new ArrayList<>();

        for (User u : allUsers) {
            if (u.getRole() == Role.ROLE_ADMIN) {
                continue;
            }
            nonAdminUsersCount++;
            LocalDate lastWorkout = lastCompletedWorkoutByUserId.get(u.getId());
            LocalDate referenceDate = lastWorkout != null ? lastWorkout : toLocalDate(u.getCreatedAt());
            long daysInactive = referenceDate != null ? Math.max(0L, ChronoUnit.DAYS.between(referenceDate, today)) : 30L;

            if (lastWorkout == null) {
                neverCompletedCount++;
            }
            if (lastWorkout == null || daysInactive >= 14L) {
                inactive14Count++;
                if (daysInactive >= 30L) {
                    inactive30Count++;
                }
                if (inactiveSamples.size() < 10) {
                    inactiveSamples.add(AdminTrainingAnalyticsResponse.InactiveUserItemDto.builder()
                            .userId(u.getId())
                            .fullName(u.getFullName())
                            .role(u.getRole().name())
                            .lastWorkoutDate(lastWorkout)
                            .daysSinceLastActivity(daysInactive)
                            .build());
                }
            }
        }

        double inactiveRate = nonAdminUsersCount > 0
                ? round2((inactive14Count * 100.0) / nonAdminUsersCount)
                : 0.0;

        AdminTrainingAnalyticsResponse.InactiveUsersSummaryDto inactiveSummary =
                AdminTrainingAnalyticsResponse.InactiveUsersSummaryDto.builder()
                        .totalUsersEvaluated(nonAdminUsersCount)
                        .inactiveOver14DaysCount(inactive14Count)
                        .inactiveOver30DaysCount(inactive30Count)
                        .neverCompletedWorkoutCount(neverCompletedCount)
                        .inactiveRatePercent(inactiveRate)
                        .inactiveUserSamples(inactiveSamples)
                        .build();

        // 6. Plan Adherence Summary & Top Engaged Programs
        List<WorkoutPlan> allPlans = workoutPlanRepository.findAll();
        List<WorkoutPlanAssignment> allAssignments = workoutPlanAssignmentRepository.findAll();

        long usersWithActivePersonalPlan = allPlans.stream()
                .filter(WorkoutPlan::isActive)
                .map(p -> p.getUser() != null ? p.getUser().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long activeCoachAssignmentsCount = allAssignments.stream()
                .filter(a -> a.getStatus() == WorkoutPlanAssignmentStatus.ACTIVE)
                .count();

        long totalPlanSessions = 0L;
        long completedPlanSessions = 0L;
        Map<Long, Long> planCompletedSessionCount = new HashMap<>();
        Map<Long, Long> planTotalSessionCount = new HashMap<>();

        for (WorkoutSession s : allSessions) {
            if (s.getWorkoutPlan() != null && s.getWorkoutPlan().getId() != null) {
                Long pId = s.getWorkoutPlan().getId();
                totalPlanSessions++;
                planTotalSessionCount.merge(pId, 1L, Long::sum);
                if (s.getStatus() == WorkoutSessionStatus.COMPLETED) {
                    completedPlanSessions++;
                    planCompletedSessionCount.merge(pId, 1L, Long::sum);
                }
            }
        }

        double adherenceRate = totalPlanSessions > 0
                ? round2((completedPlanSessions * 100.0) / totalPlanSessions)
                : 0.0;

        AdminTrainingAnalyticsResponse.PlanAdherenceSummaryDto adherenceSummary =
                AdminTrainingAnalyticsResponse.PlanAdherenceSummaryDto.builder()
                        .usersWithActivePersonalPlan(usersWithActivePersonalPlan)
                        .activeCoachAssignmentsCount(activeCoachAssignmentsCount)
                        .completedAssignedSessions(completedPlanSessions)
                        .totalAssignedSessions(totalPlanSessions)
                        .adherenceRatePercent(adherenceRate)
                        .build();

        List<AdminTrainingAnalyticsResponse.ProgramEngagementStatDto> topPrograms = allPlans.stream()
                .map(plan -> {
                    long comp = planCompletedSessionCount.getOrDefault(plan.getId(), 0L);
                    long tot = planTotalSessionCount.getOrDefault(plan.getId(), 0L);
                    double rate = tot > 0 ? round2((comp * 100.0) / tot) : 0.0;
                    String ownerName = plan.getUser() != null && plan.getUser().getFullName() != null
                            ? plan.getUser().getFullName()
                            : "Platform User";
                    return AdminTrainingAnalyticsResponse.ProgramEngagementStatDto.builder()
                            .planId(plan.getId())
                            .planName(plan.getName())
                            .splitType(plan.getSplitType() != null ? plan.getSplitType().name() : "CUSTOM")
                            .ownerName(ownerName)
                            .aiGenerated(plan.isAiGenerated())
                            .completedSessionsCount(comp)
                            .totalSessionsCount(tot)
                            .completionRatePercent(rate)
                            .build();
                })
                .sorted(Comparator
                        .comparingLong(AdminTrainingAnalyticsResponse.ProgramEngagementStatDto::getCompletedSessionsCount).reversed()
                        .thenComparing(Comparator.comparingLong(AdminTrainingAnalyticsResponse.ProgramEngagementStatDto::getTotalSessionsCount).reversed()))
                .limit(8)
                .toList();

        return AdminTrainingAnalyticsResponse.builder()
                .range(window.range())
                .startDate(startDate)
                .endDate(endDate)
                .mostPerformedExercises(mostPerformed)
                .highestCompletionRateExercises(highestCompletion)
                .skillFocusBreakdown(skillBreakdown)
                .commonTrainingGoals(commonGoals)
                .inactiveUsersSummary(inactiveSummary)
                .planAdherenceSummary(adherenceSummary)
                .topEngagedPrograms(topPrograms)
                .exerciseLibraryByCategory(byCategory)
                .exerciseLibraryByDifficulty(byDifficulty)
                .build();
    }

    private boolean matchesSkill(Exercise ex, String skillKey) {
        String name = ex.getName() != null ? ex.getName().toLowerCase().replace("-", " ") : "";
        String code = ex.getCode() != null ? ex.getCode().toLowerCase().replace("_", " ") : "";
        String desc = ex.getDescription() != null ? ex.getDescription().toLowerCase() : "";
        return name.contains(skillKey) || code.contains(skillKey) || desc.contains(skillKey);
    }

    private boolean containsSkillText(String text, String skillKey) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.toLowerCase().replace("-", " ").contains(skillKey);
    }

    private List<String> extractTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;|/]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(this::capitalizeGoal)
                .toList();
    }

    private String capitalizeGoal(String s) {
        String clean = s.trim();
        if (clean.length() <= 1) {
            return clean.toUpperCase();
        }
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private LocalDate toLocalDate(ZonedDateTime zdt) {
        return zdt != null ? zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
