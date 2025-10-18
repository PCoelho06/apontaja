package com.apontaja.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.from:noreply@apontaja.com}")
    private String fromEmail;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Override
    public void sendExistingEmailNotification(String email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Registration Attempt - Apontaja");
            message.setText(String.format(
                "Hello,\n\n" +
                "Someone attempted to register an account with this email address on Apontaja.\n\n" +
                "If this was you and you already have an account, you can log in directly.\n" +
                "If you forgot your password, you can reset it here: %s/reset-password\n\n" +
                "If this wasn't you, you can safely ignore this email.\n\n" +
                "Best regards,\n" +
                "The Apontaja Team",
                baseUrl
            ));
            
            mailSender.send(message);
            log.info("Existing email notification sent to: {}", email);
        } catch (MailException e) {
            log.error("Failed to send existing email notification to: {}", email, e);
        }
    }
    
    @Override
    public void sendRegistrationVerificationEmail(String email, String firstName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Welcome to Apontaja - Verify Your Email");
            message.setText(String.format(
                "Hello %s,\n\n" +
                "Welcome to Apontaja! We're excited to have you on board.\n\n" +
                "Your account has been successfully created. You can now log in and start using our services.\n\n" +
                "If you have any questions, please don't hesitate to contact us.\n\n" +
                "Best regards,\n" +
                "The Apontaja Team",
                firstName
            ));
            
            mailSender.send(message);
            log.info("Registration verification email sent to: {} ({})", email, firstName);
        } catch (MailException e) {
            log.error("Failed to send registration verification email to: {}", email, e);
        }
    }
}
