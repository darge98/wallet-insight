import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  AnalyticsRepository,
  CumulativeExpensePoint,
  DateRange,
  KpiSummary,
  Money,
} from '@wallet/shared-domain';

import {
  CumulativeExpenseResponse,
  KpiSummaryResponse,
  NetWorthResponse,
  toCumulativeExpensePoint,
  toKpiSummary,
  toNetWorth,
} from './movement-contract';
import { API_BASE_URL } from './api-base-url';
import { currentUserId } from './current-user';
import { requestJson } from './json-request';

/**
 * I KPI di periodo, la curva delle uscite e il patrimonio, calcolati dal backend.
 *
 * Il periodo di confronto delle tendenze non viaggia: lo decide il server e lo
 * restituisce. Mandarlo da qui vorrebbe dire due definizioni della stessa cosa — una
 * nel client e una nel server — che prima o poi divergono senza che nessuno se ne accorga.
 */
@Injectable()
export class HttpAnalyticsRepository implements AnalyticsRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async kpiSummary(range: DateRange, signal?: AbortSignal): Promise<KpiSummary> {
    const url = await this.urlFor('kpi-summary', range, signal);

    return toKpiSummary(await requestJson<KpiSummaryResponse>(this.http, 'GET', url, { signal }));
  }

  async cumulativeExpenses(
    range: DateRange,
    signal?: AbortSignal,
  ): Promise<readonly CumulativeExpensePoint[]> {
    const url = await this.urlFor('cumulative-expenses', range, signal);
    const points = await requestJson<readonly CumulativeExpenseResponse[]>(this.http, 'GET', url, {
      signal,
    });

    return points.map(toCumulativeExpensePoint);
  }

  async netWorth(signal?: AbortSignal): Promise<Money> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const url = `${this.baseUrl}/users/${userId}/analytics/net-worth`;

    return toNetWorth(await requestJson<NetWorthResponse>(this.http, 'GET', url, { signal }));
  }

  private async urlFor(resource: string, range: DateRange, signal?: AbortSignal): Promise<string> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const params = new HttpParams().set('from', range.from).set('to', range.to);

    return `${this.baseUrl}/users/${userId}/analytics/${resource}?${params.toString()}`;
  }
}
