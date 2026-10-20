package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.BusyInterval;
import com.apontaja.back.appointment.domain.ResourceBusyRepository;
import com.apontaja.back.resource.application.AvailabilityPlan;
import com.apontaja.back.resource.application.AvailabilityService;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.salon.application.BookingSettings;
import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;
import com.apontaja.back.service.application.ResourceTerms;
import com.apontaja.back.service.application.ServiceOffering;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceSummary;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Journée de référence : mercredi 2099-06-10 à Paris (UTC+2). Le salon y est ouvert 09:00-12:00 locale,
 * soit 07:00Z-10:00Z ; prestation de 60 min, granularité 30 min.
 */
@ExtendWith(MockitoExtension.class)
class PublicBookingServiceTest {

    private static final UUID SALON = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SERVICE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID R1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID R2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final String DAY = "2099-06-10";
    private static final Instant OPEN = Instant.parse("2099-06-10T07:00:00Z");
    private static final Instant CLOSE = Instant.parse("2099-06-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2099-06-01T00:00:00Z");

    private static final AvailabilityPlan OPEN_9_TO_12 = (start, end) -> !start.isBefore(OPEN)
            && !end.isAfter(CLOSE);

    @Mock
    private BookingSettingsService bookingSettingsService;
    @Mock
    private SalonQueryService salonQueryService;
    @Mock
    private ServiceQueryService serviceQueryService;
    @Mock
    private ResourceQueryService resourceQueryService;
    @Mock
    private AvailabilityService availabilityService;
    @Mock
    private ResourceBusyRepository resourceBusyRepository;

