package com.calisthenics.backend.security;

import com.calisthenics.backend.security.dto.AuthResponse;
import com.calisthenics.backend.security.dto.FirebaseLoginRequest;
import com.calisthenics.backend.security.dto.FirebaseUserInfo;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
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
        FirebaseUserInfo firebaseUser = tokenVerifier.verifyToken(request != null ? request.getIdToken() : null);

        String firebaseUid = firebaseUser.getUid();
        String email = firebaseUser.getEmail().trim().toLowerCase(Locale.ROOT);
        String name = StringUtils.hasText(firebaseUser.getName())
                ? firebaseUser.getName().trim()
                : "Calisthenics Athlete";
        String picture = firebaseUser.getPicture();
        String tokenGoogleId = firebaseUser.getGoogleId();

        User user = userRepository.findByFirebaseUid(firebaseUid)
                .map(existing -> updateExistingFirebaseUser(existing, picture, tokenGoogleId))
                .orElseGet(() -> linkOrProvisionByEmail(firebaseUid, email, name, picture, tokenGoogleId,
                        request != null ? request.getExperienceLevel() : null));

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
                .onboardingStatus(user.getOnboardingStatus() != null
                        ? user.getOnboardingStatus()
                        : OnboardingStatus.NOT_SHOWN)
                .build();
    }

    private User updateExistingFirebaseUser(User existing, String picture, String tokenGoogleId) {
        boolean modified = false;
        if (!StringUtils.hasText(existing.getAvatarUrl()) && StringUtils.hasText(picture)) {
            existing.setAvatarUrl(picture);
            modified = true;
        }
        if (!StringUtils.hasText(existing.getGoogleId()) && StringUtils.hasText(tokenGoogleId)
                && !userRepository.existsByGoogleId(tokenGoogleId)) {
            existing.setGoogleId(tokenGoogleId);
            modified = true;
        }
        if (modified) {
            return userRepository.save(existing);
        }
        log.info("Firebase user authenticated: id={}, email={}", existing.getId(), existing.getEmail());
        return existing;
    }

    private User linkOrProvisionByEmail(
            String firebaseUid,
            String email,
            String name,
            String picture,
            String tokenGoogleId,
            String requestedExperienceLevel
    ) {
        Optional<User> emailUserOpt = userRepository.findByEmailIgnoreCase(email);
        if (emailUserOpt.isPresent()) {
            User existingUser = emailUserOpt.get();

            if (StringUtils.hasText(existingUser.getPasswordHash())) {
                log.warn("Firebase authentication rejected: email {} already registered with password", email);
                throw new FirebaseAuthenticationException(
                        "ACCOUNT_EXISTS_WITH_PASSWORD",
                        "An account with this email already exists with password authentication. Please sign in using your password.",
                        HttpStatus.CONFLICT
                );
            }

            if (StringUtils.hasText(existingUser.getFirebaseUid())
                    && !existingUser.getFirebaseUid().equals(firebaseUid)) {
                log.warn("Firebase authentication rejected: email {} already linked to another Firebase UID", email);
                throw new FirebaseAuthenticationException(
                        "FIREBASE_ACCOUNT_CONFLICT",
                        "An account with this email is already linked to another Firebase account.",
                        HttpStatus.CONFLICT
                );
            }

            if (StringUtils.hasText(tokenGoogleId)
                    && StringUtils.hasText(existingUser.getGoogleId())
                    && !existingUser.getGoogleId().equals(tokenGoogleId)) {
                log.warn("Firebase authentication rejected: email {} already linked to another Google identity", email);
                throw new FirebaseAuthenticationException(
                        "GOOGLE_ACCOUNT_CONFLICT",
                        "An account with this email is already linked to another Google account.",
                        HttpStatus.CONFLICT
                );
            }

            existingUser.setFirebaseUid(firebaseUid);
            if (!StringUtils.hasText(existingUser.getGoogleId()) && StringUtils.hasText(tokenGoogleId)
                    && !userRepository.existsByGoogleId(tokenGoogleId)) {
                existingUser.setGoogleId(tokenGoogleId);
            }
            if (!StringUtils.hasText(existingUser.getAvatarUrl()) && StringUtils.hasText(picture)) {
                existingUser.setAvatarUrl(picture);
            }
            User saved = userRepository.save(existingUser);
            log.info("Linked Firebase identity to existing passwordless account: id={}, email={}",
                    saved.getId(), saved.getEmail());
            return saved;
        }

        if (StringUtils.hasText(tokenGoogleId)) {
            Optional<User> googleUserOpt = userRepository.findByGoogleId(tokenGoogleId);
            if (googleUserOpt.isPresent()) {
                User existingGoogleUser = googleUserOpt.get();
                if (StringUtils.hasText(existingGoogleUser.getPasswordHash())) {
                    throw new FirebaseAuthenticationException(
                            "ACCOUNT_EXISTS_WITH_PASSWORD",
                            "An account with this email already exists with password authentication. Please sign in using your password.",
                            HttpStatus.CONFLICT
                    );
                }
                if (StringUtils.hasText(existingGoogleUser.getFirebaseUid())
                        && !existingGoogleUser.getFirebaseUid().equals(firebaseUid)) {
                    throw new FirebaseAuthenticationException(
                            "FIREBASE_ACCOUNT_CONFLICT",
                            "This Google account is already linked to another Firebase user.",
                            HttpStatus.CONFLICT
                    );
                }
                existingGoogleUser.setFirebaseUid(firebaseUid);
                if (!StringUtils.hasText(existingGoogleUser.getAvatarUrl()) && StringUtils.hasText(picture)) {
                    existingGoogleUser.setAvatarUrl(picture);
                }
                User saved = userRepository.save(existingGoogleUser);
                log.info("Linked Firebase identity to existing Google user: id={}, email={}",
                        saved.getId(), saved.getEmail());
                return saved;
            }
        }

        String experienceLevel = StringUtils.hasText(requestedExperienceLevel)
                ? requestedExperienceLevel.trim()
                : "INTERMEDIATE";

        User newUser = User.builder()
                .email(email)
                .firebaseUid(firebaseUid)
                .googleId(StringUtils.hasText(tokenGoogleId) ? tokenGoogleId : null)
                .fullName(name)
                .avatarUrl(picture)
                .role(Role.ROLE_USER)
                .experienceLevel(experienceLevel)
                .build();

        User provisioned = userRepository.save(newUser);
        log.info("Provisioned new user from Firebase Auth: id={}, email={}, uid={}",
                provisioned.getId(), provisioned.getEmail(), firebaseUid);
        return provisioned;
    }
}
