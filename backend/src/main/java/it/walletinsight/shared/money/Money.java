package it.walletinsight.shared.money;

import java.util.List;
import java.util.Objects;

/**
 * Importo monetario immutabile.
 *
 * `amount` è in unità minori (centesimi) e sempre intero: è la stessa scelta di
 * `shared/money.ts` nel frontend, e serve a evitare gli errori di arrotondamento
 * del floating point nelle somme di transazioni.
 */
public record Money(long amount, CurrencyCode currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minorUnits) {
        return new Money(minorUnits, CurrencyCode.DEFAULT);
    }

    public static Money of(long minorUnits, CurrencyCode currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero() {
        return zero(CurrencyCode.DEFAULT);
    }

    public static Money zero(CurrencyCode currency) {
        return new Money(0L, currency);
    }

    /** Solo per i test e l'import: la produzione lavora sempre in centesimi. */
    public static Money fromMajor(double value) {
        return of(Math.round(value * 100));
    }

    public static Money sum(List<Money> values) {
        return values.stream().reduce(Money.zero(), Money::plus);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount + other.amount, currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount - other.amount, currency);
    }

    public Money negated() {
        return new Money(-amount, currency);
    }

    public Money abs() {
        return new Money(Math.abs(amount), currency);
    }

    /** Rapporto fra due importi; 0 quando il denominatore è nullo. */
    public double ratioTo(Money denominator) {
        requireSameCurrency(denominator);
        return denominator.amount == 0L ? 0.0 : (double) amount / denominator.amount;
    }

    public boolean isNegative() {
        return amount < 0L;
    }

    public boolean isZero() {
        return amount == 0L;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amount, other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}
