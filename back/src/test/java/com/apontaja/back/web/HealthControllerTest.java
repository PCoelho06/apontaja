package com.apontaja.back.web;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /health répond 200 sans authentification, et que le reste des endpoints est
 * refusé par défaut (cf. {@link com.apontaja.back.config.SecurityConfig}).
 *
 * <p>{@code @SpringBootTest} démarre le contexte complet, donc nécessite une base de données
 * depuis l'étape 7 — d'où l'import de {@link PostgresTestcontainersConfiguration}, sans quoi le
 * contexte échouerait faute de DataSource disponible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthRespondsUpWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void unknownEndpointIsDeniedByDefault() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isForbidden());
    }
}
