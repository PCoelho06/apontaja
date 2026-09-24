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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice d'autorisation des horaires et fermetures : OWNER d'organisation sans
 * staff direct, cloisonnement inter-organisations et inter-salons, sans
 * authentification, salon inexistant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ScheduleAuthorizationMatrixIT {

    private static final String SCHEDULE_BODY = "{\"slots\":[{\"dayOfWeek\":\"MONDAY\","
            + "\"startTime\":\"09:00\",\"endTime\":\"12:00\"}]}";
    private static final String CLOSURE_BODY = "{\"startAt\":\"2026-09-01T00:00:00Z\","
            + "\"endAt\":\"2026-09-02T00:00:00Z\"}";

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

    private String organizationOwnerAuth(UUID organizationId) {
        UUID accountId = createAccount();
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));
        return bearerTokenFor(accountId);
    }

    private UUID createResource(UUID salonId) {
        return resourceRepository.save(new Resource(idGenerator.generate(), salonId, "Chaise",
                ResourceType.EMPLOYEE, Instant.now())).getId();
    }

    @Test
    void un_organization_owner_sans_staff_direct_gere_horaires_et_fermetures() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        UUID resourceId = createResource(salonId);
        String auth = organizationOwnerAuth(organizationId);

        mockMvc.perform(put("/api/salons/{salonId}/schedule", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(SCHEDULE_BODY)).andExpect(status().isOk());
        mockMvc.perform(put("/api/salons/{salonId}/resources/{rid}/schedule", salonId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(SCHEDULE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/salons/{salonId}/closures", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(CLOSURE_BODY)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/salons/{salonId}/resources/{rid}/closures", salonId, resourceId)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(CLOSURE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void un_organization_owner_n_a_aucun_acces_a_une_autre_organisation() throws Exception {
        String auth = organizationOwnerAuth(createOrganization());
        UUID salonInB = createSalon(createOrganization());
        UUID resourceInB = createResource(salonInB);

        mockMvc.perform(get("/api/salons/{salonId}/schedule", salonInB).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/salons/{salonId}/schedule", salonInB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(SCHEDULE_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/salons/{salonId}/resources/{rid}/schedule", salonInB, resourceInB)
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(SCHEDULE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/closures", salonInB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(CLOSURE_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salons/{salonId}/closures", salonInB).header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_staff_d_un_salon_n_a_pas_acces_a_un_autre_salon_de_la_meme_organisation() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonB = createSalon(organizationId);
        UUID ownerOfA = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), ownerOfA, salonA, StaffRole.OWNER, Instant.now()));
        String auth = bearerTokenFor(ownerOfA);

        mockMvc.perform(get("/api/salons/{salonId}/schedule", salonB).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/salons/{salonId}/schedule", salonB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(SCHEDULE_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/closures", salonB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(CLOSURE_BODY)).andExpect(status().isForbidden());
    }

    @Test
    void refuse_sans_authentification_et_pour_un_salon_inexistant() throws Exception {
        UUID salonId = createSalon(createOrganization());
        String someone = bearerTokenFor(createAccount());

        mockMvc.perform(get("/api/salons/{salonId}/schedule", salonId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salons/{salonId}/closures", salonId)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/salons/{salonId}/schedule", UUID.randomUUID()).header("Authorization", someone))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/closures", UUID.randomUUID()).header("Authorization", someone)
                .contentType(MediaType.APPLICATION_JSON).content(CLOSURE_BODY)).andExpect(status().isForbidden());
    }
}
