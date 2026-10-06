import { InjectionToken } from '@angular/core';

import { UserProfile } from './user-profile';

/**
 * Porta di lettura del profilo dell'utente.
 *
 * L'applicazione è single-user: il profilo è un singleton. `findCurrent`
 * restituisce `null` quando non è ancora stato configurato — è l'unico segnale
 * che apre l'onboarding — mentre un errore di lettura resta un errore e non
 * viene mai confuso con l'assenza.
 *
 * La creazione non è qui: nasce dal primo accesso, che crea profilo e sorgente
 * insieme, e vive nella porta dell'onboarding. Qui c'è invece `save`, che è
 * un'altra cosa: modificare un profilo che esiste già.
 */
export interface UserProfileRepository {
  findCurrent(signal?: AbortSignal): Promise<UserProfile | null>;

  /**
   * Salva il profilo per intero e restituisce com'è rimasto.
   *
   * Sostituzione completa e non modifica parziale: è ciò che l'API espone
   * (`PUT`), ed è anche la forma giusta per una schermata che mostra tutti i
   * campi insieme — chi la compila vede esattamente ciò che sta per salvare, e
   * non c'è modo di mandare un aggiornamento che non corrisponde a ciò che ha
   * davanti.
   */
  save(profile: UserProfile, signal?: AbortSignal): Promise<UserProfile>;
}

export const USER_PROFILE_REPOSITORY = new InjectionToken<UserProfileRepository>(
  'UserProfileRepository',
);
