package com.apontaja.back.salon.infrastructure;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class SalonJpaRepositoryIT {

    @Autowired
    private SalonJpaRepository salonJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID createOrganization() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        return organization.getId();
    }

    @Test
    void persiste_et_retrouve_un_salon_vivant() {
        UUID organizationId = createOrganization();

        Salon salon = salonJpaRepository.save(new Salon(UUID.randomUUID(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now()));

        Optional<Salon> found = salonJpaRepository.findByIdAndDeletedAtIsNull(salon.getId());

        assertThat(found).contains(salon);
    }

    @Test
    void ignore_un_salon_soft_deleted() {
        UUID organizationId = createOrganization();

        Salon salon = new Salon(UUID.randomUUID(), organizationId, "Salon Test", "1 rue du Test", "06140", "Vence",
                "France", "Europe/Paris", Instant.now());
        salon.softDelete(Instant.now());
        salonJpaRepository.save(salon);
        salonJpaRepository.flush();

        assertThat(salonJpaRepository.findByIdAndDeletedAtIsNull(salon.getId())).isEmpty();
    }

    @Test
    void liste_les_salons_vivants_d_une_organisation() {
        UUID organizationId = createOrganization();

        salonJpaRepository.save(new Salon(UUID.randomUUID(), organizationId, "Salon A", "1 rue A", "06140", "Vence",
                "France", "Europe/Paris", Instant.now()));
        salonJpaRepository.save(new Salon(UUID.randomUUID(), organizationId, "Salon B", "2 rue B", "06140", "Vence",
                "France", "Europe/Paris", Instant.now()));

        List<Salon> result = salonJpaRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);

        assertThat(result).hasSize(2);
    }
}
