import { InjectionToken } from '@angular/core';
import { FinanceRecord } from '../records/finance-record';
import { RecordEdit } from '../records/record-edit';
import { RecordQuery } from '../records/record-query';
import { RecordId } from '../shared/identifier';
import { Page } from '../shared/page';
import { Money } from '../shared/money';

/**
 * Totali del risultato filtrato, indipendenti dalla paginazione.
 *
 * `expenses` è positivo: è una quantità uscita, e il segno meno accanto
 * all'etichetta «Uscite» direbbe la stessa cosa due volte.
 *
 * I giroconti non entrano in `income` né in `expenses` — le loro due gambe si
 * annullerebbero e gonfierebbero entrambe le colonne — ma `count` li conta,
 * perché è lo stesso numero che la paginazione mostra sotto l'elenco.
 */
export interface RecordTotals {
  readonly income: Money;
  readonly expenses: Money;
  readonly net: Money;
  readonly count: number;
}

export interface RecordSearchResult {
  readonly page: Page<FinanceRecord>;
  readonly totals: RecordTotals;
}

/**
 * Elenco e totali arrivano insieme, da una chiamata sola.
 *
 * Non è una comodità: sono due interrogazioni sugli stessi criteri, e separarle
 * vorrebbe dire due risposte che possono raccontare due momenti diversi — filtri
 * cambiati nel frattempo, un import finito in mezzo — con un riepilogo che non
 * corrisponde alle righe sotto di sé.
 */
export interface RecordRepository {
  search(query: RecordQuery, signal?: AbortSignal): Promise<RecordSearchResult>;

  /**
   * Corregge le tre cose che di un movimento appartengono all'utente e
   * restituisce il movimento come il server lo racconta dopo la correzione.
   *
   * Torna il movimento intero e non un `void` perché è il server a decidere che
   * cosa risulta scritto da chi: mostrare la propria versione ottimistica
   * significherebbe, la volta che le due divergono, credere alla sbagliata.
   */
  update(id: RecordId, edit: RecordEdit, signal?: AbortSignal): Promise<FinanceRecord>;
}

export const RECORD_REPOSITORY = new InjectionToken<RecordRepository>('RecordRepository');
