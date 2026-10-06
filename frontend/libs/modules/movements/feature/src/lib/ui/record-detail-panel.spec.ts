import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import {
  asAccountId,
  asCategoryId,
  asRecordId,
  Category,
  FinanceRecord,
  RecordEdit,
} from '@wallet/shared-domain';

import { RecordDetailPanel } from './record-detail-panel';

const CARBURANTE: Category = {
  id: asCategoryId('019b4c60-2f4f-7d04-9f55-3c9a7e4d1f83'),
  parentId: null,
  name: 'Carburante',
  color: null,
};

const MOVIMENTO: FinanceRecord = {
  id: asRecordId('019b4c60-2f4f-7c03-8e44-2b8f6d3c0e72'),
  accountId: asAccountId('019b4c60-2f4f-7b02-8d33-1a7e5c2b9d61'),
  type: 'expense',
  state: 'cleared',
  date: '2026-09-19',
  amount: { amount: -4453, currency: 'EUR' },
  description: 'Benzina',
  counterParty: null,
  categoryId: CARBURANTE.id,
};

describe('RecordDetailPanel', () => {
  it('salva le tre correzioni così come sono scritte', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(RecordDetailPanel);
    fixture.componentRef.setInput('record', MOVIMENTO);
    fixture.componentRef.setInput('categories', [CARBURANTE]);
    const salvati: RecordEdit[] = [];
    fixture.componentInstance.saved.subscribe((modifica) => salvati.push(modifica));
    fixture.detectChanges();
    await fixture.whenStable();

    const root = fixture.nativeElement as HTMLElement;
    const controparte = root.querySelector<HTMLInputElement>('#movement-counter-party');
    if (!controparte) throw new Error('Campo controparte assente');
    controparte.value = 'Eni Langhirano';
    controparte.dispatchEvent(new Event('input'));
    root.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(salvati).toEqual([
      { description: 'Benzina', counterParty: 'Eni Langhirano', category: CARBURANTE.id },
    ]);
  });
});
