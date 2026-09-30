package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.application.StaffMemberQueryService;
import com.apontaja.back.shared.domain.IdGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceManagementServiceStaffLinkTest {

    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");

    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private ObjectProvider<ResourceDeletionCheck> deletionChecks;
    @Mock
    private StaffMemberQueryService staffMemberQueryService;

    private ResourceManagementService service;
    private final UUID salonId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ResourceManagementService(resourceRepository, idGenerator, Clock.fixed(NOW, ZoneOffset.UTC),
                deletionChecks, staffMemberQueryService);
    }

    private Resource linkedResource(String name, UUID staffMembershipId) {
        Resource resource = new Resource(UUID.randomUUID(), salonId, name, ResourceType.EMPLOYEE, NOW);
        resource.linkToStaff(staffMembershipId);
        return resource;
    }

    private void stubFreeCreation(String name) {
        when(resourceRepository.findAliveBySalonIdAndName(salonId, name)).thenReturn(Optional.empty());
        when(idGenerator.generate()).thenReturn(UUID.randomUUID());
    }

    @Test
    void createsAnEmployeeResourceLinkedToAnExistingStaffMember() {
        when(staffMemberQueryService.existsAliveInSalon(salonId, staffId)).thenReturn(true);
        when(resourceRepository.findAliveBySalonId(salonId)).thenReturn(List.of());
        stubFreeCreation("Lea");
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        ResourceSummary result = service.create(new CreateResourceCommand(salonId, "Lea", "EMPLOYEE"), staffId);

        assertThat(result.staffMembershipId()).isEqualTo(staffId);
    }

    @Test
    void rejectsALinkOnAMachineBeforeAnyLookup() {
        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, "Four", "MACHINE"), staffId))
                .isInstanceOf(InvalidResourceStaffLinkException.class);

        verifyNoInteractions(staffMemberQueryService, resourceRepository);
    }

    @Test
    void rejectsAnUnknownOrForeignStaffMember() {
        when(staffMemberQueryService.existsAliveInSalon(salonId, staffId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, "Lea", "EMPLOYEE"), staffId))
                .isInstanceOf(LinkedStaffMemberNotFoundException.class);

        verifyNoInteractions(resourceRepository);
    }

    @Test
    void rejectsAStaffMemberAlreadyLinkedToAnotherResource() {
        when(staffMemberQueryService.existsAliveInSalon(salonId, staffId)).thenReturn(true);
        when(resourceRepository.findAliveBySalonIdAndName(salonId, "Lea")).thenReturn(Optional.empty());
        when(resourceRepository.findAliveBySalonId(salonId)).thenReturn(List.of(linkedResource("Autre", staffId)));

        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, "Lea", "EMPLOYEE"), staffId))
                .isInstanceOf(StaffMemberAlreadyLinkedException.class);

        verify(resourceRepository, never()).save(any(Resource.class));
    }

    @Test
    void updatingKeepsTheSameLinkWithoutConflictingWithItself() {
        Resource resource = linkedResource("Lea", staffId);
        when(resourceRepository.findAliveById(resource.getId())).thenReturn(Optional.of(resource));
        when(staffMemberQueryService.existsAliveInSalon(salonId, staffId)).thenReturn(true);
        when(resourceRepository.findAliveBySalonIdAndName(salonId, "Lea 2")).thenReturn(Optional.empty());
        when(resourceRepository.findAliveBySalonId(salonId)).thenReturn(List.of(resource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        ResourceSummary result = service.update(salonId, resource.getId(), "Lea 2", "EMPLOYEE", staffId);

        assertThat(result.name()).isEqualTo("Lea 2");
        assertThat(result.staffMembershipId()).isEqualTo(staffId);
    }

    @Test
    void updatingWithoutStaffMemberRemovesTheLinkWithoutLookup() {
        Resource resource = linkedResource("Lea", staffId);
        when(resourceRepository.findAliveById(resource.getId())).thenReturn(Optional.of(resource));
        when(resourceRepository.findAliveBySalonIdAndName(salonId, "Lea")).thenReturn(Optional.empty());
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        ResourceSummary result = service.update(salonId, resource.getId(), "Lea", "EMPLOYEE", null);

        assertThat(result.staffMembershipId()).isNull();
        verifyNoInteractions(staffMemberQueryService);
    }

    @Test
    void mapsTheStaffLinkIndexViolationToAStaffLinkConflict() {
        when(staffMemberQueryService.existsAliveInSalon(salonId, staffId)).thenReturn(true);
        when(resourceRepository.findAliveBySalonId(salonId)).thenReturn(List.of());
        stubFreeCreation("Lea");
        String cause = "ERROR: duplicate key value violates unique constraint \"uq_resource_staff_membership_alive\"";
        when(resourceRepository.save(any(Resource.class)))
                .thenThrow(new DataIntegrityViolationException("conflit", new IllegalStateException(cause)));

        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, "Lea", "EMPLOYEE"), staffId))
                .isInstanceOf(StaffMemberAlreadyLinkedException.class);
    }

    @Test
    void mapsAnyOtherIntegrityViolationToANameConflict() {
        stubFreeCreation("Lea");
        when(resourceRepository.save(any(Resource.class))).thenThrow(new DataIntegrityViolationException("nom pris"));

        assertThatThrownBy(() -> service.create(new CreateResourceCommand(salonId, "Lea", "EMPLOYEE")))
                .isInstanceOf(ResourceNameAlreadyUsedException.class);
    }
}
