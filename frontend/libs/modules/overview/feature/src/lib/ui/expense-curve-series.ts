import { CumulativeExpensePoint, DateRange, IsoDate, toMajorUnits } from '@wallet/shared-domain';

/** Un punto della curva: istante della mezzanotte UTC del giorno, euro spesi fin lì. */
export type CurvePoint = [number, number];

export interface ExpenseCurveSeries {
  readonly data: readonly CurvePoint[];
  /** L'ultimo giorno con una spesa: il valore che si legge, ed è lì che va il segno. */
  readonly last: CurvePoint;
}

/**
 * I punti della curva su un asse di calendario.
 *
 * Il backend dà solo i giorni con una spesa; qui la curva parte da zero all'inizio del
 * periodo e arriva fino alla sua fine col totale raggiunto, così i giorni senza spese
 * restano visibili come tratti piatti invece di sparire.
 */
export function expenseCurveSeries(
  points: readonly CumulativeExpensePoint[],
  range: DateRange,
): ExpenseCurveSeries | null {
  const first = points[0];
  const lastPoint = points[points.length - 1];
  if (!first || !lastPoint) {
    return null;
  }
  const data: CurvePoint[] = points.map((point) => [
    dayStart(point.date),
    toMajorUnits(point.total),
  ]);
  const last = data[data.length - 1] as CurvePoint;
  if (first.date > range.from) {
    data.unshift([dayStart(range.from), 0]);
  }
  if (lastPoint.date < range.to) {
    data.push([dayStart(range.to), last[1]]);
  }
  return { data, last };
}

export function dayStart(date: IsoDate): number {
  return Date.parse(`${date}T00:00:00Z`);
}
