package com.calisthenics.backend.security.passwordreset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PasswordResetOtp> findByResetTokenHashAndConsumedAtIsNull(String resetTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetOtp otp
            set otp.consumedAt = :consumedAt
            where otp.userId = :userId and otp.consumedAt is null
            """)
    int invalidateUnconsumedForUser(
            @Param("userId") Long userId,
            @Param("consumedAt") ZonedDateTime consumedAt
    );
}
