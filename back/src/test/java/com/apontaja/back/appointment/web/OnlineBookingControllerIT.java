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
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Réservation en ligne. Le salon est ouvert le lundi de 08:00 à 20:00 (Paris), prestation de 60 min, granularité
 * 60 min, préavis 0, horizon 365 j. Chaque test crée son propre salon (aucun nettoyage nécessaire). Le lundi visé
 * est calculé à partir de l'horloge réelle (≥ 14 jours) : exception documentée, comme l'IT public de 4c.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class OnlineBookingControllerIT {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

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

    private record Fixture(UUID salonId, UUID serviceId, UUID oldResourceId, UUID newResourceId) {
    }

    private static String monday(int hour) {
        LocalDate date = LocalDate.now(PARIS).plusDays(14);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(hour, 0).atZone(PARIS).toOffsetDateTime().toString();
    }

    private Fixture salon(boolean enabled, boolean autoConfirm) {
        Instant now = Instant.now();
        UUID organizationId = UUID.randomUUID();
        organizationRepository.save(new Organization(organizationId, "Org Test", now));
        UUID salonId = UUID.randomUUID();
        salonRepository.save(new Salon(salonId, organizationId, "Salon Test", "1 rue du Test", "06140", "Vence",
                "France", "Europe/Paris", now));
        UUID oldResource = UUID.randomUUID();
        UUID newResource = UUID.randomUUID();
        resourceRepository
                .save(new Resource(oldResource, salonId, "Chaise 1", ResourceType.EMPLOYEE, now.minusSeconds(100)));
        resourceRepository.save(new Resource(newResource, salonId, "Chaise 2", ResourceType.EMPLOYEE, now));

        // Prestation, association, horaires et réglages : tables inatteignables sans les API staff, posées en SQL.
        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO service (id, salon_id, name, default_duration_minutes, "
                + "default_price_cents) VALUES (?, ?, 'Coupe', 60, 3000)", serviceId, salonId);
        jdbcTemplate.update("INSERT INTO service_resource (service_id, resource_id) VALUES (?, ?)", serviceId,
                oldResource);
        jdbcTemplate.update("INSERT INTO service_resource (service_id, resource_id) VALUES (?, ?)", serviceId,
                newResource);
        jdbcTemplate.update("INSERT INTO schedule (id, salon_id, day_of_week, start_time, end_time) "
                + "VALUES (?, ?, 1, TIME '08:00', TIME '20:00')", UUID.randomUUID(), salonId);
        jdbcTemplate.update("INSERT INTO salon_booking_settings (salon_id, online_booking_enabled, auto_confirm, "
                + "slot_granularity_minutes, min_notice_minutes, horizon_days, cancellation_deadline_hours) "
                + "VALUES (?, ?, ?, 60, 0, 365, 24)", salonId, enabled, autoConfirm);
        return new Fixture(salonId, serviceId, oldResource, newResource);
    }

    private UUID account(boolean verified) {
        UUID id = UUID.randomUUID();
        Account account = new Account(id, id + "@example.com", "Alice", "Martin", "hash", Instant.now());
        if (verified) {
            account.markEmailVerified(Instant.now());
        }
        accountRepository.save(account);
        return id;
    }

    private ResultActions book(UUID accountId, UUID salonId, UUID serviceId, UUID resourceId, String startAt,
            boolean consent) throws Exception {
        String resource = resourceId == null ? "null" : "\"" + resourceId + "\"";
        String body = "{\"serviceId\":\"" + serviceId + "\",\"resourceId\":" + resource + ",\"startAt\":\"" + startAt
                + "\",\"phone\":\"0600000000\",\"shareContactConsent\":" + consent + "}";
        var request = post("/api/me/salons/" + salonId + "/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (accountId != null) {
            request.header("Authorization", "Bearer " + accessTokenIssuer.issue(accountId));
        }
        return mockMvc.perform(request);
    }

    @Test
    void refuse_sans_authentification() throws Exception {
        Fixture f = salon(true, false);

        book(null, f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), true)
                .andExpect(status().is4xxClientError());
    }

    @Test
    void refuse_un_compte_dont_l_email_n_est_pas_verifie() throws Exception {
        Fixture f = salon(true, false);

        book(account(false), f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), true)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void donne_404_uniforme_pour_salon_inconnu_reservation_desactivee_ou_prestation_etrangere() throws Exception {
        Fixture disabled = salon(false, false);
        Fixture enabled = salon(true, false);
        UUID accountId = account(true);

        book(accountId, UUID.randomUUID(), disabled.serviceId(), null, monday(10), true)
                .andExpect(status().isNotFound());
        book(accountId, disabled.salonId(), disabled.serviceId(), null, monday(10), true)
                .andExpect(status().isNotFound());
        book(accountId, enabled.salonId(), disabled.serviceId(), null, monday(10), true)
                .andExpect(status().isNotFound());
        book(accountId, enabled.salonId(), enabled.serviceId(), UUID.randomUUID(), monday(10), true)
                .andExpect(status().isNotFound());
    }

    @Test
    void exige_le_consentement_de_partage_a_la_premiere_reservation() throws Exception {
        Fixture f = salon(true, false);
        UUID accountId = account(true);

        book(accountId, f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SHARING_CONSENT_REQUIRED"));

        Integer appointments = jdbcTemplate.queryForObject("SELECT count(*) FROM appointment WHERE salon_id = ?",
                Integer.class, f.salonId());
        assertThat(appointments).isZero();
    }

    @Test
    void cree_une_demande_scheduled_et_un_profil_self_puis_reutilise_le_profil() throws Exception {
        Fixture f = salon(true, false);
        UUID accountId = account(true);

        String json = book(accountId, f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), true)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.resourceName").value("Chaise 1"))
                .andExpect(jsonPath("$.priceCents").value(3000)).andReturn().getResponse().getContentAsString();
        UUID appointmentId = UUID.fromString(JsonPath.read(json, "$.appointmentId"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment WHERE id = ?", String.class,
                appointmentId)).isEqualTo("SCHEDULED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM salon_customer_link l JOIN customer_profile p "
                + "ON p.id = l.customer_profile_id WHERE l.salon_id = ? AND p.account_id = ? AND l.source = 'SELF'",
                Integer.class, f.salonId(), accountId)).isEqualTo(1);

        // Deuxième réservation : le profil existe, le consentement n'est plus exigé et aucun profil n'est créé.
        book(accountId, f.salonId(), f.serviceId(), f.oldResourceId(), monday(12), false)
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer_profile WHERE account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
    }

    @Test
    void confirme_directement_quand_le_salon_confirme_automatiquement() throws Exception {
        Fixture f = salon(true, true);

        book(account(true), f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), true)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void refuse_un_creneau_deja_pris_ou_hors_horaires_avec_le_code_stable() throws Exception {
        Fixture f = salon(true, false);

        book(account(true), f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), true)
                .andExpect(status().isCreated());
        book(account(true), f.salonId(), f.serviceId(), f.oldResourceId(), monday(10), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        book(account(true), f.salonId(), f.serviceId(), f.oldResourceId(), monday(22), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
    }

    @Test
    void sans_preference_attribue_la_ressource_la_moins_chargee_puis_l_autre() throws Exception {
        Fixture f = salon(true, false);

        // Les deux ressources sont libres et sans charge : la plus ancienne est choisie.
        book(account(true), f.salonId(), f.serviceId(), null, monday(10), true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceId").value(f.oldResourceId().toString()));
        // Même début : la première est prise, la seconde est attribuée.
        book(account(true), f.salonId(), f.serviceId(), null, monday(10), true).andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceId").value(f.newResourceId().toString()));
        // Plus aucune ressource libre à ce début.
        book(account(true), f.salonId(), f.serviceId(), null, monday(10), true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
    }

    @Test
    void refuse_le_sixieme_rendez_vous_a_venir_du_meme_client_dans_le_salon() throws Exception {
        Fixture f = salon(true, false);
        UUID accountId = account(true);

        for (int hour = 8; hour < 13; hour++) {
            book(accountId, f.salonId(), f.serviceId(), f.oldResourceId(), monday(hour), true)
                    .andExpect(status().isCreated());
        }
        book(accountId, f.salonId(), f.serviceId(), f.oldResourceId(), monday(14), true)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOKING_QUOTA_EXCEEDED"));
    }
}
