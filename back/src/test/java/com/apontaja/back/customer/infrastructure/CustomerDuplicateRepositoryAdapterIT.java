package com.apontaja.back.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.DuplicateCandidate;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, CustomerDuplicateRepositoryAdapter.class })
class CustomerDuplicateRepositoryAdapterIT {

    @Autowired
    private CustomerDuplicateRepositoryAdapter adapter;

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

    private UUID addCustomer(UUID salonId, String firstName, String email) {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), firstName, "Test", email, null,
                Instant.now());
        entityManager.persistAndFlush(profile);
        link(salonId, profile.getId());
        return profile.getId();
    }

    private void link(UUID salonId, UUID profileId) {
        entityManager.persistAndFlush(new SalonCustomerLink(UUID.randomUUID(), salonId, profileId,
                CustomerLinkSource.MANUAL, null, Instant.now()));
    }

    @Test
    void renvoie_les_profils_vivants_non_anonymises_du_salon_seulement() {
        UUID salonId = createSalon();
        UUID kept = addCustomer(salonId, "Alice", "a@example.com");
        CustomerProfile anonymous = new CustomerProfile(UUID.randomUUID(), "Bob", "Test", "b@example.com", null,
                Instant.now());
        anonymous.anonymize(Instant.now());
        entityManager.persistAndFlush(anonymous);
        link(salonId, anonymous.getId());
        addCustomer(createSalon(), "Carol", "c@example.com");

        List<DuplicateCandidate> candidates = adapter.findCandidates(salonId);

        assertThat(candidates).extracting(DuplicateCandidate::profileId).containsExactly(kept);
        assertThat(candidates.get(0).email()).isEqualTo("a@example.com");
        assertThat(candidates.get(0).accountId()).isNull();
    }

    @Test
    void detecte_les_profils_lies_a_un_autre_salon() {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        UUID shared = addCustomer(salonA, "Alice", "a@example.com");
        link(salonB, shared);
        UUID alone = addCustomer(salonA, "Bob", "b@example.com");

        Set<UUID> result = adapter.findProfileIdsLinkedElsewhere(salonA, List.of(shared, alone));

        assertThat(result).containsExactly(shared);
        assertThat(adapter.findProfileIdsLinkedElsewhere(salonA, List.of())).isEmpty();
    }
}
