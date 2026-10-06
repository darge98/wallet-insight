import {
  asAccountId,
  asCategoryId,
  asSourceCategoryId,
  asRecordId,
  Category,
  CategoryBreakdownSlice,
  SourceCategory,
  CumulativeExpensePoint,
  FinanceRecord,
  KpiSummary,
  isCurrencyCode,
  Money,
  money,
  RECORD_STATES,
  RecordEdit,
  RecordSearchResult,
  RecordState,
  RecordType,
  SpendingTarget,
  Trend,
} from '@wallet/shared-domain';

import { contractError, PageResponse } from './api-contract';

export interface MovementResponse {
  readonly id: string;
  readonly accountId: string;
  readonly source: string;
  readonly amountCents: number;
  readonly currencyCode: string;
  readonly date: string;
  readonly direction: string;
  readonly state: string;
  readonly description?: string;
  readonly counterParty?: string;
  readonly categoryId?: string;
  readonly transferState?: string;
}

export interface MovementTotalsResponse {
  readonly incomeCents: number;
  readonly expensesCents: number;
  readonly netCents: number;
  readonly currencyCode: string;
  readonly count: number;
}

export interface MovementsPageResponse {
  readonly page: PageResponse<MovementResponse>;
  readonly totals: MovementTotalsResponse;
}

export interface CategoryResponse {
  readonly id: string;
  readonly parentId?: string;
  readonly name: string;
  readonly color?: string;
}

export interface SourceCategoryResponse {
  readonly id: string;
  readonly source: string;
  readonly name: string;
  readonly group?: string;
  readonly categoryId?: string;
}

export interface CategorySpendingResponse {
  readonly categoryId: string;
  readonly name: string;
  readonly color?: string;
  readonly totalCents: number;
  readonly count: number;
  readonly share: number;
}

export interface CounterPartySpendingResponse {
  readonly counterParty: string;
  readonly totalCents: number;
  readonly count: number;
}

/**
 * La natura del movimento, che il server non manda come campo unico.
 *
 * `transferState` valorizzato vince sul verso: le due gambe di un giroconto
 * hanno una direzione ciascuna, e senza questa precedenza una delle due
 * risulterebbe una spesa come tutte le altre. È la stessa regola che il backend
 * applica nei filtri e nei totali, ripetuta qui perché il campo derivato non
 * viaggia — e le due devono restare identiche.
 */
function toRecordType(raw: MovementResponse): RecordType {
  if (raw.transferState !== undefined) {
    return 'transfer';
  }
  return raw.direction === 'income' ? 'income' : 'expense';
}

function toRecordState(raw: string): RecordState {
  if (!(RECORD_STATES as readonly string[]).includes(raw)) {
    throw contractError('state', raw);
  }
  return raw as RecordState;
}

function blankToNull(value: string | undefined): string | null {
  const trimmed = value?.trim() ?? '';
  return trimmed === '' ? null : trimmed;
}

export function toFinanceRecord(raw: MovementResponse): FinanceRecord {
  if (!isCurrencyCode(raw.currencyCode)) {
    throw contractError('currencyCode', raw.currencyCode);
  }

  return {
    id: asRecordId(raw.id),
    accountId: asAccountId(raw.accountId),
    type: toRecordType(raw),
    state: toRecordState(raw.state),
    date: raw.date,
    // L'importo arriva giaà col segno: negativo in uscita, positivo in entrata.
    amount: money(raw.amountCents, raw.currencyCode),
    // Il server omette i campi che non hanno valore e manda una stringa vuota
    // per quelli che l'utente ha svuotato. Sono due fatti diversi là — uno è
    // «nessuno ha detto niente», l'altro è «l'ho svuotato io» — ma da mostrare
    // sono la stessa cosa, e nel dominio l'assenza si scrive `null` una volta sola.
    description: blankToNull(raw.description),
    counterParty: blankToNull(raw.counterParty),
    categoryId: raw.categoryId === undefined ? null : asCategoryId(raw.categoryId),
  };
}

/**
 * Il corpo del PATCH di un movimento: i tre campi così come sono in schermata.
 *
 * Gli spazi ai bordi si tolgono qui e non nel modulo, perché è qui che il
 * valore diventa definitivo: un campo con un solo spazio dentro è un campo
 * vuoto, e lasciarcelo arrivare al database lo renderebbe un valore.
 */
export interface UpdateMovementRequest {
  readonly description: string;
  readonly counterParty: string;
  readonly categoryId: string;
}

