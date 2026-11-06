package com.calisthenics.backend.workoutplan.repository;

import com.calisthenics.backend.admin.dto.AdminWorkoutPlanSummaryResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    List<WorkoutPlan> findByUserIdOrderByActiveDescCreatedAtDesc(Long userId);

    /** Retained for existing callers that intentionally want the chronological plan order. */
    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    List<WorkoutPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    Optional<WorkoutPlan> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    Optional<WorkoutPlan> findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(Long userId);

    boolean existsByUserIdAndActiveTrue(Long userId);

    @EntityGraph(attributePaths = {"user", "days", "days.exercises", "days.exercises.exercise"})
    Optional<WorkoutPlan> findWithDetailsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"days", "days.exercises", "days.exercises.exercise"})
    @Query("select plan from WorkoutPlan plan where plan.user.id = :userId order by plan.createdAt desc")
    List<WorkoutPlan> findByUserIdForUpdate(Long userId);

    @Query(
            value = "select new com.calisthenics.backend.admin.dto.AdminWorkoutPlanSummaryResponse(" +
                    "p.id, u.id, u.email, u.fullName, p.name, p.splitType, p.createdAt, p.updatedAt) " +
                    "from WorkoutPlan p join p.user u",
            countQuery = "select count(p) from WorkoutPlan p"
    )
    Page<AdminWorkoutPlanSummaryResponse> findAdminSummaries(Pageable pageable);

    long countByUserId(Long userId);

    Optional<WorkoutPlan> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);
}
