package it.walletinsight.core.movements.infrastructure.jdbc;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.accounts.domain.AccountRepository;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.categories.domain.SourceCategory;
import it.walletinsight.core.categories.domain.SourceCategoryId;
import it.walletinsight.core.categories.domain.SourceCategoryRepository;
import it.walletinsight.core.movements.domain.CategorySpending;
import it.walletinsight.core.movements.domain.Classification;
import it.walletinsight.core.movements.domain.CounterPartySpending;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementDirection;
import it.walletinsight.core.movements.domain.ConvertedMovements;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.movements.domain.MovementId;
import it.walletinsight.core.movements.domain.MovementKind;
import it.walletinsight.core.movements.domain.MovementSort;
import it.walletinsight.core.movements.domain.MovementState;
import it.walletinsight.core.movements.domain.MovementTotals;
import it.walletinsight.core.movements.domain.Transfer;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.core.movements.domain.CumulativeExpensePoint;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qui si verifica la scrittura che regge l'import: un `on conflict` che crea o
 * riallinea in un'istruzione sola, e che non tocca le righe già uguali. È la
 * proprietà su cui poggia il rileggere ogni volta il giorno precedente.
 *
 * E si verifica la proprietà nuova, che è la ragione per cui i movimenti hanno
 * smesso di essere di sola sorgente: quella stessa istruzione non deve poter
 * calpestare le tre cose che appartengono all'utente, nemmeno quando la sorgente
 * riscrive tutto il resto della riga.
 */
