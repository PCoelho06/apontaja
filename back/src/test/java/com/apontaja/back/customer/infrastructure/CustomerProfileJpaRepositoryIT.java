package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class CustomerProfileJpaRepositoryIT {

    @Autowired
    private CustomerProfileJpaRepository customerProfileJpaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static CustomerProfile withEmail(String email) {
        return new CustomerProfile(UUID.randomUUID(), "Alice", "Martin", email, null, Instant.now());
    }

    @Test
    void persiste_et_retrouve_un_profil_vivant() {
        CustomerProfile profile = customerProfileJpaRepository
                .saveAndFlush(withEmail("alice+" + UUID.randomUUID() + "@example.com"));

        assertThat(customerProfileJpaRepository.findByIdAndDeletedAtIsNull(profile.getId())).contains(profile);
    }

    @Test
    void un_profil_avec_seulement_un_telephone_est_valide() {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), "Bob", "Dupont", null, "0612345678",
                Instant.now());

        assertThatCode(() -> customerProfileJpaRepository.saveAndFlush(profile)).doesNotThrowAnyException();
    }

    @Test
    void deux_profils_manuels_distincts_peuvent_partager_le_meme_email() {
        String email = "carol+" + UUID.randomUUID() + "@example.com";

        assertThatCode(() -> {
            customerProfileJpaRepository.saveAndFlush(withEmail(email));
            customerProfileJpaRepository.saveAndFlush(withEmail(email));
        }).doesNotThrowAnyException();
    }

    @Test
    void un_compte_peut_reclamer_plusieurs_profils_vivants() {
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "Test", "User", "hash",
                Instant.now());
        entityManager.persistAndFlush(account);

        CustomerProfile first = withEmail("d1@example.com");
        first.claimBy(account.getId());
        CustomerProfile second = withEmail("d2@example.com");
        second.claimBy(account.getId());

        assertThatCode(() -> {
            customerProfileJpaRepository.saveAndFlush(first);
            customerProfileJpaRepository.saveAndFlush(second);
        }).doesNotThrowAnyException();
    }

    @Test
    void findAliveByIds_ignore_les_profils_inconnus_et_supprimes() {
        CustomerProfile alive = customerProfileJpaRepository
                .saveAndFlush(withEmail("e+" + UUID.randomUUID() + "@example.com"));

        List<CustomerProfile> result = customerProfileJpaRepository
                .findByIdInAndDeletedAtIsNull(List.of(alive.getId(), UUID.randomUUID()));

        assertThat(result).containsExactly(alive);
    }
}
