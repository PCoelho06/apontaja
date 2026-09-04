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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class SalonListingIT {

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
        return organizationRepository.save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
    }

    private UUID createSalon(UUID organizationId, String name) {
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, name, "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    @Test
    void liste_les_salons_via_staff_membership_et_via_organization_owner_dedupliques() throws Exception {
        UUID accountId = createAccount();
        UUID organizationId = createOrganization();
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));

        UUID salonWithStaff = createSalon(organizationId, "Salon Staff");
        staffMembershipRepository.save(new StaffMembership(idGenerator.generate(), accountId, salonWithStaff,
                StaffRole.EMPLOYEE, Instant.now()));

        createSalon(organizationId, "Salon Sans Staff");
        // aucun StaffMembership pour salonWithoutStaff -> accès uniquement via
        // OrganizationMembership OWNER

        mockMvc.perform(get("/api/salons").header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void ne_liste_pas_les_salons_sans_lien() throws Exception {
        UUID accountId = createAccount();
        createSalon(createOrganization(), "Salon Étranger");

        mockMvc.perform(get("/api/salons").header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void respecte_la_pagination() throws Exception {
        UUID accountId = createAccount();
        UUID organizationId = createOrganization();
        for (int i = 0; i < 3; i++) {
            UUID salonId = createSalon(organizationId, "Salon " + i);
            staffMembershipRepository.save(
                    new StaffMembership(idGenerator.generate(), accountId, salonId, StaffRole.OWNER, Instant.now()));
        }

        mockMvc.perform(get("/api/salons?page=0&size=2").header("Authorization", bearerTokenFor(accountId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void rejette_sans_authentification() throws Exception {
        mockMvc.perform(get("/api/salons")).andExpect(status().isForbidden());
    }
}
