package it.walletinsight.core.ingestion.application;

import it.walletinsight.core.accounts.application.AccountService;
import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportOutcome;
import it.walletinsight.core.ingestion.domain.ImportSource;
import it.walletinsight.core.ingestion.domain.ImportedMovements;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.ingestion.domain.RejectedCredentialsException;
import it.walletinsight.core.ingestion.domain.SourceUnavailableException;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.Classification;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.movements.domain.MovementSort;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * Il caso d'uso che chiamano l'onboarding, l'import a mano e le schedulazioni
 * delle sorgenti. La sorgente è un doppio: qui si verifica l'orchestrazione, cioè
 * l'ordine (prima i conti e le categorie, poi i movimenti che su entrambi
 * poggiano), il segnaposto e il fatto che una sorgente caduta non porti giù le
 * altre.
 */
class ImportServiceTest extends it.walletinsight.support.AbstractDatabaseTest {

    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");

    @Autowired
    private ImportService service;

    @Autowired
    private ImportConnectionService connections;

    @Autowired
    private AccountService accounts;

    @Autowired
    private CategoryService categorie;

    @Autowired
    private MovementService movimenti;

    @Autowired
    private it.walletinsight.core.users.domain.UserRepository users;

    @MockitoBean
    private ImportSource sorgente;

    private UserId marta;

    private UserId utenteConConnessione() {
        it.walletinsight.core.users.domain.User user = new it.walletinsight.core.users.domain.User(
                UserId.newId(), "Marta", null, null,
                new it.walletinsight.core.users.domain.UserSettings(
                        it.walletinsight.core.users.domain.UserLanguage.IT, "Europe/Rome",
                        it.walletinsight.core.users.domain.DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        connections.configure(user.id(), IngestionSource.BUDGET_BAKERS, TOKEN, true);
        return user.id();
    }

    @Test
    void importaPrimaIContiPoiIMovimentiESpostaIlSegnaposto() {
        marta = utenteConConnessione();
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willAnswer(i -> List.of(conto(
                ((ImportConnection) i.getArgument(0)).userId(), "acc-1", "Credem")));
        given(sorgente.readCategories(any())).willAnswer(i -> List.of(categoria("cat-1", "Ristoranti")));
        // I movimenti si costruiscono dai conti e dalle categorie *appena allineati*:
        // e' esattamente quello che fa un adapter vero, e verifica che l'ordine sia
        // rispettato — se arrivassero dopo, qui non ci sarebbe nessun identificatore.
        given(sorgente.readMovements(any(), any(), any())).willAnswer(i -> new ImportedMovements(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 23),
                List.of(movimento(primoConto(i.getArgument(1)), primaCategoria(i.getArgument(2)),
                        "rec-1", LocalDate.of(2026, 9, 18)))));

        List<ImportOutcome> esiti = service.importFor(marta);

        assertThat(esiti).singleElement().satisfies(e -> {
            assertThat(e.succeeded()).isTrue();
            assertThat(e.source()).isEqualTo(IngestionSource.BUDGET_BAKERS);
            assertThat(e.accountsSynced()).isEqualTo(1);
            assertThat(e.categoriesSynced()).isEqualTo(1);
            assertThat(e.movementsRead()).isEqualTo(1);
            assertThat(e.movementsSaved()).isEqualTo(1);
            assertThat(e.lastRecordDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        });
        // I conti sono davvero finiti in tabella, con l'identificatore di Wallet Insights.
        assertThat(accounts.listAccounts(marta)).singleElement()
                .extracting(Account::externalId).isEqualTo("acc-1");
        // E il movimento pesa sul saldo del conto su cui e' stato agganciato.
        assertThat(movimenti.movedByAccount(marta).values()).containsExactly(-999L);
        // Il primo import da' l'elenco di base, e il movimento finisce nella categoria
        // di Wallet Insights a cui quella della sorgente e' agganciata.
        assertThat(categorie.listSourceCategories(marta)).singleElement().satisfies(c -> {
            assertThat(categorie.findCategory(marta, c.category()).orElseThrow().name())
                    .isEqualTo("Ristoranti e asporto");
            assertThat(movimenti
                    .listMovements(marta, MovementFilter.all(), MovementSort.NEWEST_FIRST, PageRequest.firstPage())
                    .items().getFirst().classification().category()).isEqualTo(c.category());
        });
        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::lastRecordDate).isEqualTo(LocalDate.of(2026, 9, 18));
    }

    @Test
    void rigirareLoStessoImportNonRiscriveNulla() {
        marta = utenteConConnessione();
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willAnswer(i -> List.of(conto(
                ((ImportConnection) i.getArgument(0)).userId(), "acc-1", "Credem")));
        given(sorgente.readCategories(any())).willAnswer(i -> List.of(categoria("cat-1", "Ristoranti")));
        given(sorgente.readMovements(any(), any(), any())).willAnswer(i -> new ImportedMovements(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 23),
                List.of(movimento(primoConto(i.getArgument(1)), primaCategoria(i.getArgument(2)),
                        "rec-1", LocalDate.of(2026, 9, 18)))));

        service.importFor(marta);
        List<ImportOutcome> secondo = service.importFor(marta);

        // La finestra rilegge di proposito il giorno gia' importato: ripresentare
        // movimenti gia' visti e' la norma, e "letti 1, salvati 0" e' l'esito giusto.
        assertThat(secondo).singleElement().satisfies(e -> {
            assertThat(e.movementsRead()).isEqualTo(1);
            assertThat(e.movementsSaved()).isZero();
        });
        assertThat(movimenti.countImported(marta, IngestionSource.BUDGET_BAKERS)).isEqualTo(1L);
    }

