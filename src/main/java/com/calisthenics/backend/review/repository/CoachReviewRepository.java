package com.calisthenics.backend.review.repository;

import com.calisthenics.backend.review.entity.CoachReview;
import com.calisthenics.backend.review.entity.CoachReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoachReviewRepository extends JpaRepository<CoachReview, Long> {

    @EntityGraph(attributePaths = {"coach", "student"})
    Optional<CoachReview> findByCoachIdAndStudentId(Long coachId, Long studentId);

    long countByCoachIdAndStatus(Long coachId, CoachReviewStatus status);

    boolean existsByCoachIdAndStudentId(Long coachId, Long studentId);

    @EntityGraph(attributePaths = {"coach", "student"})
    Optional<CoachReview> findWithPartiesById(Long id);

    @EntityGraph(attributePaths = {"student"})
    Page<CoachReview> findByCoachIdAndStatusOrderByCreatedAtDescIdDesc(Long coachId, CoachReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"coach", "student"})
    @Query("select r from CoachReview r where (:status is null or r.status = :status) "
            + "and (:coachId is null or r.coach.id = :coachId) order by r.createdAt desc, r.id desc")
    Page<CoachReview> search(@Param("status") CoachReviewStatus status, @Param("coachId") Long coachId, Pageable pageable);

    @Query("select r.overallRating, count(r) from CoachReview r "
            + "where r.coach.id = :coachId and r.status = com.calisthenics.backend.review.entity.CoachReviewStatus.VISIBLE "
            + "group by r.overallRating")
    List<Object[]> countByRating(@Param("coachId") Long coachId);

    @Query("select avg(r.overallRating), avg(r.coachingQuality), avg(r.communication), avg(r.planEffectiveness), "
            + "avg(r.supportResponsiveness) from CoachReview r "
            + "where r.coach.id = :coachId and r.status = com.calisthenics.backend.review.entity.CoachReviewStatus.VISIBLE")
    List<Object[]> averages(@Param("coachId") Long coachId);
}
