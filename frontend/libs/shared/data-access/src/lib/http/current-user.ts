import { HttpClient } from '@angular/common/http';

import { PageResponse } from './api-contract';
import { requestJson } from './json-request';

/**
 * L'identificativo dell'utente corrente.
 *
 * Finché non c'è autenticazione «l'utente corrente» è il primo della lista:
 * l'applicazione è single-user e l'API non ha ancora un `/api/users/me`. Si chiede
 * una pagina da uno solo, così il giorno in cui gli utenti saranno più d'uno la
 * scorciatoia salta all'occhio invece di scegliere in silenzio.
 *
 * Stava scritta due volte, di proposito, perché una scorciatoia ripetuta si vede;
 * ora gli adapter che ne hanno bisogno sono quattro, e a quel punto la ripetizione
 * ha smesso di essere un segnale ed è diventata solo un posto in più da correggere.
 * Qui è una funzione sola: il giorno di `/api/users/me` si cambia una riga.
 */
/**
 * L'id già chiesto, per client HTTP: l'utente non cambia durante la vita dell'app, e
 * richiederlo prima di ogni chiamata raddoppiava i viaggi (cinque in più aprendo i
 * Movimenti). Per client e non globale, così ogni test parte da zero.
 */
const known = new WeakMap<HttpClient, Promise<string>>();

export function currentUserId(
  http: HttpClient,
  baseUrl: string,
  signal?: AbortSignal,
): Promise<string> {
  const cached = known.get(http);
  if (cached) {
    return cached;
  }
  // La richiesta è condivisa fra chi la aspetta, quindi non porta il segnale di uno di
  // loro: annullarla per uno la annullerebbe per tutti. Il segnale vale solo per chi la
  // fa partire, e solo finché non c'è una risposta da ricordare.
  const request = fetchCurrentUserId(http, baseUrl);
  known.set(http, request);
  // Un errore non si ricorda: prima del primo accesso l'utente non c'è ancora, e la
  // volta dopo va richiesto davvero.
  request.catch(() => known.delete(http));
  return signal ? abortable(request, signal) : request;
}

async function fetchCurrentUserId(http: HttpClient, baseUrl: string): Promise<string> {
  const page = await requestJson<PageResponse<{ readonly id: string }>>(
    http,
    'GET',
    `${baseUrl}/users?page=0&size=1`,
  );

  const first = page.items.at(0);
  if (!first) {
    throw new Error('Nessun utente configurato.');
  }
  return first.id;
}

function abortable<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  if (signal.aborted) {
    return Promise.reject(signal.reason);
  }
  return new Promise<T>((resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true });
    promise.then(resolve, reject);
  });
}
