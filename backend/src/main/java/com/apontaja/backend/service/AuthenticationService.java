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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
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

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = (User) authentication.getPrincipal();
        
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

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
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

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.findByToken(refreshToken)
                .ifPresent(refreshTokenService::revokeToken);
    }
}
