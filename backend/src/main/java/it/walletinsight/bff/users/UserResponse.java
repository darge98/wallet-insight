package it.walletinsight.bff.users;

import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.KebabCase;

/**
 * Forma JSON dell'utente: piatta, identica a `UserProfile` del frontend
 * (nessun oggetto `settings` annidato). Con `default-property-inclusion: non_null`
 * i campi nulli vengono omessi: l'adapter del frontend li riporterà a `null`.
 */
public record UserResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String timeZone,
        String language,
        String defaultDashboardPeriod) {

    public static UserResponse from(User user) {
        UserSettings settings = user.settings();
        return new UserResponse(
                user.id().toString(),
                user.firstName(),
                user.lastName(),
                user.email(),
                settings.timeZone(),
                KebabCase.from(settings.language()),
                KebabCase.from(settings.defaultDashboardPeriod()));
    }
}
