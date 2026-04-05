package com.sinay.core.server.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ===== PUBLIC METHODS =====

    @Async
    public void sendVerificationEmail(String toEmail, String toName, String token) {
        String verifyUrl = frontendUrl + "/verify-email?token=" + token;
        Map<String, Object> vars = Map.of(
                "name", toName,
                "verifyUrl", verifyUrl,
                "appName", "Sinay"
        );
        sendHtmlMail(toEmail, "Email Adresinizi Doğrulayın", "mail/verify-email", vars);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String toName, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        Map<String, Object> vars = Map.of(
                "name", toName,
                "resetUrl", resetUrl,
                "appName", "Sinay",
                "expiresIn", "1 saat"
        );
        sendHtmlMail(toEmail, "Şifre Sıfırlama Talebi", "mail/reset-password", vars);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String toName) {
        Map<String, Object> vars = Map.of(
                "name", toName,
                "appName", "Sinay",
                "loginUrl", frontendUrl + "/login"
        );
        sendHtmlMail(toEmail, "Hoş Geldiniz!", "mail/welcome", vars);
    }

    @Async
    public void sendPasswordChangedEmail(String toEmail, String toName) {
        Map<String, Object> vars = Map.of(
                "name", toName,
                "appName", "Sinay",
                "supportEmail", fromEmail
        );
        sendHtmlMail(toEmail, "Şifreniz Değiştirildi", "mail/password-changed", vars);
    }

    // ===== PRIVATE =====

    private void sendHtmlMail(String to, String subject, String template, Map<String, Object> variables) {
        try {
            Context context = new Context();
            variables.forEach(context::setVariable);
            String htmlContent = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.debug("Mail sent to: {} | Subject: {}", to, subject);

        } catch (MessagingException e) {
            log.error("Mail send failed to: {} | Reason: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected mail error: {}", e.getMessage());
        }
    }
}