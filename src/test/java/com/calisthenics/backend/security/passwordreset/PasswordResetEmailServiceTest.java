package com.calisthenics.backend.security.passwordreset;

import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetEmailServiceTest {

    @Test
    void sendsProfessionalOtpEmailUsingTheConfiguredSender() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        doNothing().when(sender).send(message);

        new PasswordResetEmailService(sender, "account@example.com")
                .sendOtp("athlete@example.com", "012345");

        verify(sender).send(message);
        assertEquals("Reset Your Calisthenics AI Password", message.getSubject());
        assertTrue(message.getFrom()[0].toString().contains("account@example.com"));
        String content = textContent(message.getContent());
        assertTrue(content.contains("CALISTHENICS AI"));
        assertTrue(content.contains("012345"));
        assertTrue(content.contains("5 minutes"));
        assertTrue(content.contains("Never share"));
    }

    private String textContent(Object content) throws MessagingException, IOException {
        if (!(content instanceof Multipart multipart)) {
            return content.toString();
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < multipart.getCount(); index++) {
            BodyPart part = multipart.getBodyPart(index);
            text.append(textContent(part.getContent()));
        }
        return text.toString();
    }
}
