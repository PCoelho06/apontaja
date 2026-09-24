package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class ScheduleJpaRepositoryIT {

    @Autowired
    private ScheduleJpaRepository scheduleJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID salonId;

    private UUID createResource() {
        if (salonId == null) {
            Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
            entityManager.persistAndFlush(organization);
            Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                    "Vence", "France", "Europe/Paris", Instant.now());
            entityManager.persistAndFlush(salon);
            salonId = salon.getId();
        }
        Resource resource = new Resource(UUID.randomUUID(), salonId, "R " + UUID.randomUUID(),
                ResourceType.EMPLOYEE, Instant.now());
        entityManager.persistAndFlush(resource);
        return resource.getId();
    }

    private Schedule forSalon(DayOfWeek day) {
        return Schedule.forSalon(UUID.randomUUID(), salonId, day, LocalTime.of(9, 0), LocalTime.of(12, 30),
                Instant.now());
    }

    @Test
    void persiste_le_dimanche_sous_le_code_0_et_relit_les_heures_locales() {
        createResource();
        Schedule sunday = scheduleJpaRepository.saveAndFlush(forSalon(DayOfWeek.SUNDAY));
        entityManager.clear();

        Number storedCode = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT day_of_week FROM schedule WHERE id = :id")
                .setParameter("id", sunday.getId()).getSingleResult();
        assertThat(storedCode.intValue()).isZero();

        Schedule reloaded = scheduleJpaRepository.findById(sunday.getId()).orElseThrow();
        assertThat(reloaded.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(reloaded.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(reloaded.getEndTime()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    void les_horaires_du_salon_et_ceux_de_ses_ressources_sont_distincts() {
        UUID resourceId = createResource();
        scheduleJpaRepository.saveAndFlush(forSalon(DayOfWeek.MONDAY));
        scheduleJpaRepository.saveAndFlush(Schedule.forResource(UUID.randomUUID(), resourceId, DayOfWeek.TUESDAY,
                LocalTime.of(10, 0), LocalTime.of(11, 0), Instant.now()));

        assertThat(scheduleJpaRepository.findBySalonId(salonId)).extracting(Schedule::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY);
        assertThat(scheduleJpaRepository.findByResourceId(resourceId)).extracting(Schedule::getDayOfWeek)
                .containsExactly(DayOfWeek.TUESDAY);
    }

    @Test
    void la_suppression_ne_touche_que_le_proprietaire_cible() {
        UUID resourceId = createResource();
        scheduleJpaRepository.saveAndFlush(forSalon(DayOfWeek.MONDAY));
        scheduleJpaRepository.saveAndFlush(Schedule.forResource(UUID.randomUUID(), resourceId, DayOfWeek.TUESDAY,
                LocalTime.of(10, 0), LocalTime.of(11, 0), Instant.now()));

        scheduleJpaRepository.deleteAllByResourceId(resourceId);

        assertThat(scheduleJpaRepository.findByResourceId(resourceId)).isEmpty();
        assertThat(scheduleJpaRepository.findBySalonId(salonId)).hasSize(1);

        scheduleJpaRepository.deleteAllBySalonId(salonId);
        assertThat(scheduleJpaRepository.findBySalonId(salonId)).isEmpty();
    }
}
