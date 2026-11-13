package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.data.*;
import com.calisthenics.backend.adminai.dto.data.AdminCoachClientStatisticsResponse.CoachPerformanceItem;
import com.calisthenics.backend.adminai.dto.data.AdminPlatformEngagementResponse.*;
import com.calisthenics.backend.adminai.dto.data.AdminUserDetailDossierResponse.CoachProfileSummary;
import com.calisthenics.backend.adminai.dto.data.AdminUserDetailDossierResponse.CoachRelationshipItem;
import com.calisthenics.backend.adminai.dto.data.AdminUserPersonalRecordsResponse.PersonalRecordItem;
import com.calisthenics.backend.adminai.dto.data.AdminUserPersonalRecordsResponse.SharedPrItem;
import com.calisthenics.backend.adminai.dto.data.AdminUserProgressResponse.*;
import com.calisthenics.backend.adminai.dto.data.AdminUserWorkoutHistoryResponse.AssignedPlanItem;
import com.calisthenics.backend.adminai.dto.data.AdminUserWorkoutHistoryResponse.PersonalPlanItem;
import com.calisthenics.backend.adminai.dto.data.AdminUserWorkoutHistoryResponse.WorkoutSessionItem;
import com.calisthenics.backend.adminai.repository.AdminAnalyticsQueryRepository;
import com.calisthenics.backend.adminai.repository.AdminAnalyticsQueryRepository.*;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dedicated Admin Data Access Layer providing authorized, read-only global user analytics,
 * system-wide statistics, segmentation, and user drill-down tools backed by batch/aggregate queries.
 */
