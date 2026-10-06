import { InjectionToken } from '@angular/core';

import { AccountId } from '@wallet/shared-domain';

import { Account } from './account';

/**
 * Porta dei conti: leggerli e cambiare ciò che è dell'utente.
 *
 * Non c'è `create` e non c'è `delete`, e le assenze dicono più delle presenze. Un
 * conto nasce da un import — la sua identità comprende il riferimento al dato
 * originale — e uno inventato dall'interfaccia non avrebbe nulla a cui
 * agganciarsi; cancellarne uno significherebbe buttare i movimenti che gli stanno
 * sopra, che restano validi anche quando la sorgente smette di elencarlo.
 *
 * Rinominare e colorare sono due metodi e non un unico `update` parziale: sono due
 * gesti distinti e l'interfaccia non li fa mai insieme — il nome si conferma
 * uscendo dal campo, il colore si sceglie da una tavolozza. Il backend accetta
 * anche i due campi in una richiesta sola, ma esporre qui quella possibilità
 * vorrebbe dire inventare un modo di dire «questo non toccarlo», e nel dominio
 * `null` significa assenza, non indifferenza.
 */
export interface AccountRepository {
  findAll(signal?: AbortSignal): Promise<readonly Account[]>;

  /** Il conto aggiornato: è il server a dire com'è rimasto, non il chiamante a indovinarlo. */
  rename(id: AccountId, name: string, signal?: AbortSignal): Promise<Account>;

  recolor(id: AccountId, color: string, signal?: AbortSignal): Promise<Account>;
}

export const ACCOUNT_REPOSITORY = new InjectionToken<AccountRepository>('AccountRepository');
