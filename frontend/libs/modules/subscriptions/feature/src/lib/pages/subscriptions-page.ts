import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { Account } from '@wallet/accounts-domain';
import { Subscription, SubscriptionDraft } from '@wallet/subscriptions-domain';
import { SubscriptionEditor } from '@wallet/subscriptions-ui';
import {
  Alert,
  EmptyState,
  formatMonth,
  Icon,
  SegmentedControl,
  SegmentedOption,
} from '@wallet/shared-ui';

import { SubscriptionsFacade, SubscriptionsView } from '../data-access/subscriptions-facade';
import { RenewalCalendar } from '../ui/renewal-calendar';
import { SubscriptionRow } from '../ui/subscription-row';
import { SubscriptionsSummary } from '../ui/subscriptions-summary';
import { UpcomingCharges } from '../ui/upcoming-charges';

const VIEW_OPTIONS: readonly SegmentedOption<SubscriptionsView>[] = [
  { value: 'list', label: 'Elenco' },
  { value: 'calendar', label: 'Calendario' },
];

@Component({
  selector: 'app-subscriptions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [SubscriptionsFacade],
  imports: [
    Alert,
    EmptyState,
    Icon,
    RenewalCalendar,
    SegmentedControl,
    SubscriptionEditor,
    SubscriptionRow,
    SubscriptionsSummary,
    UpcomingCharges,
  ],
  templateUrl: './subscriptions-page.html',
  host: { class: 'block' },
})
export class SubscriptionsPage {
  protected readonly facade = inject(SubscriptionsFacade);

  protected readonly viewOptions = VIEW_OPTIONS;

  protected readonly monthLabel = computed(() => formatMonth(this.facade.month()));

  protected readonly hasSubscriptions = computed(
    () => this.facade.subscriptions.value().length > 0,
  );

  /** Un conto archiviato non si propone, ma resta scelto su chi lo usa già. */
  protected readonly editorAccounts = computed<readonly Account[]>(() => {
    const visible = this.facade.accounts();
    const current = this.facade.editing()?.subscription?.accountId ?? null;
    const archived = current === null ? undefined : this.facade.accountById().get(current);
    return archived && !visible.includes(archived) ? [...visible, archived] : visible;
  });

  protected account(subscription: Subscription): Account | null {
    return subscription.accountId === null
      ? null
      : (this.facade.accountById().get(subscription.accountId) ?? null);
  }

  protected onSave(draft: SubscriptionDraft): void {
    void this.facade.save(draft);
  }

  protected onRemove(): void {
    void this.facade.remove();
  }
}
