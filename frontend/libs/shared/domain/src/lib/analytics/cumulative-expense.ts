import { IsoDate } from '../shared/date-range';
import { Money } from '../shared/money';

/**
 * Spesa cumulata giorno per giorno all'interno del periodo.
 *
 * Risponde alla domanda "a che ritmo sto consumando il mese": la curva sale
 * sempre, e la sua pendenza è il ritmo di spesa.
 */
export interface CumulativeExpensePoint {
  readonly date: IsoDate;
  readonly total: Money;
}
