package com.apontaja.back.resource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.shared.domain.IdGenerator;

/** Garde-fou de la tranche 8b : pas de fermeture par-dessus des RDV actifs. */
@ExtendWith(MockitoExtension.class)
class ClosureManagementServiceGuardTest {

    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");
    private static final String START_TEXT = "2099-01-05T08:00:00Z";
    private static final String END_TEXT = "2099-01-05T12:00:00Z";
    private static final Instant START = Instant.parse(START_TEXT);
    private static final Instant END = Instant.parse(END_TEXT);

    @Mock
    private ClosureRepository closureRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private ClosureConflictCheck conflictCheck;

    private ClosureManagementService service;
    private final UUID salonId = UUID.randomUUID();
    private final ClosureCommand command = new ClosureCommand(START_TEXT, END_TEXT, null);

    @BeforeEach
    void setUp() {
        service = new ClosureManagementService(closureRepository, resourceRepository, idGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC), conflictCheck);
    }

    private static BlockingAppointment blocking() {
        return new BlockingAppointment(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2099-01-05T09:00:00Z"),
                Instant.parse("2099-01-05T10:00:00Z"));
    }

    @Test
    void refusesToCreateASalonClosureOverActiveAppointments() {
        when(conflictCheck.findBlockingAppointments(salonId, null, START, END)).thenReturn(List.of(blocking()));

        assertThatThrownBy(() -> service.create(salonId, null, command))
                .isInstanceOfSatisfying(ClosureBlockedByAppointmentsException.class,
                        ex -> assertThat(ex.count()).isEqualTo(1));

        verify(closureRepository, never()).save(any(Closure.class));
    }

    @Test
    void checksOnlyTheResourceAppointmentsForAResourceClosure() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findAliveById(resourceId))
                .thenReturn(Optional.of(new Resource(resourceId, salonId, "R", ResourceType.EMPLOYEE, NOW)));
        when(conflictCheck.findBlockingAppointments(salonId, resourceId, START, END))
                .thenReturn(List.of(blocking()));

        assertThatThrownBy(() -> service.create(salonId, resourceId, command))
                .isInstanceOf(ClosureBlockedByAppointmentsException.class);

        verify(closureRepository, never()).save(any(Closure.class));
    }

    @Test
    void createsTheClosureWhenNothingBlocks() {
        when(conflictCheck.findBlockingAppointments(salonId, null, START, END)).thenReturn(List.of());
        when(idGenerator.generate()).thenReturn(UUID.randomUUID());
        when(closureRepository.save(any(Closure.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(salonId, null, command);

        verify(closureRepository).save(any(Closure.class));
    }

    @Test
    void refusesToMoveAClosureOverActiveAppointmentsAndLeavesItUntouched() {
        Instant oldStart = Instant.parse("2099-03-01T00:00:00Z");
        Closure closure = Closure.forSalon(UUID.randomUUID(), salonId, oldStart,
                Instant.parse("2099-03-02T00:00:00Z"), null, NOW);
        when(closureRepository.findById(closure.getId())).thenReturn(Optional.of(closure));
        when(conflictCheck.findBlockingAppointments(salonId, null, START, END)).thenReturn(List.of(blocking()));

        assertThatThrownBy(() -> service.update(salonId, null, closure.getId(), command))
                .isInstanceOf(ClosureBlockedByAppointmentsException.class);

        verify(closureRepository, never()).save(any(Closure.class));
        assertThat(closure.getStartAt()).isEqualTo(oldStart);
    }

    @Test
    void neverBlocksTheDeletionOfAClosure() {
        Closure closure = Closure.forSalon(UUID.randomUUID(), salonId, START, END, null, NOW);
        when(closureRepository.findById(closure.getId())).thenReturn(Optional.of(closure));

        service.delete(salonId, null, closure.getId());

        verify(closureRepository).delete(closure);
        verifyNoInteractions(conflictCheck);
    }
}
