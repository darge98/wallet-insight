import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import {
  SubscriptionId,
  SubscriptionsOverview,
  UPCOMING_HORIZONS,
  UpcomingHorizon,
} from '@wallet/subscriptions-domain';
import {
  formatCompactDate,
  MoneyPipe,
  SegmentedControl,
  SegmentedOption,
  Skeleton,
} from '@wallet/shared-ui';

import { formatDaysUntil } from './subscription-format';

const HORIZON_OPTIONS: readonly SegmentedOption<`${UpcomingHorizon}`>[] = UPCOMING_HORIZONS.map(
  (days) => ({ value: `${days}`, label: `${days} giorni` }),
);

/** Gli addebiti dei prossimi giorni, dal più vicino, col totale della finestra. */
@Component({
  selector: 'app-upcoming-charges',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, SegmentedControl, Skeleton],
  host: { class: 'block' },
  template: `
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h2 id="subscriptions-upcoming" class="type-section text-ink">In arrivo</h2>
      <app-segmented-control
        label="Finestra degli addebiti"
        [options]="horizonOptions"
        [value]="horizonValue()"
        (valueChange)="onHorizon($event)"
      />
    </div>

    @if (overview(); as current) {
      @if (current.upcoming.length > 0) {
        <ul class="mt-3 flex flex-col gap-0.5" aria-labelledby="subscriptions-upcoming">
          @for (charge of current.upcoming; track charge.subscriptionId + charge.date) {
            <li>
              <button
                type="button"
                class="-mx-3 w-[calc(100%+1.5rem)] cursor-pointer rounded-control px-3 text-left transition-colors hover:bg-surface-2"
                (click)="edit.emit(charge.subscriptionId)"
              >
                <span class="flex items-baseline gap-3 py-2.5 sm:gap-4">
                  <span class="tnum w-12 shrink-0 text-xs text-ink-muted">{{
                    day(charge.date)
                  }}</span>
                  <span class="min-w-0 flex-1 truncate text-sm text-ink">{{ charge.name }}</span>
                  <span class="hidden shrink-0 text-xs text-ink-faint sm:inline">{{
                    until(current.today, charge.date)
                  }}</span>
                  <span class="tnum w-24 shrink-0 text-right text-sm text-ink sm:w-28">{{
                    charge.amount | money
                  }}</span>
                </span>
              </button>
            </li>
          }
        </ul>
        <p class="tnum mt-3 flex items-baseline justify-between gap-4 text-sm">
          <span class="text-ink-muted">Totale nei prossimi {{ horizon() }} giorni</span>
          <span class="font-medium text-ink">{{ current.upcomingTotal | money }}</span>
        </p>
      } @else {
        <p class="mt-3 text-sm text-ink-muted">
          Nessun addebito nei prossimi {{ horizon() }} giorni.
        </p>
      }
    } @else {
      <div class="mt-3 flex flex-col gap-4">
        <app-skeleton [height]="22" />
        <app-skeleton [height]="22" />
        <app-skeleton [height]="22" />
      </div>
    }
  `,
})
export class UpcomingCharges {
  readonly overview = input.required<SubscriptionsOverview | undefined>();
  readonly horizon = input.required<UpcomingHorizon>();

  readonly horizonChange = output<UpcomingHorizon>();
  readonly edit = output<SubscriptionId>();

  protected readonly horizonOptions = HORIZON_OPTIONS;

  protected readonly horizonValue = computed(() => `${this.horizon()}` as const);

  protected onHorizon(value: `${UpcomingHorizon}`): void {
    const horizon = UPCOMING_HORIZONS.find((days) => `${days}` === value);
    if (horizon !== undefined) this.horizonChange.emit(horizon);
  }

  protected day(date: string): string {
    return formatCompactDate(date);
  }

  protected until(today: string, date: string): string {
    return formatDaysUntil(today, date);
  }
}
