package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.BookingQuotaRepository;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, BookingQuotaRepositoryAdapter.class })
class BookingQuotaRepositoryAdapterIT {

    private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingQuotaRepository repository;

    private UUID salonId;
    private UUID otherSalonId;
    private UUID serviceId;
    private UUID otherSalonServiceId;
    private UUID profileId;
    private UUID otherProfileId;

    @BeforeEach
    void setUp() {
        UUID organizationId = UUID.randomUUID();
        em.persistAndFlush(new Organization(organizationId, "Org Test", NOW));
        salonId = persistSalon(organizationId);
        otherSalonId = persistSalon(organizationId);
        serviceId = persistService(salonId);
        otherSalonServiceId = persistService(otherSalonId);
        profileId = persistProfile();
        otherProfileId = persistProfile();
    }

    private UUID persistSalon(UUID organizationId) {
        UUID id = UUID.randomUUID();
        em.persistAndFlush(new Salon(id, organizationId, "Salon Test", "1 rue du Test", "06140", "Vence", "France",
                "Europe/Paris", NOW));
        return id;
    }

    // Prestation : table du domaine service, posée en SQL natif (fixture).
    private UUID persistService(UUID salon) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO service (id, salon_id, name, default_duration_minutes, "
                + "default_price_cents) VALUES (?, ?, ?, 60, 3000)", id, salon, "Coupe " + id);
        return id;
    }

    private UUID persistProfile() {
        UUID id = UUID.randomUUID();
        em.persistAndFlush(new CustomerProfile(id, "Alice", "Martin", id + "@example.com", null, NOW));
        return id;
    }

    private Appointment persistAppointment(UUID salon, UUID service, UUID profile, Instant start,
            AppointmentStatus status) {
        AppointmentStatus initial = status == AppointmentStatus.SCHEDULED ? AppointmentStatus.SCHEDULED
                : AppointmentStatus.CONFIRMED;
        Appointment appointment = new Appointment(UUID.randomUUID(), salon, profile, service, start,
                start.plusSeconds(3600), 3000, 60, NOW, initial);
        if (status == AppointmentStatus.CANCELLED) {
            appointment.cancel(NOW, null, null);
        } else if (status == AppointmentStatus.COMPLETED) {
            appointment.complete();
        } else if (status == AppointmentStatus.NO_SHOW) {
            appointment.markNoShow();
        }
        return em.persistAndFlush(appointment);
    }

    private static Instant future(int days) {
        return NOW.plusSeconds(days * 86_400L);
    }

    @Test
    void compte_les_rendez_vous_scheduled_et_confirmed_a_venir() {
        persistAppointment(salonId, serviceId, profileId, future(1), AppointmentStatus.SCHEDULED);
        persistAppointment(salonId, serviceId, profileId, future(2), AppointmentStatus.CONFIRMED);

        assertThat(repository.countUpcomingActive(salonId, profileId, NOW)).isEqualTo(2);
    }

    @Test
    void ignore_annules_termines_absents_et_supprimes() {
        persistAppointment(salonId, serviceId, profileId, future(1), AppointmentStatus.CANCELLED);
        persistAppointment(salonId, serviceId, profileId, future(2), AppointmentStatus.COMPLETED);
        persistAppointment(salonId, serviceId, profileId, future(3), AppointmentStatus.NO_SHOW);
        Appointment deleted = persistAppointment(salonId, serviceId, profileId, future(4),
                AppointmentStatus.CONFIRMED);
        jdbcTemplate.update("UPDATE appointment SET deleted_at = now() WHERE id = ?", deleted.getId());

        assertThat(repository.countUpcomingActive(salonId, profileId, NOW)).isZero();
    }

    @Test
    void ne_compte_que_les_debuts_strictement_apres_maintenant() {
        persistAppointment(salonId, serviceId, profileId, NOW, AppointmentStatus.CONFIRMED);
        persistAppointment(salonId, serviceId, profileId, NOW.minusSeconds(3600), AppointmentStatus.CONFIRMED);
        persistAppointment(salonId, serviceId, profileId, NOW.plusSeconds(1), AppointmentStatus.CONFIRMED);

        assertThat(repository.countUpcomingActive(salonId, profileId, NOW)).isEqualTo(1);
    }

    @Test
    void ne_compte_ni_un_autre_client_ni_un_autre_salon() {
        persistAppointment(salonId, serviceId, otherProfileId, future(1), AppointmentStatus.CONFIRMED);
        persistAppointment(otherSalonId, otherSalonServiceId, profileId, future(2), AppointmentStatus.CONFIRMED);
        persistAppointment(salonId, serviceId, profileId, future(3), AppointmentStatus.CONFIRMED);

        assertThat(repository.countUpcomingActive(salonId, profileId, NOW)).isEqualTo(1);
        assertThat(repository.countUpcomingActive(otherSalonId, profileId, NOW)).isEqualTo(1);
        assertThat(repository.countUpcomingActive(salonId, otherProfileId, NOW)).isEqualTo(1);
    }
}
