package com.calisthenics.backend.review.repository;

import com.calisthenics.backend.review.entity.CoachReviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachReviewEventRepository extends JpaRepository<CoachReviewEvent, Long> {
    List<CoachReviewEvent> findByReviewIdOrderByCreatedAtAscIdAsc(Long reviewId);
}
