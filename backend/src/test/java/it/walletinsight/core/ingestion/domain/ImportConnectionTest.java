package it.walletinsight.core.ingestion.domain;

import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportConnectionTest {

    private static final UserId UTENTE = UserId.newId();
    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");
    private static final PersonalToken ALTRO_TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.wXyZ9876");
    private static final LocalDate OGGI = LocalDate.of(2026, 9, 21);

    @Test
    void collegaUnaSorgenteConUnIdentificatoreSequenziale() {
        ImportConnection connection =
                ImportConnection.configure(UTENTE, IngestionSource.BUDGET_BAKERS, TOKEN, true, OGGI);

        assertThat(connection.id().value().version()).isEqualTo(7);
        assertThat(connection.enabled()).isTrue();
        assertThat(connection.configuredAt()).isEqualTo(OGGI);
    }

    @Test
    void ricollegareSostituisceLeCredenzialiMantenendoLIdentificatore() {
        ImportConnection connection =
                ImportConnection.configure(UTENTE, IngestionSource.BUDGET_BAKERS, TOKEN, true, OGGI);

        ImportConnection ricollegata =
                connection.reconfigure(ALTRO_TOKEN, false, OGGI.plusDays(1));

        assertThat(ricollegata.id()).isEqualTo(connection.id());
        assertThat(ricollegata.token()).isEqualTo(ALTRO_TOKEN);
        assertThat(ricollegata.enabled()).isFalse();
        assertThat(ricollegata.configuredAt()).isEqualTo(OGGI.plusDays(1));
    }

    @Test
    void unaSorgenteAppenaCollegataNonEMaiStataImportata() {
        ImportConnection connection =
                ImportConnection.configure(UTENTE, IngestionSource.BUDGET_BAKERS, TOKEN, true, OGGI);

        assertThat(connection.neverImported()).isTrue();
        assertThat(connection.lastRecordDate()).isNull();
    }

    @Test
    void ilSegnapostoAvanzaFinoAllUltimoDatoLetto() {
        ImportConnection connection =
                ImportConnection.configure(UTENTE, IngestionSource.BUDGET_BAKERS, TOKEN, true, OGGI);

        ImportConnection importata = connection.importedThrough(OGGI.minusDays(1));

        assertThat(importata.lastRecordDate()).isEqualTo(OGGI.minusDays(1));
        assertThat(importata.neverImported()).isFalse();
        assertThat(importata.id()).isEqualTo(connection.id());
    }

    @Test
    void ilSegnapostoNonTornaIndietro() {
        ImportConnection importata = ImportConnection
                .configure(UTENTE, IngestionSource.BUDGET_BAKERS, TOKEN, true, OGGI)
                .importedThrough(OGGI);

        // Un import che rilegge una finestra gia' coperta e' normale: deve restare fermo.
        assertThat(importata.importedThrough(OGGI.minusDays(10))).isEqualTo(importata);
        assertThat(importata.importedThrough(OGGI)).isEqualTo(importata);
    }

    @Test
    void ricollegareNonAzzeraIlSegnaposto() {
        ImportConnection importata = ImportConnection
                .configure(UTENTE, IngestionSource.BUDGET_BAKERS, TOKEN, true, OGGI)
                .importedThrough(OGGI);

        assertThat(importata.reconfigure(ALTRO_TOKEN, true, OGGI.plusDays(1)).lastRecordDate())
                .isEqualTo(OGGI);
    }

    @Test
    void rifiutaUnaSorgenteNonAncoraDisponibile() {
        assertThatThrownBy(() ->
                ImportConnection.configure(UTENTE, IngestionSource.PSD2, TOKEN, true, OGGI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("psd2")
                .hasMessageContaining("non è ancora disponibile");
    }
}
