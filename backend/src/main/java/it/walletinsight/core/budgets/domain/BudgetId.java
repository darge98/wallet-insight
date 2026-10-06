package it.walletinsight.core.budgets.domain;

import it.walletinsight.shared.identifier.Uuids;

import java.util.Objects;
import java.util.UUID;

public record BudgetId(UUID value) {

    public BudgetId {
        Objects.requireNonNull(value, "value");
    }

    public static BudgetId of(UUID value) {
        return new BudgetId(value);
    }

    public static BudgetId newId() {
        return new BudgetId(Uuids.v7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
