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

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanAssignmentRepository extends JpaRepository<WorkoutPlanAssignment, Long> {

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByClientIdOrderByCreatedAtDesc(Long clientId);

    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    List<WorkoutPlanAssignment> findByRelationshipIdOrderByCreatedAtDesc(Long relationshipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"workoutPlan", "workoutPlan.days", "coach", "client", "relationship"})
    @Query("select assignment from WorkoutPlanAssignment assignment where assignment.id = :id")
    Optional<WorkoutPlanAssignment> findByIdForUpdate(@Param("id") Long id);

    List<WorkoutPlanAssignment> findByRelationshipIdAndStatus(
            Long relationshipId, WorkoutPlanAssignmentStatus status);

    long countByCoachIdAndStatus(Long coachId, WorkoutPlanAssignmentStatus status);
}
