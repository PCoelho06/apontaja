package com.apontaja.back.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.shared.domain.EmailSender;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

/**
 * Tranche 4d-1 : acceptation explicite des conditions à l'inscription et liens
 * d'email par portail.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RegisterConsentAndPortalIT {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private AccountRepository accountRepository;

        @MockitoBean
        private EmailSender emailSender;

        private ResultActions call(String path, String body) throws Exception {
                return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
        }

        private static String registerBody(String email, String extraFields) {
                return """
                                {"email":"%s","password":"un-mot-de-passe-solide","firstName":"Alice",
                                "lastName":"Martin"%s}
                                """
                                .formatted(email, extraFields);
        }

        private String captureBody(String email, String marker) {
                ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
                verify(emailSender, atLeastOnce()).send(eq(email), anyString(), bodyCaptor.capture());
                return bodyCaptor.getAllValues().stream().filter(body -> body.contains(marker)).findFirst()
                                .orElseThrow(() -> new AssertionError("Aucun email contenant " + marker));
        }

        @Test
        void refuse_l_inscription_sans_acceptation_des_conditions() throws Exception {
                String email = UUID.randomUUID() + "@example.com";

                call("/api/auth/register", registerBody(email, ",\"acceptTos\":false,\"acceptPrivacy\":false"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.acceptTos").exists())
                                .andExpect(jsonPath("$.fieldErrors.acceptPrivacy").exists());
                call("/api/auth/register", registerBody(email, ",\"acceptTos\":true,\"acceptPrivacy\":false"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.acceptPrivacy").exists());

                assertThat(accountRepository.existsAliveByEmail(email)).isFalse();
        }

        @Test
        void refuse_un_portail_inconnu() throws Exception {
                String email = UUID.randomUUID() + "@example.com";

                call("/api/auth/register",
                                registerBody(email, ",\"acceptTos\":true,\"acceptPrivacy\":true,\"portal\":\"HACKER\""))
                                                .andExpect(status().isBadRequest());

                assertThat(accountRepository.existsAliveByEmail(email)).isFalse();
        }

        @Test
        void sans_portail_le_lien_de_verification_pointe_vers_le_portail_salon() throws Exception {
                String email = UUID.randomUUID() + "@example.com";

                call("/api/auth/register", registerBody(email, ",\"acceptTos\":true,\"acceptPrivacy\":true"))
                                .andExpect(status().isCreated());

                assertThat(captureBody(email, "confirmer-email"))
                                .contains("http://localhost:5173/confirmer-email?token=");
        }

        @Test
        void le_renvoi_et_le_mot_de_passe_oublie_suivent_le_portail_demande() throws Exception {
                String email = UUID.randomUUID() + "@example.com";
                call("/api/auth/register", registerBody(email, ",\"acceptTos\":true,\"acceptPrivacy\":true"))
                                .andExpect(status().isCreated());

                call("/api/auth/resend-verification-email", "{\"email\":\"%s\",\"portal\":\"CLIENT\"}".formatted(email))
                                .andExpect(status().isNoContent());
                call("/api/auth/forgot-password", "{\"email\":\"%s\",\"portal\":\"CLIENT\"}".formatted(email))
                                .andExpect(status().isNoContent());

                assertThat(captureBody(email, "http://localhost:5174/confirmer-email?token="))
                                .contains("http://localhost:5174/confirmer-email?token=");
                assertThat(captureBody(email, "http://localhost:5174/reinitialiser-mot-de-passe?token="))
                                .contains("http://localhost:5174/reinitialiser-mot-de-passe?token=");
        }
}
