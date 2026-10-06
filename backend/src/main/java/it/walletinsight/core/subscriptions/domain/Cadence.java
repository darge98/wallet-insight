package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.shared.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Ogni quanto si rinnova un abbonamento: «ogni 3 mesi», «ogni anno». Mensile,
 * trimestrale e annuale sono solo i casi più comuni.
 */
public record Cadence(int every, CadenceUnit unit) {

    public static final int MAX_EVERY = 99;

    /** Settimane in un anno medio, 365,25 / 7, come frazione esatta. */
    private static final long WEEKS_PER_YEAR_NUMERATOR = 36_525;
    private static final long WEEKS_PER_YEAR_DENOMINATOR = 700;

    public Cadence {
        Objects.requireNonNull(unit, "unit");
        if (every < 1 || every > MAX_EVERY) {
            throw new IllegalArgumentException(
                    "La cadenza va da 1 a %d.".formatted(MAX_EVERY));
        }
    }

    public static Cadence monthly() {
        return new Cadence(1, CadenceUnit.MONTH);
    }

    /**
     * L'{@code index}-esimo addebito a partire da {@code start}, contato sempre da
     * lì e non dal precedente: un abbonamento del 31 gennaio cade il 28 febbraio e
     * torna al 31 marzo, invece di scivolare al 28 per sempre.
     */
    LocalDate occurrence(LocalDate start, long index) {
        return start.plus(index * every, unit.chronoUnit());
    }

    /** L'indice del primo addebito che cade in {@code date} o dopo. */
    long firstIndexOnOrAfter(LocalDate start, LocalDate date) {
        if (!date.isAfter(start)) {
            return 0;
        }
        long indice = Math.max(0, unit.chronoUnit().between(start, date) / every - 1);
        while (occurrence(start, indice).isBefore(date)) {
            indice++;
        }
        return indice;
    }

    /** Il costo di un anno medio, arrotondato al centesimo. */
    Money yearly(Money amount) {
        return switch (unit) {
            case WEEK -> scaled(amount, WEEKS_PER_YEAR_NUMERATOR, WEEKS_PER_YEAR_DENOMINATOR * every);
            case MONTH -> scaled(amount, 12, every);
            case YEAR -> scaled(amount, 1, every);
        };
    }

    /**
     * Il costo di un mese medio. Si calcola dal valore esatto e non dividendo
     * {@link #yearly}: arrotondare due volte sbaglierebbe di un centesimo.
     */
    Money monthly(Money amount) {
        return switch (unit) {
            case WEEK -> scaled(amount, WEEKS_PER_YEAR_NUMERATOR, WEEKS_PER_YEAR_DENOMINATOR * 12L * every);
            case MONTH -> scaled(amount, 1, every);
            case YEAR -> scaled(amount, 1, 12L * every);
        };
    }

    private static Money scaled(Money amount, long numerator, long denominator) {
        long prodotto = amount.amount() * numerator;
        long arrotondato = Math.floorDiv(2 * prodotto + denominator, 2 * denominator);
        return Money.of(arrotondato, amount.currency());
    }
}
