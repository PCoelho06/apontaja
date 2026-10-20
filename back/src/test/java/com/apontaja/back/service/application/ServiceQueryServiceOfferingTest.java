package com.apontaja.back.service.application;

import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Les entités et ResourceSummary sont simulées (leurs constructeurs ne sont pas figés ici) : seuls les
 * accesseurs lus par ServiceQueryService comptent.
 */
@ExtendWith(MockitoExtension.class)
class ServiceQueryServiceOfferingTest {

    private static final UUID SALON = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SERVICE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID R1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID R2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID DEAD = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Mock
    private SalonServiceRepository serviceRepository;
    @Mock
    private ServiceResourceRepository serviceResourceRepository;
    @Mock
    private ResourceQueryService resourceQueryService;

    private ServiceQueryService queryService() {
        return new ServiceQueryService(serviceRepository, serviceResourceRepository, resourceQueryService);
    }

    private static SalonService salonService(UUID salonId) {
        SalonService service = mock(SalonService.class);
        lenient().when(service.getId()).thenReturn(SERVICE);
        lenient().when(service.getSalonId()).thenReturn(salonId);
        lenient().when(service.getName()).thenReturn("Coupe");
        lenient().when(service.getDescription()).thenReturn(null);
        lenient().when(service.getDefaultDurationMinutes()).thenReturn(60);
        lenient().when(service.getDefaultPriceCents()).thenReturn(3000);
        lenient().when(service.getCreatedAt()).thenReturn(Instant.parse("2099-01-01T00:00:00Z"));
        return service;
    }

    private static ServiceResource link(UUID resourceId, Integer price, Integer duration) {
        ServiceResource link = mock(ServiceResource.class);
        lenient().when(link.getResourceId()).thenReturn(resourceId);
        lenient().when(link.getOverridePriceCents()).thenReturn(price);
        lenient().when(link.getOverrideDurationMinutes()).thenReturn(duration);
        return link;
    }

    private static ResourceSummary resource(UUID id) {
        ResourceSummary resource = mock(ResourceSummary.class);
        lenient().when(resource.resourceId()).thenReturn(id);
        return resource;
    }

    @Test
    void l_offre_applique_les_surcharges_ignore_les_ressources_mortes_et_suit_l_ordre_des_ressources() {
        // Les mocks sont construits AVANT les when(...) : en créer à l'intérieur d'un thenReturn
        // provoque un UnfinishedStubbing.
        SalonService service = salonService(SALON);
        List<ResourceSummary> resources = List.of(resource(R2), resource(R1));
        List<ServiceResource> links = List.of(link(R1, null, null), link(R2, 4500, 90), link(DEAD, 1, 1));
        when(serviceRepository.findAliveBySalonId(SALON)).thenReturn(List.of(service));
        when(resourceQueryService.findAliveBySalonId(SALON)).thenReturn(resources);
        when(serviceResourceRepository.findByServiceId(SERVICE)).thenReturn(links);

        List<ServiceOffering> offerings = queryService().findOfferingsBySalonId(SALON);

        assertThat(offerings).hasSize(1);
        assertThat(offerings.get(0).service().name()).isEqualTo("Coupe");
        assertThat(offerings.get(0).resources()).containsExactly(new ResourceTerms(R2, 4500, 90),
                new ResourceTerms(R1, 3000, 60));
    }

    @Test
    void findOffering_est_vide_pour_une_prestation_d_un_autre_salon() {
        SalonService otherSalonService = salonService(UUID.randomUUID());
        when(serviceRepository.findAliveById(SERVICE)).thenReturn(Optional.of(otherSalonService));

        assertThat(queryService().findOffering(SALON, SERVICE)).isEmpty();
    }

    @Test
    void findOffering_renvoie_l_offre_du_salon() {
        SalonService service = salonService(SALON);
        List<ResourceSummary> resources = List.of(resource(R1));
        List<ServiceResource> links = List.of(link(R1, null, 45));
        when(serviceRepository.findAliveById(SERVICE)).thenReturn(Optional.of(service));
        when(resourceQueryService.findAliveBySalonId(SALON)).thenReturn(resources);
        when(serviceResourceRepository.findByServiceId(SERVICE)).thenReturn(links);

        Optional<ServiceOffering> offering = queryService().findOffering(SALON, SERVICE);

        assertThat(offering).isPresent();
        assertThat(offering.get().resources()).containsExactly(new ResourceTerms(R1, 3000, 45));
    }
}
