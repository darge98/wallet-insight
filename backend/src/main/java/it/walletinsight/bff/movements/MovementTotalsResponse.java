package it.walletinsight.bff.movements;

import it.walletinsight.core.movements.domain.MovementTotals;

/**
 * Quanto pesa l'elenco filtrato, al di là della pagina che si sta guardando.
 *
 * `expensesCents` è **positivo**: è una quantità uscita, e il segno meno accanto
 * all'etichetta «Uscite» direbbe la stessa cosa due volte. Il segno resta dove è
 * un dato, cioè sui singoli movimenti e su `netCents`.
 *
 * I giroconti non entrano nelle due colonne — le loro gambe si annullano e
 * gonfierebbero entrambe con denaro che non è entrato né uscito — ma `count` li
 * conta, perché è lo stesso numero che la paginazione mostra lì sotto.
 *
 * @param incomeCents   entrate del periodo filtrato, in centesimi interi
 * @param expensesCents uscite, positive
 * @param netCents      entrate meno uscite, col segno
 * @param currencyCode  valuta ISO 4217 dei tre importi
 * @param count         quanti movimenti corrispondono al filtro, giroconti compresi
 */
public record MovementTotalsResponse(
        long incomeCents,
        long expensesCents,
        long netCents,
        String currencyCode,
        long count) {

    public static MovementTotalsResponse from(MovementTotals totals) {
        return new MovementTotalsResponse(
                totals.income().amount(),
                totals.expenses().amount(),
                totals.net().amount(),
                totals.income().currency().name(),
                totals.count());
    }
}
