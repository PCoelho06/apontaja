package com.apontaja.back.appointment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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

/**
 * Déplacement d'un RDV (Phase 3, tranche 8). Dates fixes (2099 = toujours
 * futur), aucune dépendance à l'horloge réelle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentRescheduleIT {

    private static final String APPOINTMENTS = "/api/salons/{salonId}/appointments";
    private static final String APPOINTMENT = APPOINTMENTS + "/{id}";
    private static final String RESCHEDULE = APPOINTMENT + "/reschedule";
    private static final String H10 = "2099-01-05T10:00:00+01:00"; // 09:00Z -> 10:00Z
    private static final String H10_30 = "2099-01-05T10:30:00+01:00"; // 09:30Z -> 10:30Z
    private static final String H12 = "2099-01-05T12:00:00+01:00"; // 11:00Z -> 12:00Z
    private static final String H14 = "2099-01-05T14:00:00+01:00"; // 13:00Z -> 14:00Z
    private static final String H23_30 = "2099-01-05T23:30:00+01:00"; // finit après 23:59 locale

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

    /**
     * Salon ouvert 00:00-23:59 tous les jours, prestation 60 min / 2500 cts, une
     * ressource, un client.
     */
    private Setup setup() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        UUID salonId = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        scheduleRepository
                .saveAll(Arrays.stream(DayOfWeek.values()).map(day -> Schedule.forSalon(idGenerator.generate(), salonId,
                        day, LocalTime.of(0, 0), LocalTime.of(23, 59), Instant.now())).toList());
        UUID serviceId = salonServiceRepository
                .save(new SalonService(idGenerator.generate(), salonId, "Coupe", null, 60, 2500, Instant.now()))
                .getId();
        UUID resourceId = createResource(salonId, serviceId);
        return new Setup(salonId, resourceId, serviceId, createCustomer(salonId), authOf(salonId, StaffRole.EMPLOYEE),
                authOf(salonId, StaffRole.OWNER));
    }

    private ResultActions tryBook(Setup s, UUID resourceId, String startAt) throws Exception {
        String body = """
                {"customerProfileId":"%s","serviceId":"%s","resourceId":"%s","startAt":"%s"}
                """.formatted(s.customerId(), s.serviceId(), resourceId, startAt);
        return mockMvc.perform(post(APPOINTMENTS, s.salonId()).header("Authorization", s.employee())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String book(Setup s, UUID resourceId, String startAt) throws Exception {
        String response = tryBook(s, resourceId, startAt).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("appointmentId").asText();
    }

    private ResultActions reschedule(UUID salonId, String appointmentId, String auth, String startAt) throws Exception {
        return mockMvc.perform(patch(RESCHEDULE, salonId, appointmentId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"startAt\":\"" + startAt + "\"}"));
    }

    private void assertStillAt(Setup s, String appointmentId, String startAtUtc) throws Exception {
        mockMvc.perform(get(APPOINTMENT, s.salonId(), appointmentId).header("Authorization", s.employee()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startAt").value(startAtUtc));
    }

    @Test
    void un_employee_deplace_un_rdv_en_conservant_le_snapshot_et_en_recalculant_la_fin() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), H10);

        reschedule(s.salonId(), id, s.employee(), H14).andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentId").value(id)).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startAt").value("2099-01-05T13:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2099-01-05T14:00:00Z"))
                .andExpect(jsonPath("$.priceAtBookingCents").value(2500))
                .andExpect(jsonPath("$.durationAtBookingMinutes").value(60))
                .andExpect(jsonPath("$.resourceId").value(s.resourceId().toString()));

        assertStillAt(s, id, "2099-01-05T13:00:00Z");
    }

    @Test
    void le_deplacement_libere_l_ancien_creneau_et_occupe_le_nouveau() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), H10);
        reschedule(s.salonId(), id, s.employee(), H14).andExpect(status().isOk());

        // preuve que le trigger a recalculé appointment_resource : l'ancien créneau est
        // libre...
        book(s, s.resourceId(), H10);
        // ... et le nouveau est occupé
        tryBook(s, s.resourceId(), H14).andExpect(status().isConflict());
    }

    @Test
    void un_rdv_peut_glisser_a_l_interieur_de_son_propre_creneau_sans_conflit_avec_lui_meme() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), H10); // 09:00Z -> 10:00Z

        reschedule(s.salonId(), id, s.employee(), H10_30).andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value("2099-01-05T09:30:00Z"))
                .andExpect(jsonPath("$.endAt").value("2099-01-05T10:30:00Z"));
    }

    @Test
    void un_deplacement_qui_chevauche_un_autre_rdv_de_la_ressource_est_refuse_et_ne_change_rien() throws Exception {
        Setup s = setup();
        String moving = book(s, s.resourceId(), H10);
        book(s, s.resourceId(), H12); // 11:00Z -> 12:00Z

        // 11:30Z -> 12:30Z chevauche l'autre RDV ; l'agenda ne voit que la contrainte
        // PG
        reschedule(s.salonId(), moving, s.employee(), "2099-01-05T12:30:00+01:00").andExpect(status().isConflict());

        assertStillAt(s, moving, "2099-01-05T09:00:00Z");
    }

    @Test
    void des_bornes_adjacentes_ne_sont_pas_un_conflit_et_une_autre_ressource_n_est_pas_concernee() throws Exception {
        Setup s = setup();
        UUID otherResource = createResource(s.salonId(), s.serviceId());
        String moving = book(s, s.resourceId(), H10);
        book(s, s.resourceId(), H12); // 11:00Z -> 12:00Z
        book(s, otherResource, H14);

        // 10:00Z -> 11:00Z : collé au RDV de 11:00Z
        reschedule(s.salonId(), moving, s.employee(), "2099-01-05T11:00:00+01:00").andExpect(status().isOk());
        // même créneau que celui d'une autre ressource : sans incidence
        reschedule(s.salonId(), moving, s.employee(), H14).andExpect(status().isOk());
    }

    @Test
    void un_deplacement_hors_horaires_du_salon_est_refuse_avec_le_detail_des_violations() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), H10);

        // 23:30 + 60 min dépasse 23:59 (une minute de "trou" avant 00:00 le lendemain)
        reschedule(s.salonId(), id, s.employee(), H23_30).andExpect(status().isConflict())
                .andExpect(jsonPath("$.issues[0]").value("OUTSIDE_SALON_HOURS"));

        assertStillAt(s, id, "2099-01-05T09:00:00Z");
    }

    @Test
    void un_rdv_annule_ou_termine_ne_peut_pas_etre_deplace() throws Exception {
        Setup s = setup();
        String cancelled = book(s, s.resourceId(), H10);
        String completed = book(s, s.resourceId(), H12);

        mockMvc.perform(patch(APPOINTMENT + "/cancel", s.salonId(), cancelled).header("Authorization", s.employee())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(patch(APPOINTMENT + "/complete", s.salonId(), completed).header("Authorization", s.employee()))
                .andExpect(status().isOk());

        reschedule(s.salonId(), cancelled, s.employee(), H14).andExpect(status().isConflict());
        reschedule(s.salonId(), completed, s.employee(), H14).andExpect(status().isConflict());
    }

    @Test
    void un_rdv_scheduled_peut_etre_deplace_et_reste_scheduled() throws Exception {
        Setup s = setup();
        Appointment scheduled = new Appointment(idGenerator.generate(), s.salonId(), s.customerId(), s.serviceId(),
                Instant.parse("2099-02-02T09:00:00Z"), Instant.parse("2099-02-02T10:00:00Z"), 2500, 60, Instant.now());
        // Le constructeur pose toujours CONFIRMED : SCHEDULED n'est pas atteignable par
        // l'API en Phase 3.
        try {
            var field = Appointment.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(scheduled, AppointmentStatus.SCHEDULED);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        appointmentRepository.save(scheduled);
        appointmentResourceRepository.save(new AppointmentResource(scheduled.getId(), s.resourceId()));

        reschedule(s.salonId(), scheduled.getId().toString(), s.employee(), "2099-02-02T12:00:00+01:00")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.startAt").value("2099-02-02T11:00:00Z"));
    }

    @Test
    void une_date_illisible_est_refusee() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), H10);

        reschedule(s.salonId(), id, s.employee(), "pas-une-date").andExpect(status().isBadRequest());
        assertStillAt(s, id, "2099-01-05T09:00:00Z");
    }

    @Test
    void un_rdv_d_un_autre_salon_ou_inexistant_est_introuvable() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String appointmentOfB = book(b, b.resourceId(), H10);

        reschedule(a.salonId(), appointmentOfB, a.owner(), H14).andExpect(status().isNotFound());
        reschedule(a.salonId(), UUID.randomUUID().toString(), a.owner(), H14).andExpect(status().isNotFound());
        assertStillAt(b, appointmentOfB, "2099-01-05T09:00:00Z");
    }

    @Test
    void le_deplacement_exige_une_authentification_et_l_acces_au_salon() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String id = book(a, a.resourceId(), H10);
        String body = "{\"startAt\":\"" + H14 + "\"}";

        mockMvc.perform(patch(RESCHEDULE, a.salonId(), id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        reschedule(a.salonId(), id, b.owner(), H14).andExpect(status().isForbidden());
        reschedule(UUID.randomUUID(), id, a.owner(), H14).andExpect(status().isForbidden());
        assertStillAt(a, id, "2099-01-05T09:00:00Z");
    }

    @Test
    void deux_deplacements_concurrents_vers_le_meme_creneau_n_en_laissent_passer_qu_un() throws Exception {
        Setup s = setup();
        String first = book(s, s.resourceId(), H10);
        String second = book(s, s.resourceId(), H12);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> f1 = executor.submit(() -> {
                start.await();
                return reschedule(s.salonId(), first, s.employee(), H14).andReturn().getResponse().getStatus();
            });
            Future<Integer> f2 = executor.submit(() -> {
                start.await();
                return reschedule(s.salonId(), second, s.employee(), H14).andReturn().getResponse().getStatus();
            });
            start.countDown();

            List<Integer> statuses = List.of(f1.get(20, TimeUnit.SECONDS), f2.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }
}
