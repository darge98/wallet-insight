import { Injectable, computed, inject, resource } from '@angular/core';

import { Account, ACCOUNT_REPOSITORY, activeAccounts } from '@wallet/accounts-domain';
import { ANALYTICS_REPOSITORY, Money, money } from '@wallet/shared-domain';

/**
 * I conti collegati, condivisi da chi li mostra.
 *
 * Sta in una libreria a sé perché la sidebar è presente in ogni sezione: tenerlo
 * qui evita che ogni feature ricarichi la stessa lista, e che il drawer mobile
 * duplichi la richiesta della sidebar desktop.
 *
 * Fino a ieri girava su `DemoAccount`, il conto del dataset dimostrativo, e
 * stava in `shared/data-access` perché `shared` non può nominare un'area. Ora
 * che i movimenti arrivano dal backend quel modello non serve più — i filtri e i
 * nomi si appoggiano ai conti veri — e questo facade è passato nell'area a cui
 * appartiene, esponendosi come fanno `user` e `ingestion`.
 *
 * Il saldo non viene ricalcolato qui: arriva dal server già composto (saldo
 * iniziale più la somma dei movimenti). Sommare in due posti diversi è il modo
 * più sicuro di ottenere due risposte diverse.
 */
@Injectable({ providedIn: 'root' })
export class AccountsOverview {
  private readonly repository = inject(ACCOUNT_REPOSITORY);
  private readonly analytics = inject(ANALYTICS_REPOSITORY);

  readonly accounts = resource({
    loader: ({ abortSignal }) => this.repository.findAll(abortSignal),
    defaultValue: [] as readonly Account[],
  });

  /** Gli archiviati restano fuori: possiedono storico, ma non si guardano più. */
  readonly visible = computed(() => activeAccounts(this.accounts.value()));

  readonly count = computed(() => this.visible().length);

  private readonly netWorth = resource({
    loader: ({ abortSignal }) => this.analytics.netWorth(abortSignal),
    defaultValue: money(0),
  });

  /**
   * Il totale di ciò che l'utente possiede, come lo calcola il server: in euro, con i
   * conti in valuta convertiti, senza archiviati né conti fuori dalle statistiche. È
   * lo stesso numero del patrimonio nei KPI, non una seconda somma fatta qui.
   */
  readonly total = computed<Money>(() => this.netWorth.value());

  readonly isLoading = computed(() => this.accounts.isLoading());

  /**
   * Sostituisce in elenco il conto che il server ha appena restituito.
   *
   * Esiste perché questo facade è l'**unica** copia dei conti in memoria: chi li
   * modifica — oggi le Impostazioni, che rinominano e ricolorano — non tiene una
   * lista sua da aggiornare per conto proprio. Quando ne teneva una, cambiare il
   * colore di un conto lo aggiornava in Impostazioni e lasciava i movimenti con
   * il colore vecchio fino al ricaricamento della pagina: due liste degli stessi
   * conti, una sola delle due aggiornata.
   *
   * `adopt` e non `reload`: la risposta del server *è* lo stato nuovo di quel
   * conto, e rileggere tutto costerebbe un giro di rete per sapere ciò che si sa
   * già — oltre a far sfarfallare l'elenco mentre si digita un nome.
   */
  adopt(updated: Account): void {
    this.accounts.value.update((accounts) =>
      accounts.map((account) => (account.id === updated.id ? updated : account)),
    );
  }

  reload(): void {
    this.accounts.reload();
    this.netWorth.reload();
  }
}
