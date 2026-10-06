package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.math.BigDecimal;

/**
 * Importo come lo manda BudgetBakers: decimale, non in centesimi.
 *
 * `value` è dichiarato {@code double} nella specifica, ma qui è un
 * `BigDecimal`: letto come double, -9.99 diventa -9.9900000000000002 e
 * moltiplicato per cento può dare 998 centesimi invece di 999.
 */
public record AmountDto(BigDecimal value, String currencyCode) {
}
