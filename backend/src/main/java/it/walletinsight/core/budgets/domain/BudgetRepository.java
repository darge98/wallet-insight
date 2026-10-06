package it.walletinsight.core.budgets.domain;

import it.walletinsight.core.users.domain.UserId;

import java.util.List;

/** Porta di persistenza dei budget, con le loro categorie e lo storico dei limiti. */
public interface BudgetRepository {

    /** I budget di un utente in ordine di nome, principali e sotto-budget insieme. */
    List<Budget> findByUser(UserId userId);

    void insert(Budget budget);

    /** Riscrive nome, categorie e limiti; il principale di un budget non cambia. */
    void update(Budget budget);

    /** Cancella il budget e, se è un principale, i suoi sotto-budget. */
    void delete(UserId userId, BudgetId id);
}
