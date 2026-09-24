package com.apontaja.back.resource.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.organization.domain.OrganizationRole;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice d'autorisation du catalogue "ressources" : OrganizationMembership
 * OWNER sans staff direct (accès via l'organisation), cloisonnement
 * inter-organisations, inter-salons, et absence d'authentification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ResourceAuthorizationMatrixIT {

    private static final String BODY = """
            {"name":"Chaise","type":"EMPLOYEE"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

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

    private UUID createOrganization() {
        return organizationRepository.save(new Organization(idGenerator.generate(), "Org Test", Instant.now()))
                .getId();
    }

    private UUID createSalon(UUID organizationId) {
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private UUID organizationOwner(UUID organizationId) {
        UUID accountId = createAccount();
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));
        return accountId;
    }

    private UUID createResource(UUID salonId) {
        return resourceRepository.save(
                new Resource(idGenerator.generate(), salonId, "Existante", ResourceType.EMPLOYEE, Instant.now()))
                .getId();
    }

    @Test
    void un_organization_owner_sans_staff_direct_gere_les_ressources() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        String auth = bearerTokenFor(organizationOwner(organizationId));
        UUID resourceId = createResource(salonId);

        mockMvc.perform(post("/api/salons/{salonId}/resources", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/salons/{salonId}/resources", salonId).header("Authorization", auth))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth)).andExpect(status().isNoContent());
    }

    @Test
    void un_organization_owner_n_a_aucun_acces_aux_ressources_d_une_autre_organisation() throws Exception {
        UUID organizationA = createOrganization();
        UUID salonInB = createSalon(createOrganization());
        String auth = bearerTokenFor(organizationOwner(organizationA));
        UUID resourceId = createResource(salonInB);

        mockMvc.perform(get("/api/salons/{salonId}/resources", salonInB).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/resources", salonInB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", salonInB, resourceId)
                .header("Authorization", auth)).andExpect(status().isForbidden());
    }

    @Test
    void un_staff_d_un_salon_n_a_pas_acces_aux_ressources_d_un_autre_salon_de_la_meme_organisation()
            throws Exception {
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonB = createSalon(organizationId);
        UUID ownerOfA = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), ownerOfA, salonA, StaffRole.OWNER, Instant.now()));
        String auth = bearerTokenFor(ownerOfA);

        mockMvc.perform(get("/api/salons/{salonId}/resources", salonB).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/resources", salonB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }

    @Test
    void refuse_sans_authentification_et_pour_un_salon_inexistant() throws Exception {
        UUID salonId = createSalon(createOrganization());

        mockMvc.perform(get("/api/salons/{salonId}/resources", salonId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salons/{salonId}/resources", UUID.randomUUID())
                .header("Authorization", bearerTokenFor(createAccount()))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/resources", UUID.randomUUID())
                .header("Authorization", bearerTokenFor(createAccount())).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isForbidden());
    }
}
