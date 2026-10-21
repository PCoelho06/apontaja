package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountEmailVerifiedListener;
import com.apontaja.back.customer.domain.ClaimCandidate;
import com.apontaja.back.customer.domain.ClaimPlanner;
import com.apontaja.back.customer.domain.CustomerClaimLock;
import com.apontaja.back.customer.domain.CustomerClaimRepository;
import com.apontaja.back.customer.domain.CustomerMergeLock;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rattache un compte aux profils des carnets de salons qui portent son email. Le rattachement ne se fait
 * <strong>qu'après vérification de l'email</strong> (prouve la maîtrise de la boîte), jamais par téléphone. Chaque
 * salon garde son propre profil : seul {@code account_id} change (identité, notes internes et consentement marketing
 * du salon restent intacts). Au plus un profil réclamé par (compte, salon). Idempotent.
 */
@Service
public class CustomerClaimService implements AccountEmailVerifiedListener {

    private final CustomerClaimRepository claimRepository;
    private final CustomerClaimLock claimLock;
    private final CustomerMergeLock mergeLock;
    private final CustomerProfileRepository profileRepository;

    CustomerClaimService(CustomerClaimRepository claimRepository, CustomerClaimLock claimLock,
            CustomerMergeLock mergeLock, CustomerProfileRepository profileRepository) {
        this.claimRepository = claimRepository;
        this.claimLock = claimLock;
        this.mergeLock = mergeLock;
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional
    public void onEmailVerified(UUID accountId, String email) {
        claimMatchingProfiles(accountId, email);
    }

    /**
     * À n'appeler que pour un compte dont l'email est vérifié.
     *
     * @return le nombre de profils rattachés
     */
    @Transactional
    public int claimMatchingProfiles(UUID accountId, String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        claimLock.lockAccount(accountId);

        List<ClaimCandidate> selected = ClaimPlanner.plan(claimRepository.findClaimCandidates(email.trim()),
                claimRepository.findSalonIdsHoldingClaimedProfile(accountId));
        if (selected.isEmpty()) {
            return 0;
        }

        // Même verrou que la fusion : un profil ne peut pas être réclamé pendant qu'on l'absorbe. Ordre stable.
        selected.stream().map(ClaimCandidate::salonId).distinct().sorted().forEach(mergeLock::lockSalon);

        // Relecture après verrouillage : l'état a pu changer (fusion, anonymisation, autre rattachement).
        Map<UUID, CustomerProfile> profiles = profileRepository
                .findAliveByIds(selected.stream().map(ClaimCandidate::profileId).toList()).stream()
                .collect(Collectors.toMap(CustomerProfile::getId, Function.identity()));

        int claimed = 0;
        for (ClaimCandidate candidate : selected) {
            CustomerProfile profile = profiles.get(candidate.profileId());
            if (profile == null || profile.isAnonymized() || profile.getAccountId() != null) {
                continue;
            }
            profile.claimBy(accountId);
            profileRepository.save(profile);
            claimed++;
        }
        return claimed;
    }
}
