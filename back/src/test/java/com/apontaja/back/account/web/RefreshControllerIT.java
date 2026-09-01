package com.apontaja.back.account.web;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RefreshControllerIT {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AccountRepository accountRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private IdGenerator idGenerator;

        @Autowired
        private Clock clock;

        private Cookie loginAndGetRefreshCookie(String email) throws Exception {
                accountRepository.save(new Account(
                                idGenerator.generate(), email,
                                passwordEncoder.encode("un-mot-de-passe-suffisamment-long"),
                                clock.instant()));

                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"email": "%s", "password": "un-mot-de-passe-suffisamment-long"}
                                                """.formatted(email)))
                                .andExpect(status().isOk())
                                .andReturn();

                Cookie cookie = loginResult.getResponse().getCookie("refresh_token");
                assertThat(cookie).isNotNull();
                return cookie;
        }

        @Test
        void refresh_sans_token_csrf_est_rejete_403() throws Exception {
                Cookie refreshCookie = loginAndGetRefreshCookie("kate-" + UUID.randomUUID() + "@example.com");

                mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                                .andExpect(status().isForbidden());
        }

        @Test
        void refresh_tourne_le_token_et_l_ancien_devient_inutilisable() throws Exception {
                Cookie refreshCookie = loginAndGetRefreshCookie("liam-" + UUID.randomUUID() + "@example.com");

                MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).with(csrf()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                .andReturn();

                Cookie newCookie = refreshResult.getResponse().getCookie("refresh_token");
                assertThat(newCookie).isNotNull();
                assertThat(newCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

                // L'ancien cookie ne fonctionne plus (rotation).
                mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).with(csrf()))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void reutiliser_un_token_deja_tourne_invalide_aussi_le_nouveau() throws Exception {
                Cookie refreshCookie = loginAndGetRefreshCookie("mona-" + UUID.randomUUID() + "@example.com");

                MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).with(csrf()))
                                .andExpect(status().isOk())
                                .andReturn();
                Cookie rotatedCookie = refreshResult.getResponse().getCookie("refresh_token");

                // Rejeu de l'ancien token (déjà révoqué par la rotation ci-dessus) :
                // détection de réutilisation -> toute la famille est invalidée.
                mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).with(csrf()))
                                .andExpect(status().isUnauthorized());

                // Le token pourtant "valide" issu de la rotation est maintenant
                // révoqué lui aussi (effet de la détection de réutilisation).
                mockMvc.perform(post("/api/auth/refresh").cookie(rotatedCookie).with(csrf()))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void logout_revoque_le_cookie_et_efface_le_cookie_cote_client() throws Exception {
                Cookie refreshCookie = loginAndGetRefreshCookie("noah-" + UUID.randomUUID() + "@example.com");

                MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie).with(csrf()))
                                .andExpect(status().isNoContent())
                                .andReturn();

                Cookie clearedCookie = logoutResult.getResponse().getCookie("refresh_token");
                assertThat(clearedCookie).isNotNull();
                assertThat(clearedCookie.getMaxAge()).isZero();

                mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie).with(csrf()))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void logout_sans_cookie_est_idempotent() throws Exception {
                mockMvc.perform(post("/api/auth/logout").with(csrf()))
                                .andExpect(status().isNoContent());
        }
}
