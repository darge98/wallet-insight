import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { SubscriptionsOverview } from '@wallet/subscriptions-domain';
import { MoneyPipe, Skeleton, SoftPanel } from '@wallet/shared-ui';

/**
 * Quanto pesano gli abbonamenti attivi, ricondotti a un mese medio: è l'unico
 * modo di sommare un canone mensile con uno annuale.
 */
@Component({
  selector: 'app-subscriptions-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, Skeleton, SoftPanel],
  host: { class: 'block' },
  template: `
    <app-soft-panel heading="Costo mensile">
      @if (overview(); as current) {
        <p class="tnum type-figure mt-3 text-ink">{{ current.monthlyCost | money }}</p>

        <dl class="mt-6 flex flex-col gap-2 text-sm">
          <div class="flex items-baseline justify-between gap-4">
            <dt class="text-ink-on-soft">Costo annuo</dt>
            <dd class="tnum text-ink">{{ current.yearlyCost | money }}</dd>
          </div>
          <div class="flex items-baseline justify-between gap-4">
            <dt class="text-ink-on-soft">Attivi</dt>
            <dd class="tnum text-ink">{{ current.activeCount }}</dd>
          </div>
        </dl>
      } @else {
        <app-skeleton class="mt-4" [height]="40" width="64%" />
        <app-skeleton class="mt-4" [height]="12" width="80%" />
      }
    </app-soft-panel>
  `,
})
export class SubscriptionsSummary {
  readonly overview = input.required<SubscriptionsOverview | undefined>();
}
