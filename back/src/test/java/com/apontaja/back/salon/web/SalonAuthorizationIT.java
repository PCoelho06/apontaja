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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class SalonAuthorizationIT {

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
        Account account = accountRepository
                .save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", Instant.now()));
        return account.getId();
    }

    private String bearerTokenFor(UUID accountId) {
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private UUID createOrganization() {
        Organization organization = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now()));
        return organization.getId();
    }

    private UUID createSalon(UUID organizationId) {
        Salon salon = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now()));
        return salon.getId();
    }

    @Test
    void accede_au_salon_via_staff_membership_direct() throws Exception {
        UUID accountId = createAccount();
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        staffMembershipRepository.save(
                new StaffMembership(idGenerator.generate(), accountId, salonId, StaffRole.EMPLOYEE, Instant.now()));

        mockMvc.perform(get("/api/salons/{salonId}", salonId).header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isOk());
    }

    @Test
    void accede_au_salon_via_organization_membership_owner_sans_staff_membership_direct() throws Exception {
        UUID accountId = createAccount();
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));

        mockMvc.perform(get("/api/salons/{salonId}", salonId).header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isOk());
    }

    @Test
    void refuse_un_compte_sans_lien_avec_le_salon() throws Exception {
        UUID accountId = createAccount();
        UUID salonId = createSalon(createOrganization());

        mockMvc.perform(get("/api/salons/{salonId}", salonId).header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse_un_staff_membership_sur_un_autre_salon() throws Exception {
        UUID accountId = createAccount();
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonB = createSalon(organizationId);
        staffMembershipRepository.save(
                new StaffMembership(idGenerator.generate(), accountId, salonA, StaffRole.EMPLOYEE, Instant.now()));

        mockMvc.perform(get("/api/salons/{salonId}", salonB).header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse_sans_authentification() throws Exception {
        UUID salonId = createSalon(createOrganization());

        mockMvc.perform(get("/api/salons/{salonId}", salonId)).andExpect(status().isForbidden());
    }

    @Test
    void refuse_un_salon_inexistant_meme_pour_un_compte_authentifie() throws Exception {
        UUID accountId = createAccount();

        mockMvc.perform(
                get("/api/salons/{salonId}", UUID.randomUUID()).header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isForbidden());
    }
}
