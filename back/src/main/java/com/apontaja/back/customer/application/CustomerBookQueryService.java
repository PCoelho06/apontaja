package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.CustomerSearchRepository;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lecture du carnet client d'un salon : liste paginée avec recherche, fiche, historique des RDV. */
@Service
public class CustomerBookQueryService {

    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_PAGE_SIZE = 100;

    private final CustomerSearchRepository searchRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final SalonCustomerLinkRepository salonCustomerLinkRepository;
    private final CustomerVisitLookup visitLookup;
    private final CustomerAppointmentHistory appointmentHistory;
    private final Clock clock;

    CustomerBookQueryService(CustomerSearchRepository searchRepository,
            CustomerProfileRepository customerProfileRepository,
            SalonCustomerLinkRepository salonCustomerLinkRepository, CustomerVisitLookup visitLookup,
            CustomerAppointmentHistory appointmentHistory, Clock clock) {
        this.searchRepository = searchRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.salonCustomerLinkRepository = salonCustomerLinkRepository;
        this.visitLookup = visitLookup;
        this.appointmentHistory = appointmentHistory;
        this.clock = clock;
    }

    /** Clients vivants et non anonymisés, par nom puis prénom. Page et taille sont bornées. */
    public CustomerCardPage search(UUID salonId, String query, int page, int size) {
        int safePage = sanitizePage(page);
        int safeSize = sanitizeSize(size);
        CustomerSearchRepository.LinkPage result = searchRepository.searchAliveLinks(salonId,
                normalizeQuery(query), safePage, safeSize);
        List<SalonCustomerLink> links = result.links();
        if (links.isEmpty()) {
            return new CustomerCardPage(List.of(), safePage, safeSize, result.totalElements());
        }

        List<UUID> profileIds = links.stream().map(SalonCustomerLink::getCustomerProfileId).toList();
        Map<UUID, CustomerProfile> profiles = customerProfileRepository.findAliveByIds(profileIds).stream()
                .collect(Collectors.toMap(CustomerProfile::getId, Function.identity()));
        Map<UUID, CustomerVisits> visits = visitLookup.findVisits(salonId, profileIds, clock.instant());

        List<CustomerCard> cards = links.stream().filter(link -> profiles.containsKey(link.getCustomerProfileId()))
                .map(link -> CustomerCard.of(link, profiles.get(link.getCustomerProfileId()),
                        visits.get(link.getCustomerProfileId())))
                .toList();
        return new CustomerCardPage(cards, safePage, safeSize, result.totalElements());
    }

    /** Fiche d'un client, y compris anonymisé (l'agenda continue d'afficher ses anciens RDV). */
    public Optional<CustomerCard> find(UUID salonId, UUID customerProfileId) {
        Optional<SalonCustomerLink> link = salonCustomerLinkRepository
                .findAliveBySalonIdAndCustomerProfileId(salonId, customerProfileId);
        if (link.isEmpty()) {
            return Optional.empty();
        }
        return customerProfileRepository.findAliveById(customerProfileId).map(profile -> {
            Map<UUID, CustomerVisits> visits = visitLookup.findVisits(salonId, List.of(customerProfileId),
                    clock.instant());
            return CustomerCard.of(link.get(), profile, visits.get(customerProfileId));
        });
    }

    /** @throws CustomerNotFoundException si le client n'existe pas dans ce salon */
    public CustomerHistoryPage history(UUID salonId, UUID customerProfileId, int page, int size) {
        if (salonCustomerLinkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, customerProfileId)
                .isEmpty()) {
            throw new CustomerNotFoundException();
        }
        return appointmentHistory.list(salonId, customerProfileId, sanitizePage(page), sanitizeSize(size));
    }

    private static int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    private static int sanitizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > MAX_QUERY_LENGTH ? normalized.substring(0, MAX_QUERY_LENGTH) : normalized;
    }
}
