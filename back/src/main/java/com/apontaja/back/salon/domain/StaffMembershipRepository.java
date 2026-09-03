package com.apontaja.back.salon.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffMembershipRepository {

    StaffMembership save(StaffMembership membership);

    Optional<StaffMembership> findAliveByAccountIdAndSalonId(UUID accountId, UUID salonId);

    List<StaffMembership> findAliveBySalonId(UUID salonId);

    List<StaffMembership> findAliveByAccountId(UUID accountId);
}
