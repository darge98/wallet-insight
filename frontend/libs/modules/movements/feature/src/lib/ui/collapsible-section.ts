import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

import { Icon } from '@wallet/shared-ui';

/**
 * Sezione del riepilogo laterale: apribile su viewport strette, sempre aperta
 * da `lg` in su.
 *
 * Su un telefono le due classifiche stanno *sopra* l'elenco — è il riassunto,
 * e viene prima del dettaglio — ma se stessero aperte spingerebbero i movimenti
 * fuori dalla prima schermata. Chiuse sono due righe, e chi vuole guardarle le
 * apre. Da `lg` in su la colonna laterale c'è ed è tutta loro: lì non c'è niente
 * da aprire, e il comando sparisce invece di restare a non fare nulla.
 *
 * Il titolo è il comando, come nella sidebar: una sezione che si apre e si
 * chiude non ha bisogno di un pulsante accanto a sé, e `aria-expanded` annuncia
 * lo stato a chi non vede ruotare la freccia. Titolo e comando sono due
 * elementi e non uno solo perché a un pulsante nascosto da `lg:hidden` non
 * servono né stato né ruolo: sopra quella soglia resta un titolo e basta.
 */
@Component({
  selector: 'app-collapsible-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    <section [attr.aria-labelledby]="headingId()">
      <h2 [id]="headingId()" class="type-eyebrow text-ink-faint">
        <button
          type="button"
          class="group flex w-full cursor-pointer items-center gap-2 py-1 text-left lg:hidden"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="headingId() + '-content'"
          (click)="open.set(!open())"
        >
          <span class="flex-1 transition-colors group-hover:text-ink-muted">{{ heading() }}</span>
          <app-icon
            name="chevron-down"
            [size]="14"
            class="shrink-0 transition-transform"
            [class.-rotate-90]="!open()"
          />
        </button>
        <span class="hidden lg:block">{{ heading() }}</span>
      </h2>

      <!-- «lg:block» vince su «hidden» perché le varianti stanno più in basso
           nel foglio: da «lg» in su il contenuto c'è comunque, qualunque sia lo
           stato del comando che lassù non si vede. -->
      <div [id]="headingId() + '-content'" class="mt-4 lg:block" [class.hidden]="!open()">
        <ng-content />
      </div>
    </section>
  `,
})
export class CollapsibleSection {
  readonly heading = input.required<string>();
  /** Serve a legare titolo e contenuto: `aria-labelledby` e `aria-controls`. */
  readonly headingId = input.required<string>();

  protected readonly open = signal(false);
}
