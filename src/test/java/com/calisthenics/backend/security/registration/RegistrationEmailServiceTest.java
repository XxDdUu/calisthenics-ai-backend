package com.calisthenics.backend.security.registration;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationEmailServiceTest {

    private JavaMailSender mailSender;
    private MimeMessage mimeMessage;
    private static final String DEFAULT_SENDER = "noreply@calisthenics.ai";

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("1. Valid Gmail address succeeds and delivers email")
    void validGmailAddress_Success() throws Exception {
        RegistrationEmailService service = new RegistrationEmailService(mailSender, DEFAULT_SENDER);
        String recipient = "tranquangduy15122006@gmail.com";
        String otp = "123456";

        service.sendRegistrationOtp(recipient, otp);

        verify(mailSender, times(1)).send(mimeMessage);
        assertEquals("Verify Your Calisthenics AI Account", mimeMessage.getSubject());
        assertEquals("tranquangduy15122006@gmail.com", mimeMessage.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals(DEFAULT_SENDER, mimeMessage.getFrom()[0].toString());

        String content = extractText(mimeMessage.getContent());
        assertTrue(content.contains("123456"));
        assertTrue(content.contains("CALISTHENICS AI"));
        assertTrue(content.contains("5 minutes"));
    }

    @Test
    @DisplayName("2. Leading and trailing whitespace is stripped and delivery succeeds")
    void leadingTrailingWhitespace_NormalizesAndSucceeds() throws Exception {
        RegistrationEmailService service = new RegistrationEmailService(mailSender, DEFAULT_SENDER);
        String recipientWithWhitespace = "   tranquangduy15122006@gmail.com   ";
        String otp = "654321";

        service.sendRegistrationOtp(recipientWithWhitespace, otp);

        verify(mailSender, times(1)).send(mimeMessage);
        assertEquals("tranquangduy15122006@gmail.com", mimeMessage.getRecipients(Message.RecipientType.TO)[0].toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tranquangduy15122006@gmail.com\n",
            "tranquangduy15122006@gmail.com\r\n",
            "tran\nquang@gmail.com",
            "user@gmail.com\rBcc:attacker@example.com",
            "user\u0000@example.com",
            "user\u0007@example.com"
    })
    @DisplayName("3. Newline or hidden control characters are rejected with EmailAddressException")
    void newlineOrControlCharacters_ThrowsEmailAddressException(String invalidRecipient) {
        RegistrationEmailService service = new RegistrationEmailService(mailSender, DEFAULT_SENDER);

        EmailAddressException ex = assertThrows(
                EmailAddressException.class,
                () -> service.sendRegistrationOtp(invalidRecipient, "112233")
        );

        assertEquals(AddressField.TO, ex.getField());
        assertNotNull(ex.getSanitizedAddress());
        assertFalse(ex.getSanitizedAddress().contains("\n"));
        assertFalse(ex.getSanitizedAddress().contains("\r"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "invalid-sender",
            "missing-domain@",
            "@missing-local.com",
            "sender@example.com\n",
            "sender name <missing-at>"
    })
    @DisplayName("4. Invalid or unconfigured sender address is rejected with EmailAddressException for FROM")
    void invalidSenderAddress_ThrowsEmailAddressException(String invalidSender) {
        RegistrationEmailService service = new RegistrationEmailService(mailSender, invalidSender);

        EmailAddressException ex = assertThrows(
                EmailAddressException.class,
                () -> service.sendRegistrationOtp("valid@example.com", "123456")
        );

        assertEquals(AddressField.FROM, ex.getField());
        assertNotNull(ex.getSanitizedAddress());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "   \n  "})
    @DisplayName("5. Missing recipient (null or empty) is rejected with EmailAddressException for TO")
    void missingRecipient_ThrowsEmailAddressException(String missingRecipient) {
        RegistrationEmailService service = new RegistrationEmailService(mailSender, DEFAULT_SENDER);

        EmailAddressException ex = assertThrows(
                EmailAddressException.class,
                () -> service.sendRegistrationOtp(missingRecipient, "123456")
        );

        assertEquals(AddressField.TO, ex.getField());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("6. Successful email delivery with personal name sender")
    void successfulDelivery_WithPersonalNameSender() throws Exception {
        String senderWithPersonal = "Calisthenics AI <verify@calisthenics.ai>";
        RegistrationEmailService service = new RegistrationEmailService(mailSender, senderWithPersonal);
        String recipient = "athlete@gmail.com";
        String otp = "998877";

        service.sendRegistrationOtp(recipient, otp);

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        assertEquals("Verify Your Calisthenics AI Account", sentMessage.getSubject());
        assertEquals("athlete@gmail.com", sentMessage.getRecipients(Message.RecipientType.TO)[0].toString());
        assertTrue(sentMessage.getFrom()[0].toString().contains("verify@calisthenics.ai"));
    }

    @Test
    @DisplayName("7. SMTP delivery failure is thrown as MailSendException (distinct from AddressException)")
    void smtpDeliveryFailure_ThrowsMailSendException() {
        RegistrationEmailService service = new RegistrationEmailService(mailSender, DEFAULT_SENDER);
        doThrow(new MailSendException("SMTP connection timed out"))
                .when(mailSender).send(any(MimeMessage.class));

        MailSendException ex = assertThrows(
                MailSendException.class,
                () -> service.sendRegistrationOtp("tranquangduy15122006@gmail.com", "123456")
        );

        assertTrue(ex.getMessage().contains("mail server") || ex.getMessage().contains("Không thể gửi"));
    }

    private String extractText(Object content) throws MessagingException, IOException {
        if (!(content instanceof Multipart multipart)) {
            return content.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            sb.append(extractText(bodyPart.getContent()));
        }
        return sb.toString();
    }
}

