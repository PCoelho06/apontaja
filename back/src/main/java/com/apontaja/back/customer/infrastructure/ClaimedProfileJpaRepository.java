package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.SalonCustomerLink;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ClaimedProfileJpaRepository extends Repository<SalonCustomerLink, UUID> {

    @Query("SELECT p.id FROM SalonCustomerLink l, CustomerProfile p WHERE p.id = l.customerProfileId "
            + "AND l.salonId = :salonId AND l.deletedAt IS NULL AND p.deletedAt IS NULL "
            + "AND p.anonymizedAt IS NULL AND p.accountId = :accountId ORDER BY p.createdAt, p.id")
    List<UUID> findProfileIds(@Param("accountId") UUID accountId, @Param("salonId") UUID salonId);
}
