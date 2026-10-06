import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import {
  Budget,
  BudgetDraft,
  BudgetRejectedError,
  BudgetRepository,
  BUDGET_REPOSITORY,
} from '@wallet/budgets-domain';
import { CATEGORY_REPOSITORY, money } from '@wallet/shared-domain';
import { UserFacade } from '@wallet/user-data-access';

import { BudgetsFacade } from './budgets-facade';

const BOZZA: BudgetDraft = { parentId: null, name: 'Cibo', categoryIds: [], limit: money(40_000) };

function facade(create: BudgetRepository['create']): BudgetsFacade {
  const repository: BudgetRepository = {
    findAll: async () => [],
    month: async () => {
      throw new Error('non serve qui');
    },
    create,
    update: async () => {
      throw new Error('non serve qui');
    },
    remove: async () => undefined,
  };
  TestBed.configureTestingModule({
    providers: [
      BudgetsFacade,
      { provide: BUDGET_REPOSITORY, useValue: repository },
      { provide: CATEGORY_REPOSITORY, useValue: { findAll: async () => [] } },
      { provide: UserFacade, useValue: { profile: signal(null) } },
    ],
  });
  return TestBed.inject(BudgetsFacade);
}

describe('BudgetsFacade', () => {
  it('le regole del server tornano nel pannello col loro motivo', async () => {
    const budget = facade(async () => {
      throw new BudgetRejectedError('«Bar» è già in «Uscite».');
    });
    budget.create();

    await budget.save(BOZZA);

    expect(budget.saveError()).toBe('«Bar» è già in «Uscite».');
    expect(budget.editing()).not.toBeNull();
  });

  it('un errore qualsiasi diventa un messaggio generico', async () => {
    const budget = facade(async () => {
      throw new Error('rete');
    });
    budget.create();

    await budget.save(BOZZA);

    expect(budget.saveError()).toBe('Non è stato possibile salvare. Riprova tra poco.');
    expect(budget.saving()).toBe(false);
  });

  it('salvato, il pannello si chiude', async () => {
    const budget = facade(async (draft) => ({ id: 'b-1', ...draft }) as unknown as Budget);
    budget.create();

    await budget.save(BOZZA);

    expect(budget.editing()).toBeNull();
  });
});
