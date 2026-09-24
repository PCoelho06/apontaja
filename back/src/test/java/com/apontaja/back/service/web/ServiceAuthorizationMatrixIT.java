package com.apontaja.back.service.web;

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
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice d'autorisation du catalogue "prestations" : OWNER d'organisation sans
 * staff direct, cloisonnement inter-organisations et inter-salons, sans
 * authentification, salon inexistant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ServiceAuthorizationMatrixIT {

    private static final String BODY = """
            {"name":"Coupe","defaultDurationMinutes":30,"defaultPriceCents":2500}
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
    private SalonServiceRepository serviceRepository;

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

    private String organizationOwnerAuth(UUID organizationId) {
        UUID accountId = createAccount();
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));
        return bearerTokenFor(accountId);
    }

    private UUID createService(UUID salonId) {
        return serviceRepository
                .save(new SalonService(idGenerator.generate(), salonId, "Existante", null, 30, 2500, Instant.now()))
                .getId();
    }

    private UUID createResource(UUID salonId) {
        return resourceRepository.save(
                new Resource(idGenerator.generate(), salonId, "Chaise", ResourceType.EMPLOYEE, Instant.now()))
                .getId();
    }

    @Test
    void un_organization_owner_sans_staff_direct_gere_les_prestations_et_associations() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        String auth = organizationOwnerAuth(organizationId);
        UUID serviceId = createService(salonId);
        UUID resourceId = createResource(salonId);

        mockMvc.perform(post("/api/salons/{salonId}/services", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/salons/{salonId}/services/{id}/resources/{rid}", salonId, serviceId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/salons/{salonId}/services/{id}", salonId, serviceId)
                .header("Authorization", auth)).andExpect(status().isNoContent());
    }

    @Test
    void un_organization_owner_n_a_aucun_acces_aux_prestations_d_une_autre_organisation() throws Exception {
        String auth = organizationOwnerAuth(createOrganization());
        UUID salonInB = createSalon(createOrganization());
        UUID serviceId = createService(salonInB);
        UUID resourceId = createResource(salonInB);

        mockMvc.perform(get("/api/salons/{salonId}/services", salonInB).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/services", salonInB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/salons/{salonId}/services/{id}", salonInB, serviceId)
                .header("Authorization", auth)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/salons/{salonId}/services/{id}/resources/{rid}", salonInB, serviceId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_staff_d_un_salon_n_a_pas_acces_aux_prestations_d_un_autre_salon_de_la_meme_organisation()
            throws Exception {
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonB = createSalon(organizationId);
        UUID ownerOfA = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), ownerOfA, salonA, StaffRole.OWNER, Instant.now()));
        String auth = bearerTokenFor(ownerOfA);

        mockMvc.perform(get("/api/salons/{salonId}/services", salonB).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/services", salonB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }

    @Test
    void refuse_sans_authentification_et_pour_un_salon_inexistant() throws Exception {
        UUID salonId = createSalon(createOrganization());

        mockMvc.perform(get("/api/salons/{salonId}/services", salonId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salons/{salonId}/services", UUID.randomUUID())
                .header("Authorization", bearerTokenFor(createAccount()))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/services", UUID.randomUUID())
                .header("Authorization", bearerTokenFor(createAccount())).contentType(MediaType.APPLICATION_JSON)
                .content(BODY)).andExpect(status().isForbidden());
    }
}
