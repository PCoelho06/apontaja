package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;

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
class ResourceManagementServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ObjectProvider<ResourceDeletionCheck> deletionChecks;

    @Mock
    private ResourceDeletionCheck deletionCheck;

    private ResourceManagementService service;

    private final Instant fixedNow = Instant.parse("2026-09-24T10:00:00Z");
    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ResourceManagementService(resourceRepository, () -> new UUID(0, 1),
                Clock.fixed(fixedNow, ZoneOffset.UTC), deletionChecks);
    }

    private Resource existing(String name) {
        return new Resource(UUID.randomUUID(), salonId, name, ResourceType.EMPLOYEE, fixedNow);
    }

    @Test
    void create_rejette_un_nom_deja_pris() {
        when(resourceRepository.findAliveBySalonIdAndName(salonId, "Chaise 1")).thenReturn(Optional.of(existing("x")));

        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, " Chaise 1 ", "EMPLOYEE")))
                .isInstanceOf(ResourceNameAlreadyUsedException.class);

        verify(resourceRepository, never()).save(any());
    }

    @Test
    void create_rejette_un_type_invalide() {
        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, "Chaise", "ROBOT")))
                .isInstanceOf(InvalidResourceTypeException.class);
    }

    @Test
    void update_avec_son_propre_nom_n_est_pas_un_conflit() {
        Resource resource = existing("Chaise 1");
        when(resourceRepository.findAliveById(resource.getId())).thenReturn(Optional.of(resource));
        when(resourceRepository.findAliveBySalonIdAndName(salonId, "Chaise 1")).thenReturn(Optional.of(resource));
        when(resourceRepository.save(resource)).thenReturn(resource);

        ResourceSummary result = service.update(salonId, resource.getId(), "Chaise 1", "MACHINE");

        assertThat(result.type()).isEqualTo("MACHINE");
    }

    @Test
    void delete_est_bloque_si_des_rdv_actifs_existent() {
        Resource resource = existing("Chaise 1");
        when(resourceRepository.findAliveById(resource.getId())).thenReturn(Optional.of(resource));
        when(deletionChecks.orderedStream()).thenAnswer(invocation -> Stream.of(deletionCheck));
        when(deletionCheck.hasBlockingAppointments(resource.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(salonId, resource.getId())).isInstanceOf(ResourceInUseException.class);

        assertThat(resource.isDeleted()).isFalse();
        verify(resourceRepository, never()).save(any());
    }

    @Test
    void delete_marque_la_ressource_supprimee_si_rien_ne_bloque() {
        Resource resource = existing("Chaise 1");
        when(resourceRepository.findAliveById(resource.getId())).thenReturn(Optional.of(resource));
        when(deletionChecks.orderedStream()).thenAnswer(invocation -> Stream.of(deletionCheck));
        when(deletionCheck.hasBlockingAppointments(resource.getId())).thenReturn(false);

        service.delete(salonId, resource.getId());

        assertThat(resource.isDeleted()).isTrue();
        verify(resourceRepository).save(resource);
    }

    @Test
    void delete_refuse_une_ressource_d_un_autre_salon() {
        Resource resource = existing("Chaise 1");
        when(resourceRepository.findAliveById(resource.getId())).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), resource.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
