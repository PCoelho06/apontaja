package com.apontaja.back.customer.infrastructure;

import com.apontaja.back.customer.domain.CustomerLinkSource;
import com.apontaja.back.customer.domain.CustomerProfile;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class SalonCustomerLinkJpaRepositoryIT {

    @Autowired
    private SalonCustomerLinkJpaRepository linkJpaRepository;

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

    private UUID createProfile(String firstName) {
        CustomerProfile profile = new CustomerProfile(UUID.randomUUID(), firstName, "Nom",
                firstName.toLowerCase(java.util.Locale.ROOT) + "@example.com", null, Instant.now());
        entityManager.persistAndFlush(profile);
        return profile.getId();
    }

    private SalonCustomerLink newLink(UUID salonId, UUID profileId) {
        return new SalonCustomerLink(UUID.randomUUID(), salonId, profileId, CustomerLinkSource.MANUAL, null,
                Instant.now());
    }

    @Test
    void persiste_et_retrouve_un_lien_vivant() {
        UUID salonId = createSalon();
        UUID profileId = createProfile("Alice");
        SalonCustomerLink link = linkJpaRepository.saveAndFlush(newLink(salonId, profileId));

        assertThat(linkJpaRepository.findBySalonIdAndCustomerProfileIdAndDeletedAtIsNull(salonId, profileId))
                .contains(link);
    }

    @Test
    void rejette_un_second_lien_vivant_pour_le_meme_salon_et_profil() {
        UUID salonId = createSalon();
        UUID profileId = createProfile("Bob");
        linkJpaRepository.saveAndFlush(newLink(salonId, profileId));

        assertThatThrownBy(() -> linkJpaRepository.saveAndFlush(newLink(salonId, profileId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void le_meme_profil_peut_etre_rattache_a_deux_salons_differents() {
        UUID profileId = createProfile("Carol");

        List<SalonCustomerLink> saved = List.of(linkJpaRepository.saveAndFlush(newLink(createSalon(), profileId)),
                linkJpaRepository.saveAndFlush(newLink(createSalon(), profileId)));

        assertThat(saved).extracting(SalonCustomerLink::getId).doesNotHaveDuplicates();
    }

    @Test
    void liste_les_liens_vivants_du_salon_dans_l_ordre_de_creation() {
        UUID salonId = createSalon();
        UUID first = linkJpaRepository.saveAndFlush(newLink(salonId, createProfile("Dave"))).getId();
        UUID second = linkJpaRepository.saveAndFlush(newLink(salonId, createProfile("Eve"))).getId();
        linkJpaRepository.saveAndFlush(newLink(createSalon(), createProfile("Frank")));

        List<SalonCustomerLink> result = linkJpaRepository.findBySalonIdAndDeletedAtIsNullOrderByCreatedAtAsc(salonId);

        assertThat(result).extracting(SalonCustomerLink::getId).containsExactly(first, second);
    }
}
