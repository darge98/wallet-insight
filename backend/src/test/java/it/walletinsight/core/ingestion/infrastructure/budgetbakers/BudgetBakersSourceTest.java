package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.core.ingestion.domain.ImportedMovements;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Quale finestra chiede l'adapter, e da dove.
 *
 * È il test che mancava quando la finestra iniziale è stata accorciata a tre
 * mesi: nessuno si è rotto, e il danno si è visto solo nei saldi — il saldo è il
 * saldo iniziale del conto più la somma di *tutti* i movimenti, quindi tagliare
 * lo storico non perde righe vecchie, sbaglia il numero di oggi (misurato:
 * PayPal 4531,60 invece di 0). Qui la regola è fissata: primo giro dall'origine,
 * poi dal segnaposto, ma mai meno degli ultimi tre mesi.
 */
class BudgetBakersSourceTest {

    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");
    /** Domani, escluso: i movimenti pianificati hanno date future e non sono accaduti. */
    private static final LocalDate DOMANI = LocalDate.now().plusDays(1);

    private final BudgetBakersClient client = mock(BudgetBakersClient.class);
    private final BudgetBakersSource source = new BudgetBakersSource(
            client, mock(AccountMapper.class), mock(CategoryMapper.class), mock(MovementMapper.class));

    private static ImportConnection maiImportata() {
        return new ImportConnection(
                ImportConnectionId.newId(), UserId.newId(), IngestionSource.BUDGET_BAKERS,
                TOKEN, true, LocalDate.now());
    }

    @Test
    void ilPrimoGiroLeggeDaSempre() {
        given(client.recordsBetween(any(), any(), any())).willReturn(List.of());

        ImportedMovements letti = source.readMovements(maiImportata(), List.of(), Map.of());

        // L'epoch e non una data recente: qualunque taglio dello storico sposta i saldi.
        verify(client).recordsBetween(TOKEN.value(), LocalDate.EPOCH, DOMANI);
        assertThat(letti.from()).isEqualTo(LocalDate.EPOCH);
    }

    @Test
    void conUnSegnapostoRecenteRileggeGliUltimiTreMesi() {
        given(client.recordsBetween(any(), any(), any())).willReturn(List.of());
        ImportConnection giaLetta = maiImportata().importedThrough(LocalDate.now().minusDays(2));

        source.readMovements(giaLetta, List.of(), Map.of());

        verify(client).recordsBetween(TOKEN.value(), LocalDate.now().minusMonths(3), DOMANI);
    }

    @Test
    void conUnSegnapostoVecchioRipartDalSegnaposto() {
        given(client.recordsBetween(any(), any(), any())).willReturn(List.of());
        LocalDate segnaposto = LocalDate.now().minusMonths(5);
        ImportConnection giaLetta = maiImportata().importedThrough(segnaposto);

        source.readMovements(giaLetta, List.of(), Map.of());

        verify(client).recordsBetween(TOKEN.value(), segnaposto, DOMANI);
    }
}
