package com.calisthenics.backend.security.registration;

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
public class RegistrationEmailService {

    private final JavaMailSender mailSender;
    private final String mailUsername;

    public RegistrationEmailService(
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

    public void sendRegistrationOtp(String recipient, String otp) {
        // 1. Validate configured sender separately
        InternetAddress senderAddress = validateSender();

        // 2. Normalize and validate recipient address
        if (recipient == null) {
            EmailAddressValidator.logFailure(AddressField.TO, null, 0, false, false, "Recipient address must not be null");
            throw new EmailAddressException(AddressField.TO, "null", "Recipient email address must not be null");
        }

        InternetAddress recipientAddress = EmailAddressValidator.validateAndParse(AddressField.TO, recipient, false);
        String normalizedRecipient = recipient.strip();

        // 3. Prepare and send email message
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderAddress);
            helper.setTo(recipientAddress);
            helper.setSubject("Verify Your Calisthenics AI Account");
            helper.setText(
                    "Welcome to Calisthenics AI! Your 6-digit verification code is " + otp
                            + ". It expires in 5 minutes. Do not share this code with anyone.",
                    html(otp)
            );
            mailSender.send(message);
            log.info("Successfully delivered registration verification OTP email to {}", EmailAddressValidator.sanitize(normalizedRecipient));
        } catch (MessagingException ex) {
            log.error("Failed to build MIME message for recipient {}: {}", EmailAddressValidator.sanitize(normalizedRecipient), ex.getMessage(), ex);
            throw new MailSendException("Không thể chuẩn bị email xác thực: " + ex.getMessage(), ex);
        } catch (MailException ex) {
            log.error("Failed to deliver registration verification email via SMTP to {}: {}", EmailAddressValidator.sanitize(normalizedRecipient), ex.getMessage(), ex);
            throw new MailSendException("Không thể gửi email xác thực qua hệ thống mail server. Vui lòng thử lại sau.", ex);
        }
    }

    private String html(String otp) {
        return """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#0d0d0d;font-family:Arial,sans-serif;color:#ffffff">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                           style="padding:36px 12px;background:#0d0d0d">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                               style="max-width:520px;background:#171717;border:1px solid #303030;border-radius:18px;padding:36px">
                          <tr><td>
                            <div style="font-size:14px;font-weight:bold;letter-spacing:1.5px;color:#b7f56a">
                               CALISTHENICS AI
                            </div>
                            <h1 style="font-size:24px;margin:20px 0 12px;color:#ffffff">Verify your email address</h1>
                            <p style="font-size:15px;line-height:1.6;color:#a0a0a0">
                               Thank you for joining Calisthenics AI! Use the verification code below to complete your registration:
                            </p>
                            <div style="margin:28px 0;padding:18px;text-align:center;background:#202020;
                                        border:1px solid #b7f56a;border-radius:14px;font-size:36px;font-weight:bold;
                                        letter-spacing:12px;color:#b7f56a">%s</div>
                            <p style="font-size:14px;line-height:1.6;color:#a0a0a0">
                               This code expires in <strong style="color:#ffffff">5 minutes</strong>. Never share this code with anyone.
                            </p>
                            <p style="font-size:13px;line-height:1.6;color:#707070">
                               If you did not request this account registration, you can safely ignore this email.
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
