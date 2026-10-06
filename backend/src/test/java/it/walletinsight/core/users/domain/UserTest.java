package it.walletinsight.core.users.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);

    private static User utente(String firstName, String lastName, String email) {
        return new User(UserId.newId(), firstName, lastName, email, IMPOSTAZIONI);
    }

    @Test
    void normalizzaSpaziEMaiuscole() {
        User user = utente(" Marta ", null, " Marta.Rossi@Esempio.IT ");

        assertThat(user.firstName()).isEqualTo("Marta");
        // L'email lowercase è necessaria: il vincolo di unicità PostgreSQL è case-sensitive.
        assertThat(user.email()).isEqualTo("marta.rossi@esempio.it");
    }

    @Test
    void trasformaITestiVuotiInNull() {
        User user = utente("Marta", "   ", "");

        assertThat(user.lastName()).isNull();
        assertThat(user.email()).isNull();
    }

    @Test
    void rifiutaUnNomeMancante() {
        assertThatThrownBy(() -> utente(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName");
        assertThatThrownBy(() -> utente("  ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName");
    }

    @Test
    void rifiutaCampiPiuLunghiDelLimite() {
        String nomeLungo = "x".repeat(User.MAX_NAME_LENGTH + 1);
        String emailLunga = "a".repeat(User.MAX_EMAIL_LENGTH - 10) + "@esempio.it";

        assertThatThrownBy(() -> utente(nomeLungo, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> utente("Marta", nomeLungo, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(emailLunga.length()).isGreaterThan(User.MAX_EMAIL_LENGTH);
        assertThatThrownBy(() -> utente("Marta", null, emailLunga))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void accettaUnEmailLungaFinoAlLimite() {
        String emailAlLimite = "a".repeat(User.MAX_EMAIL_LENGTH - 11) + "@esempio.it";

        assertThat(utente("Marta", null, emailAlLimite).email()).hasSize(User.MAX_EMAIL_LENGTH);
    }

    @Test
    void withSettingsRestituisceUnaCopiaSenzaToccareLOriginale() {
        User user = utente("Marta", "Rossi", null);
        UserSettings nuove = new UserSettings(UserLanguage.EN, "UTC", DashboardPeriod.LAST_30_DAYS);

        User copia = user.withSettings(nuove);

        assertThat(copia.settings()).isEqualTo(nuove);
        assertThat(copia.id()).isEqualTo(user.id());
        assertThat(copia.firstName()).isEqualTo("Marta");
        assertThat(user.settings()).isEqualTo(IMPOSTAZIONI);
    }

    @Test
    void withProfileSostituisceSoloLAnagrafica() {
        User user = utente("Marta", null, null);

        User copia = user.withProfile("Luca", "Bianchi", "luca@esempio.it");

        assertThat(copia.settings()).isEqualTo(IMPOSTAZIONI);
        assertThat(copia.firstName()).isEqualTo("Luca");
        assertThat(copia.lastName()).isEqualTo("Bianchi");
        assertThat(copia.email()).isEqualTo("luca@esempio.it");
    }
}
