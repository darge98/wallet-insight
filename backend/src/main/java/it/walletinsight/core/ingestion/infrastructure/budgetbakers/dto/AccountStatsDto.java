package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

/**
 * Le statistiche che BudgetBakers tiene aggiornate per conto.
 *
 * Non entrano nel dominio: servono al log, per dire quanti movimenti ci si deve
 * aspettare da un conto prima ancora di averli letti. Dei molti campi esposti
 * modelliamo solo quello.
 */
public record AccountStatsDto(Integer recordCount) {
}
