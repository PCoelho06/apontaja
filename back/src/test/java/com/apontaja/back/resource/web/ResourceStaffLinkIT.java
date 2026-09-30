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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tranche 8c : lien optionnel ressource EMPLOYEE ↔ membre de l'équipe. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class ResourceStaffLinkIT {

    private static final String RESOURCES = "/api/salons/{salonId}/resources";
    private static final String RESOURCE = RESOURCES + "/{id}";

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Member(UUID accountId, UUID membershipId) {
    }

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

    private Member member(UUID salonId, StaffRole role) {
        UUID accountId = createAccount();
        UUID membershipId = staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, role, Instant.now())).getId();
        return new Member(accountId, membershipId);
    }

    private String ownerAuth(UUID salonId) {
        return bearerTokenFor(member(salonId, StaffRole.OWNER).accountId());
    }

    private static String payload(String name, String type, UUID staffMembershipId) {
        String link = staffMembershipId == null ? "null" : "\"" + staffMembershipId + "\"";
        return "{\"name\":\"%s\",\"type\":\"%s\",\"staffMembershipId\":%s}".formatted(name, type, link);
    }

    private ResultActions postResource(UUID salonId, String auth, String body) throws Exception {
        return mockMvc.perform(post(RESOURCES, salonId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putResource(UUID salonId, UUID resourceId, String auth, String body) throws Exception {
        return mockMvc.perform(put(RESOURCE, salonId, resourceId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private UUID createResourceVia(UUID salonId, String auth, String body) throws Exception {
        String response = postResource(salonId, auth, body).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("resourceId").asText());
    }

    private void assertNoLink(String responseBody) throws Exception {
        JsonNode link = objectMapper.readTree(responseBody).get("staffMembershipId");
        assertThat(link == null || link.isNull()).isTrue();
    }

    private Resource linkedResource(UUID salonId, String name, UUID staffMembershipId) {
        Resource resource = new Resource(idGenerator.generate(), salonId, name, ResourceType.EMPLOYEE,
                Instant.now());
        resource.linkToStaff(staffMembershipId);
        return resource;
    }

    @Test
    void cree_une_ressource_liee_a_un_membre_et_l_expose_en_lecture() throws Exception {
        UUID salonId = createSalon();
        String auth = ownerAuth(salonId);
        Member employee = member(salonId, StaffRole.EMPLOYEE);

        postResource(salonId, auth, payload("Lea", "EMPLOYEE", employee.membershipId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffMembershipId").value(employee.membershipId().toString()));

        mockMvc.perform(get(RESOURCES, salonId).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].staffMembershipId").value(employee.membershipId().toString()));
    }

    @Test
    void une_ressource_sans_lien_reste_valide() throws Exception {
        UUID salonId = createSalon();

        String body = postResource(salonId, ownerAuth(salonId), payload("Chaise", "EMPLOYEE", null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertNoLink(body);
    }

    @Test
    void refuse_un_lien_sur_une_machine() throws Exception {
        UUID salonId = createSalon();
        Member employee = member(salonId, StaffRole.EMPLOYEE);

        postResource(salonId, ownerAuth(salonId), payload("Four", "MACHINE", employee.membershipId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refuse_un_membre_inconnu_d_un_autre_salon_ou_retire() throws Exception {
        UUID salonId = createSalon();
        String auth = ownerAuth(salonId);
        Member foreign = member(createSalon(), StaffRole.EMPLOYEE);
        Member removed = member(salonId, StaffRole.EMPLOYEE);
        StaffMembership membership = staffMembershipRepository.findAliveById(removed.membershipId()).orElseThrow();
        membership.softDelete(Instant.now());
        staffMembershipRepository.save(membership);

        postResource(salonId, auth, payload("A", "EMPLOYEE", UUID.randomUUID())).andExpect(status().isNotFound());
        postResource(salonId, auth, payload("B", "EMPLOYEE", foreign.membershipId()))
                .andExpect(status().isNotFound());
        postResource(salonId, auth, payload("C", "EMPLOYEE", removed.membershipId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void un_membre_ne_peut_etre_lie_qu_a_une_seule_ressource() throws Exception {
        UUID salonId = createSalon();
        String auth = ownerAuth(salonId);
        UUID memberId = member(salonId, StaffRole.EMPLOYEE).membershipId();
        UUID first = createResourceVia(salonId, auth, payload("Lea", "EMPLOYEE", memberId));
        UUID second = createResourceVia(salonId, auth, payload("Chaise", "EMPLOYEE", null));

        postResource(salonId, auth, payload("Lea bis", "EMPLOYEE", memberId)).andExpect(status().isConflict());
        putResource(salonId, second, auth, payload("Chaise", "EMPLOYEE", memberId))
                .andExpect(status().isConflict());
        // renommer la ressource déjà liée ne la met pas en conflit avec elle-même
        putResource(salonId, first, auth, payload("Lea renommee", "EMPLOYEE", memberId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.staffMembershipId").value(memberId.toString()));
    }

    @Test
    void delier_libere_le_membre_pour_une_autre_ressource() throws Exception {
        UUID salonId = createSalon();
        String auth = ownerAuth(salonId);
        UUID memberId = member(salonId, StaffRole.EMPLOYEE).membershipId();
        UUID first = createResourceVia(salonId, auth, payload("Lea", "EMPLOYEE", memberId));

        String unlinked = putResource(salonId, first, auth, payload("Lea", "EMPLOYEE", null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNoLink(unlinked);

        postResource(salonId, auth, payload("Autre", "EMPLOYEE", memberId)).andExpect(status().isCreated());
    }

    @Test
    void supprimer_la_ressource_libere_le_membre() throws Exception {
        UUID salonId = createSalon();
        String auth = ownerAuth(salonId);
        UUID memberId = member(salonId, StaffRole.EMPLOYEE).membershipId();
        UUID first = createResourceVia(salonId, auth, payload("Lea", "EMPLOYEE", memberId));

        mockMvc.perform(delete(RESOURCE, salonId, first).header("Authorization", auth))
                .andExpect(status().isNoContent());

        postResource(salonId, auth, payload("Lea", "EMPLOYEE", memberId)).andExpect(status().isCreated());
    }

    @Test
    void l_index_unique_protege_aussi_contre_une_course_et_ignore_les_ressources_supprimees() {
        UUID salonId = createSalon();
        UUID memberId = member(salonId, StaffRole.EMPLOYEE).membershipId();
        Resource first = resourceRepository.save(linkedResource(salonId, "R1", memberId));

        assertThatThrownBy(() -> resourceRepository.save(linkedResource(salonId, "R2", memberId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        first.softDelete(Instant.now());
        resourceRepository.save(first);
        resourceRepository.save(linkedResource(salonId, "R3", memberId));
    }
}
