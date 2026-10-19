package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture unitaire pour les autres domaines (appointment). La liste du carnet est dans
 * {@link CustomerBookQueryService}.
 */
@Service
public class CustomerQueryService {

    private final CustomerProfileRepository customerProfileRepository;
    private final SalonCustomerLinkRepository salonCustomerLinkRepository;

    CustomerQueryService(CustomerProfileRepository customerProfileRepository,
            SalonCustomerLinkRepository salonCustomerLinkRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.salonCustomerLinkRepository = salonCustomerLinkRepository;
    }

    /**
     * Vide si le client n'existe pas, appartient à un autre salon, son profil n'est plus vivant ou il a été
     * anonymisé (on ne réserve plus pour un client anonymisé). Utilisé par appointment pour valider qu'un
     * customerProfileId appartient bien au salon du RDV.
     */
    public Optional<CustomerSummary> findAliveInSalon(UUID salonId, UUID customerProfileId) {
        Optional<SalonCustomerLink> link = salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId,
                customerProfileId);
        Optional<CustomerProfile> profile = customerProfileRepository.findAliveById(customerProfileId)
                .filter(found -> !found.isAnonymized());
        if (link.isEmpty() || profile.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(CustomerSummary.of(link.get(), profile.get()));
    }
}
