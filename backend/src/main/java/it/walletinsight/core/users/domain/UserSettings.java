package it.walletinsight.core.users.domain;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Impostazioni dell'utente: lingua, fuso orario e periodo predefinito della panoramica.
 *
 * Value object dell'aggregato {@link User}: le impostazioni non hanno identità propria
 * e si sostituiscono in blocco, mai campo per campo. Il tema dell'interfaccia non sta
 * qui: è una preferenza solo client-side che il frontend tiene in localStorage.
 */
public record UserSettings(UserLanguage language, String timeZone, DashboardPeriod defaultDashboardPeriod) {

    public UserSettings {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(defaultDashboardPeriod, "defaultDashboardPeriod");
        timeZone = requireSupportedZone(timeZone);
    }

    private static String requireSupportedZone(String timeZone) {
        Objects.requireNonNull(timeZone, "timeZone");
        if (!ZoneId.getAvailableZoneIds().contains(timeZone)) {
            // Il messaggio finisce nel ProblemDetail 400: contiene solo un valore inviato dal client.
            throw new IllegalArgumentException("Fuso orario non valido: %s.".formatted(timeZone));
        }
        return timeZone;
    }
}
