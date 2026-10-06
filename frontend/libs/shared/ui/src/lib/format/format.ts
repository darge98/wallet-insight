import { CurrencyCode, Money, toMajorUnits } from '@wallet/shared-domain';

export const APP_LOCALE = 'it-IT';

const currencyFormatters = new Map<string, Intl.NumberFormat>();

function formatterFor(
  currency: CurrencyCode,
  compact: boolean,
  decimals: boolean,
): Intl.NumberFormat {
  const key = `${currency}|${compact}|${decimals}`;
  let formatter = currencyFormatters.get(key);

  if (!formatter) {
    const compactDigits = compact ? 1 : 0;
    formatter = new Intl.NumberFormat(APP_LOCALE, {
      style: 'currency',
      currency,
      notation: compact ? 'compact' : 'standard',
      maximumFractionDigits: decimals ? 2 : compactDigits,
      minimumFractionDigits: decimals ? 2 : 0,
    });
    currencyFormatters.set(key, formatter);
  }

  return formatter;
}

export interface MoneyFormatOptions {
  /** Antepone sempre `+` o `−`. */
  readonly signed?: boolean;
  /** Notazione abbreviata (1,2 mila €): per assi e KPI compatti. */
  readonly compact?: boolean;
  readonly decimals?: boolean;
}

export function formatMoney(value: Money, options: MoneyFormatOptions = {}): string {
  const { signed = false, compact = false, decimals = !compact } = options;
  const major = toMajorUnits(value);
  const formatted = formatterFor(value.currency, compact, decimals).format(Math.abs(major));

  if (!signed) {
    return major < 0 ? `−${formatted}` : formatted;
  }

  return `${major < 0 ? '−' : '+'}${formatted}`;
}

const percentFormatter = new Intl.NumberFormat(APP_LOCALE, {
  style: 'percent',
  maximumFractionDigits: 1,
});

export function formatPercent(value: number): string {
  const formatted = percentFormatter.format(Math.abs(value) / 100);
  return value < 0 ? `−${formatted}` : formatted;
}

export function formatSignedPercent(value: number): string {
  const formatted = percentFormatter.format(Math.abs(value) / 100);
  return `${value < 0 ? '−' : '+'}${formatted}`;
}

const numberFormatter = new Intl.NumberFormat(APP_LOCALE);

export function formatCount(value: number): string {
  return numberFormatter.format(value);
}
