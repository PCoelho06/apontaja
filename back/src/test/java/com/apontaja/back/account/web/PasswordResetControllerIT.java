package com.apontaja.back.account.web;

import com.apontaja.back.account.domain.EmailSender;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @MockitoBean remplace @MockBean, supprimé en Spring Boot 4.0 (Spring
 *              Framework 7). Nécessaire ici : le token en clair du reset ne
 *              sort jamais dans une réponse HTTP (par conception), seulement
 *              dans l'email — il faut intercepter l'EmailSender pour le
 *              récupérer et tester le flux complet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class PasswordResetControllerIT {

        static final String RATE_LIMITING_DISABLED = "apontaja.security.rate-limiting.enabled=false";

        private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private EmailSender emailSender;

        private String registerAndReturnEmail() throws Exception {
                String email = "reset-" + UUID.randomUUID() + "@example.com";

                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "email": "%s",
                                  "password": "ancien-mot-de-passe-suffisant",
                                  "acceptTos": true,
                                  "acceptPrivacy": true
                                }
                                """.formatted(email))).andExpect(status().isCreated());

                return email;
        }

        private String extractResetToken(String email) throws Exception {
                mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "%s"}
                                """.formatted(email))).andExpect(status().isNoContent());

                ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
                verify(emailSender, atLeastOnce()).send(org.mockito.ArgumentMatchers.eq(email),
                                org.mockito.ArgumentMatchers.anyString(), bodyCaptor.capture());

                String resetBody = bodyCaptor.getAllValues().stream()
                                .filter(body -> body.contains("reinitialiser-mot-de-passe")).findFirst()
                                .orElseThrow(() -> new AssertionError("Aucun email de reset capturé"));

                Matcher matcher = TOKEN_PATTERN.matcher(resetBody);
                assertThat(matcher.find()).isTrue();
                return matcher.group(1);
        }

        @Test
        void forgot_password_repond_toujours_204_meme_pour_un_email_inconnu() throws Exception {
                mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "personne-nexiste-pas@example.com"}
                                """)).andExpect(status().isNoContent());
        }

        @Test
        void flux_complet_forgot_password_puis_reset_password() throws Exception {
                String email = registerAndReturnEmail();
                String token = extractResetToken(email);

                mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                                {"token": "%s", "newPassword": "nouveau-mot-de-passe-suffisant"}
                                """.formatted(token))).andExpect(status().isNoContent());

                // Le token est à usage unique : une seconde utilisation échoue.
                mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                                {"token": "%s", "newPassword": "encore-un-autre-mot-de-passe"}
                                """.formatted(token))).andExpect(status().isBadRequest());

                // Le nouveau mot de passe fonctionne désormais pour se connecter.
                mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "%s", "password": "nouveau-mot-de-passe-suffisant"}
                                """.formatted(email))).andExpect(status().isOk());
        }

        @Test
        void reset_password_avec_un_token_bidon_repond_400() throws Exception {
                mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content("""
                                {"token": "ce-token-n-existe-pas", "newPassword": "peu-importe-le-mot-de-passe"}
                                """)).andExpect(status().isBadRequest());
        }
}
