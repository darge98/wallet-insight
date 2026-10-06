package it.walletinsight.bff.analytics;

/**
 * Quanto l'utente possiede, da solo: lo mostrano la barra laterale e le Impostazioni,
 * che non hanno un periodo e non devono chiedere i KPI per averlo.
 */
public record NetWorthResponse(long netWorthCents, String currencyCode) {
}
