package com.apontaja.back.service.application;

import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceResourceLinkServiceTest {

    @Mock
    private SalonServiceRepository serviceRepository;

    @Mock
    private ServiceResourceRepository serviceResourceRepository;

    @Mock
    private ResourceQueryService resourceQueryService;

    private ServiceResourceLinkService linkService;

    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final SalonService salonService = new SalonService(UUID.randomUUID(), salonId, "Coupe", null, 30, 2500,
            Instant.parse("2026-09-24T10:00:00Z"));

    @BeforeEach
    void setUp() {
        linkService = new ServiceResourceLinkService(serviceRepository, serviceResourceRepository,
                resourceQueryService);
    }

    private ResourceSummary resourceSummary(UUID id) {
        return new ResourceSummary(id, salonId, "Chaise", "EMPLOYEE", Instant.now());
    }

    private void serviceExists() {
        when(serviceRepository.findAliveById(salonService.getId())).thenReturn(Optional.of(salonService));
    }

    @Test
    void link_cree_une_association_avec_les_valeurs_effectives() {
        serviceExists();
        when(resourceQueryService.findAliveInSalon(salonId, resourceId))
                .thenReturn(Optional.of(resourceSummary(resourceId)));
        when(serviceResourceRepository.findByServiceIdAndResourceId(salonService.getId(), resourceId))
                .thenReturn(Optional.empty());
        when(serviceResourceRepository.save(any(ServiceResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceResourceLinkSummary result = linkService.link(salonId, salonService.getId(), resourceId, null, 45);

        assertThat(result.effectivePriceCents()).isEqualTo(2500);
        assertThat(result.effectiveDurationMinutes()).isEqualTo(45);
    }

    @Test
    void link_met_a_jour_les_surcharges_d_une_association_existante() {
        serviceExists();
        ServiceResource existing = new ServiceResource(salonService.getId(), resourceId, 1000, 15);
        when(resourceQueryService.findAliveInSalon(salonId, resourceId))
                .thenReturn(Optional.of(resourceSummary(resourceId)));
        when(serviceResourceRepository.findByServiceIdAndResourceId(salonService.getId(), resourceId))
                .thenReturn(Optional.of(existing));
        when(serviceResourceRepository.save(existing)).thenReturn(existing);

        linkService.link(salonId, salonService.getId(), resourceId, 3000, null);

        assertThat(existing.getOverridePriceCents()).isEqualTo(3000);
        assertThat(existing.getOverrideDurationMinutes()).isNull();
    }

    @Test
    void link_refuse_une_ressource_absente_ou_d_un_autre_salon() {
        serviceExists();
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.link(salonId, salonService.getId(), resourceId, null, null))
                .isInstanceOf(LinkedResourceNotFoundException.class);

        verify(serviceResourceRepository, never()).save(any());
    }

    @Test
    void link_refuse_un_service_d_un_autre_salon() {
        serviceExists();

        assertThatThrownBy(() -> linkService.link(UUID.randomUUID(), salonService.getId(), resourceId, null, null))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    void unlink_supprime_l_association_existante_et_ignore_une_absente() {
        serviceExists();
        when(serviceResourceRepository.findByServiceIdAndResourceId(salonService.getId(), resourceId))
                .thenReturn(Optional.of(new ServiceResource(salonService.getId(), resourceId, null, null)));

        linkService.unlink(salonId, salonService.getId(), resourceId);

        verify(serviceResourceRepository).deleteByServiceIdAndResourceId(salonService.getId(), resourceId);
    }

    @Test
    void list_masque_les_associations_dont_la_ressource_est_supprimee() {
        serviceExists();
        UUID deletedResourceId = UUID.randomUUID();
        when(resourceQueryService.findAliveBySalonId(salonId)).thenReturn(List.of(resourceSummary(resourceId)));
        when(serviceResourceRepository.findByServiceId(salonService.getId())).thenReturn(List.of(
                new ServiceResource(salonService.getId(), resourceId, null, null),
                new ServiceResource(salonService.getId(), deletedResourceId, null, null)));

        List<ServiceResourceLinkSummary> result = linkService.list(salonId, salonService.getId());

        assertThat(result).extracting(ServiceResourceLinkSummary::resourceId).containsExactly(resourceId);
    }
}
