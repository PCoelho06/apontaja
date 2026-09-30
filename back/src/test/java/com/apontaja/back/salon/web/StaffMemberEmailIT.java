package com.apontaja.back.salon.web;

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

/** Tranche 8c : la liste du staff expose l'email de chaque membre à tout le staff du salon. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class StaffMemberEmailIT {

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

    private UUID accountWithEmail(String email) {
        return accountRepository.save(new Account(UUID.randomUUID(), email, "hash", Instant.now())).getId();
    }

    private void join(UUID accountId, UUID salonId, StaffRole role) {
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
    }

    @Test
    void la_liste_du_staff_expose_l_email_de_chaque_membre_meme_a_un_employee() throws Exception {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        UUID salonId = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        String employeeEmail = "employee-" + UUID.randomUUID() + "@example.com";
        UUID ownerAccount = accountWithEmail(ownerEmail);
        UUID employeeAccount = accountWithEmail(employeeEmail);
        join(ownerAccount, salonId, StaffRole.OWNER);
        join(employeeAccount, salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(get("/api/salons/{salonId}/staff", salonId).header("Authorization",
                "Bearer " + accessTokenIssuer.issue(employeeAccount))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.accountId == '%s')].email".formatted(ownerAccount)).value(ownerEmail))
                .andExpect(jsonPath("$[?(@.accountId == '%s')].email".formatted(employeeAccount))
                        .value(employeeEmail));
    }
}
