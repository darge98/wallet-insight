import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { UserProfile, UserProfileRepository } from '@wallet/user-domain';
import { API_BASE_URL, PageResponse, requestJson } from '@wallet/shared-data-access';

import { UserResponse, toUserProfile, toUserRequest } from './user-contract';

/**
 * Profilo dell'utente letto dal backend.
 *
 * Finché non c'è autenticazione, "l'utente corrente" è il primo della lista:
 * l'applicazione è single-user e l'API non ha ancora un `/api/users/me`. Si
 * chiede una pagina da uno solo, così il giorno in cui gli utenti saranno più
 * d'uno questa scorciatoia salta all'occhio invece di scegliere in silenzio.
 */
@Injectable()
export class HttpUserProfileRepository implements UserProfileRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async findCurrent(signal?: AbortSignal): Promise<UserProfile | null> {
    const page = await requestJson<PageResponse<UserResponse>>(
      this.http,
      'GET',
      `${this.baseUrl}/users?page=0&size=1`,
      { signal },
    );

    const first = page.items.at(0);
    return first ? toUserProfile(first) : null;
  }

  /**
   * Salva il profilo e restituisce com'è rimasto **secondo il server**.
   *
   * Non il profilo appena mandato: normalizzazioni e valori rifiutati si vedono
   * solo nella risposta, e rimettere in schermata ciò che si è scritto darebbe
   * per salvato qualcosa che potrebbe non esserlo.
   */
  async save(profile: UserProfile, signal?: AbortSignal): Promise<UserProfile> {
    const saved = await requestJson<UserResponse>(
      this.http,
      'PUT',
      `${this.baseUrl}/users/${profile.id}`,
      { body: toUserRequest(profile), signal },
    );

    return toUserProfile(saved);
  }
}
