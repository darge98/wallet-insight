import { IsoDate, YearMonth, fromIsoDate, toIsoDate } from '@wallet/shared-domain';

import { APP_LOCALE } from './format';

const dayFormatter = new Intl.DateTimeFormat(APP_LOCALE, {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

const dayWithYearFormatter = new Intl.DateTimeFormat(APP_LOCALE, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const compactFormatter = new Intl.DateTimeFormat(APP_LOCALE, { day: '2-digit', month: 'short' });

/** Etichetta di gruppo per la lista movimenti: "Oggi", "Ieri" o data estesa. */
export function formatDayLabel(date: IsoDate, today: Date = new Date()): string {
  const todayIso = toIsoDate(today);
  if (date === todayIso) return 'Oggi';

  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  if (date === toIsoDate(yesterday)) return 'Ieri';

  const parsed = fromIsoDate(date);
  const label =
    parsed.getFullYear() === today.getFullYear()
      ? dayFormatter.format(parsed)
      : dayWithYearFormatter.format(parsed);

  return label.charAt(0).toUpperCase() + label.slice(1);
}

export function formatCompactDate(date: IsoDate): string {
  return compactFormatter.format(fromIsoDate(date)).replace('.', '');
}

export function formatLongDate(date: IsoDate): string {
  const label = dayWithYearFormatter.format(fromIsoDate(date));
  return label.charAt(0).toUpperCase() + label.slice(1);
}

const monthFormatter = new Intl.DateTimeFormat(APP_LOCALE, {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

/** «ottobre 2026». */
export function formatMonth(month: YearMonth): string {
  const [year = 0, monthNumber = 1] = month.split('-').map(Number);
  return monthFormatter.format(new Date(Date.UTC(year, monthNumber - 1, 1)));
}
