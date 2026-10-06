import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { budgetHealth, budgetUsage, BudgetStatus } from '@wallet/budgets-domain';
import { absMoney } from '@wallet/shared-domain';
import { formatMoney, Icon, MoneyPipe, ProgressBar, ProgressTone } from '@wallet/shared-ui';

const TONES: Readonly<Record<ReturnType<typeof budgetHealth>, ProgressTone>> = {
  'on-track': 'accent',
  'at-risk': 'warning',
  exceeded: 'negative',
};

/**
 * Speso su limite di un budget, con la barra e il residuo.
 *
 * Lo stato si legge anche senza colori: lo sforamento ha l'icona e la parola,
 * il «quasi al limite» la sua etichetta.
 */
@Component({
  selector: 'app-budget-meter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, MoneyPipe, ProgressBar],
  host: { class: 'block' },
  template: `
    <div class="flex items-baseline justify-between gap-4">
      <p
        class="min-w-0 truncate text-ink"
        [class]="compact() ? 'text-sm' : 'text-[0.9375rem] font-medium'"
      >
        {{ status().name }}
      </p>
      <p class="tnum shrink-0 text-sm whitespace-nowrap">
        <span class="text-ink">{{ status().spent | money }}</span>
        <span class="text-ink-muted"> di {{ status().limit | money }}</span>
      </p>
    </div>

    <app-progress-bar
      class="mt-2"
      [value]="usage()"
      [tone]="tone()"
      [label]="'Limite di ' + status().name + ' consumato'"
    />

    <p
      class="tnum mt-1.5 flex items-center gap-1 text-xs"
      [class]="health() === 'exceeded' ? 'text-negative' : 'text-ink-muted'"
    >
      @if (health() === 'exceeded') {
        <app-icon name="alert" [size]="13" />
      }
      {{ caption() }}
    </p>
  `,
})
export class BudgetMeter {
  readonly status = input.required<Pick<BudgetStatus, 'name' | 'limit' | 'spent' | 'remaining'>>();
  readonly compact = input(false);

  protected readonly usage = computed(() => budgetUsage(this.status()));
  protected readonly health = computed(() => budgetHealth(this.status()));
  protected readonly tone = computed(() => TONES[this.health()]);

  protected readonly caption = computed(() => {
    const remaining = this.status().remaining;
    switch (this.health()) {
      case 'exceeded':
        return `Sforato di ${formatMoney(absMoney(remaining))}`;
      case 'at-risk':
        return `Restano ${formatMoney(remaining)} · quasi al limite`;
      case 'on-track':
        return `Restano ${formatMoney(remaining)}`;
    }
  });
}
