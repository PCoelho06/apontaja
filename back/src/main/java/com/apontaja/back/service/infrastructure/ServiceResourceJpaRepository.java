package com.apontaja.back.service.infrastructure;

import com.apontaja.back.service.domain.ServiceResource;
import com.apontaja.back.service.domain.ServiceResourceId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ServiceResourceJpaRepository extends JpaRepository<ServiceResource, ServiceResourceId> {

    @Query("SELECT l FROM ServiceResource l WHERE l.id.serviceId = :serviceId")
    List<ServiceResource> findByServiceId(@Param("serviceId") UUID serviceId);
}
