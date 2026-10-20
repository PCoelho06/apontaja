package com.apontaja.back.salon.infrastructure;

import com.apontaja.back.organization.domain.Organization;
import com.apontaja.back.salon.domain.Salon;
import com.apontaja.back.salon.domain.SalonBookingSettings;
import com.apontaja.back.salon.domain.SalonBookingSettingsRepository;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, SalonBookingSettingsRepositoryAdapter.class})
class SalonBookingSettingsRepositoryAdapterIT {

    private static final Instant T0 = Instant.parse("2099-01-01T10:00:00Z");

    @Autowired
    private SalonBookingSettingsRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID persistSalon() {
        UUID organizationId = UUID.randomUUID();
        entityManager.persistAndFlush(new Organization(organizationId, "Org Test", T0));
        UUID salonId = UUID.randomUUID();
        entityManager.persistAndFlush(new Salon(salonId, organizationId, "Salon Test", "1 rue du Test", "06140",
                "Vence", "France", "Europe/Paris", T0));
        return salonId;
    }

    @Test
    void aucune_ligne_tant_que_rien_n_est_enregistre() {
        assertThat(repository.findBySalonId(persistSalon())).isEmpty();
    }

    @Test
    void enregistre_puis_relit_les_reglages() {
        UUID salonId = persistSalon();

        repository.save(new SalonBookingSettings(salonId, true, false, 30, 60, 90, 12, T0));
        entityManager.clear();

        SalonBookingSettings found = repository.findBySalonId(salonId).orElseThrow();
        assertThat(found.isOnlineBookingEnabled()).isTrue();
        assertThat(found.isAutoConfirm()).isFalse();
        assertThat(found.getSlotGranularityMinutes()).isEqualTo(30);
        assertThat(found.getMinNoticeMinutes()).isEqualTo(60);
        assertThat(found.getHorizonDays()).isEqualTo(90);
        assertThat(found.getCancellationDeadlineHours()).isEqualTo(12);
        assertThat(found.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void une_mise_a_jour_remplace_la_ligne_existante() {
        UUID salonId = persistSalon();
        repository.save(new SalonBookingSettings(salonId, true, false, 30, 60, 90, 12, T0));
        entityManager.clear();

        SalonBookingSettings loaded = repository.findBySalonId(salonId).orElseThrow();
        Instant later = Instant.parse("2099-02-01T10:00:00Z");
        loaded.apply(false, true, 15, 0, 7, 0, later);
        repository.save(loaded);
        entityManager.clear();

        SalonBookingSettings found = repository.findBySalonId(salonId).orElseThrow();
        assertThat(found.isOnlineBookingEnabled()).isFalse();
        assertThat(found.isAutoConfirm()).isTrue();
        assertThat(found.getSlotGranularityMinutes()).isEqualTo(15);
        assertThat(found.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void la_base_refuse_les_valeurs_hors_bornes() {
        UUID salonId = persistSalon();

        assertThatThrownBy(() -> repository.save(new SalonBookingSettings(salonId, true, false, 7, 60, 90, 12, T0)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void le_verrou_consultatif_s_acquiert_dans_une_transaction() {
        repository.lockSalon(persistSalon());
    }
}
