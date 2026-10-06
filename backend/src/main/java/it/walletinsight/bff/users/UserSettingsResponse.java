package it.walletinsight.bff.users;

import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.KebabCase;

/** Forma JSON delle sole impostazioni. */
public record UserSettingsResponse(
        String timeZone,
        String language,
        String defaultDashboardPeriod) {

    public static UserSettingsResponse from(UserSettings settings) {
        return new UserSettingsResponse(
                settings.timeZone(),
                KebabCase.from(settings.language()),
                KebabCase.from(settings.defaultDashboardPeriod()));
    }
}
