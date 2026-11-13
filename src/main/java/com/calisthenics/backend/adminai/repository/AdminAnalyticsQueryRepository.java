package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.dto.data.AdminUserSegmentFilter;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.user.Gender;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.workout.WorkoutSessionExerciseStatus;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Read-only batch and aggregate query repository for the Admin Data Access Layer.
 * Uses flat scalar projections and GROUP BY batch queries to eliminate N+1 queries.
 */
@Repository
@Transactional(readOnly = true)
public class AdminAnalyticsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ---------------- Internal Scalar Row Records ----------------

    public record UserBaseRow(
            Long userId,
            String fullName,
            String email,
            Role role,
            String experienceLevel,
            Gender gender,
            ZonedDateTime registeredAt,
            String clientDisplayName,
            String bio,
            String profileExperienceLevel,
            String trainingExperience,
            String primaryGoals,
            String trainingInterests,
            String preferredFrequency,
            String coachingLanguage,
            BigDecimal heightCm,
            Long coachProfileId,
            String coachDisplayName,
            String coachSpecialties,
            Integer coachYearsExperience,
            String coachLanguages,
            Boolean coachPublic,
            Boolean coachAcceptingClients,
            Boolean coachVerified
    ) {}

    public record UserWorkoutAggRow(
            Long userId,
            long totalWorkouts,
            long completedWorkouts,
            long abandonedOrIncompleteWorkouts,
            long cancelledWorkouts,
            long totalDurationSeconds,
            ZonedDateTime lastExplicitWorkoutAt,
            LocalDate lastWorkoutDate,
            ZonedDateTime lastCreatedWorkoutAt,
            ZonedDateTime lastCompletedExplicitAt,
            LocalDate lastCompletedWorkoutDate
    ) {
        public ZonedDateTime resolveEffectiveLastWorkoutAt() {
            if (lastExplicitWorkoutAt != null) {
                return lastExplicitWorkoutAt;
            }
            if (lastWorkoutDate != null) {
                return lastWorkoutDate.atStartOfDay(java.time.ZoneId.systemDefault());
            }
            return lastCreatedWorkoutAt;
        }

        public ZonedDateTime resolveEffectiveLastCompletedWorkoutAt() {
            if (lastCompletedExplicitAt != null) {
                return lastCompletedExplicitAt;
            }
            if (lastCompletedWorkoutDate != null) {
                return lastCompletedWorkoutDate.atStartOfDay(java.time.ZoneId.systemDefault());
            }
            return completedWorkouts > 0 ? lastCreatedWorkoutAt : null;
        }
    }

    public record UserPrAggRow(
            Long userId,
            long totalPrs,
            BigDecimal maxImprovementPercent,
            ZonedDateTime lastPrAt
    ) {}

    public record UserActivityAggRow(
            Long userId,
            long totalCount,
            long rangeCount,
            ZonedDateTime lastAt
    ) {}

    public record UserCoachStatusAggRow(
            Long userId,
            CoachClientRelationshipStatus status,
            long count
    ) {}

    public record WorkoutSessionRow(
            Long sessionId,
            String title,
            WorkoutSessionStatus status,
            String source,
            LocalDate workoutDate,
            Long durationSeconds,
            String workoutPlanName,
            ZonedDateTime startedAt,
            ZonedDateTime completedAt,
            ZonedDateTime createdAt,
            long totalExercises,
            long completedExercises
    ) {}

    public record AssignedPlanRow(
            Long assignmentId,
            Long workoutPlanId,
            String planName,
            String splitType,
            String coachName,
            WorkoutPlanAssignmentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String notes
    ) {}

    public record PersonalPlanRow(
            Long workoutPlanId,
            String name,
            String splitType,
            boolean active,
            boolean aiGenerated,
            ZonedDateTime createdAt
    ) {}

    public record PersonalRecordRow(
            Long recordId,
            String exerciseName,
            String recordType,
            BigDecimal recordValue,
            String unit,
            BigDecimal previousRecordValue,
            BigDecimal improvementPercentage,
            ZonedDateTime achievedAt,
            String notes
    ) {}

    public record SharedPrRow(
            Long sharedId,
            Long personalRecordId,
            String exerciseName,
            Long coachUserId,
            String coachName,
            String status,
            Integer rating,
            String coachFeedback,
            ZonedDateTime sharedAt,
            ZonedDateTime feedbackAt
    ) {}

    public record BodyMeasurementRow(
            BigDecimal weightKg,
            BigDecimal bodyFatPercentage,
            ZonedDateTime measuredAt
    ) {}

    public record CoachRelationshipRow(
            Long relationshipId,
            Long coachUserId,
            String coachName,
            Long clientUserId,
            String clientName,
            Long requestedById,
            CoachClientRelationshipStatus status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {}

    public record ExercisePerformanceRow(
            Long exerciseId,
            String exerciseName,
            String category,
            String difficulty,
            long totalPerformances,
            long completedPerformances,
            long uniqueUsers
    ) {}

    public record SkillImproverRow(
            Long userId,
            String fullName,
            String experienceLevel,
            String exerciseName,
            String recordType,
            BigDecimal recordValue,
            BigDecimal previousRecordValue,
            String unit,
            BigDecimal improvementPercentage,
            ZonedDateTime achievedAt
    ) {}

    public record CoachAggregateRow(
            Long coachUserId,
            String coachName,
            boolean verified,
            boolean acceptingClients,
            long activeClients,
            long pendingClients
    ) {}

    public record LabelCountRow(
            String label,
            long count
    ) {}

    // ---------------- Global Scalar & Aggregate Queries (Zero N+1) ----------------

    public long countTotalUsers() {
        return em.createQuery("SELECT COUNT(u.id) FROM User u", Long.class).getSingleResult();
    }

    public long countUsersByRole(Role role) {
        return em.createQuery("SELECT COUNT(u.id) FROM User u WHERE u.role = :role", Long.class)
                .setParameter("role", role)
                .getSingleResult();
    }

    public long countTotalCoaches() {
        return em.createQuery("SELECT COUNT(cp.id) FROM CoachProfile cp", Long.class).getSingleResult();
    }

    public long countPublicCoaches() {
        return em.createQuery("SELECT COUNT(cp.id) FROM CoachProfile cp WHERE cp.isPublic = true", Long.class)
                .getSingleResult();
    }

    public long countAcceptingCoaches() {
        return em.createQuery("SELECT COUNT(cp.id) FROM CoachProfile cp WHERE cp.isAcceptingClients = true", Long.class)
                .getSingleResult();
    }

    public long countVerifiedCoaches() {
        return em.createQuery("SELECT COUNT(cp.id) FROM CoachProfile cp WHERE cp.isVerified = true", Long.class)
                .getSingleResult();
    }

    public long countNewUsersBetween(ZonedDateTime start, ZonedDateTime end) {
        return em.createQuery(
                        "SELECT COUNT(u.id) FROM User u WHERE u.createdAt >= :start AND u.createdAt <= :end",
                        Long.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getSingleResult();
    }

    public long countDistinctWorkoutActiveUsers(LocalDate startDate, ZonedDateTime startZdt, ZonedDateTime endZdt) {
        return em.createQuery(
                        "SELECT COUNT(DISTINCT ws.user.id) FROM WorkoutSession ws " +
                                "WHERE ((ws.completedAt IS NOT NULL AND ws.completedAt BETWEEN :startZdt AND :endZdt) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt BETWEEN :startZdt AND :endZdt) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate >= :startDate AND ws.workoutDate <= :endDate) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt BETWEEN :startZdt AND :endZdt))",
                        Long.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();
    }

    public long countWorkoutsInRange(ZonedDateTime startZdt, ZonedDateTime endZdt, WorkoutSessionStatus status) {
        StringBuilder base = new StringBuilder(
                "SELECT COUNT(ws.id) FROM WorkoutSession ws " +
                        "WHERE ((ws.completedAt IS NOT NULL AND ws.completedAt BETWEEN :startZdt AND :endZdt) " +
                        "   OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt BETWEEN :startZdt AND :endZdt) " +
                        "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate BETWEEN :startDate AND :endDate) " +
                        "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt BETWEEN :startZdt AND :endZdt))"
        );
        if (status != null) {
            base.append(" AND ws.status = :status");
        }
        TypedQuery<Long> q = em.createQuery(base.toString(), Long.class)
                .setParameter("startDate", startZdt.toLocalDate())
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt);
        if (status != null) {
            q.setParameter("status", status);
        }
        return q.getSingleResult();
    }

    public long countTotalPersonalRecords() {
        return em.createQuery("SELECT COUNT(pr.id) FROM PersonalRecord pr", Long.class).getSingleResult();
    }

    public long countPersonalRecordsInRange(ZonedDateTime startZdt, ZonedDateTime endZdt) {
        return em.createQuery(
                        "SELECT COUNT(pr.id) FROM PersonalRecord pr WHERE pr.achievedAt BETWEEN :startZdt AND :endZdt",
                        Long.class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();
    }

    public long countUsersWithoutRecentWorkout(ZonedDateTime cutoffZdt, LocalDate cutoffDate) {
        return em.createQuery(
                        "SELECT COUNT(u.id) FROM User u " +
                                "WHERE NOT EXISTS (" +
                                "   SELECT 1 FROM WorkoutSession ws WHERE ws.user.id = u.id " +
                                "   AND ws.status = :completed " +
                                "   AND ((ws.completedAt IS NOT NULL AND ws.completedAt >= :cutoffZdt) " +
                                "        OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt >= :cutoffZdt) " +
                                "        OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate >= :cutoffDate) " +
                                "        OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt >= :cutoffZdt))" +
                                ")",
                        Long.class)
                .setParameter("completed", WorkoutSessionStatus.COMPLETED)
                .setParameter("cutoffDate", cutoffDate)
                .setParameter("cutoffZdt", cutoffZdt)
                .getSingleResult();
    }

    public long countUsersWithStalePrs(ZonedDateTime cutoffZdt) {
        return em.createQuery(
                        "SELECT COUNT(u.id) FROM User u " +
                                "WHERE EXISTS (SELECT 1 FROM PersonalRecord pr0 WHERE pr0.user.id = u.id) " +
                                "AND NOT EXISTS (SELECT 1 FROM PersonalRecord pr1 WHERE pr1.user.id = u.id AND pr1.achievedAt >= :cutoffZdt)",
                        Long.class)
                .setParameter("cutoffZdt", cutoffZdt)
                .getSingleResult();
    }

    public List<LabelCountRow> countExperienceLevelsDistribution() {
        List<Object[]> rows = em.createQuery(
                        "SELECT UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'UNKNOWN')), COUNT(u.id) " +
                                "FROM User u LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                                "GROUP BY UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'UNKNOWN'))",
                        Object[].class)
                .getResultList();
        return toLabelCounts(rows);
    }

    public List<LabelCountRow> countDistinctClientsByRelationshipStatus() {
        List<Object[]> rows = em.createQuery(
                        "SELECT CAST(r.status AS string), COUNT(DISTINCT r.client.id) " +
                                "FROM CoachClientRelationship r GROUP BY r.status",
                        Object[].class)
                .getResultList();
        return toLabelCounts(rows);
    }

    public List<LabelCountRow> countRelationshipsByStatus() {
        List<Object[]> rows = em.createQuery(
                        "SELECT CAST(r.status AS string), COUNT(r.id) " +
                                "FROM CoachClientRelationship r GROUP BY r.status",
                        Object[].class)
                .getResultList();
        return toLabelCounts(rows);
    }

    public List<LabelCountRow> countAssignedPlansByStatus() {
        List<Object[]> rows = em.createQuery(
                        "SELECT CAST(a.status AS string), COUNT(a.id) " +
                                "FROM WorkoutPlanAssignment a GROUP BY a.status",
                        Object[].class)
                .getResultList();
        return toLabelCounts(rows);
    }

    public long countTotalSharedPrs() {
        return em.createQuery("SELECT COUNT(s.id) FROM SharedPersonalRecord s", Long.class).getSingleResult();
    }

    public long countSharedPrsWithFeedback() {
        return em.createQuery(
                        "SELECT COUNT(s.id) FROM SharedPersonalRecord s " +
                                "WHERE s.feedbackAt IS NOT NULL OR (s.coachFeedback IS NOT NULL AND s.coachFeedback <> '')",
                        Long.class)
                .getSingleResult();
    }

    public List<String> fetchNonEmptyPrimaryGoals() {
        return em.createQuery(
                        "SELECT cp.primaryGoals FROM ClientProfile cp " +
                                "WHERE cp.primaryGoals IS NOT NULL AND TRIM(cp.primaryGoals) <> ''",
                        String.class)
                .getResultList();
    }

    // ---------------- User Base Queries ----------------

    public List<UserBaseRow> fetchFilteredUserBaseRows(
            AdminUserSegmentFilter filter,
            Integer offset,
            Integer limit
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT new com.calisthenics.backend.adminai.repository.AdminAnalyticsQueryRepository$UserBaseRow(" +
                        "u.id, u.fullName, u.email, u.role, u.experienceLevel, u.gender, u.createdAt, " +
                        "cp.displayName, cp.bio, cp.experienceLevel, cp.trainingExperience, cp.primaryGoals, " +
                        "cp.trainingInterests, cp.preferredFrequency, cp.coachingLanguage, cp.heightCm, " +
                        "coach.id, coach.displayName, coach.specialties, coach.yearsExperience, coach.languages, " +
                        "coach.isPublic, coach.isAcceptingClients, coach.isVerified" +
                        ") FROM User u " +
                        "LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                        "LEFT JOIN CoachProfile coach ON coach.user.id = u.id " +
                        "WHERE 1=1 "
        );

        Map<String, Object> params = new LinkedHashMap<>();
        appendUserBasePredicates(jpql, params, filter);

        String orderClause = switch (filter != null && filter.getSortBy() != null ? filter.getSortBy().toUpperCase() : "CREATED_AT_DESC") {
            case "NAME_ASC" -> " ORDER BY LOWER(u.fullName) ASC, u.id ASC";
            default -> " ORDER BY u.createdAt DESC, u.id DESC";
        };
        jpql.append(orderClause);

        TypedQuery<UserBaseRow> query = em.createQuery(jpql.toString(), UserBaseRow.class);
        params.forEach(query::setParameter);
        if (offset != null && offset > 0) {
            query.setFirstResult(offset);
        }
        if (limit != null && limit > 0) {
            query.setMaxResults(limit);
        }
        return query.getResultList();
    }

    public long countFilteredUserBaseRows(AdminUserSegmentFilter filter) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(u.id) FROM User u " +
                        "LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                        "LEFT JOIN CoachProfile coach ON coach.user.id = u.id " +
                        "WHERE 1=1 "
        );
        Map<String, Object> params = new LinkedHashMap<>();
        appendUserBasePredicates(jpql, params, filter);

        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        params.forEach(query::setParameter);
        return query.getSingleResult();
    }

    public Optional<UserBaseRow> fetchUserBaseRowById(Long userId) {
        List<UserBaseRow> rows = em.createQuery(
                        "SELECT new com.calisthenics.backend.adminai.repository.AdminAnalyticsQueryRepository$UserBaseRow(" +
                                "u.id, u.fullName, u.email, u.role, u.experienceLevel, u.gender, u.createdAt, " +
                                "cp.displayName, cp.bio, cp.experienceLevel, cp.trainingExperience, cp.primaryGoals, " +
                                "cp.trainingInterests, cp.preferredFrequency, cp.coachingLanguage, cp.heightCm, " +
                                "coach.id, coach.displayName, coach.specialties, coach.yearsExperience, coach.languages, " +
                                "coach.isPublic, coach.isAcceptingClients, coach.isVerified" +
                                ") FROM User u " +
                                "LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                                "LEFT JOIN CoachProfile coach ON coach.user.id = u.id " +
                                "WHERE u.id = :userId",
                        UserBaseRow.class)
                .setParameter("userId", userId)
                .getResultList();
        return rows.stream().findFirst();
    }

    private void appendUserBasePredicates(
            StringBuilder jpql,
            Map<String, Object> params,
            AdminUserSegmentFilter filter
    ) {
        if (filter == null) {
            return;
        }
        if (filter.getSearchQuery() != null && !filter.getSearchQuery().isBlank()) {
            String q = "%" + filter.getSearchQuery().trim().toLowerCase() + "%";
            jpql.append(" AND (LOWER(u.fullName) LIKE :searchQ OR LOWER(COALESCE(cp.displayName, '')) LIKE :searchQ ")
                    .append("OR LOWER(COALESCE(cp.primaryGoals, '')) LIKE :searchQ OR LOWER(u.email) LIKE :searchQ) ");
            params.put("searchQ", q);
        }
        if (filter.getExperienceLevel() != null && !filter.getExperienceLevel().isBlank()) {
            jpql.append(" AND UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, '')) = :expLevel ");
            params.put("expLevel", filter.getExperienceLevel().trim().toUpperCase());
        }
        if (filter.getFitnessGoal() != null && !filter.getFitnessGoal().isBlank()) {
            jpql.append(" AND (LOWER(COALESCE(cp.primaryGoals, '')) LIKE :goalQ OR LOWER(COALESCE(cp.trainingInterests, '')) LIKE :goalQ) ");
            params.put("goalQ", "%" + filter.getFitnessGoal().trim().toLowerCase() + "%");
        }
        if (filter.getPreferredFrequency() != null && !filter.getPreferredFrequency().isBlank()) {
            jpql.append(" AND LOWER(COALESCE(cp.preferredFrequency, '')) LIKE :freqQ ");
            params.put("freqQ", "%" + filter.getPreferredFrequency().trim().toLowerCase() + "%");
        }
        if (filter.getRoleFilter() != null && !filter.getRoleFilter().isBlank() && !"ALL".equalsIgnoreCase(filter.getRoleFilter())) {
            String roleFilter = filter.getRoleFilter().trim().toUpperCase();
            if ("ADMIN".equals(roleFilter)) {
                jpql.append(" AND u.role = :roleAdmin ");
                params.put("roleAdmin", Role.ROLE_ADMIN);
            } else if ("COACH".equals(roleFilter)) {
                jpql.append(" AND coach.id IS NOT NULL ");
            } else if ("CLIENT".equals(roleFilter)) {
                jpql.append(" AND u.role = :roleUser AND coach.id IS NULL ");
                params.put("roleUser", Role.ROLE_USER);
            }
        }
        if (filter.getRegisteredFrom() != null) {
            jpql.append(" AND u.createdAt >= :regFrom ");
            params.put("regFrom", filter.getRegisteredFrom().atStartOfDay(java.time.ZoneId.systemDefault()));
        }
        if (filter.getRegisteredTo() != null) {
            jpql.append(" AND u.createdAt <= :regTo ");
            params.put("regTo", filter.getRegisteredTo().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).minusNanos(1));
        }
        if (filter.getSkillKeyword() != null && !filter.getSkillKeyword().isBlank()) {
            String skillLike = "%" + filter.getSkillKeyword().trim().toLowerCase() + "%";
            jpql.append(" AND (")
                    .append("EXISTS (SELECT 1 FROM PersonalRecord prS LEFT JOIN prS.exercise exS WHERE prS.user.id = u.id ")
                    .append("        AND (LOWER(COALESCE(exS.name, '')) LIKE :skillLike OR LOWER(COALESCE(prS.customExerciseName, '')) LIKE :skillLike)) ")
                    .append("OR EXISTS (SELECT 1 FROM WorkoutSessionExercise wseS WHERE wseS.workoutSession.user.id = u.id ")
                    .append("           AND LOWER(wseS.exerciseNameSnapshot) LIKE :skillLike) ")
                    .append("OR LOWER(COALESCE(cp.trainingInterests, '')) LIKE :skillLike ")
                    .append("OR LOWER(COALESCE(cp.primaryGoals, '')) LIKE :skillLike ")
                    .append(") ");
            params.put("skillLike", skillLike);
        }
    }

    // ---------------- Batch Aggregate Maps for User Collections (O(1) Queries) ----------------

    public Map<Long, UserWorkoutAggRow> fetchWorkoutAggregatesForUsers(
            Collection<Long> userIds,
            ZonedDateTime startZdt,
            ZonedDateTime endZdt
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT ws.user.id, " +
                                "COUNT(ws.id), " +
                                "SUM(CASE WHEN ws.status = :completed THEN 1 ELSE 0 END), " +
                                "SUM(CASE WHEN ws.status IN (:inProgress, :cancelled, :scheduled) THEN 1 ELSE 0 END), " +
                                "SUM(CASE WHEN ws.status = :cancelled THEN 1 ELSE 0 END), " +
                                "COALESCE(SUM(COALESCE(ws.durationSeconds, 0L)), 0L), " +
                                "MAX(COALESCE(ws.completedAt, ws.startedAt)), " +
                                "MAX(ws.workoutDate), " +
                                "MAX(ws.createdAt), " +
                                "MAX(CASE WHEN ws.status = :completed THEN COALESCE(ws.completedAt, ws.startedAt) ELSE NULL END), " +
                                "MAX(CASE WHEN ws.status = :completed THEN ws.workoutDate ELSE NULL END) " +
                                "FROM WorkoutSession ws " +
                                "WHERE ws.user.id IN (:userIds) " +
                                "AND (:applyRange = false OR ((ws.completedAt IS NOT NULL AND ws.completedAt BETWEEN :startZdt AND :endZdt) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt BETWEEN :startZdt AND :endZdt) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate BETWEEN :startDate AND :endDate) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt BETWEEN :startZdt AND :endZdt))) " +
                                "GROUP BY ws.user.id",
                        Object[].class)
                .setParameter("userIds", userIds)
                .setParameter("completed", WorkoutSessionStatus.COMPLETED)
                .setParameter("inProgress", WorkoutSessionStatus.IN_PROGRESS)
                .setParameter("cancelled", WorkoutSessionStatus.CANCELLED)
                .setParameter("scheduled", WorkoutSessionStatus.SCHEDULED)
                .setParameter("applyRange", startZdt != null && endZdt != null)
                .setParameter("startDate", startZdt != null ? startZdt.toLocalDate() : LocalDate.now().minusYears(50))
                .setParameter("endDate", endZdt != null ? endZdt.toLocalDate() : LocalDate.now().plusYears(1))
                .setParameter("startZdt", startZdt != null ? startZdt : ZonedDateTime.now().minusYears(50))
                .setParameter("endZdt", endZdt != null ? endZdt : ZonedDateTime.now().plusYears(1))
                .getResultList();

        Map<Long, UserWorkoutAggRow> map = new HashMap<>();
        for (Object[] r : rows) {
            Long uid = (Long) r[0];
            map.put(uid, new UserWorkoutAggRow(
                    uid,
                    toLong(r[1]),
                    toLong(r[2]),
                    toLong(r[3]),
                    toLong(r[4]),
                    toLong(r[5]),
                    (ZonedDateTime) r[6],
                    (LocalDate) r[7],
                    (ZonedDateTime) r[8],
                    (ZonedDateTime) r[9],
                    (LocalDate) r[10]
            ));
        }
        return map;
    }

    public Map<Long, long[]> fetchRecentVsPriorWorkoutCounts(
            Collection<Long> userIds,
            ZonedDateTime midpointZdt,
            ZonedDateTime priorStartZdt
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        LocalDate midDate = midpointZdt.toLocalDate();
        LocalDate priorDate = priorStartZdt.toLocalDate();
        List<Object[]> rows = em.createQuery(
                        "SELECT ws.user.id, " +
                                "SUM(CASE WHEN ((ws.completedAt IS NOT NULL AND ws.completedAt >= :midpoint) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt >= :midpoint) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate >= :midDate) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt >= :midpoint)) THEN 1 ELSE 0 END), " +
                                "SUM(CASE WHEN ((ws.completedAt IS NOT NULL AND ws.completedAt >= :priorStart AND ws.completedAt < :midpoint) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt >= :priorStart AND ws.startedAt < :midpoint) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate >= :priorDate AND ws.workoutDate < :midDate) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt >= :priorStart AND ws.createdAt < :midpoint)) THEN 1 ELSE 0 END) " +
                                "FROM WorkoutSession ws " +
                                "WHERE ws.user.id IN (:userIds) AND ws.status = :completed " +
                                "AND ((ws.completedAt IS NOT NULL AND ws.completedAt >= :priorStart) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NOT NULL AND ws.startedAt >= :priorStart) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NOT NULL AND ws.workoutDate >= :priorDate) " +
                                "   OR (ws.completedAt IS NULL AND ws.startedAt IS NULL AND ws.workoutDate IS NULL AND ws.createdAt >= :priorStart)) " +
                                "GROUP BY ws.user.id",
                        Object[].class)
                .setParameter("userIds", userIds)
                .setParameter("completed", WorkoutSessionStatus.COMPLETED)
                .setParameter("midpoint", midpointZdt)
                .setParameter("priorStart", priorStartZdt)
                .setParameter("midDate", midDate)
                .setParameter("priorDate", priorDate)
                .getResultList();

        Map<Long, long[]> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((Long) r[0], new long[]{toLong(r[1]), toLong(r[2])});
        }
        return map;
    }

    public Map<Long, UserPrAggRow> fetchPrAggregatesForUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT pr.user.id, COUNT(pr.id), MAX(pr.improvementPercentage), MAX(pr.achievedAt) " +
                                "FROM PersonalRecord pr WHERE pr.user.id IN (:userIds) GROUP BY pr.user.id",
                        Object[].class)
                .setParameter("userIds", userIds)
                .getResultList();
        Map<Long, UserPrAggRow> map = new HashMap<>();
        for (Object[] r : rows) {
            Long uid = (Long) r[0];
            map.put(uid, new UserPrAggRow(uid, toLong(r[1]), (BigDecimal) r[2], (ZonedDateTime) r[3]));
        }
        return map;
    }

    public Map<Long, Map<CoachClientRelationshipStatus, Long>> fetchClientRelationshipCounts(Collection<Long> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT r.client.id, r.status, COUNT(r.id) FROM CoachClientRelationship r " +
                                "WHERE r.client.id IN (:ids) GROUP BY r.client.id, r.status",
                        Object[].class)
                .setParameter("ids", clientIds)
                .getResultList();
        Map<Long, Map<CoachClientRelationshipStatus, Long>> map = new HashMap<>();
        for (Object[] r : rows) {
            map.computeIfAbsent((Long) r[0], k -> new EnumMap<>(CoachClientRelationshipStatus.class))
                    .put((CoachClientRelationshipStatus) r[1], toLong(r[2]));
        }
        return map;
    }

    public Map<Long, Long> fetchCoachActiveClientCounts(Collection<Long> coachIds) {
        if (coachIds == null || coachIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT r.coach.id, COUNT(r.id) FROM CoachClientRelationship r " +
                                "WHERE r.coach.id IN (:ids) AND r.status = :active GROUP BY r.coach.id",
                        Object[].class)
                .setParameter("ids", coachIds)
                .setParameter("active", CoachClientRelationshipStatus.ACTIVE)
                .getResultList();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((Long) r[0], toLong(r[1]));
        }
        return map;
    }

    public Map<Long, UserActivityAggRow> fetchAiConversationAggregates(
            Collection<Long> userIds,
            ZonedDateTime startZdt,
            ZonedDateTime endZdt
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT ac.user.id, COUNT(ac.id), " +
                                "SUM(CASE WHEN ac.updatedAt BETWEEN :startZdt AND :endZdt THEN 1 ELSE 0 END), " +
                                "MAX(ac.updatedAt) " +
                                "FROM AiConversation ac WHERE ac.user.id IN (:ids) GROUP BY ac.user.id",
                        Object[].class)
                .setParameter("ids", userIds)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList();
        return toActivityMap(rows);
    }

    public Map<Long, UserActivityAggRow> fetchChatMessageAggregates(
            Collection<Long> userIds,
            ZonedDateTime startZdt,
            ZonedDateTime endZdt
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT cm.sender.id, COUNT(cm.id), " +
                                "SUM(CASE WHEN cm.createdAt BETWEEN :startZdt AND :endZdt THEN 1 ELSE 0 END), " +
                                "MAX(cm.createdAt) " +
                                "FROM ChatMessage cm WHERE cm.sender.id IN (:ids) GROUP BY cm.sender.id",
                        Object[].class)
                .setParameter("ids", userIds)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList();
        return toActivityMap(rows);
    }

    public Map<Long, ZonedDateTime> fetchPresenceLastSeenMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT p.userId, p.lastSeen FROM UserPresenceRecord p WHERE p.userId IN (:ids)",
                        Object[].class)
                .setParameter("ids", userIds)
                .getResultList();
        Map<Long, ZonedDateTime> map = new HashMap<>();
        for (Object[] r : rows) {
            if (r[1] != null) {
                map.put((Long) r[0], (ZonedDateTime) r[1]);
            }
        }
        return map;
    }

    // ---------------- Single-User Drill-Down Queries ----------------

    public Optional<BodyMeasurementRow> fetchLatestBodyMeasurement(Long userId) {
        List<BodyMeasurementRow> rows = em.createQuery(
                        "SELECT new com.calisthenics.backend.adminai.repository.AdminAnalyticsQueryRepository$BodyMeasurementRow(" +
                                "m.weightKg, m.bodyFatPercentage, m.measuredAt) " +
                                "FROM ClientBodyMeasurement m WHERE m.client.id = :userId ORDER BY m.measuredAt DESC",
                        BodyMeasurementRow.class)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }

    public List<BodyMeasurementRow> fetchBodyMeasurementsForUser(Long userId, int limit) {
        return em.createQuery(
                        "SELECT new com.calisthenics.backend.adminai.repository.AdminAnalyticsQueryRepository$BodyMeasurementRow(" +
                                "m.weightKg, m.bodyFatPercentage, m.measuredAt) " +
                                "FROM ClientBodyMeasurement m WHERE m.client.id = :userId ORDER BY m.measuredAt DESC",
                        BodyMeasurementRow.class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<String> fetchInferredEquipmentForUser(Long userId) {
        Set<String> equipment = new LinkedHashSet<>();
        List<String> fromSessions = em.createQuery(
                        "SELECT DISTINCT ex.equipment FROM WorkoutSessionExercise wse " +
                                "JOIN wse.exercise ex " +
                                "WHERE wse.workoutSession.user.id = :userId " +
                                "AND ex.equipment IS NOT NULL AND TRIM(ex.equipment) <> ''",
                        String.class)
                .setParameter("userId", userId)
                .getResultList();
        equipment.addAll(fromSessions);

        List<String> fromPlans = em.createQuery(
                        "SELECT DISTINCT ex.equipment FROM WorkoutPlanExercise wpe " +
                                "JOIN wpe.exercise ex " +
                                "WHERE wpe.workoutPlanDay.workoutPlan.user.id = :userId " +
                                "AND ex.equipment IS NOT NULL AND TRIM(ex.equipment) <> ''",
                        String.class)
                .setParameter("userId", userId)
                .getResultList();
        equipment.addAll(fromPlans);
        return new ArrayList<>(equipment);
    }

    public List<WorkoutSessionRow> fetchWorkoutSessionsForUser(
            Long userId,
            ZonedDateTime startZdt,
            ZonedDateTime endZdt,
            int limit
    ) {
        List<Object[]> rows = em.createQuery(
                        "SELECT ws.id, ws.title, ws.status, CAST(ws.source AS string), ws.workoutDate, " +
                                "ws.durationSeconds, wp.name, ws.startedAt, ws.completedAt, ws.createdAt " +
                                "FROM WorkoutSession ws LEFT JOIN ws.workoutPlan wp " +
                                "WHERE ws.user.id = :userId " +
                                "AND " + workoutSessionInRangeJpql("ws") + " " +
                                "ORDER BY COALESCE(ws.completedAt, ws.startedAt, ws.createdAt) DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("startDate", startZdt.toLocalDate())
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .setMaxResults(limit)
                .getResultList();

        List<Long> sessionIds = rows.stream().map(r -> (Long) r[0]).toList();
        Map<Long, long[]> exerciseCounts = fetchExerciseCountsBySessionIds(sessionIds);

        List<WorkoutSessionRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long sid = (Long) r[0];
            long[] counts = exerciseCounts.getOrDefault(sid, new long[]{0L, 0L});
            result.add(new WorkoutSessionRow(
                    sid,
                    (String) r[1],
                    (WorkoutSessionStatus) r[2],
                    (String) r[3],
                    (LocalDate) r[4],
                    (Long) r[5],
                    (String) r[6],
                    (ZonedDateTime) r[7],
                    (ZonedDateTime) r[8],
                    (ZonedDateTime) r[9],
                    counts[0],
                    counts[1]
            ));
        }
        return result;
    }

    private Map<Long, long[]> fetchExerciseCountsBySessionIds(Collection<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT wse.workoutSession.id, COUNT(wse.id), " +
                                "SUM(CASE WHEN wse.status = :completed THEN 1 ELSE 0 END) " +
                                "FROM WorkoutSessionExercise wse " +
                                "WHERE wse.workoutSession.id IN (:ids) " +
                                "GROUP BY wse.workoutSession.id",
                        Object[].class)
                .setParameter("ids", sessionIds)
                .setParameter("completed", WorkoutSessionExerciseStatus.COMPLETED)
                .getResultList();
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((Long) r[0], new long[]{toLong(r[1]), toLong(r[2])});
        }
        return map;
    }

    public long[] countSessionExercisesForUser(Long userId, ZonedDateTime startZdt, ZonedDateTime endZdt) {
        Object[] row = em.createQuery(
                        "SELECT COUNT(wse.id), " +
                                "SUM(CASE WHEN wse.status = :completed THEN 1 ELSE 0 END) " +
                                "FROM WorkoutSessionExercise wse " +
                                "WHERE wse.workoutSession.user.id = :userId " +
                                "AND " + workoutSessionInRangeJpql("wse.workoutSession"),
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("completed", WorkoutSessionExerciseStatus.COMPLETED)
                .setParameter("startDate", startZdt.toLocalDate())
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();
        return new long[]{toLong(row[0]), toLong(row[1])};
    }

    public List<AssignedPlanRow> fetchAssignedPlansForClient(Long userId) {
        List<Object[]> rows = em.createQuery(
                        "SELECT a.id, a.workoutPlan.id, a.workoutPlan.name, CAST(a.workoutPlan.splitType AS string), " +
                                "a.coach.fullName, a.status, a.startDate, a.endDate, a.notes " +
                                "FROM WorkoutPlanAssignment a " +
                                "WHERE a.client.id = :userId ORDER BY a.createdAt DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        List<AssignedPlanRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new AssignedPlanRow(
                    (Long) r[0],
                    (Long) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    (WorkoutPlanAssignmentStatus) r[5],
                    (LocalDate) r[6],
                    (LocalDate) r[7],
                    (String) r[8]
            ));
        }
        return result;
    }

    public List<PersonalPlanRow> fetchPersonalPlansForUser(Long userId) {
        List<Object[]> rows = em.createQuery(
                        "SELECT wp.id, wp.name, CAST(wp.splitType AS string), wp.active, wp.aiGenerated, wp.createdAt " +
                                "FROM WorkoutPlan wp WHERE wp.user.id = :userId ORDER BY wp.createdAt DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        List<PersonalPlanRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new PersonalPlanRow(
                    (Long) r[0],
                    (String) r[1],
                    (String) r[2],
                    Boolean.TRUE.equals(r[3]),
                    Boolean.TRUE.equals(r[4]),
                    (ZonedDateTime) r[5]
            ));
        }
        return result;
    }

    public List<PersonalRecordRow> fetchPersonalRecordsForUser(Long userId, int limit) {
        List<Object[]> rows = em.createQuery(
                        "SELECT pr.id, COALESCE(ex.name, pr.customExerciseName, 'Unknown Exercise'), " +
                                "CAST(pr.recordType AS string), pr.recordValue, pr.unit, pr.previousRecordValue, " +
                                "pr.improvementPercentage, pr.achievedAt, pr.notes " +
                                "FROM PersonalRecord pr LEFT JOIN pr.exercise ex " +
                                "WHERE pr.user.id = :userId ORDER BY pr.achievedAt DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList();
        List<PersonalRecordRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new PersonalRecordRow(
                    (Long) r[0],
                    (String) r[1],
                    (String) r[2],
                    (BigDecimal) r[3],
                    (String) r[4],
                    (BigDecimal) r[5],
                    (BigDecimal) r[6],
                    (ZonedDateTime) r[7],
                    (String) r[8]
            ));
        }
        return result;
    }

    public List<SharedPrRow> fetchSharedPrsForClient(Long userId) {
        List<Object[]> rows = em.createQuery(
                        "SELECT s.id, s.personalRecord.id, " +
                                "COALESCE(ex.name, s.personalRecord.customExerciseName, 'Unknown Exercise'), " +
                                "s.coach.id, s.coach.fullName, s.status, s.rating, s.coachFeedback, s.sharedAt, s.feedbackAt " +
                                "FROM SharedPersonalRecord s " +
                                "LEFT JOIN s.personalRecord.exercise ex " +
                                "WHERE s.client.id = :userId ORDER BY s.sharedAt DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        List<SharedPrRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new SharedPrRow(
                    (Long) r[0],
                    (Long) r[1],
                    (String) r[2],
                    (Long) r[3],
                    (String) r[4],
                    (String) r[5],
                    (Integer) r[6],
                    (String) r[7],
                    (ZonedDateTime) r[8],
                    (ZonedDateTime) r[9]
            ));
        }
        return result;
    }

    public List<CoachRelationshipRow> fetchCoachRelationshipsForUser(Long userId) {
        List<Object[]> rows = em.createQuery(
                        "SELECT r.id, r.coach.id, r.coach.fullName, r.client.id, r.client.fullName, " +
                                "r.requestedBy.id, r.status, r.createdAt, r.updatedAt " +
                                "FROM CoachClientRelationship r " +
                                "WHERE r.client.id = :userId OR r.coach.id = :userId ORDER BY r.updatedAt DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        List<CoachRelationshipRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new CoachRelationshipRow(
                    (Long) r[0],
                    (Long) r[1],
                    (String) r[2],
                    (Long) r[3],
                    (String) r[4],
                    (Long) r[5],
                    (CoachClientRelationshipStatus) r[6],
                    (ZonedDateTime) r[7],
                    (ZonedDateTime) r[8]
            ));
        }
        return result;
    }

    public List<Object[]> fetchUserExercisePerformance(Long userId, ZonedDateTime startZdt, ZonedDateTime endZdt) {
        return em.createQuery(
                        "SELECT wse.exerciseNameSnapshot, COUNT(wse.id), " +
                                "SUM(CASE WHEN wse.status = :completed THEN 1 ELSE 0 END) " +
                                "FROM WorkoutSessionExercise wse " +
                                "WHERE wse.workoutSession.user.id = :userId " +
                                "AND " + workoutSessionInRangeJpql("wse.workoutSession") + " " +
                                "GROUP BY wse.exerciseNameSnapshot ORDER BY COUNT(wse.id) DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("completed", WorkoutSessionExerciseStatus.COMPLETED)
                .setParameter("startDate", startZdt.toLocalDate())
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList();
    }

    // ---------------- User Privacy-Safe Activity & AI/Chat Metadata Queries ----------------

    public Object[] fetchUserChatStats(Long userId, ZonedDateTime startZdt, ZonedDateTime endZdt) {
        long convCount = em.createQuery(
                        "SELECT COUNT(c.id) FROM ChatConversation c WHERE c.coach.id = :userId OR c.client.id = :userId",
                        Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
        Object[] msgRow = em.createQuery(
                        "SELECT COUNT(m.id), " +
                                "SUM(CASE WHEN m.createdAt BETWEEN :startZdt AND :endZdt THEN 1 ELSE 0 END), " +
                                "MAX(m.createdAt) " +
                                "FROM ChatMessage m WHERE m.sender.id = :userId",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();
        return new Object[]{convCount, toLong(msgRow[0]), toLong(msgRow[1]), msgRow[2]};
    }

    public Object[] fetchUserAiAssistantStats(Long userId, ZonedDateTime startZdt, ZonedDateTime endZdt) {
        long convCount = em.createQuery(
                        "SELECT COUNT(c.id) FROM AiConversation c WHERE c.user.id = :userId",
                        Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
        Object[] msgRow = em.createQuery(
                        "SELECT COUNT(m.id), " +
                                "SUM(CASE WHEN m.createdAt BETWEEN :startZdt AND :endZdt THEN 1 ELSE 0 END), " +
                                "MAX(m.createdAt) " +
                                "FROM AiMessage m WHERE m.conversation.user.id = :userId",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList().get(0);
        List<Object[]> intents = em.createQuery(
                        "SELECT COALESCE(m.intent, 'GENERAL'), COUNT(m.id) FROM AiMessage m " +
                                "WHERE m.conversation.user.id = :userId AND m.intent IS NOT NULL AND m.intent <> '' " +
                                "GROUP BY m.intent ORDER BY COUNT(m.id) DESC",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        return new Object[]{convCount, toLong(msgRow[0]), toLong(msgRow[1]), msgRow[2], toLabelCounts(intents)};
    }

    public Object[] fetchUserPlannerAndAnalysisStats(
            Long userId,
            ZonedDateTime startZdt,
            ZonedDateTime endZdt,
            Instant startInstant,
            Instant endInstant
    ) {
        Object[] plannerRow = em.createQuery(
                        "SELECT COUNT(l.id), " +
                                "SUM(CASE WHEN l.createdAt BETWEEN :startZdt AND :endZdt THEN 1 ELSE 0 END), " +
                                "SUM(CASE WHEN UPPER(l.status) = 'SUCCESS' OR UPPER(l.status) = 'COMPLETED' THEN 1 ELSE 0 END) " +
                                "FROM AiWorkoutPlanLog l WHERE l.user.id = :userId",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();

        Object[] liveRow = em.createQuery(
                        "SELECT COUNT(s.id), MAX(s.startedAt) FROM AnalysisSession s " +
                                "WHERE s.user.id = :userId AND s.startedAt BETWEEN :startInst AND :endInst",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("startInst", startInstant)
                .setParameter("endInst", endInstant)
                .getSingleResult();

        Object[] videoRow = em.createQuery(
                        "SELECT COUNT(v.id), MAX(v.createdAt) FROM VideoAnalysis v " +
                                "WHERE v.user.id = :userId AND v.createdAt BETWEEN :startZdt AND :endZdt",
                        Object[].class)
                .setParameter("userId", userId)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();

        return new Object[]{
                toLong(plannerRow[0]), toLong(plannerRow[1]), toLong(plannerRow[2]),
                toLong(liveRow[0]), liveRow[1],
                toLong(videoRow[0]), videoRow[1]
        };
    }

    public Object[] fetchUserPresence(Long userId) {
        List<Object[]> rows = em.createQuery(
                        "SELECT p.status, p.lastSeen FROM UserPresenceRecord p WHERE p.userId = :userId",
                        Object[].class)
                .setParameter("userId", userId)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------------- Platform Engagement & Coaching Platform Queries ----------------

    public List<ExercisePerformanceRow> fetchTopPerformedExercisesInRange(
            ZonedDateTime startZdt,
            ZonedDateTime endZdt,
            int limit
    ) {
        List<Object[]> rows = em.createQuery(
                        "SELECT ex.id, COALESCE(ex.name, wse.exerciseNameSnapshot), " +
                                "CAST(ex.category AS string), CAST(ex.difficulty AS string), " +
                                "COUNT(wse.id), " +
                                "SUM(CASE WHEN wse.status = :completed THEN 1 ELSE 0 END), " +
                                "COUNT(DISTINCT wse.workoutSession.user.id) " +
                                "FROM WorkoutSessionExercise wse LEFT JOIN wse.exercise ex " +
                                "WHERE " + workoutSessionInRangeJpql("wse.workoutSession") + " " +
                                "GROUP BY ex.id, COALESCE(ex.name, wse.exerciseNameSnapshot), ex.category, ex.difficulty " +
                                "ORDER BY COUNT(wse.id) DESC",
                        Object[].class)
                .setParameter("completed", WorkoutSessionExerciseStatus.COMPLETED)
                .setParameter("startDate", startZdt.toLocalDate())
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .setMaxResults(limit)
                .getResultList();
        List<ExercisePerformanceRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new ExercisePerformanceRow(
                    (Long) r[0],
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    toLong(r[4]),
                    toLong(r[5]),
                    toLong(r[6])
            ));
        }
        return result;
    }

    public List<SkillImproverRow> fetchTopSkillImprovers(
            String skillKeyword,
            ZonedDateTime startZdt,
            ZonedDateTime endZdt,
            int limit
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT pr.user.id, pr.user.fullName, COALESCE(cp.experienceLevel, pr.user.experienceLevel, 'INTERMEDIATE'), " +
                        "COALESCE(ex.name, pr.customExerciseName, 'Unknown Exercise'), " +
                        "CAST(pr.recordType AS string), pr.recordValue, pr.previousRecordValue, pr.unit, " +
                        "pr.improvementPercentage, pr.achievedAt " +
                        "FROM PersonalRecord pr " +
                        "LEFT JOIN pr.exercise ex " +
                        "LEFT JOIN ClientProfile cp ON cp.user.id = pr.user.id " +
                        "WHERE pr.achievedAt BETWEEN :startZdt AND :endZdt " +
                        "AND pr.improvementPercentage IS NOT NULL AND pr.improvementPercentage > 0 "
        );
        if (skillKeyword != null && !skillKeyword.isBlank()) {
            jpql.append("AND (LOWER(COALESCE(ex.name, '')) LIKE :skill OR LOWER(COALESCE(pr.customExerciseName, '')) LIKE :skill) ");
        }
        jpql.append("ORDER BY pr.improvementPercentage DESC, pr.recordValue DESC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt);
        if (skillKeyword != null && !skillKeyword.isBlank()) {
            query.setParameter("skill", "%" + skillKeyword.trim().toLowerCase() + "%");
        }
        List<Object[]> rows = query.setMaxResults(Math.max(limit * 3, 25)).getResultList();
        List<SkillImproverRow> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(new SkillImproverRow(
                    (Long) r[0],
                    (String) r[1],
                    (String) r[2],
                    (String) r[3],
                    (String) r[4],
                    (BigDecimal) r[5],
                    (BigDecimal) r[6],
                    (String) r[7],
                    (BigDecimal) r[8],
                    (ZonedDateTime) r[9]
            ));
        }
        return result;
    }

    public List<CoachAggregateRow> fetchCoachLeaderboard(int limit) {
        List<Object[]> rows = em.createQuery(
                        "SELECT cp.user.id, COALESCE(cp.displayName, cp.user.fullName), cp.isVerified, cp.isAcceptingClients, " +
                                "SUM(CASE WHEN r.status = :active THEN 1 ELSE 0 END), " +
                                "SUM(CASE WHEN r.status = :pending THEN 1 ELSE 0 END) " +
                                "FROM CoachProfile cp " +
                                "LEFT JOIN CoachClientRelationship r ON r.coach.id = cp.user.id " +
                                "GROUP BY cp.user.id, COALESCE(cp.displayName, cp.user.fullName), cp.isVerified, cp.isAcceptingClients " +
                                "ORDER BY SUM(CASE WHEN r.status = :active THEN 1 ELSE 0 END) DESC, cp.user.id ASC",
                        Object[].class)
                .setParameter("active", CoachClientRelationshipStatus.ACTIVE)
                .setParameter("pending", CoachClientRelationshipStatus.PENDING)
                .setMaxResults(limit)
                .getResultList();
        List<CoachAggregateRow> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            result.add(new CoachAggregateRow(
                    (Long) r[0],
                    (String) r[1],
                    Boolean.TRUE.equals(r[2]),
                    Boolean.TRUE.equals(r[3]),
                    toLong(r[4]),
                    toLong(r[5])
            ));
        }
        return result;
    }

    public Map<Long, Long> fetchCoachActiveAssignmentCounts(Collection<Long> coachIds) {
        if (coachIds == null || coachIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT a.coach.id, COUNT(a.id) FROM WorkoutPlanAssignment a " +
                                "WHERE a.coach.id IN (:ids) AND a.status = :active GROUP BY a.coach.id",
                        Object[].class)
                .setParameter("ids", coachIds)
                .setParameter("active", WorkoutPlanAssignmentStatus.ACTIVE)
                .getResultList();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((Long) r[0], toLong(r[1]));
        }
        return map;
    }

    public Map<Long, Long> fetchCoachSharedPrFeedbackCounts(Collection<Long> coachIds) {
        if (coachIds == null || coachIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT s.coach.id, COUNT(s.id) FROM SharedPersonalRecord s " +
                                "WHERE s.coach.id IN (:ids) AND (s.feedbackAt IS NOT NULL OR (s.coachFeedback IS NOT NULL AND s.coachFeedback <> '')) " +
                                "GROUP BY s.coach.id",
                        Object[].class)
                .setParameter("ids", coachIds)
                .getResultList();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((Long) r[0], toLong(r[1]));
        }
        return map;
    }

    public Object[] fetchPlatformChatAndAiSummary(
            ZonedDateTime startZdt,
            ZonedDateTime endZdt,
            Instant startInst,
            Instant endInst
    ) {
        Object[] chatRow = em.createQuery(
                        "SELECT COUNT(m.id), COUNT(DISTINCT m.sender.id) FROM ChatMessage m " +
                                "WHERE m.createdAt BETWEEN :startZdt AND :endZdt",
                        Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();

        Object[] aiRow = em.createQuery(
                        "SELECT COUNT(m.id), COUNT(DISTINCT m.conversation.user.id) FROM AiMessage m " +
                                "WHERE m.createdAt BETWEEN :startZdt AND :endZdt",
                        Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();

        Object[] plannerRow = em.createQuery(
                        "SELECT COUNT(l.id), " +
                                "SUM(CASE WHEN UPPER(l.status) = 'SUCCESS' OR UPPER(l.status) = 'COMPLETED' THEN 1 ELSE 0 END) " +
                                "FROM AiWorkoutPlanLog l WHERE l.createdAt BETWEEN :startZdt AND :endZdt",
                        Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();

        long prInRange = countPersonalRecordsInRange(startZdt, endZdt);

        long liveInRange = em.createQuery(
                        "SELECT COUNT(s.id) FROM AnalysisSession s WHERE s.startedAt BETWEEN :startInst AND :endInst",
                        Long.class)
                .setParameter("startInst", startInst)
                .setParameter("endInst", endInst)
                .getSingleResult();

        long videoInRange = em.createQuery(
                        "SELECT COUNT(v.id) FROM VideoAnalysis v WHERE v.createdAt BETWEEN :startZdt AND :endZdt",
                        Long.class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getSingleResult();

        List<Object[]> intents = em.createQuery(
                        "SELECT COALESCE(m.intent, 'GENERAL'), COUNT(m.id) FROM AiMessage m " +
                                "WHERE m.createdAt BETWEEN :startZdt AND :endZdt AND m.intent IS NOT NULL AND m.intent <> '' " +
                                "GROUP BY m.intent ORDER BY COUNT(m.id) DESC",
                        Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .setMaxResults(8)
                .getResultList();

        return new Object[]{
                toLong(chatRow[0]), toLong(chatRow[1]),
                toLong(aiRow[0]), toLong(aiRow[1]),
                toLong(plannerRow[0]), toLong(plannerRow[1]),
                prInRange, liveInRange, videoInRange, toLabelCounts(intents)
        };
    }

    public long countPlatformActiveUsersInWindow(
            ZonedDateTime startZdt,
            ZonedDateTime endZdt,
            LocalDate startDate
    ) {
        // Distinct users active across workouts, AI messages, or chat messages in window using union-friendly single queries
        Set<Long> active = new HashSet<>();
        active.addAll(em.createQuery(
                        "SELECT DISTINCT ws.user.id FROM WorkoutSession ws " +
                                "WHERE ((ws.workoutDate IS NOT NULL AND ws.workoutDate >= :startDate) " +
                                "   OR COALESCE(ws.completedAt, ws.startedAt, ws.createdAt) BETWEEN :startZdt AND :endZdt)",
                        Long.class)
                .setParameter("startDate", startDate)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList());
        active.addAll(em.createQuery(
                        "SELECT DISTINCT ac.user.id FROM AiConversation ac WHERE ac.updatedAt BETWEEN :startZdt AND :endZdt",
                        Long.class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList());
        active.addAll(em.createQuery(
                        "SELECT DISTINCT cm.sender.id FROM ChatMessage cm WHERE cm.createdAt BETWEEN :startZdt AND :endZdt",
                        Long.class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList());
        return active.size();
    }

    /**
     * Experience-level engagement comparison computed with constant batch aggregate queries.
     */
    public List<Object[]> fetchExperienceComparisonAggregates(ZonedDateTime startZdt, ZonedDateTime endZdt) {
        List<Object[]> userCounts = em.createQuery(
                        "SELECT UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE')), COUNT(u.id) " +
                                "FROM User u LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                                "GROUP BY UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE'))",
                        Object[].class)
                .getResultList();

        List<Object[]> workoutStats = em.createQuery(
                        "SELECT UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE')), " +
                                "COUNT(DISTINCT ws.user.id), " +
                                "COUNT(ws.id), " +
                                "SUM(CASE WHEN ws.status = :completed THEN 1 ELSE 0 END) " +
                                "FROM WorkoutSession ws JOIN ws.user u LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                                "WHERE " + workoutSessionInRangeJpql("ws") + " " +
                                "GROUP BY UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE'))",
                        Object[].class)
                .setParameter("completed", WorkoutSessionStatus.COMPLETED)
                .setParameter("startDate", startZdt.toLocalDate())
                .setParameter("endDate", endZdt.toLocalDate())
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList();

        List<Object[]> prStats = em.createQuery(
                        "SELECT UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE')), COUNT(pr.id) " +
                                "FROM PersonalRecord pr JOIN pr.user u LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                                "WHERE pr.achievedAt BETWEEN :startZdt AND :endZdt " +
                                "GROUP BY UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE'))",
                        Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList();

        List<Object[]> aiStats = em.createQuery(
                        "SELECT UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE')), COUNT(m.id) " +
                                "FROM AiMessage m JOIN m.conversation c JOIN c.user u LEFT JOIN ClientProfile cp ON cp.user.id = u.id " +
                                "WHERE m.createdAt BETWEEN :startZdt AND :endZdt " +
                                "GROUP BY UPPER(COALESCE(cp.experienceLevel, u.experienceLevel, 'INTERMEDIATE'))",
                        Object[].class)
                .setParameter("startZdt", startZdt)
                .setParameter("endZdt", endZdt)
                .getResultList();

        return userCounts.isEmpty() ? List.of() : mergeExperienceBuckets(userCounts, workoutStats, prStats, aiStats);
    }

    private List<Object[]> mergeExperienceBuckets(
            List<Object[]> userCounts,
            List<Object[]> workoutStats,
            List<Object[]> prStats,
            List<Object[]> aiStats
    ) {
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (Object[] r : userCounts) {
            buckets.computeIfAbsent((String) r[0], k -> new long[6])[0] = toLong(r[1]);
        }
        for (Object[] r : workoutStats) {
            long[] arr = buckets.computeIfAbsent((String) r[0], k -> new long[6]);
            arr[1] = toLong(r[1]); // active users
            arr[2] = toLong(r[2]); // total workouts
            arr[3] = toLong(r[3]); // completed workouts
        }
        for (Object[] r : prStats) {
            buckets.computeIfAbsent((String) r[0], k -> new long[6])[4] = toLong(r[1]);
        }
        for (Object[] r : aiStats) {
            buckets.computeIfAbsent((String) r[0], k -> new long[6])[5] = toLong(r[1]);
        }
        List<Object[]> merged = new ArrayList<>(buckets.size());
        buckets.forEach((level, arr) -> merged.add(new Object[]{level, arr[0], arr[1], arr[2], arr[3], arr[4], arr[5]}));
        return merged;
    }

    private Map<Long, UserActivityAggRow> toActivityMap(List<Object[]> rows) {
        Map<Long, UserActivityAggRow> map = new HashMap<>();
        for (Object[] r : rows) {
            Long uid = (Long) r[0];
            map.put(uid, new UserActivityAggRow(uid, toLong(r[1]), toLong(r[2]), (ZonedDateTime) r[3]));
        }
        return map;
    }

    private List<LabelCountRow> toLabelCounts(List<Object[]> rows) {
        List<LabelCountRow> list = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            list.add(new LabelCountRow(String.valueOf(r[0]), toLong(r[1])));
        }
        return list;
    }

    private String workoutSessionInRangeJpql(String alias) {
        return "((" + alias + ".completedAt IS NOT NULL AND " + alias + ".completedAt BETWEEN :startZdt AND :endZdt) " +
                "OR (" + alias + ".completedAt IS NULL AND " + alias + ".startedAt IS NOT NULL AND " + alias + ".startedAt BETWEEN :startZdt AND :endZdt) " +
                "OR (" + alias + ".completedAt IS NULL AND " + alias + ".startedAt IS NULL AND " + alias + ".workoutDate IS NOT NULL AND " + alias + ".workoutDate BETWEEN :startDate AND :endDate) " +
                "OR (" + alias + ".completedAt IS NULL AND " + alias + ".startedAt IS NULL AND " + alias + ".workoutDate IS NULL AND " + alias + ".createdAt BETWEEN :startZdt AND :endZdt))";
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
