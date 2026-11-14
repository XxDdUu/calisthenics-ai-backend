package com.calisthenics.backend.workout;

import com.calisthenics.backend.admin.dto.AdminWorkoutSessionSummaryResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {

    @Query(
            value = "select new com.calisthenics.backend.admin.dto.AdminWorkoutSessionSummaryResponse(" +
                    "s.id, u.id, u.email, u.fullName, s.title, s.workoutDate, s.status, " +
                    "p.id, p.name, s.createdAt, s.startedAt, s.completedAt) " +
                    "from WorkoutSession s join s.user u left join s.workoutPlan p",
            countQuery = "select count(s) from WorkoutSession s"
    )
    Page<AdminWorkoutSessionSummaryResponse> findAdminSummaries(Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "workoutPlan", "workoutPlanDay", "workoutPlanAssignment",
            "exercises", "exercises.exercise", "exercises.sets"
    })
    Optional<WorkoutSession> findWithDetailsById(Long id);

    List<WorkoutSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, WorkoutSessionStatus status);

    long countByUserIdAndCompletedAtIsNotNull(Long userId);

    List<WorkoutSession> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    Optional<WorkoutSession> findByUserIdAndWorkoutPlanDayIdAndWorkoutDateAndStatus(
            Long userId, Long workoutPlanDayId, LocalDate workoutDate, WorkoutSessionStatus status);

    List<WorkoutSession> findByUserIdAndWorkoutDateAndStatus(
            Long userId, LocalDate workoutDate, WorkoutSessionStatus status);

    List<WorkoutSession> findByUserIdAndWorkoutDateAndStatusIn(
            Long userId, LocalDate workoutDate, Collection<WorkoutSessionStatus> statuses);

    boolean existsByUserIdAndWorkoutDateAndStatus(
            Long userId, LocalDate workoutDate, WorkoutSessionStatus status);

    boolean existsByUserIdAndWorkoutDateAndStatusIn(
            Long userId, LocalDate workoutDate, Collection<WorkoutSessionStatus> statuses);

    List<WorkoutSession> findByUserIdAndStatusAndStartedAtIsNullAndWorkoutDateAfter(
            Long userId, WorkoutSessionStatus status, LocalDate date);

    List<WorkoutSession> findByWorkoutPlanAssignmentIdInAndStatusIn(
            Collection<Long> assignmentIds, Collection<WorkoutSessionStatus> statuses);

    List<WorkoutSession> findByUserIdAndStatusOrderByStartedAtDesc(Long userId, WorkoutSessionStatus status);

    @Query("SELECT s FROM WorkoutSession s WHERE s.user.id = :userId " +
           "AND s.status IN ('IN_PROGRESS', 'SCHEDULED') " +
           "AND ((s.workoutDate IS NOT NULL AND s.workoutDate <= :today) " +
           "OR (s.workoutDate IS NULL AND s.status = 'IN_PROGRESS')) " +
           "ORDER BY s.workoutDate DESC, s.startedAt DESC")
    List<WorkoutSession> findUnfinishedSessionsAsOf(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("today") LocalDate today);

    @Query("SELECT s FROM WorkoutSession s WHERE s.user.id = :userId " +
           "AND s.status IN ('SCHEDULED', 'IN_PROGRESS') " +
           "AND s.workoutDate IS NOT NULL AND s.workoutDate > :today " +
           "ORDER BY s.workoutDate ASC, s.id ASC")
    List<WorkoutSession> findUpcomingSessionsAfter(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("today") LocalDate today);

    Page<WorkoutSession> findByUserIdAndStatus(Long userId, WorkoutSessionStatus status, Pageable pageable);

    List<WorkoutSession> findByUserIdAndStatusOrderByWorkoutDateDesc(Long userId, WorkoutSessionStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM WorkoutSession s JOIN s.exercises e " +
           "WHERE s.user.id = :userId AND s.status = :status AND e.exercise.id = :exerciseId " +
           "ORDER BY s.workoutDate DESC")
    List<WorkoutSession> findCompletedSessionsByExercise(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("status") WorkoutSessionStatus status,
            @org.springframework.data.repository.query.Param("exerciseId") Long exerciseId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s.workoutDate FROM WorkoutSession s WHERE s.user.id = :userId " +
           "AND s.status = :status AND s.workoutDate IS NOT NULL ORDER BY s.workoutDate DESC")
    List<LocalDate> findDistinctCompletedWorkoutDates(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("status") WorkoutSessionStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM WorkoutSession s " +
           "WHERE s.user.id = :userId AND s.status = :status AND s.workoutDate BETWEEN :startDate AND :endDate " +
           "ORDER BY s.workoutDate ASC")
    List<WorkoutSession> findCompletedSessionsBetweenDates(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("status") WorkoutSessionStatus status,
            @org.springframework.data.repository.query.Param("startDate") LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") LocalDate endDate);

    @Modifying(flushAutomatically = true)
    @Query(
            value = "DELETE FROM workout_session_sets WHERE workout_session_exercise_id IN (" +
                    "SELECT id FROM workout_session_exercises WHERE workout_session_id IN (" +
                    "SELECT id FROM workout_sessions WHERE workout_plan_id = :workoutPlanId))",
            nativeQuery = true
    )
    int deleteSessionSetsByWorkoutPlanId(@Param("workoutPlanId") Long workoutPlanId);

    @Modifying(flushAutomatically = true)
    @Query(
            value = "DELETE FROM workout_session_exercises WHERE workout_session_id IN (" +
                    "SELECT id FROM workout_sessions WHERE workout_plan_id = :workoutPlanId)",
            nativeQuery = true
    )
    int deleteSessionExercisesByWorkoutPlanId(@Param("workoutPlanId") Long workoutPlanId);

    @Modifying(flushAutomatically = true)
    @Query(
            value = "DELETE FROM workout_logs WHERE workout_session_id IN (" +
                    "SELECT id FROM workout_sessions WHERE workout_plan_id = :workoutPlanId)",
            nativeQuery = true
    )
    int deleteWorkoutLogsByWorkoutPlanId(@Param("workoutPlanId") Long workoutPlanId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "DELETE FROM workout_sessions WHERE workout_plan_id = :workoutPlanId",
            nativeQuery = true
    )
    int deleteAllByWorkoutPlanId(@Param("workoutPlanId") Long workoutPlanId);
    List<WorkoutSession> findByUserIdAndWorkoutDateOrderByIdAsc(Long userId, LocalDate workoutDate);

    List<WorkoutSession> findByGettingStartedEnrollmentIdAndStatusIn(
            Long enrollmentId, java.util.Collection<WorkoutSessionStatus> statuses);

    Optional<WorkoutSession> findFirstByGettingStartedEnrollmentIdAndGettingStartedDayNumberAndStatusInOrderByIdDesc(
            Long enrollmentId, Integer dayNumber, java.util.Collection<WorkoutSessionStatus> statuses);

    Optional<WorkoutSession> findFirstByGettingStartedEnrollmentIdAndGettingStartedDayNumberOrderByIdDesc(
            Long enrollmentId, Integer dayNumber);
}
