import { Injectable, computed, inject, linkedSignal, resource, signal } from '@angular/core';

import { Account } from '@wallet/accounts-domain';
import { AccountsOverview } from '@wallet/accounts-data-access';
import {
  AccountId,
  browserTimeZone,
  Category,
  CATEGORY_REPOSITORY,
  CategoryId,
  monthOf,
  shiftMonth,
  todayInTimeZone,
  YearMonth,
} from '@wallet/shared-domain';
import {
  Subscription,
  SubscriptionDraft,
  SubscriptionId,
  SubscriptionRejectedError,
  SUBSCRIPTION_REPOSITORY,
  UpcomingHorizon,
} from '@wallet/subscriptions-domain';

export type SubscriptionsView = 'list' | 'calendar';

/** Gli abbonamenti attivi di una categoria; `key` è vuota per quelli senza. */
export interface SubscriptionGroup {
  readonly key: string;
  readonly label: string;
  readonly subscriptions: readonly Subscription[];
}

@Injectable()
export class SubscriptionsFacade {
  private readonly repository = inject(SUBSCRIPTION_REPOSITORY);
  private readonly categoryRepository = inject(CATEGORY_REPOSITORY);
  private readonly accountsOverview = inject(AccountsOverview);

  private readonly viewState = signal<SubscriptionsView>('list');
  private readonly horizonState = signal<UpcomingHorizon>(30);

  readonly view = this.viewState.asReadonly();
  readonly horizon = this.horizonState.asReadonly();

  readonly overview = resource({
    params: () => this.horizon(),
    loader: ({ params, abortSignal }) => this.repository.overview(params, abortSignal),
  });

  readonly subscriptions = resource({
    loader: ({ abortSignal }) => this.repository.findAll(abortSignal),
    defaultValue: [] as readonly Subscription[],
  });

  readonly categories = resource({
    loader: ({ abortSignal }) => this.categoryRepository.findAll(abortSignal),
    defaultValue: [] as readonly Category[],
  });

  /**
   * L'«oggi» del server, che lo calcola nel fuso del profilo; finché non risponde,
   * quello del dispositivo basta a proporre una data e un mese.
   */
  readonly today = computed(
    () => this.overview.value()?.today ?? todayInTimeZone(browserTimeZone()),
  );

  readonly currentMonth = computed(() => monthOf(this.today()));

  private readonly monthState = linkedSignal<YearMonth>(() => this.currentMonth());

  readonly month = this.monthState.asReadonly();

  readonly isCurrentMonth = computed(() => this.month() === this.currentMonth());

  /** Si chiede solo quando il calendario è aperto. */
  readonly calendar = resource({
    params: () => (this.view() === 'calendar' ? this.month() : undefined),
    loader: ({ params, abortSignal }) => this.repository.calendar(params, abortSignal),
  });

  private readonly sources = [this.overview, this.subscriptions, this.categories, this.calendar];

  readonly hasError = computed(() => this.sources.some((source) => source.error() !== undefined));

  /** I conti fra cui scegliere: gli archiviati no, ma restano leggibili su chi li usa già. */
  readonly accounts = this.accountsOverview.visible;

  readonly accountById = computed(
    () =>
      new Map<AccountId, Account>(
        this.accountsOverview.accounts.value().map((account) => [account.id, account]),
      ),
  );

  readonly categoryById = computed(
    () => new Map<CategoryId, Category>(this.categories.value().map((item) => [item.id, item])),
  );

  /**
   * Gli attivi per categoria, in ordine di nome; dentro, dal prossimo addebito,
   * come li manda il server. Senza categoria stanno in fondo.
   */
  readonly activeGroups = computed<readonly SubscriptionGroup[]>(() => {
    const groups = new Map<string, { label: string; subscriptions: Subscription[] }>();
    for (const subscription of this.subscriptions.value()) {
      if (!subscription.active) continue;
      const category =
        subscription.categoryId === null
          ? undefined
          : this.categoryById().get(subscription.categoryId);
      const key = category?.id ?? '';
      const group = groups.get(key) ?? {
        label: category?.name ?? 'Senza categoria',
        subscriptions: [],
      };
      group.subscriptions.push(subscription);
      groups.set(key, group);
    }
    return [...groups.entries()]
      .map(([key, group]) => ({ key, ...group }))
      .sort((a, b) =>
        a.key === '' ? 1 : b.key === '' ? -1 : a.label.localeCompare(b.label, 'it'),
      );
  });

  readonly ended = computed(() => this.subscriptions.value().filter((item) => !item.active));

  private readonly editingState = signal<{ readonly subscription: Subscription | null } | null>(
    null,
  );
  private readonly savingState = signal(false);
  private readonly saveErrorState = signal<string | null>(null);

  readonly editing = this.editingState.asReadonly();
  readonly saving = this.savingState.asReadonly();
  readonly saveError = this.saveErrorState.asReadonly();

  setView(view: SubscriptionsView): void {
    this.viewState.set(view);
  }

  setHorizon(horizon: UpcomingHorizon): void {
    this.horizonState.set(horizon);
  }

  previousMonth(): void {
    this.monthState.update((month) => shiftMonth(month, -1));
  }

  nextMonth(): void {
    this.monthState.update((month) => shiftMonth(month, 1));
  }

  goToCurrentMonth(): void {
    this.monthState.set(this.currentMonth());
  }

  create(): void {
    this.open(null);
  }

  edit(id: SubscriptionId): void {
    const subscription = this.subscriptions.value().find((item) => item.id === id);
    if (subscription) this.open(subscription);
  }

  closeEditor(): void {
    this.editingState.set(null);
  }

  async save(draft: SubscriptionDraft): Promise<void> {
    const editing = this.editingState();
    if (!editing || this.savingState()) return;

    const current = editing.subscription;
    await this.write(() =>
      current === null ? this.repository.create(draft) : this.repository.update(current.id, draft),
    );
  }

  async remove(): Promise<void> {
    const subscription = this.editingState()?.subscription;
    if (!subscription || this.savingState()) return;

    await this.write(() => this.repository.remove(subscription.id));
  }

  refresh(): void {
    for (const source of this.sources) {
      source.reload();
    }
  }

  private open(subscription: Subscription | null): void {
    this.saveErrorState.set(null);
    this.editingState.set({ subscription });
  }

  /** Dopo ogni scrittura si rileggono elenco, resoconto e calendario: cambiano tutti. */
  private async write(operation: () => Promise<unknown>): Promise<void> {
    this.savingState.set(true);
    this.saveErrorState.set(null);
    try {
      await operation();
      this.editingState.set(null);
      this.overview.reload();
      this.subscriptions.reload();
      this.calendar.reload();
    } catch (failure) {
      this.saveErrorState.set(
        failure instanceof SubscriptionRejectedError
          ? failure.message
          : 'Non è stato possibile salvare. Riprova tra poco.',
      );
    } finally {
      this.savingState.set(false);
    }
  }
}
