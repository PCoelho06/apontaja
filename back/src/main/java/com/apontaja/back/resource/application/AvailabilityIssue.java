package com.apontaja.back.resource.application;

/** Miroir applicatif de AvailabilityViolation (le domaine n'est pas exposé aux autres domaines). */
public enum AvailabilityIssue {
    OUTSIDE_SALON_HOURS, OUTSIDE_RESOURCE_HOURS, SALON_CLOSED, RESOURCE_CLOSED
}
