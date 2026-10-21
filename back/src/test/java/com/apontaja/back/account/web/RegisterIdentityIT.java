package com.apontaja.back.account.web;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tranche 12 : prénom et nom obligatoires à l'inscription. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class RegisterIdentityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String payload(String email, String firstName, String lastName) {
        return """
                {"email":"%s","password":"un-mot-de-passe-solide","firstName":"%s","lastName":"%s",
                 "acceptTos":true,"acceptPrivacy":true}
                """.formatted(email, firstName, lastName);
    }

    @Test
    void inscrit_un_compte_et_enregistre_les_noms_sans_espaces_autour() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        register(payload(email, "  Pierre ", " Dupont  ")).andExpect(status().isCreated());

        Account account = accountRepository.findAliveByEmail(email).orElseThrow();
        assertThat(account.getFirstName()).isEqualTo("Pierre");
        assertThat(account.getLastName()).isEqualTo("Dupont");
    }

    @Test
    void refuse_un_prenom_ou_un_nom_absent_ou_blanc() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String withoutFirstName = """
                {"email":"%s","password":"un-mot-de-passe-solide","lastName":"Dupont",
                 "acceptTos":true,"acceptPrivacy":true}
                """.formatted(email);

        register(withoutFirstName).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
        register(payload(email, "Pierre", "   ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists());

        assertThat(accountRepository.existsAliveByEmail(email)).isFalse();
    }

    @Test
    void refuse_un_prenom_ou_un_nom_de_plus_de_100_caracteres() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        register(payload(email, "a".repeat(101), "Dupont")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
        register(payload(email, "Pierre", "d".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists());
    }
}
