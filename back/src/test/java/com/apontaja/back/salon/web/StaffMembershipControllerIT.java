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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class StaffMembershipControllerIT {

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

    private UUID addStaff(UUID accountId, UUID salonId, StaffRole role) {
        return staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now())).getId();
    }

    private String rolePayload(String role) {
        return """
                {"role":"%s"}
                """.formatted(role);
    }

    @Test
    void un_employee_peut_lister_le_staff() throws Exception {
        UUID salonId = createSalon();
        UUID employeeId = createAccount();
        addStaff(employeeId, salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(get("/api/salons/{salonId}/staff", salonId).header("Authorization", bearerTokenFor(employeeId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void un_owner_peut_promouvoir_un_employee_en_manager() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        addStaff(ownerId, salonId, StaffRole.OWNER);
        UUID employeeMembershipId = addStaff(createAccount(), salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(patch("/api/salons/{salonId}/staff/{id}", salonId, employeeMembershipId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(rolePayload("MANAGER"))).andExpect(status().isNoContent());

        assertThat(staffMembershipRepository.findAliveById(employeeMembershipId))
                .hasValueSatisfying(m -> assertThat(m.getRole()).isEqualTo(StaffRole.MANAGER));
    }

    @Test
    void un_manager_ne_peut_pas_promouvoir_un_employee_en_manager() throws Exception {
        UUID salonId = createSalon();
        UUID managerId = createAccount();
        addStaff(managerId, salonId, StaffRole.MANAGER);
        UUID employeeMembershipId = addStaff(createAccount(), salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(patch("/api/salons/{salonId}/staff/{id}", salonId, employeeMembershipId)
                .header("Authorization", bearerTokenFor(managerId)).contentType(MediaType.APPLICATION_JSON)
                .content(rolePayload("MANAGER"))).andExpect(status().isForbidden());
    }

    @Test
    void un_manager_peut_retirer_un_employee() throws Exception {
        UUID salonId = createSalon();
        UUID managerId = createAccount();
        addStaff(managerId, salonId, StaffRole.MANAGER);
        UUID employeeMembershipId = addStaff(createAccount(), salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonId, employeeMembershipId)
                .header("Authorization", bearerTokenFor(managerId))).andExpect(status().isNoContent());

        assertThat(staffMembershipRepository.findAliveById(employeeMembershipId)).isEmpty();
    }

    @Test
    void un_manager_ne_peut_pas_retirer_un_owner() throws Exception {
        UUID salonId = createSalon();
        UUID managerId = createAccount();
        addStaff(managerId, salonId, StaffRole.MANAGER);
        UUID ownerMembershipId = addStaff(createAccount(), salonId, StaffRole.OWNER);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonId, ownerMembershipId).header("Authorization",
                bearerTokenFor(managerId))).andExpect(status().isForbidden());
    }

    @Test
    void un_employee_ne_peut_rien_modifier() throws Exception {
        UUID salonId = createSalon();
        UUID employeeId = createAccount();
        addStaff(employeeId, salonId, StaffRole.EMPLOYEE);
        UUID otherEmployeeMembershipId = addStaff(createAccount(), salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonId, otherEmployeeMembershipId)
                .header("Authorization", bearerTokenFor(employeeId))).andExpect(status().isForbidden());
    }

    @Test
    void refuse_de_retirer_le_dernier_owner() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        UUID ownerMembershipId = addStaff(ownerId, salonId, StaffRole.OWNER);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonId, ownerMembershipId).header("Authorization",
                bearerTokenFor(ownerId))).andExpect(status().isConflict());

        assertThat(staffMembershipRepository.findAliveById(ownerMembershipId)).isPresent();
    }

    @Test
    void refuse_de_retrograder_le_dernier_owner() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        UUID ownerMembershipId = addStaff(ownerId, salonId, StaffRole.OWNER);

        mockMvc.perform(patch("/api/salons/{salonId}/staff/{id}", salonId, ownerMembershipId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(rolePayload("MANAGER"))).andExpect(status().isConflict());
    }

    @Test
    void autorise_le_retrait_d_un_owner_si_un_autre_owner_reste() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        addStaff(ownerId, salonId, StaffRole.OWNER);
        UUID secondOwnerMembershipId = addStaff(createAccount(), salonId, StaffRole.OWNER);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonId, secondOwnerMembershipId)
                .header("Authorization", bearerTokenFor(ownerId))).andExpect(status().isNoContent());
    }

    @Test
    void refuse_une_action_sur_un_membre_d_un_autre_salon() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        UUID ownerA = createAccount();
        addStaff(ownerA, salonA, StaffRole.OWNER);
        UUID membershipOnSalonB = addStaff(createAccount(), salonB, StaffRole.EMPLOYEE);

        mockMvc.perform(delete("/api/salons/{salonId}/staff/{id}", salonA, membershipOnSalonB).header("Authorization",
                bearerTokenFor(ownerA))).andExpect(status().isForbidden());
    }

    @Test
    void refuse_sans_authentification() throws Exception {
        UUID salonId = createSalon();

        mockMvc.perform(get("/api/salons/{salonId}/staff", salonId)).andExpect(status().isForbidden());
    }
}
