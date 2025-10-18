package com.apontaja.backend.service;

import com.apontaja.backend.dto.AuthResponse;
import com.apontaja.backend.dto.LoginRequest;
import com.apontaja.backend.dto.RefreshTokenRequest;
import com.apontaja.backend.dto.RegisterRequest;

public interface AuthenticationService {
    
    /**
     * Register a new user
     * 
     * @param request The registration request
     * @return Authentication response with access and refresh tokens
     */
    AuthResponse register(RegisterRequest request);
    
    /**
     * Authenticate a user and generate tokens
     * 
     * @param request The login request
     * @return Authentication response with access and refresh tokens
     */
    AuthResponse login(LoginRequest request);
    
    /**
     * Refresh an access token using a refresh token
     * 
     * @param request The refresh token request
     * @return Authentication response with new access token
     */
    AuthResponse refreshToken(RefreshTokenRequest request);
    
    /**
     * Logout a user by deleting their refresh token
     * 
     * @param refreshToken The refresh token to delete
     */
    void logout(String refreshToken);
}
