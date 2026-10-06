package it.walletinsight.core.ingestion.domain;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il segnaposto che torna al backend: e' la data del movimento piu' recente
 * *letto*, non l'estremo della finestra richiesta. La differenza conta quando
 * un giro non porta nulla.
 */
class ImportedMovementsTest {

    private static final LocalDate DA = LocalDate.of(2026, 9, 1);
    private static final LocalDate A_ESCLUSO = LocalDate.of(2026, 9, 22);
    private static final UserId UTENTE = UserId.newId();
    private static final AccountId CONTO = AccountId.newId();

    @Test
    void ilSegnapostoEIlMovimentoPiuRecente() {
        ImportedMovements imported = new ImportedMovements(DA, A_ESCLUSO, List.of(
                movimento("rec-1", LocalDate.of(2026, 9, 3)),
                movimento("rec-2", LocalDate.of(2026, 9, 18)),
                movimento("rec-3", LocalDate.of(2026, 9, 10))));

        assertThat(imported.lastRecordDate()).contains(LocalDate.of(2026, 9, 18));
        assertThat(imported.size()).isEqualTo(3);
    }

    @Test
    void senzaMovimentiNonCEUnSegnapostoDaSpostare() {
        ImportedMovements imported = new ImportedMovements(DA, A_ESCLUSO, List.of());

        // Vuoto e non "l'estremo della finestra": altrimenti un movimento registrato
        // in ritardo su un giorno gia' superato resterebbe fuori per sempre.
        assertThat(imported.lastRecordDate()).isEmpty();
        assertThat(imported.isEmpty()).isTrue();
    }

    private static Movement movimento(String id, LocalDate data) {
        return Movement.imported(UTENTE, CONTO, IngestionSource.BUDGET_BAKERS, id,
                Money.of(-999L), Money.of(-999L), istante(data), MovementDirection.EXPENSE, MovementState.CLEARED,
                null, null, it.walletinsight.core.movements.domain.Classification.none(), null);
    }

    /** La mezzanotte UTC del giorno: com'è codificata la maggior parte dei movimenti della sorgente. */
    private static Instant istante(LocalDate giorno) {
        return giorno.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
