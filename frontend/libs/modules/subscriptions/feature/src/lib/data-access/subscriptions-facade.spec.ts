import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { AccountsOverview } from '@wallet/accounts-data-access';
import {
  asCategoryId,
  Category,
  CATEGORY_REPOSITORY,
  CategoryRepository,
  money,
} from '@wallet/shared-domain';
import {
  asSubscriptionId,
  Subscription,
  SubscriptionDraft,
  SubscriptionRejectedError,
  SubscriptionRepository,
  SUBSCRIPTION_REPOSITORY,
} from '@wallet/subscriptions-domain';

import { SubscriptionsFacade } from './subscriptions-facade';

const MUSICA = asCategoryId('cat-musica');
const CASA = asCategoryId('cat-casa');

const CATEGORIE: readonly Category[] = [
  { id: MUSICA, parentId: null, name: 'Musica', color: null },
  { id: CASA, parentId: null, name: 'Casa', color: null },
];

function abbonamento(
  id: string,
  categoryId: Subscription['categoryId'],
  active = true,
): Subscription {
  return {
    id: asSubscriptionId(id),
    name: id,
    amount: money(999),
    cadence: { every: 1, unit: 'month' },
    startDate: '2026-01-12',
    endDate: active ? null : '2026-06-12',
    active,
    nextChargeDate: active ? '2026-10-12' : null,
    monthlyCost: money(999),
    yearlyCost: money(11_988),
    categoryId,
    accountId: null,
  };
}

const BOZZA: SubscriptionDraft = {
  name: 'Spotify',
  amount: money(1_199),
  cadence: { every: 1, unit: 'month' },
  startDate: '2026-10-12',
  endDate: null,
  categoryId: null,
  accountId: null,
};

const prossimoGiro = () => new Promise((resolve) => setTimeout(resolve, 0));

function facade(
  abbonamenti: readonly Subscription[],
  scrittura: Partial<SubscriptionRepository> = {},
): SubscriptionsFacade {
  const repository: SubscriptionRepository = {
    findAll: async () => abbonamenti,
    overview: async () => {
      throw new Error('non serve qui');
    },
    calendar: async () => {
      throw new Error('non serve qui');
    },
    create: async () => abbonamenti[0] as Subscription,
    update: async () => abbonamenti[0] as Subscription,
    remove: async () => undefined,
    ...scrittura,
  };
  const categorie: Pick<CategoryRepository, 'findAll'> = { findAll: async () => CATEGORIE };
  const conti = signal([]);
  TestBed.configureTestingModule({
    providers: [
      SubscriptionsFacade,
      { provide: SUBSCRIPTION_REPOSITORY, useValue: repository },
      { provide: CATEGORY_REPOSITORY, useValue: categorie },
      {
        provide: AccountsOverview,
        useValue: { visible: computed(() => conti()), accounts: { value: conti } },
      },
    ],
  });
  return TestBed.inject(SubscriptionsFacade);
}

describe('SubscriptionsFacade', () => {
  it('raggruppa gli attivi per categoria in ordine di nome, quelli senza in fondo', async () => {
    const abbonamenti = facade([
      abbonamento('senza', null),
      abbonamento('spotify', MUSICA),
      abbonamento('luce', CASA),
      abbonamento('netflix', MUSICA),
      abbonamento('disdetto', CASA, false),
    ]);
    await prossimoGiro();

    expect(
      abbonamenti.activeGroups().map((gruppo) => [gruppo.label, gruppo.subscriptions.length]),
    ).toEqual([
      ['Casa', 1],
      ['Musica', 2],
      ['Senza categoria', 1],
    ]);
    expect(abbonamenti.ended().map((item) => item.name)).toEqual(['disdetto']);
  });

  it('un rifiuto del server resta nel pannello col suo motivo', async () => {
    const abbonamenti = facade([], {
      create: async () => {
        throw new SubscriptionRejectedError('La categoria non è una sottocategoria.');
      },
    });
    abbonamenti.create();

    await abbonamenti.save(BOZZA);

    expect(abbonamenti.saveError()).toBe('La categoria non è una sottocategoria.');
    expect(abbonamenti.editing()).not.toBeNull();
    expect(abbonamenti.saving()).toBe(false);
  });

  it('un errore qualsiasi diventa un messaggio generico', async () => {
    const abbonamenti = facade([], {
      create: async () => {
        throw new Error('rete');
      },
    });
    abbonamenti.create();

    await abbonamenti.save(BOZZA);

    expect(abbonamenti.saveError()).toBe('Non è stato possibile salvare. Riprova tra poco.');
  });

  it('salvato, il pannello si chiude', async () => {
    const abbonamenti = facade([]);
    abbonamenti.create();

    await abbonamenti.save(BOZZA);

    expect(abbonamenti.editing()).toBeNull();
    expect(abbonamenti.saveError()).toBeNull();
  });
});
