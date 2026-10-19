package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.CustomerSearchRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Modification d'un client, consentement marketing (par salon) et anonymisation RGPD. */
@Service
public class CustomerEditionService {

    private final CustomerProfileRepository customerProfileRepository;
    private final SalonCustomerLinkRepository salonCustomerLinkRepository;
    private final CustomerSearchRepository searchRepository;
    private final CustomerVisitLookup visitLookup;
    private final Clock clock;

    CustomerEditionService(CustomerProfileRepository customerProfileRepository,
            SalonCustomerLinkRepository salonCustomerLinkRepository, CustomerSearchRepository searchRepository,
            CustomerVisitLookup visitLookup, Clock clock) {
        this.customerProfileRepository = customerProfileRepository;
        this.salonCustomerLinkRepository = salonCustomerLinkRepository;
        this.searchRepository = searchRepository;
        this.visitLookup = visitLookup;
        this.clock = clock;
    }

    @Transactional
    public CustomerCard update(UpdateCustomerCommand command) {
        SalonCustomerLink link = findLink(command.salonId(), command.customerProfileId());
        CustomerProfile profile = findProfile(command.customerProfileId());
        refuseIfAnonymized(profile);

        String email = normalize(command.email());
        String phone = normalize(command.phone());
        if (email == null && phone == null) {
            throw new ContactRequiredException();
        }

        profile.updateIdentity(command.firstName().trim(), command.lastName().trim(), email, phone);
        link.updateInternalNotes(normalize(command.internalNotes()));
        customerProfileRepository.save(profile);
        salonCustomerLinkRepository.save(link);
        return card(link, profile);
    }

    /**
     * Enregistre que le client a accepté (ou retiré) de recevoir les communications marketing de ce salon. Accepter
     * deux fois conserve la date du premier consentement ; retirer sans consentement ne fait rien.
     */
    @Transactional
    public CustomerCard setMarketingConsent(UUID salonId, UUID customerProfileId, boolean consented) {
        SalonCustomerLink link = findLink(salonId, customerProfileId);
        CustomerProfile profile = findProfile(customerProfileId);
        refuseIfAnonymized(profile);

        if (consented && !link.hasMarketingConsent()) {
            link.grantMarketingConsent(clock.instant());
            salonCustomerLinkRepository.save(link);
        } else if (!consented && link.hasMarketingConsent()) {
            link.revokeMarketingConsent(clock.instant());
            salonCustomerLinkRepository.save(link);
        }
        return card(link, profile);
    }

    /**
     * Droit à l'effacement : le profil perd nom, email et téléphone, le lien perd notes et consentement. Les RDV
     * sont conservés. Idempotent. Refusé si le profil n'est pas propre à ce salon ou si un RDV est à venir.
     */
    @Transactional
    public void anonymize(UUID salonId, UUID customerProfileId) {
        SalonCustomerLink link = findLink(salonId, customerProfileId);
        CustomerProfile profile = findProfile(customerProfileId);
        if (profile.isAnonymized()) {
            return;
        }
        if (profile.getAccountId() != null
                || searchRepository.existsAliveLinkInOtherSalon(customerProfileId, salonId)) {
            throw new CustomerSharedException();
        }
        Instant now = clock.instant();
        CustomerVisits visits = visitLookup.findVisits(salonId, List.of(customerProfileId), now)
                .get(customerProfileId);
        if (visits != null && visits.nextVisitAt() != null) {
            throw new CustomerHasUpcomingAppointmentsException();
        }

        profile.anonymize(now);
        link.clearPersonalData();
        customerProfileRepository.save(profile);
        salonCustomerLinkRepository.save(link);
    }

    private CustomerCard card(SalonCustomerLink link, CustomerProfile profile) {
        CustomerVisits visits = visitLookup.findVisits(link.getSalonId(), List.of(profile.getId()), clock.instant())
                .get(profile.getId());
        return CustomerCard.of(link, profile, visits);
    }

    private SalonCustomerLink findLink(UUID salonId, UUID customerProfileId) {
        return salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, customerProfileId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    private CustomerProfile findProfile(UUID customerProfileId) {
        return customerProfileRepository.findAliveById(customerProfileId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    private static void refuseIfAnonymized(CustomerProfile profile) {
        if (profile.isAnonymized()) {
            throw new CustomerAnonymizedException();
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
