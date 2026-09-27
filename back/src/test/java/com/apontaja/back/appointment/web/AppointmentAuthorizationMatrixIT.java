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
import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.organization.domain.OrganizationRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice d'autorisation de la réservation : OWNER d'organisation sans staff
 * direct, cloisonnement inter-organisations et inter-salons, sans
 * authentification, salon inexistant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentAuthorizationMatrixIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

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

    private UUID createAccount() {
        return accountRepository
                .save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", Instant.now()))
                .getId();
    }

    private String bearerTokenFor(UUID accountId) {
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private UUID createOrganization() {
        return organizationRepository.save(new Organization(idGenerator.generate(), "Org Test", Instant.now()))
                .getId();
    }

    private UUID createSalon(UUID organizationId) {
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private String organizationOwnerAuth(UUID organizationId) {
        UUID accountId = createAccount();
        organizationMembershipRepository.save(new OrganizationMembership(idGenerator.generate(), accountId,
                organizationId, OrganizationRole.OWNER, Instant.now()));
        return bearerTokenFor(accountId);
    }

    private void openSalonAllWeek(UUID salonId) {
        List<Schedule> slots = Arrays.stream(DayOfWeek.values())
                .map(day -> Schedule.forSalon(idGenerator.generate(), salonId, day, LocalTime.of(0, 0),
                        LocalTime.of(23, 59), Instant.now()))
                .toList();
        scheduleRepository.saveAll(slots);
    }

    private UUID createResource(UUID salonId) {
        return resourceRepository.save(
                new Resource(idGenerator.generate(), salonId, "Chaise", ResourceType.EMPLOYEE, Instant.now()))
                .getId();
    }

    private UUID createService(UUID salonId, UUID resourceId) {
        UUID serviceId = salonServiceRepository
                .save(new SalonService(idGenerator.generate(), salonId, "Coupe", null, 60, 2500, Instant.now()))
                .getId();
        serviceResourceRepository.save(new ServiceResource(serviceId, resourceId, null, null));
        return serviceId;
    }

    private UUID createCustomer(UUID salonId) {
        CustomerProfile profile = customerProfileRepository.save(new CustomerProfile(idGenerator.generate(), "Alice",
                "Martin", "alice+" + UUID.randomUUID() + "@example.com", null, Instant.now()));
        salonCustomerLinkRepository.save(new SalonCustomerLink(idGenerator.generate(), salonId, profile.getId(),
                CustomerLinkSource.MANUAL, null, Instant.now()));
        return profile.getId();
    }

    private static String bookingBody(UUID customerProfileId, UUID serviceId, UUID resourceId) {
        return """
                {"customerProfileId":"%s","serviceId":"%s","resourceId":"%s","startAt":"2026-09-24T10:00:00+02:00"}
                """.formatted(customerProfileId, serviceId, resourceId);
    }

    @Test
    void un_organization_owner_sans_staff_direct_peut_reserver_et_consulter() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonId = createSalon(organizationId);
        openSalonAllWeek(salonId);
        UUID resourceId = createResource(salonId);
        UUID serviceId = createService(salonId, resourceId);
        UUID customerProfileId = createCustomer(salonId);
        String auth = organizationOwnerAuth(organizationId);

        mockMvc.perform(post("/api/salons/{salonId}/appointments", salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(customerProfileId, serviceId, resourceId)))
                .andExpect(status().isCreated());
    }

    @Test
    void un_organization_owner_n_a_aucun_acces_a_une_autre_organisation() throws Exception {
        String auth = organizationOwnerAuth(createOrganization());
        UUID salonInB = createSalon(createOrganization());
        openSalonAllWeek(salonInB);
        UUID resourceId = createResource(salonInB);
        UUID serviceId = createService(salonInB, resourceId);
        UUID customerProfileId = createCustomer(salonInB);

        mockMvc.perform(get("/api/salons/{salonId}/appointments/{id}", salonInB, UUID.randomUUID())
                .header("Authorization", auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/appointments", salonInB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(customerProfileId, serviceId, resourceId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_staff_d_un_salon_n_a_pas_acces_a_un_autre_salon_de_la_meme_organisation() throws Exception {
        UUID organizationId = createOrganization();
        UUID salonA = createSalon(organizationId);
        UUID salonB = createSalon(organizationId);
        openSalonAllWeek(salonB);
        UUID resourceId = createResource(salonB);
        UUID serviceId = createService(salonB, resourceId);
        UUID customerProfileId = createCustomer(salonB);
        UUID ownerOfA = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), ownerOfA, salonA, StaffRole.OWNER, Instant.now()));
        String auth = bearerTokenFor(ownerOfA);

        mockMvc.perform(post("/api/salons/{salonId}/appointments", salonB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(customerProfileId, serviceId, resourceId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refuse_sans_authentification_et_pour_un_salon_inexistant() throws Exception {
        String someone = bearerTokenFor(createAccount());
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/salons/{salonId}/appointments/{id}", randomId, randomId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/salons/{salonId}/appointments", randomId).header("Authorization", someone)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(randomId, randomId, randomId))).andExpect(status().isForbidden());
    }
}
