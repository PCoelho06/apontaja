package com.apontaja.back.web;

/**
 * Catalogue des codes d'erreur stables du back-office (propriété {@code code} des réponses d'erreur).
 * Le front s'appuie sur ces codes, jamais sur le message (qui reste en français et peut changer).
 * Les GET unitaires d'une entité absente répondent 404 sans corps ni code (le statut suffit).
 * Un code déjà publié ne change ni de nom ni de sens. Les codes propres au client
 * (réservation en ligne, annulation) sont émis par leurs contrôleurs et ne figurent pas ici.
 */
public enum ApiErrorCode {

    // Ressources, horaires, fermetures
    RESOURCE_NOT_FOUND,
    RESOURCE_NAME_ALREADY_USED,
    /** Suppression refusée : la ressource est encore utilisée (RDV actif). */
    RESOURCE_IN_USE,
    INVALID_RESOURCE_TYPE,
    RESOURCE_STAFF_LINK_INVALID,
    STAFF_MEMBER_NOT_FOUND,
    STAFF_MEMBER_ALREADY_LINKED,
    INVALID_TIME_RANGE,
    CLOSURE_NOT_FOUND,
    CLOSURE_BLOCKED_BY_APPOINTMENTS,

    // Prestations
    SERVICE_NOT_FOUND,
    SERVICE_NAME_ALREADY_USED,
    /** Suppression refusée : la prestation est encore utilisée (RDV actif). */
    SERVICE_IN_USE,
    SERVICE_RESOURCE_LINK_CONFLICT,

    // Réglages de réservation
    INVALID_BOOKING_SETTINGS,

    // Rendez-vous (côté staff)
    APPOINTMENT_NOT_FOUND,
    SERVICE_RESOURCE_NOT_ASSOCIATED,
    APPOINTMENT_NOT_AVAILABLE,
    APPOINTMENT_CONFLICT,
    APPOINTMENT_TRANSITION_FORBIDDEN,
    APPOINTMENT_NOT_RESCHEDULABLE,

    // Clients
    CUSTOMER_NOT_FOUND,
    CUSTOMER_CONTACT_REQUIRED,
    CUSTOMER_ANONYMIZED,
    CUSTOMER_SHARED,
    CUSTOMER_CLAIMED,
    CUSTOMER_HAS_UPCOMING_APPOINTMENTS,
    INVALID_MERGE_REQUEST
}
