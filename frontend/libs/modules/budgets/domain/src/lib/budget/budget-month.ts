import { CategoryId, Money, moneyRatio } from '@wallet/shared-domain';

import { BudgetId } from './budget';
import { YearMonth } from '@wallet/shared-domain';

export interface CategorySpent {
  readonly categoryId: CategoryId;
  readonly name: string;
  readonly color: string | null;
  readonly spent: Money;
}

/** Un budget in un mese, col limite che aveva allora. `remaining` negativo è lo sforamento. */
export interface BudgetStatus {
  readonly id: BudgetId;
  readonly name: string;
  readonly limit: Money;
  readonly spent: Money;
  readonly remaining: Money;
  readonly categories: readonly CategorySpent[];
  readonly children: readonly BudgetStatus[];
}

/**
 * Un mese rispetto ai budget in vigore allora.
 *
 * I totali sommano solo i principali: la spesa di un sotto-budget è già dentro
 * quella del suo principale. `unbudgeted` è ciò che è uscito fuori da ogni budget,
 * quindi `spent + unbudgeted = expenses`.
 */
export interface BudgetMonth {
  readonly month: YearMonth;
  readonly limit: Money;
  readonly spent: Money;
  readonly remaining: Money;
  readonly unbudgeted: Money;
  readonly expenses: Money;
  readonly budgets: readonly BudgetStatus[];
}

export type BudgetHealth = 'on-track' | 'at-risk' | 'exceeded';

/** Da qui in su un budget è «quasi finito»: ne resta meno di un quinto. */
const AT_RISK_USAGE = 80;

/** Quota di limite consumata, in percentuale: supera 100 quando si è sforato. */
export function budgetUsage(status: Pick<BudgetStatus, 'spent' | 'limit'>): number {
  return Math.max(0, moneyRatio(status.spent, status.limit) * 100);
}

export function budgetHealth(status: Pick<BudgetStatus, 'spent' | 'limit'>): BudgetHealth {
  if (status.spent.amount > status.limit.amount) return 'exceeded';
  return budgetUsage(status) >= AT_RISK_USAGE ? 'at-risk' : 'on-track';
}
