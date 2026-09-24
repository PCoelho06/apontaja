package com.apontaja.back.resource.infrastructure;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
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
class ClosureJpaRepositoryIT {

    private static final Instant T10 = Instant.parse("2026-09-24T10:00:00Z");
    private static final Instant T12 = Instant.parse("2026-09-24T12:00:00Z");

    @Autowired
    private ClosureJpaRepository closureJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID salonId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        salonId = salon.getId();
        Resource resource = new Resource(UUID.randomUUID(), salonId, "Chaise", ResourceType.EMPLOYEE, Instant.now());
        entityManager.persistAndFlush(resource);
        resourceId = resource.getId();
        closureJpaRepository
                .saveAndFlush(Closure.forSalon(UUID.randomUUID(), salonId, T10, T12, "Salon", Instant.now()));
    }

    private int countSalonOverlapping(Instant from, Instant to) {
        return closureJpaRepository.findOverlappingBySalonId(salonId, from, to).size();
    }

    @Test
    void recouvrement_partiel_inclusion_et_contenance_sont_detectes() {
        assertThat(countSalonOverlapping(T10.plusSeconds(3600), T12.plusSeconds(3600))).isEqualTo(1);
        assertThat(countSalonOverlapping(T10.minusSeconds(3600), T10.plusSeconds(60))).isEqualTo(1);
        assertThat(countSalonOverlapping(T10.plusSeconds(600), T12.minusSeconds(600))).isEqualTo(1);
        assertThat(countSalonOverlapping(T10.minusSeconds(3600), T12.plusSeconds(3600))).isEqualTo(1);
    }

    @Test
    void des_bornes_adjacentes_ne_se_recouvrent_pas() {
        assertThat(countSalonOverlapping(T12, T12.plusSeconds(3600))).isZero();
        assertThat(countSalonOverlapping(T10.minusSeconds(3600), T10)).isZero();
    }

    @Test
    void separe_les_fermetures_du_salon_de_celles_des_ressources_et_trie_par_debut() {
        closureJpaRepository.saveAndFlush(
                Closure.forResource(UUID.randomUUID(), resourceId, T10, T12, "Ressource", Instant.now()));
        closureJpaRepository.saveAndFlush(Closure.forSalon(UUID.randomUUID(), salonId, T10.minusSeconds(7200),
                T10.minusSeconds(3600), "Plus tot", Instant.now()));

        assertThat(closureJpaRepository.findOverlappingByResourceId(resourceId, T10.minusSeconds(86400),
                T12.plusSeconds(86400))).extracting(Closure::getReason).containsExactly("Ressource");
        assertThat(closureJpaRepository.findOverlappingBySalonId(salonId, T10.minusSeconds(86400),
                T12.plusSeconds(86400))).extracting(Closure::getReason).containsExactly("Plus tot", "Salon");
    }
}
