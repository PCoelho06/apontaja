package com.apontaja.back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration de sécurité minimale pour la Phase 0 / étape 2.
 *
 * <p>
 * Seul /health est accessible sans authentification ; "/api/auth/register
 * ajouté en Phase 1 tranche 3, public par nature (impossible d'exiger une
 * authentification avant de créer le compte)".
 *
 * <p>
 * <b>CSRF — statut [PROVISIONAL], ne pas considérer comme acté</b> : la
 * protection CSRF par
 * défaut de Spring Security (basée sur les formulaires HTML) est désactivée ici
 * car elle n'a pas
 * de sens pour une API JSON pure sans session ni cookie d'auth pour l'instant.
 * La stratégie
 * définitive (SameSite=Strict + header custom sur les endpoints sensibles comme
 * /auth/refresh,
 * cf. §2) reste à implémenter et à valider explicitement en Phase 1 — cette
 * désactivation est
 * un choix de bootstrap temporaire, pas une décision d'architecture.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }
}
