package com.apontaja.back.resource.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.apontaja.back.resource.domain.AvailabilityViolation.OUTSIDE_RESOURCE_HOURS;
import static com.apontaja.back.resource.domain.AvailabilityViolation.OUTSIDE_SALON_HOURS;
import static com.apontaja.back.resource.domain.AvailabilityViolation.RESOURCE_CLOSED;
import static com.apontaja.back.resource.domain.AvailabilityViolation.SALON_CLOSED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jeudi 2026-09-24 (CEST, UTC+2), lundi 2026-09-28. Passage à l'heure d'été en
 * Europe/Paris : dimanche 2026-03-29 (02:00 CET -> 03:00 CEST, 01:00Z). Retour
 * à l'heure d'hiver : dimanche 2026-10-25 (03:00 CEST -> 02:00 CET, 01:00Z).
 */
class AvailabilityEngineTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final List<DayOfWeek> WEEKDAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    // ---------- helpers ----------

    private static Instant paris(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(PARIS).toInstant();
    }

    private static TimeInterval parisSlot(String from, String to) {
        return new TimeInterval(paris(from), paris(to));
    }

    private static TimeInterval utc(String from, String to) {
        return new TimeInterval(Instant.parse(from), Instant.parse(to));
    }

    private static WeeklyWindow window(DayOfWeek day, String start, String end) {
        return new WeeklyWindow(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    /** Lundi-vendredi, une plage par couple "HH:mm-HH:mm". */
    private static List<WeeklyWindow> weekdays(String... ranges) {
        List<WeeklyWindow> hours = new ArrayList<>();
        for (DayOfWeek day : WEEKDAYS) {
            for (String range : ranges) {
                String[] parts = range.split("-");
                hours.add(window(day, parts[0], parts[1]));
            }
        }
        return hours;
    }

    private static List<WeeklyWindow> everyDay(String start, String end) {
        return java.util.Arrays.stream(DayOfWeek.values()).map(day -> window(day, start, end)).toList();
    }

    private static List<AvailabilityViolation> violations(TimeInterval slot, List<WeeklyWindow> salonHours,
            List<WeeklyWindow> resourceHours, List<TimeInterval> salonClosures, List<TimeInterval> resourceClosures) {
        return AvailabilityEngine.check(new AvailabilityInput(slot, PARIS, salonHours, resourceHours, salonClosures,
                resourceClosures)).violations();
    }

    private static List<AvailabilityViolation> salonOnly(TimeInterval slot, List<WeeklyWindow> salonHours) {
        return violations(slot, salonHours, List.of(), List.of(), List.of());
    }

    // ---------- horaires du salon : début ET fin ----------

    @Test
    void un_creneau_entierement_dans_les_horaires_est_disponible() {
        assertThat(salonOnly(parisSlot("2026-09-24T10:00", "2026-09-24T11:00"), weekdays("09:00-18:00"))).isEmpty();
    }

    @Test
    void les_bornes_exactes_d_ouverture_et_de_fermeture_sont_acceptees() {
        List<WeeklyWindow> hours = weekdays("09:00-18:00");

        assertThat(salonOnly(parisSlot("2026-09-24T09:00", "2026-09-24T10:00"), hours)).isEmpty();
        assertThat(salonOnly(parisSlot("2026-09-24T17:00", "2026-09-24T18:00"), hours)).isEmpty();
        assertThat(salonOnly(parisSlot("2026-09-24T09:00", "2026-09-24T18:00"), hours)).isEmpty();
    }

    @Test
    void un_debut_avant_l_ouverture_est_refuse() {
        assertThat(salonOnly(parisSlot("2026-09-24T08:30", "2026-09-24T09:30"), weekdays("09:00-18:00")))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void la_fin_est_verifiee_autant_que_le_debut_bug_historique() {
        List<WeeklyWindow> hours = weekdays("09:00-18:00");

        // début valide, fin après la fermeture : l'ancien code l'acceptait
        assertThat(salonOnly(parisSlot("2026-09-24T17:30", "2026-09-24T18:30"), hours))
                .containsExactly(OUTSIDE_SALON_HOURS);
        assertThat(salonOnly(parisSlot("2026-09-24T17:59", "2026-09-24T18:01"), hours))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void un_creneau_qui_enjambe_une_pause_est_refuse_meme_si_debut_et_fin_sont_ouverts() {
        List<WeeklyWindow> hours = weekdays("09:00-12:00", "14:00-18:00");

        assertThat(salonOnly(parisSlot("2026-09-24T11:00", "2026-09-24T15:00"), hours))
                .containsExactly(OUTSIDE_SALON_HOURS);
        assertThat(salonOnly(parisSlot("2026-09-24T10:00", "2026-09-24T11:00"), hours)).isEmpty();
        assertThat(salonOnly(parisSlot("2026-09-24T14:00", "2026-09-24T15:00"), hours)).isEmpty();
    }

    @Test
    void des_plages_contigues_sont_fusionnees() {
        List<WeeklyWindow> hours = weekdays("09:00-12:00", "12:00-14:00");

        assertThat(salonOnly(parisSlot("2026-09-24T11:00", "2026-09-24T13:00"), hours)).isEmpty();
    }

    @Test
    void un_jour_sans_horaire_ou_un_salon_sans_horaire_est_ferme() {
        List<WeeklyWindow> hours = weekdays("09:00-18:00");

        assertThat(salonOnly(parisSlot("2026-09-26T10:00", "2026-09-26T11:00"), hours))
                .containsExactly(OUTSIDE_SALON_HOURS);
        assertThat(salonOnly(parisSlot("2026-09-24T10:00", "2026-09-24T11:00"), List.of()))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void un_creneau_a_cheval_sur_minuit_n_est_jamais_couvert() {
        assertThat(salonOnly(parisSlot("2026-09-24T23:00", "2026-09-25T00:30"), everyDay("00:00", "23:59")))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void un_tres_long_creneau_multi_jours_est_refuse_sans_boucle_infinie() {
        assertThat(salonOnly(parisSlot("2026-09-24T08:00", "2026-09-27T08:00"), everyDay("00:00", "23:59")))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    // ---------- horaires de la ressource (Q3) ----------

    @Test
    void sans_horaires_propres_la_ressource_suit_le_salon() {
        assertThat(violations(parisSlot("2026-09-24T10:00", "2026-09-24T11:00"), weekdays("09:00-18:00"),
                List.of(), List.of(), List.of())).isEmpty();
    }

    @Test
    void des_horaires_de_ressource_plus_etroits_refusent_le_creneau_hors_ressource() {
        assertThat(violations(parisSlot("2026-09-24T09:30", "2026-09-24T10:30"), weekdays("09:00-18:00"),
                weekdays("10:00-16:00"), List.of(), List.of())).containsExactly(OUTSIDE_RESOURCE_HOURS);
    }

    @Test
    void la_ressource_reste_bornee_par_le_salon_meme_avec_des_horaires_plus_larges() {
        assertThat(violations(parisSlot("2026-09-24T08:00", "2026-09-24T09:00"), weekdays("09:00-18:00"),
                weekdays("07:00-20:00"), List.of(), List.of())).containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void une_ressource_avec_des_horaires_propres_est_fermee_les_jours_non_listes() {
        List<WeeklyWindow> mondayOnly = List.of(window(DayOfWeek.MONDAY, "09:00", "18:00"));

        assertThat(violations(parisSlot("2026-09-24T10:00", "2026-09-24T11:00"), weekdays("09:00-18:00"),
                mondayOnly, List.of(), List.of())).containsExactly(OUTSIDE_RESOURCE_HOURS);
        assertThat(violations(parisSlot("2026-09-28T10:00", "2026-09-28T11:00"), weekdays("09:00-18:00"),
                mondayOnly, List.of(), List.of())).isEmpty();
    }

    @Test
    void salon_et_ressource_peuvent_etre_refuses_ensemble() {
        assertThat(violations(parisSlot("2026-09-24T07:00", "2026-09-24T08:00"), weekdays("09:00-18:00"),
                weekdays("10:00-16:00"), List.of(), List.of()))
                        .containsExactly(OUTSIDE_SALON_HOURS, OUTSIDE_RESOURCE_HOURS);
    }

    // ---------- fermetures ----------

    @Test
    void une_fermeture_qui_recouvre_partiellement_le_creneau_le_refuse() {
        List<WeeklyWindow> hours = weekdays("09:00-18:00");
        TimeInterval slot = parisSlot("2026-09-24T10:00", "2026-09-24T11:00");

        assertThat(violations(slot, hours, List.of(), List.of(parisSlot("2026-09-24T10:30", "2026-09-24T12:00")),
                List.of())).containsExactly(SALON_CLOSED);
        assertThat(violations(slot, hours, List.of(), List.of(parisSlot("2026-09-24T09:00", "2026-09-24T10:01")),
                List.of())).containsExactly(SALON_CLOSED);
    }

    @Test
    void une_fermeture_incluse_dans_le_creneau_ou_le_contenant_le_refuse() {
        List<WeeklyWindow> hours = weekdays("09:00-18:00");
        TimeInterval slot = parisSlot("2026-09-24T10:00", "2026-09-24T12:00");

        assertThat(violations(slot, hours, List.of(), List.of(parisSlot("2026-09-24T10:30", "2026-09-24T11:00")),
                List.of())).containsExactly(SALON_CLOSED);
        assertThat(violations(slot, hours, List.of(), List.of(parisSlot("2026-09-24T00:00", "2026-09-25T00:00")),
                List.of())).containsExactly(SALON_CLOSED);
    }

    @Test
    void une_fermeture_adjacente_avant_ou_apres_n_empeche_pas_la_reservation() {
        List<WeeklyWindow> hours = weekdays("09:00-18:00");
        TimeInterval slot = parisSlot("2026-09-24T10:00", "2026-09-24T11:00");

        assertThat(violations(slot, hours, List.of(), List.of(parisSlot("2026-09-24T09:00", "2026-09-24T10:00"),
                parisSlot("2026-09-24T11:00", "2026-09-24T12:00")), List.of())).isEmpty();
    }

    @Test
    void une_fermeture_de_ressource_ne_produit_que_resource_closed() {
        assertThat(violations(parisSlot("2026-09-24T10:00", "2026-09-24T11:00"), weekdays("09:00-18:00"),
                List.of(), List.of(), List.of(parisSlot("2026-09-24T10:30", "2026-09-24T11:30"))))
                        .containsExactly(RESOURCE_CLOSED);
    }

    @Test
    void toutes_les_violations_peuvent_se_cumuler_dans_l_ordre_stable() {
        TimeInterval slot = parisSlot("2026-09-24T07:00", "2026-09-24T08:00");

        assertThat(violations(slot, weekdays("09:00-18:00"), weekdays("10:00-16:00"), List.of(slot), List.of(slot)))
                .containsExactly(OUTSIDE_SALON_HOURS, OUTSIDE_RESOURCE_HOURS, SALON_CLOSED, RESOURCE_CLOSED);
    }

    // ---------- DST (Europe/Paris) ----------

    @Test
    void les_memes_horaires_locaux_donnent_des_instants_differents_avant_et_apres_le_passage_a_l_heure_d_ete() {
        List<WeeklyWindow> hours = everyDay("09:00", "18:00");

        assertThat(AvailabilityEngine.windowsOn(PARIS, hours, LocalDate.parse("2026-03-28")))
                .containsExactly(utc("2026-03-28T08:00:00Z", "2026-03-28T17:00:00Z"));
        assertThat(AvailabilityEngine.windowsOn(PARIS, hours, LocalDate.parse("2026-03-29")))
                .containsExactly(utc("2026-03-29T07:00:00Z", "2026-03-29T16:00:00Z"));
    }

    @Test
    void les_memes_horaires_locaux_donnent_des_instants_differents_avant_et_apres_le_retour_a_l_heure_d_hiver() {
        List<WeeklyWindow> hours = everyDay("09:00", "18:00");

        assertThat(AvailabilityEngine.windowsOn(PARIS, hours, LocalDate.parse("2026-10-24")))
                .containsExactly(utc("2026-10-24T07:00:00Z", "2026-10-24T16:00:00Z"));
        assertThat(AvailabilityEngine.windowsOn(PARIS, hours, LocalDate.parse("2026-10-25")))
                .containsExactly(utc("2026-10-25T08:00:00Z", "2026-10-25T17:00:00Z"));
    }

    @Test
    void une_plage_de_nuit_dure_5_heures_reelles_au_printemps_et_7_heures_a_l_automne() {
        List<WeeklyWindow> night = everyDay("00:00", "06:00");

        assertThat(AvailabilityEngine.windowsOn(PARIS, night, LocalDate.parse("2026-03-29")))
                .containsExactly(utc("2026-03-28T23:00:00Z", "2026-03-29T04:00:00Z"));
        assertThat(AvailabilityEngine.windowsOn(PARIS, night, LocalDate.parse("2026-10-25")))
                .containsExactly(utc("2026-10-24T22:00:00Z", "2026-10-25T05:00:00Z"));
    }

    @Test
    void resolution_d_une_heure_locale_inexistante_ambigue_ou_normale() {
        // saut de printemps : 02:30 n'existe pas -> instant de la transition (03:00 CEST = 01:00Z)
        assertThat(AvailabilityEngine.resolveLocal(LocalDateTime.parse("2026-03-29T02:30:00"), PARIS))
                .isEqualTo(Instant.parse("2026-03-29T01:00:00Z"));
        // retour d'automne : 02:30 existe deux fois -> première occurrence (CEST = 00:30Z)
        assertThat(AvailabilityEngine.resolveLocal(LocalDateTime.parse("2026-10-25T02:30:00"), PARIS))
                .isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
        assertThat(AvailabilityEngine.resolveLocal(LocalDateTime.parse("2026-09-24T10:00:00"), PARIS))
                .isEqualTo(Instant.parse("2026-09-24T08:00:00Z"));
    }

    @Test
    void une_plage_qui_touche_l_heure_manquante_ou_repetee_a_sa_duree_reelle() {
        List<WeeklyWindow> hours = everyDay("01:00", "02:30");

        assertThat(AvailabilityEngine.windowsOn(PARIS, hours, LocalDate.parse("2026-03-29")))
                .containsExactly(utc("2026-03-29T00:00:00Z", "2026-03-29T01:00:00Z"));
        assertThat(AvailabilityEngine.windowsOn(PARIS, hours, LocalDate.parse("2026-10-25")))
                .containsExactly(utc("2026-10-24T23:00:00Z", "2026-10-25T00:30:00Z"));
    }

    @Test
    void une_plage_entierement_dans_l_heure_manquante_disparait() {
        assertThat(AvailabilityEngine.windowsOn(PARIS, everyDay("02:10", "02:50"), LocalDate.parse("2026-03-29")))
                .isEmpty();
        assertThat(salonOnly(utc("2026-03-29T00:30:00Z", "2026-03-29T00:45:00Z"),
                List.of(window(DayOfWeek.SUNDAY, "02:10", "02:50")))).containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void un_creneau_de_2_heures_reelles_a_cheval_sur_le_saut_de_printemps_est_couvert() {
        // 00:30Z-02:30Z = 01:30 CET -> 04:30 CEST (3 h d'horloge, 2 h réelles), plage 00:00-06:00
        assertThat(salonOnly(utc("2026-03-29T00:30:00Z", "2026-03-29T02:30:00Z"),
                List.of(window(DayOfWeek.SUNDAY, "00:00", "06:00")))).isEmpty();
    }

    @Test
    void un_creneau_a_cheval_sur_l_heure_repetee_de_l_automne_est_couvert() {
        // 00:30Z-01:30Z = 02:30 CEST -> 02:30 CET, plage 00:00-06:00
        assertThat(salonOnly(utc("2026-10-25T00:30:00Z", "2026-10-25T01:30:00Z"),
                List.of(window(DayOfWeek.SUNDAY, "00:00", "06:00")))).isEmpty();
    }

    @Test
    void la_derniere_heure_de_la_journee_du_changement_d_heure_reste_correcte() {
        List<WeeklyWindow> sunday = List.of(window(DayOfWeek.SUNDAY, "09:00", "18:00"));

        // le dimanche du passage à l'heure d'été, 17:00-18:00 locales = 15:00Z-16:00Z
        assertThat(salonOnly(utc("2026-03-29T15:00:00Z", "2026-03-29T16:00:00Z"), sunday)).isEmpty();
        // 16:00Z-17:00Z = 18:00-19:00 locales : hors horaires
        assertThat(salonOnly(utc("2026-03-29T16:00:00Z", "2026-03-29T17:00:00Z"), sunday))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    // ---------- changement de fuseau ----------

    private static List<AvailabilityViolation> inZone(ZoneId zone, TimeInterval slot, List<WeeklyWindow> hours) {
        return AvailabilityEngine.check(new AvailabilityInput(slot, zone, hours, List.of(), List.of(), List.of()))
                .violations();
    }

    @Test
    void le_meme_instant_est_ouvert_ou_ferme_selon_le_fuseau_du_salon() {
        List<WeeklyWindow> thursday = List.of(window(DayOfWeek.THURSDAY, "09:00", "18:00"));
        TimeInterval slot = utc("2026-09-24T07:00:00Z", "2026-09-24T08:00:00Z");

        assertThat(inZone(PARIS, slot, thursday)).isEmpty(); // 09:00-10:00 à Paris
        assertThat(inZone(ZoneId.of("America/New_York"), slot, thursday)) // 03:00-04:00 à New York
                .containsExactly(OUTSIDE_SALON_HOURS);
    }

    @Test
    void le_jour_local_depend_du_fuseau_de_part_et_d_autre_de_la_ligne_de_changement_de_date() {
        List<WeeklyWindow> fridayOnly = List.of(window(DayOfWeek.FRIDAY, "08:00", "18:00"));
        TimeInterval slot = utc("2026-09-24T20:00:00Z", "2026-09-24T21:00:00Z");

        assertThat(inZone(PARIS, slot, fridayOnly)).containsExactly(OUTSIDE_SALON_HOURS); // jeudi 22:00
        assertThat(inZone(ZoneId.of("Pacific/Auckland"), slot, fridayOnly)).isEmpty(); // vendredi 08:00
    }

    @Test
    void un_fuseau_a_decalage_de_30_minutes_est_gere() {
        List<WeeklyWindow> thursday = List.of(window(DayOfWeek.THURSDAY, "09:00", "18:00"));

        assertThat(inZone(ZoneId.of("Asia/Kolkata"), utc("2026-09-24T03:30:00Z", "2026-09-24T04:30:00Z"), thursday))
                .isEmpty();
        assertThat(inZone(ZoneId.of("Asia/Kolkata"), utc("2026-09-24T03:00:00Z", "2026-09-24T04:00:00Z"), thursday))
                .containsExactly(OUTSIDE_SALON_HOURS);
    }
}
