package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminBootstrapRequest;
import com.calisthenics.backend.admin.dto.AdminBootstrapResponse;
import com.calisthenics.backend.security.passwordreset.PasswordResetRateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminBootstrapController {

    private static final String BOOTSTRAP_SECRET_HEADER = "X-Admin-Bootstrap-Secret";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final AdminBootstrapService adminBootstrapService;
    private final PasswordResetRateLimiter rateLimiter;

    @PostMapping("/bootstrap")
    public ResponseEntity<AdminBootstrapResponse> bootstrap(
            @RequestHeader(value = BOOTSTRAP_SECRET_HEADER, required = false) String secret,
            @Valid @RequestBody AdminBootstrapRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimiter.consume(
                "admin-bootstrap:" + httpRequest.getRemoteAddr(),
                MAX_ATTEMPTS,
                RATE_LIMIT_WINDOW
        );
        return ResponseEntity.ok(adminBootstrapService.bootstrap(secret, request));
    }
}
