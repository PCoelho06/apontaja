package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.salon.domain.StaffInvitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffInvitationJpaRepository extends JpaRepository<StaffInvitation, UUID> {

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    @Query("SELECT i FROM StaffInvitation i WHERE i.salonId = :salonId "
            + "AND i.acceptedAt IS NULL AND i.revokedAt IS NULL")
    List<StaffInvitation> findPendingBySalonId(@Param("salonId") UUID salonId);

    @Query("SELECT COUNT(i) > 0 FROM StaffInvitation i WHERE i.salonId = :salonId AND i.email = :email "
            + "AND i.acceptedAt IS NULL AND i.revokedAt IS NULL")
    boolean existsPendingBySalonIdAndEmail(@Param("salonId") UUID salonId, @Param("email") String email);
}
