package com.apontaja.back.resource.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClosureRepository {

    /** Flush immédiat. */
    Closure save(Closure closure);

    Optional<Closure> findById(UUID id);

    /**
     * Fermetures du salon lui-même recouvrant [from, to) : end &gt; from ET start
     * &lt; to (bornes adjacentes = pas de recouvrement). Triées par début.
     */
    List<Closure> findOverlappingBySalonId(UUID salonId, Instant from, Instant to);

    List<Closure> findOverlappingByResourceId(UUID resourceId, Instant from, Instant to);

    void delete(Closure closure);
}
