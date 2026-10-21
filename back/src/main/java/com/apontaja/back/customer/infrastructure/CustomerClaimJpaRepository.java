package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.ClaimCandidate;
import com.apontaja.back.customer.domain.SalonCustomerLink;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface CustomerClaimJpaRepository extends Repository<SalonCustomerLink, UUID> {

    // LOWER des deux côtés : un paramètre varchar contre une colonne citext peut être comparé en texte (sensible
    // à la casse) ; la casse de l'email saisi en salon ne doit pas empêcher le rattachement.
    @Query("SELECT new com.apontaja.back.customer.domain.ClaimCandidate(p.id, l.salonId, p.createdAt) "
            + "FROM SalonCustomerLink l, CustomerProfile p WHERE p.id = l.customerProfileId "
            + "AND l.deletedAt IS NULL AND p.deletedAt IS NULL AND p.anonymizedAt IS NULL "
            + "AND p.accountId IS NULL AND LOWER(p.email) = LOWER(:email) ORDER BY p.createdAt, p.id")
    List<ClaimCandidate> findCandidatesByEmail(@Param("email") String email);

    @Query("SELECT l.salonId FROM SalonCustomerLink l, CustomerProfile p WHERE p.id = l.customerProfileId "
            + "AND l.deletedAt IS NULL AND p.deletedAt IS NULL AND p.accountId = :accountId")
    List<UUID> findSalonIdsWithClaimedProfile(@Param("accountId") UUID accountId);
}