    private PublicBookingService service(Instant now) {
        return new PublicBookingService(bookingSettingsService, salonQueryService, serviceQueryService,
                resourceQueryService, availabilityService, resourceBusyRepository,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static SalonSummary salon() {
        return new SalonSummary(SALON, UUID.randomUUID(), "Salon Test", "1 rue du Test", "06140", "Vence",
                "France", "0400000000", "Europe/Paris");
    }

    private static BookingSettings settings(boolean enabled, int granularity, int minNotice, int horizonDays) {
        return new BookingSettings(SALON, enabled, false, granularity, minNotice, horizonDays, 24);
    }

    private void givenBookable(BookingSettings settings) {
        when(salonQueryService.findAliveById(SALON)).thenReturn(Optional.of(salon()));
        when(bookingSettingsService.get(SALON)).thenReturn(settings);
    }

    private void givenOffering(ResourceTerms... terms) {
        ServiceSummary summary = new ServiceSummary(SERVICE, SALON, "Coupe", "Coupe femme", 60, 3000, NOW);
        when(serviceQueryService.findOffering(SALON, SERVICE))
                .thenReturn(Optional.of(new ServiceOffering(summary, List.of(terms))));
    }

    /** ResourceSummary est un record du domaine resource : on le simule plutôt que d'en deviner le constructeur. */
    private void givenResourceNames(ResourceSummary... resources) {
        when(resourceQueryService.findAliveBySalonId(SALON)).thenReturn(List.of(resources));
    }

    private static ResourceSummary resource(UUID id, String name) {
        ResourceSummary resource = mock(ResourceSummary.class);
        lenient().when(resource.resourceId()).thenReturn(id);
        lenient().when(resource.name()).thenReturn(name);
        return resource;
    }

    private static List<Instant> startsOf(PublicBooking.Slots slots) {
        return slots.days().stream().flatMap(day -> day.slots().stream()).map(PublicBooking.Slot::startAt).toList();
    }

    @Test
    void salon_inconnu_ou_sans_reservation_en_ligne_donne_le_meme_introuvable() {
        when(salonQueryService.findAliveById(SALON)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(NOW).salon(SALON)).isInstanceOf(PublicBookingNotFoundException.class);

        when(salonQueryService.findAliveById(SALON)).thenReturn(Optional.of(salon()));
        when(bookingSettingsService.get(SALON)).thenReturn(settings(false, 30, 0, 365));
        assertThatThrownBy(() -> service(NOW).salon(SALON)).isInstanceOf(PublicBookingNotFoundException.class);
        assertThatThrownBy(() -> service(NOW).services(SALON)).isInstanceOf(PublicBookingNotFoundException.class);
        assertThatThrownBy(() -> service(NOW).slots(SALON, SERVICE, null, DAY, DAY))
                .isInstanceOf(PublicBookingNotFoundException.class);
    }

    @Test
    void la_fiche_publique_expose_le_salon_et_les_reglages_utiles_au_client() {
        givenBookable(new BookingSettings(SALON, true, true, 30, 90, 45, 12));

        PublicBooking.SalonInfo info = service(NOW).salon(SALON);

        assertThat(info).isEqualTo(new PublicBooking.SalonInfo(SALON, "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "0400000000", "Europe/Paris", true, 90, 45, 12));
    }

    @Test
    void la_liste_des_prestations_ne_garde_que_celles_qui_ont_une_ressource() {
        givenBookable(settings(true, 30, 0, 365));
        givenResourceNames(resource(R1, "Marie"));
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        when(serviceQueryService.findOfferingsBySalonId(SALON)).thenReturn(List.of(
                new ServiceOffering(new ServiceSummary(SERVICE, SALON, "Coupe", null, 60, 3000, NOW),
                        List.of(new ResourceTerms(R1, 3500, 45))),
                new ServiceOffering(new ServiceSummary(other, SALON, "Sans ressource", null, 30, 1000, NOW),
                        List.of())));

        List<PublicBooking.Offering> offerings = service(NOW).services(SALON);

        assertThat(offerings).containsExactly(new PublicBooking.Offering(SERVICE, "Coupe", null, 60, 3000,
                List.of(new PublicBooking.Resource(R1, "Marie", 3500, 45))));
    }

    @Test
    void propose_les_debuts_ou_le_creneau_tient_entierement_dans_les_horaires() {
        givenBookable(settings(true, 30, 0, 365));
        givenOffering(new ResourceTerms(R1, 3000, 60));
        givenResourceNames(resource(R1, "Marie"));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of());
        when(availabilityService.plan(eq(SALON), eq(R1), any(), any())).thenReturn(OPEN_9_TO_12);

        PublicBooking.Slots slots = service(NOW).slots(SALON, SERVICE, null, DAY, DAY);

        assertThat(slots.timezone()).isEqualTo("Europe/Paris");
        assertThat(slots.days()).hasSize(1);
        assertThat(slots.days().get(0).date()).isEqualTo(DAY);
        assertThat(startsOf(slots)).containsExactly(Instant.parse("2099-06-10T07:00:00Z"),
                Instant.parse("2099-06-10T07:30:00Z"), Instant.parse("2099-06-10T08:00:00Z"),
                Instant.parse("2099-06-10T08:30:00Z"), Instant.parse("2099-06-10T09:00:00Z"));
        assertThat(slots.resources()).containsExactly(new PublicBooking.Resource(R1, "Marie", 3000, 60));
    }

    @Test
    void les_rdv_existants_retirent_les_creneaux_qui_les_recouvrent() {
        givenBookable(settings(true, 30, 0, 365));
        givenOffering(new ResourceTerms(R1, 3000, 60));
        givenResourceNames(resource(R1, "Marie"));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of(
                new BusyInterval(R1, Instant.parse("2099-06-10T08:00:00Z"), Instant.parse("2099-06-10T09:00:00Z"))));
        when(availabilityService.plan(eq(SALON), eq(R1), any(), any())).thenReturn(OPEN_9_TO_12);

        PublicBooking.Slots slots = service(NOW).slots(SALON, SERVICE, null, DAY, DAY);

        // 07:00-08:00 et 09:00-10:00 touchent le RDV 08:00-09:00 sans le recouvrir.
        assertThat(startsOf(slots)).containsExactly(Instant.parse("2099-06-10T07:00:00Z"),
                Instant.parse("2099-06-10T09:00:00Z"));
    }

    @Test
    void sans_preference_chaque_debut_n_apparait_qu_une_fois_avec_ses_ressources_libres() {
        givenBookable(settings(true, 30, 0, 365));
        givenOffering(new ResourceTerms(R1, 3000, 60), new ResourceTerms(R2, 3000, 60));
        givenResourceNames(resource(R1, "Marie"), resource(R2, "Paul"));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of(
                new BusyInterval(R1, Instant.parse("2099-06-10T08:00:00Z"), Instant.parse("2099-06-10T09:00:00Z"))));
        when(availabilityService.plan(eq(SALON), any(), any(), any())).thenReturn(OPEN_9_TO_12);

        PublicBooking.Slots slots = service(NOW).slots(SALON, SERVICE, null, DAY, DAY);

        List<PublicBooking.Slot> all = slots.days().get(0).slots();
        assertThat(all).hasSize(5);
        assertThat(all.get(0).resourceIds()).containsExactly(R1, R2);
        assertThat(all.get(1).resourceIds()).containsExactly(R2);
        assertThat(all.get(2).resourceIds()).containsExactly(R2);
        assertThat(all.get(3).resourceIds()).containsExactly(R2);
        assertThat(all.get(4).resourceIds()).containsExactly(R1, R2);
    }

    @Test
    void une_ressource_choisie_restreint_les_creneaux_a_cette_ressource() {
        givenBookable(settings(true, 30, 0, 365));
        givenOffering(new ResourceTerms(R1, 3000, 60), new ResourceTerms(R2, 4000, 60));
        givenResourceNames(resource(R1, "Marie"), resource(R2, "Paul"));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of());
        when(availabilityService.plan(eq(SALON), eq(R2), any(), any())).thenReturn(OPEN_9_TO_12);

        PublicBooking.Slots slots = service(NOW).slots(SALON, SERVICE, R2, DAY, DAY);

        assertThat(slots.resources()).containsExactly(new PublicBooking.Resource(R2, "Paul", 4000, 60));
        assertThat(slots.days().get(0).slots()).allSatisfy(slot -> assertThat(slot.resourceIds()).containsExactly(R2));
    }

