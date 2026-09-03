package com.apontaja.back.organization.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationRole;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class OrganizationMembershipJpaRepositoryIT {

        @Autowired
        private OrganizationJpaRepository organizationJpaRepository;

        @Autowired
        private OrganizationMembershipJpaRepository membershipJpaRepository;

        // AccountJpaRepository est package-private dans
        // com.apontaja.back.account.infrastructure,
        // donc invisible depuis ce package : on crée le compte via TestEntityManager
        // directement sur
        // l'entité publique Account, sans dépendre du repository d'un autre domaine.
        @Autowired
        private TestEntityManager entityManager;

        private UUID createAccount() {
                Instant now = Instant.now();
                Account account = new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash", now);
                entityManager.persistAndFlush(account);
                return account.getId();
        }

        private Organization createOrganization() {
                return organizationJpaRepository.save(new Organization(UUID.randomUUID(), "Org Test", Instant.now()));
        }

        @Test
        void persiste_et_retrouve_un_membership_vivant() {
                UUID accountId = createAccount();
                Organization org = createOrganization();

                OrganizationMembership membership = membershipJpaRepository.save(new OrganizationMembership(
                                UUID.randomUUID(), accountId, org.getId(), OrganizationRole.OWNER, Instant.now()));

                Optional<OrganizationMembership> found = membershipJpaRepository
                                .findByAccountIdAndOrganizationIdAndDeletedAtIsNull(accountId, org.getId());

                assertThat(found).contains(membership);
        }

        @Test
        void ignore_un_membership_soft_deleted() {
                UUID accountId = createAccount();
                Organization org = createOrganization();

                OrganizationMembership membership = new OrganizationMembership(UUID.randomUUID(), accountId,
                                org.getId(), OrganizationRole.OWNER, Instant.now());
                membership.softDelete(Instant.now());
                membershipJpaRepository.save(membership);
                membershipJpaRepository.flush();

                assertThat(membershipJpaRepository.findByAccountIdAndOrganizationIdAndDeletedAtIsNull(accountId,
                                org.getId())).isEmpty();
        }

        @Test
        void rejette_un_doublon_vivant_pour_le_meme_compte_et_la_meme_organisation() {
                UUID accountId = createAccount();
                Organization org = createOrganization();

                membershipJpaRepository.save(new OrganizationMembership(UUID.randomUUID(), accountId, org.getId(),
                                OrganizationRole.OWNER, Instant.now()));
                membershipJpaRepository.flush();

                assertThatThrownBy(() -> {
                        membershipJpaRepository.save(new OrganizationMembership(UUID.randomUUID(), accountId,
                                        org.getId(), OrganizationRole.OWNER, Instant.now()));
                        membershipJpaRepository.flush();
                }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void liste_les_memberships_vivants_d_une_organisation() {
                Organization org = createOrganization();

                membershipJpaRepository.save(new OrganizationMembership(UUID.randomUUID(), createAccount(), org.getId(),
                                OrganizationRole.OWNER, Instant.now()));
                membershipJpaRepository.save(new OrganizationMembership(UUID.randomUUID(), createAccount(), org.getId(),
                                OrganizationRole.OWNER, Instant.now()));

                List<OrganizationMembership> result = membershipJpaRepository
                                .findByOrganizationIdAndDeletedAtIsNull(org.getId());

                assertThat(result).hasSize(2);
        }
}
