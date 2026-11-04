package com.calisthenics.backend.analytics.repository;

import com.calisthenics.backend.analytics.model.SharedPersonalRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SharedPersonalRecordRepository extends JpaRepository<SharedPersonalRecord, Long> {

    boolean existsByPersonalRecordIdAndCoachId(Long personalRecordId, Long coachId);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    Optional<SharedPersonalRecord> findByPersonalRecordIdAndCoachId(Long personalRecordId, Long coachId);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    Optional<SharedPersonalRecord> findByIdAndCoachId(Long id, Long coachId);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    List<SharedPersonalRecord> findByClientIdOrderBySharedAtDesc(Long clientId);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    List<SharedPersonalRecord> findByCoachIdOrderBySharedAtDesc(Long coachId);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    List<SharedPersonalRecord> findByCoachIdAndIsReadOrderBySharedAtDesc(Long coachId, boolean isRead);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    List<SharedPersonalRecord> findByCoachIdAndClientIdOrderBySharedAtDesc(Long coachId, Long clientId);

    @EntityGraph(attributePaths = {"personalRecord", "personalRecord.exercise", "personalRecord.workoutSession", "coach", "client"})
    List<SharedPersonalRecord> findByClientIdAndCoachFeedbackIsNotNullOrderByFeedbackAtDesc(Long clientId);
}

