package it.walletinsight.core.movements.domain;

/**
 * La natura di un movimento vista da chi lo guarda: entrata, uscita, giroconto.
 *
 * Non è una colonna e non si memorizza: si deriva da {@code direction} e dalla
 * presenza di un {@code transfer}. Esiste perché è la domanda che una persona fa
 * davvero — «fammi vedere solo le uscite» — e perché senza di essa un giroconto
 * si confonderebbe con una spesa: le sue due gambe hanno un verso, quindi una
 * delle due sarebbe un'uscita come tutte le altre, e «quanto ho speso» conterebbe
 * un denaro che non ha lasciato le tasche di nessuno.
 *
 * Il giroconto vince sul verso, e la misura dice che il conto torna: 1369 uscite,
 * 163 giroconti e 146 entrate sui 1678 movimenti reali, senza sovrapposizioni.
 */
public enum MovementKind {
    INCOME,
    EXPENSE,
    TRANSFER;

    /** La natura di un movimento già letto, con la stessa regola che usa l'SQL. */
    public static MovementKind of(Movement movement) {
        if (movement.isTransfer()) {
            return TRANSFER;
        }
        return movement.direction() == MovementDirection.INCOME ? INCOME : EXPENSE;
    }
}
