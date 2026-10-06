import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DateRange, PeriodPreset, RecordEdit, RecordId } from '@wallet/shared-domain';
import {
  formatCount,
  formatPercent,
  Icon,
  PaginationControl,
  PeriodFilterBar,
} from '@wallet/shared-ui';

import { MovementsFacade } from '../data-access/movements-facade';
import { BreakdownEntry, BreakdownList } from '../ui/breakdown-list';
import { CollapsibleSection } from '../ui/collapsible-section';
import { MovementsList } from '../ui/movements-list';
import { MovementsSummary } from '../ui/movements-summary';
import { RecordDetailPanel } from '../ui/record-detail-panel';
import { SubscriptionDraft } from '@wallet/subscriptions-domain';
import { SubscriptionEditor } from '@wallet/subscriptions-ui';

@Component({
  selector: 'app-movements-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [MovementsFacade],
  imports: [
    RouterLink,
    Icon,
    PaginationControl,
    PeriodFilterBar,
    MovementsSummary,
    MovementsList,
    CollapsibleSection,
    BreakdownList,
    RecordDetailPanel,
    SubscriptionEditor,
  ],
  templateUrl: './movements-page.html',
  host: { class: 'block' },
})
export class MovementsPage {
  protected readonly facade = inject(MovementsFacade);

  protected readonly categoryEntries = computed<readonly BreakdownEntry[]>(() =>
    this.facade.categoryBreakdown.value().map((slice) => ({
      key: slice.categoryId,
      label: slice.name,
      amount: slice.total,
      caption: `${formatPercent(slice.share)} delle uscite · ${this.movementsLabel(
        slice.transactionCount,
      )}`,
    })),
  );

  protected readonly counterPartyEntries = computed<readonly BreakdownEntry[]>(() =>
    this.facade.spendingTargets.value().map((target) => ({
      key: target.counterParty,
      label: target.counterParty,
      amount: target.total,
      caption: this.movementsLabel(target.transactionCount),
    })),
  );

  protected readonly selectedCategory = computed(() => {
    const categoryId = this.facade.selectedRecord()?.categoryId;
    return categoryId ? (this.facade.categoryById().get(categoryId) ?? null) : null;
  });

  protected readonly selectedAccount = computed(() => {
    const record = this.facade.selectedRecord();
    return record ? (this.facade.accountById().get(record.accountId) ?? null) : null;
  });

  protected onPeriod(preset: PeriodPreset): void {
    this.facade.selectPeriod(preset);
  }

  protected onCustomRange(range: DateRange): void {
    this.facade.setCustomRange(range);
  }

  protected onSelect(id: RecordId): void {
    this.facade.select(id);
  }

  protected onSave(edit: RecordEdit): void {
    void this.facade.save(edit);
  }

  protected onSaveSubscription(draft: SubscriptionDraft): void {
    void this.facade.saveSubscription(draft);
  }

  private movementsLabel(count: number): string {
    return `${formatCount(count)} ${count === 1 ? 'movimento' : 'movimenti'}`;
  }
}
