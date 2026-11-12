package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.AdminPlatformOverviewResponse;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlatformAnalyticsService {

    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachClientRelationshipRepository coachClientRelationshipRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final ExerciseRepository exerciseRepository;

    public record DateWindow(AdminAnalyticsRange range, LocalDate startDate, LocalDate endDate) {}

    public DateWindow resolveDateWindow(AdminAnalyticsRange requestedRange, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = LocalDate.now();
        AdminAnalyticsRange effectiveRange = requestedRange != null ? requestedRange : AdminAnalyticsRange.LAST_30_DAYS;
        LocalDate start;
        LocalDate end = today;

        switch (effectiveRange) {
            case TODAY -> start = today;
            case LAST_7_DAYS -> start = today.minusDays(6);
            case CUSTOM -> {
                end = customEnd != null ? customEnd : today;
                start = customStart != null ? customStart : end.minusDays(29);
                if (start.isAfter(end)) {
                    LocalDate tmp = start;
                    start = end;
                    end = tmp;
                }
            }
            case LAST_30_DAYS -> start = today.minusDays(29);
            default -> start = today.minusDays(29);
        }
        return new DateWindow(effectiveRange, start, end);
    }

    @Transactional(readOnly = true)
    public AdminPlatformOverviewResponse getOverview(
            AdminAnalyticsRange requestedRange,
            LocalDate customStart,
            LocalDate customEnd
    ) {
        DateWindow window = resolveDateWindow(requestedRange, customStart, customEnd);
        LocalDate startDate = window.startDate();
        LocalDate endDate = window.endDate();
        long periodDays = Math.max(1L, ChronoUnit.DAYS.between(startDate, endDate) + 1L);
        LocalDate prevEndDate = startDate.minusDays(1);
        LocalDate prevStartDate = prevEndDate.minusDays(periodDays - 1L);

        List<User> allUsers = userRepository.findAll();
        long totalRegisteredUsers = allUsers.size();
        long totalCoaches = coachProfileRepository.count();
        long totalClients = clientProfileRepository.count();
        long totalAdmins = userRepository.countByRole(Role.ROLE_ADMIN);

        long newRegistrationsInPeriod = 0L;
        long previousPeriodNewUsers = 0L;
        Map<LocalDate, Long> dailyNewUsers = new HashMap<>();

        for (User u : allUsers) {
            LocalDate createdDate = toLocalDate(u.getCreatedAt());
            if (createdDate != null) {
                if (!createdDate.isBefore(startDate) && !createdDate.isAfter(endDate)) {
                    newRegistrationsInPeriod++;
                    dailyNewUsers.merge(createdDate, 1L, Long::sum);
                } else if (!createdDate.isBefore(prevStartDate) && !createdDate.isAfter(prevEndDate)) {
                    previousPeriodNewUsers++;
                }
            }
        }

        List<CoachClientRelationship> relationships = coachClientRelationshipRepository.findAll();
        long activeRelationships = relationships.stream()
                .filter(r -> r.getStatus() == CoachClientRelationshipStatus.ACTIVE)
                .count();
        long pendingRelationships = relationships.stream()
                .filter(r -> r.getStatus() == CoachClientRelationshipStatus.PENDING)
                .count();

        List<WorkoutPlan> allPlans = workoutPlanRepository.findAll();
        long totalWorkoutPlans = allPlans.size();
        long activeWorkoutPlans = allPlans.stream().filter(WorkoutPlan::isActive).count();
        long aiGeneratedWorkoutPlans = allPlans.stream().filter(WorkoutPlan::isAiGenerated).count();
        long usersWithActivePlanCount = allPlans.stream()
                .filter(WorkoutPlan::isActive)
                .map(p -> p.getUser() != null ? p.getUser().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        List<WorkoutSession> allSessions = workoutSessionRepository.findAll();
        long totalCompletedWorkoutsAllTime = 0L;
        long completedWorkoutsInPeriod = 0L;
        long scheduledOrInProgressInPeriod = 0L;
        long previousPeriodCompletedWorkouts = 0L;
        Set<Long> activeUserIds = new HashSet<>();
        Map<LocalDate, Long> dailyCompletedWorkouts = new HashMap<>();

        for (WorkoutSession s : allSessions) {
            LocalDate sessionDate = resolveSessionDate(s);
            boolean inPeriod = sessionDate != null && !sessionDate.isBefore(startDate) && !sessionDate.isAfter(endDate);
            boolean inPrevPeriod = sessionDate != null && !sessionDate.isBefore(prevStartDate) && !sessionDate.isAfter(prevEndDate);

            if (s.getStatus() == WorkoutSessionStatus.COMPLETED) {
                totalCompletedWorkoutsAllTime++;
                if (inPeriod) {
                    completedWorkoutsInPeriod++;
                    dailyCompletedWorkouts.merge(sessionDate, 1L, Long::sum);
                    if (s.getUser() != null && s.getUser().getId() != null) {
                        activeUserIds.add(s.getUser().getId());
                    }
                } else if (inPrevPeriod) {
                    previousPeriodCompletedWorkouts++;
                }
            } else if (inPeriod) {
                scheduledOrInProgressInPeriod++;
                if (s.getStatus() == WorkoutSessionStatus.IN_PROGRESS && s.getUser() != null && s.getUser().getId() != null) {
                    activeUserIds.add(s.getUser().getId());
                }
            }
        }

        List<PersonalRecord> allPrs = personalRecordRepository.findAll();
        long totalRecordedPrsAllTime = allPrs.size();
        long recordedPrsInPeriod = 0L;
        long previousPeriodPrs = 0L;
        Map<LocalDate, Long> dailyPrs = new HashMap<>();

        for (PersonalRecord pr : allPrs) {
            LocalDate prDate = toLocalDate(pr.getAchievedAt() != null ? pr.getAchievedAt() : pr.getCreatedAt());
            if (prDate != null) {
                if (!prDate.isBefore(startDate) && !prDate.isAfter(endDate)) {
                    recordedPrsInPeriod++;
                    dailyPrs.merge(prDate, 1L, Long::sum);
                    if (pr.getUser() != null && pr.getUser().getId() != null) {
                        activeUserIds.add(pr.getUser().getId());
                    }
                } else if (!prDate.isBefore(prevStartDate) && !prDate.isAfter(prevEndDate)) {
                    previousPeriodPrs++;
                }
            }
        }

        List<Exercise> allExercises = exerciseRepository.findAll();
        long totalExercises = allExercises.size();
        long activeExercises = allExercises.stream().filter(Exercise::isActive).count();

        long activeUsersInPeriod = activeUserIds.size();
        double activeUserRate = totalRegisteredUsers > 0
                ? round2((activeUsersInPeriod * 100.0) / totalRegisteredUsers)
                : 0.0;

        long totalPeriodSessions = completedWorkoutsInPeriod + scheduledOrInProgressInPeriod;
        double workoutCompletionRate = totalPeriodSessions > 0
                ? round2((completedWorkoutsInPeriod * 100.0) / totalPeriodSessions)
                : (totalCompletedWorkoutsAllTime > 0 && allSessions.size() > 0
                        ? round2((totalCompletedWorkoutsAllTime * 100.0) / allSessions.size())
                        : 0.0);

        double avgWorkoutsPerActiveUser = activeUsersInPeriod > 0
                ? round2((double) completedWorkoutsInPeriod / activeUsersInPeriod)
                : 0.0;

        double workoutGrowth = computeGrowthPercent(previousPeriodCompletedWorkouts, completedWorkoutsInPeriod);
        double userGrowth = computeGrowthPercent(previousPeriodNewUsers, newRegistrationsInPeriod);

        List<AdminPlatformOverviewResponse.DailyTrendPointDto> dailyPoints = new ArrayList<>();
        long maxTrendDays = Math.min(periodDays, 31L);
        LocalDate cursor = endDate.minusDays(maxTrendDays - 1L);
        while (!cursor.isAfter(endDate)) {
            dailyPoints.add(AdminPlatformOverviewResponse.DailyTrendPointDto.builder()
                    .date(cursor)
                    .completedWorkouts(dailyCompletedWorkouts.getOrDefault(cursor, 0L))
                    .newUsers(dailyNewUsers.getOrDefault(cursor, 0L))
                    .recordedPrs(dailyPrs.getOrDefault(cursor, 0L))
                    .build());
            cursor = cursor.plusDays(1);
        }

        return AdminPlatformOverviewResponse.builder()
                .range(window.range())
                .startDate(startDate)
                .endDate(endDate)
                .totalRegisteredUsers(totalRegisteredUsers)
                .activeUsersInPeriod(activeUsersInPeriod)
                .newRegistrationsInPeriod(newRegistrationsInPeriod)
                .totalCoaches(totalCoaches)
                .totalClients(totalClients)
                .totalAdmins(totalAdmins)
                .activeCoachClientRelationships(activeRelationships)
                .pendingCoachClientRelationships(pendingRelationships)
                .totalWorkoutPlans(totalWorkoutPlans)
                .activeWorkoutPlans(activeWorkoutPlans)
                .aiGeneratedWorkoutPlans(aiGeneratedWorkoutPlans)
                .totalCompletedWorkoutsAllTime(totalCompletedWorkoutsAllTime)
                .completedWorkoutsInPeriod(completedWorkoutsInPeriod)
                .scheduledOrInProgressWorkoutsInPeriod(scheduledOrInProgressInPeriod)
                .totalRecordedPrsAllTime(totalRecordedPrsAllTime)
                .recordedPrsInPeriod(recordedPrsInPeriod)
                .totalExercises(totalExercises)
                .activeExercises(activeExercises)
                .engagementMetrics(AdminPlatformOverviewResponse.EngagementMetricsDto.builder()
                        .activeUserRatePercent(activeUserRate)
                        .workoutCompletionRatePercent(workoutCompletionRate)
                        .avgWorkoutsPerActiveUser(avgWorkoutsPerActiveUser)
                        .usersWithActivePlanCount(usersWithActivePlanCount)
                        .build())
                .activityTrends(AdminPlatformOverviewResponse.ActivityTrendSummaryDto.builder()
                        .previousPeriodCompletedWorkouts(previousPeriodCompletedWorkouts)
                        .previousPeriodNewUsers(previousPeriodNewUsers)
                        .previousPeriodPrs(previousPeriodPrs)
                        .workoutGrowthPercent(workoutGrowth)
                        .userGrowthPercent(userGrowth)
                        .dailyPoints(dailyPoints)
                        .build())
                .build();
    }

    private LocalDate resolveSessionDate(WorkoutSession s) {
        if (s.getWorkoutDate() != null) {
            return s.getWorkoutDate();
        }
        if (s.getCompletedAt() != null) {
            return s.getCompletedAt().toLocalDate();
        }
        return toLocalDate(s.getCreatedAt());
    }

    private LocalDate toLocalDate(ZonedDateTime zdt) {
        return zdt != null ? zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private double computeGrowthPercent(long previous, long current) {
        if (previous <= 0L) {
            return current > 0L ? 100.0 : 0.0;
        }
        return round2(((current - previous) * 100.0) / previous);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
