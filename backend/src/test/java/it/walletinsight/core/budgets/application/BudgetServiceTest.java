package it.walletinsight.core.budgets.application;

import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.budgets.domain.BudgetId;
import it.walletinsight.core.budgets.domain.BudgetLimit;
import it.walletinsight.core.budgets.domain.BudgetRepository;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.money.Money;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetServiceTest extends AbstractDatabaseTest {

    /** Le 23:30 UTC del 31 ottobre: a Roma è già novembre. */
    private static final Clock FINE_OTTOBRE_UTC =
            Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneOffset.UTC);
    private static final YearMonth NOVEMBRE = YearMonth.of(2026, 11);

    @Autowired
    private BudgetRepository repository;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    private BudgetService service;
    private UserId marta;
    private CategoryId ristoranti;
    private CategoryId spesa;

    @BeforeEach
    void prepara() {
        service = new BudgetService(repository, categories, users, FINE_OTTOBRE_UTC);
        marta = utente("Marta");
        ristoranti = categoria(marta, "cat-1", "Ristoranti");
        spesa = categoria(marta, "cat-2", "Spesa");
    }

    @Test
    void ilMeseCorrenteEQuelloDelFusoDellUtente() {
        assertThat(service.currentMonth(marta)).isEqualTo(NOVEMBRE);
    }

    @Test
    void unBudgetNuovoValeDalMeseCorrente() {
        Budget cibo = service.createBudget(marta, null, "Cibo", Set.of(ristoranti, spesa), Money.of(50_000));

        assertThat(cibo.limits()).containsExactly(new BudgetLimit(NOVEMBRE, Money.of(50_000)));
    }

    @Test
    void cambiareIlLimiteLasciaIMesiPassatiColLoro() {
        Budget cibo = service.createBudget(marta, null, "Cibo", Set.of(ristoranti), Money.of(40_000));
        repository.update(new Budget(cibo.id(), marta, null, "Cibo", cibo.categories(),
                java.util.List.of(new BudgetLimit(YearMonth.of(2026, 9), Money.of(40_000)))));

        Budget aggiornato = service.updateBudget(marta, cibo.id(), null, null, Money.of(50_000));

        assertThat(aggiornato.limitIn(YearMonth.of(2026, 10))).contains(Money.of(40_000));
        assertThat(aggiornato.limitIn(NOVEMBRE)).contains(Money.of(50_000));
        assertThat(service.listBudgets(marta).getFirst()).isEqualTo(aggiornato);
    }

    @Test
    void unaCategoriaDiUnAltroUtenteNonEsiste() {
        CategoryId diLuca = categoria(utente("Luca"), "cat-1", "Ristoranti");

        assertThatThrownBy(() -> service.createBudget(marta, null, "Cibo", Set.of(diLuca), Money.of(1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unPrincipaleCheNonEsisteEUn404() {
        assertThatThrownBy(() -> service.createBudget(
                marta, BudgetId.newId(), "Ristoranti", Set.of(ristoranti), Money.of(1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void leRegoleFraBudgetArrivanoComeConflitto() {
        service.createBudget(marta, null, "Cibo", Set.of(ristoranti), Money.of(40_000));

        assertThatThrownBy(() -> service.createBudget(marta, null, "Uscite", Set.of(ristoranti), Money.of(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellareUnBudgetDiUnAltroEUn404() {
        Budget cibo = service.createBudget(marta, null, "Cibo", Set.of(ristoranti), Money.of(40_000));
        UserId luca = utente("Luca");

        assertThatThrownBy(() -> service.deleteBudget(luca, cibo.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.listBudgets(marta)).hasSize(1);
    }

    private UserId utente(String nome) {
        User user = new User(UserId.newId(), nome, null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        return user.id();
    }

    private CategoryId categoria(UserId utente, String externalId, String nome) {
        Category macro = Category.create(utente, null, "Macro " + externalId);
        Category categoria = Category.create(utente, macro.id(), nome);
        categories.insertAll(List.of(macro, categoria));
        return categoria.id();
    }

    @Test
    void unBudgetNonComprendeUnaMacro() {
        Category macro = Category.create(marta, null, "Cibo e bevande");
        categories.insertAll(List.of(macro));

        assertThatThrownBy(() -> service.createBudget(marta, null, "Cibo", Set.of(macro.id()), Money.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
