package com.calisthenics.backend.analytics.repository;

import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, Long> {

    boolean existsByUserIdAndWorkoutSessionIdAndExerciseIdAndRecordType(
            Long userId, Long workoutSessionId, Long exerciseId, PersonalRecordType recordType);

    @Query("SELECT COUNT(pr) > 0 FROM PersonalRecord pr WHERE pr.user.id = :userId " +
           "AND pr.exercise.id = :exerciseId AND pr.recordType = :recordType " +
           "AND pr.recordValue = :recordValue " +
           "AND pr.achievedAt BETWEEN :start AND :end")
    boolean existsSimilarPr(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            @Param("recordType") PersonalRecordType recordType,
            @Param("recordValue") BigDecimal recordValue,
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @Query("SELECT COUNT(pr) > 0 FROM PersonalRecord pr WHERE pr.user.id = :userId " +
           "AND LOWER(pr.customExerciseName) = LOWER(:customExerciseName) AND pr.recordType = :recordType " +
           "AND pr.recordValue = :recordValue " +
           "AND pr.achievedAt BETWEEN :start AND :end")
    boolean existsSimilarCustomPr(
            @Param("userId") Long userId,
            @Param("customExerciseName") String customExerciseName,
            @Param("recordType") PersonalRecordType recordType,
            @Param("recordValue") BigDecimal recordValue,
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end);

    @EntityGraph(attributePaths = {"exercise", "workoutSession"})
    List<PersonalRecord> findByWorkoutSessionId(Long workoutSessionId);

    @EntityGraph(attributePaths = {"exercise", "workoutSession"})
    List<PersonalRecord> findByUserIdOrderByAchievedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"exercise", "workoutSession"})
    List<PersonalRecord> findByUserIdAndExerciseIdOrderByAchievedAtDesc(Long userId, Long exerciseId);

    @Query("SELECT pr FROM PersonalRecord pr " +
           "LEFT JOIN FETCH pr.exercise " +
           "LEFT JOIN FETCH pr.workoutSession " +
           "WHERE pr.user.id = :userId " +
           "AND (:exerciseId IS NULL OR pr.exercise.id = :exerciseId) " +
           "AND (:recordType IS NULL OR pr.recordType = :recordType) " +
           "ORDER BY pr.achievedAt DESC")
    List<PersonalRecord> findByUserIdAndFilters(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            @Param("recordType") PersonalRecordType recordType);

    @Query("SELECT pr FROM PersonalRecord pr " +
           "LEFT JOIN FETCH pr.exercise " +
           "LEFT JOIN FETCH pr.workoutSession " +
           "WHERE pr.user.id = :userId " +
           "AND pr.exercise.id = :exerciseId " +
           "AND (:recordType IS NULL OR pr.recordType = :recordType) " +
           "ORDER BY pr.achievedAt ASC")
    List<PersonalRecord> findHistoryByUserIdAndExerciseId(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            @Param("recordType") PersonalRecordType recordType);

    @Query("SELECT pr FROM PersonalRecord pr " +
           "LEFT JOIN FETCH pr.exercise " +
           "WHERE pr.user.id = :userId " +
           "AND pr.exercise.id = :exerciseId " +
           "AND (:recordType IS NULL OR pr.recordType = :recordType) " +
           "ORDER BY pr.achievedAt DESC, pr.recordValue DESC LIMIT 1")
    Optional<PersonalRecord> findLatestByUserIdAndExerciseId(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            @Param("recordType") PersonalRecordType recordType);

    Optional<PersonalRecord> findTopByUserIdAndExerciseIdAndRecordTypeOrderByRecordValueDesc(
            Long userId, Long exerciseId, PersonalRecordType recordType);

    @Query("SELECT pr FROM PersonalRecord pr WHERE pr.user.id = :userId " +
           "AND pr.exercise.id = :exerciseId AND pr.recordType = :recordType " +
           "AND pr.achievedAt < :before ORDER BY pr.recordValue DESC LIMIT 1")
    Optional<PersonalRecord> findBestHistoricalRecord(
            @Param("userId") Long userId,
            @Param("exerciseId") Long exerciseId,
            @Param("recordType") PersonalRecordType recordType,
            @Param("before") ZonedDateTime before);

    long countByUserId(Long userId);

    long countByUserIdAndAchievedAtBetween(Long userId, ZonedDateTime start, ZonedDateTime end);

    long countByExerciseId(Long exerciseId);

    @Modifying(flushAutomatically = true)
    @Query(
            value = "UPDATE personal_records SET workout_session_id = NULL, workout_session_set_id = NULL " +
                    "WHERE workout_session_id IN (SELECT id FROM workout_sessions WHERE workout_plan_id = :workoutPlanId)",
            nativeQuery = true
    )
    int detachWorkoutSessionsByPlanId(@Param("workoutPlanId") Long workoutPlanId);
}
