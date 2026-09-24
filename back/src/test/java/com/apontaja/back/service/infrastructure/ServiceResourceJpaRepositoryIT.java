package com.apontaja.back.service.infrastructure;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceId;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class ServiceResourceJpaRepositoryIT {

    @Autowired
    private ServiceResourceJpaRepository linkJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID salonId;

    private UUID createSalon() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        return salon.getId();
    }

    private UUID createService() {
        if (salonId == null) {
            salonId = createSalon();
        }
        SalonService service = new SalonService(UUID.randomUUID(), salonId, "Prestation " + UUID.randomUUID(), null,
                30, 2500, Instant.now());
        entityManager.persistAndFlush(service);
        return service.getId();
    }

    private UUID createResource() {
        Resource resource = new Resource(UUID.randomUUID(), salonId, "Ressource " + UUID.randomUUID(),
                ResourceType.EMPLOYEE, Instant.now());
        entityManager.persistAndFlush(resource);
        return resource.getId();
    }

    @Test
    void persiste_retrouve_et_liste_les_associations_d_un_service() {
        UUID serviceId = createService();
        UUID resourceA = createResource();
        UUID resourceB = createResource();

        linkJpaRepository.saveAndFlush(new ServiceResource(serviceId, resourceA, 3000, 45));
        linkJpaRepository.saveAndFlush(new ServiceResource(serviceId, resourceB, null, null));
        entityManager.clear();

        assertThat(linkJpaRepository.findById(new ServiceResourceId(serviceId, resourceA)))
                .hasValueSatisfying(link -> {
                    assertThat(link.getOverridePriceCents()).isEqualTo(3000);
                    assertThat(link.getOverrideDurationMinutes()).isEqualTo(45);
                });

        List<ServiceResource> links = linkJpaRepository.findByServiceId(serviceId);
        assertThat(links).extracting(ServiceResource::getResourceId).containsExactlyInAnyOrder(resourceA, resourceB);
    }

    @Test
    void met_a_jour_puis_supprime_une_association() {
        UUID serviceId = createService();
        UUID resourceId = createResource();
        linkJpaRepository.saveAndFlush(new ServiceResource(serviceId, resourceId, null, null));
        entityManager.clear();

        ServiceResource loaded = linkJpaRepository.findById(new ServiceResourceId(serviceId, resourceId))
                .orElseThrow();
        loaded.changeOverrides(1000, 15);
        linkJpaRepository.saveAndFlush(loaded);
        entityManager.clear();

        assertThat(linkJpaRepository.findById(new ServiceResourceId(serviceId, resourceId)))
                .hasValueSatisfying(link -> assertThat(link.getOverridePriceCents()).isEqualTo(1000));

        linkJpaRepository.deleteById(new ServiceResourceId(serviceId, resourceId));
        linkJpaRepository.flush();

        assertThat(linkJpaRepository.findByServiceId(serviceId)).isEmpty();
    }
}
