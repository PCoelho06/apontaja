package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.ConsentRecord;
import com.apontaja.back.account.domain.ConsentType;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class ConsentRecordJpaRepositoryIT {

        @Autowired
        private AccountJpaRepository accountJpaRepository;

        @Autowired
        private ConsentRecordJpaRepository consentRecordJpaRepository;

        @Test
        void enregistre_et_retrouve_les_consentements_d_un_compte() {
                Instant now = Instant.now();
                Account account = new Account(UUID.randomUUID(), "dave@example.com", "hash", now);
                accountJpaRepository.save(account);
                accountJpaRepository.flush();

                consentRecordJpaRepository.save(
                                new ConsentRecord(UUID.randomUUID(), account.getId(), ConsentType.TOS, "v1", now));
                consentRecordJpaRepository.save(
                                new ConsentRecord(UUID.randomUUID(), account.getId(), ConsentType.PRIVACY, "v1", now));
                consentRecordJpaRepository.flush();

                List<ConsentRecord> result = consentRecordJpaRepository.findByAccountId(account.getId());

                assertThat(result).extracting(ConsentRecord::getType)
                                .containsExactlyInAnyOrder(ConsentType.TOS, ConsentType.PRIVACY);
        }
}
