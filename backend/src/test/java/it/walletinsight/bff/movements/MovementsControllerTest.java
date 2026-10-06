package it.walletinsight.bff.movements;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.Classification;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.movements.domain.MovementId;
import it.walletinsight.core.movements.domain.MovementKind;
import it.walletinsight.core.movements.domain.MovementSort;
import it.walletinsight.core.movements.domain.MovementTotals;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.users.application.UserService;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Contratto dei movimenti: `Page<T>` nostra, centesimi interi, enum in
 * kebab-case, e soprattutto le tre coppie valore-dell'utente / valore-della-
 * sorgente esposte entrambe, con il booleano che dice quale si sta guardando.
 */
@WebMvcTest(MovementsController.class)
class MovementsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final MovementId MOVIMENTO =
            MovementId.of(UUID.fromString("019b4c60-2f4f-7c03-8e44-2b8f6d3c0e72"));
    private static final AccountId CONTO =
            AccountId.of(UUID.fromString("019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61"));
    private static final CategoryId RISTORANTI =
            CategoryId.of(UUID.fromString("019b4c60-2f4f-7d04-9f55-3c9a7e4d1f83"));
    private static final CategoryId SPESA =
            CategoryId.of(UUID.fromString("019b4c60-2f4f-7e05-8a66-4dab8f5e2a94"));
    private static final AccountId ALTRO_CONTO =
            AccountId.of(UUID.fromString("019b4c60-2f4f-7f06-8b77-5ebc9a6f3b05"));
    private static final String URL = "/api/users/" + UTENTE + "/movements";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovementService service;

    @MockitoBean
    private UserService users;

    /**
     * I totali accompagnano ogni elenco, quindi ogni test ne ha bisogno: stanno
     * qui una volta sola invece che ripetuti in ognuno, dove sarebbero rumore.
     */
    @BeforeEach
    void iTotaliDelFiltro() {
        given(service.totals(any(), any())).willReturn(
                new MovementTotals(Money.of(0L), Money.of(999L), Money.of(-999L), 1L));
        given(users.getSettings(UTENTE)).willReturn(
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
    }

    @Test
    void elencaIMovimentiConUnValorePerCampo() {
        given(service.listMovements(eq(UTENTE), any(), any(), any())).willReturn(
                unaPagina(importato().describedAs("Spesa della settimana")));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {
                          "page": {
                            "items": [{
                            "id": "019b4c60-2f4f-7c03-8e44-2b8f6d3c0e72",
                            "accountId": "019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61",
                            "source": "budget-bakers",
                            "amountCents": -999,
                            "currencyCode": "EUR",
                            "date": "2026-09-18",
                            "direction": "expense",
                            "state": "wait-for-assign",
                            "description": "Spesa della settimana",
                            "counterParty": "Conad",
                            "categoryId": "019b4c60-2f4f-7d04-9f55-3c9a7e4d1f83"
                            }],
                            "total": 1,
                            "index": 0,
                            "size": 25,
                            "pageCount": 1
                          },
                          "totals": {
                            "incomeCents": 0,
                            "expensesCents": 999,
                            "netCents": -999,
                            "currencyCode": "EUR",
                            "count": 1
                          }
                        }""");
    }

    @Test
    void laCategoriaMandataEQuellaSuCuiIlMovimentoSta() {
        given(service.listMovements(eq(UTENTE), any(), any(), any()))
                .willReturn(unaPagina(importato().classifiedAs(SPESA)));

        // Una sola categoria nella risposta, e nessuna traccia di dove stava prima:
        // la traccia grezza serve al riaggancio, non a chi guarda.
        assertThat(mockMvc.get().uri(URL)).hasStatusOk().bodyJson()
                .extractingPath("$.page.items[0].categoryId").isEqualTo(SPESA.toString());
    }

    @Test
    void iFiltriArrivanoAlDominio() {
        given(service.listMovements(eq(UTENTE), any(), any(), any())).willReturn(unaPagina(importato()));

        assertThat(mockMvc.get().uri(URL + "?from=2026-09-01&to=2026-09-30&accountId=" + CONTO
                + "&categoryId=" + SPESA + "&type=expense&page=2&size=10")).hasStatusOk();

        ArgumentCaptor<MovementFilter> filtro = ArgumentCaptor.forClass(MovementFilter.class);
        ArgumentCaptor<PageRequest> pagina = ArgumentCaptor.forClass(PageRequest.class);
        then(service).should()
                .listMovements(eq(UTENTE), filtro.capture(), any(), pagina.capture());
        assertThat(filtro.getValue().period())
                .isEqualTo(new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        assertThat(filtro.getValue().accountIds()).containsExactly(CONTO);
        assertThat(filtro.getValue().categoryIds()).containsExactly(SPESA);
        assertThat(filtro.getValue().kinds()).containsExactly(MovementKind.EXPENSE);
        assertThat(pagina.getValue()).isEqualTo(PageRequest.of(2, 10));
    }

    @Test
    void piuValoriDelloStessoFiltroSonoUnOr() {
        given(service.listMovements(eq(UTENTE), any(), any(), any())).willReturn(unaPagina(importato()));

        assertThat(mockMvc.get().uri(
                URL + "?accountId=" + CONTO + "&accountId=" + ALTRO_CONTO
                        + "&type=income&type=transfer")).hasStatusOk();

        ArgumentCaptor<MovementFilter> filtro = ArgumentCaptor.forClass(MovementFilter.class);
        then(service).should().listMovements(eq(UTENTE), filtro.capture(), any(), any());
        // Un movimento sta su un conto solo: intersecare i due non darebbe mai niente.
        assertThat(filtro.getValue().accountIds()).containsExactly(CONTO, ALTRO_CONTO);
        assertThat(filtro.getValue().kinds())
                .containsExactlyInAnyOrder(MovementKind.INCOME, MovementKind.TRANSFER);
    }

    @Test
    void lOrdinamentoArrivaAlDominio() {
        given(service.listMovements(eq(UTENTE), any(), any(), any())).willReturn(unaPagina(importato()));

        assertThat(mockMvc.get().uri(URL + "?sortBy=counter-party&sortDirection=asc"))
                .hasStatusOk();

        ArgumentCaptor<MovementSort> ordinamento = ArgumentCaptor.forClass(MovementSort.class);
        then(service).should()
                .listMovements(eq(UTENTE), any(), ordinamento.capture(), any());
        assertThat(ordinamento.getValue())
                .isEqualTo(new MovementSort(
                        MovementSort.Field.COUNTER_PARTY, MovementSort.Direction.ASC));
    }

    @Test
    void senzaOrdinamentoSiParteDaiPiuRecenti() {
        given(service.listMovements(eq(UTENTE), any(), any(), any())).willReturn(unaPagina(importato()));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk();

        ArgumentCaptor<MovementSort> ordinamento = ArgumentCaptor.forClass(MovementSort.class);
        then(service).should().listMovements(eq(UTENTE), any(), ordinamento.capture(), any());
        assertThat(ordinamento.getValue()).isEqualTo(MovementSort.NEWEST_FIRST);
    }

    @Test
    void unFiltroInesistenteEUnaRichiestaSbagliata() {
        // Rispondere "tutto" a `?type=uscite` sembrerebbe funzionare, ed e' il modo
        // peggiore di scoprire un errore di battitura.
        assertThat(mockMvc.get().uri(URL + "?type=uscite")).hasStatus(400);
    }

    @Test
    void unPeriodoConUnSoloEstremoEUnaRichiestaSbagliata() {
        // Un elenco che mostra un periodo diverso da quello chiesto e' peggio di un errore.
        assertThat(mockMvc.get().uri(URL + "?from=2026-09-01")).hasStatus(400);
    }

    @Test
    void riscrivereLaDescrizioneNonToccaGliAltriDueCampi() {
        given(service.updateMovement(any(), any(), any(), any(), any()))
                .willReturn(importato().describedAs("Spesa della settimana"));

        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description": "Spesa della settimana"}""")).hasStatusOk();

        // Gli altri due arrivano come "non toccare", non come "azzera".
        then(service).should()
                .updateMovement(UTENTE, MOVIMENTO, "Spesa della settimana", null, null);
    }

    @Test
    void unaDescrizioneSvuotataArrivaVuotaENonCancellata() {
        given(service.updateMovement(any(), any(), any(), any(), any())).willReturn(importato());

        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description": "", "categoryId": ""}""")).hasStatusOk();

        // La stringa vuota e' il valore che l'utente ha scelto, non piu' la
        // richiesta di rimettere il campo com'era: svuotare un campo deve
        // lasciarlo vuoto, altrimenti il testo importato torna al salvataggio dopo.
        // Una categoria vuota invece non tocca niente: toglierla non si puo'.
        then(service).should().updateMovement(UTENTE, MOVIMENTO, "", null, null);
    }

    @Test
    void correggereUnMovimentoDaContabilizzareEUn409() {
        // Non un 400: la richiesta non ha niente di sbagliato, e la stessa fra un
        // giorno funzionera'. E' lo stato del movimento a non ammetterla adesso.
        given(service.updateMovement(any(), any(), any(), any(), any()))
                .willThrow(new IllegalStateException("Questo movimento è ancora da contabilizzare."));

        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"description": "Spesa"}""")).hasStatus(409);
    }

    @Test
    void riclassificareArrivaComeUnaCategoriaDaAssegnare() {
        given(service.updateMovement(any(), any(), any(), any(), any()))
                .willReturn(importato().classifiedAs(SPESA));

        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\": \"" + SPESA + "\"}")).hasStatusOk();

        then(service).should().updateMovement(UTENTE, MOVIMENTO, null, null, SPESA);
    }

    @Test
    void indicareIlPagatoreEUnaModificaLegittima() {
        given(service.updateMovement(any(), any(), any(), any(), any()))
                .willReturn(importato().attributedTo("Pirru"));

        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"counterParty": "Pirru"}""")).hasStatusOk()
                .bodyJson().extractingPath("$.counterParty").isEqualTo("Pirru");
    }

    @Test
    void unaCategoriaMalformataEUnaRichiestaSbagliata() {
        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": "non-un-uuid"}""")).hasStatus(400);
    }

    @Test
    void importoEStatoNonSiPossonoModificare() {
        given(service.updateMovement(any(), any(), any(), any(), any())).willReturn(importato());

        // Sono fatti avvenuti altrove: il campo non esiste nel corpo, e mandarlo non
        // fa niente. Il prossimo import li riscriverebbe comunque.
        assertThat(mockMvc.patch().uri(URL + "/" + MOVIMENTO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amountCents": 1, "state": "void"}""")).hasStatusOk()
                .bodyJson().extractingPath("$.amountCents").isEqualTo(-999);
    }

    private static Page<Movement> unaPagina(Movement movimento) {
        return Page.of(List.of(movimento), 1L, PageRequest.firstPage());
    }

    private static Movement importato() {
        return new Movement(MOVIMENTO, UTENTE, CONTO, IngestionSource.BUDGET_BAKERS, "rec-1",
                Money.of(-999L), istante(LocalDate.of(2026, 9, 18)), MovementDirection.EXPENSE,
                MovementState.WAIT_FOR_ASSIGN,
                "PAGAMENTO DEBINT",
                "Conad",
                Classification.fromSource(RISTORANTI, "cat-1", "Ristoranti"),
                null);
    }

    /** La mezzanotte UTC del giorno: com'è codificata la maggior parte dei movimenti della sorgente. */
    private static Instant istante(LocalDate giorno) {
        return giorno.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
