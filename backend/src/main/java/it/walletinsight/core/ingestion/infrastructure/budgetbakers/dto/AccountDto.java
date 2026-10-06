package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.time.Instant;

/**
 * Un conto come arriva da {@code GET /v1/api/accounts}.
 *
 * Come per i movimenti, nessun campo è dichiarato obbligatorio: sono tutti tipi
 * che ammettono null. `bankAccountNumber` sparisce proprio come chiave sui conti
 * che non ne hanno (i contanti).
 *
 * `balance` e `isBankSync` sono dichiarati read-only dalla specifica: li leggiamo
 * e basta. `color`, `isInvestmentAccount` e i campi di `recordStats` oltre al
 * conteggio non sono modellati; la deserializzazione ignora ciò che non conosce.
 */
public record AccountDto(
        String id,
        String name,
        String accountType,
        String currencyCode,
        Boolean archived,
        String bankAccountNumber,
        Boolean isBankSync,
        Boolean excludeFromStats,
        AccountBalanceDto balance,
        AccountStatsDto recordStats,
        Instant createdAt,
        Instant updatedAt) {
}
