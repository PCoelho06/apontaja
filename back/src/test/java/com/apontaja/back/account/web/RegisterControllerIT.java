package com.apontaja.back.account.web;

import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RegisterControllerIT {

        @Autowired
        private MockMvc mockMvc;

        private static String validRegisterBody(String email) {
                return """
                                {
                                  "email": "%s",
                                  "password": "un-mot-de-passe-suffisamment-long"
                                }
                                """.formatted(email);
        }

        @Test
        void register_cree_le_compte_et_repond_201() throws Exception {
                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                                .content(validRegisterBody("eve@example.com"))).andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accountId").exists())
                                .andExpect(jsonPath("$.email").value("eve@example.com"));
        }

        @Test
        void register_repond_409_si_email_deja_utilise() throws Exception {
                String body = validRegisterBody("frank@example.com");

                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isConflict());
        }

        @Test
        void register_repond_400_si_mot_de_passe_trop_court() throws Exception {
                String body = """
                                {
                                  "email": "gwen@example.com",
                                  "password": "court"
                                }
                                """;

                mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
}
