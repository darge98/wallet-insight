import { describe, expect, it } from 'vitest';

import { money } from '@wallet/shared-domain';

import { formatCount, formatMoney, formatPercent, formatSignedPercent } from './format';

// Intl separa cifra e simbolo con uno spazio non divisibile: lo si normalizza
// per scrivere le attese come si leggono.
const testo = (value: string) => value.replace(/\s/g, ' ');

describe('formatMoney', () => {
  it('scrive i centesimi e mette il meno tipografico', () => {
    expect(testo(formatMoney(money(-1_234_567)))).toBe('−12.345,67 €');
  });

  it('col segno antepone sempre più o meno', () => {
    expect(testo(formatMoney(money(500), { signed: true }))).toBe('+5,00 €');
    expect(testo(formatMoney(money(-500), { signed: true }))).toBe('−5,00 €');
  });

  it('in forma compatta tiene un decimale e niente centesimi', () => {
    expect(testo(formatMoney(money(1_234_567_890), { compact: true }))).toBe('12,3 Mln €');
  });

  it('senza decimali arrotonda all’euro', () => {
    expect(testo(formatMoney(money(1_250), { decimals: false }))).toBe('13 €');
  });
});

describe('percentuali e conteggi', () => {
  it('scrive le percentuali col meno tipografico', () => {
    expect(testo(formatPercent(-12.5))).toBe('−12,5%');
    expect(testo(formatSignedPercent(3))).toBe('+3%');
  });

  // In italiano un numero di quattro cifre non si raggruppa: «1678», non «1.678».
  it('raggruppa le migliaia', () => {
    expect(formatCount(12_345)).toBe('12.345');
  });
});
