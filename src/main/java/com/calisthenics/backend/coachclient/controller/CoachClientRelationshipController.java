package com.calisthenics.backend.coachclient.controller;

import com.calisthenics.backend.coachclient.dto.request.CreateCoachClientRequest;
import com.calisthenics.backend.coachclient.dto.request.CreateClientCoachRequest;
import com.calisthenics.backend.coachclient.dto.response.CoachClientRelationshipResponse;
import com.calisthenics.backend.coachclient.service.CoachClientRelationshipService;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coach-client-relationships")
@RequiredArgsConstructor
public class CoachClientRelationshipController {

    private final CoachClientRelationshipService relationshipService;

    @PostMapping
    public ResponseEntity<CoachClientRelationshipResponse> request(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateCoachClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(relationshipService.request(currentUser.getId(), request));
    }

    @PostMapping("/requests")
    public ResponseEntity<CoachClientRelationshipResponse> requestAsClient(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateClientCoachRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(relationshipService.requestAsClient(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<CoachClientRelationshipResponse>> list(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(relationshipService.listForUser(currentUser.getId()));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<CoachClientRelationshipResponse> accept(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(relationshipService.transition(
                currentUser.getId(), id, CoachClientRelationshipStatus.ACTIVE));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<CoachClientRelationshipResponse> reject(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(relationshipService.transition(
                currentUser.getId(), id, CoachClientRelationshipStatus.REJECTED));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CoachClientRelationshipResponse> cancel(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(relationshipService.transition(
                currentUser.getId(), id, CoachClientRelationshipStatus.CANCELLED));
    }
}
