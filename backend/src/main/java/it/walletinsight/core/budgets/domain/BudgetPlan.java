package it.walletinsight.core.budgets.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * I budget di un utente, presi insieme: è qui che vivono le regole fra un budget e
 * l'altro, quelle che nessun budget può verificare da solo.
 *
 * Le violazioni sono {@link IllegalStateException}, cioè 409: la richiesta è ben
 * scritta, è lo stato degli altri budget a non ammetterla. I messaggi arrivano
 * all'utente così come sono.
 */
public final class BudgetPlan {

    private final List<Budget> budgets;

    public BudgetPlan(List<Budget> budgets) {
        this.budgets = List.copyOf(budgets);
    }

    public Optional<Budget> find(BudgetId id) {
        return budgets.stream().filter(budget -> budget.id().equals(id)).findFirst();
    }

    public List<Budget> childrenOf(BudgetId id) {
        return budgets.stream().filter(budget -> id.equals(budget.parentId())).toList();
    }

    /** Verifica che {@code candidate}, nuovo o modificato, possa stare accanto agli altri. */
    public void requireCompatible(Budget candidate, YearMonth month) {
        List<Budget> altri = budgets.stream()
                .filter(budget -> !budget.id().equals(candidate.id()))
                .toList();

        for (Budget altro : altri) {
            if (altro.isMain() == candidate.isMain()
                    && !Collections.disjoint(altro.categories(), candidate.categories())) {
                throw new IllegalStateException(
                        "Una delle categorie scelte è già nel budget «%s».".formatted(altro.name()));
            }
        }

        if (candidate.isMain()) {
            requireCoversChildren(candidate);
            requireRoomForChildren(candidate, childrenOf(candidate.id()), month);
        } else {
            Budget principale = altri.stream()
                    .filter(budget -> budget.id().equals(candidate.parentId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Il budget principale non esiste."));
            if (!principale.isMain()) {
                throw new IllegalStateException(
                        "Un sotto-budget non può contenere altri sotto-budget.");
            }
            if (!principale.categories().containsAll(candidate.categories())) {
                throw new IllegalStateException(
                        "Un sotto-budget può comprendere solo categorie di «%s»."
                                .formatted(principale.name()));
            }
            List<Budget> fratelli = altri.stream()
                    .filter(budget -> principale.id().equals(budget.parentId()))
                    .toList();
            requireRoomForChildren(principale,
                    Stream.concat(fratelli.stream(), Stream.of(candidate)).toList(), month);
        }
    }

    private void requireCoversChildren(Budget main) {
        for (Budget figlio : childrenOf(main.id())) {
            if (!main.categories().containsAll(figlio.categories())) {
                throw new IllegalStateException(
                        "Il sotto-budget «%s» usa categorie che vuoi togliere: toglile prima da lì."
                                .formatted(figlio.name()));
            }
        }
    }

    /**
     * I sotto-budget non possono promettere più del principale: altrimenti si
     * starebbe nei limiti in ognuno e si sforerebbe comunque il totale.
     */
    private static void requireRoomForChildren(Budget main, List<Budget> children, YearMonth month) {
        Money tetto = main.limitIn(month).orElseThrow(() -> new IllegalStateException(
                "«%s» non ha un limite in questo mese.".formatted(main.name())));
        Money promesso = Money.zero(tetto.currency());
        for (Budget figlio : children) {
            promesso = promesso.plus(figlio.limitIn(month).orElse(Money.zero(tetto.currency())));
        }
        if (promesso.compareTo(tetto) > 0) {
            throw new IllegalStateException(
                    "I limiti dei sotto-budget di «%s» supererebbero il suo.".formatted(main.name()));
        }
    }

    /** Le categorie di tutti i budget: quelle su cui serve sapere quanto si è speso. */
    public List<CategoryId> categories() {
        return budgets.stream().flatMap(budget -> budget.categories().stream()).distinct().toList();
    }

    public List<Budget> all() {
        return budgets;
    }
}
