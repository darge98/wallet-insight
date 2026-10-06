import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  MonthCharges,
  Subscription,
  SubscriptionDraft,
  SubscriptionId,
  SubscriptionRejectedError,
  SubscriptionRepository,
  SubscriptionsOverview,
} from '@wallet/subscriptions-domain';
import { API_BASE_URL, currentUserId, requestJson } from '@wallet/shared-data-access';
import { YearMonth } from '@wallet/shared-domain';

import {
  MonthChargesResponse,
  SubscriptionResponse,
  SubscriptionsOverviewResponse,
  toMonthCharges,
  toOverview,
  toRequest,
  toSubscription,
} from './subscription-contract';

@Injectable()
export class HttpSubscriptionRepository implements SubscriptionRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async findAll(signal?: AbortSignal): Promise<readonly Subscription[]> {
    const subscriptions = await requestJson<readonly SubscriptionResponse[]>(
      this.http,
      'GET',
      await this.subscriptionsUrl(signal),
      { signal },
    );
    return subscriptions.map(toSubscription);
  }

  async overview(days: number, signal?: AbortSignal): Promise<SubscriptionsOverview> {
    const overview = await requestJson<SubscriptionsOverviewResponse>(
      this.http,
      'GET',
      `${await this.subscriptionsUrl(signal)}/overview?days=${days}`,
      { signal },
    );
    return toOverview(overview);
  }

  async calendar(month: YearMonth, signal?: AbortSignal): Promise<MonthCharges> {
    const charges = await requestJson<MonthChargesResponse>(
      this.http,
      'GET',
      `${await this.subscriptionsUrl(signal)}/calendar?month=${encodeURIComponent(month)}`,
      { signal },
    );
    return toMonthCharges(charges);
  }

  async create(draft: SubscriptionDraft, signal?: AbortSignal): Promise<Subscription> {
    return toSubscription(
      await rejectingInvalid(
        requestJson<SubscriptionResponse>(this.http, 'POST', await this.subscriptionsUrl(signal), {
          body: toRequest(draft),
          signal,
        }),
      ),
    );
  }

  async update(
    id: SubscriptionId,
    draft: SubscriptionDraft,
    signal?: AbortSignal,
  ): Promise<Subscription> {
    return toSubscription(
      await rejectingInvalid(
        requestJson<SubscriptionResponse>(
          this.http,
          'PUT',
          `${await this.subscriptionsUrl(signal)}/${id}`,
          { body: toRequest(draft), signal },
        ),
      ),
    );
  }

  async remove(id: SubscriptionId, signal?: AbortSignal): Promise<void> {
    const url = `${await this.subscriptionsUrl(signal)}/${id}`;
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

  private async subscriptionsUrl(signal?: AbortSignal): Promise<string> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    return `${this.baseUrl}/users/${userId}/subscriptions`;
  }
}

/**
 * Un 400 col `detail` è una regola del dominio (la fine prima del primo addebito)
 * scritta per l'utente; il pannello ne previene la gran parte, il server resta l'arbitro.
 */
async function rejectingInvalid<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (cause) {
    if (cause instanceof HttpErrorResponse && (cause.status === 400 || cause.status === 409)) {
      const detail = (cause.error as { detail?: unknown } | null)?.detail;
      throw new SubscriptionRejectedError(
        typeof detail === 'string' ? detail : 'Controlla i dati dell’abbonamento.',
      );
    }
    throw cause;
  }
}
