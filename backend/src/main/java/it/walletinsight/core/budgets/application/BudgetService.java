package it.walletinsight.core.budgets.application;

import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.budgets.domain.BudgetId;
import it.walletinsight.core.budgets.domain.BudgetPlan;
import it.walletinsight.core.budgets.domain.BudgetRepository;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.money.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Casi d'uso dei budget.
 *
 * Un limite nuovo vale dal mese corrente, quello del fuso dell'utente: i mesi
 * passati restano confrontati col limite che avevano.
 */
@Service
@Transactional
public class BudgetService {

    private static final String USER_RESOURCE_TYPE = "Utente";
    private static final String BUDGET_RESOURCE_TYPE = "Budget";
    private static final String CATEGORY_RESOURCE_TYPE = "Categoria";

    private final BudgetRepository repository;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public BudgetService(BudgetRepository repository,
                         CategoryRepository categories,
                         UserRepository users) {
        this(repository, categories, users, Clock.systemUTC());
    }

    BudgetService(BudgetRepository repository,
                  CategoryRepository categories,
                  UserRepository users,
                  Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Budget> listBudgets(UserId userId) {
        requireUser(userId);
        return repository.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public YearMonth currentMonth(UserId userId) {
        return currentMonth(requireUser(userId));
    }

    public Budget createBudget(UserId userId, BudgetId parentId, String name,
                               Set<CategoryId> categoryIds, Money limit) {
        User utente = requireUser(userId);
        requireOwnCategories(userId, categoryIds);
        BudgetPlan piano = new BudgetPlan(repository.findByUser(userId));
        if (parentId != null && piano.find(parentId).isEmpty()) {
            throw new ResourceNotFoundException(BUDGET_RESOURCE_TYPE, parentId.toString());
        }

        YearMonth mese = currentMonth(utente);
        Budget nuovo = Budget.create(userId, parentId, name, categoryIds, mese, limit);
        piano.requireCompatible(nuovo, mese);
        repository.insert(nuovo);
        return nuovo;
    }

    /** Un parametro {@code null} vuol dire «non toccare», come nel PATCH da cui arriva. */
    public Budget updateBudget(UserId userId, BudgetId budgetId, String name,
                               Set<CategoryId> categoryIds, Money limit) {
        User utente = requireUser(userId);
        BudgetPlan piano = new BudgetPlan(repository.findByUser(userId));
        Budget budget = piano.find(budgetId).orElseThrow(
                () -> new ResourceNotFoundException(BUDGET_RESOURCE_TYPE, budgetId.toString()));

        YearMonth mese = currentMonth(utente);
        Budget aggiornato = budget;
        if (name != null) {
            aggiornato = aggiornato.renamedTo(name);
        }
        if (categoryIds != null) {
            requireOwnCategories(userId, categoryIds);
            aggiornato = aggiornato.covering(categoryIds);
        }
        if (limit != null && !budget.limitIn(mese).map(limit::equals).orElse(false)) {
            aggiornato = aggiornato.limitedFrom(mese, limit);
        }
        if (aggiornato.equals(budget)) {
            return budget;
        }

        piano.requireCompatible(aggiornato, mese);
        repository.update(aggiornato);
        return aggiornato;
    }

    public void deleteBudget(UserId userId, BudgetId budgetId) {
        requireUser(userId);
        if (new BudgetPlan(repository.findByUser(userId)).find(budgetId).isEmpty()) {
            throw new ResourceNotFoundException(BUDGET_RESOURCE_TYPE, budgetId.toString());
        }
        repository.delete(userId, budgetId);
    }

    /** I nomi dei budget che comprendono la categoria. */
    @Transactional(readOnly = true)
    public List<String> budgetsUsing(UserId userId, CategoryId categoryId) {
        return repository.findByUser(userId).stream()
                .filter(budget -> budget.categories().contains(categoryId))
                .map(Budget::name)
                .toList();
    }

    /**
     * Un budget comprende sottocategorie: la spesa sta lì. Una categoria di un altro
     * utente è un 404, non un 403: dire «vietato» confermerebbe che esiste.
     */
    private void requireOwnCategories(UserId userId, Set<CategoryId> categoryIds) {
        Map<CategoryId, Category> proprie = categories.findByUser(userId).stream()
                .collect(Collectors.toMap(Category::id, categoria -> categoria));
        for (CategoryId id : categoryIds) {
            Category categoria = proprie.get(id);
            if (categoria == null) {
                throw new ResourceNotFoundException(CATEGORY_RESOURCE_TYPE, id.toString());
            }
            if (categoria.isMacro()) {
                throw new IllegalArgumentException(
                        "«%s» è una macro: scegli le sue sottocategorie.".formatted(categoria.name()));
            }
        }
    }

    private YearMonth currentMonth(User user) {
        return YearMonth.now(clock.withZone(ZoneId.of(user.settings().timeZone())));
    }

    private User requireUser(UserId userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString()));
    }
}
