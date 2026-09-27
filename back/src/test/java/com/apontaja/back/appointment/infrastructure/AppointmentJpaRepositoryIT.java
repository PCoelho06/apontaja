package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentJpaRepositoryIT {

    private static final Instant T10 = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant T11 = Instant.parse("2026-09-24T11:00:00Z");

    @Autowired
    private AppointmentJpaRepository appointmentJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID salonId;
    private UUID customerProfileId;

    private void ensureFixtures() {
        if (salonId != null) {
            return;
        }
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        salonId = salon.getId();

        CustomerProfile customer = new CustomerProfile(UUID.randomUUID(), "Alice", "Martin",
                "alice+" + UUID.randomUUID() + "@example.com", null, Instant.now());
        entityManager.persistAndFlush(customer);
        customerProfileId = customer.getId();
    }

    private UUID createService() {
        ensureFixtures();
        SalonService service = new SalonService(UUID.randomUUID(), salonId, "S " + UUID.randomUUID(), null, 60, 2500,
                Instant.now());
        entityManager.persistAndFlush(service);
        return service.getId();
    }

    private Appointment createAppointment(UUID serviceId) {
        ensureFixtures();
        Appointment appointment = new Appointment(UUID.randomUUID(), salonId, customerProfileId, serviceId, T10, T11,
                2500, 60, Instant.now());
        return entityManager.persistAndFlush(appointment);
    }

    @Test
    void persiste_et_retrouve_un_rdv_vivant() {
        Appointment appointment = createAppointment(createService());

        assertThat(appointmentJpaRepository.findByIdAndDeletedAtIsNull(appointment.getId())).contains(appointment);
    }

    @Test
    void ignore_un_rdv_soft_deleted() {
        // deletedAt non exposé par le contrat public en tranche 6 : réflexion, même principe
        // que les autres tests ciblés de ce type dans le repo (ex. CustomerProfileJpaRepositoryIT).
        Appointment appointment = createAppointment(createService());
        try {
            var field = Appointment.class.getDeclaredField("deletedAt");
            field.setAccessible(true);
            field.set(appointment, Instant.now());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        entityManager.getEntityManager().flush();

        assertThat(appointmentJpaRepository.findByIdAndDeletedAtIsNull(appointment.getId())).isEmpty();
    }

    @Test
    void existsByServiceId_est_vrai_pour_un_rdv_actif_et_faux_pour_un_annule() {
        UUID activeServiceId = createService();
        UUID cancelledServiceId = createService();
        createAppointment(activeServiceId);
        Appointment cancelled = createAppointment(cancelledServiceId);
        try {
            var field = Appointment.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(cancelled, AppointmentStatus.CANCELLED);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        entityManager.getEntityManager().flush();

        assertThat(appointmentJpaRepository.existsByServiceIdAndDeletedAtIsNullAndStatusNot(activeServiceId,
                AppointmentStatus.CANCELLED)).isTrue();
        assertThat(appointmentJpaRepository.existsByServiceIdAndDeletedAtIsNullAndStatusNot(cancelledServiceId,
                AppointmentStatus.CANCELLED)).isFalse();
    }
}
