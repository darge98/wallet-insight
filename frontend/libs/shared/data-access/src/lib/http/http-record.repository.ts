import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  FinanceRecord,
  RecordEdit,
  RecordId,
  RecordQuery,
  RecordRepository,
  RecordSearchResult,
} from '@wallet/shared-domain';

import {
  MovementResponse,
  MovementsPageResponse,
  toFinanceRecord,
  toRecordSearchResult,
  toUpdateMovementRequest,
} from './movement-contract';
import { API_BASE_URL } from './api-base-url';
import { currentUserId } from './current-user';
import { requestJson } from './json-request';

/**
 * I movimenti letti dal backend.
 *
 * Elenco e totali arrivano dalla stessa risposta perché sono la stessa domanda
 * fatta due volte — le righe e quanto pesano — e due chiamate separate
 * potrebbero raccontare due momenti diversi, con un riepilogo che non
 * corrisponde alle righe sotto di sé.
 *
 * Nessun filtro e nessun ordinamento vengono applicati qui: li fa PostgreSQL. Su
 * 1678 movimenti scaricare tutto per filtrarlo nel browser sarebbe già una
 * cattiva idea, e lo storico cresce a ogni import.
 */
@Injectable()
export class HttpRecordRepository implements RecordRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async search(query: RecordQuery, signal?: AbortSignal): Promise<RecordSearchResult> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const url = `${this.baseUrl}/users/${userId}/movements?${toQueryString(query)}`;

    return toRecordSearchResult(
      await requestJson<MovementsPageResponse>(this.http, 'GET', url, { signal }),
    );
  }

  /**
   * La correzione viaggia come PATCH e torna il movimento aggiornato: è la
   * risposta del server a finire in schermata, non la bozza appena digitata.
   */
  async update(id: RecordId, edit: RecordEdit, signal?: AbortSignal): Promise<FinanceRecord> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const url = `${this.baseUrl}/users/${userId}/movements/${id}`;

    return toFinanceRecord(
      await requestJson<MovementResponse>(this.http, 'PATCH', url, {
        body: toUpdateMovementRequest(edit),
        signal,
      }),
    );
  }
}

/**
 * I criteri tradotti in querystring.
 *
 * I filtri a più valori si ripetono (`?accountId=a&accountId=b`) invece di
 * arrivare come lista separata da virgole: è la forma che il server legge senza
 * doversi inventare un separatore, e non si rompe il giorno in cui un valore
 * contiene il separatore stesso.
 *
 * `HttpParams` fa la codifica, così un identificatore o una data non hanno modo
 * di finire nell'URL interpretati come qualcos'altro.
 */
function toQueryString(query: RecordQuery): string {
  let params = new HttpParams()
    .set('page', query.page.index)
    .set('size', query.page.size)
    .set('sortBy', toSortBy(query.sort.field))
    .set('sortDirection', query.sort.direction);

  const range = query.filters.range;
  if (range) {
    // Gli estremi vanno insieme: il server rifiuta una finestra con un solo capo,
    // invece di chiuderla d'ufficio e mostrare un periodo che nessuno ha chiesto.
    params = params.set('from', range.from).set('to', range.to);
  }
  for (const type of query.filters.types) {
    params = params.append('type', type);
  }
  for (const accountId of query.filters.accountIds) {
    params = params.append('accountId', accountId);
  }
  for (const categoryId of query.filters.categoryIds) {
    params = params.append('categoryId', categoryId);
  }

  return params.toString();
}

/** Il contratto HTTP parla kebab-case, il dominio camelCase. */
function toSortBy(field: RecordQuery['sort']['field']): string {
  return field === 'counterParty' ? 'counter-party' : field;
}
