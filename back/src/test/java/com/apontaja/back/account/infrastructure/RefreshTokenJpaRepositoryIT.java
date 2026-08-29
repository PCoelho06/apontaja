package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.RefreshToken;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class RefreshTokenJpaRepositoryIT {

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID createAccount() {
        Instant now = Instant.now();
        Account account = new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", now);
        accountJpaRepository.save(account);
        entityManager.flush();
        return account.getId();
    }

    @Test
    void token_hash_est_unique_en_base() {
        UUID accountId = createAccount();
        Instant now = Instant.now();

        refreshTokenJpaRepository.save(
                new RefreshToken(UUID.randomUUID(), accountId, "same-hash", "device-1", now.plus(30, ChronoUnit.DAYS),
                        now));
        entityManager.flush();

        RefreshToken duplicate = new RefreshToken(UUID.randomUUID(), accountId, "same-hash", "device-2",
                now.plus(30, ChronoUnit.DAYS), now);

        assertThatThrownBy(() -> {
            refreshTokenJpaRepository.save(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void find_active_by_account_id_exclut_les_tokens_revoques() {
        UUID accountId = createAccount();
        Instant now = Instant.now();

        RefreshToken active = new RefreshToken(UUID.randomUUID(), accountId, "hash-active", null,
                now.plus(30, ChronoUnit.DAYS), now);
        RefreshToken revoked = new RefreshToken(UUID.randomUUID(), accountId, "hash-revoked", null,
                now.plus(30, ChronoUnit.DAYS), now);
        revoked.revoke(now);

        refreshTokenJpaRepository.save(active);
        refreshTokenJpaRepository.save(revoked);
        entityManager.flush();

        List<RefreshToken> result = refreshTokenJpaRepository.findActiveByAccountId(accountId);

        assertThat(result).extracting(RefreshToken::getTokenHash).containsExactly("hash-active");
    }
}
