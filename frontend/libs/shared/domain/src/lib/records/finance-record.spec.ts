import { describe, expect, it } from 'vitest';

import { asAccountId, asRecordId } from '../shared/identifier';
import { FinanceRecord, recordDetail, recordTitle } from './finance-record';

const MOVIMENTO: FinanceRecord = {
  id: asRecordId('019b4c60-2f4f-7c03-8e44-2b8f6d3c0e72'),
  accountId: asAccountId('019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61'),
  type: 'expense',
  state: 'cleared',
  date: '2026-09-19',
  amount: { amount: -1250, currency: 'EUR' },
  description: 'Spesa',
  counterParty: 'Conad',
  categoryId: null,
};

describe('recordDetail', () => {
  it('con la controparte nel titolo, la descrizione sta accanto', () => {
    expect(recordTitle(MOVIMENTO)).toBe('Conad');
    expect(recordDetail(MOVIMENTO)).toBe('Spesa');
  });

  it('senza controparte la descrizione è già il titolo, e non si ripete', () => {
    const movimento = { ...MOVIMENTO, counterParty: null };

    expect(recordTitle(movimento)).toBe('Spesa');
    expect(recordDetail(movimento)).toBeNull();
  });

  it('una controparte senza descrizione lascia la colonna vuota', () => {
    expect(recordDetail({ ...MOVIMENTO, description: null })).toBeNull();
  });
});
