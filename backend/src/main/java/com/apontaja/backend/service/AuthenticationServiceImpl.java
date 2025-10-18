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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            // Send notification to existing user that someone tried to register with their email
            emailService.sendExistingEmailNotification(request.getEmail());
            
            // Return generic response without revealing the email exists
            // This prevents email enumeration attacks
            log.info("Registration attempt with existing email: {}", request.getEmail());
            throw new RuntimeException("Registration request processed. If this email is valid, you will receive a confirmation email.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);
        
        // Send verification email to new user
        emailService.sendRegistrationVerificationEmail(user.getEmail(), user.getFirstName());
        
        log.info("New user registered: {}", user.getEmail());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = (User) authentication.getPrincipal();
        
        log.info("User logged in: {}", user.getEmail());
        
        // Delete existing refresh tokens for this user
        refreshTokenService.deleteByUser(user);

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
                    log.debug("Access token refreshed for user: {}", user.getEmail());
                    return AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(requestRefreshToken)
                            .tokenType("Bearer")
                            .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                            .build();
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken, 
                    "Refresh token not found"));
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.findByToken(refreshToken)
                .ifPresent(token -> {
                    log.info("User logged out, deleting refresh token");
                    refreshTokenService.deleteToken(token);
                });
    }
}
