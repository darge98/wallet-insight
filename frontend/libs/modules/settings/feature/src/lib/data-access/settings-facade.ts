import { Injectable, computed, inject, signal } from '@angular/core';

import { Account, ACCOUNT_REPOSITORY } from '@wallet/accounts-domain';
import { AccountsOverview } from '@wallet/accounts-data-access';
import { ImportConnections } from '@wallet/ingestion-data-access';
import { ImportSource } from '@wallet/ingestion-domain';
import { AccountId } from '@wallet/shared-domain';
import { USER_PROFILE_REPOSITORY, UserProfile } from '@wallet/user-domain';
import { UserFacade } from '@wallet/user-data-access';

/**
 * Lo stato della schermata Impostazioni.
 *
 * Mette insieme tre cose che il resto dell'applicazione tiene separate — il
 * profilo, i conti, le sorgenti collegate — perché è l'unico posto in cui si
 * guardano insieme: "questi sono i miei conti, arrivano da qui, e sono aggiornati
 * a ieri sera".
 *
 * Niente di ciò che si vede qui è tenuto da questo facade: il profilo lo tiene
 * `UserFacade`, i conti `AccountsOverview` e le sorgenti `ImportConnections`, perché li leggono anche la
 * shell e le altre sezioni. Le Impostazioni sono la schermata che *scrive*, e
 * scrivere su una copia privata è il modo di avere due verità: era già la regola
 * per il profilo — salvarlo da qui aggiorna quello, altrimenti la sidebar
 * mostrerebbe il nome vecchio — e ora vale anche per i conti, dopo che
 * ricolorare un conto lo aggiornava qui e lasciava i movimenti col colore di
 * prima fino al ricaricamento della pagina.
 *
 * Sull'importazione l'unica scrittura è il token: l'aggiornamento non si chiede
 * da qui, lo fanno l'onboarding e le schedulazioni del backend.
 */
@Injectable()
export class SettingsFacade {
  private readonly accountRepository = inject(ACCOUNT_REPOSITORY);
  private readonly importConnections = inject(ImportConnections);
  private readonly profileRepository = inject(USER_PROFILE_REPOSITORY);
  private readonly user = inject(UserFacade);
  private readonly accountsOverview = inject(AccountsOverview);

  /** I conti sono quelli di tutti: archiviati compresi, che qui si guardano. */
  readonly accounts = this.accountsOverview.accounts;

  readonly connections = this.importConnections.connections;

  readonly profile = this.user.profile;

  private readonly failureState = signal<string | null>(null);
  private readonly savingProfileState = signal(false);
  private readonly profileSavedState = signal(false);
  private readonly replacingTokenState = signal<ImportSource | null>(null);

  readonly failure = this.failureState.asReadonly();
  readonly isSavingProfile = this.savingProfileState.asReadonly();
  readonly profileSaved = this.profileSavedState.asReadonly();
  readonly replacingToken = this.replacingTokenState.asReadonly();

  readonly isLoading = computed(() => this.accounts.isLoading() || this.connections.isLoading());

  readonly hasError = computed(
    () => this.accounts.error() != null || this.connections.error() != null,
  );

  /**
   * Il saldo complessivo, chiesto a chi lo calcola già.
   *
   * La regola — fuori gli archiviati e quelli che la sorgente esclude dalle
   * statistiche — vive in `AccountsOverview` e basta che viva lì: sommare in due
   * posti è il modo più sicuro di ottenere due totali diversi nella stessa
   * applicazione.
   */
  readonly totalBalance = this.accountsOverview.total;

  async rename(id: AccountId, name: string): Promise<void> {
    await this.applyToAccount(() => this.accountRepository.rename(id, name));
  }

  async recolor(id: AccountId, color: string): Promise<void> {
    await this.applyToAccount(() => this.accountRepository.recolor(id, color));
  }

  async saveProfile(profile: UserProfile): Promise<void> {
    this.savingProfileState.set(true);
    this.profileSavedState.set(false);
    this.failureState.set(null);

    try {
      // `adopt` e non un ricaricamento: il profilo salvato è già quello nuovo, e
      // rileggerlo costerebbe un giro di rete per sapere ciò che si sa già.
      this.user.adopt(await this.profileRepository.save(profile));
      this.profileSavedState.set(true);
    } catch (error) {
      this.failureState.set(messageOf(error));
    } finally {
      this.savingProfileState.set(false);
    }
  }

  async replaceToken(source: ImportSource, token: string): Promise<void> {
    this.replacingTokenState.set(source);
    this.failureState.set(null);
    try {
      await this.importConnections.replaceToken(source, token);
    } catch (error) {
      this.failureState.set(messageOf(error));
    } finally {
      this.replacingTokenState.set(null);
    }
  }

  /**
   * Consegna la modifica e lascia che sia il server ad avere ragione.
   *
   * La risposta *è* lo stato nuovo del conto: se il server ha normalizzato un
   * valore lo si vede subito, invece di ciò che si è digitato. A metterla in
   * elenco è `AccountsOverview`, che l'elenco lo possiede — così il colore nuovo
   * arriva anche ai movimenti e alla sidebar, non solo a questa schermata.
   */
  private async applyToAccount(change: () => Promise<Account>): Promise<void> {
    this.failureState.set(null);
    try {
      this.accountsOverview.adopt(await change());
    } catch (error) {
      this.failureState.set(messageOf(error));
    }
  }
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'Qualcosa non ha funzionato.';
}
