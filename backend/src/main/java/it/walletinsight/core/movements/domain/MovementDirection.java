package it.walletinsight.core.movements.domain;

/**
 * Verso del movimento, come lo dichiara la sorgente.
 *
 * Nei 1672 movimenti osservati coincide sempre con il segno dell'importo —
 * uscite negative, entrate positive — ma resta un campo suo invece di essere
 * dedotto dal segno: è la sorgente a decidere cosa considera un'entrata, e il
 * giorno che le due cose non coincideranno si vedrà, invece di essere nascosto
 * da un calcolo.
 *
 * {@code UNKNOWN} esiste perché una sorgente può introdurre un verso che non
 * conosciamo: il movimento entra comunque, perché il suo importo è un fatto e
 * perderlo falserebbe il saldo.
 */
public enum MovementDirection {
    INCOME,
    EXPENSE,
    UNKNOWN
}
