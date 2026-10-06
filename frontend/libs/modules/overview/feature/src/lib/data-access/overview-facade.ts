import { Injectable, computed, inject, resource, signal } from '@angular/core';

import { formatCompactDate } from '@wallet/shared-ui';

import { AccountsOverview } from '@wallet/accounts-data-access';
import { BUDGET_REPOSITORY } from '@wallet/budgets-domain';
import { UserFacade } from '@wallet/user-data-access';
import {
  ANALYTICS_REPOSITORY,
  browserTimeZone,
  CATEGORY_REPOSITORY,
  CUSTOM_PERIOD,
  DateRange,
  DEFAULT_RECORD_SORT,
  EMPTY_RECORD_FILTERS,
  monthOf,
  PERIOD_PRESET_LABELS,
  PeriodChoice,
  PeriodPreset,
  RECORD_REPOSITORY,
  resolvePeriod,
  SPENDING_BREAKDOWN_REPOSITORY,
  todayInTimeZone,
} from '@wallet/shared-domain';

import { budgetHighlights } from './budget-highlights';
import { comparisonLabel } from './comparison-label';

const RECENT_RECORDS_LIMIT = 6;

/**
 * Quante categorie entrano nella struttura delle spese.
 *
 * Sei più il resto: le categorie di un utente reale sono novantacinque, e
 * l'elenco intero non è una struttura, è un altro elenco di movimenti. Sei righe
 * stanno in una colonna senza scorrere e coprono la parte di spesa che si può
 * davvero decidere di cambiare.
 */
const CATEGORY_STRUCTURE_LIMIT = 6;

/** Due righe da due: il resto sta nella pagina Budget. */
const BUDGET_HIGHLIGHTS_LIMIT = 4;

/**
 * Facade della Panoramica.
 *
 * Unico punto di contatto fra la UI e le porte di dominio: i widget restano
 * componenti di presentazione, senza dipendenze dai repository.
 */
@Injectable()
export class OverviewFacade {
  private readonly analytics = inject(ANALYTICS_REPOSITORY);
  private readonly breakdowns = inject(SPENDING_BREAKDOWN_REPOSITORY);
  private readonly categoryRepository = inject(CATEGORY_REPOSITORY);
  private readonly recordRepository = inject(RECORD_REPOSITORY);
  private readonly budgetRepository = inject(BUDGET_REPOSITORY);
  private readonly accountsOverview = inject(AccountsOverview);
  private readonly user = inject(UserFacade);

  /**
   * Periodo iniziale: quello scelto dall'utente in onboarding, non un default
   * fisso. Una selezione successiva vale solo per la visita corrente.
   */
  private readonly periodState = signal<PeriodPreset>(
    this.user.profile()?.defaultDashboardPeriod ?? 'current-month',
  );

  /**
   * Le date scritte a mano, quando ci sono. Escludono il preset invece di
   * affiancarlo, perché la domanda è una — quale finestra guardo — e le
   * risposte si escludono. È la stessa regola dei Movimenti.
   */
  private readonly customRangeState = signal<DateRange | null>(null);

  /** Il periodo attivo: un preset, oppure le date scelte a mano. */
  readonly period = computed<PeriodChoice>(() =>
    this.customRangeState() === null ? this.periodState() : CUSTOM_PERIOD,
  );

  private readonly today = computed(() =>
    todayInTimeZone(this.user.profile()?.timeZone ?? browserTimeZone()),
  );

  readonly range = computed<DateRange>(
    () => this.customRangeState() ?? resolvePeriod(this.periodState(), this.today()),
  );

  /**
   * I budget sono mensili e non seguono il periodo scelto: su «ultimi 7 giorni»
   * o su date libere un limite mensile non ha un residuo da mostrare.
   */
  readonly budgetMonth = computed(() => monthOf(this.today()));

  /**
   * Come si chiama il periodo nei titoli dei pannelli e nel sottotitolo.
   *
   * Una finestra scritta a mano non ha un nome, quindi prende le sue due date:
   * `PERIOD_PRESET_LABELS` è indicizzato sui preset e non avrebbe una voce per
   * «custom», e scrivere lì «Date» direbbe come si è scelto invece di che cosa
   * si sta guardando.
   */
  readonly periodLabel = computed(() => {
    const custom = this.customRangeState();
    return custom === null
      ? PERIOD_PRESET_LABELS[this.periodState()]
      : `${formatCompactDate(custom.from)} – ${formatCompactDate(custom.to)}`;
  });

  /** Se la finestra è scritta a mano: il sottotitolo non può chiamarla per nome. */
  readonly customPeriod = computed(() => this.customRangeState() !== null);

  /** Come nominare il termine di paragone nei dettagli dei KPI: quello che ha usato il server. */
  readonly comparisonLabel = computed(() => {
    const previous = this.summary.value()?.previousPeriod;
    return previous ? comparisonLabel(previous) : 'al periodo precedente';
  });

  readonly accountCount = this.accountsOverview.count;

  readonly summary = resource({
    params: () => this.range(),
    loader: ({ params, abortSignal }) => this.analytics.kpiSummary(params, abortSignal),
  });

  readonly expenseCurve = resource({
    params: () => this.range(),
    loader: ({ params, abortSignal }) => this.analytics.cumulativeExpenses(params, abortSignal),
    defaultValue: [],
  });

  readonly categoryExpenses = resource({
    params: () => this.range(),
    loader: ({ params, abortSignal }) =>
      this.breakdowns.expensesByCategory({ range: params }, CATEGORY_STRUCTURE_LIMIT, abortSignal),
    defaultValue: [],
  });

  readonly categories = resource({
    loader: ({ abortSignal }) => this.categoryRepository.findAll(abortSignal),
    defaultValue: [],
  });

  readonly recentRecords = resource({
    params: () => this.range(),
    loader: ({ params, abortSignal }) =>
      this.recordRepository.search(
        {
          filters: { ...EMPTY_RECORD_FILTERS, range: params },
          sort: DEFAULT_RECORD_SORT,
          page: { index: 0, size: RECENT_RECORDS_LIMIT },
        },
        abortSignal,
      ),
  });

  readonly records = computed(() => this.recentRecords.value()?.page.items ?? []);

  readonly budgetReport = resource({
    params: () => this.budgetMonth(),
    loader: ({ params, abortSignal }) => this.budgetRepository.month(params, abortSignal),
  });

  readonly budgets = computed(() => {
    const report = this.budgetReport.value();
    return report ? budgetHighlights(report, BUDGET_HIGHLIGHTS_LIMIT) : [];
  });

  private readonly sources = [
    this.summary,
    this.expenseCurve,
    this.categoryExpenses,
    this.categories,
    this.recentRecords,
    this.budgetReport,
  ];

  readonly isLoading = computed(() => this.sources.some((source) => source.isLoading()));

  readonly hasError = computed(() => this.sources.some((source) => source.error() !== undefined));

  /** Scegliere un preset abbandona le date scritte a mano: comanda una sola cosa. */
  selectPeriod(preset: PeriodPreset): void {
    this.periodState.set(preset);
    this.customRangeState.set(null);
  }

  setCustomRange(range: DateRange): void {
    this.customRangeState.set(range);
  }

  /** Ricarica tutti i blocchi: è l'azione del pulsante "Aggiorna". */
  refresh(): void {
    for (const source of this.sources) {
      source.reload();
    }
    this.accountsOverview.reload();
  }
}
