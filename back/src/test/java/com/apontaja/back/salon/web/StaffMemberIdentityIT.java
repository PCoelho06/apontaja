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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tranche 12 : la liste du staff expose le nom d'affichage à tout le staff, et l'email seulement aux
 * OWNER/MANAGER (remplace StaffMemberEmailIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class StaffMemberIdentityIT {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Member(UUID accountId, String email) {
    }

    private record Team(UUID salonId, Member owner, Member manager, Member employee) {
    }

    private Member join(UUID salonId, String firstName, String lastName, StaffRole role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        UUID accountId = accountRepository
                .save(new Account(UUID.randomUUID(), email, firstName, lastName, "hash", Instant.now())).getId();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return new Member(accountId, email);
    }

    private Team team() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        UUID salonId = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        Member owner = join(salonId, "Pierre", "Dupont", StaffRole.OWNER);
        Member manager = join(salonId, "Marie", "curie", StaffRole.MANAGER);
        Member employee = join(salonId, "Léa", "Martin", StaffRole.EMPLOYEE);
        return new Team(salonId, owner, manager, employee);
    }

    private ResultActions listAs(Team team, Member actor) throws Exception {
        return mockMvc.perform(get("/api/salons/{salonId}/staff", team.salonId()).header("Authorization",
                "Bearer " + accessTokenIssuer.issue(actor.accountId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    private static String byAccount(String property, Member member) {
        return "$[?(@.accountId == '%s')].%s".formatted(member.accountId(), property);
    }

    @Test
    void un_employee_voit_les_noms_d_affichage_mais_aucun_email() throws Exception {
        Team team = team();

        String body = listAs(team, team.employee())
                .andExpect(jsonPath(byAccount("displayName", team.owner())).value("Pierre D."))
                .andExpect(jsonPath(byAccount("displayName", team.manager())).value("Marie C."))
                .andExpect(jsonPath(byAccount("displayName", team.employee())).value("Léa M.")).andReturn()
                .getResponse().getContentAsString();

        for (JsonNode member : objectMapper.readTree(body)) {
            JsonNode email = member.path("email");
            assertThat(email.isMissingNode() || email.isNull()).isTrue();
        }
    }

    @Test
    void un_owner_et_un_manager_voient_aussi_les_emails() throws Exception {
        Team team = team();

        for (Member actor : new Member[] { team.owner(), team.manager() }) {
            listAs(team, actor).andExpect(jsonPath(byAccount("email", team.owner())).value(team.owner().email()))
                    .andExpect(jsonPath(byAccount("email", team.employee())).value(team.employee().email()))
                    .andExpect(jsonPath(byAccount("displayName", team.owner())).value("Pierre D."));
        }
    }
}
