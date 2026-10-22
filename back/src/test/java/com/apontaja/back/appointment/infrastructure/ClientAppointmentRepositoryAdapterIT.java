package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.ClientAppointmentRow;
import com.apontaja.back.appointment.domain.ClientAppointmentRows;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Mes RDV » : SQL natif joignant customer_profile.account_id. Horloge de référence 2099-01-01 : les RDV de 2099-06
 * sont à venir, ceux de 2020 sont passés.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, ClientAppointmentRepositoryAdapter.class })
class ClientAppointmentRepositoryAdapterIT {

    private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2099-06-10T08:00:00Z");
    private static final Instant PAST = Instant.parse("2020-06-10T08:00:00Z");

    @Autowired
    private ClientAppointmentRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    private record Fixture(UUID salonId, UUID serviceId, UUID resourceId) {
    }

    private Fixture fixture() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        Resource resource = new Resource(UUID.randomUUID(), salon.getId(), "Chaise 1", ResourceType.EMPLOYEE,
                Instant.now());
        entityManager.persistAndFlush(resource);
        UUID serviceId = UUID.randomUUID();
        // Table service : entité d'un autre domaine, insérée en SQL natif.
        entityManager.getEntityManager().createNativeQuery("INSERT INTO service (id, salon_id, name, "
                + "default_duration_minutes, default_price_cents) VALUES (:id, :salonId, 'Coupe', 60, 3000)")
                .setParameter("id", serviceId).setParameter("salonId", salon.getId()).executeUpdate();
        return new Fixture(salon.getId(), serviceId, resource.getId());
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        entityManager.persistAndFlush(new Account(id, id + "@example.com", "Alice", "Martin", "hash",
                Instant.now()));
        return id;
    }

    private UUID profile(UUID accountId) {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), "Alice", "Martin",
                UUID.randomUUID() + "@example.com", null, Instant.now());
        if (accountId != null) {
            profile.claimBy(accountId);
        }
        entityManager.persistAndFlush(profile);
        return profile.getId();
    }

    /** Statuts finaux posés par les mutateurs (un statut initial ne peut être que SCHEDULED ou CONFIRMED). */
    private UUID appointment(Fixture f, UUID profileId, Instant start, AppointmentStatus status) {
        AppointmentStatus initial = status == AppointmentStatus.SCHEDULED ? AppointmentStatus.SCHEDULED
                : AppointmentStatus.CONFIRMED;
        Appointment appointment = new Appointment(UUID.randomUUID(), f.salonId(), profileId, f.serviceId(), start,
                start.plusSeconds(3600), 3000, 60, Instant.now(), initial);
        switch (status) {
            case CANCELLED -> appointment.cancel(Instant.parse("2098-12-01T00:00:00Z"), null, "x");
            case COMPLETED -> appointment.complete();
            case NO_SHOW -> appointment.markNoShow();
            default -> {
            }
        }
        entityManager.persistAndFlush(appointment);
        entityManager.persistAndFlush(new AppointmentResource(appointment.getId(), f.resourceId()));
        return appointment.getId();
    }

    private void nativeUpdate(String sql, UUID id) {
        entityManager.getEntityManager().createNativeQuery(sql).setParameter("id", id).executeUpdate();
    }

    private static List<UUID> ids(ClientAppointmentRows rows) {
        return rows.rows().stream().map(ClientAppointmentRow::appointmentId).toList();
    }

    @Test
    void a_venir_ne_renvoie_que_les_rdv_actifs_non_termines_du_compte_du_plus_proche_au_plus_lointain() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID mine = profile(accountId);
        UUID later = appointment(f, mine, FUTURE.plusSeconds(7200), AppointmentStatus.CONFIRMED);
        UUID sooner = appointment(f, mine, FUTURE, AppointmentStatus.SCHEDULED);
        appointment(f, mine, FUTURE.plusSeconds(14_400), AppointmentStatus.CANCELLED);
        appointment(f, mine, FUTURE.plusSeconds(18_000), AppointmentStatus.COMPLETED);
        appointment(f, mine, PAST, AppointmentStatus.SCHEDULED);
        UUID deleted = appointment(f, mine, FUTURE.plusSeconds(21_600), AppointmentStatus.CONFIRMED);
        nativeUpdate("UPDATE appointment SET deleted_at = now() WHERE id = :id", deleted);
        // Autres clients : un autre compte, et un profil du même salon sans compte.
        appointment(f, profile(account()), FUTURE.plusSeconds(25_200), AppointmentStatus.CONFIRMED);
        appointment(f, profile(null), FUTURE.plusSeconds(28_800), AppointmentStatus.CONFIRMED);

        ClientAppointmentRows rows = adapter.findForAccount(accountId, true, NOW, 0, 20);

        assertThat(ids(rows)).containsExactly(sooner, later);
        assertThat(rows.totalElements()).isEqualTo(2);
    }

    @Test
    void a_venir_pagine_et_compte_le_total() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID mine = profile(accountId);
        UUID first = appointment(f, mine, FUTURE, AppointmentStatus.CONFIRMED);
        UUID second = appointment(f, mine, FUTURE.plusSeconds(7200), AppointmentStatus.CONFIRMED);
        UUID third = appointment(f, mine, FUTURE.plusSeconds(14_400), AppointmentStatus.CONFIRMED);

        ClientAppointmentRows page0 = adapter.findForAccount(accountId, true, NOW, 0, 2);
        ClientAppointmentRows page1 = adapter.findForAccount(accountId, true, NOW, 1, 2);

        assertThat(ids(page0)).containsExactly(first, second);
        assertThat(ids(page1)).containsExactly(third);
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page1.totalElements()).isEqualTo(3);
    }

    @Test
    void passes_renvoie_tout_le_reste_du_plus_recent_au_plus_ancien() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID mine = profile(accountId);
        UUID cancelledFuture = appointment(f, mine, FUTURE, AppointmentStatus.CANCELLED);
        UUID completed = appointment(f, mine, PAST.plusSeconds(7200), AppointmentStatus.COMPLETED);
        UUID noShow = appointment(f, mine, PAST, AppointmentStatus.NO_SHOW);
        UUID unprocessedRequest = appointment(f, mine, PAST.plusSeconds(14_400), AppointmentStatus.SCHEDULED);
        appointment(f, mine, FUTURE.plusSeconds(7200), AppointmentStatus.CONFIRMED);
        appointment(f, profile(account()), PAST.plusSeconds(21_600), AppointmentStatus.COMPLETED);

        ClientAppointmentRows rows = adapter.findForAccount(accountId, false, NOW, 0, 20);

        assertThat(ids(rows)).containsExactly(cancelledFuture, unprocessedRequest, completed, noShow);
        assertThat(rows.totalElements()).isEqualTo(4);
        assertThat(rows.rows().get(0).cancelledAt()).isEqualTo(Instant.parse("2098-12-01T00:00:00Z"));
        assertThat(rows.rows().get(1).cancelledAt()).isNull();
    }

    @Test
    void un_rdv_qui_se_termine_exactement_a_l_instant_courant_est_passe() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID endsNow = appointment(f, profile(accountId), NOW.minusSeconds(3600), AppointmentStatus.CONFIRMED);

        assertThat(ids(adapter.findForAccount(accountId, true, NOW, 0, 20))).isEmpty();
        assertThat(ids(adapter.findForAccount(accountId, false, NOW, 0, 20))).containsExactly(endsNow);
    }

    @Test
    void expose_les_noms_et_les_valeurs_figees_sans_donnee_de_compte() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID id = appointment(f, profile(accountId), FUTURE, AppointmentStatus.SCHEDULED);

        ClientAppointmentRow row = adapter.findOwned(accountId, id).orElseThrow();

        assertThat(row.appointmentId()).isEqualTo(id);
        assertThat(row.salonId()).isEqualTo(f.salonId());
        assertThat(row.salonName()).isEqualTo("Salon Test");
        assertThat(row.salonAddress()).isEqualTo("1 rue du Test");
        assertThat(row.salonPostalCode()).isEqualTo("06140");
        assertThat(row.salonCity()).isEqualTo("Vence");
        assertThat(row.salonTimezone()).isEqualTo("Europe/Paris");
        assertThat(row.serviceId()).isEqualTo(f.serviceId());
        assertThat(row.serviceName()).isEqualTo("Coupe");
        assertThat(row.resourceId()).isEqualTo(f.resourceId());
        assertThat(row.resourceName()).isEqualTo("Chaise 1");
        assertThat(row.startAt()).isEqualTo(FUTURE);
        assertThat(row.endAt()).isEqualTo(FUTURE.plusSeconds(3600));
        assertThat(row.status()).isEqualTo("SCHEDULED");
        assertThat(row.priceCents()).isEqualTo(3000);
        assertThat(row.durationMinutes()).isEqualTo(60);
        assertThat(row.cancelledAt()).isNull();
    }

    @Test
    void find_owned_ignore_les_rdv_des_autres_comptes_supprimes_ou_inconnus() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID mine = profile(accountId);
        UUID own = appointment(f, mine, FUTURE, AppointmentStatus.CONFIRMED);
        UUID other = appointment(f, profile(account()), FUTURE.plusSeconds(7200), AppointmentStatus.CONFIRMED);
        UUID unclaimed = appointment(f, profile(null), FUTURE.plusSeconds(14_400), AppointmentStatus.CONFIRMED);
        UUID deleted = appointment(f, mine, FUTURE.plusSeconds(21_600), AppointmentStatus.CONFIRMED);
        nativeUpdate("UPDATE appointment SET deleted_at = now() WHERE id = :id", deleted);

        assertThat(adapter.findOwned(accountId, own)).isPresent();
        assertThat(adapter.findOwned(accountId, other)).isEmpty();
        assertThat(adapter.findOwned(accountId, unclaimed)).isEmpty();
        assertThat(adapter.findOwned(accountId, deleted)).isEmpty();
        assertThat(adapter.findOwned(accountId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void renvoie_aussi_les_rdv_annules_et_garde_les_noms_d_une_prestation_ou_ressource_supprimee() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID id = appointment(f, profile(accountId), PAST, AppointmentStatus.COMPLETED);
        nativeUpdate("UPDATE service SET deleted_at = now() WHERE id = :id", f.serviceId());
        nativeUpdate("UPDATE resource SET deleted_at = now() WHERE id = :id", f.resourceId());

        ClientAppointmentRow row = adapter.findOwned(accountId, id).orElseThrow();

        assertThat(row.status()).isEqualTo("COMPLETED");
        assertThat(row.serviceName()).isEqualTo("Coupe");
        assertThat(row.resourceName()).isEqualTo("Chaise 1");
        assertThat(ids(adapter.findForAccount(accountId, false, NOW, 0, 20))).containsExactly(id);
    }

    @Test
    void un_profil_supprime_n_expose_plus_ses_rdv() {
        Fixture f = fixture();
        UUID accountId = account();
        UUID mine = profile(accountId);
        UUID id = appointment(f, mine, FUTURE, AppointmentStatus.CONFIRMED);
        nativeUpdate("UPDATE customer_profile SET deleted_at = now() WHERE id = :id", mine);

        assertThat(adapter.findOwned(accountId, id)).isEmpty();
        assertThat(adapter.findForAccount(accountId, true, NOW, 0, 20).rows()).isEmpty();
    }
}
