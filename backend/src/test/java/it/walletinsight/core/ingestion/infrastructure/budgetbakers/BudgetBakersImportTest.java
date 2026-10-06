package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static it.walletinsight.core.ingestion.infrastructure.budgetbakers.BudgetBakersImport.daAggiornare;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BudgetBakersImportTest {

    private static final Instant AVVIO = Instant.parse("2026-10-05T18:00:00Z");

    @Test
    void accesaDopoIlGiroDelleCinqueImportaComunque() {
        var ultimoImport = Instant.parse("2026-10-05T03:00:05Z"); // oggi, 5:00 a Roma

        assertThat(daAggiornare(importataIl(ultimoImport), AVVIO)).isTrue();
    }

    @Test
    void unRiavvioACortaDistanzaNonImporta() {
        var ultimoImport = AVVIO.minusSeconds(5 * 60);

        assertThat(daAggiornare(importataIl(ultimoImport), AVVIO)).isFalse();
    }

    @Test
    void allaSogliaEsattaImporta() {
        var ultimoImport = AVVIO.minus(BudgetBakersImport.INTERVALLO_MINIMO);

        assertThat(daAggiornare(importataIl(ultimoImport), AVVIO)).isTrue();
    }

    @Test
    void unaConnessioneMaiImportataSiImportaSempre() {
        assertThat(daAggiornare(importataIl(null), AVVIO)).isTrue();
    }

    @Test
    void unTokenIndecifrabileNonImpedisceLAvvio() {
        var imports = mock(ImportService.class);
        when(imports.importForAllUsers(eq(IngestionSource.BUDGET_BAKERS), any()))
                .thenThrow(new CorruptedDataException("Segreto cifrato non decifrabile con la chiave corrente.", null));
        var job = new BudgetBakersImport(imports, "0 0 5 * * *");

        assertThatNoException().isThrownBy(job::importaAllAvvio);
    }

    @Test
    void conLaSchedulazioneSpentaLAvvioNonImporta() {
        var imports = mock(ImportService.class);

        new BudgetBakersImport(imports, "-").importaAllAvvio();

        verify(imports, never()).importForAllUsers(any(), any());
    }

    private static ImportConnection importataIl(Instant lastRunAt) {
        return new ImportConnection(ImportConnectionId.newId(), UserId.newId(),
                IngestionSource.BUDGET_BAKERS,
                new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234"),
                true, LocalDate.now(), null, lastRunAt);
    }
}
