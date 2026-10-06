import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Account } from '@wallet/accounts-domain';
import { AccountId, Category, CategoryId, FinanceRecord, RecordId } from '@wallet/shared-domain';
import { DayLabelPipe, EmptyState, RecordRow, Skeleton } from '@wallet/shared-ui';

import { RecordGroup } from '../data-access/movements-facade';

@Component({
  selector: 'app-movements-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyState, RecordRow, Skeleton, DayLabelPipe],
  host: { class: 'block' },
  template: `
    @if (loading() && groups().length === 0) {
      <div class="flex flex-col gap-4">
        @for (placeholder of skeletonRows; track placeholder) {
          <app-skeleton [height]="22" />
        }
      </div>
    } @else if (groups().length === 0) {
      <app-empty-state title="Nessun movimento trovato" />
    } @else {
      <div class="flex flex-col gap-7" [class.opacity-55]="loading()">
        @for (group of groups(); track group.date) {
          <section>
            <h3 class="type-eyebrow text-ink-faint">{{ group.date | dayLabel }}</h3>
            <ul class="mt-2 flex flex-col gap-0.5">
              @for (record of group.records; track record.id) {
                <li>
                  <button
                    type="button"
                    class="-mx-3 w-[calc(100%+1.5rem)] cursor-pointer rounded-control px-3 text-left transition-colors hover:bg-surface-2"
                    [class]="record.id === selectedId() ? 'bg-surface-2' : ''"
                    (click)="selected.emit(record.id)"
                  >
                    <app-record-row
                      [record]="record"
                      [category]="categoryOf(record)"
                      [accountName]="accountOf(record)?.name ?? null"
                      [accountColor]="accountOf(record)?.color ?? null"
                      [showDate]="false"
                    />
                  </button>
                </li>
              }
            </ul>
          </section>
        }
      </div>
    }
  `,
})
export class MovementsList {
  readonly groups = input.required<readonly RecordGroup[]>();
  readonly categoryById = input.required<ReadonlyMap<CategoryId, Category>>();
  /**
   * I conti, per dare a ogni riga la barra del suo: la lista li riceve già
   * indicizzati dal facade, che li tiene per i filtri, invece di rifare la mappa
   * a ogni disegno.
   */
  readonly accountById = input.required<ReadonlyMap<AccountId, Account>>();
  readonly selectedId = input<RecordId | null>(null);
  readonly loading = input(false);

  readonly selected = output<RecordId>();

  protected readonly skeletonRows = [1, 2, 3, 4, 5, 6, 7, 8];

  /** Il conto su cui il movimento poggia; manca solo se l'elenco dei conti non è ancora arrivato. */
  protected accountOf(record: FinanceRecord): Account | null {
    return this.accountById().get(record.accountId) ?? null;
  }

  /** Un movimento può non avere categoria: la riga lo dice, invece di cercarne una. */
  protected categoryOf(record: FinanceRecord): Category | null {
    const id = record.categoryId;
    return id === null ? null : (this.categoryById().get(id) ?? null);
  }
}
