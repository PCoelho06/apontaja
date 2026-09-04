package com.apontaja.back.salon.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.organization.domain.OrganizationRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Complète la matrice d'autorisation du domaine salon (Phase 2) sur les deux
 * angles non couverts par les tests des tranches précédentes : accès via
 * OrganizationMembership OWNER sans StaffMembership direct sur les endpoints
 * d'écriture, et cloisonnement strict entre deux organisations distinctes. Le
 * reste de la matrice (rôles staff directs, cross-salon au sein d'une même
 * organisation, dernier OWNER) est déjà couvert dans SalonAuthorizationIT,
 * StaffInvitationControllerIT et StaffMembershipControllerIT — non dupliqué
 * ici.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class SalonAuthorizationMatrixIT {

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
        return organizationRepository.save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
    }

    private void makeOrganizationOwner(UUID accountId, UUID organizationId) {
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));
    }

    private UUID createSalon(UUID organizationId) {
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private UUID addStaff(UUID accountId, UUID salonId, StaffRole role) {
        return staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now())).getId();
    }

    // --- 1. OrganizationMembership OWNER sans StaffMembership direct, endpoints
    // d'écriture ---

    @Test
    void un_organization_owner_sans_staff_membership_peut_inviter_du_staff() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        UUID ownerId = createAccount();
        makeOrganizationOwner(ownerId, organizationId);
        // Aucun StaffMembership direct pour ownerId sur ce salon.

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"nouveau@example.com","role":"MANAGER"}
                        """)).andExpect(status().isCreated());
    }

    @Test
    void un_organization_owner_sans_staff_membership_peut_changer_le_role_d_un_membre() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        UUID ownerId = createAccount();
        makeOrganizationOwner(ownerId, organizationId);
        UUID employeeMembershipId = addStaff(createAccount(), salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(patch("/api/salons/{salonId}/staff/{id}", salonId, employeeMembershipId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"role":"MANAGER"}
                        """)).andExpect(status().isNoContent());
    }

    @Test
    void un_organization_owner_sans_staff_membership_peut_retirer_un_membre() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        UUID ownerId = createAccount();
        makeOrganizationOwner(ownerId, organizationId);
        UUID employeeMembershipId = addStaff(createAccount(), salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonId, employeeMembershipId)
                .header("Authorization", bearerTokenFor(ownerId))).andExpect(status().isNoContent());
    }

    // --- 2. Cloisonnement strict entre deux organisations distinctes ---

    @Test
    void un_organization_owner_n_a_aucun_acces_a_un_salon_d_une_autre_organisation() throws Exception {
        UUID organizationA = createOrganization();
        UUID organizationB = createOrganization();
        UUID salonInOrganizationB = createSalon(organizationB);

        UUID ownerOfA = createAccount();
        makeOrganizationOwner(ownerOfA, organizationA);

        mockMvc.perform(
                get("/api/salons/{salonId}", salonInOrganizationB).header("Authorization", bearerTokenFor(ownerOfA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_organization_owner_ne_peut_pas_inviter_du_staff_sur_un_salon_d_une_autre_organisation() throws Exception {
        UUID organizationA = createOrganization();
        UUID organizationB = createOrganization();
        UUID salonInOrganizationB = createSalon(organizationB);

        UUID ownerOfA = createAccount();
        makeOrganizationOwner(ownerOfA, organizationA);

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonInOrganizationB)
                .header("Authorization", bearerTokenFor(ownerOfA)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"intrus@example.com","role":"EMPLOYEE"}
                        """)).andExpect(status().isForbidden());
    }

    @Test
    void un_organization_owner_ne_peut_pas_gerer_le_staff_d_un_salon_d_une_autre_organisation() throws Exception {
        UUID organizationA = createOrganization();
        UUID organizationB = createOrganization();
        UUID salonInOrganizationB = createSalon(organizationB);
        UUID targetMembershipId = addStaff(createAccount(), salonInOrganizationB, StaffRole.EMPLOYEE);

        UUID ownerOfA = createAccount();
        makeOrganizationOwner(ownerOfA, organizationA);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonInOrganizationB, targetMembershipId)
                .header("Authorization", bearerTokenFor(ownerOfA))).andExpect(status().isForbidden());
    }

    @Test
    void avoir_un_staff_membership_sur_un_salon_d_une_organisation_ne_donne_pas_acces_a_l_organisation_entiere()
            throws Exception {
        UUID organizationA = createOrganization();
        UUID salonA1 = createSalon(organizationA);
        UUID salonA2 = createSalon(organizationA);

        UUID employeeId = createAccount();
        addStaff(employeeId, salonA1, StaffRole.EMPLOYEE);
        // employeeId n'est ni staff de salonA2, ni OrganizationMembership OWNER de
        // organizationA.

        mockMvc.perform(get("/api/salons/{salonId}", salonA2).header("Authorization", bearerTokenFor(employeeId)))
                .andExpect(status().isForbidden());
    }
}
