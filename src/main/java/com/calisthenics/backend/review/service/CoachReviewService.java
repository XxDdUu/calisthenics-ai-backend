package com.calisthenics.backend.review.service;

import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.review.dto.*;
import com.calisthenics.backend.review.entity.*;
import com.calisthenics.backend.review.repository.CoachReviewEventRepository;
import com.calisthenics.backend.review.repository.CoachReviewRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CoachReviewService {

    private static final List<CoachClientRelationshipStatus> ELIGIBLE_STATUSES =
            List.of(CoachClientRelationshipStatus.ACTIVE, CoachClientRelationshipStatus.TERMINATED);

    private final CoachReviewRepository reviewRepository;
    private final CoachReviewEventRepository eventRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;

    /** Minimum seconds between two edits of the same review (anti-spam). */
    @Value("${app.reviews.edit-cooldown-seconds:30}")
    private long editCooldownSeconds;

    // ------------------------------------------------------------------ student / public

    @Transactional(readOnly = true)
    public CoachReviewSummaryResponse getSummary(Long coachId) {
        requireUser(coachId, "Coach not found");
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(String.valueOf(star), 0L);
        }
        long total = 0;
        for (Object[] row : reviewRepository.countByRating(coachId)) {
            long count = ((Number) row[1]).longValue();
            distribution.put(String.valueOf(((Number) row[0]).intValue()), count);
            total += count;
        }
        Object[] avg = reviewRepository.averages(coachId).get(0);
        return new CoachReviewSummaryResponse(coachId,
                total == 0 ? null : round(avg[0]), total, distribution,
                round(avg[1]), round(avg[2]), round(avg[3]), round(avg[4]));
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<CoachReviewResponse> listVisible(Long viewerId, Long coachId, int page, int size) {
        requireUser(coachId, "Coach not found");
        Page<CoachReview> result = reviewRepository.findByCoachIdAndStatusOrderByCreatedAtDescIdDesc(
                coachId, CoachReviewStatus.VISIBLE, PageRequest.of(Math.max(page, 0), clamp(size)));
        return AdminPageResponse.from(result.map(r -> toResponse(r, viewerId)));
    }

    @Transactional(readOnly = true)
    public MyCoachReviewResponse getMine(Long studentId, Long coachId) {
        requireUser(coachId, "Coach not found");
        CoachReview existing = reviewRepository.findByCoachIdAndStudentId(coachId, studentId).orElse(null);
        String reason = ineligibleReason(studentId, coachId);
        // A student who already reviewed stays eligible to edit even if the relationship state changes.
        boolean eligible = reason == null || existing != null && !Objects.equals(coachId, studentId);
        return new MyCoachReviewResponse(eligible, eligible ? null : reason,
                existing == null ? null : toResponse(existing, studentId));
    }

    @Transactional
    public CoachReviewResponse create(Long studentId, Long coachId, CoachReviewRequest request) {
        User student = requireUser(studentId, "User not found");
        User coach = requireUser(coachId, "Coach not found");
        assertEligible(studentId, coachId);
        if (reviewRepository.existsByCoachIdAndStudentId(coachId, studentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this coach. Edit your review instead.");
        }
        CoachReview review = CoachReview.builder()
                .coach(coach)
                .student(student)
                .status(CoachReviewStatus.VISIBLE)
                .build();
        apply(review, request);
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this coach. Edit your review instead.");
        }
        record(review, CoachReviewEventType.CREATED, studentId, null);
        return toResponse(review, studentId);
    }

    @Transactional
    public CoachReviewResponse edit(Long studentId, Long coachId, CoachReviewRequest request) {
        CoachReview review = reviewRepository.findByCoachIdAndStudentId(coachId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You have not reviewed this coach yet"));
        if (editCooldownSeconds > 0 && review.getUpdatedAt() != null
                && Duration.between(review.getUpdatedAt(), Instant.now()).getSeconds() < editCooldownSeconds) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You edited this review very recently. Please wait a moment before editing again.");
        }
        apply(review, request);
        review = reviewRepository.saveAndFlush(review);
        record(review, CoachReviewEventType.EDITED, studentId, null);
        return toResponse(review, studentId);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminCoachReviewResponse> adminList(CoachReviewStatus status, Long coachId, int page, int size) {
        Page<CoachReview> result = reviewRepository.search(status, coachId, PageRequest.of(Math.max(page, 0), clamp(size)));
        return AdminPageResponse.from(result.map(r -> toAdmin(r, false)));
    }

    @Transactional(readOnly = true)
    public AdminCoachReviewResponse adminGet(Long reviewId) {
        return toAdmin(requireReview(reviewId), true);
    }

    @Transactional
    public AdminCoachReviewResponse moderate(Long reviewId, Long adminId, ModerateCoachReviewRequest request) {
        CoachReview review = requireReview(reviewId);
        String reason = request.reason() == null ? null : request.reason().trim();
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        if (request.status() == CoachReviewStatus.HIDDEN && reason == null) {
            throw new IllegalArgumentException("A reason is required to hide a review");
        }
        if (review.getStatus() == request.status()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review is already " + request.status());
        }
        review.setStatus(request.status());
        review.setModerationReason(reason);
        review.setModeratedByAdminId(adminId);
        review.setModeratedAt(Instant.now());
        review = reviewRepository.saveAndFlush(review);
        record(review, request.status() == CoachReviewStatus.HIDDEN ? CoachReviewEventType.HIDDEN : CoachReviewEventType.RESTORED,
                adminId, reason);
        return toAdmin(review, true);
    }

    // ------------------------------------------------------------------ helpers

    private void assertEligible(Long studentId, Long coachId) {
        String reason = ineligibleReason(studentId, coachId);
        if (reason != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
        }
    }

    private String ineligibleReason(Long studentId, Long coachId) {
        if (Objects.equals(studentId, coachId)) {
            return "You cannot review yourself";
        }
        if (!relationshipRepository.existsByCoachIdAndClientIdAndStatusIn(coachId, studentId, ELIGIBLE_STATUSES)) {
            return "Only students who are or were assigned to this coach can leave a review";
        }
        return null;
    }

    private void apply(CoachReview review, CoachReviewRequest request) {
        review.setOverallRating(request.overallRating());
        review.setCoachingQuality(request.coachingQuality());
        review.setCommunication(request.communication());
        review.setPlanEffectiveness(request.planEffectiveness());
        review.setSupportResponsiveness(request.supportResponsiveness());
        String comment = request.comment() == null ? null : request.comment().trim();
        review.setComment(comment == null || comment.isEmpty() ? null : comment);
    }

    private void record(CoachReview review, CoachReviewEventType type, Long actorId, String note) {
        eventRepository.save(CoachReviewEvent.builder()
                .reviewId(review.getId())
                .eventType(type)
                .actorId(actorId)
                .overallRating(review.getOverallRating())
                .comment(review.getComment())
                .note(note)
                .build());
    }

    private User requireUser(Long id, String message) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }

    private CoachReview requireReview(Long id) {
        return reviewRepository.findWithPartiesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    private CoachReviewResponse toResponse(CoachReview r, Long viewerId) {
        User student = r.getStudent();
        boolean edited = r.getUpdatedAt() != null && r.getCreatedAt() != null
                && Duration.between(r.getCreatedAt(), r.getUpdatedAt()).getSeconds() >= 1
                && r.getVersion() != null && r.getVersion() > 0;
        return new CoachReviewResponse(r.getId(), r.getOverallRating(), r.getCoachingQuality(), r.getCommunication(),
                r.getPlanEffectiveness(), r.getSupportResponsiveness(), r.getComment(),
                publicName(student.getFullName()), student.getAvatarUrl(),
                Objects.equals(student.getId(), viewerId), edited, r.getCreatedAt(), r.getUpdatedAt());
    }

    private AdminCoachReviewResponse toAdmin(CoachReview r, boolean withHistory) {
        List<AdminCoachReviewResponse.Event> history = withHistory
                ? eventRepository.findByReviewIdOrderByCreatedAtAscIdAsc(r.getId()).stream()
                .map(e -> new AdminCoachReviewResponse.Event(e.getEventType().name(), e.getActorId(),
                        e.getOverallRating(), e.getComment(), e.getNote(), e.getCreatedAt()))
                .toList()
                : List.of();
        return new AdminCoachReviewResponse(r.getId(), r.getCoach().getId(), r.getCoach().getFullName(),
                r.getStudent().getId(), r.getStudent().getFullName(), r.getOverallRating(), r.getCoachingQuality(),
                r.getCommunication(), r.getPlanEffectiveness(), r.getSupportResponsiveness(), r.getComment(),
                r.getStatus().name(), r.getModerationReason(), r.getModeratedByAdminId(), r.getModeratedAt(),
                r.getCreatedAt(), r.getUpdatedAt(), history);
    }

    /** "Nguyen Van A" becomes "Nguyen A."; never exposes the full name. */
    static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Student";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }

    private static Double round(Object value) {
        if (value == null) {
            return null;
        }
        return Math.round(((Number) value).doubleValue() * 10.0) / 10.0;
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), 50);
    }
}
