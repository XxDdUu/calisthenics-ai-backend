package com.calisthenics.backend.workout;

import com.calisthenics.backend.admin.dto.AdminWorkoutSessionSummaryResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    long countByUserIdAndCompletedAtIsNotNull(Long userId);

    List<WorkoutSession> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);

    Optional<WorkoutSession> findByUserIdAndWorkoutPlanDayIdAndWorkoutDateAndStatus(
            Long userId, Long workoutPlanDayId, LocalDate workoutDate, WorkoutSessionStatus status);

    List<WorkoutSession> findByUserIdAndWorkoutDateAndStatus(
            Long userId, LocalDate workoutDate, WorkoutSessionStatus status);

    List<WorkoutSession> findByUserIdAndStatusOrderByStartedAtDesc(Long userId, WorkoutSessionStatus status);

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
}
