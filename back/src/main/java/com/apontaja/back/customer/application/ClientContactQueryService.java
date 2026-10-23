package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountQueryService;
import com.apontaja.back.customer.domain.CustomerProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Coordonnées d'un client, pour les emails envoyés par le domaine {@code appointment} (qui ne lit jamais
 * {@code account}). Appelé après la validation d'une transaction (écouteur AFTER_COMMIT), d'où la transaction
 * indépendante en lecture seule : celle de l'appelant est déjà terminée.
 */
@Service
public class ClientContactQueryService {

    private final AccountQueryService accountQueryService;
    private final CustomerProfileRepository customerProfileRepository;

    ClientContactQueryService(AccountQueryService accountQueryService,
            CustomerProfileRepository customerProfileRepository) {
        this.accountQueryService = accountQueryService;
        this.customerProfileRepository = customerProfileRepository;
    }

    /** Vide si le compte n'existe plus (supprimé). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ClientContact> find(UUID accountId) {
        return findAccountContact(accountId);
    }

    /**
     * Destinataire d'un email déclenché par le staff : le compte si le profil en est rattaché, sinon l'email que le
     * client a donné au salon. Vide si le profil n'existe plus, est anonymisé, ou n'a aucun email exploitable.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ClientContact> findForProfile(UUID customerProfileId) {
        return customerProfileRepository.findAliveById(customerProfileId).filter(profile -> !profile.isAnonymized())
                .flatMap(profile -> {
                    if (profile.getAccountId() != null) {
                        return findAccountContact(profile.getAccountId());
                    }
                    String email = profile.getEmail();
                    if (email == null || email.isBlank()) {
                        return Optional.empty();
                    }
                    return Optional.of(new ClientContact(email.trim(), profile.getFirstName()));
                });
    }

    private Optional<ClientContact> findAccountContact(UUID accountId) {
        return accountQueryService.findAliveById(accountId)
                .map(account -> new ClientContact(account.email(), account.firstName()));
    }
}
