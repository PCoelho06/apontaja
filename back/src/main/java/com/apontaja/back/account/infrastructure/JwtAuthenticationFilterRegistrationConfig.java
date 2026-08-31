package com.apontaja.back.account.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sans ceci, Spring Boot enregistre automatiquement tout bean {@code Filter}
 * dans la chaîne de servlets globale (en plus de son ajout explicite dans
 * SecurityConfig via addFilterBefore) — le filtre tournerait deux fois par
 * requête. Piège connu des filtres JWT custom en Spring Boot.
 */
@Configuration
class JwtAuthenticationFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableAutoRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
