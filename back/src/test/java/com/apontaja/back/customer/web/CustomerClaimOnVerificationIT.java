package com.apontaja.back.customer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.customer.application.CreateCustomerCommand;
import com.apontaja.back.customer.application.CustomerManagementService;
import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rattachement d'un compte à ses profils de carnets à la vérification de l'email : de bout en bout (inscription du
 * portail client, email, confirmation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
class CustomerClaimOnVerificationIT {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private SalonRepository salonRepository;
    @Autowired
    private CustomerManagementService customerManagementService;
    @Autowired
    private CustomerProfileRepository profileRepository;
    @Autowired
    private SalonCustomerLinkRepository linkRepository;
    @Autowired
    private IdGenerator idGenerator;

    @MockitoBean
    private EmailSender emailSender;

    private UUID createSalon() {
        UUID organizationId = organizationRepository
                .save(new Organization(idGenerator.generate(), "Org Test", Instant.now())).getId();
        return salonRepository.save(new Salon(idGenerator.generate(), organizationId, "Salon Test", "1 rue du Test",
                "06140", "Vence", "France", "Europe/Paris", Instant.now())).getId();
    }

    private UUID addCustomer(UUID salonId, String firstName, String email) {
        return customerManagementService
                .create(new CreateCustomerCommand(salonId, firstName, "Martin", email, null, null)).customerProfileId();
    }

    private UUID claimedByAnotherAccount(UUID salonId, String email) {
        UUID otherAccount = accountRepository.save(new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com",
                "Autre", "Compte", "hash", Instant.now())).getId();
        UUID profileId = addCustomer(salonId, "Deja", email);
        CustomerProfile profile = profileRepository.findAliveById(profileId).orElseThrow();
        profile.claimBy(otherAccount);
        profileRepository.save(profile);
        return profileId;
    }

    private UUID accountIdOf(UUID profileId) {
        return profileRepository.findAliveById(profileId).orElseThrow().getAccountId();
    }

    private void registerAsClient(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "email": "%s",
                  "password": "un-mot-de-passe-suffisamment-long",
                  "firstName": "Alice",
                  "lastName": "Martin",
                  "acceptTos": true,
                  "acceptPrivacy": true,
                  "portal": "CLIENT"
                }
                """.formatted(email))).andExpect(status().isAccepted());
    }

    private String capturedVerificationBody(String email) {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).send(eq(email), anyString(), bodyCaptor.capture());
        return bodyCaptor.getAllValues().stream().filter(body -> body.contains("confirmar-email")).findFirst()
                .orElseThrow(() -> new AssertionError("Aucun email de vérification capturé"));
    }

    private void confirm(String body) throws Exception {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        mockMvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON).content("""
                {"token": "%s"}
                """.formatted(matcher.group(1)))).andExpect(status().is2xxSuccessful());
    }

    @Test
    void la_verification_rattache_un_profil_par_salon_et_pas_avant() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        // Casse différente : l'email d'un carnet est saisi à la main.
        UUID oldestInA = addCustomer(salonA, "Alice", email.toUpperCase());
        UUID duplicateInA = addCustomer(salonA, "Alicia", email);
        UUID inB = addCustomer(salonB, "Alice", email);
        UUID someoneElse = addCustomer(salonA, "Bob", UUID.randomUUID() + "@example.com");

        registerAsClient(email);

        // Inscrit mais pas vérifié : rien n'est rattaché.
        assertThat(accountIdOf(oldestInA)).isNull();
        assertThat(accountIdOf(inB)).isNull();

        String body = capturedVerificationBody(email);
        assertThat(body).contains("http://localhost:5174/confirmar-email?token=");
        confirm(body);

        UUID accountId = accountRepository.findAliveByEmail(email).orElseThrow().getId();
        assertThat(accountIdOf(oldestInA)).isEqualTo(accountId);
        assertThat(accountIdOf(inB)).isEqualTo(accountId);
        assertThat(accountIdOf(duplicateInA)).isNull();
        assertThat(accountIdOf(someoneElse)).isNull();
    }

    @Test
    void la_verification_ne_modifie_ni_l_identite_ni_les_notes_du_salon() throws Exception {
        UUID salon = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        UUID profileId = customerManagementService
                .create(new CreateCustomerCommand(salon, "Alicia", "Dupont", email, "0612345678", "Cliente fidèle"))
                .customerProfileId();

        registerAsClient(email);
        confirm(capturedVerificationBody(email));

        CustomerProfile profile = profileRepository.findAliveById(profileId).orElseThrow();
        assertThat(profile.getAccountId()).isNotNull();
        assertThat(profile.getFirstName()).isEqualTo("Alicia");
        assertThat(profile.getLastName()).isEqualTo("Dupont");
        assertThat(profile.getPhone()).isEqualTo("0612345678");
        assertThat(linkRepository.findAliveBySalonIdAndCustomerProfileId(salon, profileId).orElseThrow()
                .getInternalNotes()).isEqualTo("Cliente fidèle");
    }

    @Test
    void un_profil_deja_reclame_par_un_autre_compte_n_est_pas_touche() throws Exception {
        UUID salon = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        UUID taken = claimedByAnotherAccount(salon, email);
        UUID free = addCustomer(createSalon(), "Libre", email);

        registerAsClient(email);
        confirm(capturedVerificationBody(email));

        UUID accountId = accountRepository.findAliveByEmail(email).orElseThrow().getId();
        assertThat(accountIdOf(taken)).isNotEqualTo(accountId).isNotNull();
        assertThat(accountIdOf(free)).isEqualTo(accountId);
    }

    @Test
    void un_profil_lie_a_plusieurs_salons_n_est_pas_rattache() throws Exception {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        UUID shared = addCustomer(salonA, "Partage", email);
        linkRepository.save(new SalonCustomerLink(idGenerator.generate(), salonB, shared, CustomerLinkSource.MANUAL,
                null, Instant.now()));

        registerAsClient(email);
        confirm(capturedVerificationBody(email));

        assertThat(accountIdOf(shared)).isNull();
    }
}
