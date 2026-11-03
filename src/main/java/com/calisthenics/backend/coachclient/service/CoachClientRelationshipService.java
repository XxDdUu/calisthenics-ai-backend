package com.calisthenics.backend.coachclient.service;

import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateClientCoachRequest;
import com.calisthenics.backend.coachclient.dto.response.CoachClientRelationshipResponse;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;

import java.util.List;

public interface CoachClientRelationshipService {

    CoachClientRelationshipResponse request(Long coachId, CreateCoachClientRequest request);

    CoachClientRelationshipResponse requestAsClient(Long clientId, CreateClientCoachRequest request);

    List<CoachClientRelationshipResponse> listForUser(Long userId);

    CoachClientRelationshipResponse transition(
            Long userId, Long relationshipId, CoachClientRelationshipStatus targetStatus);
}
