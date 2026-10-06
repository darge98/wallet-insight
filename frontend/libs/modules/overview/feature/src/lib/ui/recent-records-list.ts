import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Category, CategoryId, FinanceRecord } from '@wallet/shared-domain';
import { EmptyState, RecordRow, Skeleton } from '@wallet/shared-ui';

@Component({
  selector: 'app-recent-records-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyState, RecordRow, Skeleton],
  host: { class: 'block' },
  template: `
    @if (loading() && records().length === 0) {
      <div class="flex flex-col gap-4">
        @for (placeholder of [1, 2, 3, 4]; track placeholder) {
          <app-skeleton [height]="20" />
        }
      </div>
    } @else if (records().length === 0) {
      <app-empty-state title="Nessun movimento nel periodo" />
    } @else {
      <ul class="flex flex-col gap-1.5">
        @for (record of records(); track record.id) {
          <li>
            <app-record-row [record]="record" [category]="categoryOf(record)" />
          </li>
        }
      </ul>
    }
  `,
})
export class RecentRecordsList {
  readonly records = input.required<readonly FinanceRecord[]>();
  readonly categories = input.required<readonly Category[]>();
  readonly loading = input(false);

  private readonly categoryById = computed<ReadonlyMap<CategoryId, Category>>(
    () => new Map(this.categories().map((category) => [category.id, category])),
  );

  /** Un movimento può non avere categoria: la riga lo dice, invece di cercarne una. */
  protected categoryOf(record: FinanceRecord): Category | null {
    const id = record.categoryId;
    return id === null ? null : (this.categoryById().get(id) ?? null);
  }
}
