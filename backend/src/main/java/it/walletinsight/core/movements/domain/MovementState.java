package it.walletinsight.core.movements.domain;

/**
 * Stato di riconciliazione di un movimento.
 *
 * Non è un'etichetta decorativa: {@link #VOID} è un movimento annullato, e gli
 * annullati non entrano nel saldo — vedi la somma in `infrastructure/jdbc`.
 * Tutti gli altri sono soldi che si sono mossi davvero, in un momento diverso
 * del loro percorso.
 *
 * {@code WAIT_FOR_ASSIGN} è il movimento arrivato dalla banca e non ancora
 * assegnato a una categoria: manca l'etichetta, non il denaro.
 */
public enum MovementState {
    RECONCILED,
    CLEARED,
    UNCLEARED,
    VOID,
    WAIT_FOR_ASSIGN,
    UNKNOWN
}
