import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormField, FormRoot, form, schema, validate } from '@angular/forms/signals';

import { Budget, BudgetDraft, roomForChild, unavailableCategories } from '@wallet/budgets-domain';
import {
  asCategoryId,
  Category,
  CategoryId,
  categoryTree,
  money,
  YearMonth,
} from '@wallet/shared-domain';
import { amountInput, formatMoney, formatMonth, Icon, parseAmount } from '@wallet/shared-ui';

interface BudgetFormModel {
  name: string;
  limit: string;
  categoryIds: string[];
}

interface CategoryOption {
  readonly id: CategoryId;
  readonly name: string;
  /** Il budget che la tiene già, se non si può scegliere. */
  readonly takenBy: string | null;
}

interface CategoryGroup {
  readonly label: string;
  readonly options: readonly CategoryOption[];
  readonly selected: number;
  /** Quelle che non sono già di un altro budget: è su queste che agisce la macro. */
  readonly available: number;
}

/** Come `Budget.MAX_NAME_LENGTH` nel backend. */
const NAME_MAX_LENGTH = 80;

const budgetSchema = schema<BudgetFormModel>((path) => {
  validate(path.name, ({ value }) => {
    const name = value().trim();
    if (name === '') return { kind: 'required' };
    return name.length > NAME_MAX_LENGTH ? { kind: 'too-long' } : undefined;
  });
  validate(path.limit, ({ value }) =>
    parseAmount(value()) === null ? { kind: 'amount' } : undefined,
  );
  validate(path.categoryIds, ({ value }) =>
    value().length === 0 ? { kind: 'required' } : undefined,
  );
});

/**
 * Il pannello con cui si crea o si modifica un budget, o un sotto-budget.
 *
 * Le categorie che le regole non permettono restano visibili ma spente, con il
 * nome del budget che le tiene: sparire senza spiegazione farebbe cercare una
 * categoria che c'è.
 */
@Component({
  selector: 'app-budget-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, FormRoot, Icon],
  host: { class: 'contents' },
  templateUrl: './budget-editor.html',
})
export class BudgetEditor {
  readonly open = input(false);
  /** `null` vuol dire «nuovo». */
  readonly budget = input<Budget | null>(null);
  readonly parent = input<Budget | null>(null);
  readonly budgets = input<readonly Budget[]>([]);
  readonly categories = input<readonly Category[]>([]);
  readonly currentMonth = input.required<YearMonth>();
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly closed = output<void>();
  readonly saved = output<BudgetDraft>();
  readonly removed = output<void>();

  protected readonly model = signal<BudgetFormModel>({ name: '', limit: '', categoryIds: [] });

  protected readonly budgetForm = form(this.model, budgetSchema, {
    submission: {
      action: async () => {
        if (this.overRoom()) return;
        const { name, limit, categoryIds } = this.model();
        this.saved.emit({
          parentId: this.parent()?.id ?? null,
          name: name.trim(),
          categoryIds: categoryIds.map(asCategoryId),
          limit: money(parseAmount(limit) ?? 0),
        });
      },
    },
  });

  protected readonly attempted = signal(false);
  protected readonly confirmingDelete = signal(false);

  /**
   * I gruppi aperti. Si decidono all'apertura del pannello e poi solo a mano:
   * se seguissero la selezione, scegliere l'ultima categoria di un gruppo lo
   * chiuderebbe sotto il puntatore.
   */
  private readonly expanded = signal<ReadonlySet<string>>(new Set());

  constructor() {
    effect(() => {
      // Anche `open`: riaprire «nuovo» due volte di fila deve ripartire da un modulo vuoto.
      this.open();
      const budget = this.budget();
      this.model.set({
        name: budget?.name ?? '',
        limit: budget ? amountInput(budget.limit.amount) : '',
        categoryIds: budget ? [...budget.categoryIds] : [],
      });
      this.budgetForm().reset();
      this.attempted.set(false);
      this.confirmingDelete.set(false);
      this.expanded.set(
        new Set(
          untracked(() => this.groups())
            .filter(
              (group) =>
                this.parent() !== null || (group.selected > 0 && group.selected < group.available),
            )
            .map((group) => group.label),
        ),
      );
    });
  }

