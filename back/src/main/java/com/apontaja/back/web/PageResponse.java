package com.apontaja.back.web;

import java.util.List;

/**
 * Enveloppe générique pour toute réponse paginée — évite de dépendre
 * directement de
 * {@code org.springframework.data.domain.Page}/{@code PagedModel} dans les DTO
 * de réponse HTTP (dont le support de sérialisation Jackson 2 vs Jackson 3, ce
 * dernier étant le défaut depuis Spring Boot 4, est encore en évolution côté
 * Spring Data — mieux vaut un contrat HTTP explicite et stable, cohérent avec
 * le reste du projet où aucun type framework ne fuite dans l'API).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
