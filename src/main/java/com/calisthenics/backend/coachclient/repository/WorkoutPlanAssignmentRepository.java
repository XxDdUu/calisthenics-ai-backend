package com.calisthenics.backend.coachclient.repository;

import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanAssignmentRepository extends JpaRepository<WorkoutPlanAssignment, Long> {

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByClientIdOrderByCreatedAtDesc(Long clientId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByClientIdAndStatusOrderByCreatedAtDesc(
            Long clientId, WorkoutPlanAssignmentStatus status);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByClientIdAndStatusNotInOrderByCreatedAtDesc(
            Long clientId, Collection<WorkoutPlanAssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByCoachIdOrderByCreatedAtDesc(Long coachId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByCoachIdAndStatusOrderByCreatedAtDesc(
            Long coachId, WorkoutPlanAssignmentStatus status);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByCoachIdAndStatusNotInOrderByCreatedAtDesc(
            Long coachId, Collection<WorkoutPlanAssignmentStatus> statuses);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByCoachIdAndClientIdOrderByCreatedAtDesc(Long coachId, Long clientId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    Optional<WorkoutPlanAssignment> findByIdAndCoachId(Long id, Long coachId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    Optional<WorkoutPlanAssignment> findByIdAndClientId(Long id, Long clientId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    Optional<WorkoutPlanAssignment> findFirstByClientIdAndStatusOrderByCreatedAtDesc(
            Long clientId, WorkoutPlanAssignmentStatus status);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByRelationshipIdOrderByCreatedAtDesc(Long relationshipId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByRelationshipIdAndStatusOrderByCreatedAtDesc(
            Long relationshipId, WorkoutPlanAssignmentStatus status);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByRelationshipIdAndStatusNotInOrderByCreatedAtDesc(
            Long relationshipId, Collection<WorkoutPlanAssignmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    @Query("select assignment from WorkoutPlanAssignment assignment where assignment.id = :id")
    Optional<WorkoutPlanAssignment> findByIdForUpdate(@Param("id") Long id);

    List<WorkoutPlanAssignment> findByRelationshipIdAndStatus(
            Long relationshipId, WorkoutPlanAssignmentStatus status);

    @Query(
            value = "select * from workout_plan_assignments " +
                    "where relationship_id = :relationshipId and status in (:statuses) for update",
            nativeQuery = true
    )
    List<WorkoutPlanAssignment> findByRelationshipIdAndStatusInNativeForUpdate(
            @Param("relationshipId") Long relationshipId,
            @Param("statuses") Collection<String> statuses);

    default List<WorkoutPlanAssignment> findByRelationshipIdAndStatusInForUpdate(
            Long relationshipId, Collection<WorkoutPlanAssignmentStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return findByRelationshipIdAndStatusInNativeForUpdate(
                relationshipId,
                statuses.stream().map(Enum::name).toList());
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    Optional<WorkoutPlanAssignment> findFirstByRelationshipIdAndWorkoutPlanIdAndStatusOrderByCreatedAtDesc(
            Long relationshipId, Long workoutPlanId, WorkoutPlanAssignmentStatus status);

    long countByCoachIdAndStatus(Long coachId, WorkoutPlanAssignmentStatus status);
}
