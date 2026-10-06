package it.walletinsight.bff.analytics;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.core.accounts.application.AccountService;
import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.CategorySpending;
import it.walletinsight.core.movements.domain.ConvertedMovements;
import it.walletinsight.core.movements.domain.CounterPartySpending;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Le domande di sintesi su un periodo: com'è andato, e dove sono andati i soldi.
 *
 * Quattro endpoint e nessuno è un elenco di movimenti: sono aggregazioni, e le
 * fa PostgreSQL. Il client non scarica lo storico per sommarlo — su 1678
 * movimenti sarebbe già una cattiva idea, e lo storico cresce a ogni import.
 *
 * Stanno in un'area loro e non sotto `/movements` perché rispondono a una
 * domanda diversa da «quali movimenti»: qui si chiede quanto, non quali.
 *
 * Due di questi sono il caso che giustifica il BFF. `kpi-summary` mette insieme
 * il patrimonio, che viene dai conti, con i totali di periodo, che vengono dai
 * movimenti; `expenses-by-category` mette gli importi, che conta
 * `core.movements`, accanto ai nomi, che ha `core.categories`. In entrambi i
 * casi i moduli coinvolti non si conoscono fra loro, e a comporli è questo layer.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/analytics")
@Tag(name = "Analytics")
class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    /** Oltre non è più una classifica, è di nuovo un elenco da leggere tutto. */
    private static final int MAX_LIMIT = 50;

    private final MovementService movements;
    private final CategoryService categories;
    private final AccountService accounts;

    AnalyticsController(MovementService movements,
                        CategoryService categories,
                        AccountService accounts) {
        this.movements = movements;
        this.categories = categories;
        this.accounts = accounts;
    }

    /**
     * I numeri di apertura: patrimonio, entrate, uscite, margine e le tendenze.
     *
     * Il periodo di confronto lo decide il server (vedi {@link DateRange#previousComparable})
     * e lo restituisce: lasciarlo ricalcolare al client per nominarlo vorrebbe dire due
     * definizioni della stessa cosa, ed è già successo che divergessero.
     */
    @GetMapping("/kpi-summary")
    KpiSummaryResponse kpiSummary(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        UserId id = UserId.of(userId);
        DateRange period = new DateRange(from, to);
        DateRange confronto = period.previousComparable();

        return KpiSummaryResponse.of(
                netWorthOf(id),
                movements.totals(id, MovementFilter.inPeriod(period)),
                confronto,
                movements.totals(id, MovementFilter.inPeriod(confronto)));
    }

    /** Il patrimonio, lo stesso dei KPI: due calcoli dello stesso numero sono due numeri. */
    @GetMapping("/net-worth")
    NetWorthResponse netWorth(@PathVariable UUID userId) {
        Money patrimonio = netWorthOf(UserId.of(userId));
        return new NetWorthResponse(patrimonio.amount(), patrimonio.currency().name());
    }

    /** La curva delle uscite: quanto si è speso dall'inizio del periodo, giorno per giorno. */
    @GetMapping("/cumulative-expenses")
    List<CumulativeExpenseResponse> cumulativeExpenses(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return movements.cumulativeExpenses(UserId.of(userId), new DateRange(from, to)).stream()
                .map(CumulativeExpenseResponse::from)
                .toList();
    }

    /**
     * Quanto l'utente possiede: la somma dei saldi dei conti che contano.
     *
     * È lo stesso saldo che l'elenco dei conti mostra — saldo iniziale più la
     * somma dei movimenti — composto qui dagli stessi due pezzi e non calcolato
     * in un secondo modo: due formule per lo stesso numero sono due numeri.
     *
     * Restano fuori gli archiviati e quelli che la sorgente marca come esclusi
     * dalle statistiche: sono conti di appoggio, e sommarli gonfierebbe la
     * risposta a «quanto ho».
     *
     * Tutto in euro: ogni movimento al cambio del suo giorno, il saldo iniziale di un
     * conto in valuta a quello del suo primo movimento (vedi {@link ConvertedMovements}).
     */
    private Money netWorthOf(UserId userId) {
        Map<AccountId, ConvertedMovements> spostato = movements.convertedByAccount(userId);
        Money totale = Money.zero(Movement.TOTALS_CURRENCY);
        for (Account conto : accounts.listAccounts(userId)) {
            if (conto.archived() || conto.excludedFromStats()) {
                continue;
            }
            ConvertedMovements movimenti = spostato.getOrDefault(conto.id(), ConvertedMovements.NONE);
            Optional<Money> iniziale = movimenti.convert(conto.initialBalance());
            if (iniziale.isPresent()) {
                totale = totale.plus(iniziale.get()).plus(movimenti.total());
            } else {
                log.warn("Conto {} in {} senza movimenti da cui ricavare il cambio: fuori dal patrimonio",
                        conto.id(), conto.initialBalance().currency());
            }
        }
        return totale;
    }

    /**
     * Le categorie su cui si è speso di più nel periodo.
     *
     * La quota la calcola il server sul totale delle uscite di **tutto** il
     * periodo, non sulla somma delle categorie restituite: sulle prime cinque le
     * percentuali sommerebbero a cento e direbbero che lì è finito tutto.
     *
     * `accountId` restringe la classifica ai conti indicati, con la stessa forma
     * e la stessa regola dell'elenco dei movimenti: è la classifica che sta
     * accanto a quell'elenco, e deve parlare delle stesse righe. La quota si
     * calcola allora sulle uscite di quei conti.
     */
    @GetMapping("/expenses-by-category")
    List<CategorySpendingResponse> expensesByCategory(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<UUID> accountId,
            @RequestParam(defaultValue = "5") int limit) {

        UserId id = UserId.of(userId);
        MovementFilter filtro = filterOf(from, to, accountId);
        List<CategorySpending> spese = movements.expensesByCategory(id, filtro, limitOf(limit));
        if (spese.isEmpty()) {
            return List.of();
        }

        long totaleUscite = movements.totals(id, filtro).expenses().amount();
        Map<CategoryId, Category> anagrafica = new HashMap<>();
        for (Category categoria : categories.listCategories(id)) {
            anagrafica.put(categoria.id(), categoria);
        }

        return spese.stream()
                .map(spesa -> {
                    Category categoria = anagrafica.get(spesa.categoryId());
                    return new CategorySpendingResponse(
                            spesa.categoryId().toString(),
                            // Una categoria cancellata sotto i piedi non fa sparire la
                            // riga: l'importo e' speso comunque, e una classifica che
                            // perde una voce senza dirlo e' peggio di una voce senza nome.
                            categoria == null ? "Categoria sconosciuta" : categoria.name(),
                            categoria == null ? null : categoria.color(),
                            spesa.total().amount(),
                            spesa.count(),
                            share(spesa.total().amount(), totaleUscite));
                })
                .toList();
    }

    /** Le controparti verso cui si è speso di più nel periodo, sui conti indicati se ce ne sono. */
    @GetMapping("/top-counter-parties")
    List<CounterPartySpendingResponse> topCounterParties(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<UUID> accountId,
            @RequestParam(defaultValue = "5") int limit) {

        return movements
                .topCounterParties(UserId.of(userId), filterOf(from, to, accountId), limitOf(limit))
                .stream()
                .map(AnalyticsController::toResponse)
                .toList();
    }

    private static CounterPartySpendingResponse toResponse(CounterPartySpending spesa) {
        return new CounterPartySpendingResponse(
                spesa.counterParty(), spesa.total().amount(), spesa.count());
    }

    private static MovementFilter filterOf(LocalDate from, LocalDate to, List<UUID> accountIds) {
        List<AccountId> conti = accountIds == null
                ? List.of()
                : accountIds.stream().map(AccountId::of).toList();
        return new MovementFilter(new DateRange(from, to), Set.of(), conti, List.of());
    }

    /**
     * Un `limit` fuori scala viene riportato dentro i limiti invece di sollevare:
     * è la stessa scelta di `PageRequest`, e un `?limit=0` arrivato dal client è
     * una richiesta malscritta, non qualcosa da mostrare all'utente.
     */
    private static int limitOf(int requested) {
        return Math.clamp(requested, 1, MAX_LIMIT);
    }

    /** Senza uscite nel periodo la quota è zero, non una divisione per zero. */
    private static double share(long amount, long total) {
        return total == 0 ? 0d : (double) amount / total;
    }
}
