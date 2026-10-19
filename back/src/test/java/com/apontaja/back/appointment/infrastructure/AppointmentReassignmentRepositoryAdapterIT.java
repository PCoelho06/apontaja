package com.apontaja.back.appointment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apontaja.back.appointment.domain.Appointment;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, AppointmentReassignmentRepositoryAdapter.class })
class AppointmentReassignmentRepositoryAdapterIT {

    private static final Instant START = Instant.parse("2099-01-01T10:00:00Z");

    @Autowired
    private AppointmentReassignmentRepositoryAdapter adapter;

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
        UUID serviceId = UUID.randomUUID();
        // Table service : entité d'un autre domaine, insérée en SQL natif.
        entityManager.getEntityManager().createNativeQuery("INSERT INTO service (id, salon_id, name, "
                + "default_duration_minutes, default_price_cents) VALUES (:id, :salonId, :name, 30, 2000)")
                .setParameter("id", serviceId).setParameter("salonId", salonId)
                .setParameter("name", "Coupe " + serviceId).executeUpdate();
        return serviceId;
    }

    private UUID createProfile(String firstName) {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), firstName, "Test",
                firstName + "@example.com", null, Instant.now());
        entityManager.persistAndFlush(profile);
        return profile.getId();
    }

    private void createAppointment(UUID salonId, UUID profileId, UUID serviceId, int hourOffset) {
        Instant start = START.plusSeconds(hourOffset * 3_600L);
        entityManager.persistAndFlush(new Appointment(UUID.randomUUID(), salonId, profileId, serviceId, start,
                start.plusSeconds(1_800), 2_000, 30, Instant.now()));
    }

    private long countFor(UUID profileId) {
        entityManager.clear();
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM appointment WHERE customer_profile_id = :id")
                .setParameter("id", profileId).getSingleResult();
        return count.longValue();
    }

    @Test
    void reaffecte_les_rdv_des_profils_source_au_profil_cible_sans_toucher_aux_autres() {
        UUID salonId = createSalon();
        UUID serviceId = createService(salonId);
        UUID target = createProfile("target");
        UUID sourceA = createProfile("sourcea");
        UUID sourceB = createProfile("sourceb");
        UUID bystander = createProfile("bystander");
        createAppointment(salonId, sourceA, serviceId, 0);
        createAppointment(salonId, sourceA, serviceId, 1);
        createAppointment(salonId, sourceB, serviceId, 2);
        createAppointment(salonId, bystander, serviceId, 3);
        createAppointment(salonId, target, serviceId, 4);

        int updated = adapter.reassignCustomer(salonId, List.of(sourceA, sourceB), target);

        assertThat(updated).isEqualTo(3);
        assertThat(countFor(target)).isEqualTo(4);
        assertThat(countFor(sourceA)).isZero();
        assertThat(countFor(sourceB)).isZero();
        assertThat(countFor(bystander)).isEqualTo(1);
    }

    @Test
    void ne_touche_pas_aux_rdv_d_un_autre_salon() {
        UUID salonId = createSalon();
        UUID otherSalon = createSalon();
        UUID serviceId = createService(salonId);
        UUID otherService = createService(otherSalon);
        UUID target = createProfile("target");
        UUID source = createProfile("source");
        createAppointment(salonId, source, serviceId, 0);
        createAppointment(otherSalon, source, otherService, 0);

        int updated = adapter.reassignCustomer(salonId, List.of(source), target);

        assertThat(updated).isEqualTo(1);
        assertThat(countFor(source)).isEqualTo(1);
        assertThat(countFor(target)).isEqualTo(1);
    }

    @Test
    void une_liste_vide_ne_fait_rien() {
        assertThat(adapter.reassignCustomer(createSalon(), List.of(), UUID.randomUUID())).isZero();
    }
}
