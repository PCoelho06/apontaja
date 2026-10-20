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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le cas « OWNER d'organisation sans staff direct » n'est pas rejoué ici : il relève de
 * CatalogManagementGuard / SalonAccessGuard, déjà testés, et ce contrôleur n'ajoute aucune règle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class BookingSettingsControllerIT {

    private static final String URL = "/api/salons/{salonId}/booking-settings";
    private static final String VALID = json(true, false, 30, 60, 90, 12);

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

    private static String json(boolean enabled, boolean autoConfirm, int granularity, int minNotice, int horizon,
            int cancellation) {
        return ("{\"onlineBookingEnabled\":%s,\"autoConfirm\":%s,\"slotGranularityMinutes\":%d,"
                + "\"minNoticeMinutes\":%d,\"horizonDays\":%d,\"cancellationDeadlineHours\":%d}")
                .formatted(enabled, autoConfirm, granularity, minNotice, horizon, cancellation);
    }

    private UUID createOrganization() {
        return organizationRepository.save(new Organization(idGenerator.generate(), "Org Test", Instant.now()))
                .getId();
    }

    private UUID createSalon(UUID organizationId) {
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private String authOf(UUID salonId, StaffRole role) {
        UUID accountId = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Test", "User", "hash", Instant.now())).getId();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    @Test
    void sans_configuration_tout_le_staff_lit_les_valeurs_par_defaut() throws Exception {
        UUID salonId = createSalon(createOrganization());

        for (StaffRole role : StaffRole.values()) {
            mockMvc.perform(get(URL, salonId).header("Authorization", authOf(salonId, role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.onlineBookingEnabled").value(false))
                    .andExpect(jsonPath("$.autoConfirm").value(false))
                    .andExpect(jsonPath("$.slotGranularityMinutes").value(15))
                    .andExpect(jsonPath("$.minNoticeMinutes").value(120))
                    .andExpect(jsonPath("$.horizonDays").value(60))
                    .andExpect(jsonPath("$.cancellationDeadlineHours").value(24));
        }
    }

    @Test
    void un_owner_puis_un_manager_remplacent_les_reglages_et_les_relisent() throws Exception {
        UUID salonId = createSalon(createOrganization());

        mockMvc.perform(put(URL, salonId).header("Authorization", authOf(salonId, StaffRole.OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.onlineBookingEnabled").value(true))
                .andExpect(jsonPath("$.slotGranularityMinutes").value(30));
        mockMvc.perform(get(URL, salonId).header("Authorization", authOf(salonId, StaffRole.EMPLOYEE)))
                .andExpect(jsonPath("$.horizonDays").value(90))
                .andExpect(jsonPath("$.cancellationDeadlineHours").value(12));

        mockMvc.perform(put(URL, salonId).header("Authorization", authOf(salonId, StaffRole.MANAGER))
                .contentType(MediaType.APPLICATION_JSON).content(json(false, true, 60, 0, 1, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.autoConfirm").value(true));
        mockMvc.perform(get(URL, salonId).header("Authorization", authOf(salonId, StaffRole.OWNER)))
                .andExpect(jsonPath("$.onlineBookingEnabled").value(false))
                .andExpect(jsonPath("$.slotGranularityMinutes").value(60))
                .andExpect(jsonPath("$.horizonDays").value(1));
    }

    @Test
    void un_employee_ne_peut_pas_modifier_et_les_reglages_restent_inchanges() throws Exception {
        UUID salonId = createSalon(createOrganization());

        mockMvc.perform(put(URL, salonId).header("Authorization", authOf(salonId, StaffRole.EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isForbidden());

        mockMvc.perform(get(URL, salonId).header("Authorization", authOf(salonId, StaffRole.OWNER)))
                .andExpect(jsonPath("$.onlineBookingEnabled").value(false));
    }

    @Test
    void refuse_les_valeurs_hors_bornes_et_les_champs_manquants() throws Exception {
        UUID salonId = createSalon(createOrganization());
        String auth = authOf(salonId, StaffRole.OWNER);
        String[] bodies = {json(true, false, 7, 60, 90, 12), json(true, false, 30, -1, 90, 12),
                json(true, false, 30, 10_081, 90, 12), json(true, false, 30, 60, 0, 12),
                json(true, false, 30, 60, 366, 12), json(true, false, 30, 60, 90, -1),
                json(true, false, 30, 60, 90, 721), "{}",
                "{\"onlineBookingEnabled\":true,\"autoConfirm\":false}"};

        for (String body : bodies) {
            mockMvc.perform(put(URL, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(URL, salonId).header("Authorization", auth))
                .andExpect(jsonPath("$.onlineBookingEnabled").value(false));
    }

    @Test
    void les_reglages_d_un_salon_ne_fuient_pas_vers_un_autre() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonB = createSalon(organizationId);

        mockMvc.perform(put(URL, salonA).header("Authorization", authOf(salonA, StaffRole.OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isOk());

        mockMvc.perform(get(URL, salonB).header("Authorization", authOf(salonB, StaffRole.OWNER)))
                .andExpect(jsonPath("$.onlineBookingEnabled").value(false));
    }

    @Test
    void matrice_d_autorisation_autre_salon_autre_organisation_salon_inexistant_sans_authentification()
            throws Exception {
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonSameOrg = createSalon(organizationId);
        UUID salonOtherOrg = createSalon(createOrganization());

        String ownerOfSameOrgSalon = authOf(salonSameOrg, StaffRole.OWNER);
        String ownerOfOtherOrgSalon = authOf(salonOtherOrg, StaffRole.OWNER);

        for (String auth : new String[] {ownerOfSameOrgSalon, ownerOfOtherOrgSalon}) {
            mockMvc.perform(get(URL, salonA).header("Authorization", auth)).andExpect(status().isForbidden());
            mockMvc.perform(put(URL, salonA).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                    .content(VALID)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL, UUID.randomUUID()).header("Authorization", ownerOfSameOrgSalon))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(URL, UUID.randomUUID()).header("Authorization", ownerOfSameOrgSalon)
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isForbidden());

        // Sans jeton : refus (le code exact, 401 ou 403, dépend de l'entry point configuré).
        mockMvc.perform(get(URL, salonA)).andExpect(status().is4xxClientError());
        mockMvc.perform(put(URL, salonA).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().is4xxClientError());
    }
}
