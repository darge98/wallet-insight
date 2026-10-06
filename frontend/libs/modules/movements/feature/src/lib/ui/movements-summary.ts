import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { RecordTotals } from '@wallet/shared-domain';
import { formatCount, formatMoney, Skeleton } from '@wallet/shared-ui';

interface SummaryTile {
  readonly label: string;
  readonly value: string;
  readonly tone: string;
}

const PLACEHOLDER = '—';

/** Totali del risultato filtrato, indipendenti dalla pagina visualizzata. */
@Component({
  selector: 'app-movements-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Skeleton],
  host: {
    // Stessa griglia degli indicatori della Panoramica, e per lo stesso motivo:
    // a 320 px di finestra una grondaia da 40 px lascia 116 px alla colonna,
    // mentre «70.626,92 €» — le entrate di tutto lo storico — ne misura 122 e
    // non può andare a capo, perché Intl mette uno spazio unificatore prima
    // dell'euro. Sotto i 360 px le tessere si impilano: `type-figure-sm` è una
    // misura fissa, non si stringe come il numero grande dei KPI.
    class: 'grid grid-cols-2 gap-x-5 gap-y-6 max-[359px]:grid-cols-1 sm:grid-cols-4 sm:gap-x-10',
  },
  template: `
    @for (tile of tiles(); track tile.label) {
      <div>
        <p class="text-[0.8125rem] text-ink-muted">{{ tile.label }}</p>
        @if (showSkeleton()) {
          <app-skeleton class="mt-2" [height]="22" width="70%" />
        } @else {
          <p class="tnum type-figure-sm mt-1" [class]="tile.tone">{{ tile.value }}</p>
        }
      </div>
    }
  `,
})
export class MovementsSummary {
  readonly totals = input.required<RecordTotals | null>();
  readonly loading = input(false);

  protected readonly showSkeleton = computed(() => this.loading() && this.totals() === null);

  protected readonly tiles = computed<readonly SummaryTile[]>(() => {
    const totals = this.totals();

    return [
      {
        label: 'Movimenti',
        value: totals ? formatCount(totals.count) : PLACEHOLDER,
        tone: 'text-ink',
      },
      {
        label: 'Entrate',
        value: totals ? formatMoney(totals.income) : PLACEHOLDER,
        tone: 'text-ink',
      },
      {
        label: 'Uscite',
        value: totals ? formatMoney(totals.expenses) : PLACEHOLDER,
        tone: 'text-ink',
      },
      {
        label: 'Margine',
        value: totals ? formatMoney(totals.net, { signed: true }) : PLACEHOLDER,
        tone: totals && totals.net.amount < 0 ? 'text-negative' : 'text-accent',
      },
    ];
  });
}
