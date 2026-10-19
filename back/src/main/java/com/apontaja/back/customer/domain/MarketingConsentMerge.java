package com.apontaja.back.customer.domain;

import java.time.Instant;
import java.util.Collection;

/**
 * Consentement marketing d'un client fusionné : l'événement le plus récent (octroi ou retrait) parmi tous les
 * liens l'emporte ; à date égale, le retrait l'emporte (règle la plus prudente).
 */
public final class MarketingConsentMerge {

    private MarketingConsentMerge() {
    }

    /** Dates à poser sur le lien survivant ({@code null}/{@code null} = aucun consentement). */
    public record Result(Instant consentAt, Instant revokedAt) {
    }

    public static Result merge(Collection<SalonCustomerLink> links) {
        Instant bestAt = null;
        boolean bestGranted = false;
        Instant bestConsentAt = null;
        Instant bestRevokedAt = null;
        for (SalonCustomerLink link : links) {
            Instant consentAt = link.getMarketingConsentAt();
            if (consentAt == null) {
                continue;
            }
            Instant revokedAt = link.getMarketingConsentRevokedAt();
            boolean granted = revokedAt == null;
            Instant eventAt = granted ? consentAt : revokedAt;
            boolean wins = bestAt == null || eventAt.isAfter(bestAt)
                    || (eventAt.equals(bestAt) && bestGranted && !granted);
            if (wins) {
                bestAt = eventAt;
                bestGranted = granted;
                bestConsentAt = consentAt;
                bestRevokedAt = revokedAt;
            }
        }
        return new Result(bestConsentAt, bestRevokedAt);
    }
}
