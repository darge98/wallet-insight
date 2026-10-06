import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { Account, AccountRepository } from '@wallet/accounts-domain';
import { AccountId } from '@wallet/shared-domain';
import { API_BASE_URL, currentUserId, requestJson } from '@wallet/shared-data-access';

import { AccountResponse, toAccount } from './account-contract';

/**
 * I conti letti dal backend.
 *
 * L'elenco non è paginato, ed è una scelta del server: i conti di una persona
 * sono una decina, e girare pagine per trovare un nome costerebbe più di quanto
 * valga. Il saldo arriva già calcolato — è il saldo iniziale più la somma dei
 * movimenti — e non viene ricalcolato qui: sommare in due posti diversi è il modo
 * più sicuro di ottenere due risposte diverse.
 */
@Injectable()
export class HttpAccountRepository implements AccountRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async findAll(signal?: AbortSignal): Promise<readonly Account[]> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const accounts = await requestJson<readonly AccountResponse[]>(
      this.http,
      'GET',
      `${this.baseUrl}/users/${userId}/accounts`,
      { signal },
    );

    return accounts.map(toAccount);
  }

  rename(id: AccountId, name: string, signal?: AbortSignal): Promise<Account> {
    return this.patch(id, { name }, signal);
  }

  recolor(id: AccountId, color: string, signal?: AbortSignal): Promise<Account> {
    return this.patch(id, { color }, signal);
  }

  /**
   * `PATCH` con il solo campo che cambia.
   *
   * Mandarne uno solo non è un'ottimizzazione: è ciò che dice al server «l'altro
   * non toccarlo». Con un `PUT` due schermate aperte insieme si sovrascriverebbero
   * a vicenda il campo che nessuna delle due ha modificato.
   */
  private async patch(
    id: AccountId,
    body: { readonly name?: string; readonly color?: string },
    signal?: AbortSignal,
  ): Promise<Account> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const updated = await requestJson<AccountResponse>(
      this.http,
      'PATCH',
      `${this.baseUrl}/users/${userId}/accounts/${id}`,
      { body, signal },
    );

    return toAccount(updated);
  }
}
