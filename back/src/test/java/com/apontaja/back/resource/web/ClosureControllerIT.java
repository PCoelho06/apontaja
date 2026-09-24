package com.apontaja.back.resource.web;

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
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;
import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ClosureControllerIT {

    private static final String SALON_CLOSURES = "/api/salons/{salonId}/closures";
    private static final String SALON_CLOSURE = SALON_CLOSURES + "/{id}";
    private static final String RESOURCE_CLOSURES = "/api/salons/{salonId}/resources/{resourceId}/closures";
    private static final String RESOURCE_CLOSURE = RESOURCE_CLOSURES + "/{id}";

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

    private UUID createAccount() {
        return accountRepository
                .save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", Instant.now()))
                .getId();
    }

    private String bearerTokenFor(UUID accountId) {
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private UUID createSalon() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private String authOf(UUID salonId, StaffRole role) {
        UUID accountId = createAccount();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now()));
        return bearerTokenFor(accountId);
    }

    private UUID createResource(UUID salonId) {
        return resourceRepository.save(new Resource(idGenerator.generate(), salonId, "R " + UUID.randomUUID(),
                ResourceType.EMPLOYEE, Instant.now())).getId();
    }

    @Autowired
    private ClosureRepository closureRepository;

    private static String body(String start, String end) {
        return "{\"startAt\":\"%s\",\"endAt\":\"%s\",\"reason\":\"Vacances\"}".formatted(start, end);
    }

    private UUID salonClosure(UUID salonId, String start, String end) {
        return closureRepository.save(Closure.forSalon(idGenerator.generate(), salonId, Instant.parse(start),
                Instant.parse(end), null, Instant.now())).getId();
    }

    private UUID resourceClosure(UUID resourceId, String start, String end) {
        return closureRepository.save(Closure.forResource(idGenerator.generate(), resourceId, Instant.parse(start),
                Instant.parse(end), null, Instant.now())).getId();
    }

    @Test
    void un_owner_et_un_manager_creent_une_fermeture_avec_offset() throws Exception {
        UUID salonId = createSalon();

        mockMvc.perform(post(SALON_CLOSURES, salonId).header("Authorization", authOf(salonId, StaffRole.OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("2026-08-15T00:00:00+02:00", "2026-08-16T00:00:00+02:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.closureId").exists())
                .andExpect(jsonPath("$.reason").value("Vacances"));

        mockMvc.perform(post(SALON_CLOSURES, salonId).header("Authorization", authOf(salonId, StaffRole.MANAGER))
                .contentType(MediaType.APPLICATION_JSON).content(body("2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z")))
                .andExpect(status().isCreated());
    }

    @Test
    void un_employee_ne_peut_rien_ecrire_mais_peut_lire() throws Exception {
        UUID salonId = createSalon();
        UUID resourceId = createResource(salonId);
        String employee = authOf(salonId, StaffRole.EMPLOYEE);
        UUID closureId = salonClosure(salonId, "2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z");
        String json = body("2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z");

        mockMvc.perform(post(SALON_CLOSURES, salonId).header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mockMvc.perform(put(SALON_CLOSURE, salonId, closureId).header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mockMvc.perform(delete(SALON_CLOSURE, salonId, closureId).header("Authorization", employee))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RESOURCE_CLOSURES, salonId, resourceId).header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());

        mockMvc.perform(get(SALON_CLOSURES, salonId).header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(RESOURCE_CLOSURES, salonId, resourceId).header("Authorization", employee))
                .andExpect(status().isOk());
    }

    @Test
    void la_liste_filtre_par_recouvrement_et_valide_ses_parametres() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        salonClosure(salonId, "2026-08-10T10:00:00Z", "2026-08-10T12:00:00Z");
        salonClosure(salonId, "2026-08-20T10:00:00Z", "2026-08-20T12:00:00Z");

        mockMvc.perform(get(SALON_CLOSURES, salonId).header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(SALON_CLOSURES, salonId).header("Authorization", auth)
                .param("from", "2026-08-15T00:00:00Z").param("to", "2026-08-25T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(SALON_CLOSURES, salonId).header("Authorization", auth)
                .param("from", "2026-08-10T12:00:00Z").param("to", "2026-08-11T00:00:00Z"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(SALON_CLOSURES, salonId).header("Authorization", auth)
                .param("from", "2026-09-01T00:00:00Z").param("to", "2026-08-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SALON_CLOSURES, salonId).header("Authorization", auth).param("from", "hier"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refuse_une_periode_inversee_ou_illisible() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);

        mockMvc.perform(post(SALON_CLOSURES, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("2026-09-02T00:00:00Z", "2026-09-01T00:00:00Z")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(SALON_CLOSURES, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("2026-09-01", "2026-09-02")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(SALON_CLOSURES, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void met_a_jour_puis_supprime_une_fermeture() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        UUID closureId = salonClosure(salonId, "2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z");

        mockMvc.perform(put(SALON_CLOSURE, salonId, closureId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("2026-09-05T00:00:00Z", "2026-09-06T00:00:00Z")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("Vacances"));
        mockMvc.perform(delete(SALON_CLOSURE, salonId, closureId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(SALON_CLOSURE, salonId, closureId).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void les_perimetres_salon_ressource_et_autre_salon_sont_etanches() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String auth = authOf(salonA, StaffRole.OWNER);
        UUID resourceA = createResource(salonA);
        UUID resourceB = createResource(salonB);
        UUID salonClosureA = salonClosure(salonA, "2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z");
        UUID resourceClosureA = resourceClosure(resourceA, "2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z");
        UUID closureOfB = salonClosure(salonB, "2026-09-01T00:00:00Z", "2026-09-02T00:00:00Z");
        String json = body("2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z");

        mockMvc.perform(put(RESOURCE_CLOSURE, salonA, resourceA, salonClosureA).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
        mockMvc.perform(put(SALON_CLOSURE, salonA, resourceClosureA).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
        mockMvc.perform(delete(SALON_CLOSURE, salonA, closureOfB).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RESOURCE_CLOSURES, salonA, resourceB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
        mockMvc.perform(get(RESOURCE_CLOSURES, salonA, resourceB).header("Authorization", auth))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(SALON_CLOSURES, salonA).header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(RESOURCE_CLOSURES, salonA, resourceA).header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
