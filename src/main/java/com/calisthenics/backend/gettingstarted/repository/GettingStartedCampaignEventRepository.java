package com.calisthenics.backend.gettingstarted.repository;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedCampaignEvent;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedCampaignEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GettingStartedCampaignEventRepository extends JpaRepository<GettingStartedCampaignEvent, Long> {

    long countByProgramIdAndEventType(Long programId, GettingStartedCampaignEventType eventType);

    boolean existsByProgramIdAndProgramVersionAndUserIdAndEventType(
            Long programId,
            Integer programVersion,
            Long userId,
            GettingStartedCampaignEventType eventType
    );
}
