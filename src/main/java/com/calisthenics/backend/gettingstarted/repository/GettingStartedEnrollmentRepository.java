package com.calisthenics.backend.gettingstarted.repository;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedEnrollment;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GettingStartedEnrollmentRepository extends JpaRepository<GettingStartedEnrollment, Long> {

    Optional<GettingStartedEnrollment> findByUserIdAndProgramId(Long userId, Long programId);

    Optional<GettingStartedEnrollment> findFirstByUserIdAndStatusOrderByEnrolledAtDesc(Long userId, GettingStartedEnrollmentStatus status);

    List<GettingStartedEnrollment> findAllByUserIdOrderByEnrolledAtDesc(Long userId);
}
