package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.resource.domain.ScheduleOwnerLock;
import com.apontaja.back.resource.domain.ScheduleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleManagementServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ScheduleOwnerLock ownerLock;

    private ScheduleManagementService service;

    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ScheduleManagementService(scheduleRepository, resourceRepository, ownerLock,
                UUID::randomUUID, Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC));
    }

    private static ScheduleSlotCommand slot(String day, String start, String end) {
        return new ScheduleSlotCommand(day, start, end);
    }

    @Test
    void remplace_le_salon_sous_verrou_avec_tri_par_jour_puis_heure() {
        List<ScheduleSlotSummary> result = service.replaceSalonSchedule(salonId,
                List.of(slot("tuesday", "09:00", "12:00"), slot("MONDAY", "14:00", "18:00"),
                        slot("MONDAY", "09:00", "12:00")));

        assertThat(result.stream().map(s -> s.dayOfWeek() + " " + s.startTime()).toList())
                .containsExactly("MONDAY 09:00", "MONDAY 14:00", "TUESDAY 09:00");
        InOrder inOrder = inOrder(ownerLock, scheduleRepository);
        inOrder.verify(ownerLock).lock(salonId);
        inOrder.verify(scheduleRepository).deleteBySalonId(salonId);
        inOrder.verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    void des_plages_adjacentes_sont_acceptees() {
        List<ScheduleSlotSummary> result = service.replaceSalonSchedule(salonId,
                List.of(slot("MONDAY", "09:00", "12:00"), slot("MONDAY", "12:00", "14:00")));

        assertThat(result).hasSize(2);
    }

    @Test
    void un_chevauchement_partiel_ou_total_est_refuse_avant_tout_verrou_ou_ecriture() {
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId,
                List.of(slot("MONDAY", "09:00", "12:00"), slot("MONDAY", "11:59", "14:00"))))
                        .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId,
                List.of(slot("MONDAY", "09:00", "18:00"), slot("MONDAY", "10:00", "11:00"))))
                        .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId,
                List.of(slot("MONDAY", "09:00", "12:00"), slot("MONDAY", "09:00", "12:00"))))
                        .isInstanceOf(InvalidTimeRangeException.class);

        verify(ownerLock, never()).lock(any());
        verify(scheduleRepository, never()).deleteBySalonId(any());
    }

    @Test
    void les_memes_heures_sur_deux_jours_differents_ne_se_chevauchent_pas() {
        assertThat(service.replaceSalonSchedule(salonId,
                List.of(slot("MONDAY", "09:00", "12:00"), slot("TUESDAY", "09:00", "12:00")))).hasSize(2);
    }

    @Test
    void refuse_jour_heure_ou_plage_invalides() {
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId, List.of(slot("LUNDI", "09:00", "12:00"))))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId, List.of(slot("MONDAY", "9h", "12:00"))))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId, List.of(slot("MONDAY", "24:00", "23:00"))))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId, List.of(slot("MONDAY", "12:00", "12:00"))))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.replaceSalonSchedule(salonId, List.of(slot("MONDAY", "18:00", "09:00"))))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void une_liste_vide_efface_sans_rien_inserer() {
        assertThat(service.replaceSalonSchedule(salonId, List.of())).isEmpty();

        verify(scheduleRepository).deleteBySalonId(salonId);
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    void remplace_les_horaires_d_une_ressource_du_salon() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findAliveById(resourceId)).thenReturn(Optional.of(
                new Resource(resourceId, salonId, "Chaise", ResourceType.EMPLOYEE, Instant.now())));

        service.replaceResourceSchedule(salonId, resourceId, List.of(slot("FRIDAY", "10:00", "16:00")));

        InOrder inOrder = inOrder(ownerLock, scheduleRepository);
        inOrder.verify(ownerLock).lock(resourceId);
        inOrder.verify(scheduleRepository).deleteByResourceId(resourceId);
        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    void refuse_une_ressource_d_un_autre_salon_ou_inconnue() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findAliveById(resourceId)).thenReturn(Optional.of(
                new Resource(resourceId, UUID.randomUUID(), "Chaise", ResourceType.EMPLOYEE, Instant.now())));

        assertThatThrownBy(() -> service.replaceResourceSchedule(salonId, resourceId, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getResourceSchedule(salonId, resourceId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ownerLock, never()).lock(any());
    }

    @Test
    void lit_le_planning_du_salon_trie() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        when(scheduleRepository.findBySalonId(salonId)).thenReturn(List.of(
                Schedule.forSalon(id, salonId, DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), now),
                Schedule.forSalon(id, salonId, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), now)));

        assertThat(service.getSalonSchedule(salonId)).extracting(ScheduleSlotSummary::dayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
    }
}
