package com.calisthenics.backend.report.service;

import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.report.dto.*;
import com.calisthenics.backend.report.entity.*;
import com.calisthenics.backend.report.repository.UserReportActionRepository;
import com.calisthenics.backend.report.repository.UserReportRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserReportService {

    static final String STORAGE_PATH_MARKER = "/api/v1/storage/files/";
    private static final List<UserReportStatus> ACTIVE = List.of(UserReportStatus.PENDING, UserReportStatus.UNDER_REVIEW);
    private static final int MAX_EVIDENCE_URL_LENGTH = 500;

    private final UserReportRepository reportRepository;
    private final UserReportActionRepository actionRepository;
    private final UserRepository userRepository;

    /** Max reports one user may file per rolling 24h (anti-spam). */
    @Value("${app.reports.max-per-day:10}")
    private int maxReportsPerDay;

    // ------------------------------------------------------------------ reporter

    @Transactional
    public UserReportReceiptResponse submit(Long reporterId, Long reportedUserId, CreateUserReportRequest request) {
        if (Objects.equals(reporterId, reportedUserId)) {
            throw new IllegalArgumentException("You cannot report yourself");
        }
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User reported = userRepository.findById(reportedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<String> evidence = validateEvidence(request.evidenceUrls());

        if (reportRepository.countByReporterIdAndCreatedAtAfter(reporterId, Instant.now().minus(Duration.ofDays(1))) >= maxReportsPerDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have submitted too many reports recently. Please try again later.");
        }
        if (reportRepository.existsByReporterIdAndReportedUserIdAndReasonAndStatusIn(
                reporterId, reportedUserId, request.reason(), ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open report for this user with the same reason.");
        }

        String description = request.description() == null ? null : request.description().trim();
        UserReport report = UserReport.builder()
                .reporter(reporter)
                .reportedUser(reported)
                .reason(request.reason())
                .description(description == null || description.isEmpty() ? null : description)
                .evidenceUrls(evidence)
                .status(UserReportStatus.PENDING)
                .build();
        try {
            report = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have an open report for this user with the same reason.");
        }
        return new UserReportReceiptResponse(report.getId(), report.getStatus().name(), report.getCreatedAt());
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public UserReportSummaryResponse summary() {
        long pending = reportRepository.countByStatus(UserReportStatus.PENDING);
        long underReview = reportRepository.countByStatus(UserReportStatus.UNDER_REVIEW);
        long resolved = reportRepository.countByStatus(UserReportStatus.RESOLVED);
        long dismissed = reportRepository.countByStatus(UserReportStatus.DISMISSED);
        return new UserReportSummaryResponse(pending + underReview + resolved + dismissed,
                pending, underReview, resolved, dismissed);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserReportResponse> list(UserReportStatus status, Long reportedUserId, int page, int size) {
        return AdminPageResponse.from(reportRepository
                .search(status, reportedUserId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)))
                .map(r -> toResponse(r, false)));
    }

    @Transactional(readOnly = true)
    public AdminUserReportResponse get(Long reportId) {
        return toResponse(require(reportId), true);
    }

    @Transactional
    public AdminUserReportResponse changeStatus(Long reportId, Long adminId, UpdateUserReportStatusRequest request) {
        UserReport report = require(reportId);
        UserReportStatus from = report.getStatus();
        UserReportStatus to = request.status();
        if (!isAllowedTransition(from, to)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot change report status from " + from + " to " + to);
        }
        Instant now = Instant.now();
        report.setStatus(to);
        report.setHandledByAdminId(adminId);
        if (report.getReviewedAt() == null) {
            report.setReviewedAt(now);
        }
        if (to == UserReportStatus.RESOLVED || to == UserReportStatus.DISMISSED) {
            report.setResolvedAt(now);
        }
        reportRepository.saveAndFlush(report);
        actionRepository.save(UserReportAction.builder()
                .reportId(reportId).actionType(UserReportActionType.STATUS_CHANGE)
                .fromStatus(from).toStatus(to).note(clean(request.note())).adminId(adminId).build());
        return toResponse(report, true);
    }

    @Transactional
    public AdminUserReportResponse addNote(Long reportId, Long adminId, AddUserReportNoteRequest request) {
        UserReport report = require(reportId);
        actionRepository.save(UserReportAction.builder()
                .reportId(reportId).actionType(UserReportActionType.NOTE)
                .note(clean(request.note())).adminId(adminId).build());
        return toResponse(report, true);
    }

    /**
     * Records the administrator's moderation decision against the report. The platform has no account
     * suspension mechanism, so this is an auditable decision only; it never changes the user's account.
     */
    @Transactional
    public AdminUserReportResponse recordAction(Long reportId, Long adminId, RecordModerationActionRequest request) {
        UserReport report = require(reportId);
        if (report.getStatus() == UserReportStatus.DISMISSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot take action on a dismissed report");
        }
        if (request.action() == ModerationAction.TEMPORARY_SUSPENSION) {
            if (request.suspendedUntil() == null || !request.suspendedUntil().isAfter(Instant.now())) {
                throw new IllegalArgumentException("A future suspension end date is required for a temporary suspension");
            }
        } else if (request.suspendedUntil() != null) {
            throw new IllegalArgumentException("A suspension end date only applies to a temporary suspension");
        }
        actionRepository.save(UserReportAction.builder()
                .reportId(reportId).actionType(UserReportActionType.MODERATION_ACTION)
                .moderationAction(request.action()).suspendedUntil(request.suspendedUntil())
                .note(clean(request.note())).adminId(adminId).build());
        return toResponse(report, true);
    }

    // ------------------------------------------------------------------ helpers

    static boolean isAllowedTransition(UserReportStatus from, UserReportStatus to) {
        return switch (from) {
            case PENDING -> to == UserReportStatus.UNDER_REVIEW || to == UserReportStatus.RESOLVED
                    || to == UserReportStatus.DISMISSED;
            case UNDER_REVIEW -> to == UserReportStatus.RESOLVED || to == UserReportStatus.DISMISSED;
            case RESOLVED, DISMISSED -> false;
        };
    }

    private List<String> validateEvidence(List<String> urls) {
        if (urls == null) {
            return new ArrayList<>();
        }
        List<String> cleaned = new ArrayList<>();
        for (String raw : urls) {
            String url = raw == null ? "" : raw.trim();
            boolean ok = !url.isEmpty() && url.length() <= MAX_EVIDENCE_URL_LENGTH
                    && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/"))
                    && url.contains(STORAGE_PATH_MARKER);
            if (!ok) {
                throw new IllegalArgumentException("Evidence must be a file uploaded through the app");
            }
            if (!cleaned.contains(url)) {
                cleaned.add(url);
            }
        }
        return cleaned;
    }

    private UserReport require(Long id) {
        return reportRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AdminUserReportResponse toResponse(UserReport r, boolean detail) {
        AdminUserReportResponse.PartyInfo reporter = party(r.getReporter());
        AdminUserReportResponse.PartyInfo reported = party(r.getReportedUser());
        if (!detail) {
            return new AdminUserReportResponse(r.getId(), r.getReason().name(), r.getStatus().name(), reporter, reported,
                    r.getCreatedAt(), r.getUpdatedAt(), null, List.of(), r.getHandledByAdminId(), r.getReviewedAt(),
                    r.getResolvedAt(), List.of(), List.of());
        }
        List<AdminUserReportResponse.PreviousReport> previous = reportRepository
                .findByReportedUserIdAndIdNotOrderByCreatedAtDescIdDesc(r.getReportedUser().getId(), r.getId()).stream()
                .map(p -> new AdminUserReportResponse.PreviousReport(p.getId(), p.getReason().name(),
                        p.getStatus().name(), p.getCreatedAt()))
                .toList();
        List<UserReportAction> actions = actionRepository.findByReportIdOrderByCreatedAtAscIdAsc(r.getId());
        Map<Long, String> adminNames = userRepository.findAllById(actions.stream()
                        .map(UserReportAction::getAdminId).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        List<AdminUserReportResponse.ActionInfo> actionInfos = actions.stream()
                .map(a -> new AdminUserReportResponse.ActionInfo(a.getId(), a.getActionType().name(),
                        a.getFromStatus() == null ? null : a.getFromStatus().name(),
                        a.getToStatus() == null ? null : a.getToStatus().name(),
                        a.getModerationAction() == null ? null : a.getModerationAction().name(),
                        a.getSuspendedUntil(), a.getNote(), a.getAdminId(),
                        a.getAdminId() == null ? null : adminNames.get(a.getAdminId()), a.getCreatedAt()))
                .toList();
        return new AdminUserReportResponse(r.getId(), r.getReason().name(), r.getStatus().name(), reporter, reported,
                r.getCreatedAt(), r.getUpdatedAt(), r.getDescription(), List.copyOf(r.getEvidenceUrls()),
                r.getHandledByAdminId(), r.getReviewedAt(), r.getResolvedAt(), previous, actionInfos);
    }

    private static AdminUserReportResponse.PartyInfo party(User u) {
        return new AdminUserReportResponse.PartyInfo(u.getId(), u.getFullName(), u.getEmail());
    }
}
