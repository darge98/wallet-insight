package it.walletinsight.core.budgets.domain;

import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static it.walletinsight.core.budgets.domain.BudgetFixtures.MARTA;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.OTTOBRE;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.RISTORANTI;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.principale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetTest {

    @Test
    void primaDelPrimoLimiteIlBudgetNonEsisteva() {
        Budget cibo = principale("Cibo", 50_000, RISTORANTI);

        assertThat(cibo.limitIn(OTTOBRE.minusMonths(1))).isEmpty();
        assertThat(cibo.limitIn(OTTOBRE)).contains(Money.of(50_000));
        assertThat(cibo.limitIn(OTTOBRE.plusMonths(5))).contains(Money.of(50_000));
    }

    @Test
    void unLimiteNuovoNonRiscriveIMesiPrecedenti() {
        Budget cibo = principale("Cibo", 40_000, RISTORANTI)
                .limitedFrom(OTTOBRE.plusMonths(1), Money.of(50_000));

        assertThat(cibo.limitIn(OTTOBRE)).contains(Money.of(40_000));
        assertThat(cibo.limitIn(OTTOBRE.plusMonths(1))).contains(Money.of(50_000));
    }

    @Test
    void cambiareDueVolteIlLimiteNelloStessoMeseTieneSoloLUltimo() {
        Budget cibo = principale("Cibo", 40_000, RISTORANTI)
                .limitedFrom(OTTOBRE, Money.of(45_000))
                .limitedFrom(OTTOBRE, Money.of(50_000));

        assertThat(cibo.limits()).singleElement()
                .isEqualTo(new BudgetLimit(OTTOBRE, Money.of(50_000)));
    }

    @Test
    void unBudgetSenzaCategorieOSenzaNomeNonEsiste() {
        assertThatThrownBy(() -> Budget.create(MARTA, null, "Cibo", Set.of(), OTTOBRE, Money.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Budget.create(MARTA, null, "  ", Set.of(RISTORANTI), OTTOBRE, Money.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unLimiteAZeroNonELimiteDiNiente() {
        assertThatThrownBy(() -> principale("Cibo", 0, RISTORANTI))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
