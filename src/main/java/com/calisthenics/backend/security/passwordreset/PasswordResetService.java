package com.calisthenics.backend.security.passwordreset;

import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class PasswordResetService {

    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final String GENERIC_MESSAGE =
            "Nếu email tồn tại trong hệ thống, mã OTP sẽ được gửi đến email đó.";

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordResetEmailService emailService;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] otpHmacSecret;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetOtpRepository otpRepository,
            PasswordResetEmailService emailService,
            PasswordResetRateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.password-reset.otp-hmac-secret}") String otpHmacSecret
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.otpHmacSecret = otpHmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public String requestOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        rateLimiter.consume("forgot:" + sha256(normalizedEmail), 3, REQUEST_WINDOW);
        Optional<User> candidate = userRepository.findByEmailIgnoreCaseForUpdate(normalizedEmail);
        if (candidate.isEmpty()) {
            return GENERIC_MESSAGE;
        }

        User user = candidate.get();
        ZonedDateTime now = ZonedDateTime.now(clock);
        String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        otpRepository.invalidateUnconsumedForUser(user.getId(), now);

        PasswordResetOtp resetOtp = new PasswordResetOtp();
        resetOtp.setUserId(user.getId());
        resetOtp.setOtpHash(hmac(otp));
        resetOtp.setCreatedAt(now);
        resetOtp.setExpiresAt(now.plus(OTP_LIFETIME));
        otpRepository.save(resetOtp);

        try {
            emailService.sendOtp(user.getEmail(), otp);
        } catch (MailException ex) {
            resetOtp.setConsumedAt(now);
            otpRepository.save(resetOtp);
            log.warn("Password reset email delivery failed");
        }
        return GENERIC_MESSAGE;
    }

    @Transactional(noRollbackFor = PasswordResetException.class)
    public VerificationResult verifyOtp(String email, String otp) {
        User user = userRepository.findByEmailIgnoreCaseForUpdate(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> invalidOtp());
        PasswordResetOtp resetOtp = otpRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> invalidOtp());
        ZonedDateTime now = ZonedDateTime.now(clock);

        if (resetOtp.getConsumedAt() != null || resetOtp.getVerifiedAt() != null) {
            throw invalidOtp();
        }
        if (!resetOtp.getExpiresAt().isAfter(now)) {
            throw new PasswordResetException("OTP_EXPIRED", "Mã OTP đã hết hạn.", HttpStatus.BAD_REQUEST);
        }
        if (resetOtp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw attemptsExceeded();
        }
        if (!constantTimeEquals(resetOtp.getOtpHash(), hmac(otp))) {
            resetOtp.setAttempts(resetOtp.getAttempts() + 1);
            otpRepository.save(resetOtp);
            if (resetOtp.getAttempts() >= MAX_OTP_ATTEMPTS) {
                throw attemptsExceeded();
            }
            throw invalidOtp();
        }

        String resetToken = randomToken();
        resetOtp.setVerifiedAt(now);
        resetOtp.setResetTokenHash(sha256(resetToken));
        resetOtp.setResetExpiresAt(now.plus(RESET_TOKEN_LIFETIME));
        otpRepository.save(resetOtp);
        return new VerificationResult(resetToken, (int) RESET_TOKEN_LIFETIME.toSeconds());
    }

    @Transactional(noRollbackFor = PasswordResetException.class)
    public void resetPassword(String resetToken, String newPassword, String confirmPassword) {
        String tokenHash = sha256(resetToken);
        PasswordResetOtp tokenCandidate = otpRepository.findByResetTokenHashAndConsumedAtIsNull(tokenHash)
                .orElseThrow(this::invalidResetToken);
        rateLimiter.consume("reset:" + tokenHash, 5, REQUEST_WINDOW);
        User user = userRepository.findByIdForUpdate(tokenCandidate.getUserId())
                .orElseThrow(this::invalidResetToken);
        PasswordResetOtp resetOtp = otpRepository.findByResetTokenHashAndConsumedAtIsNull(tokenHash)
                .filter(otp -> otp.getUserId().equals(user.getId()))
                .orElseThrow(this::invalidResetToken);
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (resetOtp.getVerifiedAt() == null || resetOtp.getResetExpiresAt() == null) {
            throw invalidResetToken();
        }
        if (!resetOtp.getResetExpiresAt().isAfter(now)) {
            throw new PasswordResetException(
                    "RESET_TOKEN_EXPIRED", "Mã xác thực đặt lại mật khẩu đã hết hạn.", HttpStatus.BAD_REQUEST
            );
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordResetException(
                    "PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp.", HttpStatus.BAD_REQUEST
            );
        }
        if (!isStrongPassword(newPassword)) {
            throw new PasswordResetException(
                    "WEAK_PASSWORD",
                    "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.",
                    HttpStatus.BAD_REQUEST
            );
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetOtp.setConsumedAt(now);
        otpRepository.save(resetOtp);
        otpRepository.invalidateUnconsumedForUser(user.getId(), now);
    }

    private boolean isStrongPassword(String password) {
        return password != null
                && password.length() >= 8
                && password.length() <= 72
                && password.matches("(?s).*\\p{Lu}.*")
                && password.matches("(?s).*\\p{Ll}.*")
                && password.matches("(?s).*\\d.*")
                && password.matches("(?s).*[^\\p{L}\\d].*");
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpHmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash password reset OTP");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash password reset token");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private PasswordResetException invalidOtp() {
        return new PasswordResetException("INVALID_OTP", "Mã OTP không hợp lệ.", HttpStatus.BAD_REQUEST);
    }

    private PasswordResetException attemptsExceeded() {
        return new PasswordResetException(
                "OTP_ATTEMPTS_EXCEEDED", "Bạn đã nhập sai OTP quá nhiều lần.", HttpStatus.TOO_MANY_REQUESTS
        );
    }

    private PasswordResetException invalidResetToken() {
        return new PasswordResetException(
                "RESET_TOKEN_INVALID", "Mã xác thực đặt lại mật khẩu không hợp lệ.", HttpStatus.BAD_REQUEST
        );
    }

    public record VerificationResult(String resetToken, int expiresIn) {
    }
}
