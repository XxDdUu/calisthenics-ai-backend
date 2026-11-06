package com.calisthenics.backend.security.passwordreset;

import com.calisthenics.backend.security.registration.AddressField;
import com.calisthenics.backend.security.registration.EmailAddressException;
import com.calisthenics.backend.security.registration.EmailAddressValidator;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;
    private final String mailUsername;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:${spring.mail.username:}}") String mailUsername
    ) {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
    }

    public InternetAddress validateSender() {
        if (mailUsername == null || mailUsername.isBlank()) {
            EmailAddressValidator.logFailure(
                    AddressField.FROM,
                    mailUsername,
                    mailUsername == null ? 0 : mailUsername.length(),
                    mailUsername != null && EmailAddressValidator.containsWhitespace(mailUsername),
                    mailUsername != null && EmailAddressValidator.containsControlChars(mailUsername),
                    "Configured sender email address (spring.mail.username / app.mail.from) is empty or unconfigured"
            );
            throw new EmailAddressException(
                    AddressField.FROM,
                    EmailAddressValidator.sanitize(mailUsername),
                    "Configured sender email address is missing or unconfigured in application settings"
            );
        }
        return EmailAddressValidator.validateAndParse(AddressField.FROM, mailUsername, true);
    }

    public void sendOtp(String recipient, String otp) {
        InternetAddress senderAddress = validateSender();

        if (recipient == null) {
            EmailAddressValidator.logFailure(AddressField.TO, null, 0, false, false, "Recipient address must not be null");
            throw new EmailAddressException(AddressField.TO, "null", "Recipient email address must not be null");
        }

        InternetAddress recipientAddress = EmailAddressValidator.validateAndParse(AddressField.TO, recipient, false);
        String normalizedRecipient = recipient.strip();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderAddress);
            helper.setTo(recipientAddress);
            helper.setSubject("Reset Your Calisthenics AI Password");
            helper.setText(
                    "A password reset was requested for your Calisthenics AI account. "
                            + "Your 6-digit OTP is " + otp + ". It expires in 5 minutes. "
                            + "Do not share this code. Ignore this email if you did not request a reset.",
                    html(otp)
            );
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            log.error("Failed to deliver password reset email to {}: {}", EmailAddressValidator.sanitize(normalizedRecipient), ex.getMessage(), ex);
            throw new MailSendException("Password reset email delivery failed");
        }
    }

    private String html(String otp) {
        return """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#f4f6f8;font-family:Arial,sans-serif;color:#17212b">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                           style="padding:36px 12px;background:#f4f6f8">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                               style="max-width:520px;background:#fff;border-radius:16px;padding:36px">
                          <tr><td>
                            <div style="font-size:13px;font-weight:bold;letter-spacing:1px;color:#178c72">
                               CALISTHENICS AI
                            </div>
                            <h1 style="font-size:24px;margin:20px 0 12px">Reset your password</h1>
                            <p style="font-size:15px;line-height:1.6;color:#52606d">
                               We received a request to reset the password for your account.
                               Enter this one-time code to continue:
                            </p>
                            <div style="margin:28px 0;padding:18px;text-align:center;background:#edf8f5;
                                        border-radius:12px;font-size:34px;font-weight:bold;
                                        letter-spacing:10px;color:#116b59">%s</div>
                            <p style="font-size:14px;line-height:1.6;color:#52606d">
                               This code expires in <strong>5 minutes</strong>. Never share it with anyone.
                            </p>
                            <p style="font-size:13px;line-height:1.6;color:#78838d">
                               If you did not request a password reset, you can safely ignore this email.
                            </p>
                          </td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(otp);
    }
}
