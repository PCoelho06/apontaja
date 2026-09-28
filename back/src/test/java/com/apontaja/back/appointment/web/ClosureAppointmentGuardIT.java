package com.apontaja.back.appointment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.UUID;

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
 * Tranche 8b : une fermeture (création ou modification) ne peut pas recouvrir
 * un RDV SCHEDULED/CONFIRMED. Dates fixes (2099), aucune horloge réelle.
 */
@SpringBootTest (webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc 
@Import(PostgresTestcontainersConfiguration.class)
class ClosureAppointmentGuardIT {

    private static final String APPOINTMENTS = "/api/salons/{salonId}/appointments";
    private static final String APPOINTMENT = APPOINTMENTS + "/{id}";
    private static final String SALON_CLOSURES = "/api/salons/{salonId}/closures";
    private static final String SALON_CLOSURE = SALON_CLOSURES + "/{id}";
    private static final String RESOURCE_CLOSURES = "/api/salons/{salonId}/resources/{resourceId}/closures";
    private static final String H10 = "2099-01-05T10:00:00+01:00"; // 09:00Z -> 10:00Z
    private static final String H12 = "2099-01-05T12:00:00+01:00"; // 11:00Z -> 12:00Z

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

    /** Salon ouvert 00:00-23:59 tous les jours, prestation 60 min, une ressource, un client. */
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

    private static String closureBody(String startAt, String endAt) {
        return "{\"startAt\":\"%s\",\"endAt\":\"%s\",\"reason\":\"Travaux\"}".formatted(startAt, endAt);
    }

    private ResultActions postSalonClosure(Setup s, String startAt, String endAt) throws Exception {
        return mockMvc.perform(post(SALON_CLOSURES, s.salonId()).header("Authorization", s.owner())
                .contentType(MediaType.APPLICATION_JSON).content(closureBody(startAt, endAt)));
    }

    private ResultActions postResourceClosure(Setup s, UUID resourceId, String startAt, String endAt)
            throws Exception {
        return mockMvc.perform(post(RESOURCE_CLOSURES, s.salonId(), resourceId).header("Authorization", s.owner())
                .contentType(MediaType.APPLICATION_JSON).content(closureBody(startAt, endAt)));
    }

    @Test
    void une_fermeture_de_salon_par_dessus_un_rdv_actif_est_refusee_avec_la_liste_des_rdv() throws Exception {
        Setup s = setup();
        String id = book(s, s.resourceId(), H10);

        postSalonClosure(s, "2099-01-05T08:30:00Z", "2099-01-05T09:30:00Z").andExpect(status().isConflict())
                .andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.appointments.length()").value(1))
                .andExpect(jsonPath("$.appointments[0].appointmentId").value(id))
                .andExpect(jsonPath("$.appointments[0].resourceId").value(s.resourceId().toString()))
                .andExpect(jsonPath("$.appointments[0].startAt").value("2099-01-05T09:00:00Z"))
                .andExpect(jsonPath("$.appointments[0].endAt").value("2099-01-05T10:00:00Z"));

        mockMvc.perform(get(SALON_CLOSURES, s.salonId()).header("Authorization", s.owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void des_bornes_adjacentes_ne_bloquent_pas() throws Exception {
        Setup s = setup();
        book(s, s.resourceId(), H10); // 09:00Z -> 10:00Z

        postSalonClosure(s, "2099-01-05T10:00:00Z", "2099-01-05T11:00:00Z").andExpect(status().isCreated());
        postSalonClosure(s, "2099-01-05T08:00:00Z", "2099-01-05T09:00:00Z").andExpect(status().isCreated());
    }

    @Test
    void une_fermeture_de_ressource_ne_regarde_que_les_rdv_de_cette_ressource() throws Exception {
        Setup s = setup();
        UUID otherResource = createResource(s.salonId(), s.serviceId());
        book(s, s.resourceId(), H10);

        postResourceClosure(s, otherResource, "2099-01-05T08:30:00Z", "2099-01-05T09:30:00Z")
                .andExpect(status().isCreated());
        postResourceClosure(s, s.resourceId(), "2099-01-05T08:30:00Z", "2099-01-05T09:30:00Z")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void une_fermeture_de_salon_liste_les_rdv_de_toutes_les_ressources_tries_par_debut() throws Exception {
        Setup s = setup();
        UUID otherResource = createResource(s.salonId(), s.serviceId());
        book(s, otherResource, H12); // 11:00Z -> 12:00Z, réservé en premier
        book(s, s.resourceId(), H10); // 09:00Z -> 10:00Z

        postSalonClosure(s, "2099-01-05T08:00:00Z", "2099-01-05T12:00:00Z").andExpect(status().isConflict())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.appointments[0].startAt").value("2099-01-05T09:00:00Z"))
                .andExpect(jsonPath("$.appointments[0].resourceId").value(s.resourceId().toString()))
                .andExpect(jsonPath("$.appointments[1].startAt").value("2099-01-05T11:00:00Z"))
                .andExpect(jsonPath("$.appointments[1].resourceId").value(otherResource.toString()));
    }

    @Test
    void les_rdv_annules_ou_termines_ne_bloquent_pas() throws Exception {
        Setup s = setup();
        String cancelled = book(s, s.resourceId(), H10);
        String completed = book(s, s.resourceId(), H12);
        mockMvc.perform(patch(APPOINTMENT + "/cancel", s.salonId(), cancelled)
                .header("Authorization", s.employee()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch(APPOINTMENT + "/complete", s.salonId(), completed)
                .header("Authorization", s.employee())).andExpect(status().isOk());

        postSalonClosure(s, "2099-01-05T08:00:00Z", "2099-01-05T13:00:00Z").andExpect(status().isCreated());
    }

    @Test
    void un_rdv_d_un_autre_salon_ne_bloque_pas() throws Exception {
        Setup a = setup();
        Setup b = setup();
        book(b, b.resourceId(), H10);

        postSalonClosure(a, "2099-01-05T08:30:00Z", "2099-01-05T09:30:00Z").andExpect(status().isCreated());
    }

    @Test
    void la_modification_d_une_fermeture_est_revalidee() throws Exception {
        Setup s = setup();
        book(s, s.resourceId(), H10);
        String created = postSalonClosure(s, "2099-03-01T00:00:00Z", "2099-03-02T00:00:00Z")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String closureId = objectMapper.readTree(created).get("closureId").asText();

        String overlapping = closureBody("2099-01-05T08:30:00Z", "2099-01-05T09:30:00Z");
        mockMvc.perform(put(SALON_CLOSURE, s.salonId(), closureId).header("Authorization", s.owner())
                .contentType(MediaType.APPLICATION_JSON).content(overlapping)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.count").value(1));

        // la fermeture n'a pas bougé
        mockMvc.perform(get(SALON_CLOSURES, s.salonId()).header("Authorization", s.owner()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startAt").value("2099-03-01T00:00:00Z"));

        String harmless = closureBody("2099-04-01T00:00:00Z", "2099-04-02T00:00:00Z");
        mockMvc.perform(put(SALON_CLOSURE, s.salonId(), closureId).header("Authorization", s.owner())
                .contentType(MediaType.APPLICATION_JSON).content(harmless)).andExpect(status().isOk());
    }
}
