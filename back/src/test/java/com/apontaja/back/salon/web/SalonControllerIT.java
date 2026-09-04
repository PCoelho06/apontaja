package com.apontaja.back.salon.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class SalonControllerIT {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private AccountRepository accountRepository;

        @Autowired
        private AccessTokenIssuer accessTokenIssuer;

        private final ObjectMapper objectMapper = new ObjectMapper();

        private String bearerTokenForNewAccount() {
                Account account = accountRepository.save(new Account(UUID.randomUUID(),
                                UUID.randomUUID() + "@example.com", "hash", Instant.now()));
                return "Bearer " + accessTokenIssuer.issue(account.getId());
        }

        private String salonPayload(String name) {
                return """
                                {"name":"%s","address":"1 rue du Test","postalCode":"06140",
                                 "city":"Vence","country":"France","timezone":"Europe/Paris"}
                                """.formatted(name);
        }

        @Test
        void cree_un_salon_avec_organisation_et_staff_membership_owner() throws Exception {
                String token = bearerTokenForNewAccount();

                mockMvc.perform(post("/api/salons").header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON).content(salonPayload("Salon Test")))
                                .andExpect(status().isCreated()).andExpect(jsonPath("$.salonId").exists())
                                .andExpect(jsonPath("$.organizationId").exists());
        }

        @Test
        void deux_salons_du_meme_compte_partagent_la_meme_organisation() throws Exception {
                String token = bearerTokenForNewAccount();

                String firstBody = mockMvc
                                .perform(post("/api/salons").header("Authorization", token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(salonPayload("Salon A")))
                                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

                String secondBody = mockMvc
                                .perform(post("/api/salons").header("Authorization", token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(salonPayload("Salon B")))
                                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

                String orgA = objectMapper.readTree(firstBody).get("organizationId").asText();
                String orgB = objectMapper.readTree(secondBody).get("organizationId").asText();

                assertThat(orgA).isEqualTo(orgB);
        }

        @Test
        void rejette_la_creation_sans_authentification() throws Exception {
                mockMvc.perform(post("/api/salons").contentType(MediaType.APPLICATION_JSON)
                                .content(salonPayload("Salon Test"))).andExpect(status().isForbidden());
        }

        @Test
        void rejette_une_requete_invalide() throws Exception {
                String token = bearerTokenForNewAccount();

                mockMvc.perform(post("/api/salons").header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isBadRequest());
        }
}
