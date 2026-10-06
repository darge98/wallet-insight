package it.walletinsight.core.budgets.infrastructure.jdbc;

import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.budgets.domain.BudgetId;
import it.walletinsight.core.budgets.domain.BudgetLimit;
import it.walletinsight.core.budgets.domain.BudgetRepository;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Adapter JDBC dei budget: tutto il loro SQL vive qui. */
@Repository
class JdbcBudgetRepository implements BudgetRepository {

    private final JdbcClient jdbc;

    JdbcBudgetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Budget> findByUser(UserId userId) {
        Map<UUID, Set<CategoryId>> categorie = new HashMap<>();
        jdbc.sql("select budget_id, category_id from budget_categories where user_id = :userId")
                .param("userId", userId.value())
                .query(rs -> {
                    categorie.computeIfAbsent(rs.getObject("budget_id", UUID.class), id -> new HashSet<>())
                            .add(CategoryId.of(rs.getObject("category_id", UUID.class)));
                });

        Map<UUID, List<BudgetLimit>> limiti = new HashMap<>();
        jdbc.sql("""
                select l.budget_id, l.valid_from, l.amount_cents
                  from budget_limits l
                  join budgets b on b.id = l.budget_id
                 where b.user_id = :userId
                """)
                .param("userId", userId.value())
                .query(rs -> {
                    limiti.computeIfAbsent(rs.getObject("budget_id", UUID.class), id -> new ArrayList<>())
                            .add(new BudgetLimit(
                                    YearMonth.from(rs.getObject("valid_from", LocalDate.class)),
                                    Money.of(rs.getLong("amount_cents"))));
                });

        return jdbc.sql("""
                select id, parent_id, name from budgets
                 where user_id = :userId
                 order by lower(name), id
                """)
                .param("userId", userId.value())
                .query((rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    UUID principale = rs.getObject("parent_id", UUID.class);
                    return new Budget(
                            BudgetId.of(id),
                            userId,
                            principale == null ? null : BudgetId.of(principale),
                            rs.getString("name"),
                            categorie.getOrDefault(id, Set.of()),
                            limiti.getOrDefault(id, List.of()));
                })
                .list();
    }

    @Override
    public void insert(Budget budget) {
        jdbc.sql("""
                insert into budgets (id, user_id, parent_id, name)
                values (:id, :userId, :parentId, :name)
                """)
                .param("id", budget.id().value())
                .param("userId", budget.userId().value())
                .param("parentId", parentOf(budget))
                .param("name", budget.name())
                .update();
        insertCategories(budget, budget.categories());
        writeLimits(budget);
    }

    @Override
    public void update(Budget budget) {
        jdbc.sql("""
                update budgets set name = :name, updated_at = now()
                 where id = :id and user_id = :userId
                """)
                .param("id", budget.id().value())
                .param("userId", budget.userId().value())
                .param("name", budget.name())
                .update();

        // Solo la differenza, non cancella-e-reinserisci: le righe di un principale
        // sono il bersaglio della foreign key dei suoi sotto-budget, e toglierle
        // tutte anche per un istante la violerebbe.
        Set<CategoryId> attuali = new HashSet<>(jdbc.sql(
                        "select category_id from budget_categories where budget_id = :id")
                .param("id", budget.id().value())
                .query((rs, rowNum) -> CategoryId.of(rs.getObject("category_id", UUID.class)))
                .list());
        List<UUID> tolte = attuali.stream()
                .filter(categoria -> !budget.categories().contains(categoria))
                .map(CategoryId::value)
                .toList();
        if (!tolte.isEmpty()) {
            jdbc.sql("delete from budget_categories where budget_id = :id and category_id in (:tolte)")
                    .param("id", budget.id().value())
                    .param("tolte", tolte)
                    .update();
        }
        insertCategories(budget, budget.categories().stream()
                .filter(categoria -> !attuali.contains(categoria))
                .toList());

        writeLimits(budget);
    }

    @Override
    public void delete(UserId userId, BudgetId id) {
        jdbc.sql("delete from budgets where id = :id and user_id = :userId")
                .param("id", id.value())
                .param("userId", userId.value())
                .update();
    }

    private void insertCategories(Budget budget, Iterable<CategoryId> categories) {
        for (CategoryId categoria : categories) {
            jdbc.sql("""
                    insert into budget_categories (budget_id, user_id, category_id, parent_budget_id)
                    values (:budgetId, :userId, :categoryId, :parentId)
                    """)
                    .param("budgetId", budget.id().value())
                    .param("userId", budget.userId().value())
                    .param("categoryId", categoria.value())
                    .param("parentId", parentOf(budget))
                    .update();
        }
    }

    private void writeLimits(Budget budget) {
        List<LocalDate> mesi = budget.limits().stream()
                .map(limite -> limite.validFrom().atDay(1))
                .toList();
        jdbc.sql("delete from budget_limits where budget_id = :id and valid_from not in (:mesi)")
                .param("id", budget.id().value())
                .param("mesi", mesi)
                .update();
        for (BudgetLimit limite : budget.limits()) {
            jdbc.sql("""
                    insert into budget_limits (budget_id, valid_from, amount_cents)
                    values (:id, :validFrom, :amount)
                    on conflict (budget_id, valid_from) do update set amount_cents = excluded.amount_cents
                    """)
                    .param("id", budget.id().value())
                    .param("validFrom", limite.validFrom().atDay(1))
                    .param("amount", limite.amount().amount())
                    .update();
        }
    }

    private static UUID parentOf(Budget budget) {
        return budget.parentId() == null ? null : budget.parentId().value();
    }
}
