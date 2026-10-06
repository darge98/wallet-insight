import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { BudgetDraft, BudgetId } from '@wallet/budgets-domain';
import { daysLeftInMonth } from '@wallet/shared-domain';
import { Alert, EmptyState, formatMonth, Icon } from '@wallet/shared-ui';

import { BudgetsFacade } from '../data-access/budgets-facade';
import { BudgetCard } from '../ui/budget-card';
import { BudgetEditor } from '../ui/budget-editor';
import { BudgetMonthPanel } from '../ui/budget-month-panel';

@Component({
  selector: 'app-budgets-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [BudgetsFacade],
  imports: [Alert, BudgetCard, BudgetEditor, BudgetMonthPanel, EmptyState, Icon],
  templateUrl: './budgets-page.html',
  host: { class: 'block' },
})
export class BudgetsPage {
  protected readonly facade = inject(BudgetsFacade);

  protected readonly monthLabel = computed(() => formatMonth(this.facade.month()));

  protected readonly daysLeft = computed(() =>
    this.facade.isCurrentMonth() ? daysLeftInMonth(this.facade.today()) : null,
  );

  protected readonly hasBudgets = computed(() => this.facade.budgets.value().length > 0);

  protected isEditable(id: BudgetId): boolean {
    return this.facade.budgetById().has(id);
  }

  protected onSave(draft: BudgetDraft): void {
    void this.facade.save(draft);
  }

  protected onRemove(): void {
    void this.facade.remove();
  }
}
