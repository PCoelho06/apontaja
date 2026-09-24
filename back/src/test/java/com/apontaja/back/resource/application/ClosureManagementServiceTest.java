package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class ClosureManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    @Mock
    private ClosureRepository closureRepository;

    @Mock
    private ResourceRepository resourceRepository;

    private ClosureManagementService service;

    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClosureManagementService(closureRepository, resourceRepository, UUID::randomUUID,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void resourceExistsInSalon(UUID resourceId) {
        when(resourceRepository.findAliveById(resourceId))
                .thenReturn(Optional.of(new Resource(resourceId, salonId, "Chaise", ResourceType.EMPLOYEE, NOW)));
    }

    @Test
    void normalise_un_offset_en_instant_utc_et_une_raison_vide() {
        when(closureRepository.save(any(Closure.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClosureSummary result = service.create(salonId, null,
                new ClosureCommand("2026-08-15T00:00:00+02:00", "2026-08-16T00:00:00+02:00", "  "));

        assertThat(result.startAt()).isEqualTo(Instant.parse("2026-08-14T22:00:00Z"));
        assertThat(result.endAt()).isEqualTo(Instant.parse("2026-08-15T22:00:00Z"));
        assertThat(result.reason()).isNull();
        assertThat(result.salonId()).isEqualTo(salonId);
        assertThat(result.resourceId()).isNull();
    }

    @Test
    void refuse_une_fin_non_posterieure_ou_une_date_illisible() {
        assertThatThrownBy(() -> service.create(salonId, null,
                new ClosureCommand("2026-08-15T10:00:00Z", "2026-08-15T10:00:00Z", null)))
                        .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.create(salonId, null, new ClosureCommand("demain", "après", null)))
                .isInstanceOf(InvalidTimeRangeException.class);

        verify(closureRepository, never()).save(any());
    }

    @Test
    void cree_une_fermeture_de_ressource_seulement_si_elle_appartient_au_salon() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findAliveById(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(salonId, resourceId,
                new ClosureCommand("2026-08-15T10:00:00Z", "2026-08-15T12:00:00Z", null)))
                        .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void une_fermeture_de_salon_n_est_pas_accessible_via_une_ressource_et_inversement() {
        UUID resourceId = UUID.randomUUID();
        resourceExistsInSalon(resourceId);
        Closure salonClosure = Closure.forSalon(UUID.randomUUID(), salonId, NOW, NOW.plusSeconds(60), null, NOW);
        when(closureRepository.findById(salonClosure.getId())).thenReturn(Optional.of(salonClosure));

        assertThatThrownBy(() -> service.delete(salonId, resourceId, salonClosure.getId()))
                .isInstanceOf(ClosureNotFoundException.class);
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), null, salonClosure.getId()))
                .isInstanceOf(ClosureNotFoundException.class);

        verify(closureRepository, never()).delete(any());
    }

    @Test
    void met_a_jour_et_supprime_une_fermeture_du_salon() {
        Closure closure = Closure.forSalon(UUID.randomUUID(), salonId, NOW, NOW.plusSeconds(60), null, NOW);
        when(closureRepository.findById(closure.getId())).thenReturn(Optional.of(closure));
        when(closureRepository.save(closure)).thenReturn(closure);

        ClosureSummary updated = service.update(salonId, null, closure.getId(),
                new ClosureCommand("2026-10-01T08:00:00Z", "2026-10-01T09:00:00Z", " Travaux "));
        service.delete(salonId, null, closure.getId());

        assertThat(updated.reason()).isEqualTo("Travaux");
        verify(closureRepository).delete(closure);
    }

    @Test
    void la_liste_applique_des_bornes_ouvertes_par_defaut_et_refuse_from_apres_to() {
        when(closureRepository.findOverlappingBySalonId(any(), any(), any())).thenReturn(List.of());

        assertThat(service.list(salonId, null, null, null)).isEmpty();
        assertThatThrownBy(() -> service.list(salonId, null, "2026-09-25T00:00:00Z", "2026-09-24T00:00:00Z"))
                .isInstanceOf(InvalidTimeRangeException.class);
    }
}
