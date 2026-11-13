package com.calisthenics.backend.report.repository;

import com.calisthenics.backend.report.entity.ReportReason;
import com.calisthenics.backend.report.entity.UserReport;
import com.calisthenics.backend.report.entity.UserReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    boolean existsByReporterIdAndReportedUserIdAndReasonAndStatusIn(
            Long reporterId, Long reportedUserId, ReportReason reason, Collection<UserReportStatus> statuses);

    long countByReporterIdAndCreatedAtAfter(Long reporterId, Instant since);

    long countByStatus(UserReportStatus status);

    @EntityGraph(attributePaths = {"reporter", "reportedUser"})
    @Query("select r from UserReport r where (:status is null or r.status = :status) "
            + "and (:reportedUserId is null or r.reportedUser.id = :reportedUserId) "
            + "order by r.createdAt desc, r.id desc")
    Page<UserReport> search(@Param("status") UserReportStatus status,
                            @Param("reportedUserId") Long reportedUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "reportedUser", "evidenceUrls"})
    Optional<UserReport> findWithDetailsById(Long id);

    List<UserReport> findByReportedUserIdAndIdNotOrderByCreatedAtDescIdDesc(Long reportedUserId, Long excludeId);
}
