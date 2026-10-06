import { asBudgetId, BudgetMonth, BudgetStatus } from '@wallet/budgets-domain';
import { Money } from '@wallet/shared-domain';

import { budgetHighlights } from './budget-highlights';

const euro = (amount: number): Money => ({ amount, currency: 'EUR' });

function status(name: string, spent: number, limit: number): BudgetStatus {
  return {
    id: asBudgetId(name),
    name,
    limit: euro(limit),
    spent: euro(spent),
    remaining: euro(limit - spent),
    categories: [],
    children: [],
  };
}

function month(...budgets: BudgetStatus[]): BudgetMonth {
  return {
    month: '2026-10',
    limit: euro(0),
    spent: euro(0),
    remaining: euro(0),
    unbudgeted: euro(0),
    expenses: euro(0),
    budgets,
  };
}

describe('budgetHighlights', () => {
  it('mette in cima il budget più consumato, non il più grande', () => {
    const result = budgetHighlights(
      month(
        status('Spesa', 20_000, 40_000),
        status('Svago', 9_000, 10_000),
        status('Auto', 30_000, 25_000),
      ),
      4,
    );

    expect(result.map((budget) => budget.name)).toEqual(['Auto', 'Svago', 'Spesa']);
  });

  it('si ferma al limite', () => {
    const result = budgetHighlights(
      month(status('A', 1, 10), status('B', 5, 10), status('C', 9, 10)),
      2,
    );

    expect(result.map((budget) => budget.name)).toEqual(['C', 'B']);
  });
});
