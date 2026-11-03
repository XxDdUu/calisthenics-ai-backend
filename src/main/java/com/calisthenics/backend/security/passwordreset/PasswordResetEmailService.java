package com.calisthenics.backend.security.passwordreset;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;
    private final String mailUsername;

    public PasswordResetEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
    }

    public void sendOtp(String recipient, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername);
            helper.setTo(recipient);
            helper.setSubject("Reset Your Calisthenics AI Password");
            helper.setText(
                    "A password reset was requested for your Calisthenics AI account. "
                            + "Your 6-digit OTP is " + otp + ". It expires in 5 minutes. "
                            + "Do not share this code. Ignore this email if you did not request a reset.",
                    html(otp)
            );
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
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
