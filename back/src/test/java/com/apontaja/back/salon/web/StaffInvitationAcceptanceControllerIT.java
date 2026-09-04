package com.apontaja.back.salon.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffInvitation;
import com.apontaja.back.salon.domain.StaffInvitationRepository;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.shared.domain.TokenHasher;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class StaffInvitationAcceptanceControllerIT {

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
        private StaffInvitationRepository staffInvitationRepository;

        @Autowired
        private TokenHasher tokenHasher;

        @Autowired
        private IdGenerator idGenerator;

        private UUID createAccountWithEmail(String email) {
                return accountRepository.save(new Account(UUID.randomUUID(), email, "hash", Instant.now())).getId();
        }

        private String bearerTokenFor(UUID accountId) {
                return "Bearer " + accessTokenIssuer.issue(accountId);
        }

        private UUID createSalon(String name) {
                UUID organizationId = organizationRepository
                                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
                return salonRepository.save(new Salon(idGenerator.generate(), organizationId, name, "1 rue du Test",
                                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        }

        /**
         * Insère une invitation directement en base avec un token brut connu — la seule
         * façon de tester le chemin complet, le token brut n'étant jamais renvoyé par
         * l'API (voir StaffInvitationService.createInvitation, même principe que la
         * vérification email).
         */
        private StaffInvitation createPendingInvitation(UUID salonId, String email, StaffRole role, String rawToken) {
                return staffInvitationRepository.save(new StaffInvitation(idGenerator.generate(), salonId, email, role,
                                null, tokenHasher.hash(rawToken), Instant.now().plus(7, ChronoUnit.DAYS),
                                Instant.now()));
        }

        @Test
        void accepte_avec_le_bon_token_cree_le_staff_membership_et_marque_l_invitation_acceptee() throws Exception {
                UUID salonId = createSalon("Salon Test");
                String email = "invite-" + UUID.randomUUID() + "@example.com";
                String rawToken = "raw-token-" + UUID.randomUUID();
                createPendingInvitation(salonId, email, StaffRole.MANAGER, rawToken);
                UUID accountId = createAccountWithEmail(email);

                mockMvc.perform(post("/api/staff-invitations/accept").header("Authorization", bearerTokenFor(accountId))
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"token":"%s"}
                                                """.formatted(rawToken))).andExpect(status().isNoContent());

                assertThat(staffMembershipRepository.findAliveByAccountIdAndSalonId(accountId, salonId))
                                .hasValueSatisfying(membership -> assertThat(membership.getRole())
                                                .isEqualTo(StaffRole.MANAGER));

                Optional<StaffInvitation> invitation = staffInvitationRepository
                                .findByTokenHash(tokenHasher.hash(rawToken));
                assertThat(invitation).hasValueSatisfying(inv -> assertThat(inv.isAccepted()).isTrue());
        }

        @Test
        void refuse_une_seconde_acceptation_du_meme_token() throws Exception {
                UUID salonId = createSalon("Salon Test");
                String email = "invite-" + UUID.randomUUID() + "@example.com";
                String rawToken = "raw-token-" + UUID.randomUUID();
                createPendingInvitation(salonId, email, StaffRole.EMPLOYEE, rawToken);
                UUID accountId = createAccountWithEmail(email);
                String body = """
                                {"token":"%s"}
                                """.formatted(rawToken);

                mockMvc.perform(post("/api/staff-invitations/accept").header("Authorization", bearerTokenFor(accountId))
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isNoContent());

                mockMvc.perform(post("/api/staff-invitations/accept").header("Authorization", bearerTokenFor(accountId))
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refuse_l_acceptation_par_un_compte_dont_l_email_ne_correspond_pas() throws Exception {
                UUID salonId = createSalon("Salon Test");
                String email = "invite-" + UUID.randomUUID() + "@example.com";
                String rawToken = "raw-token-" + UUID.randomUUID();
                createPendingInvitation(salonId, email, StaffRole.EMPLOYEE, rawToken);
                UUID unrelatedAccountId = createAccountWithEmail("quelqu-un-d-autre@example.com");

                mockMvc.perform(post("/api/staff-invitations/accept")
                                .header("Authorization", bearerTokenFor(unrelatedAccountId))
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"token":"%s"}
                                                """.formatted(rawToken))).andExpect(status().isForbidden());

                assertThat(staffMembershipRepository.findAliveByAccountIdAndSalonId(unrelatedAccountId, salonId))
                                .isEmpty();
        }

        @Test
        void refuse_l_acceptation_sans_authentification() throws Exception {
                mockMvc.perform(post("/api/staff-invitations/accept").contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"token":"peu-importe"}
                                                """)).andExpect(status().isForbidden());
        }

        @Test
        void refuse_un_token_inconnu() throws Exception {
                UUID accountId = createAccountWithEmail("peu-importe@example.com");

                mockMvc.perform(post("/api/staff-invitations/accept").header("Authorization", bearerTokenFor(accountId))
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                                {"token":"token-inexistant"}
                                                """)).andExpect(status().isBadRequest());
        }

        @Test
        void lookup_retourne_les_details_avec_accountExists_true_si_le_compte_existe_deja() throws Exception {
                UUID salonId = createSalon("Salon Visible");
                String email = "invite-" + UUID.randomUUID() + "@example.com";
                String rawToken = "raw-token-" + UUID.randomUUID();
                createPendingInvitation(salonId, email, StaffRole.OWNER, rawToken);
                createAccountWithEmail(email);

                mockMvc.perform(get("/api/staff-invitations/{token}", rawToken)).andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value(email))
                                .andExpect(jsonPath("$.role").value("OWNER"))
                                .andExpect(jsonPath("$.salonName").value("Salon Visible"))
                                .andExpect(jsonPath("$.accountExists").value(true));
        }

        @Test
        void lookup_retourne_accountExists_false_si_aucun_compte_pour_cet_email() throws Exception {
                UUID salonId = createSalon("Salon Test");
                String rawToken = "raw-token-" + UUID.randomUUID();
                createPendingInvitation(salonId, "pas-encore-inscrit@example.com", StaffRole.EMPLOYEE, rawToken);

                mockMvc.perform(get("/api/staff-invitations/{token}", rawToken)).andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountExists").value(false));
        }

        @Test
        void lookup_refuse_un_token_inconnu() throws Exception {
                mockMvc.perform(get("/api/staff-invitations/{token}", "token-inexistant"))
                                .andExpect(status().isBadRequest());
        }
}
