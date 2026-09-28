package com.apontaja.back.resource.application;

import java.util.Comparator;
import java.util.List;

/**
 * Fermeture refusée : elle recouvrirait des RDV actifs (SCHEDULED/CONFIRMED),
 * que le staff doit d'abord annuler ou déplacer. {@link #count()} est le total ;
 * {@link #appointments()} n'en liste que les {@value #MAX_LISTED} premiers,
 * triés par début, pour borner la taille de la réponse.
 */
public class ClosureBlockedByAppointmentsException extends RuntimeException {

    public static final int MAX_LISTED = 20;

    private final int count;
    private final List<BlockingAppointment> appointments;

    public ClosureBlockedByAppointmentsException(List<BlockingAppointment> blocking) {
        super("La fermeture recouvre " + blocking.size()
                + " rendez-vous actif(s) : annulez-les ou déplacez-les d'abord");
        this.count = blocking.size();
        this.appointments = blocking.stream().sorted(Comparator.comparing(BlockingAppointment::startAt))
                .limit(MAX_LISTED).toList();
    }

    public int count() {
        return count;
    }

    public List<BlockingAppointment> appointments() {
        return appointments;
    }
}
