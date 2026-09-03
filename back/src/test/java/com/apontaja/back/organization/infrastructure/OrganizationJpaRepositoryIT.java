package com.apontaja.back.organization.infrastructure;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class OrganizationJpaRepositoryIT {

    @Autowired
    private OrganizationJpaRepository organizationJpaRepository;

    @Test
    void persiste_et_retrouve_une_organisation_vivante() {
        Instant now = Instant.now();
        Organization organization = organizationJpaRepository
                .save(new Organization(UUID.randomUUID(), "Salon Test", now));

        Optional<Organization> found = organizationJpaRepository.findByIdAndDeletedAtIsNull(organization.getId());

        assertThat(found).contains(organization);
    }

    @Test
    void ignore_une_organisation_soft_deleted() {
        Instant now = Instant.now();
        Organization organization = new Organization(UUID.randomUUID(), "Salon Test", now);
        organization.softDelete(now);
        organizationJpaRepository.save(organization);
        organizationJpaRepository.flush();

        assertThat(organizationJpaRepository.findByIdAndDeletedAtIsNull(organization.getId())).isEmpty();
    }
}
