package com.apontaja.back.appointment.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.resource.domain.ScheduleRepository;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceRepository;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agenda (liste filtrée) et transitions de statut (Phase 3, tranche 7). Dates
 * fixes très lointaines (2099, jamais passées) ou très anciennes (2020,
 * toujours passées) selon le besoin du no-show, pour ne dépendre d'aucune
 * horloge réelle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentAgendaAndStatusIT {

    private static final String APPOINTMENTS = "/api/salons/{salonId}/appointments";
    private static final String APPOINTMENT = APPOINTMENTS + "/{id}";
    private static final String FUTURE_10H = "2099-01-05T10:00:00+01:00";
    private static final String FUTURE_12H = "2099-01-05T12:00:00+01:00";
    private static final String PAST_10H = "2020-01-06T10:00:00+01:00";

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
    private SalonServiceRepository salonServiceRepository;

    @Autowired
    private ServiceResourceRepository serviceResourceRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private SalonCustomerLinkRepository salonCustomerLinkRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AppointmentResourceRepository appointmentResourceRepository;

    @Autowired
    private IdGenerator idGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Setup(UUID salonId, UUID resourceId, UUID serviceId, UUID customerId, String employee,
            String owner) {
    }

    private UUID createAccount() {
        return accountRepository
                .save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", Instant.now()))
                .getId();
    }

    private String authOf(UUID salonId, StaffRole role) {
        UUID accountId = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private UUID createResource(UUID salonId, UUID serviceId) {
        UUID resourceId = resourceRepository.save(new Resource(idGenerator.generate(), salonId,
                "R " + UUID.randomUUID(), ResourceType.EMPLOYEE, Instant.now())).getId();
        serviceResourceRepository.save(new ServiceResource(serviceId, resourceId, null, null));
        return resourceId;
    }

    private UUID createCustomer(UUID salonId) {
        CustomerProfile profile = customerProfileRepository.save(new CustomerProfile(idGenerator.generate(), "Alice",
                "Martin", "alice+" + UUID.randomUUID() + "@example.com", null, Instant.now()));
        salonCustomerLinkRepository.save(new SalonCustomerLink(idGenerator.generate(), salonId, profile.getId(),
                CustomerLinkSource.MANUAL, null, Instant.now()));
        return profile.getId();
    }

    private Setup setup() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        UUID salonId = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        scheduleRepository.saveAll(Arrays.stream(DayOfWeek.values()).map(day -> Schedule.forSalon(
                idGenerator.generate(), salonId, day, LocalTime.of(0, 0), LocalTime.of(23, 59), Instant.now()))
                .toList());
        UUID serviceId = salonServiceRepository.save(new SalonService(idGenerator.generate(), salonId, "Coupe",
                null, 60, 2500, Instant.now())).getId();
        UUID resourceId = createResource(salonId, serviceId);
        return new Setup(salonId, resourceId, serviceId, createCustomer(salonId),
                authOf(salonId, StaffRole.EMPLOYEE), authOf(salonId, StaffRole.OWNER));
    }

    private String book(Setup s, UUID resourceId, String startAt) throws Exception {
        String body = """
                {"customerProfileId":"%s","serviceId":"%s","resourceId":"%s","startAt":"%s"}
                """.formatted(s.customerId(), s.serviceId(), resourceId, startAt);
        String response = mockMvc
                .perform(post(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("appointmentId").asText();
    }

    private void cancel(Setup s, String appointmentId) throws Exception {
        mockMvc.perform(patch(APPOINTMENT + "/cancel", s.salonId(), appointmentId)
                .header("Authorization", s.employee()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    // ---------- transitions ----------

    @Test
    void confirmed_puis_completed_et_toute_transition_ulterieure_est_refusee() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), FUTURE_10H);

        mockMvc.perform(patch(APPOINTMENT + "/complete", s.salonId(), id).header("Authorization", s.employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(patch(APPOINTMENT + "/complete", s.salonId(), id).header("Authorization", s.employee()))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(APPOINTMENT + "/cancel", s.salonId(), id).header("Authorization", s.employee())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mockMvc.perform(patch(APPOINTMENT + "/confirm", s.salonId(), id).header("Authorization", s.employee()))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_est_refuse_sur_un_rdv_deja_confirme_a_la_creation() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), FUTURE_10H);

        mockMvc.perform(patch(APPOINTMENT + "/confirm", s.salonId(), id).header("Authorization", s.employee()))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_fonctionne_sur_un_rdv_scheduled_insere_directement() throws Exception {
        Setup s = setup();
        Appointment scheduled = new Appointment(idGenerator.generate(), s.salonId(), s.customerId(), s.serviceId(),
                Instant.parse("2099-02-02T09:00:00Z"), Instant.parse("2099-02-02T10:00:00Z"), 2500, 60,
                Instant.now());
        try {
            var field = Appointment.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(scheduled, AppointmentStatus.SCHEDULED);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        appointmentRepository.save(scheduled);
        appointmentResourceRepository.save(new AppointmentResource(scheduled.getId(), s.resourceId()));

        mockMvc.perform(patch(APPOINTMENT + "/confirm", s.salonId(), scheduled.getId())
                .header("Authorization", s.employee())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void no_show_est_refuse_avant_le_debut_et_accepte_apres() throws Exception {
        Setup s = setup();
        String future = book(s, s.resourceId(), FUTURE_10H);
        String past = book(s, s.resourceId(), PAST_10H);

        mockMvc.perform(patch(APPOINTMENT + "/no-show", s.salonId(), future).header("Authorization", s.employee()))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(APPOINTMENT + "/no-show", s.salonId(), past).header("Authorization", s.employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void annuler_libere_le_creneau_et_rend_possible_la_suppression_de_la_ressource_et_de_la_prestation()
            throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), FUTURE_10H);

        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", s.salonId(), s.resourceId())
                .header("Authorization", s.owner())).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/salons/{salonId}/services/{id}", s.salonId(), s.serviceId())
                .header("Authorization", s.owner())).andExpect(status().isConflict());

        mockMvc.perform(patch(APPOINTMENT + "/cancel", s.salonId(), id).header("Authorization", s.employee())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Client malade\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // le même créneau sur la même ressource est de nouveau réservable
        book(s, s.resourceId(), FUTURE_10H);
        cancel(s, book(s, s.resourceId(), FUTURE_12H));

        // ... mais tant qu'un RDV actif subsiste, la ressource reste protégée
        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", s.salonId(), s.resourceId())
                .header("Authorization", s.owner())).andExpect(status().isConflict());
    }

    @Test
    void la_suppression_devient_possible_quand_tous_les_rdv_sont_annules() throws Exception {
        Setup s = setup();
        cancel(s, book(s, s.resourceId(), FUTURE_10H));

        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", s.salonId(), s.resourceId())
                .header("Authorization", s.owner())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/salons/{salonId}/services/{id}", s.salonId(), s.serviceId())
                .header("Authorization", s.owner())).andExpect(status().isNoContent());
    }

    @Test
    void un_rdv_d_un_autre_salon_est_introuvable_pour_toutes_les_transitions() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String appointmentOfB = book(b, b.resourceId(), FUTURE_10H);

        for (String action : List.of("confirm", "complete", "no-show")) {
            mockMvc.perform(patch(APPOINTMENT + "/" + action, a.salonId(), appointmentOfB)
                    .header("Authorization", a.owner())).andExpect(status().isNotFound());
        }
        mockMvc.perform(patch(APPOINTMENT + "/cancel", a.salonId(), appointmentOfB)
                .header("Authorization", a.owner()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void les_transitions_exigent_une_authentification_et_l_acces_au_salon() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String id = book(a, a.resourceId(), FUTURE_10H);

        mockMvc.perform(patch(APPOINTMENT + "/complete", a.salonId(), id)).andExpect(status().isForbidden());
        mockMvc.perform(patch(APPOINTMENT + "/complete", a.salonId(), id).header("Authorization", b.owner()))
                .andExpect(status().isForbidden());
    }

    // ---------- agenda ----------

    @Test
    void l_agenda_liste_les_rdv_tries_par_debut_et_filtre_par_ressource() throws Exception {
        Setup s = setup();
        UUID otherResource = createResource(s.salonId(), s.serviceId());
        book(s, s.resourceId(), FUTURE_12H);
        book(s, s.resourceId(), FUTURE_10H);
        book(s, otherResource, FUTURE_10H);

        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .param("resourceId", s.resourceId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startAt").value("2099-01-05T09:00:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2099-01-05T11:00:00Z"))
                .andExpect(jsonPath("$[0].resourceId").value(s.resourceId().toString()));
    }

    @Test
    void l_agenda_filtre_par_recouvrement_avec_des_bornes_adjacentes_exclues() throws Exception {
        Setup s = setup();
        book(s, s.resourceId(), FUTURE_10H); // 09:00Z -> 10:00Z

        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .param("from", "2099-01-05T09:30:00Z").param("to", "2099-01-05T12:00:00Z"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .param("from", "2099-01-05T10:00:00Z").param("to", "2099-01-05T12:00:00Z"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .param("from", "2099-01-05T07:00:00Z").param("to", "2099-01-05T09:00:00Z"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void l_agenda_inclut_les_rdv_annules_avec_leur_statut() throws Exception {
        Setup s = setup();
        cancel(s, book(s, s.resourceId(), FUTURE_10H));

        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void l_agenda_ne_montre_jamais_les_rdv_d_un_autre_salon() throws Exception {
        Setup a = setup();
        Setup b = setup();
        book(b, b.resourceId(), FUTURE_10H);

        mockMvc.perform(get(APPOINTMENTS, a.salonId()).header("Authorization", a.employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(APPOINTMENTS, a.salonId()).header("Authorization", a.employee())
                .param("resourceId", b.resourceId().toString())).andExpect(status().isNotFound());
        mockMvc.perform(get(APPOINTMENTS, b.salonId()).header("Authorization", a.employee()))
                .andExpect(status().isForbidden());
    }

    @Test
    void l_agenda_refuse_des_bornes_invalides() throws Exception {
        Setup s = setup();

        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .param("from", "demain")).andExpect(status().isBadRequest());
        mockMvc.perform(get(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .param("from", "2099-01-06T00:00:00Z").param("to", "2099-01-05T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
