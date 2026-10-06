package it.walletinsight.bff.users;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.KebabCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo dell'aggiornamento completo: sostituzione dell'aggregato, non patch parziale.
 * Separato da {@link CreateUserRequest} perché i due contratti possono divergere.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = User.MAX_NAME_LENGTH) String firstName,
        @Size(max = User.MAX_NAME_LENGTH) String lastName,
        @Email @Size(max = User.MAX_EMAIL_LENGTH) String email,
        @NotBlank @Size(max = 64) @Schema(example = "Europe/Rome") String timeZone,
        @NotBlank @Schema(allowableValues = {"it", "en"}) String language,
        @NotBlank @Schema(allowableValues = {
                "today", "current-week", "current-month", "current-year", "last-7-days", "last-30-days"
        }) String defaultDashboardPeriod) {

    public UserSettings toSettings() {
        return new UserSettings(
                KebabCase.to(UserLanguage.class, language),
                timeZone,
                KebabCase.to(DashboardPeriod.class, defaultDashboardPeriod));
    }
}
