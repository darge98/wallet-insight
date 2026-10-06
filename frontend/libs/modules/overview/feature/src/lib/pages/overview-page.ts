import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BudgetMeter } from '@wallet/budgets-ui';
import { PeriodPreset, subtractMoney, sumMoney } from '@wallet/shared-domain';
import {
  Card,
  formatCount,
  formatMoney,
  formatMonth,
  formatPercent,
  Alert,
  Icon,
  PeriodFilterBar,
} from '@wallet/shared-ui';

import { OverviewFacade } from '../data-access/overview-facade';
import { CategoryStructureChart, CategoryStructureEntry } from '../ui/category-structure-chart';
import { ExpenseCurveChart } from '../ui/expense-curve-chart';
import { KpiBlock } from '../ui/kpi-block';
import { RecentRecordsList } from '../ui/recent-records-list';

/** Sotto questa soglia la variazione non merita di essere raccontata. */
const NEGLIGIBLE_DIFFERENCE_CENTS = 100;

@Component({
  selector: 'app-overview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [OverviewFacade],
  imports: [
    RouterLink,
    Card,
    Alert,
    Icon,
    PeriodFilterBar,
    KpiBlock,
    ExpenseCurveChart,
    CategoryStructureChart,
    RecentRecordsList,
    BudgetMeter,
  ],
  templateUrl: './overview-page.html',
  host: { class: 'block' },
})
export class OverviewPage {
  protected readonly facade = inject(OverviewFacade);

  protected readonly balanceDetail = computed(() => {
    const count = this.facade.accountCount();
    return count === 1 ? 'Su 1 conto collegato' : `Su ${formatCount(count)} conti collegati`;
  });

  protected readonly incomeDetail = computed(() =>
    this.differenceDetail(this.facade.summary.value()?.incomeTrend.difference.amount),
  );

  protected readonly expensesDetail = computed(() =>
    this.differenceDetail(this.facade.summary.value()?.expensesTrend.difference.amount),
  );

  protected readonly budgetMonthLabel = computed(() => formatMonth(this.facade.budgetMonth()));

  protected readonly marginDetail = computed(() => {
    const summary = this.facade.summary.value();
    return summary ? `${formatPercent(summary.savingsRate)} delle entrate` : '';
  });

  /**
   * Le categorie in classifica, più tutto il resto in una voce sola.
   *
   * Il resto non è un dettaglio da omettere: senza, la torta è una classifica
   * ma non una struttura, e sei voci che coprono il 60% della spesa sembrano
   * coprirla tutta. Si ricava per differenza dalle uscite del periodo — lo stesso
   * numero del KPI qui sopra — invece di chiedere al server tutte e novantacinque
   * le categorie per sommarne ottantanove.
   *
   * Finché le uscite del periodo non sono arrivate la voce non c'è: mostrarla a
   * zero direbbe che le categorie in classifica sono tutto, che è la cosa
   * sbagliata da far credere.
   */
  protected readonly expenseStructure = computed<readonly CategoryStructureEntry[]>(() => {
    const slices = this.facade.categoryExpenses.value();
    if (slices.length === 0) return [];

    const entries: CategoryStructureEntry[] = slices.map((slice) => ({
      key: slice.categoryId,
      label: slice.name,
      amount: slice.total,
      share: slice.share,
      transactionCount: slice.transactionCount,
      residual: false,
    }));

    const expenses = this.facade.summary.value()?.expenses;
    if (!expenses) return entries;

    const inClassifica = sumMoney(
      slices.map((slice) => slice.total),
      expenses.currency,
    );
    const resto = subtractMoney(expenses, inClassifica);
    if (resto.amount < NEGLIGIBLE_DIFFERENCE_CENTS) return entries;

    const quotaInClassifica = slices.reduce((sum, slice) => sum + slice.share, 0);
    return [
      ...entries,
      {
        key: 'resto',
        // Non "Altre categorie": la sorgente ha una categoria vera che si
        // chiama "Others", e le due righe si leggerebbero come un doppione.
        label: 'Tutto il resto',
        amount: resto,
        share: Math.max(0, 100 - quotaInClassifica),
        transactionCount: null,
        residual: true,
      },
    ];
  });

  protected onPeriodChange(preset: PeriodPreset): void {
    this.facade.selectPeriod(preset);
  }

  /** "180,00 € in più rispetto ad agosto", oppure nulla se il delta è irrisorio. */
  private differenceDetail(cents: number | undefined): string {
    if (cents === undefined) return '';

    const comparison = this.facade.comparisonLabel();
    if (Math.abs(cents) < NEGLIGIBLE_DIFFERENCE_CENTS) {
      return `In linea con il periodo precedente`;
    }

    const direction = cents > 0 ? 'in più' : 'in meno';
    const amount = formatMoney({ amount: Math.abs(cents), currency: 'EUR' });
    return `${amount} ${direction} rispetto ${comparison}`;
  }
}
