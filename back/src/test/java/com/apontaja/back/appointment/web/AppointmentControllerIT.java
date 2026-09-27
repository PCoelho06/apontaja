package com.apontaja.back.appointment.web;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
                properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentControllerIT {

        private static final String APPOINTMENTS = "/api/salons/{salonId}/appointments";
        private static final String APPOINTMENT = APPOINTMENTS + "/{id}";

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

        private UUID createAccount() {
                return accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash",
                                Instant.now())).getId();
        }

        private String bearerTokenFor(UUID accountId) {
                return "Bearer " + accessTokenIssuer.issue(accountId);
        }

        private UUID createSalon() {
                UUID organizationId = organizationRepository
                                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
                return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        }

        private String authOf(UUID salonId, StaffRole role) {
                UUID accountId = createAccount();
                staffMembershipRepository.save(
                                new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
                return bearerTokenFor(accountId);
        }

        /**
         * Ouvre le salon 00:00-23:59 tous les jours : évite toute dépendance au jour
         * d'exécution réel.
         */
        private void openSalonAllWeek(UUID salonId) {
                List<Schedule> slots = Arrays
                                .stream(DayOfWeek.values()).map(day -> Schedule.forSalon(idGenerator.generate(),
                                                salonId, day, LocalTime.of(0, 0), LocalTime.of(23, 59), Instant.now()))
                                .toList();
                scheduleRepository.saveAll(slots);
        }

        private UUID createResource(UUID salonId) {
                return resourceRepository.save(new Resource(idGenerator.generate(), salonId, "R " + UUID.randomUUID(),
                                ResourceType.EMPLOYEE, Instant.now())).getId();
        }

        private UUID createService(UUID salonId, int durationMinutes, int priceCents) {
                return salonServiceRepository.save(new SalonService(idGenerator.generate(), salonId,
                                "S " + UUID.randomUUID(), null, durationMinutes, priceCents, Instant.now())).getId();
        }

        private void linkServiceResource(UUID serviceId, UUID resourceId) {
                serviceResourceRepository.save(new ServiceResource(serviceId, resourceId, null, null));
        }

        private UUID createCustomer(UUID salonId) {
                CustomerProfile profile = customerProfileRepository.save(new CustomerProfile(idGenerator.generate(),
                                "Alice", "Martin", "alice+" + UUID.randomUUID() + "@example.com", null, Instant.now()));
                salonCustomerLinkRepository.save(new SalonCustomerLink(idGenerator.generate(), salonId, profile.getId(),
                                CustomerLinkSource.MANUAL, null, Instant.now()));
                return profile.getId();
        }

        /**
         * Salon ouvert, une ressource associée à une prestation, un client : le
         * scénario nominal.
         */
        private record BookableSetup(UUID salonId, UUID resourceId, UUID serviceId, UUID customerProfileId,
                        String auth) {
        }

        private BookableSetup bookableSetup() {
                UUID salonId = createSalon();
                openSalonAllWeek(salonId);
                UUID resourceId = createResource(salonId);
                UUID serviceId = createService(salonId, 60, 2500);
                linkServiceResource(serviceId, resourceId);
                UUID customerProfileId = createCustomer(salonId);
                String auth = authOf(salonId, StaffRole.EMPLOYEE);
                return new BookableSetup(salonId, resourceId, serviceId, customerProfileId, auth);
        }

        private static String bookingBody(UUID customerProfileId, UUID serviceId, UUID resourceId, String startAt) {
                return """
                                {"customerProfileId":"%s","serviceId":"%s","resourceId":"%s","startAt":"%s"}
                                """.formatted(customerProfileId, serviceId, resourceId, startAt);
        }

        @Test
        void reserve_avec_le_snapshot_prix_duree_de_la_prestation_et_permet_de_relire_le_rdv() throws Exception {
                BookableSetup setup = bookableSetup();

                String responseBody = mockMvc
                                .perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(bookingBody(setup.customerProfileId(), setup.serviceId(),
                                                                setup.resourceId(), "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isCreated()).andExpect(jsonPath("$.appointmentId").exists())
                                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                                .andExpect(jsonPath("$.priceAtBookingCents").value(2500))
                                .andExpect(jsonPath("$.durationAtBookingMinutes").value(60))
                                .andExpect(jsonPath("$.resourceId").value(setup.resourceId().toString()))
                                .andExpect(jsonPath("$.startAt").value("2026-09-24T08:00:00Z"))
                                .andExpect(jsonPath("$.endAt").value("2026-09-24T09:00:00Z")).andReturn().getResponse()
                                .getContentAsString();

                String appointmentId = objectMapper.readTree(responseBody).get("appointmentId").asText();

                mockMvc.perform(get(APPOINTMENT, setup.salonId(), appointmentId).header("Authorization", setup.auth()))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.priceAtBookingCents").value(2500));
        }

        @Test
        void un_employee_peut_reserver() throws Exception {
                BookableSetup setup = bookableSetup();

                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                setup.serviceId(), setup.resourceId(), "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isCreated());
        }

        @Test
        void refuse_une_ressource_une_prestation_ou_un_client_absents_du_salon() throws Exception {
                BookableSetup setup = bookableSetup();
                UUID foreignSalon = createSalon();
                UUID foreignResource = createResource(foreignSalon);
                UUID foreignService = createService(foreignSalon, 60, 2500);
                UUID foreignCustomer = createCustomer(foreignSalon);

                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                setup.serviceId(), foreignResource, "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isNotFound());
                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                foreignService, setup.resourceId(), "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isNotFound());
                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(foreignCustomer,
                                                setup.serviceId(), setup.resourceId(), "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isNotFound());
        }

        @Test
        void refuse_une_ressource_non_associee_a_la_prestation() throws Exception {
                BookableSetup setup = bookableSetup();
                UUID unlinkedResource = createResource(setup.salonId());

                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                setup.serviceId(), unlinkedResource, "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refuse_une_date_de_debut_illisible() throws Exception {
                BookableSetup setup = bookableSetup();

                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                setup.serviceId(), setup.resourceId(), "pas-une-date")))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void refuse_un_creneau_hors_horaires_avec_le_detail_des_violations() throws Exception {
                UUID salonId = createSalon();
                // Salon jamais ouvert (aucun horaire créé).
                UUID resourceId = createResource(salonId);
                UUID serviceId = createService(salonId, 60, 2500);
                linkServiceResource(serviceId, resourceId);
                UUID customerProfileId = createCustomer(salonId);
                String auth = authOf(salonId, StaffRole.OWNER);

                mockMvc.perform(post(APPOINTMENTS, salonId).header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bookingBody(customerProfileId, serviceId, resourceId,
                                                "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.issues[0]").value("OUTSIDE_SALON_HOURS"));
        }

        @Test
        void deux_reservations_concurrentes_sur_le_meme_creneau_et_la_meme_ressource_n_en_laissent_passer_qu_une()
                        throws Exception {
                BookableSetup setup = bookableSetup();
                UUID secondCustomer = createCustomer(setup.salonId());
                String body1 = bookingBody(setup.customerProfileId(), setup.serviceId(), setup.resourceId(),
                                "2026-09-24T10:00:00+02:00");
                String body2 = bookingBody(secondCustomer, setup.serviceId(), setup.resourceId(),
                                "2026-09-24T10:00:00+02:00");

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                        Future<Integer> first = executor.submit(() -> {
                                start.await();
                                return mockMvc.perform(post(APPOINTMENTS, setup.salonId())
                                                .header("Authorization", setup.auth())
                                                .contentType(MediaType.APPLICATION_JSON).content(body1)).andReturn()
                                                .getResponse().getStatus();
                        });
                        Future<Integer> second = executor.submit(() -> {
                                start.await();
                                return mockMvc.perform(post(APPOINTMENTS, setup.salonId())
                                                .header("Authorization", setup.auth())
                                                .contentType(MediaType.APPLICATION_JSON).content(body2)).andReturn()
                                                .getResponse().getStatus();
                        });
                        start.countDown();

                        List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS),
                                        second.get(20, TimeUnit.SECONDS));
                        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
                } finally {
                        executor.shutdownNow();
                }
        }

        @Test
        void reserver_bloque_la_suppression_de_la_ressource_et_de_la_prestation() throws Exception {
                BookableSetup setup = bookableSetup();
                // La réservation est ouverte à tout le staff (EMPLOYEE inclus, cf.
                // setup.auth()), mais
                // la suppression du catalogue passe par catalogManagementGuard (OWNER/MANAGER
                // uniquement)
                // — il faut donc un second compte avec un rôle habilité pour ces deux appels.
                String ownerAuth = authOf(setup.salonId(), StaffRole.OWNER);

                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).header("Authorization", setup.auth())
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                setup.serviceId(), setup.resourceId(), "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isCreated());

                mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", setup.salonId(), setup.resourceId())
                                .header("Authorization", ownerAuth)).andExpect(status().isConflict());
                mockMvc.perform(delete("/api/salons/{salonId}/services/{id}", setup.salonId(), setup.serviceId())
                                .header("Authorization", ownerAuth)).andExpect(status().isConflict());
        }

        @Test
        void refuse_sans_authentification_et_pour_un_salon_inexistant() throws Exception {
                BookableSetup setup = bookableSetup();

                mockMvc.perform(post(APPOINTMENTS, setup.salonId()).contentType(MediaType.APPLICATION_JSON)
                                .content(bookingBody(setup.customerProfileId(), setup.serviceId(), setup.resourceId(),
                                                "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get(APPOINTMENT, setup.salonId(), UUID.randomUUID()).header("Authorization",
                                setup.auth())).andExpect(status().isNotFound());
                mockMvc.perform(post(APPOINTMENTS, UUID.randomUUID())
                                .header("Authorization", bearerTokenFor(createAccount()))
                                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(setup.customerProfileId(),
                                                setup.serviceId(), setup.resourceId(), "2026-09-24T10:00:00+02:00")))
                                .andExpect(status().isForbidden());
        }
}
