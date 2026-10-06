import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

import { UserFacade } from '@wallet/user-data-access';

/**
 * Protegge la shell: senza profilo si viene mandati all'onboarding.
 *
 * `canMatch` impedisce che la shell — e con essa conti e dashboard — venga
 * attivata prima che esista un profilo. Un errore di caricamento *non* apre
 * l'onboarding: la radice mostra il recupero, così non si rischia di creare un
 * secondo profilo solo perché la lettura è fallita.
 */
export const requireUserGuard: CanMatchFn = async () => {
  const users = inject(UserFacade);
  const router = inject(Router);

  await users.ensureLoaded();

  if (users.status() === 'ready') {
    return true;
  }
  if (users.status() === 'absent') {
    return router.createUrlTree(['/onboarding']);
  }
  return true;
};

/** Guardia inversa: chi ha già un profilo non deve rivedere l'onboarding. */
export const skipIfUserGuard: CanMatchFn = async () => {
  const users = inject(UserFacade);
  const router = inject(Router);

  await users.ensureLoaded();

  return users.status() === 'ready' ? router.createUrlTree(['/panoramica']) : true;
};
