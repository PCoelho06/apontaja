package com.apontaja.back.customer.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class CustomerDuplicateControllerIT {

    private static final String CUSTOMERS = "/api/salons/{salonId}/customers";
    private static final String DUPLICATES = CUSTOMERS + "/duplicates";
    private static final String MERGE = CUSTOMERS + "/merge";

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

    private String customerBody(String firstName, String email, String phone) {
        String emailField = email == null ? "null" : "\"" + email + "\"";
        String phoneField = phone == null ? "null" : "\"" + phone + "\"";
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Test\",\"email\":" + emailField + ",\"phone\":"
                + phoneField + ",\"internalNotes\":\"Note " + firstName + "\"}";
    }

    private UUID createCustomer(UUID salonId, String auth, String firstName, String email, String phone)
            throws Exception {
        String response = mockMvc
                .perform(post(CUSTOMERS, salonId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(customerBody(firstName, email, phone)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("customerProfileId").asText());
    }

    private String mergeBody(UUID survivorId, UUID... absorbedIds) {
        StringBuilder ids = new StringBuilder();
        for (UUID id : absorbedIds) {
            ids.append(ids.length() == 0 ? "" : ",").append('"').append(id).append('"');
        }
        return "{\"survivorId\":\"" + survivorId + "\",\"absorbedIds\":[" + ids + "]}";
    }

    @Test
    void un_employee_ne_peut_ni_lister_les_doublons_ni_fusionner() throws Exception {
        UUID salonId = createSalon();
        String employee = authOf(salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(get(DUPLICATES, salonId).header("Authorization", employee)).andExpect(status().isForbidden());
        mockMvc.perform(post(MERGE, salonId).header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void sans_authentification_autre_salon_et_salon_inexistant() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String managerB = authOf(salonB, StaffRole.MANAGER);

        mockMvc.perform(get(DUPLICATES, salonA)).andExpect(status().is4xxClientError());
        mockMvc.perform(get(DUPLICATES, salonA).header("Authorization", managerB)).andExpect(status().isForbidden());
        mockMvc.perform(get(DUPLICATES, UUID.randomUUID()).header("Authorization", managerB))
                .andExpect(status().isForbidden());
    }

    @Test
    void detecte_les_doublons_par_email_sans_tenir_compte_de_la_casse() throws Exception {
        UUID salonId = createSalon();
        String manager = authOf(salonId, StaffRole.MANAGER);
        createCustomer(salonId, manager, "Alice", "alice@example.com", null);
        createCustomer(salonId, manager, "Alicia", "ALICE@example.com", null);
        createCustomer(salonId, manager, "Bob", "bob@example.com", null);

        mockMvc.perform(get(DUPLICATES, salonId).header("Authorization", manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].reasons[0]").value("EMAIL"))
                .andExpect(jsonPath("$.groups[0].customers.length()").value(2))
                .andExpect(jsonPath("$.groups[0].customers[0].mergeable").value(true))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void fusionne_dans_le_survivant_et_retire_l_absorbe_du_carnet() throws Exception {
        UUID salonId = createSalon();
        String manager = authOf(salonId, StaffRole.MANAGER);
        UUID survivor = createCustomer(salonId, manager, "Alice", "alice@example.com", null);
        UUID absorbed = createCustomer(salonId, manager, "Alicia", "alice@example.com", "0612345678");

        mockMvc.perform(post(MERGE, salonId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(survivor, absorbed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerProfileId").value(survivor.toString()))
                .andExpect(jsonPath("$.phone").value("0612345678"))
                .andExpect(jsonPath("$.internalNotes").value("Note Alice\n\nNote Alicia"));

        mockMvc.perform(get(CUSTOMERS, salonId).header("Authorization", manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get(CUSTOMERS + "/{id}", salonId, absorbed).header("Authorization", manager))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(DUPLICATES, salonId).header("Authorization", manager))
                .andExpect(jsonPath("$.groups.length()").value(0));
    }

    @Test
    void refuse_une_demande_incoherente_ou_un_client_inconnu() throws Exception {
        UUID salonId = createSalon();
        String manager = authOf(salonId, StaffRole.MANAGER);
        UUID survivor = createCustomer(salonId, manager, "Alice", "alice@example.com", null);

        mockMvc.perform(post(MERGE, salonId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(survivor, survivor)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(MERGE, salonId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(survivor)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(MERGE, salonId).header("Authorization", manager)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(survivor, UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse_de_fusionner_un_client_d_un_autre_salon_ou_anonymise() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String managerA = authOf(salonA, StaffRole.MANAGER);
        String managerB = authOf(salonB, StaffRole.MANAGER);
        UUID survivor = createCustomer(salonA, managerA, "Alice", "alice@example.com", null);
        UUID ofOtherSalon = createCustomer(salonB, managerB, "Bob", "bob@example.com", null);
        UUID toAnonymize = createCustomer(salonA, managerA, "Carol", "carol@example.com", null);

        mockMvc.perform(post(MERGE, salonA).header("Authorization", managerA)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(survivor, ofOtherSalon)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(CUSTOMERS + "/{id}", salonA, toAnonymize).header("Authorization", managerA))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(MERGE, salonA).header("Authorization", managerA)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(survivor, toAnonymize)))
                .andExpect(status().isConflict());
    }
}
