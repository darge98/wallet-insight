import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { formatCount } from '../format/format';

import { Icon } from './icon';

/** Paginazione compatta: precedente/successiva più indicatore di posizione. */
@Component({
  selector: 'app-pagination-control',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'flex items-center justify-between gap-3' },
  template: `
    <p class="text-xs text-ink-muted">{{ rangeLabel() }}</p>

    <div class="flex items-center gap-1">
      <button
        type="button"
        class="btn btn-icon disabled:cursor-not-allowed"
        aria-label="Pagina precedente"
        [disabled]="index() === 0"
        (click)="pageChange.emit(index() - 1)"
      >
        <app-icon name="chevron-left" [size]="16" />
      </button>

      <span class="tnum px-1.5 text-xs text-ink-muted">
        {{ index() + 1 }} / {{ pageCount() || 1 }}
      </span>

      <button
        type="button"
        class="btn btn-icon disabled:cursor-not-allowed"
        aria-label="Pagina successiva"
        [disabled]="index() >= pageCount() - 1"
        (click)="pageChange.emit(index() + 1)"
      >
        <app-icon name="chevron-right" [size]="16" />
      </button>
    </div>
  `,
})
export class PaginationControl {
  readonly index = input.required<number>();
  readonly pageCount = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly total = input.required<number>();

  readonly pageChange = output<number>();

  protected readonly rangeLabel = computed(() => {
    const total = this.total();
    if (total === 0) {
      return 'Nessun risultato';
    }
    const start = this.index() * this.pageSize() + 1;
    const end = Math.min(total, start + this.pageSize() - 1);
    return `${formatCount(start)}–${formatCount(end)} di ${formatCount(total)}`;
  });
}
