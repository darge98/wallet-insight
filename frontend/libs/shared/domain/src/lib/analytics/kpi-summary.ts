import { DateRange } from '../shared/date-range';
import { Money } from '../shared/money';

/** Variazione rispetto al periodo comparabile precedente. */
export interface Trend {
  readonly previous: Money;
  /** Differenza con segno rispetto al periodo precedente. */
  readonly difference: Money;
  /** Variazione percentuale; `null` quando il periodo precedente è a zero. */
  readonly changePercent: number | null;
}

export interface KpiSummary {
  readonly netWorth: Money;
  readonly income: Money;
  readonly expenses: Money;
  /** Entrate meno uscite del periodo: il "margine". */
  readonly net: Money;
  /** Quota di entrate non spesa, in percentuale. */
  readonly savingsRate: number;
  readonly incomeTrend: Trend;
  readonly expensesTrend: Trend;
  readonly netTrend: Trend;
  readonly transactionCount: number;
  /** Il periodo con cui il server ha calcolato le tendenze: il client lo nomina, non lo ricalcola. */
  readonly previousPeriod: DateRange;
}
