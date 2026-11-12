package com.calisthenics.backend.adminai.repository;

import com.calisthenics.backend.adminai.entity.AdminAiRecommendation;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAiRecommendationRepository extends JpaRepository<AdminAiRecommendation, Long> {

    List<AdminAiRecommendation> findAllByOrderByCreatedAtDesc();

    List<AdminAiRecommendation> findByRecommendationTypeOrderByCreatedAtDesc(AdminRecommendationType recommendationType);

    List<AdminAiRecommendation> findByStatusOrderByCreatedAtDesc(AdminRecommendationStatus status);

    List<AdminAiRecommendation> findByRecommendationTypeAndStatusOrderByCreatedAtDesc(
            AdminRecommendationType recommendationType,
            AdminRecommendationStatus status
    );

    boolean existsByRecommendationTypeAndTitleIgnoreCaseAndStatusIn(
            AdminRecommendationType recommendationType,
            String title,
            List<AdminRecommendationStatus> statuses
    );
}
