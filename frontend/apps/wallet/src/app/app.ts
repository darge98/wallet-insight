import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { UserFacade } from '@wallet/user-data-access';

/**
 * Radice dell'applicazione.
 *
 * Distingue tre momenti: il profilo non è ancora noto (splash), non è leggibile
 * (recupero con "Riprova") oppure è pronto/assente, e allora si lascia lavorare
 * il router. Mostrare l'onboarding durante un errore sarebbe sbagliato: si
 * creerebbe un secondo profilo per un problema di lettura.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    @if (users.status() === 'error') {
      <div class="flex min-h-dvh flex-col items-center justify-center gap-3 bg-bg px-6 text-center">
        <svg
          width="28"
          height="28"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="text-negative"
          aria-hidden="true"
          focusable="false"
        >
          <path d="M12 3.5l9 16.5H3zM12 10v4M12 17.2h.01" />
        </svg>
        <h1 class="type-section text-ink">Impossibile leggere il profilo.</h1>
        <p class="max-w-sm text-sm text-ink-muted">
          Non è stato possibile verificare se un profilo esiste già. Riprova: se il problema
          persiste, controlla che il browser non stia bloccando l’archiviazione locale.
        </p>
        <button type="button" class="btn btn-primary mt-1" (click)="retry()">Riprova</button>
      </div>
    } @else if (users.status() === 'ready' || users.status() === 'absent') {
      <router-outlet />
    } @else {
      <div class="flex min-h-dvh items-center justify-center bg-bg">
        <p class="text-sm text-ink-muted">Un istante…</p>
      </div>
    }
  `,
})
export class App {
  protected readonly users = inject(UserFacade);

  constructor() {
    void this.users.ensureLoaded();
  }

  protected retry(): void {
    void this.users.retry();
  }
}
