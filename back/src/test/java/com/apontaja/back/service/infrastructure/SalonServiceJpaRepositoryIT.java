package com.apontaja.back.service.infrastructure;

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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class SalonServiceJpaRepositoryIT {

    @Autowired
    private SalonServiceJpaRepository serviceJpaRepository;

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

    private SalonService newService(UUID salonId, String name) {
        return new SalonService(UUID.randomUUID(), salonId, name, "desc", 30, 2500, Instant.now());
    }

    @Test
    void persiste_et_retrouve_une_prestation_vivante() {
        UUID salonId = createSalon();
        SalonService service = serviceJpaRepository.saveAndFlush(newService(salonId, "Coupe"));

        assertThat(serviceJpaRepository.findByIdAndDeletedAtIsNull(service.getId())).contains(service);
    }

    @Test
    void ignore_une_prestation_soft_deleted() {
        UUID salonId = createSalon();
        SalonService service = newService(salonId, "Coupe");
        service.softDelete(Instant.now());
        serviceJpaRepository.saveAndFlush(service);

        assertThat(serviceJpaRepository.findByIdAndDeletedAtIsNull(service.getId())).isEmpty();
    }

    @Test
    void rejette_deux_prestations_vivantes_de_meme_nom_dans_le_meme_salon() {
        UUID salonId = createSalon();
        serviceJpaRepository.saveAndFlush(newService(salonId, "Coupe"));

        assertThatThrownBy(() -> serviceJpaRepository.saveAndFlush(newService(salonId, "Coupe")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void autorise_le_meme_nom_dans_un_autre_salon_ou_apres_soft_delete() {
        UUID salonId = createSalon();
        SalonService first = newService(salonId, "Coupe");
        first.softDelete(Instant.now());
        serviceJpaRepository.saveAndFlush(first);

        assertThatCode(() -> {
            serviceJpaRepository.saveAndFlush(newService(salonId, "Coupe"));
            serviceJpaRepository.saveAndFlush(newService(createSalon(), "Coupe"));
        }).doesNotThrowAnyException();
    }

    @Test
    void liste_les_prestations_vivantes_du_salon_triees_par_nom() {
        UUID salonId = createSalon();
        serviceJpaRepository.saveAndFlush(newService(salonId, "B"));
        serviceJpaRepository.saveAndFlush(newService(salonId, "A"));
        serviceJpaRepository.saveAndFlush(newService(createSalon(), "Autre salon"));

        List<SalonService> result = serviceJpaRepository.findBySalonIdAndDeletedAtIsNullOrderByNameAsc(salonId);

        assertThat(result).extracting(SalonService::getName).containsExactly("A", "B");
    }
}
