import { HttpClient } from '@angular/common/http';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface RequestOptions {
  readonly body?: unknown;
  readonly signal?: AbortSignal;
}

/**
 * Esegue una richiesta come `Promise`, onorando l'`AbortSignal` delle porte.
 *
 * Le porte del dominio sono `async` e ricevono un segnale di annullamento; qui
 * lo si traduce nell'unico modo che `HttpClient` conosce, cioè disiscrivendosi.
 * Senza questo ponte ogni `resource()` che si rigenera lascerebbe in volo la
 * richiesta precedente.
 */
export function requestJson<T>(
  http: HttpClient,
  method: HttpMethod,
  url: string,
  options: RequestOptions = {},
): Promise<T> {
  const request = http.request<T>(method, url, {
    body: options.body,
    observe: 'body',
    responseType: 'json',
  });

  return new Promise<T>((resolve, reject) => {
    const signal = options.signal;
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }

    let received = false;
    const subscription = request.subscribe({
      next: (value) => {
        received = true;
        resolve(value);
      },
      error: reject,
      // Una risposta senza corpo dove ne attendevamo uno è un contratto rotto,
      // non un successo silenzioso.
      complete: () => {
        if (!received) reject(new Error(`Risposta vuota da ${method} ${url}.`));
      },
    });

    signal?.addEventListener(
      'abort',
      () => {
        subscription.unsubscribe();
        reject(signal.reason);
      },
      { once: true },
    );
  });
}
