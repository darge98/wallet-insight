package it.walletinsight.bff.analytics;

import it.walletinsight.core.movements.domain.CumulativeExpensePoint;

import java.time.LocalDate;

/**
 * Un punto della curva delle uscite: quanto si è speso **dall'inizio del
 * periodo fino a questo giorno**, non quanto si è speso oggi.
 *
 * @param date       il giorno, `yyyy-MM-dd`
 * @param totalCents spesa cumulata fino a qui, positiva, in centesimi interi
 */
public record CumulativeExpenseResponse(LocalDate date, long totalCents) {

    public static CumulativeExpenseResponse from(CumulativeExpensePoint point) {
        return new CumulativeExpenseResponse(point.date(), point.cumulative().amount());
    }
}
