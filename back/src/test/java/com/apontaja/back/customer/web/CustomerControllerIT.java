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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class CustomerControllerIT {

    private static final String CUSTOMERS = "/api/salons/{salonId}/customers";
    private static final String CUSTOMER = CUSTOMERS + "/{id}";

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

    private UUID createAccount() {
        return accountRepository
                .save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", Instant.now()))
                .getId();
    }

    private String bearerTokenFor(UUID accountId) {
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private UUID createSalon() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private String authOf(UUID salonId, StaffRole role) {
        UUID accountId = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return bearerTokenFor(accountId);
    }

    private static String body(String firstName, String lastName, String email, String phone) {
        String emailField = email == null ? "null" : "\"" + email + "\"";
        String phoneField = phone == null ? "null" : "\"" + phone + "\"";
        return """
                {"firstName":"%s","lastName":"%s","email":%s,"phone":%s,"internalNotes":"Allergie"}
                """.formatted(firstName, lastName, emailField, phoneField);
    }

    @Test
    void un_employee_peut_creer_lister_et_lire_un_client() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.EMPLOYEE);

        String responseBody = mockMvc
                .perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" Alice ", " Martin ", "alice@example.com", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customerProfileId").exists())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andReturn().getResponse().getContentAsString();

        String customerId = objectMapper.readTree(responseBody).get("customerProfileId").asText();

        mockMvc.perform(get(CUSTOMERS, salonId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(CUSTOMER, salonId, customerId).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lastName").value("Martin"));
    }

    @Test
    void refuse_un_client_sans_email_ni_telephone() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);

        mockMvc.perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Bob", "Dupont", null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accepte_un_client_avec_seulement_un_telephone() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);

        mockMvc.perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Bob", "Dupont", null, "0612345678")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.phone").value("0612345678"));
    }

    @Test
    void refuse_des_champs_obligatoires_vides_ou_un_email_invalide() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);

        mockMvc.perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("", "Dupont", "x@example.com", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.firstName").exists());
        mockMvc.perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Bob", "Dupont", "pas-un-email", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void un_client_d_un_autre_salon_est_introuvable_via_le_chemin_du_salon() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String authA = authOf(salonA, StaffRole.OWNER);
        String authB = authOf(salonB, StaffRole.OWNER);

        String responseBody = mockMvc
                .perform(post(CUSTOMERS, salonB).header("Authorization", authB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Carol", "B", "carol@example.com", null)))
                .andReturn().getResponse().getContentAsString();
        String customerOfB = objectMapper.readTree(responseBody).get("customerProfileId").asText();

        mockMvc.perform(get(CUSTOMER, salonA, customerOfB).header("Authorization", authA))
                .andExpect(status().isNotFound());
    }

    @Test
    void deux_creations_pour_le_meme_email_ne_sont_jamais_fusionnees() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);

        mockMvc.perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Dave", "A", "dave@example.com", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Dave", "B", "dave@example.com", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(CUSTOMERS, salonId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
