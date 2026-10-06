package it.walletinsight.core.users.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Aggregato utente: anagrafica minima più le {@link UserSettings}.
 *
 * `firstName` è l'unico dato obbligatorio: `lastName` ed `email` sono opzionali e
 * normalizzati a `null`, così l'assenza è esplicita e non si confonde con una stringa
 * vuota (stessa convenzione del frontend). L'email è normalizzata lowercase perché
 * in PostgreSQL il vincolo di unicità è sensibile alle maiuscole.
 */
public record User(UserId id, String firstName, String lastName, String email, UserSettings settings) {

    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_EMAIL_LENGTH = 160;

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(settings, "settings");
        firstName = requireText(firstName, "firstName");
        lastName = optionalText(lastName, "lastName");
        email = optionalEmail(email);
    }

    /** Copia con la sola anagrafica sostituita: le impostazioni restano quelle correnti. */
    public User withProfile(String newFirstName, String newLastName, String newEmail) {
        return new User(id, newFirstName, newLastName, newEmail, settings);
    }

    /** Copia con le impostazioni sostituite in blocco. */
    public User withSettings(UserSettings newSettings) {
        return new User(id, firstName, lastName, email, newSettings);
    }

    private static String requireText(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Il campo %s è obbligatorio.".formatted(field));
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Il campo %s supera i %d caratteri.".formatted(field, MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    /** Un testo opzionale: bianco o vuoto diventa `null`, mai una stringa vuota persistita. */
    private static String optionalText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Il campo %s supera i %d caratteri.".formatted(field, MAX_NAME_LENGTH));
        }
        return trimmed;
    }

    private static String optionalEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Il campo email supera i %d caratteri.".formatted(MAX_EMAIL_LENGTH));
        }
        return normalized;
    }
}
