package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie directement le mécanisme DB (contrainte d'exclusion + triggers) qui
 * ferme le bug historique de double-booking (§3.3 de l'audit), et le
 * fonctionnement de is_active dont dépendent ResourceDeletionCheckAdapter et
 * ServiceDeletionCheckAdapter — y compris son recalcul par le trigger AFTER
 * UPDATE lors d'une annulation, alors qu'aucune API d'annulation n'existe
 * encore (tranche 7) : accès à status via réflexion, seul moyen d'atteindre
 * cet état à ce stade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentResourceJpaRepositoryIT {

    private static final Instant T10 = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant T11 = Instant.parse("2026-09-24T11:00:00Z");
    private static final Instant T12 = Instant.parse("2026-09-24T12:00:00Z");

    @Autowired
    private AppointmentJpaRepository appointmentJpaRepository;

    @Autowired
    private AppointmentResourceJpaRepository appointmentResourceJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID salonId;
    private UUID serviceId;
    private UUID customerProfileId;

    private void ensureCommonFixtures() {
        if (salonId != null) {
            return;
        }
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        salonId = salon.getId();

        SalonService service = new SalonService(UUID.randomUUID(), salonId, "Coupe", null, 60, 2500, Instant.now());
        entityManager.persistAndFlush(service);
        serviceId = service.getId();

        CustomerProfile customer = new CustomerProfile(UUID.randomUUID(), "Alice", "Martin",
                "alice+" + UUID.randomUUID() + "@example.com", null, Instant.now());
        entityManager.persistAndFlush(customer);
        customerProfileId = customer.getId();
    }

    private UUID createResource() {
        ensureCommonFixtures();
        Resource resource = new Resource(UUID.randomUUID(), salonId, "R " + UUID.randomUUID(), ResourceType.EMPLOYEE,
                Instant.now());
        entityManager.persistAndFlush(resource);
        return resource.getId();
    }

    private UUID createAppointment(Instant start, Instant end) {
        ensureCommonFixtures();
        Appointment appointment = new Appointment(UUID.randomUUID(), salonId, customerProfileId, serviceId, start,
                end, 2500, 60, Instant.now());
        entityManager.persistAndFlush(appointment);
        return appointment.getId();
    }

    /** Le contrat Appointment n'expose aucune mutation en tranche 6 : réflexion nécessaire pour
     * atteindre un état CANCELLED, seul moyen de tester ici le mécanisme DB avant la tranche 7. */
    private void forceStatus(UUID appointmentId, AppointmentStatus status) {
        Appointment managed = entityManager.find(Appointment.class, appointmentId);
        try {
            var field = Appointment.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(managed, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        entityManager.getEntityManager().flush();
    }

    @Test
    void rejette_un_chevauchement_de_deux_rdv_actifs_sur_la_meme_ressource() {
        UUID resourceId = createResource();
        UUID first = createAppointment(T10, T11);
        UUID second = createAppointment(T10.plusSeconds(1800), T12);

        appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(first, resourceId));

        assertThatThrownBy(
                () -> appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(second, resourceId)))
                        .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void autorise_deux_rdv_aux_bornes_adjacentes_sur_la_meme_ressource() {
        UUID resourceId = createResource();
        UUID first = createAppointment(T10, T11);
        UUID second = createAppointment(T11, T12);

        assertThatCode(() -> {
            appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(first, resourceId));
            appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(second, resourceId));
        }).doesNotThrowAnyException();
    }

    @Test
    void autorise_un_chevauchement_sur_deux_ressources_differentes() {
        UUID resourceA = createResource();
        UUID resourceB = createResource();
        UUID first = createAppointment(T10, T12);
        UUID second = createAppointment(T10, T12);

        assertThatCode(() -> {
            appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(first, resourceA));
            appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(second, resourceB));
        }).doesNotThrowAnyException();
    }

    @Test
    void existsActiveByResourceId_reflete_le_statut_de_l_appointment_au_moment_de_l_insertion() {
        UUID activeResource = createResource();
        UUID cancelledResource = createResource();
        UUID activeAppointment = createAppointment(T10, T11);
        UUID cancelledAppointment = createAppointment(T10, T11);
        forceStatus(cancelledAppointment, AppointmentStatus.CANCELLED);

        appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(activeAppointment, activeResource));
        appointmentResourceJpaRepository
                .saveAndFlush(new AppointmentResource(cancelledAppointment, cancelledResource));

        assertThat(appointmentResourceJpaRepository.existsActiveByResourceId(activeResource)).isTrue();
        assertThat(appointmentResourceJpaRepository.existsActiveByResourceId(cancelledResource)).isFalse();
    }

    @Test
    void existsActiveByResourceId_bascule_a_false_apres_annulation_meme_sans_api_d_annulation() {
        UUID resourceId = createResource();
        UUID appointmentId = createAppointment(T10, T11);
        appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(appointmentId, resourceId));
        assertThat(appointmentResourceJpaRepository.existsActiveByResourceId(resourceId)).isTrue();

        forceStatus(appointmentId, AppointmentStatus.CANCELLED);

        assertThat(appointmentResourceJpaRepository.existsActiveByResourceId(resourceId)).isFalse();
    }

    @Test
    void findByAppointmentId_retrouve_la_ressource_engagee() {
        UUID resourceId = createResource();
        UUID appointmentId = createAppointment(T10, T11);
        appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(appointmentId, resourceId));

        List<AppointmentResource> result = appointmentResourceJpaRepository.findByAppointmentId(appointmentId);

        assertThat(result).extracting(AppointmentResource::getResourceId).containsExactly(resourceId);
    }

    @Test
    void un_meme_couple_appointment_resource_ne_peut_pas_etre_insere_deux_fois() {
        UUID resourceId = createResource();
        UUID appointmentId = createAppointment(T10, T11);
        appointmentResourceJpaRepository.saveAndFlush(new AppointmentResource(appointmentId, resourceId));

        assertThatThrownBy(() -> appointmentResourceJpaRepository
                .saveAndFlush(new AppointmentResource(appointmentId, resourceId)))
                        .isInstanceOf(RuntimeException.class);
    }
}
