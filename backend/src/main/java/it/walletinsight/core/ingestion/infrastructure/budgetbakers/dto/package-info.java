/**
 * Le risposte di BudgetBakers così come arrivano sul filo.
 *
 * Questi tipi non interpretano niente: gli enum restano stringhe, perché una
 * risposta con un valore nuovo deve poter essere letta e segnalata, non far
 * fallire la deserializzazione. La traduzione verso il dominio avviene in
 * {@link it.walletinsight.core.ingestion.infrastructure.budgetbakers.MovementMapper}.
 */
package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;
