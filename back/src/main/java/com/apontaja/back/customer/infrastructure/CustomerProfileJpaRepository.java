package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerProfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByIdAndDeletedAtIsNull(UUID id);

    List<CustomerProfile> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
