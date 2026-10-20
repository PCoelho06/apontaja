package com.apontaja.back.salon.application;

import com.apontaja.back.salon.domain.SalonBookingSettings;
import com.apontaja.back.salon.domain.SalonBookingSettingsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Lecture et remplacement des réglages de réservation en ligne. L'existence du salon et les droits
 * sont contrôlés en amont (guards) ; une ligne absente se lit comme les valeurs par défaut.
 */
@Service
public class BookingSettingsService {

    private final SalonBookingSettingsRepository repository;
    private final Clock clock;

    BookingSettingsService(SalonBookingSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BookingSettings get(UUID salonId) {
        return repository.findBySalonId(salonId).map(BookingSettings::from)
                .orElseGet(() -> BookingSettings.defaults(salonId));
    }

    @Transactional
    public BookingSettings update(UpdateBookingSettingsCommand command) {
        validate(command);
        repository.lockSalon(command.salonId());
        Instant now = clock.instant();
        SalonBookingSettings entity = repository.findBySalonId(command.salonId()).orElse(null);
        if (entity == null) {
            entity = new SalonBookingSettings(command.salonId(), command.onlineBookingEnabled(),
                    command.autoConfirm(), command.slotGranularityMinutes(), command.minNoticeMinutes(),
                    command.horizonDays(), command.cancellationDeadlineHours(), now);
        } else {
            entity.apply(command.onlineBookingEnabled(), command.autoConfirm(), command.slotGranularityMinutes(),
                    command.minNoticeMinutes(), command.horizonDays(), command.cancellationDeadlineHours(), now);
        }
        return BookingSettings.from(repository.save(entity));
    }

    private static void validate(UpdateBookingSettingsCommand c) {
        if (!BookingSettingsLimits.ALLOWED_GRANULARITIES.contains(c.slotGranularityMinutes())) {
            throw new InvalidBookingSettingsException(
                    "La granularité doit valoir l'une de ces valeurs (minutes) : "
                            + BookingSettingsLimits.ALLOWED_GRANULARITIES);
        }
        requireBetween(c.minNoticeMinutes(), 0, BookingSettingsLimits.MAX_MIN_NOTICE_MINUTES,
                "Le délai minimum de réservation (minutes)");
        requireBetween(c.horizonDays(), BookingSettingsLimits.MIN_HORIZON_DAYS,
                BookingSettingsLimits.MAX_HORIZON_DAYS, "L'horizon de réservation (jours)");
        requireBetween(c.cancellationDeadlineHours(), 0, BookingSettingsLimits.MAX_CANCELLATION_DEADLINE_HOURS,
                "Le délai d'annulation (heures)");
    }

    private static void requireBetween(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new InvalidBookingSettingsException(label + " doit être compris entre " + min + " et " + max + ".");
        }
    }
}
