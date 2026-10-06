package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

/**
 * La categoria, che arriva già denormalizzata dentro ogni movimento.
 *
 * @param parentId la categoria standard da cui deriva, solo per quelle create dall'utente
 */
public record CategoryDto(String id, String name, CategoryGroupDto group, String color, String parentId) {
}
