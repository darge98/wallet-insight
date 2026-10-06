package it.walletinsight.core.movements.domain;

import java.util.Objects;

/**
 * Come ordinare un elenco di movimenti.
 *
 * I campi ordinabili sono tre e sono quelli su cui una persona cerca qualcosa:
 * quando è successo, quanto pesa, chi c'era dall'altra parte. Sono un'enum e non
 * una stringa perché finiscono dentro un `order by`: un nome di colonna che
 * arriva dall'esterno è il modo classico di aprire un'iniezione SQL, e con
 * un'enum il problema non esiste invece di doverlo ricordare.
 *
 * L'ordinamento per importo guarda il **valore assoluto**: «i movimenti più
 * grandi» sono i più grandi, non tutte le entrate prima di tutte le uscite.
 * L'ordinamento per controparte guarda quella *mostrata*, come il filtro per
 * categoria: se l'utente l'ha scritta lui, è quella che vede in elenco ed è
 * quella per cui si aspetta che l'ordine valga.
 */
public record MovementSort(Field field, Direction direction) {

    public static final MovementSort NEWEST_FIRST = new MovementSort(Field.DATE, Direction.DESC);

    public MovementSort {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
    }

    public enum Field {
        DATE,
        AMOUNT,
        COUNTER_PARTY
    }

    public enum Direction {
        ASC,
        DESC
    }
}
