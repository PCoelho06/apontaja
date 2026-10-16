package com.apontaja.back.salon.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.salon.domain.StaffMembershipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffMemberQueryServiceListTest {

    private static final Instant SINCE = Instant.parse("2020-01-01T00:00:00Z");

    @Mock
    private StaffMembershipManagementService managementService;
    @Mock
    private StaffMembershipRepository staffMembershipRepository;
    @Mock
    private AccountQueryService accountQueryService;
    @Mock
    private StaffManagementGuard staffManagementGuard;

    private StaffMemberQueryService service;
    private final UUID salonId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StaffMemberQueryService(managementService, staffMembershipRepository, accountQueryService,
                staffManagementGuard);
        when(managementService.listMembers(salonId))
                .thenReturn(List.of(new StaffMemberSummary(UUID.randomUUID(), accountId, "EMPLOYEE", SINCE)));
    }

    private void accountIsAlive() {
        AccountSummary summary = new AccountSummary(accountId, "pierre@example.com", true, "Pierre", "Dupont");
        when(accountQueryService.findAliveById(accountId)).thenReturn(Optional.of(summary));
    }

    @Test
    void renseigne_l_email_quand_l_acteur_peut_le_voir() {
        when(staffManagementGuard.canViewStaffEmails(actorId, salonId)).thenReturn(true);
        accountIsAlive();

        List<StaffMemberDetails> result = service.listMembers(salonId, actorId);

        assertThat(result).singleElement().satisfies(member -> {
            assertThat(member.displayName()).isEqualTo("Pierre D.");
            assertThat(member.email()).isEqualTo("pierre@example.com");
            assertThat(member.role()).isEqualTo("EMPLOYEE");
        });
    }

    @Test
    void masque_l_email_sinon_mais_garde_le_nom_d_affichage() {
        when(staffManagementGuard.canViewStaffEmails(actorId, salonId)).thenReturn(false);
        accountIsAlive();

        List<StaffMemberDetails> result = service.listMembers(salonId, actorId);

        assertThat(result).singleElement().satisfies(member -> {
            assertThat(member.displayName()).isEqualTo("Pierre D.");
            assertThat(member.email()).isNull();
        });
    }

    @Test
    void signale_un_compte_supprime_sans_exposer_d_email() {
        when(staffManagementGuard.canViewStaffEmails(actorId, salonId)).thenReturn(true);
        when(accountQueryService.findAliveById(accountId)).thenReturn(Optional.empty());

        List<StaffMemberDetails> result = service.listMembers(salonId, actorId);

        assertThat(result).singleElement().satisfies(member -> {
            assertThat(member.displayName()).isEqualTo("Compte supprimé");
            assertThat(member.email()).isNull();
        });
    }
}
