package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.StaffMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffMembershipJpaRepository extends JpaRepository<StaffMembership, UUID> {

    Optional<StaffMembership> findByAccountIdAndSalonIdAndDeletedAtIsNull(UUID accountId, UUID salonId);

    List<StaffMembership> findBySalonIdAndDeletedAtIsNull(UUID salonId);

    List<StaffMembership> findByAccountIdAndDeletedAtIsNull(UUID accountId);
}
