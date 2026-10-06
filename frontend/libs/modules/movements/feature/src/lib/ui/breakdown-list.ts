import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Money } from '@wallet/shared-domain';
import { EmptyState, MoneyPipe, Skeleton } from '@wallet/shared-ui';

export interface BreakdownEntry {
  readonly key: string;
  readonly label: string;
  readonly amount: Money;
  readonly caption: string;
}

/**
 * Classifica con barra proporzionale.
 *
 * Una sola tinta: la grandezza è già codificata dalla lunghezza della barra, e
 * assegnare un colore diverso a ogni voce aggiungerebbe rumore senza aggiungere
 * informazione.
 */
@Component({
  selector: 'app-breakdown-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, EmptyState, Skeleton],
  host: { class: 'block' },
  template: `
    @if (loading() && entries().length === 0) {
      <div class="flex flex-col gap-4">
        @for (placeholder of [1, 2, 3, 4]; track placeholder) {
          <app-skeleton [height]="26" />
        }
      </div>
    } @else if (entries().length === 0) {
      <app-empty-state [title]="emptyTitle()" />
    } @else {
      <ol class="flex flex-col gap-3.5">
        @for (entry of entries(); track entry.key) {
          <li>
            <div class="flex items-baseline justify-between gap-3">
              <span class="min-w-0 truncate text-sm text-ink">{{ entry.label }}</span>
              <span class="tnum shrink-0 text-sm text-ink">{{
                entry.amount | money: 'rounded'
              }}</span>
            </div>
            <div class="mt-1.5 h-1 w-full overflow-hidden rounded-full bg-track">
              <div class="h-full rounded-full bg-accent" [style.width.%]="shareOf(entry)"></div>
            </div>
            <p class="mt-1 text-xs text-ink-muted">{{ entry.caption }}</p>
          </li>
        }
      </ol>
    }
  `,
})
export class BreakdownList {
  readonly entries = input.required<readonly BreakdownEntry[]>();
  readonly loading = input(false);

  /**
   * Il vuoto ha cause diverse a seconda della classifica, e dirlo sbagliato è
   * peggio che non dirlo: su una finestra di pochi giorni può esserci una spesa
   * che nessuna controparte rivendica, e "nessuna spesa nel periodo" accanto a
   * un totale diverso da zero è semplicemente falso.
   */
  readonly emptyTitle = input('Nessuna spesa nel periodo');

  private readonly maxAmount = computed(() =>
    Math.max(1, ...this.entries().map((entry) => entry.amount.amount)),
  );

  protected shareOf(entry: BreakdownEntry): number {
    return (entry.amount.amount / this.maxAmount()) * 100;
  }
}
