package com.calisthenics.backend.gettingstarted.repository;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedDayProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GettingStartedDayProgressRepository extends JpaRepository<GettingStartedDayProgress, Long> {

    List<GettingStartedDayProgress> findByEnrollmentIdOrderByDayNumberAsc(Long enrollmentId);

    Optional<GettingStartedDayProgress> findByEnrollmentIdAndDayNumber(Long enrollmentId, Integer dayNumber);
}
