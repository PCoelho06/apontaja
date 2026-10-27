package com.apontaja.back.customer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.customer.application.CreateCustomerCommand;
import com.apontaja.back.customer.application.CustomerManagementService;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

/** Un profil réclamé par un compte peut être conservé lors d'une fusion, jamais absorbé. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class CustomerMergeClaimedIT {

    private static final String CUSTOMERS = "/api/salons/{salonId}/customers";

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
    private CustomerManagementService customerManagementService;
    @Autowired
    private CustomerProfileRepository profileRepository;
    @Autowired
    private IdGenerator idGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID createSalon() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private String ownerOf(UUID salonId) {
        UUID accountId = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Test", "User", "hash", Instant.now())).getId();
        staffMembershipRepository
                .save(new StaffMembership(idGenerator.generate(), accountId, salonId, StaffRole.OWNER, Instant.now()));
        return "Bearer " + accessTokenIssuer.issue(accountId);
    }

    private UUID addCustomer(UUID salonId, String firstName, String email, String phone) {
        return customerManagementService.create(new CreateCustomerCommand(salonId, firstName, "Martin", email, phone,
                null)).customerProfileId();
    }

    private void claim(UUID profileId) {
        UUID accountId = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Client", "Compte", "hash", Instant.now())).getId();
        CustomerProfile profile = profileRepository.findAliveById(profileId).orElseThrow();
        profile.claimBy(accountId);
        profileRepository.save(profile);
    }

    private String mergeBody(UUID survivorId, UUID absorbedId) {
        return "{\"survivorId\":\"%s\",\"absorbedIds\":[\"%s\"]}".formatted(survivorId, absorbedId);
    }

    @Test
    void le_rapport_de_doublons_signale_le_profil_reclame() throws Exception {
        UUID salon = createSalon();
        String owner = ownerOf(salon);
        String email = UUID.randomUUID() + "@example.com";
        UUID claimed = addCustomer(salon, "Alice", email, null);
        UUID manual = addCustomer(salon, "Alicia", email, "0612345678");
        claim(claimed);

        String json = mockMvc.perform(get(CUSTOMERS + "/duplicates", salon).header("Authorization", owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode members = objectMapper.readTree(json).get("groups").get(0).get("customers");
        for (JsonNode member : members) {
            boolean isClaimed = member.get("customerProfileId").asText().equals(claimed.toString());
            assertThat(member.get("claimed").asBoolean()).isEqualTo(isClaimed);
            assertThat(member.get("mergeable").asBoolean()).isEqualTo(!isClaimed);
            assertThat(member.get("customerProfileId").asText()).isIn(claimed.toString(), manual.toString());
        }
    }

    @Test
    void un_profil_reclame_peut_etre_conserve_et_recoit_le_telephone_du_doublon() throws Exception {
        UUID salon = createSalon();
        String owner = ownerOf(salon);
        String email = UUID.randomUUID() + "@example.com";
        UUID claimed = addCustomer(salon, "Alice", email, null);
        UUID manual = addCustomer(salon, "Alicia", email, "0612345678");
        claim(claimed);

        mockMvc.perform(post(CUSTOMERS + "/merge", salon).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(claimed, manual)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerProfileId").value(claimed.toString()))
                .andExpect(jsonPath("$.phone").value("0612345678"));

        assertThat(profileRepository.findAliveById(claimed).orElseThrow().getAccountId()).isNotNull();
        assertThat(profileRepository.findAliveById(manual)).isEmpty();
    }

    @Test
    void un_profil_reclame_ne_peut_pas_etre_absorbe() throws Exception {
        UUID salon = createSalon();
        String owner = ownerOf(salon);
        String email = UUID.randomUUID() + "@example.com";
        UUID manual = addCustomer(salon, "Alicia", email, "0612345678");
        UUID claimed = addCustomer(salon, "Alice", email, null);
        claim(claimed);

        mockMvc.perform(post(CUSTOMERS + "/merge", salon).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(mergeBody(manual, claimed)))
                .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("CUSTOMER_CLAIMED"));

        assertThat(profileRepository.findAliveById(claimed)).isPresent();
        assertThat(profileRepository.findAliveById(manual)).isPresent();
    }
}


