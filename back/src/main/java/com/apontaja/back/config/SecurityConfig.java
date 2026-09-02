package com.apontaja.back.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import jakarta.servlet.Filter;

/**
 * CSRF — [DECIDED] : double-submit token via CookieCsrfTokenRepository (cookie
 * XSRF-TOKEN lisible en JS + header X-XSRF-TOKEN echo par le client).
 * register/login exemptés (pas de session préexistante à usurper) ; tout
 * endpoint state-changing authentifié à venir (refresh, logout, etc.) devra
 * fournir le header.
 *
 * anyRequest().authenticated() remplace le denyAll() de la Phase 0 : maintenant
 * qu'un filtre JWT existe, c'est la vraie porte d'entrée pour tout futur
 * endpoint protégé (Phase 2+).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final Filter jwtAuthenticationFilter;
        private final Filter rateLimitingFilter;

        public SecurityConfig(@Qualifier("jwtAuthenticationFilter") Filter jwtAuthenticationFilter,
                        @Qualifier("rateLimitingFilter") Filter rateLimitingFilter) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.rateLimitingFilter = rateLimitingFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http.authorizeHttpRequests(authorize -> authorize.requestMatchers("/health").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                                "/api/auth/refresh", "/api/auth/logout", "/api/auth/confirm-email",
                                                "/api/auth/resend-verification-email", "/api/auth/forgot-password",
                                                "/api/auth/reset-password")
                                .permitAll().anyRequest().authenticated())
                                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                                .ignoringRequestMatchers("/api/auth/register", "/api/auth/login",
                                                                "/api/auth/confirm-email",
                                                                "/api/auth/resend-verification-email",
                                                                "/api/auth/forgot-password", "/api/auth/reset-password")
                                // refresh/logout restent seuls hors de cette liste
                                ).addFilterBefore(rateLimitingFilter, CsrfFilter.class)
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .httpBasic(httpBasic -> httpBasic.disable())
                                .formLogin(formLogin -> formLogin.disable());

                return http.build();
        }
}
