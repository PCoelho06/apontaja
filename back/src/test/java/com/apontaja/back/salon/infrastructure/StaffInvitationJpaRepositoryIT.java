package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.StaffInvitation;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class StaffInvitationJpaRepositoryIT {

    @Autowired
    private StaffInvitationJpaRepository staffInvitationJpaRepository;

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

    @Test
    void persiste_et_retrouve_une_invitation_par_son_token_hash() {
        UUID salonId = createSalon();
        StaffInvitation invitation = staffInvitationJpaRepository
                .save(new StaffInvitation(UUID.randomUUID(), salonId, "invite@example.com", StaffRole.EMPLOYEE, null,
                        "hashed-token", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now()));

        Optional<StaffInvitation> found = staffInvitationJpaRepository.findByTokenHash("hashed-token");

        assertThat(found).contains(invitation);
    }

    @Test
    void rejette_une_seconde_invitation_en_attente_pour_le_meme_salon_et_email() {
        UUID salonId = createSalon();
        staffInvitationJpaRepository.save(new StaffInvitation(UUID.randomUUID(), salonId, "dup@example.com",
                StaffRole.EMPLOYEE, null, "hash-1", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now()));
        staffInvitationJpaRepository.flush();

        assertThatThrownBy(() -> {
            staffInvitationJpaRepository.save(new StaffInvitation(UUID.randomUUID(), salonId, "dup@example.com",
                    StaffRole.MANAGER, null, "hash-2", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now()));
            staffInvitationJpaRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void une_invitation_acceptee_libere_la_paire_salon_email() {
        UUID salonId = createSalon();
        StaffInvitation first = new StaffInvitation(UUID.randomUUID(), salonId, "again@example.com", StaffRole.EMPLOYEE,
                null, "hash-a", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now());
        first.markAccepted(Instant.now());
        staffInvitationJpaRepository.save(first);
        staffInvitationJpaRepository.flush();

        StaffInvitation second = staffInvitationJpaRepository
                .save(new StaffInvitation(UUID.randomUUID(), salonId, "again@example.com", StaffRole.EMPLOYEE, null,
                        "hash-b", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now()));
        staffInvitationJpaRepository.flush();

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void liste_uniquement_les_invitations_en_attente_du_salon() {
        UUID salonId = createSalon();

        StaffInvitation pending = staffInvitationJpaRepository
                .save(new StaffInvitation(UUID.randomUUID(), salonId, "pending@example.com", StaffRole.EMPLOYEE, null,
                        "hash-pending", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now()));

        StaffInvitation accepted = new StaffInvitation(UUID.randomUUID(), salonId, "accepted@example.com",
                StaffRole.EMPLOYEE, null, "hash-accepted", Instant.now().plus(7, ChronoUnit.DAYS), Instant.now());
        accepted.markAccepted(Instant.now());
        staffInvitationJpaRepository.save(accepted);

        List<StaffInvitation> result = staffInvitationJpaRepository.findPendingBySalonId(salonId);

        assertThat(result).containsExactly(pending);
    }
}
