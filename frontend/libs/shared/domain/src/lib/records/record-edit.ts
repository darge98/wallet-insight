import { CategoryId } from '../shared/identifier';
import { FinanceRecord, RecordType } from './finance-record';

/**
 * Come chiamare chi c'è dall'altra parte, a seconda del verso.
 *
 * Una parola sola non basta: su un'uscita la controparte è chi ha incassato, su
 * un'entrata è chi ha pagato, e chiamarle tutte e due «controparte» costringe
 * chi compila a tradurre ogni volta. Sul giroconto non c'è nessuno dei due —
 * i soldi restano propri — quindi resta il termine neutro.
 */
export const COUNTER_PARTY_LABELS: Readonly<Record<RecordType, string>> = {
  expense: 'Pagato a',
  income: 'Ricevuto da',
  transfer: 'Controparte',
};

/**
 * La categoria selezionata.
 *
 * La stringa vuota è lo stato — raro, e su questi dati inesistente — di un
 * movimento che non ha categoria: è un fatto, non una scelta offerta, perché
 * togliere la categoria a un movimento non è un'operazione che l'API conosce.
 */
export const NO_CATEGORY = '';

export type CategoryChoice = CategoryId | typeof NO_CATEGORY;

/**
 * Le tre cose di un movimento che si correggono.
 *
 * È un tipo solo, ed è insieme il modello del modulo e il comando che parte: i
 * campi sono gli stessi, e sdoppiarli vorrebbe dire una traduzione fra due
 * forme identiche. Quello che si vede è quello che si salva.
 */
export interface RecordEdit {
  readonly description: string;
  readonly counterParty: string;
  readonly category: CategoryChoice;
}

/** I valori da cui il modulo parte: quelli che il movimento ha adesso. */
export function editOf(record: FinanceRecord): RecordEdit {
  return {
    description: record.description ?? '',
    counterParty: record.counterParty ?? '',
    category: record.categoryId ?? NO_CATEGORY,
  };
}
