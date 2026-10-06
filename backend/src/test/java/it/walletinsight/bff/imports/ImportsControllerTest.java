package it.walletinsight.bff.imports;

import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportOutcome;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * Contratto di "aggiorna ora": forma JSON, kebab-case della sorgente e — la cosa
 * che conta — il fatto che una sorgente caduta non faccia fallire la richiesta.
 */
@WebMvcTest(ImportsController.class)
class ImportsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final String URL = "/api/users/" + UTENTE + "/imports";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private ImportService imports;

    @Test
    void restituisceUnEsitoPerSorgente() {
        given(imports.importFor(UTENTE)).willReturn(List.of(ImportOutcome.succeeded(
                IngestionSource.BUDGET_BAKERS, 6, 70, 2, 2, LocalDate.of(2026, 9, 19))));

        assertThat(mockMvc.post().uri(URL)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{
                          "source": "budget-bakers",
                          "succeeded": true,
                          "accountsSynced": 6,
                          "categoriesSynced": 70,
                          "movementsRead": 2,
                          "movementsSaved": 2,
                          "lastRecordDate": "2026-09-19"
                        }]""");
    }

    @Test
    void unAggiornamentoSenzaNovitaSiVedeDaiDueNumeri() {
        // La finestra rilegge di proposito il giorno gia' importato: "letti 40,
        // salvati 0" e' l'esito normale, e va distinto da "non ha funzionato".
        given(imports.importFor(UTENTE)).willReturn(List.of(ImportOutcome.succeeded(
                IngestionSource.BUDGET_BAKERS, 6, 70, 40, 0, LocalDate.of(2026, 9, 19))));

        assertThat(mockMvc.post().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[0].movementsSaved").isEqualTo(0);
    }

    @Test
    void unaSorgenteCadutaNonFaFallireLaRichiesta() {
        // Se una su due non risponde, l'utente deve comunque vedere che l'altra e' andata:
        // un 500 nasconderebbe il lavoro riuscito.
        given(imports.importFor(UTENTE)).willReturn(List.of(
                ImportOutcome.succeeded(IngestionSource.BUDGET_BAKERS, 6, 70, 2, 2, LocalDate.of(2026, 9, 19)),
                ImportOutcome.failed(IngestionSource.PSD2, "BudgetBakers ha risposto 401")));

        assertThat(mockMvc.post().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[1].succeeded").isEqualTo(false);
    }

    @Test
    void unSegnapostoAssenteVieneOmessoDalJson() {
        given(imports.importFor(UTENTE)).willReturn(List.of(
                ImportOutcome.succeeded(IngestionSource.BUDGET_BAKERS, 6, 70, 0, 0, null)));

        // `default-property-inclusion: non_null`: assente, non null.
        assertThat(mockMvc.post().uri(URL)).hasStatusOk()
                .bodyJson().doesNotHavePath("$[0].lastRecordDate");
    }

    @Test
    void senzaConnessioniLEsitoEUnaListaVuota() {
        given(imports.importFor(UTENTE)).willReturn(List.of());

        assertThat(mockMvc.post().uri(URL)).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void unUtenteSconosciutoEUn404() {
        willThrow(new ResourceNotFoundException("Utente", UTENTE.toString()))
                .given(imports).importFor(UTENTE);

        assertThat(mockMvc.post().uri(URL)).hasStatus(404);
    }
}
