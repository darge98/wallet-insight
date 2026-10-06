import { CategoryId, Identifier, Money } from '@wallet/shared-domain';

export type BudgetId = Identifier<'Budget'>;

export const asBudgetId = (raw: string): BudgetId => raw as BudgetId;

/**
 * Un limite mensile su una o più categorie, come lo si modifica.
 *
 * Senza `parentId` è un budget principale; con, è un sotto-budget che prende parte
 * delle categorie del principale e ha un tetto più stretto. `limit` è quello che
 * vale dal mese corrente: cambiarlo non riscrive i mesi passati.
 */
export interface Budget {
  readonly id: BudgetId;
  readonly parentId: BudgetId | null;
  readonly name: string;
  readonly categoryIds: readonly CategoryId[];
  readonly limit: Money;
}

export interface BudgetDraft {
  readonly parentId: BudgetId | null;
  readonly name: string;
  readonly categoryIds: readonly CategoryId[];
  readonly limit: Money;
}

/** Le modifiche di un budget: un campo assente resta com'è. */
export interface BudgetChanges {
  readonly name?: string;
  readonly categoryIds?: readonly CategoryId[];
  readonly limit?: Money;
}

export const isMainBudget = (budget: Budget): boolean => budget.parentId === null;

/**
 * Dove sta già una categoria, per chi sta scegliendo quelle di {@link Budget}.
 *
 * Fra principali una categoria è di uno solo; un sotto-budget sceglie solo fra
 * quelle del suo principale non prese dai fratelli. Restituisce, per ogni
 * categoria che non si può scegliere, il nome del budget che la tiene — o `null`
 * se è fuori dal principale.
 */
export function unavailableCategories(
  budgets: readonly Budget[],
  editing: { readonly id: BudgetId | null; readonly parentId: BudgetId | null },
): ReadonlyMap<CategoryId, string | null> {
  const taken = new Map<CategoryId, string | null>();
  const others = budgets.filter((budget) => budget.id !== editing.id);

  for (const other of others) {
    if (other.parentId === editing.parentId) {
      for (const category of other.categoryIds) {
        taken.set(category, other.name);
      }
    }
  }
  return taken;
}

/** Quanto limite del principale resta da promettere ai sotto-budget, escluso `editing`. */
export function roomForChild(
  budgets: readonly Budget[],
  parent: Budget,
  editing: BudgetId | null,
): Money {
  const promised = budgets
    .filter((budget) => budget.parentId === parent.id && budget.id !== editing)
    .reduce((sum, budget) => sum + budget.limit.amount, 0);
  return { amount: parent.limit.amount - promised, currency: parent.limit.currency };
}
