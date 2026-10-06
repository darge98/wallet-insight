package it.walletinsight.bff.categories;

import it.walletinsight.core.categories.domain.Category;

/**
 * Una categoria di Wallet Insights.
 *
 * @param parentId la macro che la contiene, assente se è una macro
 * @param color    `#rrggbb`, assente se l'utente non ne ha scelto uno
 */
public record CategoryResponse(String id, String parentId, String name, String color) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.id().toString(),
                category.parentId() == null ? null : category.parentId().toString(),
                category.name(),
                category.color());
    }
}
