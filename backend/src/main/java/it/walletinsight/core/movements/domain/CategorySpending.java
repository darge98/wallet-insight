package it.walletinsight.core.movements.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.money.Money;

/**
 * Quanto è uscito in una categoria, in un periodo.
 *
 * Porta l'identificatore e non il nome: il nome sta in `core.categories`, e
 * andarlo a prendere con una join qui dentro significherebbe che il modulo dei
 * movimenti sa com'è fatta la tabella di un altro. A comporre i due pezzi è il
 * BFF, che è l'unico posto che vede entrambi i moduli — lo stesso lavoro che già
 * fa per il saldo di un conto.
 *
 * {@code total} è positivo, come {@code expenses} in {@link MovementTotals}.
 */
public record CategorySpending(CategoryId categoryId, Money total, long count) {
}
