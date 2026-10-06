import { Injectable, computed, inject, signal } from '@angular/core';

import { USER_PROFILE_REPOSITORY, UserProfile } from '@wallet/user-domain';

/**
 * Stato in cui può trovarsi il profilo.
 *
 * `absent` e `error` sono deliberatamente distinti: un caricamento fallito non
 * deve mai aprire l'onboarding, altrimenti si rischia di sovrascrivere un
 * profilo esistente solo perché la lettura non è andata a buon fine.
 */
export type UserStatus = 'unknown' | 'loading' | 'absent' | 'ready' | 'error';

/**
 * Facade del profilo utente: l'unico punto di contatto con il dominio Utente
 * per shell, Panoramica e onboarding.
 *
 * Vive in una libreria `data-access` e non in una feature perché una feature non
 * può essere importata dalle altre; lo stato è globale (`providedIn: 'root'`)
 * perché più aree lo leggono e deve esistere una sola lettura del profilo.
 */
@Injectable({ providedIn: 'root' })
export class UserFacade {
  private readonly repository = inject(USER_PROFILE_REPOSITORY);

  private readonly statusState = signal<UserStatus>('unknown');
  private readonly profileState = signal<UserProfile | null>(null);
  private readonly failureState = signal<unknown>(null);

  private pending: Promise<void> | null = null;

  readonly status = this.statusState.asReadonly();
  readonly profile = this.profileState.asReadonly();
  readonly failure = this.failureState.asReadonly();

  readonly isReady = computed(() => this.statusState() === 'ready');
  readonly isAbsent = computed(() => this.statusState() === 'absent');

  /**
   * Carica il profilo una volta sola.
   *
   * Le guardie di più rami di routing possono chiedere il profilo in parallelo:
   * la promise condivisa fa sì che il repository venga interrogato una volta.
   */
  ensureLoaded(): Promise<void> {
    // Una richiesta già in corso va attesa, non scavalcata: altrimenti una
    // guardia concorrente leggerebbe `loading` e deciderebbe sul nulla.
    if (this.pending) {
      return this.pending;
    }
    if (this.statusState() !== 'unknown') {
      return Promise.resolve();
    }

    this.pending = this.load();
    return this.pending;
  }

  /** Riprova dopo un errore di caricamento: non è la stessa cosa di "assente". */
  async retry(): Promise<void> {
    this.failureState.set(null);
    this.statusState.set('unknown');
    this.pending = null;
    return this.ensureLoaded();
  }

  /**
   * Adotta il profilo appena creato dall'onboarding.
   *
   * È una scrittura locale, non una chiamata: il profilo esiste già sul server e
   * riportarlo qui evita la GET che seguirebbe subito dopo. Da questo momento la
   * guardia della shell lo trova pronto.
   */
  adopt(profile: UserProfile): void {
    this.profileState.set(profile);
    this.statusState.set('ready');
    this.failureState.set(null);
  }

  private async load(): Promise<void> {
    this.statusState.set('loading');

    try {
      const profile = await this.repository.findCurrent();
      this.profileState.set(profile);
      this.statusState.set(profile ? 'ready' : 'absent');
    } catch (error) {
      this.failureState.set(error);
      this.statusState.set('error');
    } finally {
      this.pending = null;
    }
  }
}
