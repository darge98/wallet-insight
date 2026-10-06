import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  CategoryBreakdownSlice,
  SpendingBreakdownRepository,
  SpendingScope,
  SpendingTarget,
} from '@wallet/shared-domain';

import {
  CategorySpendingResponse,
  CounterPartySpendingResponse,
  toCategoryBreakdownSlice,
  toSpendingTarget,
} from './movement-contract';
import { API_BASE_URL } from './api-base-url';
import { currentUserId } from './current-user';
import { requestJson } from './json-request';

/**
 * Le classifiche di periodo calcolate dal backend.
 *
 * Il nome e il colore delle categorie arrivano già dentro la risposta: a
 * comporli con gli importi è il BFF, che è l'unico posto che vede sia i
 * movimenti sia l'anagrafica. Qui non si incrocia niente, e soprattutto non si
 * scarica lo storico per sommarlo.
 */
@Injectable()
export class HttpSpendingBreakdownRepository implements SpendingBreakdownRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async expensesByCategory(
    scope: SpendingScope,
    limit: number,
    signal?: AbortSignal,
  ): Promise<readonly CategoryBreakdownSlice[]> {
    const url = await this.urlFor('expenses-by-category', scope, limit, signal);
    const slices = await requestJson<readonly CategorySpendingResponse[]>(this.http, 'GET', url, {
      signal,
    });

    return slices.map(toCategoryBreakdownSlice);
  }

  async topSpendingTargets(
    scope: SpendingScope,
    limit: number,
    signal?: AbortSignal,
  ): Promise<readonly SpendingTarget[]> {
    const url = await this.urlFor('top-counter-parties', scope, limit, signal);
    const targets = await requestJson<readonly CounterPartySpendingResponse[]>(
      this.http,
      'GET',
      url,
      { signal },
    );

    return targets.map(toSpendingTarget);
  }

  private async urlFor(
    resource: string,
    { range, accountIds = [] }: SpendingScope,
    limit: number,
    signal?: AbortSignal,
  ): Promise<string> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    let params = new HttpParams().set('from', range.from).set('to', range.to).set('limit', limit);
    for (const accountId of accountIds) {
      params = params.append('accountId', accountId);
    }

    return `${this.baseUrl}/users/${userId}/analytics/${resource}?${params.toString()}`;
  }
}
