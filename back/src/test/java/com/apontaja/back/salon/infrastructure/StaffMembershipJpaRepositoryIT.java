package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffRole;
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
class StaffMembershipJpaRepositoryIT {

        @Autowired
        private StaffMembershipJpaRepository staffMembershipJpaRepository;

        @Autowired
        private TestEntityManager entityManager;

        private UUID createAccount() {
                Account account = new Account(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "hash",
                                Instant.now());
                entityManager.persistAndFlush(account);
                return account.getId();
        }

        private UUID createSalon() {
                Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
                entityManager.persistAndFlush(organization);

                Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                                "Vence", "France", "Europe/Paris", Instant.now());
                entityManager.persistAndFlush(salon);
                return salon.getId();
        }

        @Test
        void persiste_et_retrouve_un_staff_membership_vivant() {
                UUID accountId = createAccount();
                UUID salonId = createSalon();

                StaffMembership membership = staffMembershipJpaRepository.save(new StaffMembership(UUID.randomUUID(),
                                accountId, salonId, StaffRole.OWNER, Instant.now()));

                Optional<StaffMembership> found = staffMembershipJpaRepository
                                .findByAccountIdAndSalonIdAndDeletedAtIsNull(accountId, salonId);

                assertThat(found).contains(membership);
        }

        @Test
        void ignore_un_staff_membership_soft_deleted() {
                UUID accountId = createAccount();
                UUID salonId = createSalon();

                StaffMembership membership = new StaffMembership(UUID.randomUUID(), accountId, salonId,
                                StaffRole.EMPLOYEE, Instant.now());
                membership.softDelete(Instant.now());
                staffMembershipJpaRepository.save(membership);
                staffMembershipJpaRepository.flush();

                assertThat(staffMembershipJpaRepository.findByAccountIdAndSalonIdAndDeletedAtIsNull(accountId, salonId))
                                .isEmpty();
        }

        @Test
        void rejette_un_doublon_vivant_pour_le_meme_compte_et_le_meme_salon() {
                UUID accountId = createAccount();
                UUID salonId = createSalon();

                staffMembershipJpaRepository.save(new StaffMembership(UUID.randomUUID(), accountId, salonId,
                                StaffRole.MANAGER, Instant.now()));
                staffMembershipJpaRepository.flush();

                assertThatThrownBy(() -> {
                        staffMembershipJpaRepository.save(new StaffMembership(UUID.randomUUID(), accountId, salonId,
                                        StaffRole.EMPLOYEE, Instant.now()));
                        staffMembershipJpaRepository.flush();
                }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void autorise_le_meme_compte_sur_des_salons_differents() {
                UUID accountId = createAccount();
                UUID salonA = createSalon();
                UUID salonB = createSalon();

                staffMembershipJpaRepository.save(new StaffMembership(UUID.randomUUID(), accountId, salonA,
                                StaffRole.OWNER, Instant.now()));
                staffMembershipJpaRepository.save(new StaffMembership(UUID.randomUUID(), accountId, salonB,
                                StaffRole.EMPLOYEE, Instant.now()));

                List<StaffMembership> result = staffMembershipJpaRepository
                                .findByAccountIdAndDeletedAtIsNull(accountId);

                assertThat(result).hasSize(2);
        }
}
