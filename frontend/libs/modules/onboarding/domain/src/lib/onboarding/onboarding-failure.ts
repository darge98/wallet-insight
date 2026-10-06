/**
 * Perché il primo accesso non è riuscito.
 *
 * Sono i soli tre casi che cambiano ciò che l'utente può fare: correggere
 * l'email, correggere i dati inseriti, o semplicemente riprovare. L'adapter
 * traduce qui i codici di trasporto, così la schermata non sa nulla di HTTP.
 */
export type OnboardingFailureKind = 'conflict' | 'invalid' | 'unavailable';

export class OnboardingFailed extends Error {
  constructor(
    readonly kind: OnboardingFailureKind,
    cause?: unknown,
  ) {
    super(`Primo accesso non riuscito: ${kind}.`, { cause });
    this.name = 'OnboardingFailed';
  }
}
