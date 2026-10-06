package it.walletinsight.bff.users;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.KebabCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo della sostituzione in blocco delle impostazioni. */
public record UserSettingsRequest(
        @NotBlank @Size(max = 64) @Schema(example = "Europe/Rome") String timeZone,
        @NotBlank @Schema(allowableValues = {"it", "en"}) String language,
        @NotBlank @Schema(allowableValues = {
                "today", "current-week", "current-month", "current-year", "last-7-days", "last-30-days"
        }) String defaultDashboardPeriod) {

    public UserSettings toDomain() {
        return new UserSettings(
                KebabCase.to(UserLanguage.class, language),
                timeZone,
                KebabCase.to(DashboardPeriod.class, defaultDashboardPeriod));
    }
}
