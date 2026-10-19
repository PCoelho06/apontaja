package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.SalonCustomerLink;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface CustomerSearchJpaRepository extends JpaRepository<SalonCustomerLink, UUID> {

    String ALIVE = "l.salonId = :salonId AND l.deletedAt IS NULL AND p.id = l.customerProfileId "
            + "AND p.deletedAt IS NULL AND p.anonymizedAt IS NULL";

    /** '!' sert de caractère d'échappement LIKE (évite toute ambiguïté avec l'antislash). */
    String MATCHES = " AND (LOWER(CONCAT(p.firstName, ' ', p.lastName)) LIKE :pattern ESCAPE '!' "
            + "OR LOWER(CONCAT(p.lastName, ' ', p.firstName)) LIKE :pattern ESCAPE '!' "
            + "OR (p.email IS NOT NULL AND LOWER(p.email) LIKE :pattern ESCAPE '!') "
            + "OR (p.phone IS NOT NULL AND "
            + "REPLACE(REPLACE(REPLACE(p.phone, ' ', ''), '.', ''), '-', '') LIKE :phonePattern ESCAPE '!'))";

    String ORDER = " ORDER BY LOWER(p.lastName), LOWER(p.firstName), l.id";

    String FROM = " FROM SalonCustomerLink l, CustomerProfile p WHERE ";

    @Query(value = "SELECT l" + FROM + ALIVE + ORDER, countQuery = "SELECT COUNT(l)" + FROM + ALIVE)
    Page<SalonCustomerLink> findAliveBySalon(@Param("salonId") UUID salonId, Pageable pageable);

    @Query(value = "SELECT l" + FROM + ALIVE + MATCHES + ORDER,
            countQuery = "SELECT COUNT(l)" + FROM + ALIVE + MATCHES)
    Page<SalonCustomerLink> searchAliveBySalon(@Param("salonId") UUID salonId, @Param("pattern") String pattern,
            @Param("phonePattern") String phonePattern, Pageable pageable);

    boolean existsByCustomerProfileIdAndSalonIdNotAndDeletedAtIsNull(UUID customerProfileId, UUID salonId);
}
