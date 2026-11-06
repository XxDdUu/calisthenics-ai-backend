package com.calisthenics.backend.security.registration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {

    Optional<RegistrationOtp> findFirstByEmailIgnoreCaseAndConsumedAtIsNullOrderByCreatedAtDesc(String email);

    @Modifying
    @Query("""
            update RegistrationOtp otp
            set otp.consumedAt = :consumedAt
            where lower(otp.email) = lower(:email) and otp.consumedAt is null
            """)
    int invalidateUnconsumedForEmail(
            @Param("email") String email,
            @Param("consumedAt") ZonedDateTime consumedAt
    );
}

