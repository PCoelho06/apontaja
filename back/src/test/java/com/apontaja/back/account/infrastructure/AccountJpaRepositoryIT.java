package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class AccountJpaRepositoryIT {

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void un_meme_email_ne_peut_pas_etre_reutilise_par_deux_comptes_vivants() {
        Instant now = Instant.now();
        accountJpaRepository.save(new Account(UUID.randomUUID(), "alice@example.com", "hash-1", now));
        entityManager.flush();

        Account duplicate = new Account(UUID.randomUUID(), "alice@example.com", "hash-2", now);

        assertThatThrownBy(() -> {
            accountJpaRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void un_email_libere_par_soft_delete_redevient_utilisable() {
        Instant now = Instant.now();
        Account first = new Account(UUID.randomUUID(), "bob@example.com", "hash-1", now);
        first.softDelete(now);
        accountJpaRepository.save(first);
        entityManager.flush();

        Account second = new Account(UUID.randomUUID(), "bob@example.com", "hash-2", now);
        accountJpaRepository.save(second);
        entityManager.flush();

        assertThat(accountJpaRepository.findAliveByEmail("bob@example.com"))
                .contains(second);
    }

    @Test
    void find_alive_by_email_ignore_les_comptes_supprimes() {
        Instant now = Instant.now();
        Account deleted = new Account(UUID.randomUUID(), "carol@example.com", "hash", now);
        deleted.softDelete(now);
        accountJpaRepository.save(deleted);
        entityManager.flush();

        Optional<Account> result = accountJpaRepository.findAliveByEmail("carol@example.com");

        assertThat(result).isEmpty();
        assertThat(accountJpaRepository.existsAliveByEmail("carol@example.com")).isFalse();
    }
}
