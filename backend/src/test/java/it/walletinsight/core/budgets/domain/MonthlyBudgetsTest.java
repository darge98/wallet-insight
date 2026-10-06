package it.walletinsight.core.budgets.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static it.walletinsight.core.budgets.domain.BudgetFixtures.BAR;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.CINEMA;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.OTTOBRE;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.RISTORANTI;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.SPESA;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.principale;
import static it.walletinsight.core.budgets.domain.BudgetFixtures.sotto;
import static org.assertj.core.api.Assertions.assertThat;

class MonthlyBudgetsTest {

    private final Budget cibo = principale("Cibo", 50_000, RISTORANTI, SPESA, BAR);
    private final Budget ristoranti = sotto(cibo, "Ristoranti", 20_000, RISTORANTI);

    private final Map<CategoryId, Money> spesa = Map.of(
            RISTORANTI, Money.of(24_000),
            SPESA, Money.of(15_000),
            CINEMA, Money.of(3_000));

    @Test
    void laSpesaDiUnSottoBudgetStaDentroQuellaDelPrincipale() {
        MonthlyBudgets mese = MonthlyBudgets.of(OTTOBRE, List.of(cibo, ristoranti), spesa, Money.of(60_000));

        MonthlyBudgets.Status stato = mese.budgets().getFirst();
        assertThat(stato.spent()).isEqualTo(Money.of(39_000));
        assertThat(stato.remaining()).isEqualTo(Money.of(11_000));
        assertThat(stato.children()).singleElement().satisfies(figlio -> {
            assertThat(figlio.spent()).isEqualTo(Money.of(24_000));
            // Sforato di 40 euro: il residuo negativo è quanto si è andati oltre.
            assertThat(figlio.remaining()).isEqualTo(Money.of(-4_000));
        });
    }

    @Test
    void iTotaliSommanoSoloIPrincipaliEIlRestoEFuoriBudget() {
        MonthlyBudgets mese = MonthlyBudgets.of(OTTOBRE, List.of(cibo, ristoranti), spesa, Money.of(60_000));

        assertThat(mese.limit()).isEqualTo(Money.of(50_000));
        assertThat(mese.spent()).isEqualTo(Money.of(39_000));
        assertThat(mese.unbudgeted()).isEqualTo(Money.of(21_000));
    }

    @Test
    void unMesePassatoUsaIlLimiteDiAlloraEIgnoraIBudgetNonAncoraNati() {
        Budget piuAlto = cibo.limitedFrom(OTTOBRE.plusMonths(1), Money.of(70_000));
        Budget nuovo = principale("Svago", 10_000, CINEMA).limitedFrom(OTTOBRE.plusMonths(1), Money.of(10_000));
        Budget natoANovembre = new Budget(nuovo.id(), nuovo.userId(), null, nuovo.name(), nuovo.categories(),
                List.of(nuovo.limits().getLast()));

        MonthlyBudgets ottobre = MonthlyBudgets.of(OTTOBRE, List.of(piuAlto, natoANovembre), spesa, Money.of(60_000));

        assertThat(ottobre.budgets()).singleElement().satisfies(stato -> {
            assertThat(stato.budget().name()).isEqualTo("Cibo");
            assertThat(stato.limit()).isEqualTo(Money.of(50_000));
        });
    }
}
