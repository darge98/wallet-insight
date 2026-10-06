package it.walletinsight.bff.categories;

import it.walletinsight.core.categories.domain.SourceCategory;
import it.walletinsight.platform.web.KebabCase;

/**
 * Una categoria di una sorgente e la categoria di Wallet Insights a cui è agganciata.
 *
 * @param group      il gruppo della sorgente, grezzo (`food_and_drinks`)
 * @param categoryId la categoria di Wallet Insights, assente se non agganciata
 */
public record SourceCategoryResponse(String id, String source, String name, String group, String categoryId) {

    static SourceCategoryResponse from(SourceCategory category) {
        return new SourceCategoryResponse(
                category.id().toString(),
                KebabCase.from(category.source()),
                category.name(),
                category.group(),
                category.category() == null ? null : category.category().toString());
    }
}
