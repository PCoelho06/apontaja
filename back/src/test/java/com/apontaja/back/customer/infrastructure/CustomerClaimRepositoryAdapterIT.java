package com.apontaja.back.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.customer.domain.ClaimCandidate;
import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, CustomerClaimRepositoryAdapter.class })
class CustomerClaimRepositoryAdapterIT {

    private static final Instant T0 = Instant.parse("2020-01-01T10:00:00Z");

    @Autowired
    private CustomerClaimRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    private UUID createSalon() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        return salon.getId();
    }

    private UUID createAccount() {
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "Test", "User", "hash",
                Instant.now());
        entityManager.persistAndFlush(account);
        return account.getId();
    }

    private CustomerProfile profile(String email, Instant createdAt) {
        return new CustomerProfile(UUID.randomUUID(), "Alice", "Martin", email, null, createdAt);
    }

    private SalonCustomerLink link(UUID salonId, CustomerProfile profile) {
        SalonCustomerLink link = new SalonCustomerLink(UUID.randomUUID(), salonId, profile.getId(),
                CustomerLinkSource.MANUAL, null, Instant.now());
        entityManager.persistAndFlush(link);
        return link;
    }

    private CustomerProfile addCustomer(UUID salonId, String email, Instant createdAt) {
        CustomerProfile profile = profile(email, createdAt);
        entityManager.persistAndFlush(profile);
        link(salonId, profile);
        return profile;
    }

    @Test
    void trouve_les_profils_par_email_sans_tenir_compte_de_la_casse_du_plus_ancien_au_plus_recent() {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        CustomerProfile recent = addCustomer(salonA, email.toUpperCase(), T0.plusSeconds(60));
        CustomerProfile old = addCustomer(salonB, email, T0);
        addCustomer(salonA, UUID.randomUUID() + "@example.com", T0);

        List<ClaimCandidate> candidates = adapter.findClaimCandidates(email);

        assertThat(candidates).extracting(ClaimCandidate::profileId).containsExactly(old.getId(), recent.getId());
        assertThat(candidates).extracting(ClaimCandidate::salonId).containsExactly(salonB, salonA);
    }

    @Test
    void exclut_les_profils_reclames_anonymises_ou_supprimes_et_les_liens_supprimes() {
        UUID salon = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        CustomerProfile kept = addCustomer(salon, email, T0);

        CustomerProfile claimed = profile(email, T0);
        claimed.claimBy(createAccount());
        entityManager.persistAndFlush(claimed);
        link(salon, claimed);

        CustomerProfile anonymized = profile(email, T0);
        anonymized.anonymize(Instant.now());
        entityManager.persistAndFlush(anonymized);
        link(salon, anonymized);

        // deletedAt n'est atteignable que par la fusion : posé par réflexion pour isoler le filtre.
        CustomerProfile deleted = profile(email, T0);
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.now());
        entityManager.persistAndFlush(deleted);
        link(salon, deleted);

        CustomerProfile unlinked = profile(email, T0);
        entityManager.persistAndFlush(unlinked);
        link(salon, unlinked).softDelete(Instant.now());
        entityManager.flush();

        assertThat(adapter.findClaimCandidates(email)).extracting(ClaimCandidate::profileId)
                .containsExactly(kept.getId());
    }

    @Test
    void renvoie_une_ligne_par_lien_vivant_pour_un_profil_partage() {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        String email = UUID.randomUUID() + "@example.com";
        CustomerProfile shared = addCustomer(salonA, email, T0);
        link(salonB, shared);

        assertThat(adapter.findClaimCandidates(email)).extracting(ClaimCandidate::salonId)
                .containsExactlyInAnyOrder(salonA, salonB);
    }

    @Test
    void liste_les_salons_ou_le_compte_possede_un_profil_reclame_vivant() {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        UUID salonC = createSalon();
        UUID account = createAccount();
        UUID otherAccount = createAccount();

        CustomerProfile inA = profile("a-" + UUID.randomUUID() + "@example.com", T0);
        inA.claimBy(account);
        entityManager.persistAndFlush(inA);
        link(salonA, inA);

        CustomerProfile ofOther = profile("b-" + UUID.randomUUID() + "@example.com", T0);
        ofOther.claimBy(otherAccount);
        entityManager.persistAndFlush(ofOther);
        link(salonB, ofOther);

        CustomerProfile unlinked = profile("c-" + UUID.randomUUID() + "@example.com", T0);
        unlinked.claimBy(account);
        entityManager.persistAndFlush(unlinked);
        link(salonC, unlinked).softDelete(Instant.now());
        entityManager.flush();

        assertThat(adapter.findSalonIdsHoldingClaimedProfile(account)).containsExactly(salonA);
        assertThat(adapter.findSalonIdsHoldingClaimedProfile(UUID.randomUUID())).isEmpty();
    }
}
