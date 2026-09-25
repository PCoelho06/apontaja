package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.AvailabilityViolation;
import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.resource.domain.ScheduleRepository;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T06:00:00Z");
    // jeudi 10:00-11:00 à Paris (CEST)
    private static final Instant START = Instant.parse("2026-09-24T08:00:00Z");
    private static final Instant END = Instant.parse("2026-09-24T09:00:00Z");

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ClosureRepository closureRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private SalonQueryService salonQueryService;

    private AvailabilityService service;

    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AvailabilityService(scheduleRepository, closureRepository, resourceRepository,
                salonQueryService);
    }

    private void salonAndResourceExist(String timezone) {
        when(salonQueryService.findAliveById(salonId)).thenReturn(Optional.of(new SalonSummary(salonId,
                UUID.randomUUID(), "Salon", "1 rue", "06140", "Vence", "France", null, timezone)));
        when(resourceRepository.findAliveById(resourceId))
                .thenReturn(Optional.of(new Resource(resourceId, salonId, "Chaise", ResourceType.EMPLOYEE, NOW)));
    }

    private Schedule thursday(boolean forSalon, String start, String end) {
        return forSalon
                ? Schedule.forSalon(UUID.randomUUID(), salonId, DayOfWeek.THURSDAY, LocalTime.parse(start),
                        LocalTime.parse(end), NOW)
                : Schedule.forResource(UUID.randomUUID(), resourceId, DayOfWeek.THURSDAY, LocalTime.parse(start),
                        LocalTime.parse(end), NOW);
    }

    @Test
    void un_creneau_dans_les_horaires_sans_fermeture_est_disponible_et_les_fermetures_sont_lues_sur_le_creneau() {
        salonAndResourceExist("Europe/Paris");
        when(scheduleRepository.findBySalonId(salonId)).thenReturn(List.of(thursday(true, "09:00", "18:00")));

        AvailabilityCheck result = service.check(salonId, resourceId, START, END);

        assertThat(result.available()).isTrue();
        assertThat(result.issues()).isEmpty();
        verify(closureRepository).findOverlappingBySalonId(salonId, START, END);
        verify(closureRepository).findOverlappingByResourceId(resourceId, START, END);
    }

    @Test
    void traduit_toutes_les_violations_du_moteur_en_ordre_stable() {
        salonAndResourceExist("Europe/Paris");
        when(scheduleRepository.findBySalonId(salonId)).thenReturn(List.of(thursday(true, "09:00", "18:00")));
        when(scheduleRepository.findByResourceId(resourceId)).thenReturn(List.of(thursday(false, "14:00", "18:00")));
        when(closureRepository.findOverlappingBySalonId(salonId, START, END))
                .thenReturn(List.of(Closure.forSalon(UUID.randomUUID(), salonId, START, END, null, NOW)));
        when(closureRepository.findOverlappingByResourceId(resourceId, START, END))
                .thenReturn(List.of(Closure.forResource(UUID.randomUUID(), resourceId, START, END, null, NOW)));

        AvailabilityCheck result = service.check(salonId, resourceId, START, END);

        assertThat(result.available()).isFalse();
        assertThat(result.issues()).containsExactly(AvailabilityIssue.OUTSIDE_RESOURCE_HOURS,
                AvailabilityIssue.SALON_CLOSED, AvailabilityIssue.RESOURCE_CLOSED);
    }

    @Test
    void utilise_le_fuseau_du_salon() {
        salonAndResourceExist("America/New_York");
        when(scheduleRepository.findBySalonId(salonId)).thenReturn(List.of(thursday(true, "09:00", "18:00")));

        // 08:00Z-09:00Z = 04:00-05:00 à New York : fermé
        assertThat(service.check(salonId, resourceId, START, END).issues())
                .containsExactly(AvailabilityIssue.OUTSIDE_SALON_HOURS);
    }

    @Test
    void refuse_une_ressource_d_un_autre_salon_ou_un_salon_inconnu() {
        when(salonQueryService.findAliveById(salonId)).thenReturn(Optional.of(new SalonSummary(salonId,
                UUID.randomUUID(), "Salon", "1 rue", "06140", "Vence", "France", null, "Europe/Paris")));
        when(resourceRepository.findAliveById(resourceId)).thenReturn(Optional.of(
                new Resource(resourceId, UUID.randomUUID(), "Chaise", ResourceType.EMPLOYEE, NOW)));

        assertThatThrownBy(() -> service.check(salonId, resourceId, START, END))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.check(UUID.randomUUID(), resourceId, START, END))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refuse_un_fuseau_de_salon_invalide() {
        salonAndResourceExist("Paris");

        assertThatThrownBy(() -> service.check(salonId, resourceId, START, END))
                .isInstanceOf(InvalidSalonTimezoneException.class);
    }

    @Test
    void refuse_une_fin_non_posterieure_au_debut() {
        assertThatThrownBy(() -> service.check(salonId, resourceId, END, START))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service.check(salonId, resourceId, START, START))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void les_issues_applicatives_miroitent_exactement_les_violations_du_domaine() {
        assertThat(Arrays.stream(AvailabilityIssue.values()).map(Enum::name).toList())
                .isEqualTo(Arrays.stream(AvailabilityViolation.values()).map(Enum::name).toList());
    }
}
