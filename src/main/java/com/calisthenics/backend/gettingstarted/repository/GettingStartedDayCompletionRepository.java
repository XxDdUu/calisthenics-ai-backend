package com.calisthenics.backend.gettingstarted.repository;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedDayCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GettingStartedDayCompletionRepository extends JpaRepository<GettingStartedDayCompletion, Long> {

    Optional<GettingStartedDayCompletion> findByEnrollmentIdAndDayNumber(Long enrollmentId, Integer dayNumber);

    long countByEnrollmentProgramIdAndDayNumber(Long programId, Integer dayNumber);
}
