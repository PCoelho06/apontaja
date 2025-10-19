package com.apontaja.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Captor
    private ArgumentCaptor<SimpleMailMessage> messageCaptor;

    private static final String FROM_EMAIL = "noreply@apontaja.com";
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_FIRST_NAME = "John";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "baseUrl", BASE_URL);
    }

    @Test
    void sendExistingEmailNotification_WithValidEmail_ShouldSendEmail() {
        // Given
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.sendExistingEmailNotification(TEST_EMAIL);

        // Then
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertThat(sentMessage.getFrom()).isEqualTo(FROM_EMAIL);
        assertThat(sentMessage.getTo()).containsExactly(TEST_EMAIL);
        assertThat(sentMessage.getSubject()).isEqualTo("Registration Attempt - Apontaja");
        assertThat(sentMessage.getText()).contains("attempted to register");
        assertThat(sentMessage.getText()).contains(BASE_URL + "/reset-password");
        assertThat(sentMessage.getText()).contains("The Apontaja Team");
    }

    @Test
    void sendExistingEmailNotification_WhenMailSenderThrowsException_ShouldLogError() {
        // Given
        doThrow(new MailException("SMTP error") {}).when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.sendExistingEmailNotification(TEST_EMAIL);

        // Then
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendRegistrationVerificationEmail_WithValidData_ShouldSendEmail() {
        // Given
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.sendRegistrationVerificationEmail(TEST_EMAIL, TEST_FIRST_NAME);

        // Then
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertThat(sentMessage.getFrom()).isEqualTo(FROM_EMAIL);
        assertThat(sentMessage.getTo()).containsExactly(TEST_EMAIL);
        assertThat(sentMessage.getSubject()).isEqualTo("Welcome to Apontaja - Verify Your Email");
        assertThat(sentMessage.getText()).contains("Hello " + TEST_FIRST_NAME);
        assertThat(sentMessage.getText()).contains("Welcome to Apontaja");
        assertThat(sentMessage.getText()).contains("account has been successfully created");
        assertThat(sentMessage.getText()).contains("The Apontaja Team");
    }

    @Test
    void sendRegistrationVerificationEmail_WhenMailSenderThrowsException_ShouldLogError() {
        // Given
        doThrow(new MailException("SMTP error") {}).when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.sendRegistrationVerificationEmail(TEST_EMAIL, TEST_FIRST_NAME);

        // Then
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendExistingEmailNotification_WithDifferentBaseUrl_ShouldIncludeCorrectUrl() {
        // Given
        String customBaseUrl = "https://production.apontaja.com";
        ReflectionTestUtils.setField(emailService, "baseUrl", customBaseUrl);
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.sendExistingEmailNotification(TEST_EMAIL);

        // Then
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertThat(sentMessage.getText()).contains(customBaseUrl + "/reset-password");
    }

    @Test
    void sendRegistrationVerificationEmail_WithSpecialCharactersInName_ShouldSendEmail() {
        // Given
        String specialName = "José-André";
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        // When
        emailService.sendRegistrationVerificationEmail(TEST_EMAIL, specialName);

        // Then
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertThat(sentMessage.getText()).contains("Hello " + specialName);
    }
}
