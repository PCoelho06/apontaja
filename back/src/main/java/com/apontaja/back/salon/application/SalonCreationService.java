package com.apontaja.back.salon.application;

import com.apontaja.back.organization.application.OrganizationProvisioningService;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonRepository;
import com.apontaja.back.salon.domain.StaffMembership;
import com.apontaja.back.salon.domain.StaffMembershipRepository;
import com.apontaja.back.salon.domain.StaffRole;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class SalonCreationService {

    private final OrganizationProvisioningService organizationProvisioningService;
    private final SalonRepository salonRepository;
    private final StaffMembershipRepository staffMembershipRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    SalonCreationService(OrganizationProvisioningService organizationProvisioningService,
            SalonRepository salonRepository, StaffMembershipRepository staffMembershipRepository,
            IdGenerator idGenerator, Clock clock) {
        this.organizationProvisioningService = organizationProvisioningService;
        this.salonRepository = salonRepository;
        this.staffMembershipRepository = staffMembershipRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreateSalonResult createSalon(CreateSalonCommand command) {
        UUID organizationId = organizationProvisioningService.ensureOrganizationForAccount(command.accountId());

        Instant now = clock.instant();
        Salon salon = new Salon(idGenerator.generate(), organizationId, command.name(), command.address(),
                command.postalCode(), command.city(), command.country(), command.timezone(), now);
        if (command.phone() != null) {
            salon.setPhone(command.phone());
        }
        salonRepository.save(salon);

        staffMembershipRepository.save(
                new StaffMembership(idGenerator.generate(), command.accountId(), salon.getId(), StaffRole.OWNER, now));

        return new CreateSalonResult(salon.getId(), organizationId);
    }
}
