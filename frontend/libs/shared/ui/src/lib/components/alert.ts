import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Icon } from './icon';

/**
 * Un errore della pagina: riquadro con bordo, icona e testo. Le azioni («Riprova»)
 * arrivano nel contenuto; la ✕ compare solo se qualcuno ascolta `dismissed`.
 */
@Component({
  selector: 'app-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: {
    class: 'flex items-start gap-2 rounded-card border border-line px-4 py-3 text-sm text-negative',
    role: 'alert',
  },
  template: `
    <app-icon name="alert" [size]="16" class="mt-0.5" />
    <div class="min-w-0 flex-1">{{ message() }} <ng-content /></div>
    @if (dismissible()) {
      <button
        type="button"
        class="btn btn-icon -my-2 -mr-2"
        aria-label="Chiudi"
        (click)="dismissed.emit()"
      >
        <app-icon name="close" [size]="14" />
      </button>
    }
  `,
})
export class Alert {
  readonly message = input.required<string>();
  readonly dismissible = input(false);

  readonly dismissed = output<void>();
}
