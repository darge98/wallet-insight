import { describe, expect, it } from 'vitest';

import { asCategoryId, money } from '@wallet/shared-domain';

import { asBudgetId, Budget, roomForChild, unavailableCategories } from './budget';
import { budgetHealth, budgetUsage } from './budget-month';

const RISTORANTI = asCategoryId('cat-ristoranti');
const SPESA = asCategoryId('cat-spesa');
const CINEMA = asCategoryId('cat-cinema');

const cibo: Budget = {
  id: asBudgetId('cibo'),
  parentId: null,
  name: 'Cibo',
  categoryIds: [RISTORANTI, SPESA],
  limit: money(50_000),
};
const fuori: Budget = {
  id: asBudgetId('fuori'),
  parentId: cibo.id,
  name: 'Fuori',
  categoryIds: [RISTORANTI],
  limit: money(20_000),
};
const svago: Budget = {
  id: asBudgetId('svago'),
  parentId: null,
  name: 'Svago',
  categoryIds: [CINEMA],
  limit: money(10_000),
};

describe('unavailableCategories', () => {
  it('fra principali, una categoria è di chi la tiene già', () => {
    const taken = unavailableCategories([cibo, fuori, svago], { id: null, parentId: null });

    expect(taken.get(RISTORANTI)).toBe('Cibo');
    expect(taken.get(CINEMA)).toBe('Svago');
  });

  it('un budget non si toglie le proprie categorie', () => {
    const taken = unavailableCategories([cibo, svago], { id: cibo.id, parentId: null });

    expect(taken.has(RISTORANTI)).toBe(false);
  });

  it('un sotto-budget vede prese solo quelle dei fratelli', () => {
    const taken = unavailableCategories([cibo, fuori, svago], { id: null, parentId: cibo.id });

    expect(taken.get(RISTORANTI)).toBe('Fuori');
    expect(taken.has(SPESA)).toBe(false);
  });
});

describe('roomForChild', () => {
  it('è il limite del principale meno quanto promettono gli altri sotto-budget', () => {
    expect(roomForChild([cibo, fuori], cibo, null)).toEqual(money(30_000));
    expect(roomForChild([cibo, fuori], cibo, fuori.id)).toEqual(money(50_000));
  });
});

describe('budgetHealth', () => {
  it('distingue i tre stati', () => {
    expect(budgetHealth({ spent: money(10_000), limit: money(20_000) })).toBe('on-track');
    expect(budgetHealth({ spent: money(16_000), limit: money(20_000) })).toBe('at-risk');
    expect(budgetHealth({ spent: money(20_000), limit: money(20_000) })).toBe('at-risk');
    expect(budgetHealth({ spent: money(20_001), limit: money(20_000) })).toBe('exceeded');
  });

  it('lascia la quota sopra 100 quando si è sforato', () => {
    expect(budgetUsage({ spent: money(30_000), limit: money(20_000) })).toBe(150);
  });
});
