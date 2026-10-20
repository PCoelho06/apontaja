package com.apontaja.back.appointment.web;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limitation de débit des endpoints publics, filtre ACTIF (propriété par défaut). Chaque test n'utilise
 * que son propre seau (fiche/prestations : 60 par minute ; créneaux : 20 par minute) : l'état du filtre
 * est partagé dans le contexte mis en cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class PublicRateLimitingIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void le_soixante_et_unieme_appel_public_general_est_refuse_en_429() throws Exception {
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/public/salons/{salonId}", UUID.randomUUID())).andExpect(status().isNotFound());
        }

        mockMvc.perform(get("/api/public/salons/{salonId}", UUID.randomUUID()))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void le_vingt_et_unieme_appel_de_creneaux_est_refuse_en_429() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/public/salons/{salonId}/services/{serviceId}/slots", UUID.randomUUID(),
                    UUID.randomUUID())).andExpect(status().isNotFound());
        }

        mockMvc.perform(get("/api/public/salons/{salonId}/services/{serviceId}/slots", UUID.randomUUID(),
                UUID.randomUUID())).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
