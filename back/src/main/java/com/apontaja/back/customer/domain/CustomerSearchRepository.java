package com.apontaja.back.customer.domain;

import java.util.List;
import java.util.UUID;

/**
 * Recherche paginée dans le carnet d'un salon. Ne renvoie que les liens vivants dont le profil est vivant et non
 * anonymisé, triés par nom puis prénom.
 */
public interface CustomerSearchRepository {

    /**
     * @param query texte libre (nom, email, téléphone), {@code null} ou vide = pas de filtre
     * @param page  numéro de page, à partir de 0
     */
    LinkPage searchAliveLinks(UUID salonId, String query, int page, int size);

    /** {@code true} si le profil a un lien vivant dans un autre salon que {@code salonId}. */
    boolean existsAliveLinkInOtherSalon(UUID customerProfileId, UUID salonId);

    record LinkPage(List<SalonCustomerLink> links, long totalElements) {
    }
}
