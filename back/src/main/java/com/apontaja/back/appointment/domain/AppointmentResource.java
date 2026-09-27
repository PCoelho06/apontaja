package com.apontaja.back.appointment.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Ressource engagée sur un RDV. Ne mappe QUE la clé composite
 * (appointment_id, resource_id) : {@code starts_at}/{@code ends_at}/
 * {@code is_active}/{@code during} sont entièrement pilotées par les triggers
 * SQL ({@code fn_appointment_resource_sync_on_insert},
 * {@code fn_appointment_propagate_to_resources}, voir
 * V1__initial_schema.sql) — PostgreSQL exécute les triggers BEFORE INSERT
 * avant la vérification NOT NULL, donc ces colonnes sont toujours correctement
 * renseignées même si l'entité JPA ne les écrit jamais elle-même.
 *
 * <p>
 * C'est cette contrainte d'exclusion (sur {@code is_active} et {@code during})
 * qui ferme définitivement le bug historique de double-booking non détecté
 * (§3.3 de l'audit) : {@link AppointmentResourceId} appointment_id/resource_id
 * en conflit remonte en {@code DataIntegrityViolationException} (SQLSTATE
 * 23P01) à l'appelant.
 *
 * <p>
 * Implémente {@link Persistable} (identifiant composite assigné côté
 * application, même raison que {@code ServiceResource}).
 */
@Entity
@Table(name = "appointment_resource")
public class AppointmentResource implements Persistable<AppointmentResourceId> {

    @EmbeddedId
    private AppointmentResourceId id;

    @Transient
    private boolean isNew = true;

    protected AppointmentResource() {
        // requis par Hibernate
    }

    public AppointmentResource(UUID appointmentId, UUID resourceId) {
        this.id = new AppointmentResourceId(appointmentId, resourceId);
    }

    @Override
    public AppointmentResourceId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getAppointmentId() {
        return id.getAppointmentId();
    }

    public UUID getResourceId() {
        return id.getResourceId();
    }
}
