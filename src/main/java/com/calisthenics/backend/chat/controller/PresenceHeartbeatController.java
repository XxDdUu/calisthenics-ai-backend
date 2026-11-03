package com.calisthenics.backend.chat.controller;

import com.calisthenics.backend.chat.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class PresenceHeartbeatController {

    private final UserPresenceService presenceService;

    @MessageMapping("/presence.heartbeat")
    public void heartbeat(
            Principal principal,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalStateException("Authenticated websocket principal is required");
        }
        Long userId;
        try {
            userId = Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Authenticated websocket principal is invalid");
        }
        presenceService.heartbeat(sessionId, userId);
    }
}
