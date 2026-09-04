package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.StaffInvitation;
import com.apontaja.back.salon.domain.StaffInvitationRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class StaffInvitationRepositoryAdapter implements StaffInvitationRepository {

    private final StaffInvitationJpaRepository jpaRepository;

    StaffInvitationRepositoryAdapter(StaffInvitationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StaffInvitation save(StaffInvitation invitation) {
        return jpaRepository.save(invitation);
    }

    @Override
    public Optional<StaffInvitation> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash);
    }

    @Override
    public List<StaffInvitation> findPendingBySalonId(UUID salonId) {
        return jpaRepository.findPendingBySalonId(salonId);
    }

    @Override
    public boolean existsPendingBySalonIdAndEmail(UUID salonId, String email) {
        return jpaRepository.existsPendingBySalonIdAndEmail(salonId, email);
    }
}
