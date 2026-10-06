import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { IsoDate, YearMonth } from '@wallet/shared-domain';
import { MonthCharges, SubscriptionId, UpcomingCharge } from '@wallet/subscriptions-domain';
import { EmptyState, formatLongDate, MoneyPipe, Skeleton } from '@wallet/shared-ui';

import { calendarWeeks } from './calendar-weeks';

const WEEKDAYS = ['lun', 'mar', 'mer', 'gio', 'ven', 'sab', 'dom'] as const;

/** Oltre questo numero una cella dice «+N»: la riga deve restare alta come le altre. */
const VISIBLE_PER_DAY = 2;

interface DayCharges {
  readonly date: IsoDate;
  readonly charges: readonly UpcomingCharge[];
}

/**
 * Il calendario dei rinnovi: il mese in griglia, ogni addebito sul suo giorno.
 *
 * Su telefono sette colonne sarebbero larghe 45 px, troppo poco per un nome e
 * un importo: lì il mese diventa l'elenco dei soli giorni con un addebito, come
 * i Movimenti raggruppati per giorno.
 */
@Component({
  selector: 'app-renewal-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyState, MoneyPipe, Skeleton],
  host: { class: 'block' },
  template: `
    @if (charges(); as current) {
      <div class="hidden sm:block" [class.opacity-55]="loading()">
        <div class="grid grid-cols-7 gap-px" aria-hidden="true">
          @for (weekday of weekdays; track weekday) {
            <p class="type-eyebrow pb-2 text-ink-faint">{{ weekday }}</p>
          }
        </div>
        <div
          class="grid grid-cols-7 gap-px overflow-hidden rounded-card border border-line bg-line"
          role="grid"
          [attr.aria-label]="'Addebiti di ' + monthLabel()"
        >
          @for (week of weeks(); track $index) {
            @for (date of week; track $index) {
              <div
                class="flex min-h-24 min-w-0 flex-col gap-1 bg-surface p-1.5"
                role="gridcell"
                [class.bg-surface-2]="date === null"
              >
                @if (date) {
                  <span
                    class="tnum px-1 text-xs"
                    [class]="date === today() ? 'font-medium text-accent' : 'text-ink-faint'"
                    [attr.aria-current]="date === today() ? 'date' : null"
                    >{{ dayNumber(date) }}</span
                  >
                  @for (charge of visible(date); track charge.subscriptionId) {
                    <button
                      type="button"
                      class="flex w-full min-w-0 cursor-pointer flex-col rounded-control px-1 py-0.5 text-left transition-colors hover:bg-surface-2"
                      [title]="charge.name"
                      (click)="edit.emit(charge.subscriptionId)"
                    >
                      <span class="truncate text-xs text-ink">{{ charge.name }}</span>
                      <span class="tnum text-xs text-ink-muted">{{ charge.amount | money }}</span>
                    </button>
                  }
                  @if (hidden(date) > 0) {
                    <span class="px-1 text-xs text-ink-faint">+{{ hidden(date) }} altri</span>
                  }
                }
              </div>
            }
          }
        </div>
      </div>

      <div class="sm:hidden" [class.opacity-55]="loading()">
        @for (day of days(); track day.date) {
          <section class="mt-5 first:mt-0">
            <h3 class="type-eyebrow text-ink-faint">{{ longDate(day.date) }}</h3>
            <ul class="mt-2 flex flex-col gap-0.5">
              @for (charge of day.charges; track charge.subscriptionId) {
                <li>
                  <button
                    type="button"
                    class="-mx-3 flex w-[calc(100%+1.5rem)] cursor-pointer items-baseline gap-3 rounded-control px-3 py-2.5 text-left transition-colors hover:bg-surface-2"
                    (click)="edit.emit(charge.subscriptionId)"
                  >
                    <span class="min-w-0 flex-1 truncate text-sm text-ink">{{ charge.name }}</span>
                    <span class="tnum w-24 shrink-0 text-right text-sm text-ink">{{
                      charge.amount | money
                    }}</span>
                  </button>
                </li>
              }
            </ul>
          </section>
        } @empty {
          <app-empty-state [title]="'Nessun addebito a ' + monthLabel()" />
        }
      </div>

      <p class="tnum mt-4 flex items-baseline justify-between gap-4 text-sm">
        <span class="text-ink-muted first-letter:uppercase">Totale di {{ monthLabel() }}</span>
        <span class="font-medium text-ink">{{ current.total | money }}</span>
      </p>
    } @else {
      <app-skeleton [height]="420" />
    }
  `,
})
export class RenewalCalendar {
  readonly month = input.required<YearMonth>();
  readonly monthLabel = input.required<string>();
  readonly charges = input.required<MonthCharges | undefined>();
  readonly today = input.required<IsoDate>();
  readonly loading = input(false);

  readonly edit = output<SubscriptionId>();

  protected readonly weekdays = WEEKDAYS;

  protected readonly weeks = computed(() => calendarWeeks(this.month()));

  private readonly byDate = computed(() => {
    const byDate = new Map<IsoDate, UpcomingCharge[]>();
    for (const charge of this.charges()?.charges ?? []) {
      const list = byDate.get(charge.date) ?? [];
      list.push(charge);
      byDate.set(charge.date, list);
    }
    return byDate;
  });

  protected readonly days = computed<readonly DayCharges[]>(() =>
    [...this.byDate().entries()].map(([date, charges]) => ({ date, charges })),
  );

  protected visible(date: IsoDate): readonly UpcomingCharge[] {
    const charges = this.byDate().get(date) ?? [];
    // Se ne avanzerebbe uno solo, tanto vale mostrarlo al posto del «+1».
    return charges.length <= VISIBLE_PER_DAY + 1 ? charges : charges.slice(0, VISIBLE_PER_DAY);
  }

  protected hidden(date: IsoDate): number {
    return (this.byDate().get(date)?.length ?? 0) - this.visible(date).length;
  }

  protected dayNumber(date: IsoDate): number {
    return Number(date.slice(8));
  }

  protected longDate(date: IsoDate): string {
    return formatLongDate(date);
  }
}
