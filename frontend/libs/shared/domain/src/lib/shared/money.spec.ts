import { describe, expect, it } from 'vitest';

import {
  CurrencyMismatchError,
  addMoney,
  compareMoney,
  money,
  moneyFromMajor,
  moneyRatio,
  negateMoney,
  subtractMoney,
  sumMoney,
  toMajorUnits,
  zeroMoney,
} from './money';

describe('Money', () => {
  it('converte le unità maggiori in centesimi interi', () => {
    expect(moneyFromMajor(12.34)).toEqual({ amount: 1234, currency: 'EUR' });
    expect(moneyFromMajor(0.1)).toEqual({ amount: 10, currency: 'EUR' });
  });

  it('evita gli errori di arrotondamento del floating point', () => {
    const total = sumMoney([moneyFromMajor(0.1), moneyFromMajor(0.2)]);

    expect(total.amount).toBe(30);
    expect(toMajorUnits(total)).toBe(0.3);
  });

  it('somma e sottrae mantenendo la valuta', () => {
    expect(addMoney(money(500), money(250))).toEqual({ amount: 750, currency: 'EUR' });
    expect(subtractMoney(money(500), money(250))).toEqual({ amount: 250, currency: 'EUR' });
  });

  it('rifiuta le operazioni fra valute diverse', () => {
    expect(() => addMoney(money(100, 'EUR'), money(100, 'USD'))).toThrow(CurrencyMismatchError);
  });

  it('nega e confronta', () => {
    expect(negateMoney(money(120)).amount).toBe(-120);
    expect(compareMoney(money(120), money(100))).toBeGreaterThan(0);
  });

  it('restituisce 0 come rapporto quando il denominatore è nullo', () => {
    expect(moneyRatio(money(100), zeroMoney())).toBe(0);
    expect(moneyRatio(money(50), money(200))).toBe(0.25);
  });
});
