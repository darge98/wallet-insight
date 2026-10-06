package it.walletinsight.bff.budgets;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.walletinsight.bff.budgets.BudgetMonthResponse.BudgetStatusResponse;
import it.walletinsight.bff.budgets.BudgetMonthResponse.CategorySpentResponse;
import it.walletinsight.core.budgets.application.BudgetService;
import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.budgets.domain.BudgetId;
import it.walletinsight.core.budgets.domain.BudgetPlan;
import it.walletinsight.core.budgets.domain.MonthlyBudgets;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.CategorySpending;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ApiPaths;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.money.Money;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * I budget: definirli, e vedere un mese rispetto a loro.
 *
 * Il resoconto del mese è composto qui perché tocca tre moduli che non si
 * conoscono: i limiti sono di `core.budgets`, la spesa di `core.movements`, i
 * nomi delle categorie di `core.categories`.
 */
@RestController
@RequestMapping(ApiPaths.API + "/users/{userId}/budgets")
@Tag(name = "Budgets")
class BudgetsController {

    private final BudgetService budgets;
    private final MovementService movements;
    private final CategoryService categories;

    BudgetsController(BudgetService budgets, MovementService movements, CategoryService categories) {
        this.budgets = budgets;
        this.movements = movements;
        this.categories = categories;
    }

    @GetMapping
    List<BudgetResponse> listBudgets(@PathVariable UUID userId) {
        UserId id = UserId.of(userId);
        YearMonth mese = budgets.currentMonth(id);
        return budgets.listBudgets(id).stream()
                .map(budget -> BudgetResponse.from(budget, mese))
                .toList();
    }

    @PostMapping
    ResponseEntity<BudgetResponse> createBudget(
            @PathVariable UUID userId, @Valid @RequestBody CreateBudgetRequest request) {
        UserId id = UserId.of(userId);
        Budget creato = budgets.createBudget(
                id,
                request.parentId() == null ? null : BudgetId.of(request.parentId()),
                request.name(),
                categoryIdsOf(request.categoryIds()),
                Money.of(request.limitCents()));
        URI location = URI.create(ApiPaths.API + "/users/" + userId + "/budgets/" + creato.id());
        return ResponseEntity.created(location)
                .body(BudgetResponse.from(creato, budgets.currentMonth(id)));
    }

    @PatchMapping("/{budgetId}")
    BudgetResponse updateBudget(
            @PathVariable UUID userId,
            @PathVariable UUID budgetId,
            @Valid @RequestBody UpdateBudgetRequest request) {
        UserId id = UserId.of(userId);
        Budget aggiornato = budgets.updateBudget(
                id,
                BudgetId.of(budgetId),
                request.name(),
                request.categoryIds() == null ? null : categoryIdsOf(request.categoryIds()),
                request.limitCents() == null ? null : Money.of(request.limitCents()));
        return BudgetResponse.from(aggiornato, budgets.currentMonth(id));
    }

    /** Cancellare un budget principale porta via i suoi sotto-budget. */
    @DeleteMapping("/{budgetId}")
    ResponseEntity<Void> deleteBudget(@PathVariable UUID userId, @PathVariable UUID budgetId) {
        budgets.deleteBudget(UserId.of(userId), BudgetId.of(budgetId));
        return ResponseEntity.noContent().build();
    }

    /** Il mese chiesto rispetto ai budget in vigore allora, col limite che avevano. */
    @GetMapping("/month")
    BudgetMonthResponse month(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        UserId id = UserId.of(userId);
        BudgetPlan piano = new BudgetPlan(budgets.listBudgets(id));
        DateRange periodo = new DateRange(month.atDay(1), month.atEndOfMonth());

        Map<CategoryId, Money> spesa = new HashMap<>();
        if (!piano.categories().isEmpty()) {
            MovementFilter filtro = new MovementFilter(periodo, Set.of(), List.of(), piano.categories());
            for (CategorySpending voce : movements.expensesByCategory(id, filtro, Integer.MAX_VALUE)) {
                spesa.put(voce.categoryId(), voce.total());
            }
        }
        Money uscite = movements.totals(id, MovementFilter.inPeriod(periodo)).expenses();
        MonthlyBudgets resoconto = MonthlyBudgets.of(month, piano.all(), spesa, uscite);

        Map<CategoryId, Category> anagrafica = categories.listCategories(id).stream()
                .collect(Collectors.toMap(Category::id, categoria -> categoria));

        return new BudgetMonthResponse(
                month.toString(),
                uscite.currency().name(),
                resoconto.limit().amount(),
                resoconto.spent().amount(),
                resoconto.remaining().amount(),
                resoconto.unbudgeted().amount(),
                resoconto.expenses().amount(),
                resoconto.budgets().stream()
                        .map(stato -> toResponse(stato, spesa, anagrafica))
                        .toList());
    }

    private static BudgetStatusResponse toResponse(MonthlyBudgets.Status stato,
                                                   Map<CategoryId, Money> spesa,
                                                   Map<CategoryId, Category> anagrafica) {
        List<CategorySpentResponse> voci = stato.budget().categories().stream()
                .map(categoriaId -> {
                    Category categoria = anagrafica.get(categoriaId);
                    return new CategorySpentResponse(
                            categoriaId.toString(),
                            categoria == null ? "Categoria sconosciuta" : categoria.name(),
                            categoria == null ? null : categoria.color(),
                            spesa.getOrDefault(categoriaId, Money.zero()).amount());
                })
                .sorted(Comparator.comparingLong(CategorySpentResponse::spentCents).reversed()
                        .thenComparing(CategorySpentResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new BudgetStatusResponse(
                stato.budget().id().toString(),
                stato.budget().name(),
                stato.limit().amount(),
                stato.spent().amount(),
                stato.remaining().amount(),
                voci,
                stato.children().stream().map(figlio -> toResponse(figlio, spesa, anagrafica)).toList());
    }

    private static Set<CategoryId> categoryIdsOf(List<UUID> ids) {
        return ids.stream().map(CategoryId::of).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
