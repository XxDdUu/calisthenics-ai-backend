package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.AuthResponse;
import com.calisthenics.backend.security.dto.GoogleAuthRequest;
import com.calisthenics.backend.security.dto.GoogleUserInfo;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        GoogleUserInfo googleUser = googleTokenVerifier.verifyToken(request.getIdToken());
        if (googleUser == null || googleUser.getSub() == null || googleUser.getEmail() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Google ID token");
        }

        String googleId = googleUser.getSub();
        String email = googleUser.getEmail().toLowerCase().trim();

        // 1. Check if user already exists with this googleId
        Optional<User> userByGoogleId = userRepository.findByGoogleId(googleId);
        User user;

        if (userByGoogleId.isPresent()) {
            user = userByGoogleId.get();
            log.info("Google user authenticated: id={}, email={}", user.getId(), user.getEmail());
        } else {
            // 2. Check if account already exists with matching email
            Optional<User> userByEmail = userRepository.findByEmailIgnoreCase(email);
            if (userByEmail.isPresent()) {
                User existingUser = userByEmail.get();

                // Do not automatically link if account was registered with password
                if (existingUser.getPasswordHash() != null && !existingUser.getPasswordHash().isBlank()) {
                    log.warn("Google authentication rejected: email {} already registered with password", email);
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "An account with this email already exists with password authentication. Please sign in using your password."
                    );
                }

                if (existingUser.getGoogleId() != null && !existingUser.getGoogleId().equals(googleId)) {
                    log.warn("Google authentication rejected: email {} already linked to another Google account", email);
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "An account with this email is already linked to another Google account."
                    );
                }

                // If existing account without password (e.g. Firebase), link googleId
                existingUser.setGoogleId(googleId);
                if (existingUser.getAvatarUrl() == null && googleUser.getPicture() != null) {
                    existingUser.setAvatarUrl(googleUser.getPicture());
                }
                user = userRepository.save(existingUser);
                log.info("Linked Google identity to existing account: id={}, email={}", user.getId(), user.getEmail());
            } else {
                // 3. Provision new user with default ROLE_USER
                user = User.builder()
                        .email(email)
                        .googleId(googleId)
                        .fullName(googleUser.getName() != null && !googleUser.getName().isBlank()
                                ? googleUser.getName().trim()
                                : "Calisthenics Athlete")
                        .avatarUrl(googleUser.getPicture())
                        .role(Role.ROLE_USER)
                        .experienceLevel("INTERMEDIATE")
                        .build();
                user = userRepository.save(user);
                log.info("Provisioned new user via Google Sign-In: id={}, email={}, googleId={}", user.getId(), email, googleId);
            }
        }

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateAccessTokenFromUserPrincipal(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}

