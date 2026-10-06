package it.walletinsight.bff.categories;

import it.walletinsight.core.budgets.application.BudgetService;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.SourceCategory;
import it.walletinsight.core.categories.domain.SourceCategoryId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.subscriptions.application.SubscriptionService;
import it.walletinsight.core.users.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Le riorganizzazioni che spostano movimenti (e abbonamenti): unire una categoria in un'altra e
 * riagganciare una categoria della sorgente. Toccano due moduli che non si
 * conoscono, e stanno in una transazione sola perché un movimento non resti a metà
 * strada.
 */
@Component
class CategoryReorganization {

    private final CategoryService categories;
    private final MovementService movements;
    private final BudgetService budgets;
    private final SubscriptionService subscriptions;

    CategoryReorganization(CategoryService categories, MovementService movements, BudgetService budgets,
                           SubscriptionService subscriptions) {
        this.categories = categories;
        this.movements = movements;
        this.budgets = budgets;
        this.subscriptions = subscriptions;
    }

    /**
     * Toglie una categoria spostandone i movimenti in {@code into}. Una categoria in
     * un budget non si toglie: il budget perderebbe in silenzio una parte della spesa.
     * Gli abbonamenti invece la seguono, come i movimenti: dicono solo dove cadrà l'addebito.
     */
    @Transactional
    void delete(UserId userId, CategoryId id, CategoryId into) {
        List<String> inUso = budgets.budgetsUsing(userId, id);
        if (!inUso.isEmpty()) {
            throw new IllegalStateException(
                    "La categoria è nel budget «%s»: toglila prima da lì.".formatted(inUso.getFirst()));
        }
        if (categories.findCategory(userId, id).map(categoria -> !categoria.isMacro()).orElse(false)) {
            categories.requireSubcategoryTarget(userId, id, into);
            movements.reassignCategory(userId, id, into);
            subscriptions.reassignCategory(userId, id, into);
        }
        categories.deleteCategory(userId, id, into);
    }

    /** Riaggancia e sposta i movimenti già importati che erano dove l'aggancio li aveva messi. */
    @Transactional
    SourceCategory relink(UserId userId, SourceCategoryId id, CategoryId categoryId) {
        SourceCategory prima = categories.relinkSourceCategory(userId, id, categoryId);
        movements.reassignFromSource(userId, prima.source(), prima.externalId(), prima.category(), categoryId);
        return prima.linkedTo(categoryId);
    }
}