  protected readonly title = computed(() => {
    const budget = this.budget();
    const parent = this.parent();
    if (budget) return `Modifica «${budget.name}»`;
    return parent ? `Nuovo sotto-budget di «${parent.name}»` : 'Nuovo budget';
  });

  protected readonly monthLabel = computed(() => formatMonth(this.currentMonth()));

  private readonly selected = computed(() => new Set(this.model().categoryIds));

  /** Quanto limite del principale resta ai sotto-budget; `null` per un principale. */
  protected readonly room = computed(() => {
    const parent = this.parent();
    return parent ? roomForChild(this.budgets(), parent, this.budget()?.id ?? null) : null;
  });

  protected readonly overRoom = computed(() => {
    const room = this.room();
    const cents = parseAmount(this.model().limit);
    return room !== null && cents !== null && cents > room.amount;
  });

  protected readonly roomLabel = computed(() => {
    const room = this.room();
    return room ? formatMoney(room) : '';
  });

  /** Un principale con sotto-budget non può scendere sotto la loro somma. */
  protected readonly childrenCount = computed(() => {
    const budget = this.budget();
    return budget ? this.budgets().filter((other) => other.parentId === budget.id).length : 0;
  });

  protected readonly groups = computed<readonly CategoryGroup[]>(() => {
    const parent = this.parent();
    const taken = unavailableCategories(this.budgets(), {
      id: this.budget()?.id ?? null,
      parentId: parent?.id ?? null,
    });
    const selected = this.selected();
    const byGroup = new Map<string, CategoryOption[]>();
    for (const branch of categoryTree(this.categories())) {
      const children = parent
        ? branch.children.filter((category) => parent.categoryIds.includes(category.id))
        : branch.children;
      if (children.length === 0) continue;
      const label = parent ? parent.name : branch.macro.name;
      const options = byGroup.get(label) ?? [];
      for (const category of children) {
        options.push({
          id: category.id,
          name: category.name,
          takenBy: taken.get(category.id) ?? null,
        });
      }
      byGroup.set(label, options);
    }

    return [...byGroup.entries()]
      .map(([label, options]) => ({
        label,
        options: options.sort((a, b) => a.name.localeCompare(b.name, 'it')),
        selected: options.filter((option) => selected.has(option.id)).length,
        available: options.filter((option) => option.takenBy === null).length,
      }))
      .sort((a, b) => a.label.localeCompare(b.label, 'it'));
  });

  protected readonly selectedCount = computed(() => this.model().categoryIds.length);

  protected isSelected(id: CategoryId): boolean {
    return this.selected().has(id);
  }

  protected toggle(id: CategoryId): void {
    this.model.update((model) => ({
      ...model,
      categoryIds: model.categoryIds.includes(id)
        ? model.categoryIds.filter((other) => other !== id)
        : [...model.categoryIds, id],
    }));
  }

  protected isWholeGroup(group: CategoryGroup): boolean {
    return group.available > 0 && group.selected === group.available;
  }

  protected isPartialGroup(group: CategoryGroup): boolean {
    return group.selected > 0 && group.selected < group.available;
  }

  /**
   * La macro: sceglie tutte le categorie libere del gruppo, o le toglie tutte se
   * erano già tutte scelte. Quelle di un altro budget restano dove sono.
   */
  protected toggleGroup(group: CategoryGroup): void {
    const ids = group.options
      .filter((option) => option.takenBy === null)
      .map((option) => option.id as string);
    const whole = this.isWholeGroup(group);
    this.model.update((model) => ({
      ...model,
      categoryIds: whole
        ? model.categoryIds.filter((id) => !ids.includes(id))
        : [...new Set([...model.categoryIds, ...ids])],
    }));
  }

  protected isExpanded(label: string): boolean {
    return this.expanded().has(label);
  }

  protected toggleExpanded(label: string): void {
    this.expanded.update((labels) => {
      const next = new Set(labels);
      if (!next.delete(label)) next.add(label);
      return next;
    });
  }

  protected showError(field: 'name' | 'limit' | 'categoryIds'): boolean {
    const state = this.budgetForm[field]();
    return state.invalid() && (state.touched() || this.attempted());
  }
}
