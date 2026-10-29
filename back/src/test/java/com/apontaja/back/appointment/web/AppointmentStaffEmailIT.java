package com.apontaja.back.appointment.web;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.EmailSender;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Décisions du staff sur un rendez-vous : emails au client (confirmation,
 * refus, annulation par le salon), filtre {@code status} de l'agenda et
 * autorisations. Le RDV {@code SCHEDULED} est obtenu par SQL natif : l'API
 * staff ne crée que des RDV confirmés.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class AppointmentStaffEmailIT {

    private static final String START = "2099-06-10T10:00:00+02:00";

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
    private IdGenerator idGenerator;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    private record Fixture(UUID salonId, String owner, UUID resourceId, UUID serviceId) {
    }

    private String authOf(UUID salonId, StaffRole role) {
        UUID accountId = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Test", "User", "hash", Instant.now())).getId();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private String read(org.springframework.test.web.servlet.ResultActions actions, String path) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), path);
    }

    /**
     * Salon ouvert le mercredi, une ressource et une prestation associée (45 min,
     * 40,05 €).
     */
    private Fixture fixture() throws Exception {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        UUID salonId = salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test",
                "1 rue du Test", "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
        String owner = authOf(salonId, StaffRole.OWNER);
        UUID resourceId = resourceRepository
                .save(new Resource(idGenerator.generate(), salonId, "Chaise 1", ResourceType.EMPLOYEE, Instant.now()))
                .getId();
        mockMvc.perform(put("/api/salons/{salonId}/schedule", salonId).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\":[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"09:00\",\"endTime\":\"18:00\"}]}"))
                .andExpect(status().isOk());
        UUID serviceId = UUID
                .fromString(read(
                        mockMvc.perform(post("/api/salons/{salonId}/services", salonId).header("Authorization", owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Coupe\",\"description\":null,\"defaultDurationMinutes\":45,"
                                        + "\"defaultPriceCents\":4005}"))
                                .andExpect(status().isCreated()),
                        "$.serviceId"));
        mockMvc.perform(
                put("/api/salons/{salonId}/services/{serviceId}/resources/{resourceId}", salonId, serviceId, resourceId)
                        .header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overridePriceCents\":null,\"overrideDurationMinutes\":null}"))
                .andExpect(status().isOk());
        return new Fixture(salonId, owner, resourceId, serviceId);
    }

    private UUID customer(Fixture f, String contactJson) throws Exception {
        return UUID.fromString(read(mockMvc
                .perform(post("/api/salons/{salonId}/customers", f.salonId()).header("Authorization", f.owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\",\"lastName\":\"Martin\"," + contactJson + "}"))
                .andExpect(status().isCreated()), "$.customerProfileId"));
    }

    private UUID postAppointment(Fixture f, UUID customerId) throws Exception {
        return UUID.fromString(read(mockMvc
                .perform(post("/api/salons/{salonId}/appointments", f.salonId()).header("Authorization", f.owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"customerProfileId\":\"%s\",\"serviceId\":\"%s\",\"resourceId\":\"%s\","
                                + "\"startAt\":\"%s\"}").formatted(customerId, f.serviceId(), f.resourceId(), START)))
                .andExpect(status().isCreated()), "$.appointmentId"));
    }

    /**
     * Prépare un RDV ; l'email de sa création (staff) n'est pas l'objet de ces
     * tests : le mock est remis à zéro.
     */
    private UUID appointment(Fixture f, UUID customerId, boolean asRequest) throws Exception {
        UUID id = postAppointment(f, customerId);
        clearInvocations(emailSender);
        if (asRequest) {
            jdbcTemplate.update("UPDATE appointment SET status = 'SCHEDULED' WHERE id = ?", id);
        }
        return id;
    }

    private static final String ALICE = "\"email\":\"alice@example.com\",\"phone\":null,\"internalNotes\":null";

    @Test
    void confirmer_une_demande_envoie_l_email_de_confirmation() throws Exception {
        Fixture f = fixture();
        UUID id = appointment(f, customer(f, ALICE), true);

        mockMvc.perform(
                patch("/api/salons/{s}/appointments/{a}/confirm", f.salonId(), id).header("Authorization", f.owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(emailSender).send(eq("alice@example.com"), eq("Marcação confirmada - Salon Test"), any());
    }

    @Test
    void refuser_une_demande_envoie_l_email_de_refus() throws Exception {
        Fixture f = fixture();
        UUID id = appointment(f, customer(f, ALICE), true);

        mockMvc.perform(patch("/api/salons/{s}/appointments/{a}/cancel", f.salonId(), id)
                .header("Authorization", f.owner()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(emailSender).send(eq("alice@example.com"), eq("Pedido de marcação recusado - Salon Test"), any());
    }

    @Test
    void annuler_un_rdv_confirme_envoie_l_email_d_annulation_par_le_salon() throws Exception {
        Fixture f = fixture();
        UUID id = appointment(f, customer(f, ALICE), false);

        mockMvc.perform(patch("/api/salons/{s}/appointments/{a}/cancel", f.salonId(), id)
                .header("Authorization", f.owner()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(emailSender).send(eq("alice@example.com"), eq("Marcação cancelada pelo salão - Salon Test"), any());
    }

    @Test
    void un_client_sans_email_ne_recoit_rien() throws Exception {
        Fixture f = fixture();
        UUID id = appointment(f, customer(f, "\"email\":null,\"phone\":\"0612345678\",\"internalNotes\":null"), true);

        mockMvc.perform(
                patch("/api/salons/{s}/appointments/{a}/confirm", f.salonId(), id).header("Authorization", f.owner()))
                .andExpect(status().isOk());

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void creer_un_rdv_pour_un_client_avec_email_envoie_l_email_de_confirmation() throws Exception {
        Fixture f = fixture();
        UUID customerId = customer(f, ALICE);

        postAppointment(f, customerId);

        verify(emailSender).send(eq("alice@example.com"), eq("Marcação confirmada - Salon Test"), any());
    }

    @Test
    void creer_un_rdv_pour_un_client_sans_email_n_envoie_rien() throws Exception {
        Fixture f = fixture();
        UUID customerId = customer(f, "\"email\":null,\"phone\":\"0612345678\",\"internalNotes\":null");

        postAppointment(f, customerId);

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void deplacer_un_rdv_envoie_l_email_de_modification() throws Exception {
        Fixture f = fixture();
        UUID id = appointment(f, customer(f, ALICE), false);

        mockMvc.perform(
                patch("/api/salons/{s}/appointments/{a}/reschedule", f.salonId(), id).header("Authorization", f.owner())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startAt\":\"2099-06-10T14:00:00+02:00\"}"))
                .andExpect(status().isOk());

        verify(emailSender).send(eq("alice@example.com"), eq("Marcação alterada pelo salão - Salon Test"), any());
    }

    @Test
    void le_filtre_status_ne_renvoie_que_les_demandes_et_refuse_un_statut_inconnu() throws Exception {
        Fixture f = fixture();
        UUID customerId = customer(f, ALICE);
        appointment(f, customerId, false);

        mockMvc.perform(get("/api/salons/{s}/appointments", f.salonId()).header("Authorization", f.owner())
                .param("status", "SCHEDULED")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/salons/{s}/appointments", f.salonId()).header("Authorization", f.owner())
                .param("status", "CONFIRMED")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/salons/{s}/appointments", f.salonId()).header("Authorization", f.owner())
                .param("status", "BIZARRE")).andExpect(status().isBadRequest());
    }

    @Test
    void autorisations_employee_ok_autre_salon_et_anonyme_refuses() throws Exception {
        Fixture f = fixture();
        UUID id = appointment(f, customer(f, ALICE), true);
        Fixture other = fixture();

        mockMvc.perform(patch("/api/salons/{s}/appointments/{a}/confirm", f.salonId(), id))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(patch("/api/salons/{s}/appointments/{a}/confirm", f.salonId(), id).header("Authorization",
                other.owner())).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/salons/{s}/appointments/{a}/confirm", UUID.randomUUID(), id).header("Authorization",
                f.owner())).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/salons/{s}/appointments/{a}/confirm", f.salonId(), id).header("Authorization",
                authOf(f.salonId(), StaffRole.EMPLOYEE))).andExpect(status().isOk());
    }
}
