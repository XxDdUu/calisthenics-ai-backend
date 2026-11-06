package com.calisthenics.backend.security.registration;

import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.dto.RegisterInitiationResponse;
import com.calisthenics.backend.security.dto.RegisterRequest;
import com.calisthenics.backend.security.dto.VerifyRegistrationOtpRequest;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RegistrationOtpServiceTest {

    private UserRepository userRepository;
    private RegistrationOtpRepository otpRepository;
    private RegistrationEmailService emailService;
    private RegistrationRateLimiter rateLimiter;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private Clock clock;
    private RegistrationOtpService service;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        otpRepository = mock(RegistrationOtpRepository.class);
        emailService = mock(RegistrationEmailService.class);
        rateLimiter = mock(RegistrationRateLimiter.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        clock = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneId.of("UTC"));

        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        when(tokenProvider.generateAccessTokenFromUserPrincipal(any())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");

        service = new RegistrationOtpService(
                userRepository,
                otpRepository,
                emailService,
                rateLimiter,
                passwordEncoder,
                tokenProvider,
                clock,
                secret
        );
    }

    @Test
    void initiateRegistration_Success() {
        when(userRepository.existsByEmail("athlete@example.com")).thenReturn(false);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("athlete@example.com");
        request.setPassword("StrongPass123!");
        request.setFullName("John Doe");

        RegisterInitiationResponse response = service.initiateRegistration(request);

        assertEquals(200, response.status());
        assertEquals("athlete@example.com", response.email());
        assertEquals(300, response.expiresIn());
        assertEquals(30, response.resendCooldown());

        ArgumentCaptor<RegistrationOtp> otpCaptor = ArgumentCaptor.forClass(RegistrationOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        RegistrationOtp savedOtp = otpCaptor.getValue();
        assertEquals("athlete@example.com", savedOtp.getEmail());
        assertEquals("John Doe", savedOtp.getFullName());
        assertEquals("hashed-pwd", savedOtp.getPasswordHash());
        assertNotNull(savedOtp.getOtpHash());

        ArgumentCaptor<String> sentOtpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRegistrationOtp(eq("athlete@example.com"), sentOtpCaptor.capture());
        assertEquals(6, sentOtpCaptor.getValue().length());
    }

    @Test
    void initiateRegistration_RejectsAlreadyRegisteredEmail() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("StrongPass123!");
        request.setFullName("Existing User");

        RegistrationException ex = assertThrows(RegistrationException.class, () -> service.initiateRegistration(request));
        assertEquals("EMAIL_ALREADY_EXISTS", ex.getCode());
        verify(emailService, never()).sendRegistrationOtp(anyString(), anyString());
    }

    @Test
    void resendRegistrationOtp_EnforcesCooldown() {
        when(userRepository.existsByEmail("athlete@example.com")).thenReturn(false);

        RegistrationOtp activeOtp = new RegistrationOtp();
        activeOtp.setEmail("athlete@example.com");
        activeOtp.setFullName("John Doe");
        activeOtp.setPasswordHash("hashed-pwd");
        activeOtp.setCreatedAt(ZonedDateTime.now(clock).minusSeconds(10)); // only 10s ago (< 30s cooldown)

        when(otpRepository.findFirstByEmailIgnoreCaseAndConsumedAtIsNullOrderByCreatedAtDesc("athlete@example.com"))
                .thenReturn(Optional.of(activeOtp));

        RegistrationException ex = assertThrows(RegistrationException.class, () -> service.resendRegistrationOtp("athlete@example.com"));
        assertEquals("COOLDOWN_ACTIVE", ex.getCode());
    }

    @Test
    void verifyOtpAndCompleteRegistration_CreatesUserAndReturnsTokens() {
        when(userRepository.existsByEmail("athlete@example.com")).thenReturn(false);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("athlete@example.com");
        request.setPassword("StrongPass123!");
        request.setFullName("John Doe");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        service.initiateRegistration(request);
        verify(emailService).sendRegistrationOtp(eq("athlete@example.com"), otpCaptor.capture());
        String sentOtp = otpCaptor.getValue();

        ArgumentCaptor<RegistrationOtp> savedOtpCaptor = ArgumentCaptor.forClass(RegistrationOtp.class);
        verify(otpRepository).save(savedOtpCaptor.capture());
        RegistrationOtp storedOtp = savedOtpCaptor.getValue();

        when(otpRepository.findFirstByEmailIgnoreCaseAndConsumedAtIsNullOrderByCreatedAtDesc("athlete@example.com"))
                .thenReturn(Optional.of(storedOtp));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            return u;
        });

        VerifyRegistrationOtpRequest verifyRequest = new VerifyRegistrationOtpRequest();
        verifyRequest.setEmail("athlete@example.com");
        verifyRequest.setOtp(sentOtp);

        var authResponse = service.verifyOtpAndCompleteRegistration(verifyRequest);

        assertNotNull(authResponse);
        assertEquals("access-token", authResponse.getAccessToken());
        assertEquals("refresh-token", authResponse.getRefreshToken());
        assertEquals("athlete@example.com", authResponse.getEmail());
        assertEquals(100L, authResponse.getUserId());
        assertNotNull(storedOtp.getConsumedAt());
    }
}
