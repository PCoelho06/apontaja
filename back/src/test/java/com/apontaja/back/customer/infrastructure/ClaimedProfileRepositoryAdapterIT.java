package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.customer.domain.ClaimedProfileRepository;
import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, ClaimedProfileRepositoryAdapter.class })
class ClaimedProfileRepositoryAdapterIT {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClaimedProfileRepository repository;

    private UUID accountId;
    private UUID otherAccountId;
    private UUID salonId;
    private UUID otherSalonId;

    @BeforeEach
    void setUp() {
        accountId = persistAccount();
        otherAccountId = persistAccount();
        UUID organizationId = UUID.randomUUID();
        em.persistAndFlush(new Organization(organizationId, "Org Test", NOW));
        salonId = persistSalon(organizationId);
        otherSalonId = persistSalon(organizationId);
    }

    private UUID persistAccount() {
        UUID id = UUID.randomUUID();
        em.persistAndFlush(new Account(id, id + "@example.com", "Test", "User", "hash", NOW));
        return id;
    }

    private UUID persistSalon(UUID organizationId) {
        UUID id = UUID.randomUUID();
        em.persistAndFlush(new Salon(id, organizationId, "Salon Test", "1 rue du Test", "06140", "Vence", "France",
                "Europe/Paris", NOW));
        return id;
    }

    /** Profil + lien vivant dans le salon ; {@code owner} nul = profil non réclamé. */
    private SalonCustomerLink persistProfile(UUID salon, UUID owner, Instant createdAt) {
        UUID profileId = UUID.randomUUID();
        CustomerProfile profile = new CustomerProfile(profileId, "Alice", "Martin", profileId + "@example.com", null,
                createdAt);
        if (owner != null) {
            profile.claimBy(owner);
        }
        em.persistAndFlush(profile);
        SalonCustomerLink link = new SalonCustomerLink(UUID.randomUUID(), salon, profileId, CustomerLinkSource.SELF,
                null, createdAt);
        return em.persistAndFlush(link);
    }

    @Test
    void retrouve_le_profil_reclame_par_le_compte_dans_le_salon() {
        SalonCustomerLink link = persistProfile(salonId, accountId, NOW);

        assertThat(repository.findClaimedProfileId(accountId, salonId)).contains(link.getCustomerProfileId());
    }

    @Test
    void ignore_un_profil_non_reclame_ou_reclame_par_un_autre_compte() {
        persistProfile(salonId, null, NOW);
        persistProfile(salonId, otherAccountId, NOW);

        assertThat(repository.findClaimedProfileId(accountId, salonId)).isEmpty();
    }

    @Test
    void ignore_le_profil_reclame_dans_un_autre_salon() {
        persistProfile(otherSalonId, accountId, NOW);

        assertThat(repository.findClaimedProfileId(accountId, salonId)).isEmpty();
        assertThat(repository.findClaimedProfileId(accountId, otherSalonId)).isPresent();
    }

    @Test
    void ignore_un_lien_supprime() {
        SalonCustomerLink link = persistProfile(salonId, accountId, NOW);
        link.softDelete(NOW.plusSeconds(60));
        em.persistAndFlush(link);

        assertThat(repository.findClaimedProfileId(accountId, salonId)).isEmpty();
    }

    @Test
    void ignore_un_profil_anonymise() {
        SalonCustomerLink link = persistProfile(salonId, accountId, NOW);
        CustomerProfile profile = em.find(CustomerProfile.class, link.getCustomerProfileId());
        profile.anonymize(NOW.plusSeconds(60));
        em.persistAndFlush(profile);

        assertThat(repository.findClaimedProfileId(accountId, salonId)).isEmpty();
    }

    @Test
    void renvoie_le_plus_ancien_s_il_y_en_a_plusieurs() {
        persistProfile(salonId, accountId, NOW.plusSeconds(3600));
        SalonCustomerLink oldest = persistProfile(salonId, accountId, NOW);

        assertThat(repository.findClaimedProfileId(accountId, salonId)).contains(oldest.getCustomerProfileId());
    }
}
