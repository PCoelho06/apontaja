package com.apontaja.back.customer.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Carnet client (Phase 4a) : modification, consentement, anonymisation, recherche paginée, historique. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class CustomerBookControllerIT {

    private static final String CUSTOMERS = "/api/salons/{salonId}/customers";
    private static final String CUSTOMER = CUSTOMERS + "/{id}";
    private static final String CONSENT = CUSTOMER + "/marketing-consent";
    private static final String HISTORY = CUSTOMER + "/appointments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private SalonRepository salonRepository;

    @Autowired
    private StaffMembershipRepository staffMembershipRepository;

    @Autowired
    private IdGenerator idGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID createSalon() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private String authOf(UUID salonId, StaffRole role) {
        UUID accountId = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Test", "User", "hash", Instant.now())).getId();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private static String body(String firstName, String lastName, String email, String phone) {
        String emailField = email == null ? "null" : "\"" + email + "\"";
        String phoneField = phone == null ? "null" : "\"" + phone + "\"";
        return """
                {"firstName":"%s","lastName":"%s","email":%s,"phone":%s,"internalNotes":"Note"}
                """.formatted(firstName, lastName, emailField, phoneField);
    }

    private String createCustomer(UUID salonId, String auth, String firstName, String lastName, String email)
            throws Exception {
        String response = mockMvc
                .perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(firstName, lastName, email, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("customerProfileId").asText();
    }

    private JsonNode getCustomer(UUID salonId, String auth, String customerId) throws Exception {
        String response = mockMvc.perform(get(CUSTOMER, salonId, customerId).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private static boolean isAbsentOrNull(JsonNode node, String field) {
        return node.path(field).isMissingNode() || node.path(field).isNull();
    }

    @Test
    void un_employee_modifie_un_client_par_remplacement_complet() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.EMPLOYEE);
        String customerId = createCustomer(salonId, auth, "Alice", "Martin", "alice@example.com");

        mockMvc.perform(put(CUSTOMER, salonId, customerId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body(" Alicia ", "Durand", null, "0612345678")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Alicia"))
                .andExpect(jsonPath("$.lastName").value("Durand")).andExpect(jsonPath("$.phone").value("0612345678"));

        assertThat(isAbsentOrNull(getCustomer(salonId, auth, customerId), "email")).isTrue();
    }

    @Test
    void la_modification_refuse_un_client_sans_contact_et_un_client_inconnu() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        String customerId = createCustomer(salonId, auth, "Alice", "Martin", "alice@example.com");

        mockMvc.perform(put(CUSTOMER, salonId, customerId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Alice", "Martin", null, null)))
                .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("CUSTOMER_CONTACT_REQUIRED"));
        mockMvc.perform(put(CUSTOMER, salonId, UUID.randomUUID()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Alice", "Martin", "a@example.com", null)))
                .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void le_consentement_marketing_est_donne_puis_retire() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.EMPLOYEE);
        String customerId = createCustomer(salonId, auth, "Alice", "Martin", "alice@example.com");

        assertThat(getCustomer(salonId, auth, customerId).get("marketingConsent").asBoolean()).isFalse();

        mockMvc.perform(patch(CONSENT, salonId, customerId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"consented\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marketingConsent").value(true))
                .andExpect(jsonPath("$.marketingConsentAt").exists());
        mockMvc.perform(patch(CONSENT, salonId, customerId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"consented\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marketingConsent").value(false))
                .andExpect(jsonPath("$.marketingConsentRevokedAt").exists());
        mockMvc.perform(patch(CONSENT, salonId, customerId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void seul_un_manager_ou_owner_anonymise_et_le_client_reste_lisible_mais_fige() throws Exception {
        UUID salonId = createSalon();
        String employee = authOf(salonId, StaffRole.EMPLOYEE);
        String manager = authOf(salonId, StaffRole.MANAGER);
        String customerId = createCustomer(salonId, employee, "Alice", "Martin", "alice@example.com");

        mockMvc.perform(delete(CUSTOMER, salonId, customerId).header("Authorization", employee))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(CUSTOMER, salonId, customerId).header("Authorization", manager))
                .andExpect(status().isNoContent());
        // Idempotent.
        mockMvc.perform(delete(CUSTOMER, salonId, customerId).header("Authorization", manager))
                .andExpect(status().isNoContent());

        JsonNode anonymized = getCustomer(salonId, employee, customerId);
        assertThat(anonymized.get("anonymized").asBoolean()).isTrue();
        assertThat(anonymized.get("firstName").asText()).isNotEqualTo("Alice");
        assertThat(isAbsentOrNull(anonymized, "email")).isTrue();
        assertThat(isAbsentOrNull(anonymized, "internalNotes")).isTrue();

        mockMvc.perform(put(CUSTOMER, salonId, customerId).header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON).content(body("Alice", "Martin", "a@example.com", null)))
                .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("CUSTOMER_ANONYMIZED"));
        mockMvc.perform(get(CUSTOMERS, salonId).header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void la_liste_est_paginee_triee_et_filtrable() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        createCustomer(salonId, auth, "Carol", "Zola", "carol@example.com");
        createCustomer(salonId, auth, "Alice", "Martin", "alice@example.com");
        createCustomer(salonId, auth, "Bob", "Martin", "bob@example.com");

        mockMvc.perform(get(CUSTOMERS, salonId).param("size", "2").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].firstName").value("Alice"))
                .andExpect(jsonPath("$.content[1].firstName").value("Bob"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(CUSTOMERS, salonId).param("size", "2").param("page", "1").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Carol"));
        mockMvc.perform(get(CUSTOMERS, salonId).param("q", "zol").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Zola"));
    }

    @Test
    void un_nouveau_client_n_a_ni_visite_ni_historique() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.EMPLOYEE);
        String customerId = createCustomer(salonId, auth, "Alice", "Martin", "alice@example.com");

        JsonNode customer = getCustomer(salonId, auth, customerId);
        assertThat(isAbsentOrNull(customer, "lastVisitAt")).isTrue();
        assertThat(isAbsentOrNull(customer, "nextVisitAt")).isTrue();
        mockMvc.perform(get(HISTORY, salonId, customerId).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void l_historique_d_un_client_d_un_autre_salon_est_introuvable() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String authA = authOf(salonA, StaffRole.OWNER);
        String customerOfB = createCustomer(salonB, authOf(salonB, StaffRole.OWNER), "Carol", "B", "c@example.com");

        mockMvc.perform(get(HISTORY, salonA, customerOfB).header("Authorization", authA))
                .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void un_staff_d_un_autre_salon_ne_peut_ni_modifier_ni_anonymiser_ni_consulter() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String ownerOfB = authOf(salonB, StaffRole.OWNER);
        String customerId = createCustomer(salonA, authOf(salonA, StaffRole.OWNER), "Alice", "Martin",
                "alice@example.com");

        mockMvc.perform(put(CUSTOMER, salonA, customerId).header("Authorization", ownerOfB)
                .contentType(MediaType.APPLICATION_JSON).content(body("A", "B", "a@example.com", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(CONSENT, salonA, customerId).header("Authorization", ownerOfB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"consented\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(CUSTOMER, salonA, customerId).header("Authorization", ownerOfB))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(HISTORY, salonA, customerId).header("Authorization", ownerOfB))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(CUSTOMER, salonA, customerId)).andExpect(status().isForbidden());
    }
}



