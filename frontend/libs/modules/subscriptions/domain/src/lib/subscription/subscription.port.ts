import { InjectionToken } from '@angular/core';

import { YearMonth } from '@wallet/shared-domain';

import { Subscription, SubscriptionDraft, SubscriptionId } from './subscription';
import { MonthCharges, SubscriptionsOverview } from './subscriptions-overview';

/**
 * Porta degli abbonamenti. Le risposte di scrittura sono l'abbonamento come l'ha
 * salvato il server; un rifiuto arriva come {@link SubscriptionRejectedError}.
 */
export interface SubscriptionRepository {
  findAll(signal?: AbortSignal): Promise<readonly Subscription[]>;
  overview(days: number, signal?: AbortSignal): Promise<SubscriptionsOverview>;
  calendar(month: YearMonth, signal?: AbortSignal): Promise<MonthCharges>;
  create(draft: SubscriptionDraft, signal?: AbortSignal): Promise<Subscription>;
  update(id: SubscriptionId, draft: SubscriptionDraft, signal?: AbortSignal): Promise<Subscription>;
  remove(id: SubscriptionId, signal?: AbortSignal): Promise<void>;
}

/** Un abbonamento che il server non accetta: il messaggio è per l'utente. */
export class SubscriptionRejectedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SubscriptionRejectedError';
  }
}

export const SUBSCRIPTION_REPOSITORY = new InjectionToken<SubscriptionRepository>(
  'SubscriptionRepository',
);
