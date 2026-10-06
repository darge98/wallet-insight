package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

/**
 * Un'etichetta applicata a un movimento.
 *
 * Nei 1672 movimenti osservati l'array è sempre vuoto; il tipo esiste perché
 * il campo è dichiarato e potrebbe riempirsi.
 */
public record LabelDto(String id, String name, String color) {
}
