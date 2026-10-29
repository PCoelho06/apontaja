package com.apontaja.back.account.web;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RegisterControllerIT {

        private static final String VERIFICATION_SUBJECT = "Confirme o seu endereço de email";

        private static final String ACCOUNT_EXISTS_SUBJECT = "Já tem uma conta na Apontaja";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AccountRepository accountRepository;

        @MockitoBean
        private EmailSender emailSender;

        private static String registerBody(String email, String firstName) {
                return """
                                {
                                  "email": "%s",
                                  "password": "un-mot-de-passe-suffisamment-long",
                                  "firstName":"%s",
                                  "lastName":"Martin",
                                  "acceptTos":true,
                                  "acceptPrivacy":true
                                }
                                """.formatted(email, firstName);
        }

        @Test
        void register_cree_le_compte_et_repond_202_sans_corps() throws Exception {
                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("eve@example.com", "Alice"))).andExpect(status().isAccepted())
                                .andExpect(content().string(""));

                assertThat(accountRepository.findAliveByEmail("eve@example.com")).isPresent();
                verify(emailSender).send(eq("eve@example.com"), eq(VERIFICATION_SUBJECT), anyString());
        }

        @Test
        void register_repond_pareil_si_email_deja_utilise_sans_rien_modifier() throws Exception {
                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("frank@example.com", "Alice"))).andExpect(status().isAccepted());
                clearInvocations(emailSender);

                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody("frank@example.com", "Intrus")))
                                .andExpect(status().isAccepted()).andExpect(content().string(""));

                Account stored = accountRepository.findAliveByEmail("frank@example.com").orElseThrow();
                assertThat(stored.getFirstName()).isEqualTo("Alice");
                verify(emailSender, times(1)).send(eq("frank@example.com"), eq(ACCOUNT_EXISTS_SUBJECT), anyString());
                verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
        }

        @Test
        void register_repond_400_si_mot_de_passe_trop_court() throws Exception {
                String body = """
                                {
                                  "email": "gwen@example.com",
                                  "password": "court",
                                  "firstName":"Alice",
                                  "lastName":"Martin",
                                  "acceptTos":true,
                                  "acceptPrivacy":true
                                }
                                """;

                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
}
