import { IsoDate } from './date-range';

/** Un mese di calendario, `yyyy-MM`: la forma con cui viaggia nel contratto HTTP. */
export type YearMonth = string;

export const monthOf = (date: IsoDate): YearMonth => date.slice(0, 7);

export function shiftMonth(month: YearMonth, delta: number): YearMonth {
  const [year = 0, monthNumber = 1] = month.split('-').map(Number);
  const index = year * 12 + (monthNumber - 1) + delta;
  const shiftedYear = Math.floor(index / 12);
  return `${shiftedYear}-${String(index - shiftedYear * 12 + 1).padStart(2, '0')}`;
}

/** I giorni del mese che restano, oggi compreso. */
export function daysLeftInMonth(today: IsoDate): number {
  const [year = 0, month = 1, day = 1] = today.split('-').map(Number);
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return daysInMonth - day + 1;
}
