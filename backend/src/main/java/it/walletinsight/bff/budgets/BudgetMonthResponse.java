package it.walletinsight.bff.budgets;

import java.util.List;

/**
 * Un mese rispetto ai budget in vigore allora.
 *
 * I totali sommano solo i budget principali; `unbudgetedCents` è ciò che è uscito
 * fuori da ogni budget, così `spentCents + unbudgetedCents = expensesCents`.
 * Un `remainingCents` negativo è quanto si è sforato.
 *
 * @param month `yyyy-MM`
 */
public record BudgetMonthResponse(
        String month,
        String currencyCode,
        long limitCents,
        long spentCents,
        long remainingCents,
        long unbudgetedCents,
        long expensesCents,
        List<BudgetStatusResponse> budgets) {

    /** @param children i sotto-budget, vuoto per un sotto-budget */
    public record BudgetStatusResponse(
            String id,
            String name,
            long limitCents,
            long spentCents,
            long remainingCents,
            List<CategorySpentResponse> categories,
            List<BudgetStatusResponse> children) {
    }

    /** @param color `#rrggbb`, assente se la categoria non ne ha uno */
    public record CategorySpentResponse(String categoryId, String name, String color, long spentCents) {
    }
}
