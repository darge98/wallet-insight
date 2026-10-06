package it.walletinsight.core.movements.domain;

import it.walletinsight.shared.money.Money;

/**
 * Quanto pesa un insieme di movimenti, indipendentemente dalla pagina mostrata.
 *
 * Risponde alla domanda che si fa guardando un elenco filtrato: «in tutto questo,
 * quanto è entrato e quanto è uscito». Per questo si calcola sull'intero
 * risultato del filtro e non sulle venticinque righe visibili — un totale di
 * pagina non significherebbe niente.
 *
 * {@code expenses} è un valore **positivo**: è una quantità uscita, e mostrarla
 * col segno meno accanto all'etichetta «Uscite» direbbe la stessa cosa due volte.
 * Il segno resta dove è un dato, cioè sui singoli movimenti e su {@code net}.
 *
 * I giroconti non entrano né in {@code income} né in {@code expenses}: le due
 * gambe si annullerebbero comunque sul netto, ma gonfierebbero entrambe le
 * colonne con denaro che non è entrato e non è uscito da nessuna parte.
 *
 * {@code count} invece conta **tutti** i movimenti del filtro, giroconti
 * compresi, perché è lo stesso numero che l'elenco pagina lì sotto: se i due
 * numeri non coincidessero, uno dei due sarebbe sbagliato agli occhi di chi legge.
 */
public record MovementTotals(Money income, Money expenses, Money net, long count) {

    public static MovementTotals empty() {
        return new MovementTotals(Money.zero(), Money.zero(), Money.zero(), 0L);
    }
}
