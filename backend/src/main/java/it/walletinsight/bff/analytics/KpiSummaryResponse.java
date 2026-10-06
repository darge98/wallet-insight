package it.walletinsight.bff.analytics;

import it.walletinsight.core.movements.domain.MovementTotals;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.money.Money;

import java.time.LocalDate;

/**
 * I numeri di apertura della Panoramica.
 *
 * `netWorth` è la somma dei saldi dei conti — saldo iniziale più i movimenti —
 * ed è l'unico valore che non viene dal periodo: risponde a «quanto ho», non a
 * «com'è andato questo mese». È anche il motivo per cui questa risposta la
 * compone il BFF: i saldi li sa `core.accounts`, i totali `core.movements`, e i
 * due moduli non si conoscono.
 *
 * Ogni tendenza porta il valore del periodo precedente insieme alla differenza,
 * così l'interfaccia può scrivere «681,80 € in più rispetto ad agosto» senza
 * rifare il conto — e senza rischiare di rifarlo in modo diverso.
 *
 * `changePercent` è assente quando il periodo precedente vale zero: una
 * variazione percentuale su una base nulla non è infinito, è una domanda che non
 * ha risposta, e mostrare «+∞%» sarebbe peggio che non mostrare niente.
 *
 * @param savingsRate  quota di entrate non spesa, in percentuale (0–100)
 * @param previousFrom primo giorno del periodo di confronto, deciso qui: il client lo
 *                     nomina senza ricalcolarlo
 * @param previousTo   ultimo giorno del periodo di confronto
 */
public record KpiSummaryResponse(
        long netWorthCents,
        long incomeCents,
        long expensesCents,
        long netCents,
        String currencyCode,
        double savingsRate,
        long transactionCount,
        TrendResponse incomeTrend,
        TrendResponse expensesTrend,
        TrendResponse netTrend,
        LocalDate previousFrom,
        LocalDate previousTo) {

    /** @param changePercent variazione rispetto al periodo precedente; null se quello è a zero */
    public record TrendResponse(long previousCents, long differenceCents, Double changePercent) {

        static TrendResponse between(Money current, Money previous) {
            long differenza = current.amount() - previous.amount();
            Double variazione = previous.amount() == 0
                    ? null
                    : (double) differenza / Math.abs(previous.amount()) * 100d;
            return new TrendResponse(previous.amount(), differenza, variazione);
        }
    }

    public static KpiSummaryResponse of(
            Money netWorth, MovementTotals current, DateRange previousPeriod, MovementTotals previous) {
        long entrate = current.income().amount();
        return new KpiSummaryResponse(
                netWorth.amount(),
                entrate,
                current.expenses().amount(),
                current.net().amount(),
                current.income().currency().name(),
                // Senza entrate non esiste una quota risparmiata: zero, non una
                // divisione per zero e nemmeno il 100% di niente.
                entrate == 0 ? 0d : (double) current.net().amount() / entrate * 100d,
                current.count(),
                TrendResponse.between(current.income(), previous.income()),
                TrendResponse.between(current.expenses(), previous.expenses()),
                TrendResponse.between(current.net(), previous.net()),
                previousPeriod.from(),
                previousPeriod.to());
    }
}
