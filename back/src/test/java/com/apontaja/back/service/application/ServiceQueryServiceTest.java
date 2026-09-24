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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceQueryServiceTest {

    @Mock
    private SalonServiceRepository serviceRepository;

    @Mock
    private ServiceResourceRepository serviceResourceRepository;

    @Mock
    private ResourceQueryService resourceQueryService;

    private ServiceQueryService queryService;

    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final SalonService salonService = new SalonService(UUID.randomUUID(), salonId, "Coupe", null, 30, 2500,
            Instant.parse("2026-09-24T10:00:00Z"));

    @BeforeEach
    void setUp() {
        queryService = new ServiceQueryService(serviceRepository, serviceResourceRepository, resourceQueryService);
    }

    private ResourceSummary aliveResource() {
        return new ResourceSummary(resourceId, salonId, "Chaise", "EMPLOYEE", Instant.now());
    }

    private void serviceAndResourceExist() {
        when(serviceRepository.findAliveById(salonService.getId())).thenReturn(Optional.of(salonService));
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.of(aliveResource()));
    }

    @Test
    void resolveTerms_utilise_les_surcharges_quand_elles_existent() {
        serviceAndResourceExist();
        when(serviceResourceRepository.findByServiceIdAndResourceId(salonService.getId(), resourceId))
                .thenReturn(Optional.of(new ServiceResource(salonService.getId(), resourceId, 4000, 60)));

        assertThat(queryService.resolveTerms(salonId, salonService.getId(), resourceId))
                .contains(new ServiceTerms(4000, 60));
    }

    @Test
    void resolveTerms_retombe_sur_les_valeurs_par_defaut_sans_surcharge() {
        serviceAndResourceExist();
        when(serviceResourceRepository.findByServiceIdAndResourceId(salonService.getId(), resourceId))
                .thenReturn(Optional.of(new ServiceResource(salonService.getId(), resourceId, null, null)));

        assertThat(queryService.resolveTerms(salonId, salonService.getId(), resourceId))
                .contains(new ServiceTerms(2500, 30));
    }

    @Test
    void resolveTerms_est_vide_si_la_ressource_n_est_pas_associee() {
        serviceAndResourceExist();
        when(serviceResourceRepository.findByServiceIdAndResourceId(salonService.getId(), resourceId))
                .thenReturn(Optional.empty());

        assertThat(queryService.resolveTerms(salonId, salonService.getId(), resourceId)).isEmpty();
    }

    @Test
    void resolveTerms_est_vide_si_la_ressource_n_est_plus_vivante() {
        when(serviceRepository.findAliveById(salonService.getId())).thenReturn(Optional.of(salonService));
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.empty());

        assertThat(queryService.resolveTerms(salonId, salonService.getId(), resourceId)).isEmpty();
    }

    @Test
    void resolveTerms_est_vide_pour_un_service_d_un_autre_salon() {
        when(serviceRepository.findAliveById(salonService.getId())).thenReturn(Optional.of(salonService));

        assertThat(queryService.resolveTerms(UUID.randomUUID(), salonService.getId(), resourceId)).isEmpty();
    }
}
