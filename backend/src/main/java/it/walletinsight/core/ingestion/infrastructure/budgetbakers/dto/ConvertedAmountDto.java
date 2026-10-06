package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.math.BigDecimal;

/**
 * L'importo di un movimento convertito nella valuta chiesta con `convertTo`, al
 * cambio storico del giorno del movimento.
 *
 * Quando il cambio di quel giorno manca, BudgetBakers valorizza solo
 * `currencyCode` ed `error`.
 */
public record ConvertedAmountDto(BigDecimal value, String currencyCode, BigDecimal ratio, String error) {
}
