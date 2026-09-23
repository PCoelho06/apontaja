package com.apontaja.back.organization.application;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.organization.domain.OrganizationAccountLock;
import com.apontaja.back.organization.domain.OrganizationMembership;
import com.apontaja.back.organization.domain.OrganizationMembershipRepository;
import com.apontaja.back.organization.domain.OrganizationRepository;
import com.apontaja.back.organization.domain.OrganizationRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrganizationProvisioningServiceTest {

    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Mock
    private OrganizationAccountLock organizationAccountLock;

    @Mock
    private OrganizationRepository organizationRepository;

    private OrganizationProvisioningService organizationProvisioningService;

    private final Instant fixedNow = Instant.parse("2026-08-29T10:00:00Z");

    @BeforeEach
    void setUp() {

        organizationProvisioningService = new OrganizationProvisioningService(organizationRepository,
                organizationMembershipRepository, organizationAccountLock, () -> new UUID(0, 1),
                Clock.fixed(fixedNow, ZoneOffset.UTC));
    }

    @Test
    void verrouille_le_compte_avant_de_rechercher_son_organisation() {
        UUID accountId = UUID.randomUUID();

        when(organizationMembershipRepository.findAliveByAccountId(accountId)).thenReturn(List.of());

        organizationProvisioningService.ensureOrganizationForAccount(accountId);

        InOrder inOrder = inOrder(organizationAccountLock, organizationMembershipRepository);

        inOrder.verify(organizationAccountLock).lock(accountId);
        inOrder.verify(organizationMembershipRepository).findAliveByAccountId(accountId);
    }

    @Test
    void retourne_l_organisation_existante_sans_en_creer_une_nouvelle() {
        UUID accountId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        OrganizationMembership membership = new OrganizationMembership(UUID.randomUUID(), accountId, organizationId,
                OrganizationRole.OWNER, fixedNow);

        when(organizationMembershipRepository.findAliveByAccountId(accountId)).thenReturn(List.of(membership));

        UUID result = organizationProvisioningService.ensureOrganizationForAccount(accountId);

        assertThat(result).isEqualTo(organizationId);

        verify(organizationAccountLock).lock(accountId);
        verify(organizationMembershipRepository).findAliveByAccountId(accountId);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void cree_une_organisation_et_un_membership_pour_un_compte_sans_organisation() {
        UUID accountId = UUID.randomUUID();
        UUID expectedOrganizationId = new UUID(0, 1);

        when(organizationMembershipRepository.findAliveByAccountId(accountId)).thenReturn(List.of());

        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(organizationMembershipRepository.save(any(OrganizationMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID result = organizationProvisioningService.ensureOrganizationForAccount(accountId);

        assertThat(result).isEqualTo(expectedOrganizationId);

        InOrder inOrder = inOrder(organizationAccountLock, organizationMembershipRepository, organizationRepository);

        inOrder.verify(organizationAccountLock).lock(accountId);
        inOrder.verify(organizationMembershipRepository).findAliveByAccountId(accountId);
        inOrder.verify(organizationRepository).save(any(Organization.class));
        inOrder.verify(organizationMembershipRepository).save(any(OrganizationMembership.class));
    }

}
