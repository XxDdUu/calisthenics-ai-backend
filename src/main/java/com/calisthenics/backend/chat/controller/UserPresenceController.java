package com.calisthenics.backend.chat.controller;

import com.calisthenics.backend.chat.dto.response.UserPresenceResponse;
import com.calisthenics.backend.chat.service.UserPresenceService;
import com.calisthenics.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/presence")
@RequiredArgsConstructor
public class UserPresenceController {

    private final UserPresenceService presenceService;

    @GetMapping
    public ResponseEntity<List<UserPresenceResponse>> getConversationPresence(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(presenceService.getConversationPresence(currentUser.getId()));
    }
}
