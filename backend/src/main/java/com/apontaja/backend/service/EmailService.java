package com.apontaja.backend.service;

public interface EmailService {
    
    /**
     * Send email notification when user tries to register with an existing email
     * 
     * @param email The email address that already exists
     */
    void sendExistingEmailNotification(String email);
    
    /**
     * Send email verification after successful registration
     * 
     * @param email The email address of the new user
     * @param firstName The first name of the new user
     */
    void sendRegistrationVerificationEmail(String email, String firstName);
}
