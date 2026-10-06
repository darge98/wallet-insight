package it.walletinsight.core.budgets.domain;

import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.Objects;

/** Il tetto mensile in vigore da {@code validFrom} fino al limite successivo. */
public record BudgetLimit(YearMonth validFrom, Money amount) {

    public BudgetLimit {
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(amount, "amount");
        if (amount.amount() <= 0) {
            throw new IllegalArgumentException("Il limite di un budget deve essere maggiore di zero.");
        }
    }
}
