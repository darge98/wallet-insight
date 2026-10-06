import { CurrencyCode, DEFAULT_CURRENCY } from './currency';

/**
 * Importo monetario immutabile.
 *
 * `amount` è espresso in **unità minori** (centesimi) e sempre intero: evita
 * gli errori di arrotondamento del floating point nelle somme di transazioni.
 */
export interface Money {
  readonly amount: number;
  readonly currency: CurrencyCode;
}

export class CurrencyMismatchError extends Error {
  constructor(left: CurrencyCode, right: CurrencyCode) {
    super(`Impossibile operare su valute diverse: ${left} e ${right}.`);
    this.name = 'CurrencyMismatchError';
  }
}

export function money(minorUnits: number, currency: CurrencyCode = DEFAULT_CURRENCY): Money {
  return { amount: Math.round(minorUnits), currency };
}

export function moneyFromMajor(value: number, currency: CurrencyCode = DEFAULT_CURRENCY): Money {
  return money(value * 100, currency);
}

export function zeroMoney(currency: CurrencyCode = DEFAULT_CURRENCY): Money {
  return { amount: 0, currency };
}

export function toMajorUnits(value: Money): number {
  return value.amount / 100;
}

function assertSameCurrency(left: Money, right: Money): void {
  if (left.currency !== right.currency) {
    throw new CurrencyMismatchError(left.currency, right.currency);
  }
}

export function addMoney(left: Money, right: Money): Money {
  assertSameCurrency(left, right);
  return money(left.amount + right.amount, left.currency);
}

export function subtractMoney(left: Money, right: Money): Money {
  assertSameCurrency(left, right);
  return money(left.amount - right.amount, left.currency);
}

export function sumMoney(
  values: readonly Money[],
  currency: CurrencyCode = DEFAULT_CURRENCY,
): Money {
  return values.reduce<Money>((total, value) => addMoney(total, value), zeroMoney(currency));
}

export function negateMoney(value: Money): Money {
  return money(-value.amount, value.currency);
}

export function absMoney(value: Money): Money {
  return money(Math.abs(value.amount), value.currency);
}

export function scaleMoney(value: Money, factor: number): Money {
  return money(value.amount * factor, value.currency);
}

export function isNegativeMoney(value: Money): boolean {
  return value.amount < 0;
}

export function isZeroMoney(value: Money): boolean {
  return value.amount === 0;
}

export function compareMoney(left: Money, right: Money): number {
  assertSameCurrency(left, right);
  return left.amount - right.amount;
}

/** Rapporto fra due importi (0 quando il denominatore è nullo). */
export function moneyRatio(numerator: Money, denominator: Money): number {
  assertSameCurrency(numerator, denominator);
  return denominator.amount === 0 ? 0 : numerator.amount / denominator.amount;
}
