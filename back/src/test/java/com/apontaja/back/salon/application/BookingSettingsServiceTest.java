package com.apontaja.back.salon.application;

import com.apontaja.back.salon.domain.SalonBookingSettings;
import com.apontaja.back.salon.domain.SalonBookingSettingsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");
    private static final UUID SALON = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Mock
    private SalonBookingSettingsRepository repository;

    private BookingSettingsService service;

    @BeforeEach
    void setUp() {
        service = new BookingSettingsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UpdateBookingSettingsCommand command(int granularity, int minNotice, int horizon,
            int cancellation) {
        return new UpdateBookingSettingsCommand(SALON, true, true, granularity, minNotice, horizon, cancellation);
    }

    @Test
    void sans_ligne_les_reglages_sont_les_valeurs_par_defaut_reservation_desactivee() {
        when(repository.findBySalonId(SALON)).thenReturn(Optional.empty());

        BookingSettings settings = service.get(SALON);

        assertThat(settings).isEqualTo(new BookingSettings(SALON, false, false, 15, 120, 60, 24));
    }

    @Test
    void lit_les_reglages_enregistres() {
        when(repository.findBySalonId(SALON))
                .thenReturn(Optional.of(new SalonBookingSettings(SALON, true, true, 30, 60, 30, 12, NOW)));

        assertThat(service.get(SALON)).isEqualTo(new BookingSettings(SALON, true, true, 30, 60, 30, 12));
    }

    @Test
    void cree_la_ligne_apres_avoir_pris_le_verrou() {
        when(repository.findBySalonId(SALON)).thenReturn(Optional.empty());
        when(repository.save(any(SalonBookingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingSettings result = service.update(command(30, 60, 90, 12));

        InOrder order = inOrder(repository);
        order.verify(repository).lockSalon(SALON);
        ArgumentCaptor<SalonBookingSettings> saved = ArgumentCaptor.forClass(SalonBookingSettings.class);
        order.verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSalonId()).isEqualTo(SALON);
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(NOW);
        assertThat(result).isEqualTo(new BookingSettings(SALON, true, true, 30, 60, 90, 12));
    }

    @Test
    void remplace_les_valeurs_d_une_ligne_existante() {
        SalonBookingSettings existing = new SalonBookingSettings(SALON, false, false, 15, 120, 60, 24,
                Instant.parse("2098-01-01T00:00:00Z"));
        when(repository.findBySalonId(SALON)).thenReturn(Optional.of(existing));
        when(repository.save(any(SalonBookingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingSettings result = service.update(command(60, 0, 365, 0));

        verify(repository).save(existing);
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        assertThat(result).isEqualTo(new BookingSettings(SALON, true, true, 60, 0, 365, 0));
    }

    @Test
    void accepte_les_bornes_extremes() {
        when(repository.findBySalonId(SALON)).thenReturn(Optional.empty());
        when(repository.save(any(SalonBookingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(command(5, 10_080, 1, 720))).isEqualTo(
                new BookingSettings(SALON, true, true, 5, 10_080, 1, 720));
    }

    @Test
    void refuse_les_valeurs_hors_bornes_sans_toucher_au_depot() {
        assertThatThrownBy(() -> service.update(command(7, 60, 30, 24)))
                .isInstanceOf(InvalidBookingSettingsException.class);
        assertThatThrownBy(() -> service.update(command(15, -1, 30, 24)))
                .isInstanceOf(InvalidBookingSettingsException.class);
        assertThatThrownBy(() -> service.update(command(15, 10_081, 30, 24)))
                .isInstanceOf(InvalidBookingSettingsException.class);
        assertThatThrownBy(() -> service.update(command(15, 60, 0, 24)))
                .isInstanceOf(InvalidBookingSettingsException.class);
        assertThatThrownBy(() -> service.update(command(15, 60, 366, 24)))
                .isInstanceOf(InvalidBookingSettingsException.class);
        assertThatThrownBy(() -> service.update(command(15, 60, 30, -1)))
                .isInstanceOf(InvalidBookingSettingsException.class);
        assertThatThrownBy(() -> service.update(command(15, 60, 30, 721)))
                .isInstanceOf(InvalidBookingSettingsException.class);

        verifyNoInteractions(repository);
    }
}
