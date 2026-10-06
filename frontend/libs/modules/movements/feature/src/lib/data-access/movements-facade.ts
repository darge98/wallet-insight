import { Injectable, computed, inject, linkedSignal, resource, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

import { AccountsOverview } from '@wallet/accounts-data-access';
import {
  SubscriptionDraft,
  subscriptionDraftFrom,
  SubscriptionRejectedError,
  SUBSCRIPTION_REPOSITORY,
} from '@wallet/subscriptions-domain';
import { UserFacade } from '@wallet/user-data-access';
import {
  AccountId,
  asAccountId,
  browserTimeZone,
  Category,
  CATEGORY_REPOSITORY,
  CategoryId,
  CUSTOM_PERIOD,
  DateRange,
  DEFAULT_PAGE_SIZE,
  DEFAULT_RECORD_SORT,
  EMPTY_RECORD_FILTERS,
  FinanceRecord,
  PeriodChoice,
  PeriodPreset,
  RECORD_REPOSITORY,
  RecordEdit,
  RecordFilters,
  RecordId,
  RecordQuery,
  resolvePeriod,
  SPENDING_BREAKDOWN_REPOSITORY,
  SpendingScope,
  todayInTimeZone,
} from '@wallet/shared-domain';

const BREAKDOWN_LIMIT = 5;

/** Il parametro dell'URL con cui la sidebar apre i movimenti di un conto. */
const ACCOUNT_QUERY_PARAM = 'conto';

/** Un giorno di movimenti, per la lista raggruppata. */
export interface RecordGroup {
  readonly date: string;
  readonly records: readonly FinanceRecord[];
}

/**
 * Facade della sezione Movimenti.
 *
 * Tutto lo stato della ricerca — filtri, ordinamento, pagina — vive qui in forma
 * di signal, e la query verso il repository è un valore derivato. Nessun filtro
 * e nessun ordinamento vengono applicati in memoria: li fa il database, e ciò
 * che arriva è già la pagina da mostrare. Su 1678 movimenti scaricare tutto per
 * filtrarlo nel browser sarebbe già una cattiva idea, e lo storico cresce a ogni
 * import.
 *
 * I totali arrivano insieme alle righe, dalla stessa risposta: sono la stessa
 * domanda fatta due volte, e chiederli separatamente vorrebbe dire un riepilogo
 * che può non corrispondere all'elenco sotto di sé.
 */
@Injectable()
export class MovementsFacade {
  private readonly recordRepository = inject(RECORD_REPOSITORY);
  private readonly categoryRepository = inject(CATEGORY_REPOSITORY);
  private readonly breakdowns = inject(SPENDING_BREAKDOWN_REPOSITORY);
  private readonly accountsOverview = inject(AccountsOverview);
  private readonly user = inject(UserFacade);
  private readonly subscriptionRepository = inject(SUBSCRIPTION_REPOSITORY);

  /**
   * Il conto a cui l'elenco è ristretto, o `null` per tutti.
   *
   * Sta nell'URL e non in un signal del facade: ci si arriva da un link della
   * sidebar, e un link deve poter essere riaperto, condiviso e tornato indietro
   * col pulsante del browser. Passare da un conto all'altro resta sulla stessa
   * rotta, quindi il periodo scelto non si perde.
   */
  readonly accountId = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(
      map((params) => {
        const raw = params.get(ACCOUNT_QUERY_PARAM);
        return raw ? asAccountId(raw) : null;
      }),
    ),
    { initialValue: null },
  );

  /**
   * Il mese in corso, e non il periodo predefinito del profilo: quella
   * preferenza dice come aprire la Panoramica. Qui si arriva per cercare, e la
   * finestra di partenza è sempre la stessa — il mese è il passo con cui si
   * ragiona sui soldi, ed è anche il periodo con più righe fra quelli brevi.
   */
  private readonly presetState = signal<PeriodPreset>('current-month');

  /**
   * Le due date scelte a mano, quando ci sono: `null` vuol dire «comanda il
   * preset». Sono uno stato solo con il preset e non due filtri che si sommano,
   * perché la domanda è una — quale finestra guardo — e le risposte si escludono.
   */
  private readonly customRangeState = signal<DateRange | null>(null);

  private readonly selectedIdState = signal<RecordId | null>(null);

  /**
   * Il movimento come il server l'ha restituito dopo una correzione.
   *
   * Vive accanto alla pagina invece di sostituirla perché la pagina si ricarica
   * subito dopo: fino a quando la nuova non arriva, il pannello deve già
   * mostrare ciò che è stato salvato, altrimenti sembra che il salvataggio non
   * abbia fatto niente.
   */
  private readonly patchedState = signal<FinanceRecord | null>(null);

  private readonly savingState = signal(false);
  private readonly saveFailureState = signal<unknown>(null);

  /**
   * L'ordinamento non ha più un comando: le righe scendono dalla più recente,
   * che è l'ordine con cui si legge un estratto conto. Il criterio resta però
   * quello del dominio e viaggia nella query, quindi rimetterci un controllo
   * sopra è una riga in questo file.
   */
  private readonly sort = DEFAULT_RECORD_SORT;

  /** Il periodo attivo: un preset, oppure le date scelte a mano. */
  readonly period = computed<PeriodChoice>(() =>
    this.customRangeState() === null ? this.presetState() : CUSTOM_PERIOD,
  );

  readonly accounts = this.accountsOverview.visible;

  /**
   * La finestra effettiva: le date scelte a mano se ci sono, altrimenti quelle
   * che il preset ricava da oggi.
   *
   * Il preset si risolve sul fuso del profilo e non su quello del dispositivo:
   * è la stessa regola della Panoramica, e senza di essa "Oggi" potrebbe
   * indicare due giorni diversi nelle due schermate della stessa app.
   */
  readonly range = computed<DateRange>(
    () =>
      this.customRangeState() ??
      resolvePeriod(
        this.presetState(),
        todayInTimeZone(this.user.profile()?.timeZone ?? browserTimeZone()),
      ),
  );

  /** Il conto scelto dalla sidebar, se lo si conosce: un id rimasto in un link vecchio no. */
  readonly account = computed(() => {
    const id = this.accountId();
    return id === null ? null : (this.accountById().get(id) ?? null);
  });

  private readonly accountIds = computed<readonly AccountId[]>(() => {
    const id = this.accountId();
    return id === null ? [] : [id];
  });

  /**
   * Il periodo è l'unico criterio che questa schermata sa scegliere; il conto
   * arriva dall'URL, e gli altri restano nel dominio e nella querystring, vuoti.
   * Tenerli nella forma invece di toglierli significa che rimetterli in mano
   * all'utente non richiede di rifare il giro fino al database.
   */
  readonly filters = computed<RecordFilters>(() => ({
    ...EMPTY_RECORD_FILTERS,
    accountIds: this.accountIds(),
    range: this.range(),
  }));

  /** Le classifiche guardano le stesse righe dell'elenco: stesso periodo, stesso conto. */
  private readonly spendingScope = computed<SpendingScope>(() => ({
    range: this.range(),
    accountIds: this.accountIds(),
  }));

  /** Ogni cambio di periodo riporta alla prima pagina. */
  private readonly pageIndexState = linkedSignal<RecordFilters, number>({
    source: () => this.filters(),
    computation: () => 0,
  });

  readonly pageIndex = this.pageIndexState.asReadonly();
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  private readonly query = computed<RecordQuery>(() => ({
    filters: this.filters(),
    sort: this.sort,
    page: { index: this.pageIndexState(), size: this.pageSize() },
  }));

  readonly result = resource({
    params: () => this.query(),
    loader: ({ params, abortSignal }) => this.recordRepository.search(params, abortSignal),
  });

  readonly categories = resource({
    loader: ({ abortSignal }) => this.categoryRepository.findAll(abortSignal),
    defaultValue: [] as readonly Category[],
  });

  readonly categoryBreakdown = resource({
    params: () => this.spendingScope(),
    loader: ({ params, abortSignal }) =>
      this.breakdowns.expensesByCategory(params, BREAKDOWN_LIMIT, abortSignal),
    defaultValue: [],
  });

  readonly spendingTargets = resource({
    params: () => this.spendingScope(),
    loader: ({ params, abortSignal }) =>
      this.breakdowns.topSpendingTargets(params, BREAKDOWN_LIMIT, abortSignal),
    defaultValue: [],
  });

  private readonly sources = [
    this.result,
    this.categories,
    this.categoryBreakdown,
    this.spendingTargets,
  ];

  readonly isLoading = computed(() => this.result.isLoading());

  readonly hasError = computed(() => this.sources.some((source) => source.error() !== undefined));

  readonly records = computed(() => this.result.value()?.page.items ?? []);

  readonly totals = computed(() => this.result.value()?.totals ?? null);

  readonly pageCount = computed(() => this.result.value()?.page.pageCount ?? 0);

  readonly totalCount = computed(() => this.result.value()?.page.total ?? 0);

  /** Movimenti della pagina corrente raggruppati per giorno, ordine preservato. */
  readonly groups = computed<readonly RecordGroup[]>(() => {
    const groups = new Map<string, FinanceRecord[]>();

    for (const record of this.records()) {
      const bucket = groups.get(record.date);
      if (bucket) {
        bucket.push(record);
      } else {
        groups.set(record.date, [record]);
      }
    }

    return [...groups.entries()].map(([date, records]) => ({ date, records }));
  });

  readonly saving = this.savingState.asReadonly();
  readonly saveFailure = this.saveFailureState.asReadonly();

  readonly selectedRecord = computed<FinanceRecord | null>(() => {
    const id = this.selectedIdState();
    if (id === null) {
      return null;
    }

    const patched = this.patchedState();
    if (patched?.id === id) {
      return patched;
    }
    return this.records().find((record) => record.id === id) ?? null;
  });

  readonly accountById = computed(
    () => new Map(this.accounts().map((account) => [account.id, account])),
  );

  readonly categoryById = computed<ReadonlyMap<CategoryId, Category>>(
    () => new Map(this.categories.value().map((category) => [category.id, category])),
  );

  /** Scegliere un preset abbandona le date scritte a mano: comanda una sola cosa. */
  selectPeriod(preset: PeriodPreset): void {
    this.presetState.set(preset);
    this.customRangeState.set(null);
  }

  setCustomRange(range: DateRange): void {
    this.customRangeState.set(range);
  }

  goToPage(index: number): void {
    this.pageIndexState.set(Math.max(0, Math.min(index, this.pageCount() - 1)));
  }

  /** La bozza dell'abbonamento che si sta registrando dal movimento aperto. */
  private readonly subscriptionDraftState = signal<SubscriptionDraft | null>(null);
  private readonly subscriptionSavingState = signal(false);
  private readonly subscriptionErrorState = signal<string | null>(null);
  /** I movimenti da cui in questa visita è nato un abbonamento: il pannello lo dice. */
  private readonly subscribedState = signal<ReadonlySet<RecordId>>(new Set());

  readonly subscriptionDraft = this.subscriptionDraftState.asReadonly();
  readonly subscriptionSaving = this.subscriptionSavingState.asReadonly();
  readonly subscriptionError = this.subscriptionErrorState.asReadonly();

  readonly selectedSubscribed = computed(() => {
    const record = this.selectedRecord();
    return record !== null && this.subscribedState().has(record.id);
  });

  /** Apre il pannello dell'abbonamento compilato col movimento aperto. */
  startSubscription(): void {
    const record = this.selectedRecord();
    if (!record) return;
    this.subscriptionErrorState.set(null);
    this.subscriptionDraftState.set(subscriptionDraftFrom(record));
  }

  closeSubscription(): void {
    this.subscriptionDraftState.set(null);
  }

  async saveSubscription(draft: SubscriptionDraft): Promise<void> {
    const record = this.selectedRecord();
    if (!record || this.subscriptionSavingState()) return;

    this.subscriptionSavingState.set(true);
    this.subscriptionErrorState.set(null);
    try {
      await this.subscriptionRepository.create(draft);
      this.subscriptionDraftState.set(null);
      this.subscribedState.update((ids) => new Set([...ids, record.id]));
    } catch (error) {
      this.subscriptionErrorState.set(
        error instanceof SubscriptionRejectedError
          ? error.message
          : 'Non è stato possibile salvare. Riprova tra poco.',
      );
    } finally {
      this.subscriptionSavingState.set(false);
    }
  }

  select(id: RecordId | null): void {
    this.subscriptionDraftState.set(null);
    this.selectedIdState.set(id);
    this.patchedState.set(null);
    this.saveFailureState.set(null);
  }

  /**
   * Salva le correzioni sul movimento aperto.
   *
   * Dopo il salvataggio si ricaricano elenco e classifiche: spostare un
   * movimento di categoria cambia «uscite per categoria», e rinominare la
   * controparte cambia «dove spendi di più» — lasciarle ferme mostrerebbe la
   * riga corretta accanto a riepiloghi che parlano di com'era prima.
   */
  async save(edit: RecordEdit): Promise<void> {
    const record = this.selectedRecord();
    if (!record || this.savingState()) {
      return;
    }

    this.savingState.set(true);
    this.saveFailureState.set(null);
    try {
      this.patchedState.set(await this.recordRepository.update(record.id, edit));
      this.result.reload();
      this.categoryBreakdown.reload();
      this.spendingTargets.reload();
    } catch (error) {
      this.saveFailureState.set(error);
    } finally {
      this.savingState.set(false);
    }
  }

  refresh(): void {
    for (const source of this.sources) {
      source.reload();
    }
    this.accountsOverview.reload();
  }
}
