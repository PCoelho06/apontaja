package com.apontaja.backend.service;

import com.apontaja.backend.dto.AuthResponse;
import com.apontaja.backend.dto.LoginRequest;
import com.apontaja.backend.dto.RefreshTokenRequest;
import com.apontaja.backend.dto.RegisterRequest;
import com.apontaja.backend.exception.TokenRefreshException;
import com.apontaja.backend.model.RefreshToken;
import com.apontaja.backend.model.Role;
import com.apontaja.backend.model.User;
import com.apontaja.backend.repository.UserRepository;
import com.apontaja.backend.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private RefreshTokenRequest refreshTokenRequest;
    private User user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .firstName("John")
                .lastName("Doe")
                .build();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("Password123!")
                .build();

        refreshTokenRequest = RefreshTokenRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded-password")
                .firstName("John")
                .lastName("Doe")
                .role(Role.USER)
                .enabled(true)
                .build();

        refreshToken = RefreshToken.builder()
                .id(1L)
                .token("valid-refresh-token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(86400))
                .build();
    }

    @Test
    void register_WithNewEmail_ShouldCreateUserAndReturnAuthResponse() {
        // Given
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(registerRequest.getEmail())).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(3600L);
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken);

        // When
        AuthResponse response = authenticationService.register(registerRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);

        verify(userRepository).existsByEmail(registerRequest.getEmail());
        verify(passwordEncoder).encode(registerRequest.getPassword());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendRegistrationVerificationEmail(registerRequest.getEmail(), registerRequest.getFirstName());
        verify(jwtTokenProvider).generateAccessToken(registerRequest.getEmail());
        verify(refreshTokenService).createRefreshToken(any(User.class));
    }

    @Test
    void register_WithExistingEmail_ShouldSendNotificationAndThrowException() {
        // Given
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authenticationService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Registration request processed");

        verify(userRepository).existsByEmail(registerRequest.getEmail());
        verify(emailService).sendExistingEmailNotification(registerRequest.getEmail());
        verify(userRepository, never()).save(any(User.class));
        verify(jwtTokenProvider, never()).generateAccessToken(anyString());
    }

    @Test
    void login_WithValidCredentials_ShouldReturnAuthResponse() {
        // Given
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(authentication)).thenReturn("access-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(3600L);
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        // When
        AuthResponse response = authenticationService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(refreshTokenService).deleteByUser(user);
        verify(jwtTokenProvider).generateAccessToken(authentication);
        verify(refreshTokenService).createRefreshToken(user);
    }

    @Test
    void refreshToken_WithValidToken_ShouldReturnNewAuthResponse() {
        // Given
        when(refreshTokenService.findByToken("valid-refresh-token")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
        when(jwtTokenProvider.generateAccessToken(user.getEmail())).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(3600L);

        // When
        AuthResponse response = authenticationService.refreshToken(refreshTokenRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);

        verify(refreshTokenService).findByToken("valid-refresh-token");
        verify(refreshTokenService).verifyExpiration(refreshToken);
        verify(jwtTokenProvider).generateAccessToken(user.getEmail());
    }

    @Test
    void refreshToken_WithInvalidToken_ShouldThrowTokenRefreshException() {
        // Given
        when(refreshTokenService.findByToken("invalid-token")).thenReturn(Optional.empty());
        RefreshTokenRequest invalidRequest = RefreshTokenRequest.builder()
                .refreshToken("invalid-token")
                .build();

        // When & Then
        assertThatThrownBy(() -> authenticationService.refreshToken(invalidRequest))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("Refresh token not found");

        verify(refreshTokenService).findByToken("invalid-token");
        verify(jwtTokenProvider, never()).generateAccessToken(anyString());
    }

    @Test
    void logout_WithValidRefreshToken_ShouldDeleteToken() {
        // Given
        when(refreshTokenService.findByToken("valid-refresh-token")).thenReturn(Optional.of(refreshToken));

        // When
        authenticationService.logout("valid-refresh-token");

        // Then
        verify(refreshTokenService).findByToken("valid-refresh-token");
        verify(refreshTokenService).deleteToken(refreshToken);
    }

    @Test
    void logout_WithInvalidRefreshToken_ShouldNotDeleteToken() {
        // Given
        when(refreshTokenService.findByToken("invalid-token")).thenReturn(Optional.empty());

        // When
        authenticationService.logout("invalid-token");

        // Then
        verify(refreshTokenService).findByToken("invalid-token");
        verify(refreshTokenService, never()).deleteToken(any(RefreshToken.class));
    }
}
