package it.walletinsight.bff.categories;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** @param categoryId la sottocategoria di Wallet Insights in cui far confluire la categoria della sorgente */
public record RelinkSourceCategoryRequest(@NotNull UUID categoryId) {
}
