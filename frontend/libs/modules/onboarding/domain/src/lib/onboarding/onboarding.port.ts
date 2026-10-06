import { InjectionToken } from '@angular/core';

import { CompleteOnboardingCommand, OnboardingResult } from './complete-onboarding';

/**
 * Porta del primo accesso.
 *
 * È una porta a sé, e non un metodo del repository del profilo, perché il caso
 * d'uso attraversa due domini — utente e importazione — e nessuno dei due deve
 * conoscere l'altro. L'atomicità è una garanzia dell'adapter: un token rifiutato
 * non deve lasciare dietro di sé un profilo a metà.
 */
export interface OnboardingRepository {
  complete(command: CompleteOnboardingCommand, signal?: AbortSignal): Promise<OnboardingResult>;
}

export const ONBOARDING_REPOSITORY = new InjectionToken<OnboardingRepository>(
  'OnboardingRepository',
);
