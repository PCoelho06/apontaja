package com.apontaja.back.account.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ne bloque jamais elle-même une requête sans token (ou avec token
 * invalide) — elle se contente de ne rien peupler dans le SecurityContext
 * si l'authentification échoue, laissant authorizeHttpRequests décider
 * (voir SecurityConfig : anyRequest().authenticated() une fois ce filtre
 * enregistré).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenIssuer jwtAccessTokenIssuer;

    public JwtAuthenticationFilter(JwtAccessTokenIssuer jwtAccessTokenIssuer) {
        this.jwtAccessTokenIssuer = jwtAccessTokenIssuer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        extractBearerToken(request)
                .flatMap(jwtAccessTokenIssuer::validateAndExtractAccountId)
                .ifPresent(this::authenticate);

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(UUID accountId) {
        var authentication = new UsernamePasswordAuthenticationToken(accountId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
