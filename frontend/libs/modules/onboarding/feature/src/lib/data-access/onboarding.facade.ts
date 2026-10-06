import { Injectable, inject, signal } from '@angular/core';

import {
  CompleteOnboardingCommand,
  ONBOARDING_REPOSITORY,
  OnboardingFailed,
  OnboardingFailureKind,
} from '@wallet/onboarding-domain';
import { UserFacade } from '@wallet/user-data-access';

/**
 * Facade del primo accesso.
 *
 * Il ciclo di vita è quello della pagina — `providers: [OnboardingFacade]` —
 * perché l'onboarding si attraversa una volta sola: non c'è stato da conservare
 * dopo. Ciò che invece sopravvive è il profilo, e per questo il risultato viene
 * consegnato al `UserFacade`, che resta l'unica fonte del profilo per il resto
 * dell'applicazione.
 *
 * L'esito negativo non viene rilanciato ma esposto come stato: la pagina deve
 * mostrare un messaggio, non gestire un'eccezione.
 */
@Injectable()
export class OnboardingFacade {
  private readonly repository = inject(ONBOARDING_REPOSITORY);
  private readonly users = inject(UserFacade);

  private readonly failureState = signal<OnboardingFailureKind | null>(null);

  /** Lo stato "sto inviando" lo tiene il form, che è chi conosce l'invio in corso. */
  readonly failure = this.failureState.asReadonly();

  /** `true` quando il profilo esiste e l'applicazione può aprirsi. */
  async complete(command: CompleteOnboardingCommand): Promise<boolean> {
    this.failureState.set(null);

    try {
      const result = await this.repository.complete(command);
      this.users.adopt(result.profile);
      return true;
    } catch (error) {
      // Un errore che non arriva dalla porta (un bug del mappatore, per dire)
      // non ha un rimedio diverso: si riprova.
      this.failureState.set(error instanceof OnboardingFailed ? error.kind : 'unavailable');
      return false;
    }
  }
}
