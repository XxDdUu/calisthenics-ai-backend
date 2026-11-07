package com.calisthenics.backend.coachclient.controller;

import com.calisthenics.backend.coachclient.dto.response.CoachClientRelationshipResponse;
import com.calisthenics.backend.coachclient.service.CoachClientRelationshipService;
import com.calisthenics.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coach-relationships")
@RequiredArgsConstructor
public class CoachRelationshipLegacyController {

    private final CoachClientRelationshipService relationshipService;

    @DeleteMapping("/{id}")
    public ResponseEntity<CoachClientRelationshipResponse> terminate(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(relationshipService.terminate(currentUser.getId(), id));
    }
}
