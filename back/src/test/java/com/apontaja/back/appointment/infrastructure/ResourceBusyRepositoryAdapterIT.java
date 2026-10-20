package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.BusyInterval;
import com.apontaja.back.appointment.domain.ResourceBusyRepository;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceType;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les lignes de customer_profile / service / appointment sont posées en SQL natif (états atteints par
 * d'autres domaines ou par des triggers) ; les triggers remplissent appointment_resource.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, ResourceBusyRepositoryAdapter.class})
class ResourceBusyRepositoryAdapterIT {

    private static final Instant T0 = Instant.parse("2099-01-01T10:00:00Z");

    @Autowired
    private ResourceBusyRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID salonId;
    private UUID resource1;
    private UUID resource2;
    private UUID customerId;
    private UUID serviceId;

    @BeforeEach
    void fixtures() {
        UUID organizationId = UUID.randomUUID();
        entityManager.persistAndFlush(new Organization(organizationId, "Org Test", T0));
        salonId = UUID.randomUUID();
        entityManager.persistAndFlush(new Salon(salonId, organizationId, "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", T0));
        resource1 = UUID.randomUUID();
        resource2 = UUID.randomUUID();
        entityManager.persistAndFlush(new Resource(resource1, salonId, "R1", ResourceType.EMPLOYEE, T0));
        entityManager.persistAndFlush(new Resource(resource2, salonId, "R2", ResourceType.EMPLOYEE, T0));
        customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO customer_profile (id, first_name, last_name, email) VALUES (?, 'Alice', 'Martin', ?)",
                customerId, "alice-" + customerId + "@example.com");
        serviceId = UUID.randomUUID();
        jdbc.update("INSERT INTO service (id, salon_id, name, default_duration_minutes, default_price_cents) "
                + "VALUES (?, ?, 'Coupe', 60, 3000)", serviceId, salonId);
    }

    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }

    private UUID appointment(UUID resourceId, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment (id, salon_id, customer_profile_id, service_id, start_at, end_at, "
                + "status, price_at_booking_cents, duration_at_booking_minutes) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', 3000, 60)", id, salonId, customerId, serviceId, at(start),
                at(end));
        jdbc.update("INSERT INTO appointment_resource (appointment_id, resource_id) VALUES (?, ?)", id, resourceId);
        return id;
    }

    private List<BusyInterval> busy(List<UUID> resources, String from, String to) {
        return repository.findActiveIntervals(resources, Instant.parse(from), Instant.parse(to));
    }

    @Test
    void renvoie_les_plages_des_rdv_actifs_recouvrant_la_fenetre_triees() {
        appointment(resource1, "2099-06-10T10:00:00Z", "2099-06-10T11:00:00Z");
        appointment(resource1, "2099-06-10T08:00:00Z", "2099-06-10T09:00:00Z");
        appointment(resource1, "2099-06-10T14:00:00Z", "2099-06-10T15:00:00Z");

        List<BusyInterval> result = busy(List.of(resource1), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z");

        assertThat(result).containsExactly(
                new BusyInterval(resource1, Instant.parse("2099-06-10T08:00:00Z"),
                        Instant.parse("2099-06-10T09:00:00Z")),
                new BusyInterval(resource1, Instant.parse("2099-06-10T10:00:00Z"),
                        Instant.parse("2099-06-10T11:00:00Z")));
    }

    @Test
    void les_bornes_adjacentes_ne_comptent_pas_comme_un_recouvrement() {
        appointment(resource1, "2099-06-10T08:00:00Z", "2099-06-10T09:00:00Z");
        appointment(resource1, "2099-06-10T12:00:00Z", "2099-06-10T13:00:00Z");

        assertThat(busy(List.of(resource1), "2099-06-10T09:00:00Z", "2099-06-10T12:00:00Z")).isEmpty();
    }

    @Test
    void un_rdv_a_cheval_sur_la_fenetre_est_retourne() {
        appointment(resource1, "2099-06-10T06:30:00Z", "2099-06-10T07:30:00Z");

        assertThat(busy(List.of(resource1), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z")).hasSize(1);
    }

    @Test
    void exclut_les_rdv_annules_et_supprimes() {
        UUID cancelled = appointment(resource1, "2099-06-10T08:00:00Z", "2099-06-10T09:00:00Z");
        UUID deleted = appointment(resource1, "2099-06-10T10:00:00Z", "2099-06-10T11:00:00Z");
        jdbc.update("UPDATE appointment SET status = 'CANCELLED' WHERE id = ?", cancelled);
        jdbc.update("UPDATE appointment SET deleted_at = now() WHERE id = ?", deleted);

        assertThat(busy(List.of(resource1), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z")).isEmpty();
    }

    @Test
    void les_rdv_termines_ou_no_show_restent_comptes_comme_la_contrainte_d_exclusion() {
        UUID noShow = appointment(resource1, "2099-06-10T08:00:00Z", "2099-06-10T09:00:00Z");
        jdbc.update("UPDATE appointment SET status = 'NO_SHOW' WHERE id = ?", noShow);

        assertThat(busy(List.of(resource1), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z")).hasSize(1);
    }

    @Test
    void filtre_par_ressource_et_accepte_plusieurs_ressources() {
        appointment(resource1, "2099-06-10T08:00:00Z", "2099-06-10T09:00:00Z");
        appointment(resource2, "2099-06-10T08:00:00Z", "2099-06-10T09:00:00Z");

        assertThat(busy(List.of(resource2), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z"))
                .extracting(BusyInterval::resourceId).containsExactly(resource2);
        assertThat(busy(List.of(resource1, resource2), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z"))
                .hasSize(2);
    }

    @Test
    void une_liste_de_ressources_vide_donne_une_liste_vide() {
        assertThat(busy(List.of(), "2099-06-10T07:00:00Z", "2099-06-10T12:00:00Z")).isEmpty();
    }
}
