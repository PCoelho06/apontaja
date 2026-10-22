package com.apontaja.back.appointment.application;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Texte des emails au client : texte brut, portugais (Portugal), heures dans le fuseau du salon. */
final class ClientEmailComposer {

    private static final Locale PT = Locale.of("pt", "PT");
    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("EEEE, d 'de' MMMM 'de' uuuu 'às' HH:mm", PT);

    record ClientEmail(String subject, String body) {
    }

    private ClientEmailComposer() {
    }

    static ClientEmail compose(ClientAppointmentNotification n, String firstName) {
        ZoneId zone = ZoneId.of(n.timezone());
        List<String> lines = new ArrayList<>();
        lines.add("Olá, " + firstName + ",");
        lines.add("");
        lines.add(intro(n));
        lines.add("");
        lines.add("Serviço: " + n.serviceName());
        lines.add("Com: " + Objects.requireNonNullElse(n.resourceName(), "-"));
        lines.add("Data e hora: " + n.startAt().atZone(zone).format(WHEN) + " (hora de " + n.timezone() + ")");
        lines.add("Duração: " + n.durationMinutes() + " min");
        lines.add("Preço: " + price(n.priceCents()));
        lines.add("Local: " + n.salonAddress() + ", " + n.salonPostalCode() + " " + n.salonCity());
        lines.add("");
        lines.add(closing(n, zone));
        lines.add("");
        lines.add("Com os melhores cumprimentos,");
        lines.add("Apontaja");
        return new ClientEmail(subject(n), String.join("\n", lines));
    }

    private static String subject(ClientAppointmentNotification n) {
        return switch (n.kind()) {
            case REQUEST_RECEIVED -> "Pedido de marcação recebido - " + n.salonName();
            case CONFIRMED -> "Marcação confirmada - " + n.salonName();
            case CANCELLED -> "Marcação cancelada - " + n.salonName();
        };
    }

    private static String intro(ClientAppointmentNotification n) {
        return switch (n.kind()) {
            case REQUEST_RECEIVED -> "Recebemos o seu pedido de marcação em " + n.salonName()
                    + ". O salão ainda tem de o validar. Pode acompanhar o estado do pedido em"
                    + " «As minhas marcações».";
            case CONFIRMED -> "A sua marcação em " + n.salonName() + " está confirmada.";
            case CANCELLED -> "Confirmamos o cancelamento da sua marcação em " + n.salonName() + ", a seu pedido.";
        };
    }

    private static String closing(ClientAppointmentNotification n, ZoneId zone) {
        return switch (n.kind()) {
            case REQUEST_RECEIVED -> "Pode cancelar o pedido até ao início da marcação.";
            case CONFIRMED -> n.cancellationDeadlineHours() == 0 ? "Pode cancelar até ao início da marcação."
                    : "Pode cancelar até " + n.startAt().minus(n.cancellationDeadlineHours(), ChronoUnit.HOURS)
                            .atZone(zone).format(WHEN) + ".";
            case CANCELLED -> "Se desejar, pode fazer uma nova marcação a qualquer momento.";
        };
    }

    private static String price(int cents) {
        return String.format(Locale.ROOT, "%d,%02d €", cents / 100, cents % 100);
    }
}
