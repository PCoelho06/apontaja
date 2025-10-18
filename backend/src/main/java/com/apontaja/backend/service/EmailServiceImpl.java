package com.apontaja.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);
    
    @Override
    public void sendExistingEmailNotification(String email) {
        // TODO: Implement actual email sending
        // This should inform the user that their email is already registered
        // and provide a link to reset password if they forgot it
        logger.info("Sending existing email notification to: {}", email);
        logger.debug("Email would contain: Your email is already registered. If you forgot your password, click here to reset it.");
    }
    
    @Override
    public void sendRegistrationVerificationEmail(String email, String firstName) {
        // TODO: Implement actual email sending
        // This should send a verification email to confirm the user's email address
        logger.info("Sending registration verification email to: {} ({})", email, firstName);
        logger.debug("Email would contain: Welcome {}! Please verify your email address by clicking the link.", firstName);
    }
}
