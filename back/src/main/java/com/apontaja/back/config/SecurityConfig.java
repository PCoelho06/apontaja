package com.apontaja.back.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

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
@EnableMethodSecurity
public class SecurityConfig {

        // API JSON pure : aucune ressource n'est jamais chargée ni affichée depuis une réponse de l'API.
        private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";
        private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";
        private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

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
                                .permitAll().requestMatchers(HttpMethod.GET, "/api/staff-invitations/*").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll().anyRequest()
                                .authenticated())
                                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                                                // Inversion (Phase 2) : CSRF n'a de sens que pour l'auth cookie-driven
                                                // (refresh/logout). Tout le reste est authentifié par Bearer JWT en
                                                // mémoire JS,
                                                // jamais envoyé automatiquement cross-site par le navigateur — donc
                                                // CSRF-exempt
                                                // par construction, y compris tout futur endpoint de domaine (salons,
                                                // staff...).
                                                .ignoringRequestMatchers(request -> !isCsrfProtectedPath(
                                                                request.getRequestURI())))
                                // Force la résolution du token différé à CHAQUE requête, sinon le cookie
                                // XSRF-TOKEN n'est jamais réellement écrit dans une API REST pure (rien
                                // ne "consomme" le token différé comme le ferait un template serveur).
                                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                                .addFilterBefore(rateLimitingFilter, CsrfFilter.class)
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                // En-têtes de sécurité (5b). HSTS n'est émis par Spring Security que sur une requête
                                // sécurisée : jamais en HTTP local, et en production grâce à X-Forwarded-Proto
                                // (voir application-prod.yml). Aucun CORS : portails et API partagent l'origine.
                                .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults())
                                                .frameOptions(frame -> frame.deny())
                                                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                                                .contentSecurityPolicy(
                                                                csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                                                                .includeSubDomains(true))
                                                .addHeaderWriter(
                                                                new StaticHeadersWriter("Permissions-Policy",
                                                                                PERMISSIONS_POLICY)))
                                .httpBasic(httpBasic -> httpBasic.disable())
                                .formLogin(formLogin -> formLogin.disable());

                return http.build();
        }

        private static boolean isCsrfProtectedPath(String uri) {
                return "/api/auth/refresh".equals(uri) || "/api/auth/logout".equals(uri);
        }

        private static final class CsrfCookieFilter extends OncePerRequestFilter {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
                        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                        if (csrfToken != null) {
                                csrfToken.getToken(); // force la résolution -> déclenche l'écriture du cookie
                        }
                        filterChain.doFilter(request, response);
                }
        }
}
