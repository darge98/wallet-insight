import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  Budget,
  BudgetChanges,
  BudgetDraft,
  BudgetId,
  BudgetMonth,
  BudgetRejectedError,
  BudgetRepository,
} from '@wallet/budgets-domain';
import { YearMonth } from '@wallet/shared-domain';
import { API_BASE_URL, currentUserId, requestJson } from '@wallet/shared-data-access';

import { BudgetMonthResponse, BudgetResponse, toBudget, toBudgetMonth } from './budget-contract';

@Injectable()
export class HttpBudgetRepository implements BudgetRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async findAll(signal?: AbortSignal): Promise<readonly Budget[]> {
    const budgets = await requestJson<readonly BudgetResponse[]>(
      this.http,
      'GET',
      await this.budgetsUrl(signal),
      { signal },
    );
    return budgets.map(toBudget);
  }

  async month(month: YearMonth, signal?: AbortSignal): Promise<BudgetMonth> {
    const report = await requestJson<BudgetMonthResponse>(
      this.http,
      'GET',
      `${await this.budgetsUrl(signal)}/month?month=${encodeURIComponent(month)}`,
      { signal },
    );
    return toBudgetMonth(report);
  }

  async create(draft: BudgetDraft, signal?: AbortSignal): Promise<Budget> {
    const body = {
      name: draft.name,
      parentId: draft.parentId ?? undefined,
      categoryIds: draft.categoryIds,
      limitCents: draft.limit.amount,
    };
    return toBudget(
      await rejectingRules(
        requestJson<BudgetResponse>(this.http, 'POST', await this.budgetsUrl(signal), {
          body,
          signal,
        }),
      ),
    );
  }

  async update(id: BudgetId, changes: BudgetChanges, signal?: AbortSignal): Promise<Budget> {
    // Solo i campi cambiati: un campo assente vale «non toccare».
    const body = {
      name: changes.name,
      categoryIds: changes.categoryIds,
      limitCents: changes.limit?.amount,
    };
    return toBudget(
      await rejectingRules(
        requestJson<BudgetResponse>(this.http, 'PATCH', `${await this.budgetsUrl(signal)}/${id}`, {
          body,
          signal,
        }),
      ),
    );
  }

  async remove(id: BudgetId, signal?: AbortSignal): Promise<void> {
    const url = `${await this.budgetsUrl(signal)}/${id}`;
    // Un 204 non ha corpo: `requestJson` lo considererebbe un contratto rotto.
    await new Promise<void>((resolve, reject) => {
      const subscription = this.http.delete(url, { observe: 'response' }).subscribe({
        next: () => resolve(),
        error: reject,
      });
      signal?.addEventListener(
        'abort',
        () => {
          subscription.unsubscribe();
          reject(signal.reason);
        },
        { once: true },
      );
    });
  }

  private async budgetsUrl(signal?: AbortSignal): Promise<string> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    return `${this.baseUrl}/users/${userId}/budgets`;
  }
}

/**
 * Un 409 è una regola fra budget violata (due principali sulla stessa categoria,
 * sotto-budget oltre il principale) e il suo `detail` è scritto per l'utente.
 */
async function rejectingRules<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (cause) {
    if (cause instanceof HttpErrorResponse && cause.status === 409) {
      const detail = (cause.error as { detail?: unknown } | null)?.detail;
      throw new BudgetRejectedError(
        typeof detail === 'string' ? detail : 'Questo budget non è compatibile con gli altri.',
      );
    }
    throw cause;
  }
}
