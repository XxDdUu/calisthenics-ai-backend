package com.calisthenics.backend.security.registration;

import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.security.dto.*;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Slf4j
public class RegistrationOtpService {

    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RegistrationOtpRepository otpRepository;
    private final RegistrationEmailService emailService;
    private final RegistrationRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] otpHmacSecret;

    public RegistrationOtpService(
            UserRepository userRepository,
            RegistrationOtpRepository otpRepository,
            RegistrationEmailService emailService,
            RegistrationRateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            Clock clock,
            @Value("${app.password-reset.otp-hmac-secret:${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}}") String otpHmacSecret
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.clock = clock;
        this.otpHmacSecret = otpHmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public RegisterInitiationResponse initiateRegistration(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new RegistrationException(
                    "EMAIL_ALREADY_EXISTS",
                    "Error: Email is already registered!",
                    HttpStatus.BAD_REQUEST
            );
        }

        rateLimiter.consume("register:" + sha256(normalizedEmail), 3, REQUEST_WINDOW);

        ZonedDateTime now = ZonedDateTime.now(clock);
        otpRepository.invalidateUnconsumedForEmail(normalizedEmail, now);

        String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        RegistrationOtp regOtp = new RegistrationOtp();
        regOtp.setEmail(normalizedEmail);
        regOtp.setFullName(request.getFullName().trim());
        regOtp.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        regOtp.setExperienceLevel(
                request.getExperienceLevel() != null && !request.getExperienceLevel().isBlank()
                        ? request.getExperienceLevel().trim()
                        : "INTERMEDIATE"
        );
        regOtp.setOtpHash(hmac(otp));
        regOtp.setCreatedAt(now);
        regOtp.setExpiresAt(now.plus(OTP_LIFETIME));
        otpRepository.save(regOtp);

        try {
            emailService.sendRegistrationOtp(normalizedEmail, otp);
        } catch (Exception ex) {
            regOtp.setConsumedAt(now);
            otpRepository.save(regOtp);
            throw ex;
        }

        return new RegisterInitiationResponse(
                HttpStatus.OK.value(),
                "Mã xác thực OTP đã được gửi đến email của bạn.",
                normalizedEmail,
                (int) OTP_LIFETIME.toSeconds(),
                (int) RESEND_COOLDOWN.toSeconds()
        );
    }

    @Transactional
    public RegisterInitiationResponse resendRegistrationOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new RegistrationException(
                    "EMAIL_ALREADY_EXISTS",
                    "Error: Email is already registered!",
                    HttpStatus.BAD_REQUEST
            );
        }

        RegistrationOtp previousOtp = otpRepository.findFirstByEmailIgnoreCaseAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new RegistrationException(
                        "REGISTRATION_SESSION_NOT_FOUND",
                        "Không tìm thấy phiên đăng ký. Vui lòng đăng ký lại.",
                        HttpStatus.BAD_REQUEST
                ));

        ZonedDateTime now = ZonedDateTime.now(clock);
        if (previousOtp.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new RegistrationException(
                    "COOLDOWN_ACTIVE",
                    "Vui lòng đợi trước khi yêu cầu mã OTP mới.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        rateLimiter.consume("register:" + sha256(normalizedEmail), 3, REQUEST_WINDOW);
        otpRepository.invalidateUnconsumedForEmail(normalizedEmail, now);

        String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        RegistrationOtp regOtp = new RegistrationOtp();
        regOtp.setEmail(normalizedEmail);
        regOtp.setFullName(previousOtp.getFullName());
        regOtp.setPasswordHash(previousOtp.getPasswordHash());
        regOtp.setExperienceLevel(previousOtp.getExperienceLevel());
        regOtp.setOtpHash(hmac(otp));
        regOtp.setCreatedAt(now);
        regOtp.setExpiresAt(now.plus(OTP_LIFETIME));
        otpRepository.save(regOtp);

        try {
            emailService.sendRegistrationOtp(normalizedEmail, otp);
        } catch (Exception ex) {
            regOtp.setConsumedAt(now);
            otpRepository.save(regOtp);
            throw ex;
        }

        return new RegisterInitiationResponse(
                HttpStatus.OK.value(),
                "Mã xác thực mới đã được gửi đến email của bạn.",
                normalizedEmail,
                (int) OTP_LIFETIME.toSeconds(),
                (int) RESEND_COOLDOWN.toSeconds()
        );
    }

    @Transactional(noRollbackFor = RegistrationException.class)
    public AuthResponse verifyOtpAndCompleteRegistration(VerifyRegistrationOtpRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        RegistrationOtp regOtp = otpRepository.findFirstByEmailIgnoreCaseAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> invalidOtp());

        ZonedDateTime now = ZonedDateTime.now(clock);
        if (!regOtp.getExpiresAt().isAfter(now)) {
            throw new RegistrationException("OTP_EXPIRED", "Mã OTP đã hết hạn.", HttpStatus.BAD_REQUEST);
        }
        if (regOtp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw attemptsExceeded();
        }
        if (!constantTimeEquals(regOtp.getOtpHash(), hmac(request.getOtp().trim()))) {
            regOtp.setAttempts(regOtp.getAttempts() + 1);
            otpRepository.save(regOtp);
            if (regOtp.getAttempts() >= MAX_OTP_ATTEMPTS) {
                throw attemptsExceeded();
            }
            throw invalidOtp();
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new RegistrationException(
                    "EMAIL_ALREADY_EXISTS",
                    "Error: Email is already registered!",
                    HttpStatus.BAD_REQUEST
            );
        }

        User user = User.builder()
                .email(regOtp.getEmail())
                .passwordHash(regOtp.getPasswordHash())
                .fullName(regOtp.getFullName())
                .experienceLevel(regOtp.getExperienceLevel())
                .role(Role.ROLE_USER)
                .build();

        User savedUser = userRepository.save(user);
        regOtp.setConsumedAt(now);
        otpRepository.save(regOtp);
        otpRepository.invalidateUnconsumedForEmail(normalizedEmail, now);

        UserPrincipal userPrincipal = UserPrincipal.create(savedUser);
        String accessToken = tokenProvider.generateAccessTokenFromUserPrincipal(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole().name())
                .build();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpHmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash registration OTP");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash registration rate limit key");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private RegistrationException invalidOtp() {
        return new RegistrationException("INVALID_OTP", "Mã OTP không hợp lệ.", HttpStatus.BAD_REQUEST);
    }

    private RegistrationException attemptsExceeded() {
        return new RegistrationException(
                "OTP_ATTEMPTS_EXCEEDED", "Bạn đã nhập sai OTP quá nhiều lần.", HttpStatus.TOO_MANY_REQUESTS
        );
    }
}

