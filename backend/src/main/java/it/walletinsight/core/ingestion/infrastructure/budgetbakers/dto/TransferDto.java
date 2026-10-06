package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

/** Il blocco `transfer`, sempre presente nella risposta ma spesso null. */
public record TransferDto(String type, String transferId) {
}
