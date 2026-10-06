package it.walletinsight.bff.budgets;

import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.List;

/**
 * Un budget come si modifica: la definizione col limite in vigore nel mese corrente.
 *
 * @param parentId   il budget principale, assente se lo è lui
 * @param limitCents il limite del mese corrente, in centesimi
 */
public record BudgetResponse(
        String id,
        String parentId,
        String name,
        List<String> categoryIds,
        long limitCents,
        String currencyCode) {

    static BudgetResponse from(Budget budget, YearMonth currentMonth) {
        // Il limite più recente: è quello che vale dal mese corrente in poi.
        Money limite = budget.limitIn(currentMonth).orElseGet(() -> budget.limits().getLast().amount());
        return new BudgetResponse(
                budget.id().toString(),
                budget.parentId() == null ? null : budget.parentId().toString(),
                budget.name(),
                budget.categories().stream().map(CategoryId::toString).sorted().toList(),
                limite.amount(),
                limite.currency().name());
    }
}
