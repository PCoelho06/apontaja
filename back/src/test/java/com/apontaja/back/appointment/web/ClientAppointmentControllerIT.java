package com.apontaja.back.appointment.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Mes rendez-vous » et annulation par le client. Les RDV sont posés en SQL (statut et dates inatteignables par
 * l'API) ; les dates sont relatives à l'horloge réelle avec des marges de plusieurs heures (exception documentée,
 * comme les ITs de 4c et 4e-1 : l'horloge du contexte n'est pas injectable ici). Chaque test crée son salon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ClientAppointmentControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccessTokenIssuer accessTokenIssuer;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private SalonRepository salonRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private StaffMembershipRepository staffMembershipRepository;

    @MockitoBean
    private EmailSender emailSender;

    private record Fixture(UUID salonId, UUID serviceId, UUID resourceId) {
    }

    private Fixture salon(int cancellationDeadlineHours) {
        Instant now = Instant.now();
        UUID organizationId = UUID.randomUUID();
        organizationRepository.save(new Organization(organizationId, "Org Test", now));
        UUID salonId = UUID.randomUUID();
        salonRepository.save(new Salon(salonId, organizationId, "Salon Test", "1 rue du Test", "06140", "Vence",
                "France", "Europe/Paris", now));
        UUID resourceId = UUID.randomUUID();
        resourceRepository.save(new Resource(resourceId, salonId, "Chaise 1", ResourceType.EMPLOYEE, now));
        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO service (id, salon_id, name, default_duration_minutes, "
                + "default_price_cents) VALUES (?, ?, 'Coupe', 60, 3000)", serviceId, salonId);
        jdbcTemplate.update("INSERT INTO service_resource (service_id, resource_id) VALUES (?, ?)", serviceId,
                resourceId);
        // Réglage courant du salon : la réservation en ligne est désactivée, l'annulation reste régie par lui.
        jdbcTemplate.update("INSERT INTO salon_booking_settings (salon_id, online_booking_enabled, auto_confirm, "
                + "slot_granularity_minutes, min_notice_minutes, horizon_days, cancellation_deadline_hours) "
                + "VALUES (?, false, false, 60, 0, 365, ?)", salonId, cancellationDeadlineHours);
        return new Fixture(salonId, serviceId, resourceId);
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        accountRepository.save(new Account(id, id + "@example.com", "Alice", "Martin", "hash", Instant.now()));
        return id;
    }

    private UUID claimedProfile(UUID accountId) {
        UUID profileId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO customer_profile (id, account_id, first_name, last_name, email) "
                + "VALUES (?, ?, 'Alice', 'Martin', ?)", profileId, accountId, accountId + "@example.com");
        return profileId;
    }

    private UUID appointment(Fixture f, UUID profileId, Instant start, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO appointment (id, salon_id, customer_profile_id, service_id, start_at, "
                + "end_at, status, price_at_booking_cents, duration_at_booking_minutes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 3000, 60)", id, f.salonId(), profileId, f.serviceId(),
                Timestamp.from(start), Timestamp.from(start.plus(Duration.ofHours(1))), status);
        jdbcTemplate.update("INSERT INTO appointment_resource (appointment_id, resource_id) VALUES (?, ?)", id,
                f.resourceId());
        return id;
    }

    private static Instant in(Duration duration) {
        return Instant.now().plus(duration);
    }

    private ResultActions list(UUID accountId, String query) throws Exception {
        var request = get("/api/me/appointments" + query);
        if (accountId != null) {
            request.header("Authorization", "Bearer " + accessTokenIssuer.issue(accountId));
        }
        return mockMvc.perform(request);
    }

    private ResultActions cancel(UUID accountId, String appointmentId) throws Exception {
        var request = patch("/api/me/appointments/" + appointmentId + "/cancel");
        if (accountId != null) {
            request.header("Authorization", "Bearer " + accessTokenIssuer.issue(accountId));
        }
        return mockMvc.perform(request);
    }

    private String statusOf(UUID appointmentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM appointment WHERE id = ?", String.class,
                appointmentId);
    }

    @Test
    void refuse_sans_authentification() throws Exception {
        list(null, "?scope=upcoming").andExpect(status().is4xxClientError());
        cancel(null, UUID.randomUUID().toString()).andExpect(status().is4xxClientError());
    }

    @Test
    void liste_les_rdv_a_venir_du_compte_avec_les_noms_et_les_indicateurs() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID profile = claimedProfile(accountId);
        UUID request = appointment(f, profile, in(Duration.ofDays(3)), "SCHEDULED");
        UUID confirmed = appointment(f, profile, in(Duration.ofDays(5)), "CONFIRMED");
        appointment(f, profile, in(Duration.ofDays(-5)), "COMPLETED");
        appointment(f, claimedProfile(account()), in(Duration.ofDays(4)), "CONFIRMED");

        list(accountId, "?scope=upcoming").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].appointmentId").value(request.toString()))
                .andExpect(jsonPath("$.content[1].appointmentId").value(confirmed.toString()))
                .andExpect(jsonPath("$.content[0].salon.id").value(f.salonId().toString()))
                .andExpect(jsonPath("$.content[0].salon.name").value("Salon Test"))
                .andExpect(jsonPath("$.content[0].salon.city").value("Vence"))
                .andExpect(jsonPath("$.content[0].salon.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.content[0].service.name").value("Coupe"))
                .andExpect(jsonPath("$.content[0].resource.name").value("Chaise 1"))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.content[0].priceCents").value(3000))
                .andExpect(jsonPath("$.content[0].durationMinutes").value(60))
                .andExpect(jsonPath("$.content[0].cancellable").value(true))
                .andExpect(jsonPath("$.content[1].cancellable").value(true))
                .andExpect(jsonPath("$.content[0].customerProfileId").doesNotExist())
                .andExpect(jsonPath("$.content[0].cancelledBy").doesNotExist())
                .andExpect(jsonPath("$.content[0].cancellationReason").doesNotExist());
    }

    @Test
    void liste_les_rdv_passes_dont_annules_du_plus_recent_au_plus_ancien() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID profile = claimedProfile(accountId);
        UUID older = appointment(f, profile, in(Duration.ofDays(-10)), "COMPLETED");
        UUID newer = appointment(f, profile, in(Duration.ofDays(-2)), "NO_SHOW");
        UUID cancelled = appointment(f, profile, in(Duration.ofDays(6)), "CANCELLED");
        appointment(f, profile, in(Duration.ofDays(3)), "CONFIRMED");

        list(accountId, "?scope=past").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].appointmentId").value(cancelled.toString()))
                .andExpect(jsonPath("$.content[1].appointmentId").value(newer.toString()))
                .andExpect(jsonPath("$.content[2].appointmentId").value(older.toString()))
                .andExpect(jsonPath("$.content[0].cancellable").value(false))
                .andExpect(jsonPath("$.content[2].cancellable").value(false));
    }

    @Test
    void pagine_la_liste() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID profile = claimedProfile(accountId);
        for (int day = 2; day <= 4; day++) {
            appointment(f, profile, in(Duration.ofDays(day)), "CONFIRMED");
        }

        list(accountId, "?scope=upcoming&page=1&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void un_compte_sans_rdv_obtient_une_liste_vide() throws Exception {
        list(account(), "?scope=upcoming").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()")
                .value(0)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void refuse_un_scope_absent_ou_inconnu_et_des_parametres_de_page_illisibles() throws Exception {
        UUID accountId = account();

        list(accountId, "").andExpect(status().isBadRequest());
        list(accountId, "?scope=all").andExpect(status().isBadRequest());
        list(accountId, "?scope=upcoming&page=abc").andExpect(status().isBadRequest());
        list(accountId, "?scope=upcoming&size=xyz").andExpect(status().isBadRequest());
    }

    @Test
    void annule_une_demande_non_validee_libere_le_creneau_et_previent_le_client() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID id = appointment(f, claimedProfile(accountId), in(Duration.ofHours(3)), "SCHEDULED");

        cancel(accountId, id.toString()).andExpect(status().isOk()).andExpect(jsonPath("$.status")
                .value("CANCELLED")).andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.cancelledAt").exists());

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT cancelled_by FROM appointment WHERE id = ?", UUID.class, id))
                .isEqualTo(accountId);
        assertThat(jdbcTemplate.queryForObject("SELECT cancellation_reason FROM appointment WHERE id = ?",
                String.class, id)).isEqualTo("Annulé par le client");
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM appointment_resource WHERE appointment_id = ?",
                Boolean.class, id)).isFalse();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(accountId + "@example.com"), subject.capture(), any());
        assertThat(subject.getValue()).isEqualTo("Marcação cancelada - Salon Test");
    }

    @Test
    void le_rdv_annule_passe_de_a_venir_a_passe() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID id = appointment(f, claimedProfile(accountId), in(Duration.ofDays(3)), "CONFIRMED");

        cancel(accountId, id.toString()).andExpect(status().isOk());

        list(accountId, "?scope=upcoming").andExpect(jsonPath("$.totalElements").value(0));
        list(accountId, "?scope=past").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    @Test
    void refuse_l_annulation_d_un_rdv_confirme_hors_delai_avec_le_code_stable() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID id = appointment(f, claimedProfile(accountId), in(Duration.ofHours(3)), "CONFIRMED");

        cancel(accountId, id.toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_DEADLINE_PASSED"));

        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void le_delai_courant_du_salon_s_applique_meme_si_la_reservation_en_ligne_est_desactivee() throws Exception {
        Fixture f = salon(0);
        UUID accountId = account();
        UUID id = appointment(f, claimedProfile(accountId), in(Duration.ofHours(3)), "CONFIRMED");

        cancel(accountId, id.toString()).andExpect(status().isOk());

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }

    @Test
    void refuse_les_statuts_non_annulables_et_une_demande_deja_commencee_avec_le_code_stable() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID profile = claimedProfile(accountId);
        UUID alreadyCancelled = appointment(f, profile, in(Duration.ofDays(3)), "CANCELLED");
        UUID completed = appointment(f, profile, in(Duration.ofDays(-3)), "COMPLETED");
        UUID started = appointment(f, profile, in(Duration.ofMinutes(-30)), "SCHEDULED");

        for (UUID id : new UUID[] { alreadyCancelled, completed, started }) {
            cancel(accountId, id.toString()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
        }
        assertThat(statusOf(started)).isEqualTo("SCHEDULED");
    }

    @Test
    void le_rdv_d_un_autre_compte_ou_inconnu_ou_mal_forme_donne_le_meme_404() throws Exception {
        Fixture f = salon(24);
        UUID owner = account();
        UUID intruder = account();
        UUID id = appointment(f, claimedProfile(owner), in(Duration.ofDays(3)), "SCHEDULED");

        cancel(intruder, id.toString()).andExpect(status().isNotFound());
        cancel(owner, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        cancel(owner, "pas-un-uuid").andExpect(status().isNotFound());

        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    void un_membre_du_staff_du_salon_ne_peut_pas_annuler_le_rdv_d_un_client_par_cette_route() throws Exception {
        Fixture f = salon(24);
        UUID client = account();
        UUID id = appointment(f, claimedProfile(client), in(Duration.ofDays(3)), "SCHEDULED");
        UUID staff = account();
        staffMembershipRepository
                .save(new StaffMembership(UUID.randomUUID(), staff, f.salonId(), StaffRole.OWNER, Instant.now()));

        cancel(staff, id.toString()).andExpect(status().isNotFound());
        list(staff, "?scope=upcoming").andExpect(jsonPath("$.totalElements").value(0));

        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    void un_echec_d_envoi_de_l_email_n_empeche_pas_l_annulation() throws Exception {
        Fixture f = salon(24);
        UUID accountId = account();
        UUID id = appointment(f, claimedProfile(accountId), in(Duration.ofDays(3)), "CONFIRMED");
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(any(), any(), any());

        cancel(accountId, id.toString()).andExpect(status().isOk());

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }
}
