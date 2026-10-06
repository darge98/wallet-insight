import { AccountId, CategoryId, RecordId } from '../shared/identifier';
import { IsoDate } from '../shared/date-range';
import { Money } from '../shared/money';

export const RECORD_TYPES = ['income', 'expense', 'transfer'] as const;

export type RecordType = (typeof RECORD_TYPES)[number];

export const RECORD_TYPE_LABELS: Readonly<Record<RecordType, string>> = {
  income: 'Entrata',
  expense: 'Uscita',
  transfer: 'Trasferimento',
};

export const RECORD_STATES = [
  'reconciled',
  'cleared',
  'uncleared',
  'void',
  'wait-for-assign',
  'unknown',
] as const;

export type RecordState = (typeof RECORD_STATES)[number];

export const RECORD_STATE_LABELS: Readonly<Record<RecordState, string>> = {
  reconciled: 'Riconciliato',
  cleared: 'Contabilizzato',
  uncleared: 'Da contabilizzare',
  void: 'Annullato',
  'wait-for-assign': 'Da assegnare',
  unknown: 'Sconosciuto',
};

/**
 * Movimento finanziario, nella forma in cui il backend lo racconta.
 *
 * Il nome evita la collisione con l'utility type `Record<K, V>` di TypeScript.
 *
 * **`amount` è con segno**: negativo in uscita, positivo in entrata. Prima era
 * sempre positivo e il segno si ricavava dal `type`; adesso il segno è il dato,
 * perché è il dato che arriva — e il saldo di un conto è la somma di questi
 * importi, non una somma con le regole applicate sopra.
 *
 * `type` invece resta, e non è un doppione del segno: distingue il giroconto,
 * che un segno da solo non sa raccontare. Le due gambe di un trasferimento hanno
 * un verso ciascuna, ma non sono né una spesa né un'entrata.
 *
 * Descrizione, controparte e categoria sono **un valore solo ciascuna**, e non
 * una coppia «quello dell'utente accanto a quello di chi l'ha importato». Che il
 * backend tenga le due cose in colonne separate — e che un campo svuotato ricada
 * sull'altra — è affar suo: qui questi sono i dati dell'applicazione, punto, e
 * l'interfaccia non ha modo né motivo di distinguere chi li ha scritti.
 *
 * Non ci sono più metodo di pagamento, etichette, ricorrenza e conto di
 * destinazione: erano campi del dataset dimostrativo, e la sorgente reale non li
 * dichiara. Tenerli avrebbe voluto dire mostrare caselle sempre vuote.
 */
export interface FinanceRecord {
  readonly id: RecordId;
  readonly accountId: AccountId;
  readonly type: RecordType;
  readonly state: RecordState;
  readonly date: IsoDate;
  /** Importo con segno: negativo in uscita, positivo in entrata. */
  readonly amount: Money;
  readonly description: string | null;
  /** Chi c'è dall'altra parte: una persona, un negozio. */
  readonly counterParty: string | null;
  /** La categoria del movimento; `null` se non ne ha una. */
  readonly categoryId: CategoryId | null;
}

/**
 * `true` se il movimento pesa su «quanto ho speso» e «quanto ho incassato».
 *
 * I giroconti no: le due gambe si annullano, e contarle gonfierebbe insieme
 * entrate e uscite con denaro che non è entrato né uscito da nessuna parte.
 */
export function affectsStatistics(record: FinanceRecord): boolean {
  return record.type !== 'transfer';
}

/**
 * `true` se il movimento aspetta ancora di essere contabilizzato dalla banca.
 *
 * Non è una sfumatura di stato fra le altre: finché è così, il movimento è
 * **provvisorio anche nell'identità**. Quando la banca lo conferma, la sorgente
 * non lo aggiorna — ne crea uno nuovo, con un altro identificativo, e fa sparire
 * questo. Tutto ciò che vi fosse stato scritto sopra se ne andrebbe con lui.
 */
export function awaitingSettlement(record: FinanceRecord): boolean {
  return record.state === 'uncleared';
}

/** Il testo con cui una riga si riconosce: chi c'era, o cosa c'era scritto. */
export function recordTitle(record: FinanceRecord): string {
  return record.counterParty ?? record.description ?? 'Senza descrizione';
}

/** La descrizione accanto al titolo; `null` quando il titolo è già lei. */
export function recordDetail(record: FinanceRecord): string | null {
  return record.counterParty === null ? null : record.description;
}
