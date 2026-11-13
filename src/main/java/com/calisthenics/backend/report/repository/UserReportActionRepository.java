package com.calisthenics.backend.report.repository;

import com.calisthenics.backend.report.entity.UserReportAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserReportActionRepository extends JpaRepository<UserReportAction, Long> {
    List<UserReportAction> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);
}
