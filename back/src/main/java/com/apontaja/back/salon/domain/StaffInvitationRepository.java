package com.apontaja.back.salon.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffInvitationRepository {

    StaffInvitation save(StaffInvitation invitation);

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    /**
     * "Pending" = ni acceptée ni révoquée ; l'expiration se vérifie en mémoire
     * (StaffInvitation.isUsable).
     */
    List<StaffInvitation> findPendingBySalonId(UUID salonId);

    boolean existsPendingBySalonIdAndEmail(UUID salonId, String email);
}
