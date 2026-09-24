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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ScheduleControllerIT {

    private static final String SALON_SCHEDULE = "/api/salons/{salonId}/schedule";
    private static final String RESOURCE_SCHEDULE = "/api/salons/{salonId}/resources/{resourceId}/schedule";

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

    private static String slot(String day, String start, String end) {
        return "{\"dayOfWeek\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(day, start, end);
    }

    private static String schedule(String... slots) {
        return "{\"slots\":[" + String.join(",", slots) + "]}";
    }

    @Test
    void un_owner_remplace_le_planning_du_salon_et_la_lecture_est_triee() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);

        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(schedule(slot("TUESDAY", "09:00", "12:00"),
                        slot("MONDAY", "14:00", "18:00"), slot("MONDAY", "09:00", "12:00"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(3));

        mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.slots[1].startTime").value("14:00"))
                .andExpect(jsonPath("$.slots[2].dayOfWeek").value("TUESDAY"));
    }

    @Test
    void un_manager_ecrit_un_employee_lit_seulement() throws Exception {
        UUID salonId = createSalon();
        String body = schedule(slot("MONDAY", "09:00", "12:00"));

        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", authOf(salonId, StaffRole.MANAGER))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        String employee = authOf(salonId, StaffRole.EMPLOYEE);
        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", employee)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(1));
    }

    @Test
    void chaque_remplacement_ecrase_le_precedent() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schedule(slot("MONDAY", "09:00", "12:00"), slot("TUESDAY", "09:00", "12:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(schedule(slot("FRIDAY", "10:00", "11:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", auth))
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].dayOfWeek").value("FRIDAY"));
    }

    @Test
    void un_chevauchement_est_refuse_en_400_sans_modifier_l_existant_et_l_adjacence_est_acceptee() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(schedule(slot("MONDAY", "09:00", "12:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schedule(slot("MONDAY", "09:00", "12:00"), slot("MONDAY", "11:00", "13:00"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", auth))
                .andExpect(jsonPath("$.slots.length()").value(1));

        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schedule(slot("MONDAY", "09:00", "12:00"), slot("MONDAY", "12:00", "14:00"))))
                .andExpect(status().isOk());
    }

    @Test
    void refuse_les_entrees_invalides_en_400() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        List<String> invalidBodies = List.of(schedule(slot("LUNDI", "09:00", "12:00")),
                schedule(slot("MONDAY", "neuf heures", "12:00")), schedule(slot("MONDAY", "12:00", "09:00")),
                schedule(slot("MONDAY", "", "12:00")), "{}");

        for (String body : invalidBodies) {
            mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void les_horaires_d_une_ressource_sont_independants_du_salon_et_une_liste_vide_les_efface() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        UUID resourceId = createResource(salonId);
        mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(schedule(slot("MONDAY", "08:00", "20:00"))))
                .andExpect(status().isOk());

        mockMvc.perform(put(RESOURCE_SCHEDULE, salonId, resourceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(schedule(slot("MONDAY", "10:00", "16:00"), slot("WEDNESDAY", "10:00", "16:00"))))
                .andExpect(status().isOk());
        mockMvc.perform(get(RESOURCE_SCHEDULE, salonId, resourceId).header("Authorization", auth))
                .andExpect(jsonPath("$.slots.length()").value(2));
        mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", auth))
                .andExpect(jsonPath("$.slots.length()").value(1));

        mockMvc.perform(put(RESOURCE_SCHEDULE, salonId, resourceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(schedule())).andExpect(status().isOk());
        mockMvc.perform(get(RESOURCE_SCHEDULE, salonId, resourceId).header("Authorization", auth))
                .andExpect(jsonPath("$.slots.length()").value(0));
        mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", auth))
                .andExpect(jsonPath("$.slots.length()").value(1));
    }

    @Test
    void une_ressource_d_un_autre_salon_est_introuvable() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String auth = authOf(salonA, StaffRole.OWNER);
        UUID resourceOfB = createResource(salonB);

        mockMvc.perform(get(RESOURCE_SCHEDULE, salonA, resourceOfB).header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(RESOURCE_SCHEDULE, salonA, resourceOfB).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(schedule())).andExpect(status().isNotFound());
        mockMvc.perform(get(RESOURCE_SCHEDULE, salonA, UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void deux_remplacements_concurrents_ne_se_melangent_jamais() throws Exception {
        UUID salonId = createSalon();
        String auth = authOf(salonId, StaffRole.OWNER);
        String one = schedule(slot("MONDAY", "09:00", "12:00"));
        String two = schedule(slot("TUESDAY", "09:00", "12:00"), slot("WEDNESDAY", "09:00", "12:00"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(one)).andReturn().getResponse().getStatus();
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return mockMvc.perform(put(SALON_SCHEDULE, salonId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(two)).andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        String content = mockMvc.perform(get(SALON_SCHEDULE, salonId).header("Authorization", auth))
                .andReturn().getResponse().getContentAsString();
        int slotCount = content.split("dayOfWeek", -1).length - 1;
        assertThat(slotCount).isIn(1, 2);
    }
}
