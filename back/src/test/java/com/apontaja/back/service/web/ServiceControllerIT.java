package com.apontaja.back.service.web;

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
import com.apontaja.back.service.domain.SalonService;
import com.apontaja.back.service.domain.SalonServiceRepository;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

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
class ServiceControllerIT {

    private static final String SERVICES = "/api/salons/{salonId}/services";
    private static final String SERVICE = SERVICES + "/{id}";
    private static final String LINKS = SERVICE + "/resources";
    private static final String LINK = LINKS + "/{resourceId}";

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
    private SalonServiceRepository serviceRepository;

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

    private UUID createService(UUID salonId, String name) {
        return serviceRepository
                .save(new SalonService(idGenerator.generate(), salonId, name, null, 30, 2500, Instant.now()))
                .getId();
    }

    private UUID createResource(UUID salonId, String name) {
        return resourceRepository
                .save(new Resource(idGenerator.generate(), salonId, name, ResourceType.EMPLOYEE, Instant.now()))
                .getId();
    }

    private String body(String name, int duration, int price) {
        return """
                {"name":"%s","description":"Une description","defaultDurationMinutes":%d,"defaultPriceCents":%d}
                """.formatted(name, duration, price);
    }

    @Test
    void un_owner_et_un_manager_creent_une_prestation() throws Exception {
        UUID salonId = createSalon();

        mockMvc.perform(post(SERVICES, salonId).header("Authorization", authOf(salonId, StaffRole.OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(body("  Coupe  ", 30, 2500)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.serviceId").exists())
                .andExpect(jsonPath("$.name").value("Coupe")).andExpect(jsonPath("$.defaultDurationMinutes").value(30))
                .andExpect(jsonPath("$.defaultPriceCents").value(2500));

        mockMvc.perform(post(SERVICES, salonId).header("Authorization", authOf(salonId, StaffRole.MANAGER))
                .contentType(MediaType.APPLICATION_JSON).content(body("Brushing", 20, 1500)))
                .andExpect(status().isCreated());
    }

    @Test
    void un_employee_ne_peut_rien_ecrire_mais_peut_lire() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.EMPLOYEE);
        UUID serviceId = createService(salonId, "Coupe");
        UUID resourceId = createResource(salonId, "Chaise 1");

        mockMvc.perform(post(SERVICES, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body("Nouvelle", 30, 2500))).andExpect(status().isForbidden());
        mockMvc.perform(put(SERVICE, salonId, serviceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Renomme", 30, 2500)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(SERVICE, salonId, serviceId).header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(LINK, salonId, serviceId, resourceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(delete(LINK, salonId, serviceId, resourceId).header("Authorization", auth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(SERVICES, salonId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(SERVICE, salonId, serviceId).header("Authorization", auth)).andExpect(status().isOk());
        mockMvc.perform(get(LINKS, salonId, serviceId).header("Authorization", auth)).andExpect(status().isOk());
    }

    @Test
    void refuse_un_nom_pris_et_des_valeurs_invalides() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        createService(salonId, "Coupe");

        mockMvc.perform(post(SERVICES, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body("Coupe", 30, 2500))).andExpect(status().isConflict());
        mockMvc.perform(post(SERVICES, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body("Autre", 0, 2500))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.defaultDurationMinutes").exists());
        mockMvc.perform(post(SERVICES, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body("Autre", 30, -1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.defaultPriceCents").exists());
        mockMvc.perform(post(SERVICES, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body("Autre", 1441, 100))).andExpect(status().isBadRequest());
    }

    @Test
    void modifie_une_prestation_et_refuse_le_nom_d_une_autre() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        UUID serviceId = createService(salonId, "Coupe");
        createService(salonId, "Brushing");

        mockMvc.perform(put(SERVICE, salonId, serviceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Coupe longue", 60, 4000)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Coupe longue"))
                .andExpect(jsonPath("$.defaultDurationMinutes").value(60));
        mockMvc.perform(put(SERVICE, salonId, serviceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Brushing", 60, 4000)))
                .andExpect(status().isConflict());
    }

    @Test
    void supprime_puis_libere_le_nom() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        UUID serviceId = createService(salonId, "Coupe");

        mockMvc.perform(delete(SERVICE, salonId, serviceId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(SERVICE, salonId, serviceId).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(SERVICES, salonId).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(body("Coupe", 30, 2500))).andExpect(status().isCreated());
    }

    @Test
    void une_prestation_d_un_autre_salon_est_introuvable_via_le_chemin_du_salon() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String auth = authOf(salonA, StaffRole.OWNER);
        UUID serviceOfB = createService(salonB, "Coupe B");
        UUID resourceOfA = createResource(salonA, "Chaise A");

        mockMvc.perform(get(SERVICE, salonA, serviceOfB).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(SERVICE, salonA, serviceOfB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body("Vol", 30, 100)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(SERVICE, salonA, serviceOfB).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(LINK, salonA, serviceOfB, resourceOfA).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void associe_une_ressource_avec_ou_sans_surcharges() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.MANAGER);
        UUID serviceId = createService(salonId, "Coupe");
        UUID resourceA = createResource(salonId, "Chaise A");
        UUID resourceB = createResource(salonId, "Chaise B");

        mockMvc.perform(put(LINK, salonId, serviceId, resourceA).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePriceCents").value(2500))
                .andExpect(jsonPath("$.effectiveDurationMinutes").value(30));
        mockMvc.perform(put(LINK, salonId, serviceId, resourceB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overridePriceCents\":4000,\"overrideDurationMinutes\":50}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePriceCents").value(4000))
                .andExpect(jsonPath("$.effectiveDurationMinutes").value(50));

        // upsert : la seconde écriture remplace les surcharges
        mockMvc.perform(put(LINK, salonId, serviceId, resourceB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"overridePriceCents\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectivePriceCents").value(100))
                .andExpect(jsonPath("$.effectiveDurationMinutes").value(30));

        mockMvc.perform(get(LINKS, salonId, serviceId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void refuse_des_surcharges_invalides_et_une_ressource_d_un_autre_salon() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String auth = authOf(salonA, StaffRole.OWNER);
        UUID serviceId = createService(salonA, "Coupe");
        UUID ownResource = createResource(salonA, "Chaise A");
        UUID foreignResource = createResource(salonB, "Chaise B");

        mockMvc.perform(put(LINK, salonA, serviceId, ownResource).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"overrideDurationMinutes\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(LINK, salonA, serviceId, ownResource).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"overridePriceCents\":-5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(LINK, salonA, serviceId, foreignResource).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mockMvc.perform(put(LINK, salonA, serviceId, UUID.randomUUID()).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void dissocie_une_ressource_de_facon_idempotente() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        UUID serviceId = createService(salonId, "Coupe");
        UUID resourceId = createResource(salonId, "Chaise A");
        mockMvc.perform(put(LINK, salonId, serviceId, resourceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mockMvc.perform(delete(LINK, salonId, serviceId, resourceId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(LINK, salonId, serviceId, resourceId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(LINKS, salonId, serviceId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void une_ressource_supprimee_disparait_de_la_liste_des_associations() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        UUID serviceId = createService(salonId, "Coupe");
        UUID resourceId = createResource(salonId, "Chaise A");
        mockMvc.perform(put(LINK, salonId, serviceId, resourceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mockMvc.perform(delete("/api/salons/{salonId}/resources/{id}", salonId, resourceId)
                .header("Authorization", auth)).andExpect(status().isNoContent());

        mockMvc.perform(get(LINKS, salonId, serviceId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