export function toUpdateMovementRequest(edit: RecordEdit): UpdateMovementRequest {
  return {
    description: edit.description.trim(),
    counterParty: edit.counterParty.trim(),
    categoryId: edit.category,
  };
}

export function toRecordSearchResult(raw: MovementsPageResponse): RecordSearchResult {
  const currency = raw.totals.currencyCode;
  if (!isCurrencyCode(currency)) {
    throw contractError('currencyCode', currency);
  }

  return {
    page: {
      items: raw.page.items.map(toFinanceRecord),
      total: raw.page.total,
      index: raw.page.index,
      size: raw.page.size,
      pageCount: raw.page.pageCount,
    },
    totals: {
      income: money(raw.totals.incomeCents, currency),
      expenses: money(raw.totals.expensesCents, currency),
      net: money(raw.totals.netCents, currency),
      count: raw.totals.count,
    },
  };
}

export function toCategory(raw: CategoryResponse): Category {
  return {
    id: asCategoryId(raw.id),
    parentId: raw.parentId ? asCategoryId(raw.parentId) : null,
    name: raw.name,
    color: raw.color ?? null,
  };
}

export function toSourceCategory(raw: SourceCategoryResponse): SourceCategory {
  return {
    id: asSourceCategoryId(raw.id),
    source: raw.source,
    name: raw.name,
    group: raw.group ?? null,
    categoryId: raw.categoryId ? asCategoryId(raw.categoryId) : null,
  };
}

/**
 * La quota arriva fra 0 e 1 e qui diventa una percentuale.
 *
 * Il posto della conversione è questo e non la vista: il dominio dichiara
 * `share` in percentuale, e lasciare due unità diverse in giro è il modo in cui
 * prima o poi si moltiplica per cento due volte.
 */
export function toCategoryBreakdownSlice(raw: CategorySpendingResponse): CategoryBreakdownSlice {
  return {
    categoryId: asCategoryId(raw.categoryId),
    name: raw.name,
    color: raw.color ?? null,
    total: money(raw.totalCents),
    share: raw.share * 100,
    transactionCount: raw.count,
  };
}

export function toSpendingTarget(raw: CounterPartySpendingResponse): SpendingTarget {
  return {
    counterParty: raw.counterParty,
    total: money(raw.totalCents),
    transactionCount: raw.count,
  };
}

export interface TrendResponse {
  readonly previousCents: number;
  readonly differenceCents: number;
  readonly changePercent?: number;
}

export interface KpiSummaryResponse {
  readonly netWorthCents: number;
  readonly incomeCents: number;
  readonly expensesCents: number;
  readonly netCents: number;
  readonly currencyCode: string;
  readonly savingsRate: number;
  readonly transactionCount: number;
  readonly incomeTrend: TrendResponse;
  readonly expensesTrend: TrendResponse;
  readonly netTrend: TrendResponse;
  readonly previousFrom: string;
  readonly previousTo: string;
}

export interface NetWorthResponse {
  readonly netWorthCents: number;
  readonly currencyCode: string;
}

export function toNetWorth(raw: NetWorthResponse): Money {
  if (!isCurrencyCode(raw.currencyCode)) {
    throw contractError('currencyCode', raw.currencyCode);
  }
  return money(raw.netWorthCents, raw.currencyCode);
}

export interface CumulativeExpenseResponse {
  readonly date: string;
  readonly totalCents: number;
}

export function toKpiSummary(raw: KpiSummaryResponse): KpiSummary {
  const currency = raw.currencyCode;
  if (!isCurrencyCode(currency)) {
    throw contractError('currencyCode', currency);
  }
  const trend = (value: TrendResponse): Trend => ({
    previous: money(value.previousCents, currency),
    difference: money(value.differenceCents, currency),
    // Assente quando il periodo precedente vale zero: una variazione percentuale
    // su una base nulla non ha risposta, e qui resta `null` invece di diventare 0.
    changePercent: value.changePercent ?? null,
  });

  return {
    netWorth: money(raw.netWorthCents, currency),
    income: money(raw.incomeCents, currency),
    expenses: money(raw.expensesCents, currency),
    net: money(raw.netCents, currency),
    savingsRate: raw.savingsRate,
    transactionCount: raw.transactionCount,
    incomeTrend: trend(raw.incomeTrend),
    expensesTrend: trend(raw.expensesTrend),
    netTrend: trend(raw.netTrend),
    previousPeriod: { from: raw.previousFrom, to: raw.previousTo },
  };
}

export function toCumulativeExpensePoint(raw: CumulativeExpenseResponse): CumulativeExpensePoint {
  return { date: raw.date, total: money(raw.totalCents) };
}
