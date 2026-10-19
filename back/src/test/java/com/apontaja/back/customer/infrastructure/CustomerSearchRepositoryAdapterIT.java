package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
import com.apontaja.back.customer.domain.CustomerSearchRepository.LinkPage;
import com.apontaja.back.customer.domain.SalonCustomerLink;
import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainersConfiguration.class, CustomerSearchRepositoryAdapter.class })
class CustomerSearchRepositoryAdapterIT {

    @Autowired
    private CustomerSearchRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    private UUID createSalon() {
        Organization organization = new Organization(UUID.randomUUID(), "Org Test", Instant.now());
        entityManager.persistAndFlush(organization);
        Salon salon = new Salon(UUID.randomUUID(), organization.getId(), "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", Instant.now());
        entityManager.persistAndFlush(salon);
        return salon.getId();
    }

    private UUID addCustomer(UUID salonId, String firstName, String lastName, String email, String phone) {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), firstName, lastName, email, phone,
                Instant.now());
        entityManager.persistAndFlush(profile);
        link(salonId, profile.getId());
        return profile.getId();
    }

    private void link(UUID salonId, UUID profileId) {
        entityManager.persistAndFlush(new SalonCustomerLink(UUID.randomUUID(), salonId, profileId,
                CustomerLinkSource.MANUAL, null, Instant.now()));
    }

    private List<UUID> profileIds(LinkPage page) {
        return page.links().stream().map(SalonCustomerLink::getCustomerProfileId).toList();
    }

    @Test
    void liste_par_nom_puis_prenom_sans_tenir_compte_de_la_casse_et_pagine() {
        UUID salonId = createSalon();
        UUID zola = addCustomer(salonId, "Carol", "zola", "c@example.com", null);
        UUID martinB = addCustomer(salonId, "bob", "Martin", "b@example.com", null);
        UUID martinA = addCustomer(salonId, "Alice", "martin", "a@example.com", null);

        LinkPage first = adapter.searchAliveLinks(salonId, null, 0, 2);
        LinkPage second = adapter.searchAliveLinks(salonId, null, 1, 2);

        assertThat(profileIds(first)).containsExactly(martinA, martinB);
        assertThat(profileIds(second)).containsExactly(zola);
        assertThat(first.totalElements()).isEqualTo(3);
    }

    @Test
    void exclut_les_clients_anonymises_et_ceux_d_un_autre_salon() {
        UUID salonId = createSalon();
        UUID kept = addCustomer(salonId, "Alice", "Martin", "a@example.com", null);
        CustomerProfile anonymous = new CustomerProfile(UUID.randomUUID(), "Bob", "Dupont", "b@example.com", null,
                Instant.now());
        anonymous.anonymize(Instant.now());
        entityManager.persistAndFlush(anonymous);
        link(salonId, anonymous.getId());
        addCustomer(createSalon(), "Carol", "Autre", "c@example.com", null);

        LinkPage page = adapter.searchAliveLinks(salonId, null, 0, 20);

        assertThat(profileIds(page)).containsExactly(kept);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void recherche_par_nom_dans_les_deux_sens_et_sans_tenir_compte_de_la_casse() {
        UUID salonId = createSalon();
        UUID alice = addCustomer(salonId, "Alice", "Martin", "a@example.com", null);
        addCustomer(salonId, "Bob", "Dupont", "b@example.com", null);

        assertThat(profileIds(adapter.searchAliveLinks(salonId, "ALI", 0, 20))).containsExactly(alice);
        assertThat(profileIds(adapter.searchAliveLinks(salonId, "alice martin", 0, 20))).containsExactly(alice);
        assertThat(profileIds(adapter.searchAliveLinks(salonId, "martin alice", 0, 20))).containsExactly(alice);
    }

    @Test
    void recherche_par_email() {
        UUID salonId = createSalon();
        UUID alice = addCustomer(salonId, "Alice", "Martin", "alice.martin@example.com", null);
        addCustomer(salonId, "Bob", "Dupont", "bob@example.com", null);

        assertThat(profileIds(adapter.searchAliveLinks(salonId, "Martin@Exam", 0, 20))).containsExactly(alice);
    }

    @Test
    void recherche_par_telephone_quelle_que_soit_la_mise_en_forme() {
        UUID salonId = createSalon();
        UUID alice = addCustomer(salonId, "Alice", "Martin", null, "06 12 34 56 78");
        addCustomer(salonId, "Bob", "Dupont", null, "07.98.76.54.32");

        assertThat(profileIds(adapter.searchAliveLinks(salonId, "0612", 0, 20))).containsExactly(alice);
        assertThat(profileIds(adapter.searchAliveLinks(salonId, "06 12", 0, 20))).containsExactly(alice);
        assertThat(profileIds(adapter.searchAliveLinks(salonId, "34-56", 0, 20))).containsExactly(alice);
    }

    @Test
    void les_jokers_like_sont_traites_comme_du_texte() {
        UUID salonId = createSalon();
        addCustomer(salonId, "Alice", "Martin", "a@example.com", null);

        assertThat(adapter.searchAliveLinks(salonId, "%", 0, 20).links()).isEmpty();
        assertThat(adapter.searchAliveLinks(salonId, "_", 0, 20).links()).isEmpty();
    }

    @Test
    void detecte_un_lien_vivant_dans_un_autre_salon() {
        UUID salonA = createSalon();
        UUID salonB = createSalon();
        UUID shared = addCustomer(salonA, "Alice", "Martin", "a@example.com", null);
        link(salonB, shared);
        UUID alone = addCustomer(salonA, "Bob", "Dupont", "b@example.com", null);

        assertThat(adapter.existsAliveLinkInOtherSalon(shared, salonA)).isTrue();
        assertThat(adapter.existsAliveLinkInOtherSalon(alone, salonA)).isFalse();
    }
}
