import { InjectionToken } from '@angular/core';

/**
 * Radice delle API.
 *
 * Il valore predefinito è relativo perché in esecuzione reale il frontend sta
 * dietro nginx, che inoltra `/api/` al backend: stessa origine, nessun CORS.
 * In sviluppo il dev server vive su un'altra porta e il valore va fornito
 * in `app.config.ts`.
 */
export const API_BASE_URL = new InjectionToken<string>('ApiBaseUrl', {
  providedIn: 'root',
  factory: () => '/api',
});