@Service
@RequiredArgsConstructor
public class AdminDataAccessService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_INACTIVE_DAYS = 14;
    private static final int DEFAULT_STALE_PR_DAYS = 30;
    private static final int ID_BATCH_CHUNK_SIZE = 500;

    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminAnalyticsQueryRepository queryRepository;
    private final PlatformAnalyticsService platformAnalyticsService;

    public AdminTimeRangeMetadata buildTimeRangeMetadata(
            AdminAnalyticsRange range,
            LocalDate customStart,
            LocalDate customEnd
    ) {
        PlatformAnalyticsService.DateWindow window = platformAnalyticsService.resolveDateWindow(range, customStart, customEnd);
        long days = Math.max(1L, ChronoUnit.DAYS.between(window.startDate(), window.endDate()) + 1L);
        return AdminTimeRangeMetadata.builder()
                .range(window.range().name())
                .startDate(window.startDate())
                .endDate(window.endDate())
                .periodDays(days)
                .build();
    }

    // ---------------- Tool 1: get_user_statistics ----------------

    @Transactional
    public AdminGlobalUserStatisticsResponse getUserStatistics(
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_USER_STATISTICS);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);
        ZonedDateTime rangeStart = toStartZdt(timeRange.getStartDate());
        ZonedDateTime rangeEnd = toEndZdt(timeRange.getEndDate());
        ZonedDateTime now = ZonedDateTime.now();

        long totalUsers = queryRepository.countTotalUsers();
        long totalAdmins = queryRepository.countUsersByRole(Role.ROLE_ADMIN);
        long totalRegularUsers = queryRepository.countUsersByRole(Role.ROLE_USER);
        long totalCoaches = queryRepository.countTotalCoaches();
        long verifiedCoaches = queryRepository.countVerifiedCoaches();
        long totalClients = Math.max(0L, totalRegularUsers - totalCoaches);
        long newRegistrations = queryRepository.countNewUsersBetween(rangeStart, rangeEnd);

        long activeToday = queryRepository.countDistinctWorkoutActiveUsers(LocalDate.now(), now.toLocalDate().atStartOfDay(ZoneId.systemDefault()), now);
        long active7 = queryRepository.countDistinctWorkoutActiveUsers(LocalDate.now().minusDays(6), toStartZdt(LocalDate.now().minusDays(6)), now);
        long active30 = queryRepository.countDistinctWorkoutActiveUsers(LocalDate.now().minusDays(29), toStartZdt(LocalDate.now().minusDays(29)), now);
        long activeInRange = queryRepository.countDistinctWorkoutActiveUsers(timeRange.getStartDate(), rangeStart, rangeEnd);

        long inactive14Plus = queryRepository.countUsersWithoutRecentWorkout(now.minusDays(DEFAULT_INACTIVE_DAYS), LocalDate.now().minusDays(DEFAULT_INACTIVE_DAYS));
        long inactive7Plus = queryRepository.countUsersWithoutRecentWorkout(now.minusDays(7), LocalDate.now().minusDays(7));
        long atRisk7To14 = Math.max(0L, inactive7Plus - inactive14Plus);
        long activeUnder7 = Math.max(0L, totalUsers - inactive7Plus);

        Map<String, Long> relClientCounts = labelRowsToMap(queryRepository.countDistinctClientsByRelationshipStatus());
        long usersWithActiveCoach = relClientCounts.getOrDefault(CoachClientRelationshipStatus.ACTIVE.name(), 0L);
        long usersWithPendingInvite = relClientCounts.getOrDefault(CoachClientRelationshipStatus.PENDING.name(), 0L);
        long usersWithoutCoach = Math.max(0L, totalUsers - usersWithActiveCoach);

        long totalWorkoutsInRange = queryRepository.countWorkoutsInRange(rangeStart, rangeEnd, null);
        long completedWorkoutsInRange = queryRepository.countWorkoutsInRange(rangeStart, rangeEnd, WorkoutSessionStatus.COMPLETED);
        long incompleteWorkoutsInRange = Math.max(0L, totalWorkoutsInRange - completedWorkoutsInRange);
        double completionRate = totalWorkoutsInRange > 0 ? round2((completedWorkoutsInRange * 100.0) / totalWorkoutsInRange) : 0.0;

        long totalPrs = queryRepository.countTotalPersonalRecords();
        long newPrsInRange = queryRepository.countPersonalRecordsInRange(rangeStart, rangeEnd);
        long stalePrUsers = queryRepository.countUsersWithStalePrs(now.minusDays(DEFAULT_STALE_PR_DAYS));

        List<BucketCountDto> expBuckets = toBucketDtos(queryRepository.countExperienceLevelsDistribution(), totalUsers);
        List<BucketCountDto> activityBuckets = List.of(
                BucketCountDto.builder().label("ACTIVE").count(activeUnder7).percentage(pct(activeUnder7, totalUsers)).build(),
                BucketCountDto.builder().label("AT_RISK").count(atRisk7To14).percentage(pct(atRisk7To14, totalUsers)).build(),
                BucketCountDto.builder().label("INACTIVE").count(inactive14Plus).percentage(pct(inactive14Plus, totalUsers)).build()
        );
        List<BucketCountDto> goalBuckets = aggregateTopGoals(queryRepository.fetchNonEmptyPrimaryGoals(), totalUsers, 8);

        List<String> missingNotes = new ArrayList<>();
        if (goalBuckets.isEmpty()) {
            missingNotes.add("No primary fitness goals are populated in client_profiles yet.");
        }
        missingNotes.add("Dedicated available_equipment column does not exist on client_profiles; equipment is inferred from exercises.");

        AdminGlobalUserStatisticsResponse response = AdminGlobalUserStatisticsResponse.builder()
                .timeRange(timeRange)
                .totalRegisteredUsers(totalUsers)
                .totalRegularUsers(totalRegularUsers)
                .totalAdmins(totalAdmins)
                .totalCoaches(totalCoaches)
                .verifiedCoaches(verifiedCoaches)
                .totalClients(totalClients)
                .newRegistrationsInPeriod(newRegistrations)
                .activeUsersToday(activeToday)
                .activeUsers7Days(active7)
                .activeUsers30Days(active30)
                .activeUsersInRange(activeInRange)
                .inactiveUsers14PlusDays(inactive14Plus)
                .atRiskUsers7To14Days(atRisk7To14)
                .usersWithActiveCoach(usersWithActiveCoach)
                .usersWithPendingCoachInvite(usersWithPendingInvite)
                .usersWithoutCoach(usersWithoutCoach)
                .totalWorkoutsInRange(totalWorkoutsInRange)
                .completedWorkoutsInRange(completedWorkoutsInRange)
                .incompleteOrAbandonedWorkoutsInRange(incompleteWorkoutsInRange)
                .overallWorkoutCompletionRate(completionRate)
                .totalPersonalRecordsAllTime(totalPrs)
                .newPersonalRecordsInRange(newPrsInRange)
                .usersWithStalePrs30PlusDays(stalePrUsers)
                .experienceLevelDistribution(expBuckets)
                .activityStatusDistribution(activityBuckets)
                .topFitnessGoals(goalBuckets)
                .missingDataNotes(missingNotes)
                .build();

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_USER_STATISTICS,
                admin.getId(),
                null,
                "range=" + timeRange.getRange() + ",totalUsers=" + totalUsers
        );
        return response;
    }

    // ---------------- Tool 2: get_all_users (Paginated, Masked PII, O(1) Batch Queries) ----------------

    @Transactional
    public AdminPagedResult<AdminGlobalUserSummaryDto> getAllUsers(AdminUserSegmentFilter filter) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_ALL_USERS);
        AdminUserSegmentFilter effective = normalizeFilter(filter);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(
                effective.getRange(), effective.getStartDate(), effective.getEndDate());

        int page = effective.getPage();
        int size = effective.getSize();
        boolean includeEmail = Boolean.TRUE.equals(effective.getIncludeEmail());
        boolean requiresComputedEvaluation = hasComputedMetricFilterOrSort(effective);

        List<AdminGlobalUserSummaryDto> pageItems;
        long totalElements;
        int totalPages;

        if (!requiresComputedEvaluation) {
            totalElements = queryRepository.countFilteredUserBaseRows(effective);
            totalPages = (int) Math.ceil((double) totalElements / size);
            List<UserBaseRow> basePage = queryRepository.fetchFilteredUserBaseRows(effective, page * size, size);
            pageItems = enrichUserBaseRowsBatch(basePage, effective, timeRange, includeEmail);
        } else {
            List<UserBaseRow> candidates = queryRepository.fetchFilteredUserBaseRows(effective, null, null);
            List<AdminGlobalUserSummaryDto> enriched = enrichUserBaseRowsBatch(candidates, effective, timeRange, includeEmail);
            List<AdminGlobalUserSummaryDto> filtered = enriched.stream()
                    .filter(u -> matchesComputedPredicates(u, effective))
                    .sorted(buildUserComparator(effective.getSortBy()))
                    .toList();
            totalElements = filtered.size();
            totalPages = (int) Math.ceil((double) totalElements / size);
            int fromIdx = Math.min(page * size, filtered.size());
            int toIdx = Math.min(fromIdx + size, filtered.size());
            pageItems = filtered.subList(fromIdx, toIdx);
        }

        List<String> missingNotes = buildSummaryMissingNotes(pageItems);

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_ALL_USERS,
                admin.getId(),
                null,
                "page=" + page + ",size=" + size + ",matched=" + totalElements + ",includeEmail=" + includeEmail
        );

        return AdminPagedResult.<AdminGlobalUserSummaryDto>builder()
                .items(pageItems)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(page + 1 < totalPages)
                .hasPrevious(page > 0)
                .timeRange(timeRange)
                .missingDataNotes(missingNotes)
                .build();
    }

    // ---------------- Tool 3: get_user_details ----------------

    @Transactional
    public AdminUserDetailDossierResponse getUserDetails(
            Long userId,
            boolean includeEmail,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_USER_DETAILS);
        UserBaseRow base = requireUserBaseRow(userId);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);

        Optional<BodyMeasurementRow> latestMeasurement = queryRepository.fetchLatestBodyMeasurement(userId);
        List<String> equipment = queryRepository.fetchInferredEquipmentForUser(userId);
        List<CoachRelationshipRow> relRows = queryRepository.fetchCoachRelationshipsForUser(userId);

        AdminUserWorkoutHistoryResponse workouts = buildWorkoutHistoryInternal(userId, base.fullName(), timeRange);
        AdminUserPersonalRecordsResponse prs = buildPersonalRecordsInternal(userId, base.fullName(), timeRange);
        AdminUserProgressResponse progress = buildUserProgressInternal(userId, base.fullName(), timeRange, workouts, prs);
        AdminUserActivityResponse activity = buildUserActivityInternal(userId, base.fullName(), timeRange);

        CoachProfileSummary coachSummary = null;
        if (base.coachProfileId() != null) {
            long activeClients = relRows.stream()
                    .filter(r -> Objects.equals(r.coachUserId(), userId) && r.status() == CoachClientRelationshipStatus.ACTIVE)
                    .count();
            long pendingClients = relRows.stream()
                    .filter(r -> Objects.equals(r.coachUserId(), userId) && r.status() == CoachClientRelationshipStatus.PENDING)
                    .count();
            coachSummary = CoachProfileSummary.builder()
                    .displayName(base.coachDisplayName())
                    .specialties(base.coachSpecialties())
                    .yearsExperience(base.coachYearsExperience())
                    .languages(base.coachLanguages())
                    .isPublic(base.coachPublic())
                    .isAcceptingClients(base.coachAcceptingClients())
                    .isVerified(base.coachVerified())
                    .activeClientsCount(activeClients)
                    .pendingInvitationsCount(pendingClients)
                    .build();
        }

        List<CoachRelationshipItem> relationshipItems = relRows.stream()
                .map(r -> {
                    boolean isClient = Objects.equals(r.clientUserId(), userId);
                    return CoachRelationshipItem.builder()
                            .relationshipId(r.relationshipId())
                            .counterpartRole(isClient ? "COACH" : "CLIENT")
                            .counterpartUserId(isClient ? r.coachUserId() : r.clientUserId())
                            .counterpartName(isClient ? r.coachName() : r.clientName())
                            .status(r.status().name())
                            .requestedByRole(Objects.equals(r.requestedById(), r.coachUserId()) ? "COACH" : "CLIENT")
                            .createdAt(r.createdAt())
                            .updatedAt(r.updatedAt())
                            .build();
                })
                .toList();

        List<String> missingFields = new ArrayList<>();
        if (base.heightCm() == null) missingFields.add("heightCm is not set on client_profiles");
        if (latestMeasurement.isEmpty()) missingFields.add("weightKg / body measurements have not been recorded");
        if (isBlank(base.primaryGoals())) missingFields.add("primaryGoals is not set");
        if (isBlank(base.trainingExperience())) missingFields.add("trainingExperience description is not set");
        if (isBlank(base.preferredFrequency())) missingFields.add("preferredFrequency is not set");
        if (equipment.isEmpty()) {
            missingFields.add("availableEquipment is not stored directly on client_profiles and no exercise equipment usage was found");
        } else {
            missingFields.add("availableEquipment is inferred from workout history/plans (client_profiles has no dedicated equipment column)");
        }

        String category = resolveAccountCategory(base);
        String expLevel = resolveExperienceLevel(base);

        AdminUserDetailDossierResponse dossier = AdminUserDetailDossierResponse.builder()
                .timeRange(timeRange)
                .userId(base.userId())
                .fullName(base.fullName())
                .displayName(firstNonBlank(base.clientDisplayName(), base.coachDisplayName(), base.fullName()))
                .maskedEmail(maskEmail(base.email()))
                .email(includeEmail ? base.email() : null)
                .role(base.role().name())
                .accountCategory(category)
                .gender(base.gender() != null ? base.gender().name() : null)
                .bio(base.bio())
                .experienceLevel(expLevel)
                .trainingExperience(base.trainingExperience())
                .primaryGoals(base.primaryGoals())
                .trainingInterests(base.trainingInterests())
                .preferredFrequency(base.preferredFrequency())
                .coachingLanguage(base.coachingLanguage())
                .heightCm(base.heightCm())
                .latestWeightKg(latestMeasurement.map(BodyMeasurementRow::weightKg).orElse(null))
                .latestBodyFatPercentage(latestMeasurement.map(BodyMeasurementRow::bodyFatPercentage).orElse(null))
                .latestMeasurementAt(latestMeasurement.map(BodyMeasurementRow::measuredAt).orElse(null))
                .inferredAvailableEquipment(equipment)
                .registeredAt(base.registeredAt())
                .coachProfile(coachSummary)
                .workoutSummary(workouts)
                .personalRecordsSummary(prs)
                .progressSummary(progress)
                .activitySummary(activity)
                .coachRelationships(relationshipItems)
                .missingDataFields(missingFields)
                .build();

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_USER_DETAILS,
                admin.getId(),
                userId,
                "includeEmail=" + includeEmail + ",range=" + timeRange.getRange()
        );
        return dossier;
    }

    // ---------------- Tool 4: get_user_workout_history ----------------

    @Transactional
    public AdminUserWorkoutHistoryResponse getUserWorkoutHistory(
            Long userId,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_USER_WORKOUT_HISTORY);
        UserBaseRow base = requireUserBaseRow(userId);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);
        AdminUserWorkoutHistoryResponse response = buildWorkoutHistoryInternal(userId, base.fullName(), timeRange);

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_USER_WORKOUT_HISTORY,
                admin.getId(),
                userId,
                "range=" + timeRange.getRange() + ",sessions=" + response.getTotalSessions()
        );
        return response;
    }

    // ---------------- Tool 5: get_user_personal_records ----------------

    @Transactional
    public AdminUserPersonalRecordsResponse getUserPersonalRecords(
            Long userId,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_USER_PERSONAL_RECORDS);
        UserBaseRow base = requireUserBaseRow(userId);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);
        AdminUserPersonalRecordsResponse response = buildPersonalRecordsInternal(userId, base.fullName(), timeRange);

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_USER_PERSONAL_RECORDS,
                admin.getId(),
                userId,
                "range=" + timeRange.getRange() + ",totalPRs=" + response.getTotalRecords()
        );
        return response;
    }

    // ---------------- Tool 6: get_user_progress ----------------

    @Transactional
    public AdminUserProgressResponse getUserProgress(
            Long userId,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_USER_PROGRESS);
        UserBaseRow base = requireUserBaseRow(userId);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);
        AdminUserWorkoutHistoryResponse workouts = buildWorkoutHistoryInternal(userId, base.fullName(), timeRange);
        AdminUserPersonalRecordsResponse prs = buildPersonalRecordsInternal(userId, base.fullName(), timeRange);
        AdminUserProgressResponse response = buildUserProgressInternal(userId, base.fullName(), timeRange, workouts, prs);

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_USER_PROGRESS,
                admin.getId(),
                userId,
                "range=" + timeRange.getRange() + ",trend=" + response.getConsistencyTrend()
        );
        return response;
    }

    // ---------------- Tool 7: get_user_activity (Redacted private message contents) ----------------

    @Transactional
    public AdminUserActivityResponse getUserActivity(
            Long userId,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_USER_ACTIVITY);
        UserBaseRow base = requireUserBaseRow(userId);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);
        AdminUserActivityResponse response = buildUserActivityInternal(userId, base.fullName(), timeRange);

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_USER_ACTIVITY,
                admin.getId(),
                userId,
                "range=" + timeRange.getRange() + ",aiMessages=" + response.getAiMessagesInRange()
        );
        return response;
    }

    // ---------------- Tool 8: get_coach_client_statistics ----------------

    @Transactional
    public AdminCoachClientStatisticsResponse getCoachClientStatistics(
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_COACH_CLIENT_STATISTICS);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);

        long totalCoaches = queryRepository.countTotalCoaches();
        long publicCoaches = queryRepository.countPublicCoaches();
        long acceptingCoaches = queryRepository.countAcceptingCoaches();
        long verifiedCoaches = queryRepository.countVerifiedCoaches();

        Map<String, Long> relCounts = labelRowsToMap(queryRepository.countRelationshipsByStatus());
        long activeRel = relCounts.getOrDefault(CoachClientRelationshipStatus.ACTIVE.name(), 0L);
        long pendingRel = relCounts.getOrDefault(CoachClientRelationshipStatus.PENDING.name(), 0L);
        long rejectedRel = relCounts.getOrDefault(CoachClientRelationshipStatus.REJECTED.name(), 0L);
        long cancelledRel = relCounts.getOrDefault(CoachClientRelationshipStatus.CANCELLED.name(), 0L);
        long terminatedRel = relCounts.getOrDefault(CoachClientRelationshipStatus.TERMINATED.name(), 0L);
        long totalRel = activeRel + pendingRel + rejectedRel + cancelledRel + terminatedRel;

        Map<String, Long> clientRelCounts = labelRowsToMap(queryRepository.countDistinctClientsByRelationshipStatus());
        long clientsWithActiveCoach = clientRelCounts.getOrDefault(CoachClientRelationshipStatus.ACTIVE.name(), 0L);

        Map<String, Long> planAssignCounts = labelRowsToMap(queryRepository.countAssignedPlansByStatus());
        long activeAssign = planAssignCounts.getOrDefault(WorkoutPlanAssignmentStatus.ACTIVE.name(), 0L);
        long completedAssign = planAssignCounts.getOrDefault(WorkoutPlanAssignmentStatus.COMPLETED.name(), 0L);
        long totalAssign = planAssignCounts.values().stream().mapToLong(Long::longValue).sum();

        long totalSharedPrs = queryRepository.countTotalSharedPrs();
        long sharedWithFeedback = queryRepository.countSharedPrsWithFeedback();
        double feedbackRate = totalSharedPrs > 0 ? round2((sharedWithFeedback * 100.0) / totalSharedPrs) : 0.0;

        List<CoachAggregateRow> coachLeaders = queryRepository.fetchCoachLeaderboard(10);
        List<Long> coachIds = coachLeaders.stream().map(CoachAggregateRow::coachUserId).toList();
        Map<Long, Long> coachActiveAssign = queryRepository.fetchCoachActiveAssignmentCounts(coachIds);
        Map<Long, Long> coachFeedbackCounts = queryRepository.fetchCoachSharedPrFeedbackCounts(coachIds);

        List<CoachPerformanceItem> topCoaches = coachLeaders.stream()
                .map(c -> CoachPerformanceItem.builder()
                        .coachUserId(c.coachUserId())
                        .coachName(c.coachName())
                        .verified(c.verified())
                        .acceptingClients(c.acceptingClients())
                        .activeClients(c.activeClients())
                        .pendingInvitations(c.pendingClients())
                        .activeAssignedPlans(coachActiveAssign.getOrDefault(c.coachUserId(), 0L))
                        .sharedPrFeedbackCount(coachFeedbackCounts.getOrDefault(c.coachUserId(), 0L))
                        .build())
                .toList();

        // Candidates who may benefit from a coach: regular clients with no active coach & declining/at-risk/inactive or active goals
        AdminPagedResult<AdminGlobalUserSummaryDto> candidatesPage = getAllUsers(
                AdminUserSegmentFilter.builder()
                        .roleFilter("CLIENT")
                        .coachRelationshipStatus("NO_COACH")
                        .page(0)
                        .size(10)
                        .range(range)
                        .startDate(startDate)
                        .endDate(endDate)
                        .build()
        );

        List<String> missingNotes = new ArrayList<>();
        if (totalCoaches == 0) {
            missingNotes.add("No coach profiles currently exist on the platform.");
        }
        if (totalSharedPrs == 0) {
            missingNotes.add("No Personal Records have been shared with coaches yet.");
        }

        AdminCoachClientStatisticsResponse response = AdminCoachClientStatisticsResponse.builder()
                .timeRange(timeRange)
                .totalCoaches(totalCoaches)
                .publicCoaches(publicCoaches)
                .acceptingClientsCoaches(acceptingCoaches)
                .verifiedCoaches(verifiedCoaches)
                .totalRelationships(totalRel)
                .activeRelationships(activeRel)
                .pendingInvitations(pendingRel)
                .rejectedRelationships(rejectedRel)
                .cancelledRelationships(cancelledRel)
                .terminatedRelationships(terminatedRel)
                .clientsWithActiveCoach(clientsWithActiveCoach)
                .totalAssignedPlans(totalAssign)
                .activeAssignedPlans(activeAssign)
                .completedAssignedPlans(completedAssign)
                .totalSharedPrs(totalSharedPrs)
                .sharedPrsWithFeedback(sharedWithFeedback)
                .feedbackResponseRate(feedbackRate)
                .topCoaches(topCoaches)
                .unassignedCoachCandidates(candidatesPage.getItems())
                .missingDataNotes(missingNotes)
                .build();

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_COACH_CLIENT_STATISTICS,
                admin.getId(),
                null,
                "activeRelationships=" + activeRel + ",coaches=" + totalCoaches
        );
        return response;
    }

    // ---------------- Tool 9: get_platform_engagement ----------------

    @Transactional
    public AdminPlatformEngagementResponse getPlatformEngagement(
            String skillKeyword,
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate
    ) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.GET_PLATFORM_ENGAGEMENT);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(range, startDate, endDate);
        ZonedDateTime rangeStart = toStartZdt(timeRange.getStartDate());
        ZonedDateTime rangeEnd = toEndZdt(timeRange.getEndDate());
        ZonedDateTime now = ZonedDateTime.now();

        long dau = queryRepository.countPlatformActiveUsersInWindow(
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()), now, LocalDate.now());
        long wau = queryRepository.countPlatformActiveUsersInWindow(
                toStartZdt(LocalDate.now().minusDays(6)), now, LocalDate.now().minusDays(6));
        long mau = queryRepository.countPlatformActiveUsersInWindow(
                toStartZdt(LocalDate.now().minusDays(29)), now, LocalDate.now().minusDays(29));
        double stickiness = mau > 0 ? round2((dau * 100.0) / mau) : 0.0;

        List<Object[]> expRows = queryRepository.fetchExperienceComparisonAggregates(rangeStart, rangeEnd);
        List<ExperienceComparisonItem> comparisons = expRows.stream()
                .map(r -> {
                    long userCount = toLongVal(r[1]);
                    long activeUsers = toLongVal(r[2]);
                    long totalWorkouts = toLongVal(r[3]);
                    long completedWorkouts = toLongVal(r[4]);
                    long prCount = toLongVal(r[5]);
                    long aiMessages = toLongVal(r[6]);
                    return ExperienceComparisonItem.builder()
                            .experienceLevel((String) r[0])
                            .userCount(userCount)
                            .activeUserCount(activeUsers)
                            .activeRatePercent(pct(activeUsers, userCount))
                            .totalWorkouts(totalWorkouts)
                            .completedWorkouts(completedWorkouts)
                            .avgWorkoutsPerUser(userCount > 0 ? round2((double) totalWorkouts / userCount) : 0.0)
                            .avgCompletionRate(totalWorkouts > 0 ? round2((completedWorkouts * 100.0) / totalWorkouts) : 0.0)
                            .prCount(prCount)
                            .avgAiInteractionsPerUser(userCount > 0 ? round2((double) aiMessages / userCount) : 0.0)
                            .build();
                })
                .sorted(Comparator.comparingLong(ExperienceComparisonItem::getUserCount).reversed())
                .toList();

        List<ExerciseUsageItem> topExercises = queryRepository.fetchTopPerformedExercisesInRange(rangeStart, rangeEnd, 10).stream()
                .map(e -> ExerciseUsageItem.builder()
                        .exerciseId(e.exerciseId())
                        .exerciseName(e.exerciseName())
                        .category(e.category())
                        .difficulty(e.difficulty())
                        .totalPerformances(e.totalPerformances())
                        .completedPerformances(e.completedPerformances())
                        .completionRate(e.totalPerformances() > 0 ? round2((e.completedPerformances() * 100.0) / e.totalPerformances()) : 0.0)
                        .uniqueUsersCount(e.uniqueUsers())
                        .build())
                .toList();

        // Deduplicate by user + exercise keeping highest improvement
        List<SkillImproverRow> rawImprovers = queryRepository.fetchTopSkillImprovers(skillKeyword, rangeStart, rangeEnd, 15);
        Map<Long, SkillImproverItem> bestPerUser = new LinkedHashMap<>();
        for (SkillImproverRow row : rawImprovers) {
            bestPerUser.putIfAbsent(row.userId(), SkillImproverItem.builder()
                    .userId(row.userId())
                    .fullName(row.fullName())
                    .experienceLevel(row.experienceLevel())
                    .exerciseName(row.exerciseName())
                    .recordType(row.recordType())
                    .latestRecordValue(row.recordValue())
                    .previousRecordValue(row.previousRecordValue())
                    .unit(row.unit())
                    .improvementPercent(row.improvementPercentage())
                    .achievedAt(row.achievedAt())
                    .build());
        }
        List<SkillImproverItem> topImprovers = bestPerUser.values().stream().limit(10).toList();

        // Declining consistency & stale PR users via batch query (bounded)
        List<AdminGlobalUserSummaryDto> allSample = getAllUsersInternal(
                AdminUserSegmentFilter.builder()
                        .range(range)
                        .startDate(startDate)
                        .endDate(endDate)
                        .page(0)
                        .size(100)
                        .build(),
                false,
                timeRange
        );
        List<AdminGlobalUserSummaryDto> decliningUsers = allSample.stream()
                .filter(u -> "DECLINING".equals(u.getConsistencyTrend()))
                .limit(10)
                .toList();
        ZonedDateTime staleCutoff = now.minusDays(DEFAULT_STALE_PR_DAYS);
        List<AdminGlobalUserSummaryDto> stalePrUsers = allSample.stream()
                .filter(u -> u.getTotalPersonalRecords() > 0
                        && (u.getLastPrAchievedAt() == null || u.getLastPrAchievedAt().isBefore(staleCutoff)))
                .limit(10)
                .toList();

        Object[] chatAi = queryRepository.fetchPlatformChatAndAiSummary(
                rangeStart, rangeEnd, rangeStart.toInstant(), rangeEnd.toInstant());
        long plannerRuns = toLongVal(chatAi[4]);
        long plannerSuccess = toLongVal(chatAi[5]);
        @SuppressWarnings("unchecked")
        List<LabelCountRow> rawIntents = (List<LabelCountRow>) chatAi[9];
        long intentTotal = rawIntents.stream().mapToLong(LabelCountRow::count).sum();
        ChatAiUsageSummary chatAiSummary = ChatAiUsageSummary.builder()
                .totalChatMessagesInRange(toLongVal(chatAi[0]))
                .activeChatUsersInRange(toLongVal(chatAi[1]))
                .totalAiMessagesInRange(toLongVal(chatAi[2]))
                .activeAiUsersInRange(toLongVal(chatAi[3]))
                .workoutPlannerRunsInRange(plannerRuns)
                .workoutPlannerSuccessRatePercent(plannerRuns > 0 ? round2((plannerSuccess * 100.0) / plannerRuns) : 0.0)
                .prRecordsInRange(toLongVal(chatAi[6]))
                .liveAnalysisSessionsInRange(toLongVal(chatAi[7]))
                .videoAnalysesInRange(toLongVal(chatAi[8]))
                .topAiIntents(toBucketDtos(rawIntents, Math.max(1L, intentTotal)))
                .build();

        List<String> missingNotes = new ArrayList<>();
        if (topImprovers.isEmpty()) {
            missingNotes.add(isBlank(skillKeyword)
                    ? "No Personal Records with positive improvement_percentage recorded in selected window."
                    : "No Personal Record improvements matched skill '" + skillKeyword + "' in selected window.");
        }
        if (topExercises.isEmpty()) {
            missingNotes.add("No workout session exercises logged in selected time window.");
        }

        AdminPlatformEngagementResponse response = AdminPlatformEngagementResponse.builder()
                .timeRange(timeRange)
                .dauCount(dau)
                .wauCount(wau)
                .mauCount(mau)
                .stickinessDauToMauPercent(stickiness)
                .experienceComparisons(comparisons)
                .topPerformedExercises(topExercises)
                .topSkillImprovers(topImprovers)
                .decliningConsistencyUsers(decliningUsers)
                .stalePrUsers(stalePrUsers)
                .chatAndAiUsageSummary(chatAiSummary)
                .missingDataNotes(missingNotes)
                .build();

        adminAuthorizationService.logToolExecution(
                AdminToolName.GET_PLATFORM_ENGAGEMENT,
                admin.getId(),
                null,
                "skill=" + skillKeyword + ",range=" + timeRange.getRange() + ",dau=" + dau + ",mau=" + mau
        );
        return response;
    }

    // ---------------- Tool 10: analyze_user_segments ----------------

    @Transactional
    public AdminUserSegmentsResponse analyzeUserSegments(AdminUserSegmentFilter filter) {
        User admin = adminAuthorizationService.requireReadOnlyToolAccess(AdminToolName.ANALYZE_USER_SEGMENTS);
        AdminUserSegmentFilter effective = normalizeFilter(filter);
        AdminTimeRangeMetadata timeRange = buildTimeRangeMetadata(
                effective.getRange(), effective.getStartDate(), effective.getEndDate());
        boolean includeEmail = Boolean.TRUE.equals(effective.getIncludeEmail());

        List<UserBaseRow> candidates = queryRepository.fetchFilteredUserBaseRows(effective, null, null);
        List<AdminGlobalUserSummaryDto> enriched = enrichUserBaseRowsBatch(candidates, effective, timeRange, includeEmail);
        List<AdminGlobalUserSummaryDto> matched = enriched.stream()
                .filter(u -> matchesComputedPredicates(u, effective))
                .sorted(buildUserComparator(effective.getSortBy()))
                .toList();

        long matchedCount = matched.size();
        int page = effective.getPage();
        int size = effective.getSize();
        int totalPages = (int) Math.ceil((double) matchedCount / size);
        int fromIdx = Math.min(page * size, matched.size());
        int toIdx = Math.min(fromIdx + size, matched.size());
        List<AdminGlobalUserSummaryDto> pagedUsers = matched.subList(fromIdx, toIdx);

        List<BucketCountDto> expBreakdown = buildBreakdown(matched, AdminGlobalUserSummaryDto::getExperienceLevel);
        List<BucketCountDto> activityBreakdown = buildBreakdown(matched, AdminGlobalUserSummaryDto::getActivityStatus);
        List<BucketCountDto> coachBreakdown = buildBreakdown(matched, AdminGlobalUserSummaryDto::getCoachRelationshipStatus);
        List<BucketCountDto> goalBreakdown = aggregateTopGoals(
                matched.stream().map(AdminGlobalUserSummaryDto::getPrimaryGoals).filter(g -> !isBlank(g)).toList(),
                matchedCount,
                6
        );

        String summary = String.format(
                "Matched %d users for segment criteria [%s] within %s (%s to %s).",
                matchedCount,
                describeFilter(effective),
                timeRange.getRange(),
                timeRange.getStartDate(),
                timeRange.getEndDate()
        );

        List<String> missingNotes = buildSummaryMissingNotes(pagedUsers);
        if (matchedCount == 0) {
            missingNotes.add("No users matched all combined segment filters in the database.");
        }

        AdminUserSegmentsResponse response = AdminUserSegmentsResponse.builder()
                .timeRange(timeRange)
                .appliedFilter(effective)
                .segmentSummary(summary)
                .matchedCount(matchedCount)
                .returnedCount(pagedUsers.size())
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .users(pagedUsers)
                .experienceBreakdown(expBreakdown)
                .activityBreakdown(activityBreakdown)
                .goalBreakdown(goalBreakdown)
                .coachStatusBreakdown(coachBreakdown)
                .missingDataNotes(missingNotes)
                .build();

        adminAuthorizationService.logToolExecution(
                AdminToolName.ANALYZE_USER_SEGMENTS,
                admin.getId(),
                null,
                "matched=" + matchedCount + ",criteria=" + describeFilter(effective)
        );
        return response;
    }

    // ---------------- Batch Enrichment Helpers (Zero N+1) ----------------

    private List<AdminGlobalUserSummaryDto> getAllUsersInternal(
            AdminUserSegmentFilter filter,
            boolean includeEmail,
            AdminTimeRangeMetadata timeRange
    ) {
        List<UserBaseRow> baseRows = queryRepository.fetchFilteredUserBaseRows(filter, filter.getPage() * filter.getSize(), filter.getSize());
        return enrichUserBaseRowsBatch(baseRows, filter, timeRange, includeEmail);
    }

    private List<AdminGlobalUserSummaryDto> enrichUserBaseRowsBatch(
            List<UserBaseRow> baseRows,
            AdminUserSegmentFilter filter,
            AdminTimeRangeMetadata timeRange,
            boolean includeEmail
    ) {
        if (baseRows == null || baseRows.isEmpty()) {
            return List.of();
        }
        ZonedDateTime rangeStart = toStartZdt(timeRange.getStartDate());
        ZonedDateTime rangeEnd = toEndZdt(timeRange.getEndDate());
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime midpoint = now.minusDays(14);
        ZonedDateTime priorStart = now.minusDays(28);
        int inactiveDays = filter.getInactiveDaysThreshold() != null && filter.getInactiveDaysThreshold() > 0
                ? filter.getInactiveDaysThreshold()
                : DEFAULT_INACTIVE_DAYS;

        List<Long> allIds = baseRows.stream().map(UserBaseRow::userId).toList();
        Map<Long, UserWorkoutAggRow> workoutMap = new HashMap<>();
        Map<Long, long[]> trendMap = new HashMap<>();
        Map<Long, UserPrAggRow> prMap = new HashMap<>();
        Map<Long, Map<CoachClientRelationshipStatus, Long>> clientRelMap = new HashMap<>();
        Map<Long, Long> coachClientMap = new HashMap<>();
        Map<Long, UserActivityAggRow> aiMap = new HashMap<>();
        Map<Long, UserActivityAggRow> chatMap = new HashMap<>();
        Map<Long, ZonedDateTime> presenceMap = new HashMap<>();

        // Chunk IDs at 500 so IN-clauses stay safe at large scale while keeping O(1) batch queries per chunk
        for (int i = 0; i < allIds.size(); i += ID_BATCH_CHUNK_SIZE) {
            List<Long> chunk = allIds.subList(i, Math.min(i + ID_BATCH_CHUNK_SIZE, allIds.size()));
            // All-time completed/total workouts if no range filter requested, otherwise windowed workouts?
            // Notice: to evaluate 14-day inactivity & overall completion rate accurately even when range is 7d,
            // we pass null dates when evaluating global user summaries unless date filter explicitly targets window!
            // Actually passing null dates for workout aggregates lets daysSinceLastWorkout work accurately across history,
            // while AI/Chat aggregates record both totalCount & rangeCount.
            workoutMap.putAll(queryRepository.fetchWorkoutAggregatesForUsers(chunk, null, null));
            trendMap.putAll(queryRepository.fetchRecentVsPriorWorkoutCounts(chunk, midpoint, priorStart));
            prMap.putAll(queryRepository.fetchPrAggregatesForUsers(chunk));
            clientRelMap.putAll(queryRepository.fetchClientRelationshipCounts(chunk));
            coachClientMap.putAll(queryRepository.fetchCoachActiveClientCounts(chunk));
            aiMap.putAll(queryRepository.fetchAiConversationAggregates(chunk, rangeStart, rangeEnd));
            chatMap.putAll(queryRepository.fetchChatMessageAggregates(chunk, rangeStart, rangeEnd));
            presenceMap.putAll(queryRepository.fetchPresenceLastSeenMap(chunk));
        }

        List<AdminGlobalUserSummaryDto> summaries = new ArrayList<>(baseRows.size());
        for (UserBaseRow row : baseRows) {
            Long uid = row.userId();
            UserWorkoutAggRow wAgg = workoutMap.get(uid);
            UserPrAggRow prAgg = prMap.get(uid);
            UserActivityAggRow aiAgg = aiMap.get(uid);
            UserActivityAggRow chatAgg = chatMap.get(uid);

            long totalWorkouts = wAgg != null ? wAgg.totalWorkouts() : 0L;
            long completedWorkouts = wAgg != null ? wAgg.completedWorkouts() : 0L;
            long incompleteWorkouts = wAgg != null ? wAgg.abandonedOrIncompleteWorkouts() : 0L;
            double completionRate = totalWorkouts > 0 ? round2((completedWorkouts * 100.0) / totalWorkouts) : 0.0;

            ZonedDateTime lastCompletedWorkoutAt = wAgg != null ? wAgg.resolveEffectiveLastCompletedWorkoutAt() : null;
            Long daysSinceWorkout = lastCompletedWorkoutAt != null
                    ? Math.max(0L, ChronoUnit.DAYS.between(lastCompletedWorkoutAt.toLocalDate(), LocalDate.now()))
                    : null;

            ZonedDateTime lastActivity = maxZdt(
                    wAgg != null ? wAgg.resolveEffectiveLastWorkoutAt() : null,
                    aiAgg != null ? aiAgg.lastAt() : null,
                    chatAgg != null ? chatAgg.lastAt() : null,
                    presenceMap.get(uid)
            );

            String activityStatus = resolveActivityStatus(daysSinceWorkout, inactiveDays);

            long[] recentPrior = trendMap.getOrDefault(uid, new long[]{0L, 0L});
            String consistencyTrend = computeConsistencyTrend(recentPrior[0], recentPrior[1]);

            Map<CoachClientRelationshipStatus, Long> rels = clientRelMap.getOrDefault(uid, Map.of());
            long activeCoachCount = rels.getOrDefault(CoachClientRelationshipStatus.ACTIVE, 0L);
            long pendingCoachCount = rels.getOrDefault(CoachClientRelationshipStatus.PENDING, 0L);
            long terminatedCoachCount = rels.getOrDefault(CoachClientRelationshipStatus.TERMINATED, 0L);
            String coachStatus;
            if (row.coachProfileId() != null) {
                coachStatus = "COACH_ROLE";
            } else if (activeCoachCount > 0) {
                coachStatus = "HAS_ACTIVE_COACH";
            } else if (pendingCoachCount > 0) {
                coachStatus = "PENDING_INVITATION";
            } else if (terminatedCoachCount > 0) {
                coachStatus = "TERMINATED";
            } else {
                coachStatus = "NO_COACH";
            }

            summaries.add(AdminGlobalUserSummaryDto.builder()
                    .userId(uid)
                    .fullName(row.fullName())
                    .displayName(firstNonBlank(row.clientDisplayName(), row.coachDisplayName(), row.fullName()))
                    .maskedEmail(maskEmail(row.email()))
                    .email(includeEmail ? row.email() : null)
                    .role(row.role().name())
                    .accountCategory(resolveAccountCategory(row))
                    .experienceLevel(resolveExperienceLevel(row))
                    .trainingExperience(row.trainingExperience())
                    .primaryGoals(row.primaryGoals())
                    .trainingInterests(row.trainingInterests())
                    .preferredFrequency(row.preferredFrequency())
                    .registeredAt(row.registeredAt())
                    .lastActivityAt(lastActivity)
                    .activityStatus(activityStatus)
                    .daysSinceLastWorkout(daysSinceWorkout)
                    .totalWorkouts(totalWorkouts)
                    .completedWorkouts(completedWorkouts)
                    .abandonedOrIncompleteWorkouts(incompleteWorkouts)
                    .workoutCompletionRate(completionRate)
                    .totalPersonalRecords(prAgg != null ? prAgg.totalPrs() : 0L)
                    .bestPrImprovementPercent(prAgg != null && prAgg.maxImprovementPercent() != null
                            ? prAgg.maxImprovementPercent().doubleValue() : null)
                    .lastPrAchievedAt(prAgg != null ? prAgg.lastPrAt() : null)
                    .consistencyTrend(consistencyTrend)
                    .coachRelationshipStatus(coachStatus)
                    .coachCount(activeCoachCount)
                    .activeClientCount(coachClientMap.getOrDefault(uid, 0L))
                    .aiConversationsCount(aiAgg != null ? aiAgg.totalCount() : 0L)
                    .chatMessagesSentCount(chatAgg != null ? chatAgg.totalCount() : 0L)
                    .build());
        }
        return summaries;
    }

    // ---------------- Single-User Builders ----------------

    private AdminUserWorkoutHistoryResponse buildWorkoutHistoryInternal(
            Long userId,
            String fullName,
            AdminTimeRangeMetadata timeRange
    ) {
        ZonedDateTime startZdt = toStartZdt(timeRange.getStartDate());
        ZonedDateTime endZdt = toEndZdt(timeRange.getEndDate());
        ZonedDateTime now = ZonedDateTime.now();

        List<WorkoutSessionRow> sessionRows = queryRepository.fetchWorkoutSessionsForUser(userId, startZdt, endZdt, 100);
        long[] exCounts = queryRepository.countSessionExercisesForUser(userId, startZdt, endZdt);
        List<AssignedPlanRow> assignedRows = queryRepository.fetchAssignedPlansForClient(userId);
        List<PersonalPlanRow> personalRows = queryRepository.fetchPersonalPlansForUser(userId);
        Map<Long, long[]> trendMap = queryRepository.fetchRecentVsPriorWorkoutCounts(
                List.of(userId), now.minusDays(14), now.minusDays(28));
        long[] recentPrior = trendMap.getOrDefault(userId, new long[]{0L, 0L});

        long totalSessions = sessionRows.size();
        long completedSessions = sessionRows.stream().filter(s -> s.status() == WorkoutSessionStatus.COMPLETED).count();
        long inProgress = sessionRows.stream().filter(s -> s.status() == WorkoutSessionStatus.IN_PROGRESS).count();
        long scheduled = sessionRows.stream().filter(s -> s.status() == WorkoutSessionStatus.SCHEDULED).count();
        long cancelled = sessionRows.stream().filter(s -> s.status() == WorkoutSessionStatus.CANCELLED).count();
        double completionRate = totalSessions > 0 ? round2((completedSessions * 100.0) / totalSessions) : 0.0;

        long totalSeconds = sessionRows.stream().mapToLong(s -> s.durationSeconds() != null ? s.durationSeconds() : 0L).sum();
        long totalMinutes = totalSeconds / 60L;
        long timedCount = sessionRows.stream().filter(s -> s.durationSeconds() != null && s.durationSeconds() > 0).count();
        double avgDurationMin = timedCount > 0 ? round2((totalSeconds / 60.0) / timedCount) : 0.0;
        double weeks = Math.max(1.0, timeRange.getPeriodDays() / 7.0);
        double avgPerWeek = round2(completedSessions / weeks);

        List<WorkoutSessionItem> recentItems = sessionRows.stream().limit(20).map(this::toWorkoutSessionItem).toList();
        List<WorkoutSessionItem> incompleteItems = sessionRows.stream()
                .filter(s -> s.status() != WorkoutSessionStatus.COMPLETED)
                .limit(20)
                .map(this::toWorkoutSessionItem)
                .toList();

        List<AssignedPlanItem> assignedItems = assignedRows.stream()
                .map(a -> AssignedPlanItem.builder()
                        .assignmentId(a.assignmentId())
                        .workoutPlanId(a.workoutPlanId())
                        .planName(a.planName())
                        .splitType(a.splitType())
                        .coachName(a.coachName())
                        .status(a.status().name())
                        .startDate(a.startDate())
                        .endDate(a.endDate())
                        .notes(a.notes())
                        .build())
                .toList();

        List<PersonalPlanItem> personalItems = personalRows.stream()
                .map(p -> PersonalPlanItem.builder()
                        .workoutPlanId(p.workoutPlanId())
                        .name(p.name())
                        .splitType(p.splitType())
                        .active(p.active())
                        .aiGenerated(p.aiGenerated())
                        .createdAt(p.createdAt())
                        .build())
                .toList();

        List<String> missingNotes = new ArrayList<>();
        if (totalSessions == 0) {
            missingNotes.add("No workout sessions recorded in this time range.");
        } else if (timedCount == 0) {
            missingNotes.add("Session durationSeconds was not recorded for sessions in this range.");
        }

        return AdminUserWorkoutHistoryResponse.builder()
                .timeRange(timeRange)
                .userId(userId)
                .fullName(fullName)
                .totalSessions(totalSessions)
                .completedSessions(completedSessions)
                .inProgressSessions(inProgress)
                .scheduledSessions(scheduled)
                .cancelledSessions(cancelled)
                .completionRate(completionRate)
                .totalTrainingMinutes(totalMinutes)
                .averageDurationMinutes(avgDurationMin)
                .averageSessionsPerWeek(avgPerWeek)
                .consistencyTrend(computeConsistencyTrend(recentPrior[0], recentPrior[1]))
                .completedExercisesCount(exCounts[1])
                .pendingOrIncompleteExercisesCount(Math.max(0L, exCounts[0] - exCounts[1]))
                .assignedPlans(assignedItems)
                .personalPlans(personalItems)
                .recentSessions(recentItems)
                .abandonedOrIncompleteWorkouts(incompleteItems)
                .missingDataNotes(missingNotes)
                .build();
    }

    private AdminUserPersonalRecordsResponse buildPersonalRecordsInternal(
            Long userId,
            String fullName,
            AdminTimeRangeMetadata timeRange
    ) {
        ZonedDateTime startZdt = toStartZdt(timeRange.getStartDate());
        ZonedDateTime endZdt = toEndZdt(timeRange.getEndDate());

        List<PersonalRecordRow> prRows = queryRepository.fetchPersonalRecordsForUser(userId, 50);
        List<SharedPrRow> sharedRows = queryRepository.fetchSharedPrsForClient(userId);

        long inRangeCount = prRows.stream()
                .filter(pr -> pr.achievedAt() != null && !pr.achievedAt().isBefore(startZdt) && !pr.achievedAt().isAfter(endZdt))
                .count();
        List<BigDecimal> improvements = prRows.stream()
                .map(PersonalRecordRow::improvementPercentage)
                .filter(Objects::nonNull)
                .toList();
        Double avgImp = improvements.isEmpty() ? null : round2(improvements.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0));
        Double maxImp = improvements.isEmpty() ? null : round2(improvements.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0.0));

        ZonedDateTime lastPrAt = prRows.stream()
                .map(PersonalRecordRow::achievedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Long daysSinceLastPr = lastPrAt != null
                ? Math.max(0L, ChronoUnit.DAYS.between(lastPrAt.toLocalDate(), LocalDate.now()))
                : null;

        long feedbackCount = sharedRows.stream()
                .filter(s -> s.feedbackAt() != null || !isBlank(s.coachFeedback()))
                .count();

        List<String> missingNotes = new ArrayList<>();
        if (prRows.isEmpty()) {
            missingNotes.add("No Personal Records recorded for this user.");
        }

        return AdminUserPersonalRecordsResponse.builder()
                .timeRange(timeRange)
                .userId(userId)
                .fullName(fullName)
                .totalRecords(prRows.size())
                .recordsInRange(inRangeCount)
                .averageImprovementPercent(avgImp)
                .maxImprovementPercent(maxImp)
                .lastPrAchievedAt(lastPrAt)
                .daysSinceLastPr(daysSinceLastPr)
                .sharedPrCount(sharedRows.size())
                .coachFeedbackCount(feedbackCount)
                .records(prRows.stream().map(pr -> PersonalRecordItem.builder()
                        .recordId(pr.recordId())
                        .exerciseName(pr.exerciseName())
                        .recordType(pr.recordType())
                        .recordValue(pr.recordValue())
                        .unit(pr.unit())
                        .previousRecordValue(pr.previousRecordValue())
                        .improvementPercentage(pr.improvementPercentage())
                        .achievedAt(pr.achievedAt())
                        .notes(pr.notes())
                        .build()).toList())
                .sharingAndFeedbackHistory(sharedRows.stream().map(s -> SharedPrItem.builder()
                        .sharedId(s.sharedId())
                        .personalRecordId(s.personalRecordId())
                        .exerciseName(s.exerciseName())
                        .coachUserId(s.coachUserId())
                        .coachName(s.coachName())
                        .status(s.status())
                        .rating(s.rating())
                        .coachFeedback(s.coachFeedback())
                        .sharedAt(s.sharedAt())
                        .feedbackAt(s.feedbackAt())
                        .build()).toList())
                .missingDataNotes(missingNotes)
                .build();
    }

    private AdminUserProgressResponse buildUserProgressInternal(
            Long userId,
            String fullName,
            AdminTimeRangeMetadata timeRange,
            AdminUserWorkoutHistoryResponse workouts,
            AdminUserPersonalRecordsResponse prs
    ) {
        ZonedDateTime startZdt = toStartZdt(timeRange.getStartDate());
        ZonedDateTime endZdt = toEndZdt(timeRange.getEndDate());
        long spanDays = Math.max(1L, timeRange.getPeriodDays());
        LocalDate prevEnd = timeRange.getStartDate().minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(spanDays - 1);

        List<WorkoutSessionRow> prevSessions = queryRepository.fetchWorkoutSessionsForUser(
                userId, toStartZdt(prevStart), toEndZdt(prevEnd), 100);
        long prevCompleted = prevSessions.stream().filter(s -> s.status() == WorkoutSessionStatus.COMPLETED).count();
        long currentCompleted = workouts.getCompletedSessions();
        Double deltaPercent = prevCompleted > 0
                ? round2(((currentCompleted - prevCompleted) * 100.0) / prevCompleted)
                : null;

        List<WorkoutSessionRow> currentSessions = queryRepository.fetchWorkoutSessionsForUser(userId, startZdt, endZdt, 100);
        List<PeriodPointDto> weeklyPoints = buildWeeklyPoints(timeRange.getStartDate(), timeRange.getEndDate(), currentSessions);

        List<ExercisePerformanceItem> exercisePerformance = queryRepository.fetchUserExercisePerformance(userId, startZdt, endZdt).stream()
                .limit(15)
                .map(r -> {
                    long attempts = toLongVal(r[1]);
                    long completed = toLongVal(r[2]);
                    return ExercisePerformanceItem.builder()
                            .exerciseName((String) r[0])
                            .attempts(attempts)
                            .completedCount(completed)
                            .completionRate(attempts > 0 ? round2((completed * 100.0) / attempts) : 0.0)
                            .build();
                })
                .toList();

        // Strength progression grouped by exercise + recordType from PR list
        Map<String, List<PersonalRecordItem>> prGrouped = prs.getRecords().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getExerciseName() + "|" + p.getRecordType(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<StrengthProgressionItem> strengthItems = prGrouped.values().stream()
                .map(list -> {
                    PersonalRecordItem latest = list.get(0);
                    BigDecimal bestImp = list.stream()
                            .map(PersonalRecordItem::getImprovementPercentage)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null);
                    return StrengthProgressionItem.builder()
                            .exerciseName(latest.getExerciseName())
                            .recordType(latest.getRecordType())
                            .latestValue(latest.getRecordValue())
                            .unit(latest.getUnit())
                            .bestImprovementPercent(bestImp)
                            .prCount(list.size())
                            .lastAchievedAt(latest.getAchievedAt())
                            .build();
                })
                .toList();

        List<BodyMeasurementPointDto> bodyTrends = queryRepository.fetchBodyMeasurementsForUser(userId, 10).stream()
                .map(b -> BodyMeasurementPointDto.builder()
                        .weightKg(b.weightKg())
                        .bodyFatPercentage(b.bodyFatPercentage())
                        .measuredAt(b.measuredAt())
                        .build())
                .toList();

        List<String> missingNotes = new ArrayList<>();
        if (bodyTrends.isEmpty()) missingNotes.add("No body measurement history recorded.");
        if (exercisePerformance.isEmpty()) missingNotes.add("No exercise-level performance data in selected range.");

        return AdminUserProgressResponse.builder()
                .timeRange(timeRange)
                .userId(userId)
                .fullName(fullName)
                .overallCompletionRate(workouts.getCompletionRate())
                .currentPeriodWorkouts(currentCompleted)
                .previousPeriodWorkouts(prevCompleted)
                .workoutVolumeDeltaPercent(deltaPercent)
                .consistencyTrend(workouts.getConsistencyTrend())
                .weeklySessionCounts(weeklyPoints)
                .exercisePerformance(exercisePerformance)
                .strengthProgression(strengthItems)
                .bodyMetricTrends(bodyTrends)
                .missingDataNotes(missingNotes)
                .build();
    }

    private AdminUserActivityResponse buildUserActivityInternal(
            Long userId,
            String fullName,
            AdminTimeRangeMetadata timeRange
    ) {
        ZonedDateTime startZdt = toStartZdt(timeRange.getStartDate());
        ZonedDateTime endZdt = toEndZdt(timeRange.getEndDate());

        Object[] presence = queryRepository.fetchUserPresence(userId);
        Object[] chat = queryRepository.fetchUserChatStats(userId, startZdt, endZdt);
        Object[] ai = queryRepository.fetchUserAiAssistantStats(userId, startZdt, endZdt);
        Object[] analysis = queryRepository.fetchUserPlannerAndAnalysisStats(
                userId, startZdt, endZdt, startZdt.toInstant(), endZdt.toInstant());

        List<WorkoutSessionRow> latestSession = queryRepository.fetchWorkoutSessionsForUser(
                userId, ZonedDateTime.now().minusYears(5), ZonedDateTime.now(), 1);
        ZonedDateTime lastWorkoutAt = latestSession.isEmpty() ? null : firstNonNullZdt(
                latestSession.get(0).completedAt(), latestSession.get(0).startedAt(), latestSession.get(0).createdAt());

        long prsInRange = queryRepository.fetchPersonalRecordsForUser(userId, 50).stream()
                .filter(p -> p.achievedAt() != null && !p.achievedAt().isBefore(startZdt) && !p.achievedAt().isAfter(endZdt))
                .count();

        @SuppressWarnings("unchecked")
        List<LabelCountRow> rawIntents = (List<LabelCountRow>) ai[4];
        long intentTotal = rawIntents.stream().mapToLong(LabelCountRow::count).sum();

        Instant liveLastInst = (Instant) analysis[4];
        ZonedDateTime liveLastZdt = liveLastInst != null ? liveLastInst.atZone(ZoneId.systemDefault()) : null;

        List<String> missingNotes = new ArrayList<>();
        if (presence == null) missingNotes.add("No presence heartbeat record exists for this user.");

        return AdminUserActivityResponse.builder()
                .timeRange(timeRange)
                .userId(userId)
                .fullName(fullName)
                .presenceStatus(presence != null ? String.valueOf(presence[0]) : "UNKNOWN")
                .lastSeenAt(presence != null ? (ZonedDateTime) presence[1] : null)
                .lastWorkoutAt(lastWorkoutAt)
                .lastChatMessageAt((ZonedDateTime) chat[3])
                .lastAiInteractionAt((ZonedDateTime) ai[3])
                .lastVideoAnalysisAt((ZonedDateTime) analysis[6])
                .lastLiveAnalysisAt(liveLastZdt)
                .chatConversationsCount(toLongVal(chat[0]))
                .chatMessagesSentTotal(toLongVal(chat[1]))
                .chatMessagesSentInRange(toLongVal(chat[2]))
                .aiConversationsCount(toLongVal(ai[0]))
                .aiMessagesTotal(toLongVal(ai[1]))
                .aiMessagesInRange(toLongVal(ai[2]))
                .workoutPlannerRunsTotal(toLongVal(analysis[0]))
                .workoutPlannerRunsInRange(toLongVal(analysis[1]))
                .workoutPlannerSuccessCount(toLongVal(analysis[2]))
                .liveAnalysisSessionsInRange(toLongVal(analysis[3]))
                .videoAnalysesInRange(toLongVal(analysis[5]))
                .prRecordsLoggedInRange(prsInRange)
                .topAiIntents(toBucketDtos(rawIntents, Math.max(1L, intentTotal)))
                .privacyNotice("Private chat and AI conversation message contents are redacted; only aggregate usage and intent metadata are returned.")
                .missingDataNotes(missingNotes)
                .build();
    }

    // ---------------- Filtering, Sorting & Formatting Utilities ----------------

    private AdminUserSegmentFilter normalizeFilter(AdminUserSegmentFilter raw) {
        AdminUserSegmentFilter f = raw != null ? raw : new AdminUserSegmentFilter();
        int page = f.getPage() != null && f.getPage() >= 0 ? f.getPage() : 0;
        int size = f.getSize() != null && f.getSize() > 0 ? Math.min(f.getSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        f.setPage(page);
        f.setSize(size);
        if (f.getInactiveDaysThreshold() == null || f.getInactiveDaysThreshold() <= 0) {
            f.setInactiveDaysThreshold(DEFAULT_INACTIVE_DAYS);
        }
        if (f.getStalePrDaysThreshold() == null || f.getStalePrDaysThreshold() <= 0) {
            f.setStalePrDaysThreshold(DEFAULT_STALE_PR_DAYS);
        }
        return f;
    }

    private boolean hasComputedMetricFilterOrSort(AdminUserSegmentFilter f) {
        if (!isBlank(f.getActivityStatus())
                || !isBlank(f.getPrProgressionStatus())
                || !isBlank(f.getCoachRelationshipStatus())
                || f.getMinCompletionRate() != null
                || f.getMaxCompletionRate() != null) {
            return true;
        }
        if (!isBlank(f.getSortBy())) {
            String s = f.getSortBy().toUpperCase();
            return !s.equals("CREATED_AT_DESC") && !s.equals("NAME_ASC");
        }
        return false;
    }

    private boolean matchesComputedPredicates(AdminGlobalUserSummaryDto u, AdminUserSegmentFilter f) {
        if (!isBlank(f.getActivityStatus())) {
            String target = f.getActivityStatus().trim().toUpperCase();
            if ("INACTIVE".equals(target)) {
                int threshold = f.getInactiveDaysThreshold() != null ? f.getInactiveDaysThreshold() : DEFAULT_INACTIVE_DAYS;
                if (u.getDaysSinceLastWorkout() != null && u.getDaysSinceLastWorkout() < threshold) {
                    return false;
                }
            } else if (!target.equalsIgnoreCase(u.getActivityStatus())) {
                return false;
            }
        }
        if (f.getMinCompletionRate() != null && u.getWorkoutCompletionRate() < f.getMinCompletionRate()) {
            return false;
        }
        if (f.getMaxCompletionRate() != null && u.getWorkoutCompletionRate() > f.getMaxCompletionRate()) {
            return false;
        }
        if (!isBlank(f.getPrProgressionStatus())) {
            String prTarget = f.getPrProgressionStatus().trim().toUpperCase();
            int staleDays = f.getStalePrDaysThreshold() != null ? f.getStalePrDaysThreshold() : DEFAULT_STALE_PR_DAYS;
            ZonedDateTime staleCutoff = ZonedDateTime.now().minusDays(staleDays);
            switch (prTarget) {
                case "NONE" -> {
                    if (u.getTotalPersonalRecords() > 0) return false;
                }
                case "STALE" -> {
                    if (u.getTotalPersonalRecords() == 0
                            || (u.getLastPrAchievedAt() != null && !u.getLastPrAchievedAt().isBefore(staleCutoff))) {
                        return false;
                    }
                }
                case "IMPROVING" -> {
                    if (u.getBestPrImprovementPercent() == null || u.getBestPrImprovementPercent() <= 0) {
                        return false;
                    }
                }
            }
        }
        if (!isBlank(f.getCoachRelationshipStatus())
                && !f.getCoachRelationshipStatus().trim().equalsIgnoreCase(u.getCoachRelationshipStatus())) {
            return false;
        }
        return true;
    }

    private Comparator<AdminGlobalUserSummaryDto> buildUserComparator(String sortBy) {
        String sort = sortBy != null ? sortBy.trim().toUpperCase() : "CREATED_AT_DESC";
        return switch (sort) {
            case "COMPLETION_RATE_DESC" -> Comparator.comparingDouble(AdminGlobalUserSummaryDto::getWorkoutCompletionRate).reversed()
                    .thenComparingLong(AdminGlobalUserSummaryDto::getUserId);
            case "COMPLETION_RATE_ASC" -> Comparator.comparingDouble(AdminGlobalUserSummaryDto::getWorkoutCompletionRate)
                    .thenComparingLong(AdminGlobalUserSummaryDto::getUserId);
            case "PR_IMPROVEMENT_DESC" -> Comparator.comparing(
                    AdminGlobalUserSummaryDto::getBestPrImprovementPercent,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparingLong(AdminGlobalUserSummaryDto::getUserId);
            case "WORKOUT_COUNT_DESC" -> Comparator.comparingLong(AdminGlobalUserSummaryDto::getCompletedWorkouts).reversed()
                    .thenComparingLong(AdminGlobalUserSummaryDto::getUserId);
            case "RECENT_ACTIVITY" -> Comparator.comparing(
                    AdminGlobalUserSummaryDto::getLastActivityAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparingLong(AdminGlobalUserSummaryDto::getUserId);
            case "NAME_ASC" -> Comparator.comparing(
                    AdminGlobalUserSummaryDto::getFullName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(AdminGlobalUserSummaryDto::getUserId);
            default -> Comparator.comparing(
                    AdminGlobalUserSummaryDto::getRegisteredAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Comparator.comparingLong(AdminGlobalUserSummaryDto::getUserId).reversed());
        };
    }

    private UserBaseRow requireUserBaseRow(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        return queryRepository.fetchUserBaseRowById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    private String resolveActivityStatus(Long daysSinceLastWorkout, int inactiveThreshold) {
        if (daysSinceLastWorkout == null || daysSinceLastWorkout >= inactiveThreshold) {
            return "INACTIVE";
        }
        if (daysSinceLastWorkout >= Math.max(7, inactiveThreshold / 2)) {
            return "AT_RISK";
        }
        return "ACTIVE";
    }

    private String computeConsistencyTrend(long recentCompleted, long priorCompleted) {
        if (recentCompleted + priorCompleted < 2L) {
            return "INSUFFICIENT_DATA";
        }
        if (priorCompleted > 0 && (recentCompleted == 0 || recentCompleted < priorCompleted * 0.7)) {
            return "DECLINING";
        }
        if (recentCompleted > priorCompleted * 1.25 && recentCompleted >= 2) {
            return "IMPROVING";
        }
        return "STABLE";
    }

    private List<PeriodPointDto> buildWeeklyPoints(
            LocalDate startDate,
            LocalDate endDate,
            List<WorkoutSessionRow> sessions
    ) {
        List<PeriodPointDto> points = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            LocalDate weekEnd = cursor.plusDays(6);
            if (weekEnd.isAfter(endDate)) {
                weekEnd = endDate;
            }
            LocalDate wStart = cursor;
            LocalDate wEnd = weekEnd;
            long total = 0L;
            long completed = 0L;
            for (WorkoutSessionRow s : sessions) {
                LocalDate d = s.workoutDate();
                if (d == null) {
                    ZonedDateTime z = firstNonNullZdt(s.completedAt(), s.startedAt(), s.createdAt());
                    d = z != null ? z.toLocalDate() : null;
                }
                if (d != null && !d.isBefore(wStart) && !d.isAfter(wEnd)) {
                    total++;
                    if (s.status() == WorkoutSessionStatus.COMPLETED) {
                        completed++;
                    }
                }
            }
            points.add(PeriodPointDto.builder()
                    .periodStart(wStart)
                    .periodEnd(wEnd)
                    .label(wStart + ".." + wEnd)
                    .completedCount(completed)
                    .totalCount(total)
                    .build());
            cursor = weekEnd.plusDays(1);
        }
        return points;
    }

    private WorkoutSessionItem toWorkoutSessionItem(WorkoutSessionRow row) {
        return WorkoutSessionItem.builder()
                .sessionId(row.sessionId())
                .title(row.title())
                .status(row.status().name())
                .source(row.source())
                .workoutDate(row.workoutDate())
                .durationSeconds(row.durationSeconds())
                .completedExercises(row.completedExercises())
                .totalExercises(row.totalExercises())
                .workoutPlanName(row.workoutPlanName())
                .startedAt(row.startedAt())
                .completedAt(row.completedAt())
                .build();
    }

    private List<BucketCountDto> aggregateTopGoals(List<String> rawGoals, long totalDenominator, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String raw : rawGoals) {
            if (isBlank(raw)) continue;
            for (String token : raw.split("[,;|]+")) {
                String normalized = token.trim();
                if (!normalized.isEmpty()) {
                    counts.merge(normalized, 1L, Long::sum);
                }
            }
        }
        long denom = Math.max(1L, totalDenominator);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> BucketCountDto.builder()
                        .label(e.getKey())
                        .count(e.getValue())
                        .percentage(pct(e.getValue(), denom))
                        .build())
                .toList();
    }

    private List<BucketCountDto> buildBreakdown(
            List<AdminGlobalUserSummaryDto> items,
            Function<AdminGlobalUserSummaryDto, String> classifier
    ) {
        long total = Math.max(1L, items.size());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AdminGlobalUserSummaryDto item : items) {
            String key = firstNonBlank(classifier.apply(item), "UNKNOWN");
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> BucketCountDto.builder()
                        .label(e.getKey())
                        .count(e.getValue())
                        .percentage(pct(e.getValue(), total))
                        .build())
                .toList();
    }

    private List<String> buildSummaryMissingNotes(List<AdminGlobalUserSummaryDto> users) {
        List<String> notes = new ArrayList<>();
        if (users.isEmpty()) {
            return notes;
        }
        long missingGoals = users.stream().filter(u -> isBlank(u.getPrimaryGoals())).count();
        long insufficientTrend = users.stream().filter(u -> "INSUFFICIENT_DATA".equals(u.getConsistencyTrend())).count();
        if (missingGoals > 0) {
            notes.add(missingGoals + " user(s) in this view do not have primary_goals populated.");
        }
        if (insufficientTrend > 0) {
            notes.add(insufficientTrend + " user(s) have insufficient workout history for 2-period consistency comparison.");
        }
        notes.add("Emails are masked by default unless explicitly authorized and requested with includeEmail=true.");
        return notes;
    }

    private String describeFilter(AdminUserSegmentFilter f) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(f.getSearchQuery())) parts.add("query=" + f.getSearchQuery());
        if (!isBlank(f.getExperienceLevel())) parts.add("experience=" + f.getExperienceLevel());
        if (!isBlank(f.getFitnessGoal())) parts.add("goal=" + f.getFitnessGoal());
        if (!isBlank(f.getActivityStatus())) parts.add("activity=" + f.getActivityStatus());
        if (!isBlank(f.getPrProgressionStatus())) parts.add("prStatus=" + f.getPrProgressionStatus());
        if (!isBlank(f.getCoachRelationshipStatus())) parts.add("coachStatus=" + f.getCoachRelationshipStatus());
        if (!isBlank(f.getSkillKeyword())) parts.add("skill=" + f.getSkillKeyword());
        if (f.getMinCompletionRate() != null) parts.add("minCompletion=" + f.getMinCompletionRate() + "%");
        if (f.getMaxCompletionRate() != null) parts.add("maxCompletion=" + f.getMaxCompletionRate() + "%");
        if (parts.isEmpty()) parts.add("ALL_USERS");
        return String.join(", ", parts);
    }

    private String resolveAccountCategory(UserBaseRow row) {
        if (row.role() == Role.ROLE_ADMIN) return "ADMIN";
        if (row.coachProfileId() != null) return "COACH";
        return "CLIENT";
    }

    private String resolveExperienceLevel(UserBaseRow row) {
        return firstNonBlank(row.profileExperienceLevel(), row.experienceLevel(), "INTERMEDIATE").toUpperCase();
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        String local = email.substring(0, at);
        String prefix = local.substring(0, Math.min(2, local.length()));
        return prefix + "***" + email.substring(at);
    }

    private Map<String, Long> labelRowsToMap(List<LabelCountRow> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (LabelCountRow r : rows) {
            map.put(r.label(), r.count());
        }
        return map;
    }

    private List<BucketCountDto> toBucketDtos(List<LabelCountRow> rows, long denom) {
        long safeDenom = Math.max(1L, denom);
        return rows.stream()
                .map(r -> BucketCountDto.builder()
                        .label(r.label())
                        .count(r.count())
                        .percentage(pct(r.count(), safeDenom))
                        .build())
                .toList();
    }

    private ZonedDateTime toStartZdt(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault());
    }

    private ZonedDateTime toEndZdt(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1);
    }

    private ZonedDateTime firstNonNullZdt(ZonedDateTime... values) {
        for (ZonedDateTime v : values) {
            if (v != null) return v;
        }
        return null;
    }

    private ZonedDateTime maxZdt(ZonedDateTime... values) {
        ZonedDateTime max = null;
        for (ZonedDateTime v : values) {
            if (v != null && (max == null || v.isAfter(max))) {
                max = v;
            }
        }
        return max;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private double pct(long count, long denom) {
        return denom > 0 ? round2((count * 100.0) / denom) : 0.0;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private long toLongVal(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        return Long.parseLong(obj.toString());
    }
}
