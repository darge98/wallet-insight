import { describe, expect, it } from 'vitest';

import { amountInput, parseAmount } from './amount-input';

describe('parseAmount', () => {
  it('legge gli importi come li scrive una persona', () => {
    expect(parseAmount('300')).toBe(30_000);
    expect(parseAmount('300,5')).toBe(30_050);
    expect(parseAmount(' 1.250,50 € ')).toBe(125_050);
    expect(parseAmount('12.5')).toBe(1_250);
    expect(parseAmount('1.500')).toBe(150_000);
    expect(parseAmount('12.000')).toBe(1_200_000);
    expect(parseAmount('1.50')).toBe(150);
  });

  it('rifiuta ciò che non è un importo positivo', () => {
    expect(parseAmount('')).toBeNull();
    expect(parseAmount('0')).toBeNull();
    expect(parseAmount('-20')).toBeNull();
    expect(parseAmount('dieci')).toBeNull();
    expect(parseAmount('1,234')).toBeNull();
  });
});

describe('amountInput', () => {
  it('riscrive i centesimi come si digitano', () => {
    expect(amountInput(30_000)).toBe('300');
    expect(amountInput(30_050)).toBe('300,50');
  });
});
