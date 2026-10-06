package it.walletinsight.core.users.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSettingsTest {

    private static final UserSettings VALIDE =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);

    @Test
    void accettaUnFusoIanaValido() {
        assertThat(VALIDE.timeZone()).isEqualTo("Europe/Rome");
        assertThat(new UserSettings(UserLanguage.EN, "UTC", DashboardPeriod.TODAY).timeZone())
                .isEqualTo("UTC");
    }

    @Test
    void rifiutaUnFusoCheNonEsiste() {
        assertThatThrownBy(() -> new UserSettings(
                UserLanguage.IT, "Marte/OlympusMons", DashboardPeriod.TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Marte/OlympusMons");
    }

    @Test
    void rifiutaCampiMancanti() {
        assertThatThrownBy(() -> new UserSettings(null, "Europe/Rome", DashboardPeriod.TODAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserSettings(UserLanguage.IT, null, DashboardPeriod.TODAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserSettings(UserLanguage.IT, "Europe/Rome", null))
                .isInstanceOf(NullPointerException.class);
    }
}
