package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.DuplicateCandidate;
import com.apontaja.back.customer.domain.SalonCustomerLink;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface CustomerDuplicateJpaRepository extends JpaRepository<SalonCustomerLink, UUID> {

    @Query("SELECT new com.apontaja.back.customer.domain.DuplicateCandidate(p.id, p.firstName, p.lastName, "
            + "p.email, p.phone, p.accountId, p.createdAt) FROM SalonCustomerLink l, CustomerProfile p "
            + "WHERE l.salonId = :salonId AND l.deletedAt IS NULL AND p.id = l.customerProfileId "
            + "AND p.deletedAt IS NULL AND p.anonymizedAt IS NULL")
    List<DuplicateCandidate> findCandidates(@Param("salonId") UUID salonId);

    @Query("SELECT DISTINCT l.customerProfileId FROM SalonCustomerLink l WHERE l.customerProfileId IN :ids "
            + "AND l.salonId <> :salonId AND l.deletedAt IS NULL")
    List<UUID> findProfileIdsLinkedElsewhere(@Param("salonId") UUID salonId,
            @Param("ids") Collection<UUID> ids);
}
