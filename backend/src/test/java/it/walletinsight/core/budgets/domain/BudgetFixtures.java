package it.walletinsight.core.budgets.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

final class BudgetFixtures {

    static final UserId MARTA = UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    static final YearMonth OTTOBRE = YearMonth.of(2026, 10);

    static final CategoryId RISTORANTI = CategoryId.newId();
    static final CategoryId SPESA = CategoryId.newId();
    static final CategoryId BAR = CategoryId.newId();
    static final CategoryId CINEMA = CategoryId.newId();

    private BudgetFixtures() {
    }

    static Budget principale(String nome, long limiteCents, CategoryId... categorie) {
        return Budget.create(MARTA, null, nome, Set.of(categorie), OTTOBRE, Money.of(limiteCents));
    }

    static Budget sotto(Budget principale, String nome, long limiteCents, CategoryId... categorie) {
        return Budget.create(MARTA, principale.id(), nome, Set.of(categorie), OTTOBRE, Money.of(limiteCents));
    }
}
