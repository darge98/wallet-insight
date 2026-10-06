import { describe, expect, it } from 'vitest';

import { asAccountId, asCategoryId, asRecordId } from '../shared/identifier';
import { FinanceRecord } from './finance-record';
import { editOf, NO_CATEGORY } from './record-edit';

const CATEGORIA = asCategoryId('019b4c60-2f4f-7d04-9f55-3c9a7e4d1f83');

const MOVIMENTO: FinanceRecord = {
  id: asRecordId('019b4c60-2f4f-7c03-8e44-2b8f6d3c0e72'),
  accountId: asAccountId('019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61'),
  type: 'expense',
  state: 'cleared',
  date: '2026-09-19',
  amount: { amount: -4453, currency: 'EUR' },
  description: 'Benzina',
  counterParty: null,
  categoryId: CATEGORIA,
};

describe('editOf', () => {
  it('parte da quello che il movimento dice adesso', () => {
    expect(editOf(MOVIMENTO)).toEqual({
      description: 'Benzina',
      counterParty: '',
      category: CATEGORIA,
    });
  });

  it("l'assenza diventa un campo vuoto, non la parola null", () => {
    expect(editOf({ ...MOVIMENTO, description: null, categoryId: null })).toEqual({
      description: '',
      counterParty: '',
      category: NO_CATEGORY,
    });
  });
});
