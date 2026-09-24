package com.apontaja.back.service.application;

import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceManagementServiceTest {

    @Mock
    private SalonServiceRepository serviceRepository;

    @Mock
    private ObjectProvider<ServiceDeletionCheck> deletionChecks;

    @Mock
    private ServiceDeletionCheck deletionCheck;

    private ServiceManagementService service;

    private final Instant fixedNow = Instant.parse("2026-09-24T10:00:00Z");
    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ServiceManagementService(serviceRepository, () -> new UUID(0, 1),
                Clock.fixed(fixedNow, ZoneOffset.UTC), deletionChecks);
    }

    private SalonService existing(String name) {
        return new SalonService(UUID.randomUUID(), salonId, name, null, 30, 2500, fixedNow);
    }

    @Test
    void create_rejette_un_nom_deja_pris() {
        when(serviceRepository.findAliveBySalonIdAndName(salonId, "Coupe")).thenReturn(Optional.of(existing("x")));

        assertThatThrownBy(() -> service.create(salonId, new ServiceDetails(" Coupe ", null, 30, 2500)))
                .isInstanceOf(ServiceNameAlreadyUsedException.class);

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void create_normalise_le_nom_et_une_description_vide() {
        when(serviceRepository.findAliveBySalonIdAndName(salonId, "Coupe")).thenReturn(Optional.empty());
        when(serviceRepository.save(any(SalonService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceSummary result = service.create(salonId, new ServiceDetails(" Coupe ", "   ", 30, 2500));

        assertThat(result.name()).isEqualTo("Coupe");
        assertThat(result.description()).isNull();
    }

    @Test
    void update_avec_son_propre_nom_n_est_pas_un_conflit() {
        SalonService existing = existing("Coupe");
        when(serviceRepository.findAliveById(existing.getId())).thenReturn(Optional.of(existing));
        when(serviceRepository.findAliveBySalonIdAndName(salonId, "Coupe")).thenReturn(Optional.of(existing));
        when(serviceRepository.save(existing)).thenReturn(existing);

        ServiceSummary result = service.update(salonId, existing.getId(), new ServiceDetails("Coupe", null, 45, 3000));

        assertThat(result.defaultDurationMinutes()).isEqualTo(45);
        assertThat(result.defaultPriceCents()).isEqualTo(3000);
    }

    @Test
    void delete_est_bloque_si_des_rdv_actifs_existent() {
        SalonService existing = existing("Coupe");
        when(serviceRepository.findAliveById(existing.getId())).thenReturn(Optional.of(existing));
        when(deletionChecks.orderedStream()).thenAnswer(invocation -> Stream.of(deletionCheck));
        when(deletionCheck.hasBlockingAppointments(existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(salonId, existing.getId())).isInstanceOf(ServiceInUseException.class);

        assertThat(existing.isDeleted()).isFalse();
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void delete_marque_supprime_si_rien_ne_bloque() {
        SalonService existing = existing("Coupe");
        when(serviceRepository.findAliveById(existing.getId())).thenReturn(Optional.of(existing));
        when(deletionChecks.orderedStream()).thenAnswer(invocation -> Stream.of(deletionCheck));
        when(deletionCheck.hasBlockingAppointments(existing.getId())).thenReturn(false);

        service.delete(salonId, existing.getId());

        assertThat(existing.isDeleted()).isTrue();
        verify(serviceRepository).save(existing);
    }

    @Test
    void delete_refuse_une_prestation_d_un_autre_salon() {
        SalonService existing = existing("Coupe");
        when(serviceRepository.findAliveById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), existing.getId()))
                .isInstanceOf(ServiceNotFoundException.class);
    }
}
