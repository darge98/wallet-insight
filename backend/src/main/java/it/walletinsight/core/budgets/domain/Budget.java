package it.walletinsight.core.budgets.domain;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Un limite mensile di spesa su una o più categorie.
 *
 * Senza {@code parentId} è un budget principale; con, è un sotto-budget che prende
 * parte delle categorie del principale. I limiti sono storicizzati: ognuno vale dal
 * proprio mese fino al successivo, e prima del primo il budget non esisteva.
 */
public record Budget(
        BudgetId id,
        UserId userId,
        BudgetId parentId,
        String name,
        Set<CategoryId> categories,
        List<BudgetLimit> limits) {

    public static final int MAX_NAME_LENGTH = 80;

    public Budget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        name = requireName(name);
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("Un budget deve comprendere almeno una categoria.");
        }
        limits = Objects.requireNonNull(limits, "limits").stream()
                .sorted(Comparator.comparing(BudgetLimit::validFrom))
                .toList();
        if (limits.isEmpty()) {
            throw new IllegalArgumentException("Un budget deve avere un limite.");
        }
    }

    public static Budget create(UserId userId, BudgetId parentId, String name,
                                Set<CategoryId> categories, YearMonth from, Money limit) {
        return new Budget(BudgetId.newId(), userId, parentId, name, categories,
                List.of(new BudgetLimit(from, limit)));
    }

    public boolean isMain() {
        return parentId == null;
    }

    /** Il limite in vigore nel mese, vuoto se il budget non esisteva ancora. */
    public Optional<Money> limitIn(YearMonth month) {
        Money inVigore = null;
        for (BudgetLimit limite : limits) {
            if (limite.validFrom().isAfter(month)) {
                break;
            }
            inVigore = limite.amount();
        }
        return Optional.ofNullable(inVigore);
    }

    public Budget renamedTo(String newName) {
        return new Budget(id, userId, parentId, newName, categories, limits);
    }

    public Budget covering(Set<CategoryId> newCategories) {
        return new Budget(id, userId, parentId, name, newCategories, limits);
    }

    /** Fissa il limite da {@code month} in poi: i mesi precedenti restano col loro. */
    public Budget limitedFrom(YearMonth month, Money amount) {
        List<BudgetLimit> nuovi = limits.stream()
                .filter(limite -> limite.validFrom().isBefore(month))
                .collect(Collectors.toCollection(ArrayList::new));
        nuovi.add(new BudgetLimit(month, amount));
        return new Budget(id, userId, parentId, name, categories, nuovi);
    }

    private static String requireName(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Il nome del budget è obbligatorio.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Il nome del budget supera i %d caratteri.".formatted(MAX_NAME_LENGTH));
        }
        return trimmed;
    }
}
