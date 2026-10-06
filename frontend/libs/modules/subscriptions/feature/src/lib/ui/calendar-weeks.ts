import { IsoDate, YearMonth } from '@wallet/shared-domain';

/**
 * Le settimane del mese, da lunedì a domenica, come si disegnano in griglia: i
 * giorni fuori dal mese sono `null`, così ogni riga ha sempre sette celle.
 */
export function calendarWeeks(month: YearMonth): readonly (readonly (IsoDate | null)[])[] {
  const [year = 0, monthNumber = 1] = month.split('-').map(Number);
  const daysInMonth = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  // getUTCDay: 0 è domenica; qui la settimana parte dal lunedì.
  const offset = (new Date(Date.UTC(year, monthNumber - 1, 1)).getUTCDay() + 6) % 7;

  const cells: (IsoDate | null)[] = Array.from({ length: offset }, () => null);
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push(`${month}-${String(day).padStart(2, '0')}`);
  }
  while (cells.length % 7 !== 0) cells.push(null);

  const weeks: (IsoDate | null)[][] = [];
  for (let index = 0; index < cells.length; index += 7) {
    weeks.push(cells.slice(index, index + 7));
  }
  return weeks;
}
