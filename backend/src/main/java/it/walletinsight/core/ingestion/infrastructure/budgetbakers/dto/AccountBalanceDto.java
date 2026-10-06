package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.math.BigDecimal;

/**
 * Il blocco `balance` di un conto.
 *
 * `initial` è l'unico valore che ci interessa: è il punto di partenza dichiarato
 * dall'utente quando ha creato il conto, e non cambia da solo. `currentBalance`
 * lo calcolano loro dai movimenti (`formula` dice come) e noi lo ricalcoliamo
 * dai nostri: è modellato qui perché serve a confrontare i due conteggi nel log,
 * non perché venga memorizzato.
 *
 * Decimali come `BigDecimal` e non `double`, per la stessa ragione di
 * {@link AmountDto}: letto come double, 13397.67 non è esattamente 13397.67.
 */
public record AccountBalanceDto(
        BigDecimal initial,
        BigDecimal currentBalance,
        String currencyCode,
        String balanceMode) {
}
