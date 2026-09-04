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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class StaffInvitationControllerIT {

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

    private void addStaff(UUID accountId, UUID salonId, StaffRole role) {
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
    }

    private String invitationPayload(String email, String role) {
        return """
                {"email":"%s","role":"%s"}
                """.formatted(email, role);
    }

    @Test
    void un_owner_peut_inviter_un_manager() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        addStaff(ownerId, salonId, StaffRole.OWNER);

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(invitationPayload("nouveau@example.com", "MANAGER"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").exists());
    }

    @Test
    void un_manager_ne_peut_pas_inviter_un_autre_manager() throws Exception {
        UUID salonId = createSalon();
        UUID managerId = createAccount();
        addStaff(managerId, salonId, StaffRole.MANAGER);

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(managerId)).contentType(MediaType.APPLICATION_JSON)
                .content(invitationPayload("nouveau@example.com", "MANAGER"))).andExpect(status().isForbidden());
    }

    @Test
    void un_manager_peut_inviter_un_employee() throws Exception {
        UUID salonId = createSalon();
        UUID managerId = createAccount();
        addStaff(managerId, salonId, StaffRole.MANAGER);

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(managerId)).contentType(MediaType.APPLICATION_JSON)
                .content(invitationPayload("nouveau@example.com", "EMPLOYEE"))).andExpect(status().isCreated());
    }

    @Test
    void un_employee_ne_peut_inviter_personne() throws Exception {
        UUID salonId = createSalon();
        UUID employeeId = createAccount();
        addStaff(employeeId, salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(employeeId)).contentType(MediaType.APPLICATION_JSON)
                .content(invitationPayload("nouveau@example.com", "EMPLOYEE"))).andExpect(status().isForbidden());
    }

    @Test
    void refuse_une_seconde_invitation_en_attente_pour_le_meme_email() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        addStaff(ownerId, salonId, StaffRole.OWNER);
        String payload = invitationPayload("dup@example.com", "EMPLOYEE");

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andExpect(status().isConflict());
    }

    @Test
    void un_employee_peut_lister_les_invitations_en_attente() throws Exception {
        UUID salonId = createSalon();
        UUID ownerId = createAccount();
        addStaff(ownerId, salonId, StaffRole.OWNER);
        mockMvc.perform(post("/api/salons/{salonId}/staff/invitations", salonId)
                .header("Authorization", bearerTokenFor(ownerId)).contentType(MediaType.APPLICATION_JSON)
                .content(invitationPayload("visible@example.com", "EMPLOYEE"))).andExpect(status().isCreated());

        UUID employeeId = createAccount();
        addStaff(employeeId, salonId, StaffRole.EMPLOYEE);

        mockMvc.perform(get("/api/salons/{salonId}/staff/invitations", salonId).header("Authorization",
                bearerTokenFor(employeeId))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("visible@example.com"));
    }
}
