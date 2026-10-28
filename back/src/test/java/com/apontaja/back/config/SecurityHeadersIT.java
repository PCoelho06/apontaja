package com.apontaja.back.config;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** En-têtes de sécurité HTTP (5b) et absence de CORS : topologie « même origine ». */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class SecurityHeadersIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void les_en_tetes_de_securite_sont_poses_sur_chaque_reponse() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void les_en_tetes_sont_aussi_poses_sur_une_reponse_d_erreur() throws Exception {
        mockMvc.perform(get("/api/public/salons/{salonId}", UUID.randomUUID()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void hsts_n_est_emis_que_sur_une_requete_securisee() throws Exception {
        mockMvc.perform(get("/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));

        mockMvc.perform(get("/health")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void aucune_origine_etrangere_n_est_autorisee_par_cors() throws Exception {
        mockMvc.perform(get("/api/public/salons/{salonId}", UUID.randomUUID())
                .header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        // Le statut de la pré-requête n'est volontairement pas figé : seul compte l'absence d'autorisation.
        mockMvc.perform(options("/api/me/appointments")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
