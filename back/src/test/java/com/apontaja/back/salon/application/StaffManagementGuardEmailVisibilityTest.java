package com.apontaja.back.salon.application;

import com.apontaja.back.organization.application.OrganizationMembershipQueryService;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffManagementGuardEmailVisibilityTest {

    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");

    @Mock
    private SalonRepository salonRepository;
    @Mock
    private StaffMembershipRepository staffMembershipRepository;
    @Mock
    private OrganizationMembershipQueryService organizationMembershipQueryService;

    private StaffManagementGuard guard;
    private final UUID actorId = UUID.randomUUID();
    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        guard = new StaffManagementGuard(salonRepository, staffMembershipRepository,
                organizationMembershipQueryService);
    }

    private void actorHasRole(StaffRole role) {
        when(staffMembershipRepository.findAliveByAccountIdAndSalonId(actorId, salonId))
                .thenReturn(Optional.of(new StaffMembership(UUID.randomUUID(), actorId, salonId, role, NOW)));
    }

    @Test
    void un_owner_voit_les_emails() {
        actorHasRole(StaffRole.OWNER);

        assertThat(guard.canViewStaffEmails(actorId, salonId)).isTrue();
    }

    @Test
    void un_manager_voit_les_emails() {
        actorHasRole(StaffRole.MANAGER);

        assertThat(guard.canViewStaffEmails(actorId, salonId)).isTrue();
    }

    @Test
    void un_employee_ne_voit_pas_les_emails() {
        actorHasRole(StaffRole.EMPLOYEE);

        assertThat(guard.canViewStaffEmails(actorId, salonId)).isFalse();
    }

    @Test
    void un_compte_sans_rattachement_ne_voit_pas_les_emails() {
        assertThat(guard.canViewStaffEmails(actorId, salonId)).isFalse();
    }

    @Test
    void un_owner_d_organisation_sans_staff_direct_voit_les_emails() {
        UUID organizationId = UUID.randomUUID();
        Salon salon = new Salon(salonId, organizationId, "Salon", "1 rue du Test", "06140", "Vence", "France",
                "Europe/Paris", NOW);
        when(salonRepository.findAliveById(salonId)).thenReturn(Optional.of(salon));
        when(organizationMembershipQueryService.isAliveOwner(actorId, organizationId)).thenReturn(true);

        assertThat(guard.canViewStaffEmails(actorId, salonId)).isTrue();
    }

    @Test
    void refuse_sans_identifiant() {
        assertThat(guard.canViewStaffEmails(null, salonId)).isFalse();
        assertThat(guard.canViewStaffEmails(actorId, null)).isFalse();
    }
}
