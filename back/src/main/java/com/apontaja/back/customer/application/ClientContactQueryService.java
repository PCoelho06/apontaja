package com.apontaja.back.customer.application;

import com.apontaja.back.account.application.AccountQueryService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Coordonnées d'un compte client, pour les emails envoyés par le domaine {@code appointment} (qui ne lit jamais
 * {@code account}). Appelé après la validation d'une transaction (écouteur AFTER_COMMIT), d'où la transaction
 * indépendante en lecture seule : celle de l'appelant est déjà terminée.
 */
@Service
public class ClientContactQueryService {

    private final AccountQueryService accountQueryService;

    ClientContactQueryService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    /** Vide si le compte n'existe plus (supprimé). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ClientContact> find(UUID accountId) {
        return accountQueryService.findAliveById(accountId)
                .map(account -> new ClientContact(account.email(), account.firstName()));
    }
}
