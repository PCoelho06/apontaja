package com.apontaja.back.resource.infrastructure;

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
class ResourceJpaRepositoryIT {

    @Autowired
    private ResourceJpaRepository resourceJpaRepository;

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

    private Resource newResource(UUID salonId, String name) {
        return new Resource(UUID.randomUUID(), salonId, name, ResourceType.EMPLOYEE, Instant.now());
    }

    @Test
    void persiste_et_retrouve_une_ressource_vivante() {
        UUID salonId = createSalon();
        Resource resource = resourceJpaRepository.saveAndFlush(newResource(salonId, "Chaise 1"));

        assertThat(resourceJpaRepository.findByIdAndDeletedAtIsNull(resource.getId())).contains(resource);
    }

    @Test
    void ignore_une_ressource_soft_deleted() {
        UUID salonId = createSalon();
        Resource resource = newResource(salonId, "Chaise 1");
        resource.softDelete(Instant.now());
        resourceJpaRepository.saveAndFlush(resource);

        assertThat(resourceJpaRepository.findByIdAndDeletedAtIsNull(resource.getId())).isEmpty();
    }

    @Test
    void rejette_deux_ressources_vivantes_de_meme_nom_dans_le_meme_salon() {
        UUID salonId = createSalon();
        resourceJpaRepository.saveAndFlush(newResource(salonId, "Chaise 1"));

        assertThatThrownBy(() -> resourceJpaRepository.saveAndFlush(newResource(salonId, "Chaise 1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void autorise_le_meme_nom_dans_un_autre_salon() {
        resourceJpaRepository.saveAndFlush(newResource(createSalon(), "Chaise 1"));

        assertThatCode(() -> resourceJpaRepository.saveAndFlush(newResource(createSalon(), "Chaise 1")))
                .doesNotThrowAnyException();
    }

    @Test
    void un_nom_libere_par_soft_delete_redevient_utilisable() {
        UUID salonId = createSalon();
        Resource first = newResource(salonId, "Chaise 1");
        first.softDelete(Instant.now());
        resourceJpaRepository.saveAndFlush(first);

        assertThatCode(() -> resourceJpaRepository.saveAndFlush(newResource(salonId, "Chaise 1")))
                .doesNotThrowAnyException();
    }

    @Test
    void liste_les_ressources_vivantes_du_salon_triees_par_nom() {
        UUID salonId = createSalon();
        resourceJpaRepository.saveAndFlush(newResource(salonId, "B"));
        resourceJpaRepository.saveAndFlush(newResource(salonId, "A"));
        Resource deleted = newResource(salonId, "C");
        deleted.softDelete(Instant.now());
        resourceJpaRepository.saveAndFlush(deleted);
        resourceJpaRepository.saveAndFlush(newResource(createSalon(), "Autre salon"));

        List<Resource> result = resourceJpaRepository.findBySalonIdAndDeletedAtIsNullOrderByNameAsc(salonId);

        assertThat(result).extracting(Resource::getName).containsExactly("A", "B");
    }
}
