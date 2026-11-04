package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminBootstrapRequest;
import com.calisthenics.backend.admin.dto.AdminBootstrapResponse;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final String configuredSecret;

    public AdminBootstrapService(
            UserRepository userRepository,
            @Value("${app.admin.bootstrap-secret:}") String configuredSecret
    ) {
        this.userRepository = userRepository;
        this.configuredSecret = configuredSecret;
    }

    @Transactional
    public AdminBootstrapResponse bootstrap(String providedSecret, AdminBootstrapRequest request) {
        if (!StringUtils.hasText(configuredSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin bootstrap is not configured");
        }
        if (!StringUtils.hasText(providedSecret)
                || !MessageDigest.isEqual(
                        configuredSecret.getBytes(StandardCharsets.UTF_8),
                        providedSecret.getBytes(StandardCharsets.UTF_8)
                )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bootstrap credentials");
        }

        userRepository.lockAllForAdminBootstrap();
        User user = userRepository.findByEmailIgnoreCaseForUpdate(request.email().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (userRepository.countByRole(Role.ROLE_ADMIN) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin bootstrap has already been completed");
        }

        user.setRole(Role.ROLE_ADMIN);
        User promoted = userRepository.save(user);
        return new AdminBootstrapResponse(
                promoted.getId(),
                promoted.getEmail(),
                promoted.getRole(),
                "Administrator role assigned. Sign in again to receive an updated token."
        );
    }
}
