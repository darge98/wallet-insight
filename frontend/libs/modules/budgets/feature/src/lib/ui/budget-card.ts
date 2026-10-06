import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { BudgetId, BudgetStatus } from '@wallet/budgets-domain';
import { BudgetMeter } from '@wallet/budgets-ui';
import { formatMoney, Icon } from '@wallet/shared-ui';

/** Un budget principale nel mese, con le sue categorie e i sotto-budget sotto. */
@Component({
  selector: 'app-budget-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BudgetMeter, Icon],
  host: { class: 'block' },
  template: `
    <app-budget-meter [status]="status()" />

    <!-- Dettaglio, non risposta: su telefono resta fuori. -->
    <p class="mt-1 hidden text-xs leading-relaxed text-ink-faint sm:block">
      {{ categoriesLine() }}
    </p>

    @if (status().children.length > 0) {
      <ul
        class="mt-4 flex flex-col gap-4 border-l border-line pl-4"
        [attr.aria-label]="'Sotto-budget di ' + status().name"
      >
        @for (child of status().children; track child.id) {
          <li>
            <app-budget-meter [status]="child" [compact]="true" />
            @if (editable()) {
              <button type="button" class="btn btn-text" (click)="edit.emit(child.id)">
                Modifica
              </button>
            }
          </li>
        }
      </ul>
    }

    @if (editable()) {
      <div class="mt-1 flex flex-wrap gap-x-5 sm:mt-2">
        <button type="button" class="btn btn-text" (click)="edit.emit(status().id)">
          Modifica
        </button>
        <button type="button" class="btn btn-text" (click)="addChild.emit(status().id)">
          <app-icon name="plus" [size]="12" />
          Sotto-budget
        </button>
      </div>
    }
  `,
})
export class BudgetCard {
  readonly status = input.required<BudgetStatus>();
  /** Un budget cancellato non ha più una definizione: nei mesi passati resta solo da guardare. */
  readonly editable = input(true);

  readonly edit = output<BudgetId>();
  readonly addChild = output<BudgetId>();

  protected readonly categoriesLine = computed(() =>
    this.status()
      .categories.map((category) => `${category.name} ${formatMoney(category.spent)}`)
      .join(' · '),
  );
}
