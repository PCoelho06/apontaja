package com.apontaja.back.salon.application;

import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class StaffMembershipManagementService {

    private final StaffMembershipRepository staffMembershipRepository;
    private final Clock clock;

    StaffMembershipManagementService(StaffMembershipRepository staffMembershipRepository, Clock clock) {
        this.staffMembershipRepository = staffMembershipRepository;
        this.clock = clock;
    }

    public List<StaffMemberSummary> listMembers(UUID salonId) {
        return staffMembershipRepository.findAliveBySalonId(salonId).stream()
                .map(m -> new StaffMemberSummary(m.getId(), m.getAccountId(), m.getRole().name(), m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void changeRole(UUID salonId, UUID staffMembershipId, String newRoleName) {
        StaffRole newRole = parseRole(newRoleName);
        StaffMembership membership = requireMembership(salonId, staffMembershipId);

        if (membership.getRole() == StaffRole.OWNER && newRole != StaffRole.OWNER
                && isLastOwner(salonId, staffMembershipId)) {
            throw new LastOwnerProtectionException();
        }

        membership.changeRole(newRole);
        staffMembershipRepository.save(membership);
    }

    @Transactional
    public void removeMember(UUID salonId, UUID staffMembershipId) {
        StaffMembership membership = requireMembership(salonId, staffMembershipId);

        if (membership.getRole() == StaffRole.OWNER && isLastOwner(salonId, staffMembershipId)) {
            throw new LastOwnerProtectionException();
        }

        membership.softDelete(clock.instant());
        staffMembershipRepository.save(membership);
    }

    private StaffMembership requireMembership(UUID salonId, UUID staffMembershipId) {
        StaffMembership membership = staffMembershipRepository.findAliveById(staffMembershipId)
                .orElseThrow(StaffMembershipNotFoundException::new);
        if (!membership.getSalonId().equals(salonId)) {
            throw new StaffMembershipNotFoundException();
        }
        return membership;
    }

    /**
     * "Dernier OWNER" au sens StaffMembership uniquement — un
     * OrganizationMembership OWNER sans StaffMembership direct n'est pas compté ici
     * (protection scopée au staff explicite du salon, cohérent avec la portée
     * validée pour cette tranche).
     */
    private boolean isLastOwner(UUID salonId, UUID excludingMembershipId) {
        return staffMembershipRepository.findAliveBySalonId(salonId).stream()
                .filter(m -> m.getRole() == StaffRole.OWNER).filter(m -> !m.getId().equals(excludingMembershipId))
                .findAny().isEmpty();
    }

    private static StaffRole parseRole(String role) {
        try {
            return StaffRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidStaffRoleException(role);
        }
    }
}
