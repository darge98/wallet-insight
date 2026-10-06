import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Money } from '@wallet/shared-domain';
import { MoneyPipe, Skeleton } from '@wallet/shared-ui';

/**
 * Indicatore di sintesi: etichetta, numero, dettaglio.
 *
 * Nessuna card e nessuna icona — il numero è già l'elemento forte, aggiungere
 * una cornice attorno a ciascuno appiattirebbe la gerarchia della pagina.
 */
@Component({
  selector: 'app-kpi-block',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, Skeleton],
  host: { class: 'block' },
  template: `
    <p class="text-[0.8125rem] text-ink-muted">{{ label() }}</p>

    @if (loading()) {
      <app-skeleton class="mt-2.5" [height]="34" width="72%" />
      <app-skeleton class="mt-3" [height]="11" width="52%" />
    } @else {
      <p class="tnum type-figure mt-2" [class]="highlight() ? 'text-accent' : 'text-ink'">
        {{ value() | money }}
      </p>
      <!-- La tendenza è dettaglio: su telefono resta il numero. -->
      <p
        class="mt-2 hidden text-xs sm:block"
        [class]="highlight() ? 'text-accent' : 'text-ink-muted'"
      >
        {{ detail() }}
      </p>
    }
  `,
})
export class KpiBlock {
  readonly label = input.required<string>();
  readonly value = input.required<Money | undefined>();
  readonly detail = input('');
  /** Evidenzia in accento: riservato al solo indicatore protagonista. */
  readonly highlight = input(false);
  readonly loading = input(false);
}
