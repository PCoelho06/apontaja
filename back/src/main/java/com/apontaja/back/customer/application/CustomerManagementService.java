package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Création manuelle d'un client par un salon. Toujours un nouveau
 * CustomerProfile : aucun matching ni fusion en Phase 3 (voir §2 du contexte,
 * report explicite Phase 4).
 */
@Service
public class CustomerManagementService {

    private final CustomerProfileRepository customerProfileRepository;
    private final SalonCustomerLinkRepository salonCustomerLinkRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    CustomerManagementService(CustomerProfileRepository customerProfileRepository,
            SalonCustomerLinkRepository salonCustomerLinkRepository, IdGenerator idGenerator, Clock clock) {
        this.customerProfileRepository = customerProfileRepository;
        this.salonCustomerLinkRepository = salonCustomerLinkRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public CustomerSummary create(CreateCustomerCommand command) {
        String email = normalize(command.email());
        String phone = normalize(command.phone());
        if (email == null && phone == null) {
            throw new ContactRequiredException();
        }

        Instant now = clock.instant();
        CustomerProfile profile = new CustomerProfile(idGenerator.generate(), command.firstName().trim(),
                command.lastName().trim(), email, phone, now);
        profile = customerProfileRepository.save(profile);

        SalonCustomerLink link = new SalonCustomerLink(idGenerator.generate(), command.salonId(), profile.getId(),
                CustomerLinkSource.MANUAL, normalize(command.internalNotes()), now);
        link = salonCustomerLinkRepository.save(link);

        return CustomerSummary.of(link, profile);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
