package com.apontaja.back.customer.application;

import com.apontaja.back.customer.domain.CustomerMergeLock;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;
import com.apontaja.back.customer.domain.CustomerSearchRepository;
import com.apontaja.back.customer.domain.MarketingConsentMerge;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.customer.domain.SalonCustomerLinkRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Fusion pilotée par l'utilisateur de profils d'un même salon vers un profil
 * survivant. Le survivant garde son identité (email/téléphone comblés si
 * vides), reçoit les notes (concaténées), le consentement le plus récent et
 * tous les RDV. Les profils absorbés sont effacés (même mécanique que
 * l'anonymisation), soft-delete et tracés par {@code merged_into_profile_id}.
 * Irréversible. Un profil réclamé par un compte peut être le survivant (le
 * salon y rattache ses doublons) mais jamais un profil absorbé.
 */
@Service
public class CustomerMergeService {

    static final int MAX_ABSORBED = 10;

    private final CustomerProfileRepository profileRepository;
    private final SalonCustomerLinkRepository linkRepository;
    private final CustomerSearchRepository searchRepository;
    private final CustomerMergeLock mergeLock;
    private final CustomerAppointmentReassignment reassignment;
    private final CustomerVisitLookup visitLookup;
    private final Clock clock;

    CustomerMergeService(CustomerProfileRepository profileRepository, SalonCustomerLinkRepository linkRepository,
            CustomerSearchRepository searchRepository, CustomerMergeLock mergeLock,
            CustomerAppointmentReassignment reassignment, CustomerVisitLookup visitLookup, Clock clock) {
        this.profileRepository = profileRepository;
        this.linkRepository = linkRepository;
        this.searchRepository = searchRepository;
        this.mergeLock = mergeLock;
        this.reassignment = reassignment;
        this.visitLookup = visitLookup;
        this.clock = clock;
    }

    @Transactional
    public CustomerCard merge(MergeCustomersCommand command) {
        UUID salonId = command.salonId();
        UUID survivorId = command.survivorId();
        List<UUID> absorbedIds = command.absorbedIds().stream().distinct().toList();
        validate(survivorId, absorbedIds);
        mergeLock.lockSalon(salonId);

        Loaded survivor = load(salonId, survivorId);
        List<Loaded> absorbed = absorbedIds
                .stream().map(id -> load(salonId, id)).sorted(Comparator
                        .comparing((Loaded l) -> l.profile().getCreatedAt()).thenComparing(l -> l.profile().getId()))
                .toList();
        refuseIfNotMergeable(salonId, survivor.profile(), false);
        absorbed.forEach(a -> refuseIfNotMergeable(salonId, a.profile(), true));

        Instant now = clock.instant();
        List<SalonCustomerLink> links = new ArrayList<>();
        links.add(survivor.link());
        absorbed.forEach(a -> links.add(a.link()));

        String email = survivor.profile().getEmail();
        String phone = survivor.profile().getPhone();
        for (Loaded a : absorbed) {
            email = email == null ? a.profile().getEmail() : email;
            phone = phone == null ? a.profile().getPhone() : phone;
        }
        survivor.profile().updateIdentity(survivor.profile().getFirstName(), survivor.profile().getLastName(), email,
                phone);
        MarketingConsentMerge.Result consent = MarketingConsentMerge.merge(links);
        survivor.link().updateInternalNotes(mergeNotes(links));
        survivor.link().replaceMarketingConsent(consent.consentAt(), consent.revokedAt());
        profileRepository.save(survivor.profile());
        linkRepository.save(survivor.link());

        for (Loaded a : absorbed) {
            a.profile().markMergedInto(survivorId, now);
            a.link().clearPersonalData();
            a.link().softDelete(now);
            profileRepository.save(a.profile());
            linkRepository.save(a.link());
        }
        reassignment.reassign(salonId, absorbedIds, survivorId);

        CustomerVisits visits = visitLookup.findVisits(salonId, List.of(survivorId), now).get(survivorId);
        return CustomerCard.of(survivor.link(), survivor.profile(), visits);
    }

    private static void validate(UUID survivorId, List<UUID> absorbedIds) {
        if (absorbedIds.isEmpty()) {
            throw new InvalidMergeRequestException("Sélectionnez au moins un client à fusionner.");
        }
        if (absorbedIds.contains(survivorId)) {
            throw new InvalidMergeRequestException("Le client conservé ne peut pas être fusionné avec lui-même.");
        }
        if (absorbedIds.size() > MAX_ABSORBED) {
            throw new InvalidMergeRequestException(
                    "Au plus " + MAX_ABSORBED + " clients peuvent être fusionnés à la fois.");
        }
    }

    private Loaded load(UUID salonId, UUID profileId) {
        SalonCustomerLink link = linkRepository.findAliveBySalonIdAndCustomerProfileId(salonId, profileId)
                .orElseThrow(CustomerNotFoundException::new);
        CustomerProfile profile = profileRepository.findAliveById(profileId)
                .orElseThrow(CustomerNotFoundException::new);
        return new Loaded(link, profile);
    }

    private void refuseIfNotMergeable(UUID salonId, CustomerProfile profile, boolean asAbsorbed) {
        if (profile.isAnonymized()) {
            throw new CustomerAnonymizedException();
        }
        if (asAbsorbed && profile.getAccountId() != null) {
            throw new CustomerClaimedException();
        }
        if (searchRepository.existsAliveLinkInOtherSalon(profile.getId(), salonId)) {
            throw new CustomerSharedException();
        }
    }

    /**
     * Notes non vides et distinctes, survivant d'abord, séparées par une ligne
     * vide.
     */
    private static String mergeNotes(List<SalonCustomerLink> links) {
        List<String> notes = new ArrayList<>();
        for (SalonCustomerLink link : links) {
            String note = link.getInternalNotes();
            if (note != null && !note.isBlank() && !notes.contains(note.trim())) {
                notes.add(note.trim());
            }
        }
        return notes.isEmpty() ? null : String.join("\n\n", notes);
    }

    private record Loaded(SalonCustomerLink link, CustomerProfile profile) {
    }
}
