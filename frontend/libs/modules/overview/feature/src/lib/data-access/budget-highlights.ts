import { BudgetMonth, BudgetStatus, budgetUsage } from '@wallet/budgets-domain';

/**
 * I budget principali del mese dal più consumato, fino a `limit`.
 *
 * La Panoramica non ripete la pagina Budget: chi la guarda vuole sapere dove sta
 * per finire il margine, e quello sta in cima. I sotto-budget restano fuori,
 * perché la loro spesa è già nel principale.
 */
export function budgetHighlights(month: BudgetMonth, limit: number): readonly BudgetStatus[] {
  return [...month.budgets].sort((a, b) => budgetUsage(b) - budgetUsage(a)).slice(0, limit);
}
