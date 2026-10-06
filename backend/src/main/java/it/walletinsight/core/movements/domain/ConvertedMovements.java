package it.walletinsight.core.movements.domain;

import it.walletinsight.shared.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Quanto i movimenti di un conto hanno spostato in euro, e il cambio del più vecchio.
 *
 * Il cambio serve al saldo iniziale di un conto in valuta, che non ha un giorno a
 * cui convertirlo: si usa quello del primo movimento, il più vicino a quando il
 * conto è nato.
 *
 * @param total      la somma degli importi convertiti, annullati esclusi
 * @param firstRatio euro per unità della valuta del conto nel primo movimento;
 *                   {@code null} se il conto non ha movimenti
 */
public record ConvertedMovements(Money total, BigDecimal firstRatio) {

    public static final ConvertedMovements NONE = new ConvertedMovements(Money.zero(), null);

    public ConvertedMovements {
        Objects.requireNonNull(total, "total");
    }

    /** Il saldo iniziale in euro; vuoto se è in valuta e non c'è un cambio da cui ricavarlo. */
    public Optional<Money> convert(Money initialBalance) {
        if (initialBalance.currency() == Movement.TOTALS_CURRENCY) {
            return Optional.of(initialBalance);
        }
        if (firstRatio == null) {
            return Optional.empty();
        }
        long cents = firstRatio.multiply(BigDecimal.valueOf(initialBalance.amount()))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return Optional.of(Money.of(cents, Movement.TOTALS_CURRENCY));
    }
}
