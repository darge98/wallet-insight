import { InjectionToken } from '@angular/core';

import { Budget, BudgetChanges, BudgetDraft, BudgetId } from './budget';
import { BudgetMonth } from './budget-month';
import { YearMonth } from '@wallet/shared-domain';

/**
 * Porta dei budget. Le risposte di scrittura sono il budget come l'ha salvato il
 * server; le regole fra budget le verifica lui, e un rifiuto arriva come
 * {@link BudgetRejectedError} col motivo da mostrare.
 */
export interface BudgetRepository {
  findAll(signal?: AbortSignal): Promise<readonly Budget[]>;
  month(month: YearMonth, signal?: AbortSignal): Promise<BudgetMonth>;
  create(draft: BudgetDraft, signal?: AbortSignal): Promise<Budget>;
  update(id: BudgetId, changes: BudgetChanges, signal?: AbortSignal): Promise<Budget>;
  remove(id: BudgetId, signal?: AbortSignal): Promise<void>;
}

/** Un budget che le regole non ammettono: il messaggio è per l'utente. */
export class BudgetRejectedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BudgetRejectedError';
  }
}

export const BUDGET_REPOSITORY = new InjectionToken<BudgetRepository>('BudgetRepository');
