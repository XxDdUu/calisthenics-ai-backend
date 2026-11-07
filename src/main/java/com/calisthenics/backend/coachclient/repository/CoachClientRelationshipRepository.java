package com.calisthenics.backend.coachclient.repository;

import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoachClientRelationshipRepository extends JpaRepository<CoachClientRelationship, Long> {

    boolean existsByCoachIdAndClientIdAndStatusIn(
            Long coachId, Long clientId, List<CoachClientRelationshipStatus> statuses);

    boolean existsByClientIdAndStatus(Long clientId, CoachClientRelationshipStatus status);

    @EntityGraph(attributePaths = {"coach", "client", "requestedBy"})
    List<CoachClientRelationship> findByCoachIdOrClientIdOrderByCreatedAtDesc(Long coachId, Long clientId);

    @EntityGraph(attributePaths = {"coach", "client", "requestedBy"})
    Optional<CoachClientRelationship> findById(Long id);

    @Query(value = "select * from coach_client_relationships where id = :id for update", nativeQuery = true)
    Optional<CoachClientRelationship> findByIdForUpdate(@Param("id") Long id);

    @Query("select r from CoachClientRelationship r join fetch r.coach join fetch r.client where " +
            "((r.coach.id = :firstUserId and r.client.id = :secondUserId) or " +
            "(r.coach.id = :secondUserId and r.client.id = :firstUserId)) and r.status = :status " +
            "order by r.updatedAt desc, r.id desc")
    List<CoachClientRelationship> findRelationshipsForUsers(
            @Param("firstUserId") Long firstUserId,
            @Param("secondUserId") Long secondUserId,
            @Param("status") CoachClientRelationshipStatus status);

    default Optional<CoachClientRelationship> findRelationshipForUsers(
            Long firstUserId, Long secondUserId, CoachClientRelationshipStatus status) {
        List<CoachClientRelationship> relationships = findRelationshipsForUsers(firstUserId, secondUserId, status);
        return relationships.isEmpty() ? Optional.empty() : Optional.of(relationships.get(0));
    }

    default Optional<CoachClientRelationship> findActiveRelationshipForUsers(Long firstUserId, Long secondUserId) {
        return findRelationshipForUsers(firstUserId, secondUserId, CoachClientRelationshipStatus.ACTIVE);
    }

    long countByCoachIdAndStatus(Long coachId, CoachClientRelationshipStatus status);

    long countByCoachIdAndStatusIn(Long coachId, List<CoachClientRelationshipStatus> statuses);

    @EntityGraph(attributePaths = {"coach", "client"})
    Optional<CoachClientRelationship> findFirstByClientIdAndStatusOrderByUpdatedAtDesc(Long clientId, CoachClientRelationshipStatus status);

    boolean existsByIdAndCoachId(Long id, Long coachId);
}
