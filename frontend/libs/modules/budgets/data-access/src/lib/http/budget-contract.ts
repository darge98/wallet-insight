import {
  asBudgetId,
  Budget,
  BudgetMonth,
  BudgetStatus,
  CategorySpent,
} from '@wallet/budgets-domain';
import { contractError } from '@wallet/shared-data-access';
import { asCategoryId, CurrencyCode, isCurrencyCode, money } from '@wallet/shared-domain';

export interface BudgetResponse {
  readonly id: string;
  readonly parentId?: string;
  readonly name: string;
  readonly categoryIds: readonly string[];
  readonly limitCents: number;
  readonly currencyCode: string;
}

interface CategorySpentResponse {
  readonly categoryId: string;
  readonly name: string;
  readonly color?: string;
  readonly spentCents: number;
}

interface BudgetStatusResponse {
  readonly id: string;
  readonly name: string;
  readonly limitCents: number;
  readonly spentCents: number;
  readonly remainingCents: number;
  readonly categories: readonly CategorySpentResponse[];
  readonly children: readonly BudgetStatusResponse[];
}

export interface BudgetMonthResponse {
  readonly month: string;
  readonly currencyCode: string;
  readonly limitCents: number;
  readonly spentCents: number;
  readonly remainingCents: number;
  readonly unbudgetedCents: number;
  readonly expensesCents: number;
  readonly budgets: readonly BudgetStatusResponse[];
}

function currencyOf(raw: string): CurrencyCode {
  if (!isCurrencyCode(raw)) {
    throw contractError('currencyCode', raw);
  }
  return raw;
}

export function toBudget(raw: BudgetResponse): Budget {
  return {
    id: asBudgetId(raw.id),
    parentId: raw.parentId ? asBudgetId(raw.parentId) : null,
    name: raw.name,
    categoryIds: raw.categoryIds.map(asCategoryId),
    limit: money(raw.limitCents, currencyOf(raw.currencyCode)),
  };
}

export function toBudgetMonth(raw: BudgetMonthResponse): BudgetMonth {
  const currency = currencyOf(raw.currencyCode);

  const toCategory = (category: CategorySpentResponse): CategorySpent => ({
    categoryId: asCategoryId(category.categoryId),
    name: category.name,
    color: category.color ?? null,
    spent: money(category.spentCents, currency),
  });

  const toStatus = (status: BudgetStatusResponse): BudgetStatus => ({
    id: asBudgetId(status.id),
    name: status.name,
    limit: money(status.limitCents, currency),
    spent: money(status.spentCents, currency),
    remaining: money(status.remainingCents, currency),
    categories: status.categories.map(toCategory),
    children: status.children.map(toStatus),
  });

  return {
    month: raw.month,
    limit: money(raw.limitCents, currency),
    spent: money(raw.spentCents, currency),
    remaining: money(raw.remainingCents, currency),
    unbudgeted: money(raw.unbudgetedCents, currency),
    expenses: money(raw.expensesCents, currency),
    budgets: raw.budgets.map(toStatus),
  };
}
