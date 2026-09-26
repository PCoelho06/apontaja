package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class CustomerQueryService {

    private final CustomerProfileRepository customerProfileRepository;
    private final SalonCustomerLinkRepository salonCustomerLinkRepository;

    CustomerQueryService(CustomerProfileRepository customerProfileRepository,
            SalonCustomerLinkRepository salonCustomerLinkRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.salonCustomerLinkRepository = salonCustomerLinkRepository;
    }

    /** Clients du carnet de ce salon, dans l'ordre de rattachement. */
    public List<CustomerSummary> findAliveBySalonId(UUID salonId) {
        List<SalonCustomerLink> links = salonCustomerLinkRepository.findAliveBySalonId(salonId);

        Map<UUID, CustomerProfile> profilesById = customerProfileRepository
                .findAliveByIds(links.stream().map(SalonCustomerLink::getCustomerProfileId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(CustomerProfile::getId, Function.identity()));

        return links.stream().map(link -> profilesById.get(link.getCustomerProfileId()))
                .filter(java.util.Objects::nonNull)
                .map(profile -> CustomerSummary.of(findLinkFor(links, profile.getId()), profile)).toList();
    }

    /**
     * Vide si le client n'existe pas, appartient à un autre salon, ou son profil
     * n'est plus vivant. Utilisé aussi par appointment (tranche 6) pour valider
     * qu'un customerProfileId appartient bien au salon du RDV.
     */
    public Optional<CustomerSummary> findAliveInSalon(UUID salonId, UUID customerProfileId) {
        Optional<SalonCustomerLink> link = salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId,
                customerProfileId);
        Optional<CustomerProfile> profile = customerProfileRepository.findAliveById(customerProfileId);
        if (link.isEmpty() || profile.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(CustomerSummary.of(link.get(), profile.get()));
    }

    private static SalonCustomerLink findLinkFor(List<SalonCustomerLink> links, UUID customerProfileId) {
        return links.stream().filter(l -> l.getCustomerProfileId().equals(customerProfileId)).findFirst()
                .orElseThrow();
    }
}
