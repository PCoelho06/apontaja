package com.apontaja.back.salon.application;

import com.apontaja.back.account.application.AccountQueryService;
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
class StaffMemberQueryServiceTest {

    @Mock
    private StaffMembershipManagementService managementService;
    @Mock
    private StaffMembershipRepository staffMembershipRepository;
    @Mock
    private AccountQueryService accountQueryService;

    private StaffMemberQueryService service;
    private final UUID salonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StaffMemberQueryService(managementService, staffMembershipRepository, accountQueryService);
    }

    private StaffMembership membershipIn(UUID salon) {
        return new StaffMembership(UUID.randomUUID(), UUID.randomUUID(), salon, StaffRole.EMPLOYEE,
                Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    void existsForAnAliveMemberOfTheSameSalon() {
        StaffMembership membership = membershipIn(salonId);
        when(staffMembershipRepository.findAliveById(membership.getId())).thenReturn(Optional.of(membership));

        assertThat(service.existsAliveInSalon(salonId, membership.getId())).isTrue();
    }

    @Test
    void doesNotExistForAMemberOfAnotherSalon() {
        StaffMembership foreign = membershipIn(UUID.randomUUID());
        when(staffMembershipRepository.findAliveById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThat(service.existsAliveInSalon(salonId, foreign.getId())).isFalse();
    }

    @Test
    void doesNotExistForAnUnknownOrRemovedMember() {
        UUID unknown = UUID.randomUUID();
        when(staffMembershipRepository.findAliveById(unknown)).thenReturn(Optional.empty());

        assertThat(service.existsAliveInSalon(salonId, unknown)).isFalse();
    }
}