class JdbcMovementRepositoryTest extends AbstractDatabaseTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");

    private static final LocalDate GIORNO = LocalDate.of(2026, 9, 18);

    @Autowired
    private JdbcMovementRepository repository;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private SourceCategoryRepository sourceCategories;

    private Category macro;

    @Autowired
    private UserRepository users;

    private UserId marta;
    private AccountId credem;
    private CategoryId ristoranti;
    private CategoryId spesa;

    @BeforeEach
    void unUtenteConUnContoEDueCategorie() {
        User user = new User(UserId.newId(), "Marta", null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        marta = user.id();
        Account conto = Account.imported(marta, IngestionSource.BUDGET_BAKERS, "acc-1", "Credem",
                AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR, Money.of(100_000L), null, false, false);
        accounts.insert(conto);
        credem = conto.id();
        ristoranti = categoria("cat-1", "Ristoranti");
        spesa = categoria("cat-2", "Spesa");
    }

    @Test
    void scriveERileggeUnMovimento() {
        Movement movimento = movimento("rec-1", -999L);

        assertThat(repository.upsertAll(List.of(movimento))).isEqualTo(1);

        assertThat(inPeriodo(GIORNO, GIORNO).items()).containsExactly(movimento);
    }

    @Test
    void rigirareLoStessoMovimentoNonScriveNulla() {
        Movement movimento = movimento("rec-1", -999L);
        repository.upsertAll(List.of(movimento));

        // Stesso identificativo nella sorgente: l'`on conflict` riconosce la riga, e
        // la `where ... is distinct from` si accorge che non e' cambiato niente.
        assertThat(repository.upsertAll(List.of(movimento("rec-1", -999L)))).isZero();
        assertThat(jdbc.sql("select count(*) from movements").query(Long.class).single())
                .isEqualTo(1L);
    }

    @Test
    void unMovimentoCorrettoAllaSorgenteVieneRiallineatoSenzaCambiareIdentificatore() {
        Movement primo = movimento("rec-1", -999L);
        repository.upsertAll(List.of(primo));

        assertThat(repository.upsertAll(List.of(movimento("rec-1", -1299L)))).isEqualTo(1);

        assertThat(inPeriodo(GIORNO, GIORNO).items()).singleElement().satisfies(m -> {
            assertThat(m.amount()).isEqualTo(Money.of(-1299L, CurrencyCode.EUR));
            // L'identificatore assegnato la prima volta resta: e' quello a cui si
            // appoggia tutto cio' che viene dopo.
            assertThat(m.id()).isEqualTo(primo.id());
        });
    }

    @Test
    void unImportNonCalpestaCioCheLUtenteHaScritto() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));
        Movement importato = soloMovimento();

        // L'utente riscrive tutte e tre le cose che si correggono.
        repository.updateEditable(importato
                .describedAs("Cena con Pirru")
                .attributedTo("Piadineria")
                .classifiedAs(spesa));

        // E poi la sorgente cambia importo, nota, controparte e categoria.
        Movement dallaSorgente = new Movement(MovementId.newId(), marta, credem,
                IngestionSource.BUDGET_BAKERS, "rec-1", Money.of(-1299L), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.CLEARED,
                "ALTRO TRACCIATO",
                "Amazon",
                Classification.fromSource(ristoranti, "cat-1", "Ristoranti"),
                null);
        assertThat(repository.upsertAll(List.of(dallaSorgente))).isEqualTo(1);

        Movement riletto = soloMovimento();
        // I fatti restano della sorgente e si aggiornano...
        assertThat(riletto.amount()).isEqualTo(Money.of(-1299L, CurrencyCode.EUR));
        // ...mentre i tre campi che l'import scrive una volta sola sono ancora
        // quelli dell'utente: la `on conflict do update` non li nomina.
        assertThat(riletto.description()).isEqualTo("Cena con Pirru");
        assertThat(riletto.counterParty()).isEqualTo("Piadineria");
        assertThat(riletto.classification().category()).isEqualTo(spesa);
        // La traccia grezza della categoria invece si aggiorna: serve al riaggancio.
        assertThat(riletto.classification().sourceName()).isEqualTo("Ristoranti");
    }

    @Test
    void unMovimentoRiscrittoDallUtenteRestaNonToccatoSeLaSorgenteNonCambia() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));
        repository.updateEditable(soloMovimento().describedAs("Cena con Pirru"));

        // Le colonne dell'utente non entrano nel confronto `is distinct from`: un
        // movimento riscritto ma non cambiato alla sorgente e' un movimento non toccato,
        // altrimenti ogni import a vuoto lo conterebbe come lavoro fatto.
        assertThat(repository.upsertAll(List.of(movimento("rec-1", -999L)))).isZero();
        assertThat(soloMovimento().description()).isEqualTo("Cena con Pirru");
    }

    @Test
    void unaDescrizioneSvuotataRestaVuotaAncheRiletta() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));
        repository.updateEditable(soloMovimento().describedAs("Cena con Pirru"));

        repository.updateEditable(soloMovimento().describedAs(""));

        // C'e' una colonna sola, quindi niente puo' riprendersi la scena: il
        // campo resta vuoto, che e' il motivo per cui V11 li ha uniti.
        assertThat(soloMovimento().description()).isNull();
    }

    @Test
    void laModificaDellUtenteNonToccaIFattiDellaSorgente() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));

        // L'aggregato che arriva qui porta anche i campi della sorgente, ma l'update
        // ne nomina tre soli: nemmeno un aggregato costruito male puo' riscriverli.
        Movement manomesso = new Movement(soloMovimento().id(), marta, credem,
                IngestionSource.BUDGET_BAKERS, "rec-1", Money.of(-1L), istante(GIORNO.minusYears(1)),
                MovementDirection.INCOME, MovementState.VOID,
                "Cena",
                null,
                Classification.none(),
                null);
        repository.updateEditable(manomesso);

        Movement riletto = soloMovimento();
        assertThat(riletto.amount()).isEqualTo(Money.of(-999L, CurrencyCode.EUR));
        assertThat(riletto.recordedAt()).isEqualTo(istante(GIORNO));
        assertThat(riletto.state()).isEqualTo(MovementState.CLEARED);
        assertThat(riletto.description()).isEqualTo("Cena");
    }

    @Test
    void riagganciaIMovimentiCheHannoLaTracciaMaNonIlRiferimento() {
        // È il caso che si presenta dopo la migrazione su uno storico già importato,
        // e quello di una categoria creata nella sorgente dopo i suoi movimenti.
        repository.upsertAll(List.of(Movement.imported(marta, credem,
                IngestionSource.BUDGET_BAKERS, "rec-1", Money.of(-999L), Money.of(-999L), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.CLEARED, null, null,
                Classification.fromSource(null, "cat-1", "Ristoranti"), null)));
        assertThat(soloMovimento().classification().category()).isNull();

        assertThat(repository.linkCategoriesFromSource(marta, IngestionSource.BUDGET_BAKERS))
                .isEqualTo(1);

        assertThat(soloMovimento().classification().category()).isEqualTo(ristoranti);
    }

    @Test
    void unireUnaCategoriaSpostaTuttiISuoiMovimenti() {
        repository.upsertAll(List.of(movimento("rec-1", -999L), movimento("rec-2", -500L)));

        assertThat(repository.reassignCategory(marta, ristoranti, spesa)).isEqualTo(2);

        assertThat(repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(), MovementSort.NEWEST_FIRST,
                PageRequest.firstPage()).items())
                .allSatisfy(m -> assertThat(m.classification().category()).isEqualTo(spesa));
    }

    @Test
    void riagganciareUnaCategoriaDellaSorgenteSpostaSoloIMovimentiRimastiDovEranoArrivati() {
        repository.upsertAll(List.of(movimento("rec-1", -999L), movimento("rec-2", -500L)));
        Movement spostato = repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(), MovementSort.NEWEST_FIRST,
                PageRequest.firstPage()).items().getFirst();
        repository.updateEditable(spostato.classifiedAs(spesa));

        assertThat(repository.reassignFromSource(marta, IngestionSource.BUDGET_BAKERS, "cat-1",
                ristoranti, spesa)).isEqualTo(1);
    }

    @Test
    void ilRiaggancioSiSpegneDaSoloQuandoNonCeNienteDaFare() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));

        // Gia' agganciato dall'import: non c'e' niente da riparare, e questo passo
        // gira a ogni giro — deve poter non fare nulla senza costare nulla.
        assertThat(repository.linkCategoriesFromSource(marta, IngestionSource.BUDGET_BAKERS))
                .isZero();
    }

    @Test
    void ilRiaggancioNonTornaSuUnaCategoriaGiaScelta() {
        repository.upsertAll(List.of(Movement.imported(marta, credem,
                IngestionSource.BUDGET_BAKERS, "rec-1", Money.of(-999L), Money.of(-999L), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.CLEARED, null, null,
                Classification.fromSource(null, "cat-1", "Ristoranti"), null)));
        repository.updateEditable(soloMovimento().classifiedAs(spesa));

        repository.linkCategoriesFromSource(marta, IngestionSource.BUDGET_BAKERS);

        // Il riaggancio riempie solo i buchi (`where category_id is null`): qui una
        // categoria c'e' gia', ed e' quella scelta a mano. Non deve essere rimpiazzata
        // da quella che la traccia della sorgente indica.
        assertThat(soloMovimento().classification().category()).isEqualTo(spesa);
    }

    @Test
    void ilSaldoELaSommaDeiMovimentiPerConto() {
        repository.upsertAll(List.of(
                movimento("rec-1", -999L),
                movimento("rec-2", -1L),
                movimento("rec-3", 5_000L)));

        assertThat(repository.sumByAccount(marta)).containsEntry(credem, 4_000L);
    }

    @Test
    void unMovimentoAnnullatoNonPesaSulSaldo() {
        repository.upsertAll(List.of(
                movimento("rec-1", -999L),
                new Movement(MovementId.newId(), marta, credem,
                        IngestionSource.BUDGET_BAKERS, "rec-annullato", Money.of(-100_000L),
                        istante(GIORNO), MovementDirection.EXPENSE, MovementState.VOID,
                        null, null, null, null)));

        // Annullato vuol dire che non e' avvenuto: contarlo darebbe un saldo che non
        // corrisponde a nessun estratto conto.
        assertThat(repository.sumByAccount(marta)).containsEntry(credem, -999L);
    }

    @Test
    void iTotaliSommanoGliImportiInEuroNonQuelliInValuta() {
        repository.upsertAll(List.of(movimento("rec-1", -1_000L), inDollari("rec-2", GIORNO)));

        MovementTotals totali = repository.totals(marta, ZoneOffset.UTC, MovementFilter.all());

        assertThat(totali.expenses().amount()).isEqualTo(1_000L + 4_568L);
        // Il saldo del conto resta invece nella sua valuta: lì non si mescola niente.
        assertThat(repository.sumByAccount(marta)).containsEntry(credem, -1_000L - 5_000L);
    }

    @Test
    void ilCambioDelContoEQuelloDelSuoPrimoMovimento() {
        repository.upsertAll(List.of(inDollari("rec-2", GIORNO), inDollari("rec-1", GIORNO.minusDays(30))));

        ConvertedMovements convertiti = repository.convertedByAccount(marta).get(credem);

        assertThat(convertiti.total()).isEqualTo(Money.of(-9_136L, CurrencyCode.EUR));
        assertThat(convertiti.firstRatio()).isEqualByComparingTo("0.9136");
        assertThat(convertiti.convert(Money.of(10_000L, CurrencyCode.USD)))
                .contains(Money.of(9_136L, CurrencyCode.EUR));
    }

    private Movement inDollari(String externalId, LocalDate data) {
        return Movement.imported(marta, credem, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(-5_000L, CurrencyCode.USD), Money.of(-4_568L, CurrencyCode.EUR), istante(data),
                MovementDirection.EXPENSE, MovementState.CLEARED, null, null,
                Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    @Test
    void ilPeriodoTagliaAgliEstremiInclusi() {
        repository.upsertAll(List.of(
                movimento("rec-1", -100L, GIORNO.minusDays(1)),
                movimento("rec-2", -200L, GIORNO),
                movimento("rec-3", -300L, GIORNO.plusDays(1))));

        assertThat(inPeriodo(GIORNO, GIORNO.plusDays(1)).items())
                .extracting(Movement::externalId)
                // Dal piu' recente: e' l'ordine in cui si guardano i movimenti.
                .containsExactly("rec-3", "rec-2");
    }

    @Test
    void ilGiornoDiUnMovimentoLoDecideIlFusoDiChiGuarda() {
        // Le 00:30 del 1° ottobre a Roma sono ancora il 30 settembre in UTC.
        repository.upsertAll(List.of(movimentoAlle("rec-1", -999L, Instant.parse("2026-09-30T22:30:00Z"))));
        var ottobre = MovementFilter.inPeriod(
                new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(repository.totals(marta, ROMA, ottobre).expenses().amount()).isEqualTo(999L);
        assertThat(repository.totals(marta, ZoneOffset.UTC, ottobre).expenses().amount()).isZero();
    }

    @Test
    void laCurvaRaggruppaPerGiornoNelFusoDiChiGuarda() {
        repository.upsertAll(List.of(
                movimentoAlle("rec-1", -100L, Instant.parse("2026-09-30T22:30:00Z")),
                movimentoAlle("rec-2", -200L, Instant.parse("2026-10-01T10:00:00Z"))));

        assertThat(repository.cumulativeExpenses(marta, ROMA,
                new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))))
                .containsExactly(new CumulativeExpensePoint(LocalDate.of(2026, 10, 1), Money.of(300L)));
    }

    @Test
    void laPaginaDichiaraIlTotaleDiTuttiIMovimentiNonDiQuelliMostrati() {
        repository.upsertAll(List.of(
                movimento("rec-1", -100L, GIORNO.minusDays(2)),
                movimento("rec-2", -200L, GIORNO.minusDays(1)),
                movimento("rec-3", -300L, GIORNO)));

        Page<Movement> pagina =
                repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(), MovementSort.NEWEST_FIRST, PageRequest.of(0, 2));

        assertThat(pagina.items()).extracting(Movement::externalId).containsExactly("rec-3", "rec-2");
        assertThat(pagina.total()).isEqualTo(3L);
        assertThat(pagina.pageCount()).isEqualTo(2);
    }

    @Test
    void ilFiltroPerCategoriaGuardaQuellaMostrataNonQuellaDellaSorgente() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));
        // La sorgente lo mette in Ristoranti; l'utente lo sposta in Spesa.
        repository.updateEditable(soloMovimento().classifiedAs(spesa));

        assertThat(repository.findPage(marta, ZoneOffset.UTC, soloCategoria(spesa),
                MovementSort.NEWEST_FIRST, PageRequest.firstPage()).items())
                .hasSize(1);
        // Cercandolo dov'era prima non si trova: sarebbe incomprensibile il contrario.
        assertThat(repository.findPage(marta, ZoneOffset.UTC, soloCategoria(ristoranti),
                MovementSort.NEWEST_FIRST, PageRequest.firstPage()).items())
                .isEmpty();
    }

    @Test
    void ilGirocontoConservaIlLegameConLAltraGamba() {
        Movement gamba = new Movement(MovementId.newId(), marta,
                credem, IngestionSource.BUDGET_BAKERS, "rec-tr", Money.of(-5_000L), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.CLEARED, null, null, null,
                new Transfer(Transfer.TransferState.PAIRED, "tr-1"));

        repository.upsertAll(List.of(gamba));

        assertThat(inPeriodo(GIORNO, GIORNO).items()).singleElement().satisfies(m -> {
            assertThat(m.isTransfer()).isTrue();
            assertThat(m.transfer().externalId()).isEqualTo("tr-1");
        });
    }

    @Test
    void cancellareIlContoNeCancellaIMovimenti() {
        repository.upsertAll(List.of(movimento("rec-1", -999L)));

        users.deleteById(marta);

        assertThat(jdbc.sql("select count(*) from movements").query(Long.class).single()).isZero();
    }

    @Test
    void leEnumVannoNelDatabaseInKebabCase() {
        repository.upsertAll(List.of(new Movement(
                MovementId.newId(), marta, credem,
                IngestionSource.BUDGET_BAKERS, "rec-1", Money.of(-999L), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.WAIT_FOR_ASSIGN,
                null, null, null, null)));

        assertThat(jdbc.sql("select state from movements").query(String.class).single())
                .isEqualTo("wait-for-assign");
    }

    @Test
    void unoStatoFuoriContrattoNelDatabaseEUnDatoCorrotto() {
        jdbc.sql("""
                insert into movements (id, user_id, account_id, source, external_id, amount_cents,
                                       currency_code, converted_amount_cents, recorded_at, direction, state)
                values (:id, :userId, :accountId, 'budget-bakers', 'rec-x', -100, 'EUR', -100,
                        :date, 'expense', 'quasi-pagato')
                """)
                .param("id", UUID.randomUUID())
                .param("userId", marta.value())
                .param("accountId", credem.value())
                .param("date", java.sql.Timestamp.from(istante(GIORNO)))
                .update();

        assertThatThrownBy(() -> inPeriodo(GIORNO, GIORNO))
                .isInstanceOf(CorruptedDataException.class)
                .hasMessageContaining("state");
    }

    @Test
    void ilFiltroPerNaturaDistingueUscitaEntrataEGiroconto() {
        repository.upsertAll(List.of(
                movimento("uscita", -999L),
                entrata("entrata", 5_000L),
                giroconto("giro", -2_000L)));

        assertThat(perNatura(MovementKind.EXPENSE)).containsExactly("uscita");
        assertThat(perNatura(MovementKind.INCOME)).containsExactly("entrata");
        // Il giroconto vince sul verso: ha direzione "expense" ma non e' una spesa.
        assertThat(perNatura(MovementKind.TRANSFER)).containsExactly("giro");
    }

    @Test
    void piuNatureInsiemeSonoUnOr() {
        repository.upsertAll(List.of(
                movimento("uscita", -999L),
                entrata("entrata", 5_000L),
                giroconto("giro", -2_000L)));

        assertThat(perNatura(MovementKind.INCOME, MovementKind.TRANSFER))
                .containsExactlyInAnyOrder("entrata", "giro");
    }

    @Test
    void piuContiInsiemeSonoUnOrNonUnIntersezione() {
        AccountId contanti = conto("acc-2", "Contanti");
        repository.upsertAll(List.of(
                movimento("su-credem", -999L),
                suConto(contanti, "su-contanti", -500L)));

        // Un movimento sta su un conto solo: in and non tornerebbe mai niente.
        List<Movement> trovati = repository.findPage(marta, ZoneOffset.UTC, new MovementFilter(null, Set.of(), List.of(credem, contanti), List.of()),
                MovementSort.NEWEST_FIRST, PageRequest.firstPage()).items();

        assertThat(trovati).extracting(Movement::externalId)
                .containsExactlyInAnyOrder("su-credem", "su-contanti");
    }

    @Test
    void lOrdinamentoPerImportoGuardaIlValoreAssoluto() {
        repository.upsertAll(List.of(
                movimento("piccola", -100L),
                entrata("grande-entrata", 9_000L),
                movimento("grande-uscita", -5_000L)));

        List<Movement> ordinati = repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(),
                new MovementSort(MovementSort.Field.AMOUNT, MovementSort.Direction.DESC),
                PageRequest.firstPage()).items();

        // "I movimenti piu' grandi" sono i piu' grandi, non tutte le entrate prima
        // di tutte le uscite: senza valore assoluto -5000 verrebbe per ultimo.
        assertThat(ordinati).extracting(Movement::externalId)
                .containsExactly("grande-entrata", "grande-uscita", "piccola");
    }

    @Test
    void lOrdinamentoPerControparteIgnoraLeMaiuscoleEGuardaQuellaMostrata() {
        repository.upsertAll(List.of(
                conControparte("b", "banca"),
                conControparte("a", "Amazon"),
                conControparte("c", "Zara")));
        // L'utente riscrive la controparte di "c": l'ordine deve seguire cio' che vede.
        repository.updateEditable(unoConExternalId("c").attributedTo("Aldi"));

        List<Movement> ordinati = repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(),
                new MovementSort(MovementSort.Field.COUNTER_PARTY, MovementSort.Direction.ASC),
                PageRequest.firstPage()).items();

        // aldi < amazon < banca: "c" passa davanti proprio perche' l'ordine segue il
        // nome riscritto dall'utente, non "Zara" che dice la sorgente.
        assertThat(ordinati).extracting(Movement::externalId).containsExactly("c", "a", "b");
    }

    @Test
    void iTotaliLascianoFuoriIGirocontiDalleSommeMaLiContano() {
        repository.upsertAll(List.of(
                movimento("uscita", -1_000L),
                entrata("entrata", 3_000L),
                giroconto("giro", -50_000L)));

        MovementTotals totali = repository.totals(marta, ZoneOffset.UTC, MovementFilter.all());

        assertThat(totali.income().amount()).isEqualTo(3_000L);
        // Le uscite sono positive, e il giroconto non le gonfia: non e' denaro uscito.
        assertThat(totali.expenses().amount()).isEqualTo(1_000L);
        assertThat(totali.net().amount()).isEqualTo(2_000L);
        // Il conteggio invece li comprende: e' lo stesso numero che pagina l'elenco.
        assertThat(totali.count()).isEqualTo(3L);
        assertThat(repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(), MovementSort.NEWEST_FIRST,
                PageRequest.firstPage()).total()).isEqualTo(totali.count());
    }

    @Test
    void iTotaliRispettanoGliStessiFiltriDellElenco() {
        repository.upsertAll(List.of(
                movimento("dentro", -1_000L, GIORNO),
                movimento("fuori", -9_999L, GIORNO.plusDays(10))));

        MovementFilter soloOggi = MovementFilter.inPeriod(new DateRange(GIORNO, GIORNO));

        assertThat(repository.totals(marta, ZoneOffset.UTC, soloOggi).expenses().amount()).isEqualTo(1_000L);
        assertThat(repository.totals(marta, ZoneOffset.UTC, soloOggi).count()).isEqualTo(1L);
    }

    @Test
    void leUscitePerCategoriaRaggruppanoQuellaMostrata() {
        repository.upsertAll(List.of(
                movimento("a", -1_000L),
                movimento("b", -2_000L),
                entrata("entrata", 9_000L)));
        // Tutte e due su Ristoranti dalla sorgente; una la sposta l'utente su Spesa.
        repository.updateEditable(unoConExternalId("b").classifiedAs(spesa));

        List<CategorySpending> classifica =
                repository.expensesByCategory(marta, ZoneOffset.UTC, oggi(), 5);

        assertThat(classifica).extracting(CategorySpending::categoryId)
                .containsExactly(spesa, ristoranti);
        // Importi positivi, dal maggiore. L'entrata non compare: non e' una spesa.
        assertThat(classifica).extracting(c -> c.total().amount())
                .containsExactly(2_000L, 1_000L);
    }

    @Test
    void laClassificaDelleContropartiLasciaFuoriChiNonNeHa() {
        repository.upsertAll(List.of(
                conControparte("a", "Conad"),
                conControparte("b", "Conad"),
                conControparte("c", null)));

        List<CounterPartySpending> classifica =
                repository.topCounterParties(marta, ZoneOffset.UTC, oggi(), 5);

        // "Senza nome" sarebbe quasi sempre il primo della classifica e non direbbe
        // dove sono andati i soldi: chi non ha controparte resta fuori.
        assertThat(classifica).singleElement().satisfies(voce -> {
            assertThat(voce.counterParty()).isEqualTo("Conad");
            assertThat(voce.count()).isEqualTo(2L);
            assertThat(voce.total().amount()).isEqualTo(1_998L);
        });
    }

    @Test
    void leClassificheRispettanoIlFiltroPerConto() {
        AccountId contanti = conto("acc-2", "Contanti");
        repository.upsertAll(List.of(
                movimento("su-credem", -1_000L),
                suConto(contanti, "su-contanti", -500L)));

        MovementFilter soloContanti = new MovementFilter(
                new DateRange(GIORNO, GIORNO), Set.of(), List.of(contanti), List.of());

        // Accanto a un elenco ristretto a un conto, la classifica deve parlare delle
        // stesse righe: i mille centesimi di Credem qui non ci sono.
        assertThat(repository.expensesByCategory(marta, ZoneOffset.UTC, soloContanti, 5))
                .singleElement()
                .satisfies(voce -> assertThat(voce.total().amount()).isEqualTo(500L));
    }

    private List<String> perNatura(MovementKind... kinds) {
        return repository.findPage(marta, ZoneOffset.UTC, new MovementFilter(null, Set.of(kinds), List.of(), List.of()),
                MovementSort.NEWEST_FIRST, PageRequest.firstPage())
                .items().stream().map(Movement::externalId).toList();
    }

    private Movement unoConExternalId(String externalId) {
        return repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(), MovementSort.NEWEST_FIRST,
                PageRequest.of(0, 200)).items().stream()
                .filter(m -> m.externalId().equals(externalId))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void cancellaIlMovimentoInSospesoCheLaSorgenteNonRestituiscePiu() {
        repository.upsertAll(List.of(inSospeso(credem, "pending-1", GIORNO), movimento("conf-1", -2500L)));

        List<Movement> rimossi = repository.deleteMissingUncleared(marta, IngestionSource.BUDGET_BAKERS,
                GIORNO, GIORNO.plusDays(1), List.of(credem), List.of("conf-1"));

        assertThat(rimossi).extracting(Movement::externalId).containsExactly("pending-1");
        assertThat(soloMovimento().externalId()).isEqualTo("conf-1");
    }

    @Test
    void nonCancellaCioCheEStatoLettoNeIConfermati() {
        repository.upsertAll(List.of(inSospeso(credem, "pending-1", GIORNO), movimento("conf-1", -2500L)));

        List<Movement> rimossi = repository.deleteMissingUncleared(marta, IngestionSource.BUDGET_BAKERS,
                GIORNO, GIORNO.plusDays(1), List.of(credem), List.of("pending-1"));

        assertThat(rimossi).isEmpty();
    }

    @Test
    void nonCancellaFuoriDallaFinestraNeSuiContiNonLetti() {
        AccountId contanti = conto("acc-2", "Contanti");
        repository.upsertAll(List.of(
                inSospeso(credem, "prima-della-finestra", GIORNO.minusDays(1)),
                inSospeso(contanti, "conto-non-letto", GIORNO)));

        List<Movement> rimossi = repository.deleteMissingUncleared(marta, IngestionSource.BUDGET_BAKERS,
                GIORNO, GIORNO.plusDays(1), List.of(credem), List.of());

        assertThat(rimossi).isEmpty();
    }

    private Movement inSospeso(AccountId account, String externalId, LocalDate data) {
        return Movement.imported(marta, account, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(-2500L), Money.of(-2500L), istante(data), MovementDirection.EXPENSE, MovementState.UNCLEARED,
                null, null, Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    private AccountId conto(String externalId, String nome) {
        Account altro = Account.imported(marta, IngestionSource.BUDGET_BAKERS, externalId, nome,
                AccountKind.CASH, CurrencyCode.EUR, Money.of(0L), null, false, false);
        accounts.insert(altro);
        return altro.id();
    }

    private Movement suConto(AccountId account, String externalId, long centesimi) {
        return Movement.imported(marta, account, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(centesimi), Money.of(centesimi), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.CLEARED,
                null, null, Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    private Movement entrata(String externalId, long centesimi) {
        return Movement.imported(marta, credem, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(centesimi), Money.of(centesimi), istante(GIORNO),
                MovementDirection.INCOME, MovementState.CLEARED,
                null, null, Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    private Movement giroconto(String externalId, long centesimi) {
        return Movement.imported(marta, credem, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(centesimi), Money.of(centesimi), istante(GIORNO),
                MovementDirection.EXPENSE, MovementState.CLEARED,
                null, null, Classification.fromSource(ristoranti, "cat-1", "Ristoranti"),
                new Transfer(Transfer.TransferState.PAIRED, "tr-1"));
    }

    private Movement conControparte(String externalId, String controparte) {
        return Movement.imported(marta, credem, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(-999L), Money.of(-999L), istante(GIORNO), MovementDirection.EXPENSE, MovementState.CLEARED,
                null, controparte,
                Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    private static MovementFilter oggi() {
        return MovementFilter.inPeriod(new DateRange(GIORNO, GIORNO));
    }

    private static MovementFilter soloCategoria(CategoryId categoria) {
        return new MovementFilter(null, Set.of(), List.of(), List.of(categoria));
    }

    private Page<Movement> inPeriodo(LocalDate da, LocalDate a) {
        return repository.findPage(marta, ZoneOffset.UTC, MovementFilter.inPeriod(new DateRange(da, a)),
                MovementSort.NEWEST_FIRST, PageRequest.firstPage());
    }

    private Movement soloMovimento() {
        List<Movement> tutti =
                repository.findPage(marta, ZoneOffset.UTC, MovementFilter.all(), MovementSort.NEWEST_FIRST, PageRequest.firstPage()).items();
        assertThat(tutti).hasSize(1);
        return tutti.getFirst();
    }

    /** Una sottocategoria di Wallet Insights, con la categoria della sorgente che ci confluisce. */
    private CategoryId categoria(String externalId, String nome) {
        if (macro == null) {
            macro = Category.create(marta, null, "Cibo e bevande");
            categories.insertAll(List.of(macro));
        }
        Category categoria = Category.create(marta, macro.id(), nome);
        categories.insertAll(List.of(categoria));
        sourceCategories.insert(new SourceCategory(SourceCategoryId.newId(), marta,
                IngestionSource.BUDGET_BAKERS, externalId, nome, "food_and_drinks", null, categoria.id()));
        return categoria.id();
    }

    private Movement movimento(String externalId, long centesimi) {
        return movimento(externalId, centesimi, GIORNO);
    }

    private Movement movimentoAlle(String externalId, long centesimi, Instant istante) {
        return Movement.imported(marta, credem, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(centesimi), Money.of(centesimi), istante,
                MovementDirection.EXPENSE, MovementState.CLEARED, null, null,
                Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    private Movement movimento(String externalId, long centesimi, LocalDate data) {
        return Movement.imported(marta, credem, IngestionSource.BUDGET_BAKERS, externalId,
                Money.of(centesimi), Money.of(centesimi), istante(data),
                MovementDirection.EXPENSE, MovementState.CLEARED,
                "una nota", "PayPal EUR",
                Classification.fromSource(ristoranti, "cat-1", "Ristoranti"), null);
    }

    /** La mezzanotte UTC del giorno: com'è codificata la maggior parte dei movimenti della sorgente. */
    private static Instant istante(LocalDate giorno) {
        return giorno.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
