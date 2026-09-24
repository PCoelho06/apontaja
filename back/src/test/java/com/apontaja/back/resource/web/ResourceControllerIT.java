package com.apontaja.back.resource.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ResourceControllerIT {

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
    private ResourceRepository resourceRepository;

    @Autowired
    private IdGenerator idGenerator;

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

    private UUID staffAccount(UUID salonId, StaffRole role) {
        UUID accountId = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return accountId;
    }

    private UUID createResource(UUID salonId, String name) {
        return resourceRepository
                .save(new Resource(idGenerator.generate(), salonId, name, ResourceType.EMPLOYEE, Instant.now()))
                .getId();
    }

    private String payload(String name, String type) {
        return """
                {"name":"%s","type":"%s"}
                """.formatted(name, type);
    }

    @Test
    void un_owner_cree_une_ressource() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = staffAccount(salonId, StaffRole.OWNER);

        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(payload("  Chaise 1  ", "EMPLOYEE"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceId").exists()).andExpect(jsonPath("$.name").value("Chaise 1"))
                .andExpect(jsonPath("$.type").value("EMPLOYEE"))
                .andExpect(jsonPath("$.salonId").value(salonId.toString()));
    }

    @Test
    void un_manager_cree_une_ressource() throws Exception {
        UUID salonId = createSalon();
        UUID managerId = staffAccount(salonId, StaffRole.MANAGER);

        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId)
                .header("Authorization", bearerTokenFor(managerId)).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Machine", "MACHINE"))).andExpect(status().isCreated());
    }

    @Test
    void un_employee_ne_peut_ni_creer_ni_modifier_ni_supprimer() throws Exception {
        UUID salonId = createSalon();
        UUID employeeId = staffAccount(salonId, StaffRole.EMPLOYEE);
        UUID resourceId = createResource(salonId, "Chaise 1");
        String auth = bearerTokenFor(employeeId);

        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(payload("Nouvelle", "EMPLOYEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Renomme", "EMPLOYEE"))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth)).andExpect(status().isForbidden());
    }

    @Test
    void un_employee_peut_lister_et_lire() throws Exception {
        UUID salonId = createSalon();
        UUID employeeId = staffAccount(salonId, StaffRole.EMPLOYEE);
        UUID resourceId = createResource(salonId, "Chaise 1");
        String auth = bearerTokenFor(employeeId);

        mockMvc.perform(get("/api/salons/{salonId}/resources", salonId).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chaise 1"));
    }

    @Test
    void refuse_un_nom_deja_pris_dans_le_salon() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = staffAccount(salonId, StaffRole.OWNER);
        createResource(salonId, "Chaise 1");

        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Chaise 1", "EMPLOYEE"))).andExpect(status().isConflict());
    }

    @Test
    void refuse_un_type_invalide_et_un_nom_vide() throws Exception {
        UUID salonId = createSalon();
        String auth = bearerTokenFor(staffAccount(salonId, StaffRole.OWNER));

        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(payload("Chaise", "ROBOT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(payload("", "EMPLOYEE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void modifie_une_ressource_et_refuse_le_nom_d_une_autre() throws Exception {
        UUID salonId = createSalon();
        String auth = bearerTokenFor(staffAccount(salonId, StaffRole.OWNER));
        UUID resourceId = createResource(salonId, "Chaise 1");
        createResource(salonId, "Chaise 2");

        mockMvc.perform(put("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Bac", "MACHINE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bac")).andExpect(jsonPath("$.type").value("MACHINE"));

        mockMvc.perform(put("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Chaise 2", "MACHINE"))).andExpect(status().isConflict());
    }

    @Test
    void supprime_puis_libere_le_nom() throws Exception {
        UUID salonId = createSalon();
        String auth = bearerTokenFor(staffAccount(salonId, StaffRole.OWNER));
        UUID resourceId = createResource(salonId, "Chaise 1");

        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(payload("Chaise 1", "EMPLOYEE")))
                .andExpect(status().isCreated());
    }

    @Test
    void une_ressource_d_un_autre_salon_est_introuvable_via_le_chemin_du_salon() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String auth = bearerTokenFor(staffAccount(salonA, StaffRole.OWNER));
        UUID resourceOfB = createResource(salonB, "Chaise B");

        mockMvc.perform(get("/api/salons/{salonId}/resources/{id}", salonA, resourceOfB)
                .header("Authorization", auth)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", salonA, resourceOfB)
                .header("Authorization", auth)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/salons/{salonId}/resources/{id}", salonA, resourceOfB)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Vol", "EMPLOYEE"))).andExpect(status().isNotFound());
    }
}
