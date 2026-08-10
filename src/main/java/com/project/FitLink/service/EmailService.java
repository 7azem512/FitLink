package com.project.FitLink.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String sender;

    @Async
    public void sendVerifyEmailOtp(String to, String userName, String otpCode) {
        sendOtpEmail(to, userName, otpCode, "templates/verify-email-otp.html", "Verify your FitLink account");
    }

    @Async
    public void sendForgotPasswordOtp(String to, String userName, String otpCode) {
        sendOtpEmail(to, userName, otpCode, "templates/forgot-password-otp.html", "Reset your FitLink password");
    }

    @Async
    public void sendOtpEmail(String to, String userName, String otpCode, String templatePath, String subject) {
        try {
            String template = new ClassPathResource(templatePath).getContentAsString(StandardCharsets.UTF_8);
            String body = template
                    .replace("{{userName}}", userName)
                    .replace("{{otpCode}}", otpCode);
            sendEmail(to, subject, body);
        } catch (IOException e) {
            log.error("Failed to load email template {}: {}", templatePath, e.getMessage(), e);
        }
    }

    @Async
    public void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(sender);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}
