import { Injectable, computed, inject, linkedSignal, resource, signal } from '@angular/core';

import {
  Budget,
  BudgetChanges,
  BudgetDraft,
  BudgetId,
  BudgetRejectedError,
  BUDGET_REPOSITORY,
} from '@wallet/budgets-domain';
import {
  browserTimeZone,
  Category,
  CATEGORY_REPOSITORY,
  monthOf,
  shiftMonth,
  todayInTimeZone,
  YearMonth,
} from '@wallet/shared-domain';
import { UserFacade } from '@wallet/user-data-access';

/** Che cosa si sta modificando: un budget esistente, o uno nuovo sotto `parent` se c'è. */
export interface BudgetEditing {
  readonly budget: Budget | null;
  readonly parent: Budget | null;
}

@Injectable()
export class BudgetsFacade {
  private readonly repository = inject(BUDGET_REPOSITORY);
  private readonly categoryRepository = inject(CATEGORY_REPOSITORY);
  private readonly user = inject(UserFacade);

  /** Sul fuso del profilo, come il backend: è da questo mese che vale un limite nuovo. */
  readonly today = computed(() =>
    todayInTimeZone(this.user.profile()?.timeZone ?? browserTimeZone()),
  );

  readonly currentMonth = computed(() => monthOf(this.today()));

  private readonly monthState = linkedSignal<YearMonth>(() => this.currentMonth());

  readonly month = this.monthState.asReadonly();

  readonly isCurrentMonth = computed(() => this.month() === this.currentMonth());

  readonly report = resource({
    params: () => this.month(),
    loader: ({ params, abortSignal }) => this.repository.month(params, abortSignal),
  });

  readonly budgets = resource({
    loader: ({ abortSignal }) => this.repository.findAll(abortSignal),
    defaultValue: [] as readonly Budget[],
  });

  readonly categories = resource({
    loader: ({ abortSignal }) => this.categoryRepository.findAll(abortSignal),
    defaultValue: [] as readonly Category[],
  });

  private readonly sources = [this.report, this.budgets, this.categories];

  readonly hasError = computed(() => this.sources.some((source) => source.error() !== undefined));

  readonly budgetById = computed(
    () => new Map(this.budgets.value().map((budget) => [budget.id, budget])),
  );

  private readonly editingState = signal<BudgetEditing | null>(null);
  private readonly savingState = signal(false);
  private readonly saveErrorState = signal<string | null>(null);

  readonly editing = this.editingState.asReadonly();
  readonly saving = this.savingState.asReadonly();
  readonly saveError = this.saveErrorState.asReadonly();

  previousMonth(): void {
    this.monthState.update((month) => shiftMonth(month, -1));
  }

  /** Oltre il mese corrente non c'è niente da guardare: la spesa non è ancora avvenuta. */
  nextMonth(): void {
    if (!this.isCurrentMonth()) {
      this.monthState.update((month) => shiftMonth(month, 1));
    }
  }

  goToCurrentMonth(): void {
    this.monthState.set(this.currentMonth());
  }

  create(parentId: BudgetId | null = null): void {
    const parent = parentId === null ? null : (this.budgetById().get(parentId) ?? null);
    this.open({ budget: null, parent });
  }

  edit(id: BudgetId): void {
    const budget = this.budgetById().get(id);
    if (!budget) return;
    const parent =
      budget.parentId === null ? null : (this.budgetById().get(budget.parentId) ?? null);
    this.open({ budget, parent });
  }

  closeEditor(): void {
    this.editingState.set(null);
  }

  async save(draft: BudgetDraft): Promise<void> {
    const editing = this.editingState();
    if (!editing || this.savingState()) return;

    await this.write(() =>
      editing.budget === null
        ? this.repository.create(draft)
        : this.repository.update(editing.budget.id, changesBetween(editing.budget, draft)),
    );
  }

  async remove(): Promise<void> {
    const budget = this.editingState()?.budget;
    if (!budget || this.savingState()) return;

    await this.write(() => this.repository.remove(budget.id));
  }

  refresh(): void {
    for (const source of this.sources) {
      source.reload();
    }
  }

  private open(editing: BudgetEditing): void {
    this.saveErrorState.set(null);
    this.editingState.set(editing);
  }

  /** Dopo ogni scrittura si rileggono definizioni e mese: un limite cambia i totali. */
  private async write(operation: () => Promise<unknown>): Promise<void> {
    this.savingState.set(true);
    this.saveErrorState.set(null);
    try {
      await operation();
      this.editingState.set(null);
      this.budgets.reload();
      this.report.reload();
    } catch (failure) {
      this.saveErrorState.set(
        failure instanceof BudgetRejectedError
          ? failure.message
          : 'Non è stato possibile salvare. Riprova tra poco.',
      );
    } finally {
      this.savingState.set(false);
    }
  }
}

function changesBetween(budget: Budget, draft: BudgetDraft): BudgetChanges {
  const sameCategories =
    budget.categoryIds.length === draft.categoryIds.length &&
    draft.categoryIds.every((id) => budget.categoryIds.includes(id));

  return {
    ...(draft.name !== budget.name ? { name: draft.name } : {}),
    ...(sameCategories ? {} : { categoryIds: draft.categoryIds }),
    ...(draft.limit.amount !== budget.limit.amount ? { limit: draft.limit } : {}),
  };
}
