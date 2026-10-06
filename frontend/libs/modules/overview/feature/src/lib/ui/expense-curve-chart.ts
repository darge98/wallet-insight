import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';

import {
  Chart,
  ChartTheme,
  EmptyState,
  formatCompactDate,
  formatCount,
  formatMoney,
  Skeleton,
} from '@wallet/shared-ui';
import { CumulativeExpensePoint, DateRange, IsoDate, money } from '@wallet/shared-domain';

import { CurvePoint, expenseCurveSeries } from './expense-curve-series';

const CHART_HEIGHT = 232;
const TARGET_AXIS_LABELS = 4;

/**
 * Crescita della spesa nel periodo.
 *
 * Una sola serie, una sola tinta: la grandezza è codificata dalla posizione,
 * quindi non serve una palette categoriale. Il punto finale è evidenziato
 * perché è il valore che si legge davvero.
 */
@Component({
  selector: 'app-expense-curve-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Chart, EmptyState, Skeleton],
  // Riempie la card: accanto c'è la torta con la sua legenda, più alta, e le
  // due card devono finire alla stessa riga.
  host: { class: 'flex flex-1 flex-col' },
  template: `
    @if (loading() && points().length === 0) {
      <app-skeleton [height]="chartHeight" />
    } @else if (points().length === 0) {
      <app-empty-state title="Nessuna spesa nel periodo" />
    } @else {
      <app-chart
        [options]="options()"
        [height]="chartHeight"
        [fill]="true"
        [description]="accessibleDescription()"
      />
    }
  `,
})
export class ExpenseCurveChart {
  private readonly chartTheme = inject(ChartTheme);

  readonly points = input.required<readonly CumulativeExpensePoint[]>();
  /** Il periodo intero: la curva lo copre tutto, anche i giorni senza spese ai due capi. */
  readonly range = input.required<DateRange>();
  readonly loading = input(false);

  protected readonly chartHeight = CHART_HEIGHT;

  protected readonly accessibleDescription = computed(() => {
    const points = this.points();
    const first = points[0];
    const last = points.at(-1);
    if (!first || !last) return 'Crescita della spesa nel periodo';
    return `Spesa cumulata dal ${formatCompactDate(first.date)} al ${formatCompactDate(
      last.date,
    )}: arriva a ${formatMoney(last.total)}.`;
  });

  protected readonly options = computed<EChartsCoreOption>(() => {
    const tokens = this.chartTheme.tokens();
    const series = expenseCurveSeries(this.points(), this.range());
    if (series === null) return {};
    return {
      ...this.chartTheme.baseOption(),
      // I giorni sono mezzanotti UTC: l'asse le legge in UTC, altrimenti le etichette
      // scivolerebbero sul giorno prima nei fusi a ovest di Greenwich.
      useUTC: true,
      grid: { top: 10, left: 2, right: 10, bottom: 0, containLabel: true },
      tooltip: {
        ...(this.chartTheme.baseOption()['tooltip'] as object),
        trigger: 'axis',
        axisPointer: { type: 'line', lineStyle: { color: tokens.axis, width: 1, type: 'dashed' } },
        formatter: (params: unknown) => {
          const entries = (Array.isArray(params) ? params : [params]) as {
            value?: CurvePoint;
          }[];
          const [giorno = 0, euro = 0] = entries[0]?.value ?? [];
          return `<div style="color:${tokens.inkMuted};font-size:11px">${formatCompactDate(
            isoDay(giorno),
          )}</div><div style="margin-top:2px;font-weight:500;font-variant-numeric:tabular-nums">${formatMoney(
            money(Math.round(euro * 100)),
          )}</div>`;
        },
      },
      // Un asse di calendario e non di categorie: i giorni senza spese occupano il loro
      // spazio, e la pendenza torna a dire con che ritmo si spende.
      xAxis: {
        type: 'time',
        min: series.data[0]?.[0],
        max: series.data.at(-1)?.[0],
        splitNumber: TARGET_AXIS_LABELS,
        axisTick: { show: false },
        axisLine: { show: false },
        splitLine: { show: false },
        axisLabel: {
          color: tokens.inkMuted,
          fontSize: 11,
          margin: 14,
          hideOverlap: true,
          formatter: (value: number) => formatCompactDate(isoDay(value)),
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        splitNumber: 2,
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: tokens.grid } },
        axisLabel: {
          color: tokens.inkMuted,
          fontSize: 11,
          margin: 12,
          formatter: (value: number) => formatCount(Math.round(value)),
        },
      },
      series: [
        {
          name: 'Spesa cumulata',
          type: 'line',
          data: series.data,
          // A gradino: fra un giorno di spesa e il successivo il totale non cambia.
          step: 'end',
          smooth: false,
          showSymbol: false,
          lineStyle: { width: 2, color: tokens.line },
          itemStyle: { color: tokens.line },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: tokens.fillFrom },
                { offset: 1, color: tokens.fillTo },
              ],
            },
          },
          // Il totale raggiunto, sull'ultimo giorno in cui è cambiato: è il valore che
          // il lettore cerca, e va marcato.
          markPoint: {
            symbol: 'circle',
            symbolSize: 8,
            label: { show: false },
            itemStyle: { color: tokens.line, borderColor: tokens.surface, borderWidth: 2 },
            data: [{ coord: series.last }],
          },
        },
      ],
    };
  });
}

function isoDay(timestamp: number): IsoDate {
  return new Date(timestamp).toISOString().slice(0, 10);
}
