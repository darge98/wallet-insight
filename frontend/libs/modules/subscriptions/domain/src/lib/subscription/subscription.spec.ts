import { describe, expect, it } from 'vitest';

import { asAccountId, asCategoryId, asRecordId, money } from '@wallet/shared-domain';

import { cadenceLabel, daysBetween, subscriptionDraftFrom } from './subscription';

describe('cadenceLabel', () => {
  it('dice la cadenza come la direbbe una persona', () => {
    expect(cadenceLabel({ every: 1, unit: 'month' })).toBe('Ogni mese');
    expect(cadenceLabel({ every: 3, unit: 'month' })).toBe('Ogni 3 mesi');
    expect(cadenceLabel({ every: 1, unit: 'year' })).toBe('Ogni anno');
    expect(cadenceLabel({ every: 1, unit: 'week' })).toBe('Ogni settimana');
    expect(cadenceLabel({ every: 2, unit: 'week' })).toBe('Ogni 2 settimane');
  });
});

describe('daysBetween', () => {
  it('conta i giorni di calendario, anche a cavallo del cambio d’ora', () => {
    expect(daysBetween('2026-10-05', '2026-10-05')).toBe(0);
    expect(daysBetween('2026-10-20', '2026-10-27')).toBe(7);
    expect(daysBetween('2026-10-05', '2026-10-04')).toBe(-1);
  });
});

describe('subscriptionDraftFrom', () => {
  const addebito = {
    id: asRecordId('r1'),
    accountId: asAccountId('a1'),
    type: 'expense' as const,
    state: 'cleared' as const,
    date: '2026-09-13',
    amount: money(-999),
    description: 'Fastweb S.p.A.',
    counterParty: 'Ho Mobile',
    categoryId: asCategoryId('c1'),
  };

  it('fa del movimento il primo addebito, con importo positivo e i suoi riferimenti', () => {
    expect(subscriptionDraftFrom(addebito)).toEqual({
      name: 'Ho Mobile',
      amount: money(999),
      cadence: { every: 1, unit: 'month' },
      startDate: '2026-09-13',
      endDate: null,
      categoryId: asCategoryId('c1'),
      accountId: asAccountId('a1'),
    });
  });

  it('senza controparte prende la descrizione', () => {
    expect(subscriptionDraftFrom({ ...addebito, counterParty: null }).name).toBe('Fastweb S.p.A.');
  });
});
