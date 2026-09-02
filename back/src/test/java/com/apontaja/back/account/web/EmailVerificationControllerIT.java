package com.apontaja.back.account.web;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class EmailVerificationControllerIT {

    static final String RATE_LIMITING_DISABLED = "apontaja.security.rate-limiting.enabled=false";

    @Autowired
    private MockMvc mockMvc;

    private String registerAndReturnEmail() throws Exception {
        String email = "verif-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "email": "%s",
                  "password": "un-mot-de-passe-suffisamment-long",
                  "acceptTos": true,
                  "acceptPrivacy": true
                }
                """.formatted(email))).andExpect(status().isCreated());

        return email;
    }

    @Test
    void confirm_email_avec_un_token_bidon_repond_400() throws Exception {
        registerAndReturnEmail();

        mockMvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON).content("""
                {"token": "ce-token-n-existe-pas"}
                """)).andExpect(status().isBadRequest());
    }

    @Test
    void resend_verification_email_repond_toujours_204_meme_pour_un_email_inconnu() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification-email").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "personne-nexiste-pas@example.com"}
                """)).andExpect(status().isNoContent());
    }

    @Test
    void resend_verification_email_repond_204_pour_un_email_existant() throws Exception {
        String email = registerAndReturnEmail();

        mockMvc.perform(post("/api/auth/resend-verification-email").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s"}
                """.formatted(email))).andExpect(status().isNoContent());
    }
}
