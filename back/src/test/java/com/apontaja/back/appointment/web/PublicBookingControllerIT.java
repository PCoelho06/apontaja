package com.apontaja.back.appointment.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints publics, SANS en-tête Authorization. Le service lit l'horloge réelle (délai minimum et
 * horizon) : l'IT vise donc un lundi à ~14 jours (réglages : délai 0, horizon 365). La logique
 * temporelle fine (délai, horizon, DST) est couverte avec des dates fixes par PublicBookingServiceTest
 * et SlotGridTest. Fixtures : horaires, réglages, client et RDV via l'API ; prestation et association par
 * SQL natif (états posés sans passer par l'API).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class PublicBookingControllerIT {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final String SETTINGS = "/api/salons/{salonId}/booking-settings";
    private static final String PUBLIC = "/api/public/salons/{salonId}";
    private static final String SLOTS = PUBLIC + "/services/{serviceId}/slots";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccessTokenIssuer accessTokenIssuer;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private SalonRepository salonRepository;
    @Autowired
    private StaffMembershipRepository staffMembershipRepository;
    @Autowired
    private ResourceRepository resourceRepository;
    @Autowired
    private ClosureRepository closureRepository;
    @Autowired
    private IdGenerator idGenerator;
    @Autowired
    private JdbcTemplate jdbc;

    private record Fixture(UUID salonId, UUID resourceId, UUID serviceId, String ownerAuth, LocalDate monday) {
    }

    private Fixture fixture(boolean bookingEnabled) throws Exception {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        UUID salonId = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        UUID accountId = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Test", "User", "hash", Instant.now())).getId();
        staffMembershipRepository.save(
                new StaffMembership(idGenerator.generate(), accountId, salonId, StaffRole.OWNER, Instant.now()));
        String auth = "Bearer " + accessTokenIssuer.issue(accountId);

        UUID resourceId = newResource(salonId, "Marie");
        UUID serviceId = newService(salonId, "Coupe", 60);
        link(serviceId, resourceId);

        LocalDate monday = LocalDate.now(PARIS).plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        mockMvc.perform(put("/api/salons/{salonId}/schedule", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"09:00\",\"endTime\":\"12:00\"}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(SETTINGS, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(("{\"onlineBookingEnabled\":%s,\"autoConfirm\":false,\"slotGranularityMinutes\":30,"
                        + "\"minNoticeMinutes\":0,\"horizonDays\":365,\"cancellationDeadlineHours\":24}")
                        .formatted(bookingEnabled)))
                .andExpect(status().isOk());
        return new Fixture(salonId, resourceId, serviceId, auth, monday);
    }

    private UUID newResource(UUID salonId, String name) {
        return resourceRepository
                .save(new Resource(idGenerator.generate(), salonId, name, ResourceType.EMPLOYEE, Instant.now()))
                .getId();
    }

    private UUID newService(UUID salonId, String name, int durationMinutes) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service (id, salon_id, name, default_duration_minutes, default_price_cents) "
                + "VALUES (?, ?, ?, ?, 3000)", id, salonId, name, durationMinutes);
        return id;
    }

    private void link(UUID serviceId, UUID resourceId) {
        jdbc.update("INSERT INTO service_resource (service_id, resource_id) VALUES (?, ?)", serviceId, resourceId);
    }

    private static Instant parisTime(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(PARIS).toInstant();
    }

    private MockHttpServletRequestBuilder slots(Fixture f, LocalDate from, LocalDate to) {
        return get(SLOTS, f.salonId(), f.serviceId()).param("from", from.toString()).param("to", to.toString());
    }

    private void bookAsStaff(Fixture f, Instant startAt) throws Exception {
        String customerJson = mockMvc
                .perform(post("/api/salons/{salonId}/customers", f.salonId()).header("Authorization", f.ownerAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"lastName\":\"Martin\",\"email\":\"alice."
                                + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String customerId = JsonPath.read(customerJson, "$.customerProfileId");
        mockMvc.perform(post("/api/salons/{salonId}/appointments", f.salonId())
                .header("Authorization", f.ownerAuth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerProfileId\":\"%s\",\"serviceId\":\"%s\",\"resourceId\":\"%s\",\"startAt\":\"%s\"}"
                        .formatted(customerId, f.serviceId(), f.resourceId(), startAt)))
                .andExpect(status().isCreated());
    }

    @Test
    void la_fiche_publique_est_lisible_sans_authentification_et_sans_donnee_interne() throws Exception {
        Fixture f = fixture(true);

        mockMvc.perform(get(PUBLIC, f.salonId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.salonId").value(f.salonId().toString()))
                .andExpect(jsonPath("$.name").value("Salon Test")).andExpect(jsonPath("$.city").value("Vence"))
                .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.autoConfirm").value(false))
                .andExpect(jsonPath("$.minNoticeMinutes").value(0)).andExpect(jsonPath("$.horizonDays").value(365))
                .andExpect(jsonPath("$.cancellationDeadlineHours").value(24))
                .andExpect(jsonPath("$.organizationId").doesNotExist());
    }

    @Test
    void reservation_desactivee_salon_inconnu_ou_identifiant_mal_forme_donnent_le_meme_404() throws Exception {
        Fixture disabled = fixture(false);
        Fixture other = fixture(true);

        mockMvc.perform(get(PUBLIC, disabled.salonId())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Introuvable."));
        mockMvc.perform(get(PUBLIC + "/services", disabled.salonId())).andExpect(status().isNotFound());
        mockMvc.perform(get(SLOTS, disabled.salonId(), disabled.serviceId()).param("from", "2099-01-01")
                .param("to", "2099-01-01")).andExpect(status().isNotFound());

        mockMvc.perform(get(PUBLIC, UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Introuvable."));
        mockMvc.perform(get(PUBLIC + "/services", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/salons/pas-un-uuid")).andExpect(status().isNotFound());
        mockMvc.perform(get(SLOTS, other.salonId(), "pas-un-uuid")).andExpect(status().isNotFound());
        // Prestation d'un autre salon.
        mockMvc.perform(get(SLOTS, other.salonId(), disabled.serviceId()).param("from", "2099-01-01")
                .param("to", "2099-01-01")).andExpect(status().isNotFound());
    }

    @Test
    void la_liste_des_prestations_ne_contient_que_les_prestations_reservables_avec_leurs_ressources()
            throws Exception {
        Fixture f = fixture(true);
        newService(f.salonId(), "Sans ressource", 30);

        mockMvc.perform(get(PUBLIC + "/services", f.salonId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Coupe"))
                .andExpect(jsonPath("$[0].defaultDurationMinutes").value(60))
                .andExpect(jsonPath("$[0].defaultPriceCents").value(3000))
                .andExpect(jsonPath("$[0].resources.length()").value(1))
                .andExpect(jsonPath("$[0].resources[0].resourceId").value(f.resourceId().toString()))
                .andExpect(jsonPath("$[0].resources[0].name").value("Marie"))
                .andExpect(jsonPath("$[0].resources[0].priceCents").value(3000))
                .andExpect(jsonPath("$[0].resources[0].durationMinutes").value(60))
                .andExpect(jsonPath("$[0].resources[0].staffMembershipId").doesNotExist());
    }

    @Test
    void les_creneaux_suivent_les_horaires_du_salon_a_la_granularite_reglee() throws Exception {
        Fixture f = fixture(true);

        mockMvc.perform(slots(f, f.monday(), f.monday())).andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.resources[0].name").value("Marie"))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].date").value(f.monday().toString()))
                .andExpect(jsonPath("$.days[0].slots.length()").value(5))
                .andExpect(jsonPath("$.days[0].slots[0].startAt").value(parisTime(f.monday(), 9, 0).toString()))
                .andExpect(jsonPath("$.days[0].slots[4].startAt").value(parisTime(f.monday(), 11, 0).toString()))
                .andExpect(jsonPath("$.days[0].slots[0].resourceIds[0]").value(f.resourceId().toString()));

        // Mardi : le salon n'a aucun horaire, donc aucun créneau (et aucun jour listé).
        mockMvc.perform(slots(f, f.monday().plusDays(1), f.monday().plusDays(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(0));
    }

    @Test
    void un_rdv_existant_retire_les_creneaux_qu_il_recouvre_sans_rien_reveler_du_client() throws Exception {
        Fixture f = fixture(true);
        bookAsStaff(f, parisTime(f.monday(), 10, 0));

        mockMvc.perform(slots(f, f.monday(), f.monday())).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].slots.length()").value(2))
                .andExpect(jsonPath("$.days[0].slots[0].startAt").value(parisTime(f.monday(), 9, 0).toString()))
                .andExpect(jsonPath("$.days[0].slots[1].startAt").value(parisTime(f.monday(), 11, 0).toString()))
                .andExpect(content().string(not(containsString("Alice"))))
                .andExpect(content().string(not(containsString("alice"))));
    }

    @Test
    void une_fermeture_du_salon_retire_les_creneaux_qu_elle_recouvre() throws Exception {
        Fixture f = fixture(true);
        closureRepository.save(Closure.forSalon(idGenerator.generate(), f.salonId(), parisTime(f.monday(), 10, 0),
                parisTime(f.monday(), 11, 0), null, Instant.now()));

        mockMvc.perform(slots(f, f.monday(), f.monday())).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].slots.length()").value(2));
    }

    @Test
    void sans_preference_les_ressources_sont_fusionnees_et_une_ressource_choisie_restreint() throws Exception {
        Fixture f = fixture(true);
        UUID second = newResource(f.salonId(), "Paul");
        link(f.serviceId(), second);
        bookAsStaff(f, parisTime(f.monday(), 10, 0));

        // Marie est prise de 10:00 à 11:00, Paul est libre : 5 débuts, dont 3 ne proposent que Paul.
        mockMvc.perform(slots(f, f.monday(), f.monday())).andExpect(status().isOk())
                .andExpect(jsonPath("$.resources.length()").value(2))
                .andExpect(jsonPath("$.days[0].slots.length()").value(5))
                .andExpect(jsonPath("$.days[0].slots[1].resourceIds.length()").value(1))
                .andExpect(jsonPath("$.days[0].slots[1].resourceIds[0]").value(second.toString()))
                .andExpect(jsonPath("$.days[0].slots[0].resourceIds.length()").value(2));

        mockMvc.perform(slots(f, f.monday(), f.monday()).param("resourceId", f.resourceId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resources.length()").value(1))
                .andExpect(jsonPath("$.days[0].slots.length()").value(2));
    }

    @Test
    void une_ressource_non_associee_a_la_prestation_donne_404() throws Exception {
        Fixture f = fixture(true);
        UUID unlinked = newResource(f.salonId(), "Julie");

        mockMvc.perform(slots(f, f.monday(), f.monday()).param("resourceId", unlinked.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(slots(f, f.monday(), f.monday()).param("resourceId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuse_les_parametres_de_periode_invalides_par_un_400() throws Exception {
        Fixture f = fixture(true);

        mockMvc.perform(get(SLOTS, f.salonId(), f.serviceId())).andExpect(status().isBadRequest());
        mockMvc.perform(get(SLOTS, f.salonId(), f.serviceId()).param("from", "hier").param("to", "demain"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(slots(f, f.monday().plusDays(1), f.monday())).andExpect(status().isBadRequest());
        mockMvc.perform(slots(f, f.monday(), f.monday().plusDays(31))).andExpect(status().isBadRequest());
        mockMvc.perform(slots(f, f.monday(), f.monday().plusDays(30))).andExpect(status().isOk());
    }
}
