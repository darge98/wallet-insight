import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import { ImportConnectionResponse, toImportConnection } from '@wallet/ingestion-data-access';
import {
  CompleteOnboardingCommand,
  OnboardingFailed,
  OnboardingFailureKind,
  OnboardingRepository,
  OnboardingResult,
} from '@wallet/onboarding-domain';
import { API_BASE_URL, requestJson } from '@wallet/shared-data-access';
import { toUserProfile, UserResponse } from '@wallet/user-data-access';

interface OnboardingResponse {
  readonly user: UserResponse;
  readonly connections: readonly ImportConnectionResponse[];
}

/**
 * Primo accesso su `POST /api/onboarding`.
 *
 * Una chiamata sola perché una sola è la garanzia che serve: il backend crea
 * profilo e connessione in transazione, e un token rifiutato non lascia dietro
 * di sé un utente a metà. La risposta contiene già entrambi, quindi non c'è
 * nessuna lettura da fare subito dopo.
 *
 * Quando c'è una sorgente la chiamata fa anche il primo import e **dura
 * secondi**: il backend legge lo storico dei movimenti mentre la richiesta è
 * aperta (~3 s misurati su 1678 movimenti). Chi la chiama deve mostrarlo in
 * corso, non attenderla in silenzio.
 */
@Injectable()
export class HttpOnboardingRepository implements OnboardingRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async complete(
    command: CompleteOnboardingCommand,
    signal?: AbortSignal,
  ): Promise<OnboardingResult> {
    const connection = command.importConnection;

    try {
      const response = await requestJson<OnboardingResponse>(
        this.http,
        'POST',
        `${this.baseUrl}/onboarding`,
        {
          signal,
          body: {
            profile: command.profile,
            // Il campo si omette quando l'utente rimanda la scelta: è così che
            // il contratto esprime "nessuna sorgente".
            ...(connection
              ? { importConnection: { source: connection.source, token: connection.token } }
              : {}),
          },
        },
      );

      return {
        profile: toUserProfile(response.user),
        connections: response.connections.map(toImportConnection),
      };
    } catch (cause) {
      throw new OnboardingFailed(failureKind(cause), cause);
    }
  }
}

/**
 * Traduzione dei codici di trasporto nei tre esiti che cambiano ciò che
 * l'utente può fare. Lo `0` di `HttpErrorResponse` è la richiesta mai partita —
 * rete assente, CORS, backend spento — e vale quanto un 5xx: si riprova.
 */
function failureKind(cause: unknown): OnboardingFailureKind {
  if (!(cause instanceof HttpErrorResponse)) {
    return 'unavailable';
  }
  if (cause.status === 409) return 'conflict';
  return cause.status >= 400 && cause.status < 500 ? 'invalid' : 'unavailable';
}
