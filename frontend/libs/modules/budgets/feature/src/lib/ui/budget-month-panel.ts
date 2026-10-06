import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { budgetHealth, budgetUsage, BudgetMonth } from '@wallet/budgets-domain';
import { absMoney, isNegativeMoney } from '@wallet/shared-domain';
import { MoneyPipe, ProgressBar, ProgressTone, Skeleton, SoftPanel } from '@wallet/shared-ui';

/**
 * Il mese rispetto ai budget: l'unico blocco pieno della pagina.
 *
 * Il fuori budget sta qui accanto al totale perché è ciò che rende il totale
 * onesto: senza, un mese «nei limiti» potrebbe nascondere spese mai pianificate.
 */
@Component({
  selector: 'app-budget-month-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, ProgressBar, Skeleton, SoftPanel],
  host: { class: 'block' },
  template: `
    <app-soft-panel [heading]="heading()">
      @if (report(); as month) {
        @if (month.budgets.length > 0) {
          <p class="tnum type-figure mt-3 text-ink">{{ headline() | money }}</p>
          <p class="mt-2 text-sm text-ink-on-soft">
            {{ over() ? 'oltre i budget del mese.' : 'ancora da spendere nei budget.' }}
          </p>

          <app-progress-bar
            class="mt-6"
            surface="soft"
            [value]="usage()"
            [tone]="tone()"
            label="Budget del mese consumato"
          />
          <p class="tnum mt-2.5 text-xs text-ink-on-soft">
            {{ month.spent | money }} spesi su {{ month.limit | money }}
          </p>
        }

        <dl class="mt-6 flex flex-col gap-2 text-sm">
          <div class="flex items-baseline justify-between gap-4">
            <dt class="text-ink-on-soft">Fuori budget</dt>
            <dd class="tnum text-ink">{{ month.unbudgeted | money }}</dd>
          </div>
          <div class="flex items-baseline justify-between gap-4">
            <dt class="text-ink-on-soft">Uscite del mese</dt>
            <dd class="tnum text-ink">{{ month.expenses | money }}</dd>
          </div>
        </dl>

        @if (daysLeft(); as days) {
          <p class="mt-5 text-xs text-ink-on-soft">
            {{
              days === 1
                ? 'Oggi è l’ultimo giorno del mese.'
                : 'Mancano ' + days + ' giorni alla fine del mese.'
            }}
          </p>
        }
      } @else {
        <app-skeleton class="mt-4" [height]="40" width="64%" />
        <app-skeleton class="mt-4" [height]="12" width="80%" />
        <app-skeleton class="mt-5" [height]="6" />
      }
    </app-soft-panel>
  `,
})
export class BudgetMonthPanel {
  readonly report = input.required<BudgetMonth | undefined>();
  readonly heading = input('Il mese');
  /** Solo per il mese corrente: per uno passato non manca più niente. */
  readonly daysLeft = input<number | null>(null);

  protected readonly over = computed(() => {
    const report = this.report();
    return report !== undefined && isNegativeMoney(report.remaining);
  });

  protected readonly headline = computed(() => {
    const report = this.report();
    return report ? absMoney(report.remaining) : null;
  });

  protected readonly usage = computed(() => {
    const report = this.report();
    return report ? budgetUsage(report) : 0;
  });

  protected readonly tone = computed<ProgressTone>(() => {
    const report = this.report();
    if (!report) return 'accent';
    switch (budgetHealth(report)) {
      case 'exceeded':
        return 'negative';
      case 'at-risk':
        return 'warning';
      case 'on-track':
        return 'accent';
    }
  });
}
