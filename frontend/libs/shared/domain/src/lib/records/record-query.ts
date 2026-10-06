import { AccountId, CategoryId } from '../shared/identifier';
import { DateRange } from '../shared/date-range';
import { PageRequest } from '../shared/page';
import { RecordType } from './finance-record';

export const RECORD_SORT_FIELDS = ['date', 'amount', 'counterParty'] as const;

export type RecordSortField = (typeof RECORD_SORT_FIELDS)[number];

export type SortDirection = 'asc' | 'desc';

export interface RecordSort {
  readonly field: RecordSortField;
  readonly direction: SortDirection;
}

/**
 * I criteri con cui si chiede un elenco di movimenti.
 *
 * Criteri diversi si combinano in **and** — periodo *e* conto *e* categoria —
 * mentre più valori dello stesso criterio si combinano in **or**: due conti
 * selezionati vogliono dire «uno qualsiasi dei due», non «entrambi», che su un
 * movimento solo non avrebbe senso.
 *
 * Non c'è la ricerca testuale, ed è una scelta rimandata e non una dimenticanza:
 * farla bene su descrizione e controparte vuol dire un indice apposta nel
 * database e decidere cosa significhi «trovare»; farla come una sottostringa
 * costerebbe una scansione a ogni tasto premuto per dare risultati che sembrano
 * casuali.
 */
export interface RecordFilters {
  readonly types: readonly RecordType[];
  readonly accountIds: readonly AccountId[];
  readonly categoryIds: readonly CategoryId[];
  readonly range: DateRange | null;
}

export interface RecordQuery {
  readonly filters: RecordFilters;
  readonly sort: RecordSort;
  readonly page: PageRequest;
}

export const EMPTY_RECORD_FILTERS: RecordFilters = {
  types: [],
  accountIds: [],
  categoryIds: [],
  range: null,
};

export const DEFAULT_RECORD_SORT: RecordSort = { field: 'date', direction: 'desc' };

/**
 * Filtri "extra" attivi. Il periodo è escluso: è sempre valorizzato e ha già un
 * controllo dedicato, contarlo renderebbe il badge sempre acceso.
 */
export function countActiveFilters(filters: RecordFilters): number {
  return filters.types.length + filters.accountIds.length + filters.categoryIds.length;
}
