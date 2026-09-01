package com.apontaja.back.account.web;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que RateLimitingFilter est effectivement câblé dans la chaîne de
 * sécurité réelle (le test unitaire RateLimitingFilterTest teste la logique
 * en isolation, celui-ci vérifie le branchement dans SecurityConfig).
 *
 * <p>
 * IP simulée dédiée (TEST-NET-3, RFC 5737) : les autres *IT de ce
 * paquet appellent aussi /api/auth/register via MockMvc, qui simule par
 * défaut la même IP (127.0.0.1) pour toutes les requêtes. Comme le bean
 * RateLimitingFilter est un singleton à état partagé dans tout le contexte
 * Spring (réutilisé entre classes de test), utiliser l'IP par défaut ici
 * ferait dépendre le résultat de l'ordre d'exécution des autres tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RateLimitingIT {

    private static final String DEDICATED_TEST_IP = "203.0.113.50";

    @Autowired
    private MockMvc mockMvc;

    private MockHttpServletRequestBuilder registerRequest(String email) {
        return post("/api/auth/register")
                .with(request -> {
                    request.setRemoteAddr(DEDICATED_TEST_IP);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "un-mot-de-passe-suffisamment-long",
                          "acceptTos": true,
                          "acceptPrivacy": true
                        }
                        """.formatted(email));
    }

    @Test
    void la_6e_tentative_de_register_depuis_la_meme_ip_est_bloquee_429() throws Exception {
        String suffix = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(registerRequest("rate-limit-" + i + "-" + suffix + "@example.com"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(registerRequest("rate-limit-6-" + suffix + "@example.com"))
                .andExpect(status().isTooManyRequests());
    }
}
