import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { cadenceLabel, Subscription } from '@wallet/subscriptions-domain';
import { IsoDate } from '@wallet/shared-domain';
import { formatCompactDate, formatLongDate, MoneyPipe } from '@wallet/shared-ui';

import { formatDaysUntil } from './subscription-format';

/**
 * Un abbonamento, con la stessa grammatica della riga di un movimento: titolo e
 * dettaglio a sinistra, importo incolonnato a destra, la barra del conto quando
 * c'è. Nessun divisore: bastano le colonne e lo spazio.
 *
 * Il costo al mese è un di più — solo quando la cadenza non lo è già, e non su
 * telefono.
 */
@Component({
  selector: 'app-subscription-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe],
  host: { class: 'block' },
  template: `
    <div class="flex items-baseline gap-3 py-2.5 sm:gap-4">
      @if (accountName(); as name) {
        <span
          class="h-3.5 w-0.5 shrink-0 self-center rounded-full"
          [style.background-color]="accountColor() ?? 'var(--color-line)'"
          [title]="'Conto: ' + name"
          aria-hidden="true"
        ></span>
        <span class="sr-only">Conto: {{ name }}</span>
      }

      <div class="flex min-w-0 flex-1 flex-col gap-0.5">
        <span
          class="truncate text-sm"
          [class]="subscription().active ? 'text-ink' : 'text-ink-muted'"
        >
          {{ subscription().name }}
        </span>
        <span class="truncate text-xs text-ink-muted">{{ detail() }}</span>
      </div>

      <span class="w-24 shrink-0 text-right sm:w-28">
        <span
          class="tnum block text-sm"
          [class]="subscription().active ? 'text-ink' : 'text-ink-muted'"
          >{{ subscription().amount | money }}</span
        >
        @if (showMonthly()) {
          <span class="tnum hidden text-xs text-ink-faint sm:block"
            >{{ subscription().monthlyCost | money }} al mese</span
          >
        }
      </span>
    </div>
  `,
})
export class SubscriptionRow {
  readonly subscription = input.required<Subscription>();
  readonly today = input.required<IsoDate>();
  readonly accountName = input<string | null>(null);
  readonly accountColor = input<string | null>(null);

  protected readonly showMonthly = computed(() => {
    const { cadence, active } = this.subscription();
    return active && !(cadence.unit === 'month' && cadence.every === 1);
  });

  protected readonly detail = computed(() => {
    const subscription = this.subscription();
    const cadence = cadenceLabel(subscription.cadence);
    if (!subscription.active) {
      return subscription.endDate
        ? `${cadence} · finito il ${formatLongDate(subscription.endDate).toLowerCase()}`
        : cadence;
    }
    const next = subscription.nextChargeDate;
    if (next === null) return `${cadence} · nessun altro addebito`;
    return `${cadence} · prossimo ${formatCompactDate(next)}, ${formatDaysUntil(this.today(), next)}`;
  });
}
