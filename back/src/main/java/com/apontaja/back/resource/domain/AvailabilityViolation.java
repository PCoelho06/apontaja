package com.apontaja.back.resource.domain;

/** Raisons pour lesquelles un créneau n'est pas réservable sur une ressource. */
public enum AvailabilityViolation {
    /** Le créneau ne tient pas (début ET fin) dans une plage d'ouverture du salon. */
    OUTSIDE_SALON_HOURS,
    /** La ressource a ses propres horaires et le créneau ne tient pas dedans. */
    OUTSIDE_RESOURCE_HOURS,
    /** Une fermeture du salon recouvre le créneau (même partiellement). */
    SALON_CLOSED,
    /** Une fermeture de la ressource recouvre le créneau (même partiellement). */
    RESOURCE_CLOSED
}
