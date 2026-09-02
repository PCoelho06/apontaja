package com.apontaja.back.account.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

class RateLimitingFilterTest {

    @Test
    void laisse_passer_les_chemins_non_limites_sans_toucher_au_bucket() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void ne_bloque_jamais_si_desactive_par_propriete() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(false);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(requestFrom("/api/auth/register", "203.0.113.10"), mock(HttpServletResponse.class),
                    chain);
        }

        verify(chain, org.mockito.Mockito.times(10)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bloque_avec_429_au_dela_de_la_limite_configuree_pour_register() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(true);
        FilterChain chain = mock(FilterChain.class);

        // Limite register = 5/h (voir RateLimitingFilter.LIMITED_PATHS).
        for (int i = 0; i < 5; i++) {
            HttpServletRequest request = requestFrom("/api/auth/register", "203.0.113.10");
            HttpServletResponse response = mock(HttpServletResponse.class);

            filter.doFilterInternal(request, response, chain);
        }
        verify(chain, org.mockito.Mockito.times(5)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        HttpServletRequest sixthRequest = requestFrom("/api/auth/register", "203.0.113.10");
        HttpServletResponse sixthResponse = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(sixthResponse.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(sixthRequest, sixthResponse, chain);

        verify(sixthResponse).setStatus(429);
        verify(sixthResponse).setHeader(eq("Retry-After"), anyString());
        verify(chain, never()).doFilter(sixthRequest, sixthResponse);
        assertThat(body.toString()).contains("429");
    }

    @Test
    void deux_ip_differentes_ont_des_seaux_independants() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(true);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(requestFrom("/api/auth/register", "198.51.100.1"), mock(HttpServletResponse.class),
                    chain);
        }

        // IP différente : doit encore passer malgré les 5 tentatives de la première IP.
        HttpServletResponse response = mock(HttpServletResponse.class);
        filter.doFilterInternal(requestFrom("/api/auth/register", "198.51.100.2"), response, chain);

        verify(chain, org.mockito.Mockito.times(6)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private HttpServletRequest requestFrom(String uri, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }
}
