package it.walletinsight.core.budgets.infrastructure.jdbc;

import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.budgets.domain.BudgetLimit;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.money.Money;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le regole fra budget che lo schema fa rispettare da solo: qui si verifica che un
 * errore del codice non possa passare, scrivendo di proposito ciò che il dominio
 * rifiuterebbe.
 */
class JdbcBudgetRepositoryTest extends AbstractDatabaseTest {

    private static final YearMonth OTTOBRE = YearMonth.of(2026, 10);

    @Autowired
    private JdbcBudgetRepository repository;

    @Autowired
    private UserRepository users;

    @Autowired
    private CategoryRepository categories;

    private UserId marta;
    private Category macro;
    private CategoryId ristoranti;
    private CategoryId spesa;
    private CategoryId bar;

    @BeforeEach
    void preparaUtenteECategorie() {
        User user = new User(UserId.newId(), "Marta", null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        marta = user.id();
        ristoranti = categoria("cat-1", "Ristoranti");
        spesa = categoria("cat-2", "Spesa");
        bar = categoria("cat-3", "Bar");
    }

    @Test
    void rileggeCategorieEStoricoDeiLimiti() {
        Budget cibo = principale("Cibo", 40_000, ristoranti, spesa)
                .limitedFrom(OTTOBRE.plusMonths(1), Money.of(50_000));
        repository.insert(cibo);

        assertThat(repository.findByUser(marta)).singleElement().satisfies(letto -> {
            assertThat(letto).isEqualTo(cibo);
            assertThat(letto.limits()).containsExactly(
                    new BudgetLimit(OTTOBRE, Money.of(40_000)),
                    new BudgetLimit(OTTOBRE.plusMonths(1), Money.of(50_000)));
        });
    }

    @Test
    void loSchemaRifiutaDuePrincipaliSullaStessaCategoria() {
        repository.insert(principale("Cibo", 40_000, ristoranti));

        assertThatThrownBy(() -> repository.insert(principale("Uscite", 20_000, ristoranti)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void loSchemaRifiutaUnSottoBudgetConCategorieFuoriDalPrincipale() {
        Budget cibo = principale("Cibo", 40_000, ristoranti);
        repository.insert(cibo);

        assertThatThrownBy(() -> repository.insert(sotto(cibo, "Bar", 10_000, bar)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void loSchemaRifiutaUnTerzoLivello() {
        Budget cibo = principale("Cibo", 40_000, ristoranti);
        Budget fuori = sotto(cibo, "Fuori", 20_000, ristoranti);
        repository.insert(cibo);
        repository.insert(fuori);

        assertThatThrownBy(() -> repository.insert(sotto(fuori, "Pizza", 5_000, ristoranti)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void loSchemaRifiutaDiTogliereAlPrincipaleUnaCategoriaDiUnSottoBudget() {
        Budget cibo = principale("Cibo", 40_000, ristoranti, spesa);
        repository.insert(cibo);
        repository.insert(sotto(cibo, "Ristoranti", 20_000, ristoranti));

        assertThatThrownBy(() -> repository.update(cibo.covering(Set.of(spesa))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aggiornaLeCategorieDelPrincipaleSenzaToccareQuelleDeiSottoBudget() {
        Budget cibo = principale("Cibo", 40_000, ristoranti, spesa);
        Budget fuori = sotto(cibo, "Ristoranti", 20_000, ristoranti);
        repository.insert(cibo);
        repository.insert(fuori);

        repository.update(cibo.covering(Set.of(ristoranti, bar)).renamedTo("Mangiare"));

        assertThat(repository.findByUser(marta))
                .extracting(Budget::name, Budget::categories)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Mangiare", Set.of(ristoranti, bar)),
                        org.assertj.core.groups.Tuple.tuple("Ristoranti", Set.of(ristoranti)));
    }

    @Test
    void cancellareIlPrincipalePortaViaISottoBudget() {
        Budget cibo = principale("Cibo", 40_000, ristoranti, spesa);
        repository.insert(cibo);
        repository.insert(sotto(cibo, "Ristoranti", 20_000, ristoranti));

        repository.delete(marta, cibo.id());

        assertThat(repository.findByUser(marta)).isEmpty();
        assertThat(jdbc.sql("select count(*) from budget_categories").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from budget_limits").query(Long.class).single()).isZero();
    }

    private Budget principale(String nome, long limite, CategoryId... categorie) {
        return Budget.create(marta, null, nome, Set.of(categorie), OTTOBRE, Money.of(limite));
    }

    private Budget sotto(Budget principale, String nome, long limite, CategoryId... categorie) {
        return Budget.create(marta, principale.id(), nome, Set.of(categorie), OTTOBRE, Money.of(limite));
    }

    private CategoryId categoria(String externalId, String nome) {
        if (macro == null) {
            macro = Category.create(marta, null, "Cibo e bevande");
            categories.insertAll(List.of(macro));
        }
        Category categoria = Category.create(marta, macro.id(), nome);
        categories.insertAll(List.of(categoria));
        return categoria.id();
    }
}