    @Test
    void unGiroAVuotoNonSpostaIlSegnaposto() {
        marta = utenteConConnessione();
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willReturn(List.of());
        given(sorgente.readCategories(any())).willReturn(List.of());
        given(sorgente.readMovements(any(), any(), any())).willReturn(new ImportedMovements(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 23), List.of()));

        assertThat(service.importFor(marta)).singleElement().satisfies(e -> {
            assertThat(e.succeeded()).isTrue();
            assertThat(e.movementsRead()).isZero();
            assertThat(e.lastRecordDate()).isNull();
        });
        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::neverImported).isEqualTo(true);
        // Ma l'esecuzione e' registrata lo stesso: e' la differenza fra "controllato
        // ora, niente di nuovo" e "non si sa da quando".
        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::lastRunAt).isNotNull();
    }

    @Test
    void unaSorgenteCadutaNonRisultaSincronizzata() {
        marta = utenteConConnessione();
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any()))
                .willThrow(new IllegalStateException("BudgetBakers ha risposto 401"));

        service.importFor(marta);

        // Un tentativo fallito non e' una sincronizzazione: registrarlo comunque
        // farebbe leggere "aggiornato" a chi non lo e'.
        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::lastRunAt).isNull();
    }

    @Test
    void unaSorgenteCadutaDiventaUnEsitoConUnMessaggioPerLUtente() {
        marta = utenteConConnessione();
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willThrow(new SourceUnavailableException(
                "BudgetBakers non è raggiungibile o non ha risposto in tempo.",
                new IllegalStateException("BudgetBakers ha risposto 503: <html>")));

        assertThat(service.importFor(marta)).singleElement().satisfies(e -> {
            assertThat(e.succeeded()).isFalse();
            assertThat(e.failure())
                    .isEqualTo("BudgetBakers non è raggiungibile o non ha risposto in tempo.");
        });
    }

    @Test
    void unErroreInternoNonArrivaAlClient() {
        marta = utenteConConnessione();
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willThrow(
                new IllegalStateException("Valore non ammesso in accounts.kind: 'x'"));

        assertThat(service.importFor(marta)).singleElement()
                .extracting(ImportOutcome::failure).isEqualTo(ImportService.GENERIC_FAILURE);
    }

    @Test
    void unaConnessioneInPausaNonVieneImportata() {
        marta = utenteConConnessione();
        connections.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, false);

        assertThat(service.importFor(marta)).isEmpty();
    }

    @Test
    void unUtenteSconosciutoNonSiPuoImportare() {
        assertThatThrownBy(() -> service.importFor(UserId.newId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Utente");
    }

    @Test
    void ilGiroDiUnaSorgenteImportaSoloChiLHaCollegataEAttiva() {
        UserId primo = utenteConConnessione();
        UserId secondo = utenteConConnessione();
        UserId inPausa = utenteConConnessione();
        connections.configure(inPausa, IngestionSource.BUDGET_BAKERS, TOKEN, false);
        UserId senzaSorgenti = utenteConConnessione();
        connections.disconnect(senzaSorgenti, IngestionSource.BUDGET_BAKERS);
        sorgenteVuota();

        Map<UserId, ImportOutcome> esiti = service.importForAllUsers(IngestionSource.BUDGET_BAKERS);

        assertThat(esiti.keySet()).containsExactly(primo, secondo);
        assertThat(esiti.values()).allSatisfy(e -> {
            assertThat(e.succeeded()).isTrue();
            assertThat(e.source()).isEqualTo(IngestionSource.BUDGET_BAKERS);
        });
    }

    @Test
    void ilGiroDiUnaSorgenteNonTrascinaLeAltre() {
        utenteConConnessione();
        sorgenteVuota();

        assertThat(service.importForAllUsers(IngestionSource.PSD2)).isEmpty();
    }

    @Test
    void nelGiroUnaSorgenteCadutaPerUnUtenteNonFermaGliAltri() {
        UserId primo = utenteConConnessione();
        UserId secondo = utenteConConnessione();
        sorgenteVuota();
        given(sorgente.readAccounts(any())).willAnswer(i -> {
            if (((ImportConnection) i.getArgument(0)).userId().equals(primo)) {
                throw new IllegalStateException("BudgetBakers ha risposto 401");
            }
            return List.of();
        });

        Map<UserId, ImportOutcome> esiti = service.importForAllUsers(IngestionSource.BUDGET_BAKERS);

        assertThat(esiti.get(primo).succeeded()).isFalse();
        assertThat(esiti.get(primo).failure()).isEqualTo(ImportService.GENERIC_FAILURE);
        assertThat(esiti.get(secondo).succeeded()).isTrue();
    }

    @Test
    void nelGiroUnErroreFuoriDagliEsitiDiventaUnEsitoENonFermaGliAltri() {
        UserId primo = utenteConConnessione();
        UserId secondo = utenteConConnessione();
        sorgenteVuota();
        given(sorgente.source())
                .willThrow(new IllegalStateException("adapter non pronto"))
                .willReturn(IngestionSource.BUDGET_BAKERS);

        Map<UserId, ImportOutcome> esiti = service.importForAllUsers(IngestionSource.BUDGET_BAKERS);

        assertThat(esiti.get(primo).succeeded()).isFalse();
        assertThat(esiti.get(secondo).succeeded()).isTrue();
    }

    @Test
    void ilGiroImportaSoloLeConnessioniScelte() {
        UserId primo = utenteConConnessione();
        UserId secondo = utenteConConnessione();
        sorgenteVuota();

        Map<UserId, ImportOutcome> esiti = service.importForAllUsers(
                IngestionSource.BUDGET_BAKERS, connection -> connection.userId().equals(secondo));

        assertThat(esiti.keySet()).containsExactly(secondo);
        assertThat(connections.listConnections(primo)).singleElement()
                .extracting(ImportConnection::lastRunAt).isNull();
    }

    @Test
    void unTokenRifiutatoRestaSegnatoSullaConnessione() {
        marta = utenteConConnessione();
        sorgenteVuota();
        given(sorgente.readAccounts(any())).willThrow(
                new RejectedCredentialsException("BudgetBakers ha rifiutato il token"));

        assertThat(service.importFor(marta)).singleElement()
                .extracting(ImportOutcome::succeeded).isEqualTo(false);
        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::credentialsRejectedAt).isNotNull();
    }

    @Test
    void unImportRiuscitoCancellaIlRifiuto() {
        marta = utenteConConnessione();
        connections.recordRejectedCredentials(marta, IngestionSource.BUDGET_BAKERS, Instant.now());
        sorgenteVuota();

        service.importFor(marta);

        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::credentialsRejectedAt).isNull();
    }

    @Test
    void unTokenNuovoCancellaIlRifiuto() {
        marta = utenteConConnessione();
        connections.recordRejectedCredentials(marta, IngestionSource.BUDGET_BAKERS, Instant.now());

        connections.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);

        assertThat(connections.listConnections(marta)).singleElement()
                .extracting(ImportConnection::credentialsRejectedAt).isNull();
    }

    @Test
    void quandoLaBancaConfermaIlMovimentoInSospesoLasciaIlPostoAlConfermato() {
        marta = utenteConConnessione();
        LocalDate giorno = LocalDate.now().minusDays(3);
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willAnswer(i -> List.of(conto(
                ((ImportConnection) i.getArgument(0)).userId(), "acc-1", "Credem")));
        given(sorgente.readCategories(any())).willAnswer(i -> List.of(categoria("cat-1", "Ristoranti")));
        given(sorgente.readMovements(any(), any(), any())).willAnswer(i -> new ImportedMovements(
                giorno, LocalDate.now().plusDays(1),
                List.of(inSospeso(primoConto(i.getArgument(1)), primaCategoria(i.getArgument(2)),
                        "pending-1", giorno))));
        service.importFor(marta);

        willAnswer(i -> new ImportedMovements(
                giorno, LocalDate.now().plusDays(1),
                List.of(movimento(primoConto(i.getArgument(1)), primaCategoria(i.getArgument(2)),
                        "confermato-1", giorno))))
                .given(sorgente).readMovements(any(), any(), any());
        service.importFor(marta);

        assertThat(movimenti.listMovements(marta, MovementFilter.all(), MovementSort.NEWEST_FIRST,
                PageRequest.firstPage()).items()).extracting(Movement::externalId)
                .containsExactly("confermato-1");
        assertThat(movimenti.movedByAccount(marta).values()).containsExactly(-999L);
    }

    private void sorgenteVuota() {
        given(sorgente.source()).willReturn(IngestionSource.BUDGET_BAKERS);
        given(sorgente.readAccounts(any())).willReturn(List.of());
        given(sorgente.readCategories(any())).willReturn(List.of());
        given(sorgente.readMovements(any(), any(), any())).willReturn(new ImportedMovements(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 23), List.of()));
    }

    private static Account conto(UserId userId, String externalId, String nome) {
        return Account.imported(userId, IngestionSource.BUDGET_BAKERS, externalId, nome,
                AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR, Money.of(0L), null, false, false);
    }

    /** Il conto appena allineato, quello con l'identificatore che il database conosce. */
    @SuppressWarnings("unchecked")
    private static Account primoConto(Object allineati) {
        return ((List<Account>) allineati).getFirst();
    }

    private static ImportedCategory categoria(String externalId, String nome) {
        return new ImportedCategory(externalId, nome, "food_and_drinks", null, "cibo/ristoranti");
    }

    /** La categoria di Wallet Insights a cui l'import ha appena agganciato «cat-1». */
    @SuppressWarnings("unchecked")
    private static CategoryId primaCategoria(Object agganci) {
        return ((Map<String, CategoryId>) agganci).get("cat-1");
    }

    private static Movement movimento(
            Account conto, CategoryId categoria, String id, LocalDate data) {
        return Movement.imported(conto.userId(), conto.id(), IngestionSource.BUDGET_BAKERS, id,
                Money.of(-999L), Money.of(-999L), istante(data), MovementDirection.EXPENSE, MovementState.CLEARED,
                null, null,
                Classification.fromSource(categoria, "cat-1", "Ristoranti"),
                null);
    }

    private static Movement inSospeso(
            Account conto, CategoryId categoria, String id, LocalDate data) {
        return Movement.imported(conto.userId(), conto.id(), IngestionSource.BUDGET_BAKERS, id,
                Money.of(-999L), Money.of(-999L), istante(data), MovementDirection.EXPENSE, MovementState.UNCLEARED,
                null, null,
                Classification.fromSource(categoria, "cat-1", "Ristoranti"),
                null);
    }

    /** La mezzanotte UTC del giorno: com'è codificata la maggior parte dei movimenti della sorgente. */
    private static Instant istante(LocalDate giorno) {
        return giorno.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
