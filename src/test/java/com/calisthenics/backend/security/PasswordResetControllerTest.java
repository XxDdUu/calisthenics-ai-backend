package com.calisthenics.backend.security;

import com.calisthenics.backend.security.passwordreset.PasswordResetEmailService;
import com.calisthenics.backend.security.passwordreset.PasswordResetOtp;
import com.calisthenics.backend.security.passwordreset.PasswordResetOtpRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetControllerTest {

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetOtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private PasswordResetEmailService emailService;

    @MockBean
    private com.calisthenics.backend.security.registration.RegistrationEmailService registrationEmailService;

    @BeforeEach
    void clearMailCalls() {
        clearInvocations(emailService);
    }

    @Test
    void registeredEmailReceivesOtpThatIsStoredOnlyAsHash() throws Exception {
        String email = "reset-registered@example.com";
        long userId = register(email);

        requestOtp(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(genericMessage()));

        String otp = capturedOtp(email);
        PasswordResetOtp saved = latestOtp(userId);
        assertTrue(otp.matches("\\d{6}"));
        assertNotEquals(otp, saved.getOtpHash());
        assertTrue(saved.getOtpHash().matches("[0-9a-f]{64}"));
        assertTrue(saved.getExpiresAt().isAfter(saved.getCreatedAt()));
    }

    @Test
    void unregisteredEmailGetsTheSameGenericResponseWithoutSendingMail() throws Exception {
        requestOtp("reset-missing@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(genericMessage()));
        verify(emailService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void emailDeliveryFailureDoesNotRevealWhetherTheAddressExists() throws Exception {
        String email = "reset-mail-failure@example.com";
        long userId = register(email);
        doThrow(new MailSendException("sensitive SMTP response"))
                .when(emailService).sendOtp(eq(email), org.mockito.ArgumentMatchers.anyString());

        requestOtp(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(genericMessage()));
        assertTrue(latestOtp(userId).getConsumedAt() != null);
    }

    @Test
    void issuingAnotherOtpInvalidatesThePreviousOtp() throws Exception {
        String email = "reset-new-code@example.com";
        long userId = register(email);
        requestOtp(email).andExpect(status().isOk());
        String previousOtp = capturedOtp(email);

        clearInvocations(emailService);
        requestOtp(email).andExpect(status().isOk());
        String currentOtp = capturedOtp(email);
        if (previousOtp.equals(currentOtp)) {
            clearInvocations(emailService);
            requestOtp(email).andExpect(status().isOk());
            currentOtp = capturedOtp(email);
        }
        assertTrue(otpRepository.findAll().stream()
                .filter(otp -> otp.getUserId().equals(userId))
                .anyMatch(otp -> otp.getConsumedAt() != null));

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "otp", previousOtp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "otp", currentOtp)))
                .andExpect(status().isOk());
    }

    @Test
    void limitsOtpRequestsPerEmailToThreePerWindow() throws Exception {
        String email = "reset-rate-limit@example.com";
        register(email);
        requestOtp(email).andExpect(status().isOk());
        requestOtp(email).andExpect(status().isOk());
        requestOtp(email).andExpect(status().isOk());

        requestOtp(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void verifiesValidOtpAndIssuesResetAuthorization() throws Exception {
        ResetCredentials reset = prepareReset("reset-valid@example.com");

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", reset.email(), "otp", reset.otp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(300));
    }

    @Test
    void rejectsIncorrectOtpAndIncrementsFailedAttempts() throws Exception {
        ResetCredentials reset = prepareReset("reset-wrong@example.com");

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", reset.email(), "otp", "999999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));

        assertTrue(latestOtp(reset.userId()).getAttempts() > 0);
    }

    @Test
    void rejectsExpiredOtp() throws Exception {
        ResetCredentials reset = prepareReset("reset-expired@example.com");
        PasswordResetOtp otp = latestOtp(reset.userId());
        otp.setExpiresAt(ZonedDateTime.now().minusMinutes(1));
        otpRepository.save(otp);

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", reset.email(), "otp", reset.otp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void rejectsOtpAfterFiveFailedAttempts() throws Exception {
        ResetCredentials reset = prepareReset("reset-attempts@example.com");

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", reset.email(), "otp", "999999")))
                    .andExpect(status().is(attempt == 5 ? 429 : 400));
        }

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", reset.email(), "otp", reset.otp())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void verifiedOtpCannotBeReused() throws Exception {
        ResetCredentials reset = prepareReset("reset-otp-reuse@example.com");
        verifyOtp(reset);

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", reset.email(), "otp", reset.otp())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
    }

    @Test
    void validResetChangesHashedPasswordAndRequiresLoginAgain() throws Exception {
        ResetCredentials reset = prepareReset("reset-password@example.com");
        String resetToken = verifyOtp(reset);
        assertNotEquals(resetToken, latestOtp(reset.userId()).getResetTokenHash());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(resetToken, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        User user = userRepository.findByEmail(reset.email()).orElseThrow();
        assertNotEquals(OLD_PASSWORD, user.getPasswordHash());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash()));
        login(reset.email(), OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(reset.email(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredResetToken() throws Exception {
        ResetCredentials reset = prepareReset("reset-token-expired@example.com");
        String token = verifyOtp(reset);
        PasswordResetOtp otp = latestOtp(reset.userId());
        otp.setResetExpiresAt(ZonedDateTime.now().minusMinutes(1));
        otpRepository.save(otp);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetJson(token, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
    }

    @Test
    void rejectsReusedResetToken() throws Exception {
        ResetCredentials reset = prepareReset("reset-token-reuse@example.com");
        String token = verifyOtp(reset);
        resetPassword(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        resetPassword(token, "OtherPassword789!", "OtherPassword789!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void rejectsMismatchedAndWeakPasswords() throws Exception {
        ResetCredentials mismatch = prepareReset("reset-mismatch@example.com");
        String mismatchToken = verifyOtp(mismatch);
        resetPassword(mismatchToken, NEW_PASSWORD, "DifferentPassword456!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        ResetCredentials weak = prepareReset("reset-weak@example.com");
        String weakToken = verifyOtp(weak);
        resetPassword(weakToken, "weakpass", "weakpass")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void rateLimitsRepeatedPasswordResetAttempts() throws Exception {
        ResetCredentials reset = prepareReset("reset-password-rate-limit@example.com");
        String token = verifyOtp(reset);

        for (int attempt = 0; attempt < 5; attempt++) {
            resetPassword(token, NEW_PASSWORD, "DifferentPassword456!")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        }
        resetPassword(token, NEW_PASSWORD, "DifferentPassword456!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private long register(String email) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .fullName("Reset Test")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build();
        return userRepository.save(user).getId();
    }

    private ResetCredentials prepareReset(String email) throws Exception {
        long userId = register(email);
        requestOtp(email).andExpect(status().isOk());
        return new ResetCredentials(email, capturedOtp(email), userId);
    }

    private String verifyOtp(ResetCredentials reset) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", reset.email(), "otp", reset.otp())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("resetToken").asText();
    }

    private String capturedOtp(String email) {
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtp(eq(email), otp.capture());
        return otp.getValue();
    }

    private PasswordResetOtp latestOtp(long userId) {
        return otpRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions requestOtp(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("email", email, "password", password)));
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(
            String token,
            String newPassword,
            String confirmPassword
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resetJson(token, newPassword, confirmPassword)));
    }

    private String json(String... keyValues) throws Exception {
        var object = objectMapper.createObjectNode();
        for (int index = 0; index < keyValues.length; index += 2) {
            object.put(keyValues[index], keyValues[index + 1]);
        }
        return objectMapper.writeValueAsString(object);
    }

    private String resetJson(String token, String newPassword, String confirmPassword) throws Exception {
        return json("resetToken", token, "newPassword", newPassword, "confirmPassword", confirmPassword);
    }

    private String genericMessage() {
        return "Nếu email tồn tại trong hệ thống, mã OTP sẽ được gửi đến email đó.";
    }

    private record ResetCredentials(String email, String otp, long userId) {
    }
}