    @Test
    void la_duree_effective_depend_de_la_ressource() {
        givenBookable(settings(true, 30, 0, 365));
        givenOffering(new ResourceTerms(R1, 3000, 120));
        givenResourceNames(resource(R1, "Marie"));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of());
        when(availabilityService.plan(eq(SALON), eq(R1), any(), any())).thenReturn(OPEN_9_TO_12);

        PublicBooking.Slots slots = service(NOW).slots(SALON, SERVICE, null, DAY, DAY);

        // 2 h dans une ouverture de 3 h : seuls les débuts 07:00, 07:30 et 08:00 (UTC) tiennent.
        assertThat(startsOf(slots)).containsExactly(Instant.parse("2099-06-10T07:00:00Z"),
                Instant.parse("2099-06-10T07:30:00Z"), Instant.parse("2099-06-10T08:00:00Z"));
    }

    @Test
    void respecte_le_delai_minimum_de_reservation() {
        givenBookable(settings(true, 30, 90, 365));
        givenOffering(new ResourceTerms(R1, 3000, 60));
        givenResourceNames(resource(R1, "Marie"));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of());
        when(availabilityService.plan(eq(SALON), eq(R1), any(), any())).thenReturn(OPEN_9_TO_12);

        // 06:30Z + 90 min = 08:00Z : les débuts 07:00 et 07:30 sont trop proches.
        PublicBooking.Slots slots = service(Instant.parse("2099-06-10T06:30:00Z")).slots(SALON, SERVICE, null, DAY,
                DAY);

        assertThat(startsOf(slots)).containsExactly(Instant.parse("2099-06-10T08:00:00Z"),
                Instant.parse("2099-06-10T08:30:00Z"), Instant.parse("2099-06-10T09:00:00Z"));
    }

    @Test
    void ne_propose_rien_au_dela_de_l_horizon_et_n_interroge_ni_horaires_ni_rdv() {
        givenBookable(settings(true, 30, 0, 5));
        givenOffering(new ResourceTerms(R1, 3000, 60));
        givenResourceNames(resource(R1, "Marie"));

        PublicBooking.Slots slots = service(NOW).slots(SALON, SERVICE, null, DAY, DAY);

        assertThat(slots.days()).isEmpty();
        assertThat(slots.resources()).hasSize(1);
    }

    @Test
    void une_ressource_non_associee_ou_une_prestation_inconnue_donne_introuvable() {
        givenBookable(settings(true, 30, 0, 365));
        givenOffering(new ResourceTerms(R1, 3000, 60));

        assertThatThrownBy(() -> service(NOW).slots(SALON, SERVICE, R2, DAY, DAY))
                .isInstanceOf(PublicBookingNotFoundException.class);

        UUID unknown = UUID.fromString("00000000-0000-0000-0000-0000000000b9");
        when(serviceQueryService.findOffering(SALON, unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(NOW).slots(SALON, unknown, null, DAY, DAY))
                .isInstanceOf(PublicBookingNotFoundException.class);
    }

    @Test
    void refuse_les_dates_absentes_illisibles_inversees_ou_trop_etendues() {
        givenBookable(settings(true, 30, 0, 365));

        assertThatThrownBy(() -> service(NOW).slots(SALON, SERVICE, null, null, DAY))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service(NOW).slots(SALON, SERVICE, null, "hier", DAY))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> service(NOW).slots(SALON, SERVICE, null, "2099-06-11", "2099-06-10"))
                .isInstanceOf(InvalidTimeRangeException.class);
        // 2099-06-01 -> 2099-07-02 = 32 jours : refusé ; 2099-07-01 = 31 jours : accepté (voir test suivant).
        assertThatThrownBy(() -> service(NOW).slots(SALON, SERVICE, null, "2099-06-01", "2099-07-02"))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void accepte_une_periode_de_31_jours() {
        givenBookable(settings(true, 30, 0, 5));
        givenOffering(new ResourceTerms(R1, 3000, 60));
        givenResourceNames(resource(R1, "Marie"));

        // Horloge après la période : aucun début candidat, donc ni horaires ni RDV à charger.
        PublicBooking.Slots slots = service(Instant.parse("2100-01-01T00:00:00Z")).slots(SALON, SERVICE, null,
                "2099-06-01", "2099-07-01");

        assertThat(slots.days()).isEmpty();
    }
}
