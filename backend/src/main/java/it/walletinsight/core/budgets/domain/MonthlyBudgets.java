package it.walletinsight.core.budgets.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Come è andato un mese rispetto ai budget in vigore allora.
 *
 * I totali sommano solo i budget principali: la spesa di un sotto-budget è già
 * dentro quella del suo principale. {@code unbudgeted} è ciò che è uscito fuori da
 * ogni budget, così limite più fuori budget racconta tutto il mese.
 */
public record MonthlyBudgets(
        YearMonth month,
        List<Status> budgets,
        Money limit,
        Money spent,
        Money unbudgeted,
        Money expenses) {

    /** Un budget nel mese: {@code children} è vuoto per i sotto-budget. */
    public record Status(Budget budget, Money limit, Money spent, List<Status> children) {

        public Money remaining() {
            return limit.minus(spent);
        }
    }

    public Money remaining() {
        return limit.minus(spent);
    }

    /**
     * @param spending le uscite del mese per categoria, positive
     * @param expenses tutte le uscite del mese, anche fuori dalle categorie dei budget
     */
    public static MonthlyBudgets of(YearMonth month, List<Budget> budgets,
                                    Map<CategoryId, Money> spending, Money expenses) {
        Money zero = Money.zero(expenses.currency());
        List<Status> principali = new ArrayList<>();
        Money limite = zero;
        Money speso = zero;

        for (Budget budget : budgets) {
            if (!budget.isMain()) {
                continue;
            }
            Optional<Status> stato = statusOf(budget, month, spending, zero, budgets);
            if (stato.isEmpty()) {
                continue;
            }
            principali.add(stato.get());
            limite = limite.plus(stato.get().limit());
            speso = speso.plus(stato.get().spent());
        }

        return new MonthlyBudgets(month, List.copyOf(principali), limite, speso,
                expenses.minus(speso), expenses);
    }

    private static Optional<Status> statusOf(Budget budget, YearMonth month,
                                             Map<CategoryId, Money> spending, Money zero,
                                             List<Budget> all) {
        return budget.limitIn(month).map(limite -> {
            Money speso = zero;
            for (CategoryId categoria : budget.categories()) {
                speso = speso.plus(spending.getOrDefault(categoria, zero));
            }
            List<Status> figli = budget.isMain()
                    ? all.stream()
                            .filter(altro -> budget.id().equals(altro.parentId()))
                            .flatMap(figlio -> statusOf(figlio, month, spending, zero, all).stream())
                            .toList()
                    : List.of();
            return new Status(budget, limite, speso, figli);
        });
    }
}
