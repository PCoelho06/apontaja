package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class StaffMembershipRepositoryAdapter implements StaffMembershipRepository {

    private final StaffMembershipJpaRepository jpaRepository;

    StaffMembershipRepositoryAdapter(StaffMembershipJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StaffMembership save(StaffMembership membership) {
        return jpaRepository.save(membership);
    }

    @Override
    public Optional<StaffMembership> findAliveByAccountIdAndSalonId(UUID accountId, UUID salonId) {
        return jpaRepository.findByAccountIdAndSalonIdAndDeletedAtIsNull(accountId, salonId);
    }

    @Override
    public List<StaffMembership> findAliveBySalonId(UUID salonId) {
        return jpaRepository.findBySalonIdAndDeletedAtIsNull(salonId);
    }

    @Override
    public List<StaffMembership> findAliveByAccountId(UUID accountId) {
        return jpaRepository.findByAccountIdAndDeletedAtIsNull(accountId);
    }

    @Override
    public Optional<StaffMembership> findAliveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }
}
