import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Contenitore standard: bordo sottile, niente ombra.
 *
 * Slot disponibili: `[cardActions]` per l'elemento in alto a destra e il
 * contenuto proiettato di default per il corpo.
 *
 * Il fianco è 16 px sul telefono e 24 da `sm`: su 375 px di schermo la pagina
 * si è già tolta 48 px di margine suo, e altri 48 qui dentro lasciavano 279 px
 * al contenuto. Su un grafico a barre orizzontali quei 32 px vanno tutti alle
 * barre, che sono la parte che si legge — le etichette e gli importi hanno una
 * larghezza fissa e non ne approfitterebbero.
 */
@Component({
  selector: 'app-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    // `min-w-0` per non propagare all'esterno la larghezza intrinseca di ciò che
    // sta dentro: un canvas di ECharts la dichiara in pixel, e senza questo una
    // card dentro una griglia o un flex la spinge fino a far debordare la pagina.
    class: 'flex min-w-0 flex-col rounded-card border border-line bg-surface',
  },
  template: `
    @if (heading()) {
      <header class="flex items-baseline justify-between gap-4 px-4 pt-5 pb-4 sm:px-6">
        <h2 class="type-section min-w-0 text-ink">{{ heading() }}</h2>
        <div class="flex shrink-0 items-center gap-2 text-xs whitespace-nowrap text-ink-muted">
          <ng-content select="[cardActions]" />
        </div>
      </header>
    }
    <div class="flex min-h-0 flex-1 flex-col px-4 pb-6 sm:px-6" [class.pt-6]="!heading()">
      <ng-content />
    </div>
  `,
})
export class Card {
  readonly heading = input<string>('');
}
