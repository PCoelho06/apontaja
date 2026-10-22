package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.account.application.AccountSummary;
import com.apontaja.back.customer.domain.ClaimedProfileRepository;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Profil d'un compte dans le carnet d'un salon, pour la réservation en ligne. Ordre : (1) le compte a déjà un profil
 * réclamé dans ce salon : on l'utilise ; sinon, le partage nom/coordonnées doit être consenti, puis (2) on tente
 * de rattacher les profils portant l'email du compte ({@link CustomerClaimService}), puis (3) on crée un profil
 * {@code SELF}. À appeler dans la transaction de la réservation, sous le verrou du salon : deux premières
 * réservations simultanées du même compte ne créent donc pas deux profils. Le téléphone n'est utilisé qu'à la
 * création d'un profil ; un profil existant n'est jamais modifié.
 */
@Service
public class CustomerSelfService {

    private final AccountQueryService accountQueryService;
    private final ClaimedProfileRepository claimedProfileRepository;
    private final CustomerClaimService claimService;
    private final CustomerProfileRepository profileRepository;
    private final SalonCustomerLinkRepository linkRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    CustomerSelfService(AccountQueryService accountQueryService, ClaimedProfileRepository claimedProfileRepository,
            CustomerClaimService claimService, CustomerProfileRepository profileRepository,
            SalonCustomerLinkRepository linkRepository, IdGenerator idGenerator, Clock clock) {
        this.accountQueryService = accountQueryService;
        this.claimedProfileRepository = claimedProfileRepository;
        this.claimService = claimService;
        this.profileRepository = profileRepository;
        this.linkRepository = linkRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * @return l'identifiant du profil du compte dans ce salon
     * @throws EmailNotVerifiedException compte introuvable ou email non vérifié
     * @throws SharingConsentRequiredException aucun profil réclamé et partage non consenti
     */
    @Transactional
    public UUID resolveProfileForBooking(UUID accountId, UUID salonId, String phone, boolean shareContactConsent) {
        AccountSummary account = accountQueryService.findAliveById(accountId).filter(AccountSummary::emailVerified)
                .orElseThrow(EmailNotVerifiedException::new);

        Optional<UUID> existing = claimedProfileRepository.findClaimedProfileId(accountId, salonId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!shareContactConsent) {
            throw new SharingConsentRequiredException();
        }

        claimService.claimMatchingProfiles(accountId, account.email());
        return claimedProfileRepository.findClaimedProfileId(accountId, salonId)
                .orElseGet(() -> createSelfProfile(account, salonId, phone));
    }

    private UUID createSelfProfile(AccountSummary account, UUID salonId, String phone) {
        Instant now = clock.instant();
        CustomerProfile profile = new CustomerProfile(idGenerator.generate(), account.firstName(),
                account.lastName(), account.email(), normalize(phone), now);
        profile.claimBy(account.accountId());
        profile = profileRepository.save(profile);

        linkRepository.save(new SalonCustomerLink(idGenerator.generate(), salonId, profile.getId(),
                CustomerLinkSource.SELF, null, now));
        return profile.getId();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
