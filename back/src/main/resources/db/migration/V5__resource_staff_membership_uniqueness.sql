-- Un membre de l'équipe n'est lié qu'à une seule ressource vivante (Phase 3, tranche 8c).
-- Le contrôle applicatif donne un 409 propre ; cet index tient aussi en cas de concurrence.
CREATE UNIQUE INDEX uq_resource_staff_membership_alive
    ON resource (staff_membership_id)
    WHERE staff_membership_id IS NOT NULL AND deleted_at IS NULL;
