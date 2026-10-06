package it.walletinsight.core.budgets.domain;

import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static it.walletinsight.core.budgets.domain.BudgetFixtures.BAR;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.CINEMA;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.OTTOBRE;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.RISTORANTI;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.SPESA;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.principale;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.sotto;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetPlanTest {

    private final Budget cibo = principale("Cibo", 50_000, RISTORANTI, SPESA, BAR);

    @Test
    void dueBudgetPrincipaliNonSiDividonoUnaCategoria() {
        BudgetPlan piano = new BudgetPlan(List.of(cibo));

        // Altrimenti il totale del mese conterebbe due volte la stessa cena.
        assertThatThrownBy(() -> piano.requireCompatible(principale("Uscite", 20_000, BAR, CINEMA), OTTOBRE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("«Cibo»");
    }

    @Test
    void unSottoBudgetPrendeSoloCategorieDelPrincipale() {
        BudgetPlan piano = new BudgetPlan(List.of(cibo));

        assertThatCode(() -> piano.requireCompatible(sotto(cibo, "Ristoranti", 20_000, RISTORANTI), OTTOBRE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> piano.requireCompatible(sotto(cibo, "Svago", 10_000, CINEMA), OTTOBRE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void iSottoBudgetDelloStessoPrincipaleNonSiDividonoUnaCategoria() {
        Budget ristoranti = sotto(cibo, "Ristoranti", 20_000, RISTORANTI);
        BudgetPlan piano = new BudgetPlan(List.of(cibo, ristoranti));

        assertThatThrownBy(() -> piano.requireCompatible(sotto(cibo, "Fuori", 10_000, RISTORANTI, BAR), OTTOBRE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("«Ristoranti»");
    }

    @Test
    void unSottoBudgetNonPuoAvereSottoBudget() {
        Budget ristoranti = sotto(cibo, "Ristoranti", 20_000, RISTORANTI);
        BudgetPlan piano = new BudgetPlan(List.of(cibo, ristoranti));

        assertThatThrownBy(() -> piano.requireCompatible(sotto(ristoranti, "Pizza", 5_000, RISTORANTI), OTTOBRE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void iSottoBudgetNonPromettonoPiuDelPrincipale() {
        Budget ristoranti = sotto(cibo, "Ristoranti", 30_000, RISTORANTI);
        BudgetPlan piano = new BudgetPlan(List.of(cibo, ristoranti));

        assertThatCode(() -> piano.requireCompatible(sotto(cibo, "Bar", 20_000, BAR), OTTOBRE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> piano.requireCompatible(sotto(cibo, "Bar", 20_001, BAR), OTTOBRE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abbassareIlPrincipaleSottoLaSommaDeiSottoBudgetNonEAmmesso() {
        Budget ristoranti = sotto(cibo, "Ristoranti", 30_000, RISTORANTI);
        BudgetPlan piano = new BudgetPlan(List.of(cibo, ristoranti));

        assertThatThrownBy(() -> piano.requireCompatible(cibo.limitedFrom(OTTOBRE, Money.of(25_000)), OTTOBRE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ilPrincipaleNonPuoTogliereUnaCategoriaUsataDaUnSottoBudget() {
        Budget ristoranti = sotto(cibo, "Ristoranti", 20_000, RISTORANTI);
        BudgetPlan piano = new BudgetPlan(List.of(cibo, ristoranti));

        assertThatThrownBy(() -> piano.requireCompatible(cibo.covering(Set.of(SPESA, BAR)), OTTOBRE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("«Ristoranti»");
    }

    @Test
    void modificareUnBudgetNonLoFaScontrareConSeStesso() {
        BudgetPlan piano = new BudgetPlan(List.of(cibo));

        assertThatCode(() -> piano.requireCompatible(cibo.renamedTo("Mangiare"), OTTOBRE))
                .doesNotThrowAnyException();
    }
}
