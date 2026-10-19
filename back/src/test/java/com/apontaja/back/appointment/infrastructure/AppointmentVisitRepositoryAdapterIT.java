package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentVisitRepository.HistoryPage;
import com.apontaja.back.appointment.domain.CustomerVisitDate;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.organization.domain.Organization;
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
 * Dates de visite et historique. Dates fixes : 2020 est toujours passé, 2099 toujours futur. Les statuts autres que
 * CONFIRMED (statut initial) sont posés en SQL natif : aucun mutateur ne permet d'atteindre SCHEDULED, et on évite
 * de dépendre de la signature de cancel().
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, AppointmentVisitRepositoryAdapter.class })
class AppointmentVisitRepositoryAdapterIT {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Autowired
    private AppointmentVisitRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    private UUID createSalon() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        return salon.getId();
    }

    private UUID createService(UUID salonId) {
        UUID id = UUID.randomUUID();
        entityManager.getEntityManager().createNativeQuery("INSERT INTO service (id, salon_id, name, "
                + "default_duration_minutes, default_price_cents) VALUES (:id, :salonId, :name, 30, 3000)")
                .setParameter("id", id).setParameter("salonId", salonId).setParameter("name", "S-" + id)
                .executeUpdate();
        return id;
    }

    private UUID createProfile() {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), "Alice", "Martin",
                UUID.randomUUID() + "@example.com", null, Instant.now());
        entityManager.persistAndFlush(profile);
        return profile.getId();
    }

    private void appointment(UUID salonId, UUID profileId, UUID serviceId, String startAt, String status) {
        Instant start = Instant.parse(startAt);
        Appointment appointment = new Appointment(UUID.randomUUID(), salonId, profileId, serviceId, start,
                start.plusSeconds(1800), 3000, 30, Instant.now());
        entityManager.persistAndFlush(appointment);
        if (!"CONFIRMED".equals(status)) {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE appointment SET status = :status WHERE id = :id")
                    .setParameter("status", status).setParameter("id", appointment.getId()).executeUpdate();
        }
        entityManager.clear();
    }

    @Test
    void derniere_visite_est_le_dernier_rdv_termine_du_salon() {
        UUID salonId = createSalon();
        UUID otherSalonId = createSalon();
        UUID serviceId = createService(salonId);
        UUID otherServiceId = createService(otherSalonId);
        UUID profileId = createProfile();
        appointment(salonId, profileId, serviceId, "2020-01-10T09:00:00Z", "COMPLETED");
        appointment(salonId, profileId, serviceId, "2020-03-05T14:00:00Z", "COMPLETED");
        appointment(salonId, profileId, serviceId, "2020-06-01T14:00:00Z", "CANCELLED");
        appointment(salonId, profileId, serviceId, "2020-07-01T14:00:00Z", "NO_SHOW");
        appointment(otherSalonId, profileId, otherServiceId, "2020-09-01T14:00:00Z", "COMPLETED");

        List<CustomerVisitDate> result = adapter.findLastCompletedStarts(salonId, List.of(profileId));

        assertThat(result).containsExactly(new CustomerVisitDate(profileId, Instant.parse("2020-03-05T14:00:00Z")));
    }

    @Test
    void prochaine_visite_est_le_premier_rdv_actif_a_venir() {
        UUID salonId = createSalon();
        UUID serviceId = createService(salonId);
        UUID profileId = createProfile();
        UUID withoutUpcoming = createProfile();
        appointment(salonId, profileId, serviceId, "2099-06-01T10:00:00Z", "SCHEDULED");
        appointment(salonId, profileId, serviceId, "2099-01-01T10:00:00Z", "CONFIRMED");
        appointment(salonId, profileId, serviceId, "2099-02-01T10:00:00Z", "CANCELLED");
        appointment(salonId, profileId, serviceId, "2020-05-01T10:00:00Z", "CONFIRMED");
        appointment(salonId, withoutUpcoming, serviceId, "2099-03-01T10:00:00Z", "COMPLETED");

        List<CustomerVisitDate> result = adapter.findNextActiveStarts(salonId, List.of(profileId, withoutUpcoming),
                NOW);

        assertThat(result).containsExactly(new CustomerVisitDate(profileId, Instant.parse("2099-01-01T10:00:00Z")));
    }

    @Test
    void sans_client_demande_aucune_requete_et_aucun_resultat() {
        assertThat(adapter.findLastCompletedStarts(UUID.randomUUID(), List.of())).isEmpty();
        assertThat(adapter.findNextActiveStarts(UUID.randomUUID(), List.of(), NOW)).isEmpty();
    }

    @Test
    void historique_pagine_du_plus_recent_au_plus_ancien_tous_statuts() {
        UUID salonId = createSalon();
        UUID serviceId = createService(salonId);
        UUID profileId = createProfile();
        UUID otherProfileId = createProfile();
        appointment(salonId, profileId, serviceId, "2020-01-10T09:00:00Z", "COMPLETED");
        appointment(salonId, profileId, serviceId, "2020-03-05T14:00:00Z", "CANCELLED");
        appointment(salonId, profileId, serviceId, "2099-01-01T10:00:00Z", "CONFIRMED");
        appointment(salonId, otherProfileId, serviceId, "2020-02-01T10:00:00Z", "COMPLETED");

        HistoryPage first = adapter.findHistory(salonId, profileId, 0, 2);
        HistoryPage second = adapter.findHistory(salonId, profileId, 1, 2);

        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.rows()).extracting(row -> row.startAt().toString())
                .containsExactly("2099-01-01T10:00:00Z", "2020-03-05T14:00:00Z");
        assertThat(first.rows()).extracting(row -> row.status().name()).containsExactly("CONFIRMED", "CANCELLED");
        assertThat(second.rows()).extracting(row -> row.startAt().toString())
                .containsExactly("2020-01-10T09:00:00Z");
        assertThat(first.rows().get(0).priceAtBookingCents()).isEqualTo(3000);
        assertThat(first.rows().get(0).serviceId()).isEqualTo(serviceId);
    }
}
