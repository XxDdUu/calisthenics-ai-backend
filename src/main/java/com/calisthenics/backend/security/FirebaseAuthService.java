package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.AuthResponse;
import com.calisthenics.backend.security.dto.FirebaseLoginRequest;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseAuthService {

    private final FirebaseTokenVerifier tokenVerifier;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse authenticateWithFirebase(FirebaseLoginRequest request) {
        FirebaseToken decodedToken = tokenVerifier.verifyToken(request.getIdToken());
        if (decodedToken == null) {
            throw new IllegalArgumentException("Invalid or expired Firebase ID Token");
        }

        String firebaseUid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String name = decodedToken.getName() != null ? decodedToken.getName() : "Calisthenics Athlete";
        String picture = decodedToken.getPicture();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Firebase user missing email address");
        }

        Optional<User> userOpt = userRepository.findByFirebaseUid(firebaseUid);
        User user;

        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            // Check if user exists with matching email
            Optional<User> emailUserOpt = userRepository.findByEmail(email);
            if (emailUserOpt.isPresent()) {
                user = emailUserOpt.get();
                user.setFirebaseUid(firebaseUid);
                if (user.getAvatarUrl() == null && picture != null) {
                    user.setAvatarUrl(picture);
                }
                user = userRepository.save(user);
            } else {
                // Provision new user
                user = User.builder()
                        .email(email)
                        .firebaseUid(firebaseUid)
                        .fullName(name)
                        .avatarUrl(picture)
                        .role(Role.ROLE_USER)
                        .experienceLevel(request.getExperienceLevel() != null ? request.getExperienceLevel() : "INTERMEDIATE")
                        .build();
                user = userRepository.save(user);
                log.info("Provisioned new user from Firebase Auth: email={}, uid={}", email, firebaseUid);
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
                .gender(user.getGender())
                .onboardingStatus(user.getOnboardingStatus() != null ? user.getOnboardingStatus() : com.calisthenics.backend.user.OnboardingStatus.NOT_SHOWN)
                .build();
    }
}
