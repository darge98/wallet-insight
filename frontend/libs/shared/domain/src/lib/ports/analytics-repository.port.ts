import { InjectionToken } from '@angular/core';
import { CumulativeExpensePoint } from '../analytics/cumulative-expense';
import { KpiSummary } from '../analytics/kpi-summary';
import { DateRange } from '../shared/date-range';
import { Money } from '../shared/money';

/**
 * Gli aggregati calcolati dal backend: i KPI del periodo, la curva delle uscite e il
 * patrimonio. Il client li mostra e non li ricompone: sommare in due posti diversi è il
 * modo più sicuro di ottenere due risposte diverse.
 */
export interface AnalyticsRepository {
  kpiSummary(range: DateRange, signal?: AbortSignal): Promise<KpiSummary>;
  cumulativeExpenses(
    range: DateRange,
    signal?: AbortSignal,
  ): Promise<readonly CumulativeExpensePoint[]>;
  /** Quanto l'utente possiede oggi, in euro: lo stesso numero dei KPI. */
  netWorth(signal?: AbortSignal): Promise<Money>;
}

export const ANALYTICS_REPOSITORY = new InjectionToken<AnalyticsRepository>('AnalyticsRepository');
