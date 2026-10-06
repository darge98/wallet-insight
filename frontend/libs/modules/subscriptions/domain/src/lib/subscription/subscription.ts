import {
  absMoney,
  AccountId,
  CategoryId,
  FinanceRecord,
  Identifier,
  IsoDate,
  Money,
} from '@wallet/shared-domain';

export type SubscriptionId = Identifier<'Subscription'>;

export const asSubscriptionId = (raw: string): SubscriptionId => raw as SubscriptionId;

export type CadenceUnit = 'week' | 'month' | 'year';

export const CADENCE_UNITS: readonly CadenceUnit[] = ['week', 'month', 'year'];

/** Ogni quanto si rinnova: «ogni 3 mesi» è `{ every: 3, unit: 'month' }`. */
export interface Cadence {
  readonly every: number;
  readonly unit: CadenceUnit;
}

/** Come `Cadence.MAX_EVERY` nel backend. */
export const MAX_CADENCE_EVERY = 99;

/**
 * Un abbonamento con ciò che ne deriva oggi, calcolato dal server nel fuso
 * dell'utente: prossimo addebito e costi equivalenti non si ricalcolano qui.
 */
export interface Subscription {
  readonly id: SubscriptionId;
  readonly name: string;
  readonly amount: Money;
  readonly cadence: Cadence;
  /** Il primo addebito: i successivi si contano da qui. */
  readonly startDate: IsoDate;
  /** L'ultimo giorno in cui può cadere un addebito; `null` se non è disdetto. */
  readonly endDate: IsoDate | null;
  readonly active: boolean;
  /** `null` quando non ci sono più addebiti. */
  readonly nextChargeDate: IsoDate | null;
  readonly monthlyCost: Money;
  readonly yearlyCost: Money;
  /** La sottocategoria in cui cadrà l'addebito, se scelta. */
  readonly categoryId: CategoryId | null;
  /** Il conto da cui esce, se scelto. */
  readonly accountId: AccountId | null;
}

/** La definizione intera: si scrive tutta, anche per modificarla. */
export interface SubscriptionDraft {
  readonly name: string;
  readonly amount: Money;
  readonly cadence: Cadence;
  readonly startDate: IsoDate;
  readonly endDate: IsoDate | null;
  readonly categoryId: CategoryId | null;
  readonly accountId: AccountId | null;
}

/**
 * La bozza di un abbonamento a partire da un suo addebito: quel movimento è il
 * primo addebito, e nome, importo, categoria e conto sono i suoi. La cadenza non
 * si indovina — si propone la più comune, mensile, e la sceglie l'utente.
 */
export function subscriptionDraftFrom(record: FinanceRecord): SubscriptionDraft {
  return {
    name: (record.counterParty ?? record.description ?? '').trim().slice(0, 80),
    amount: absMoney(record.amount),
    cadence: { every: 1, unit: 'month' },
    startDate: record.date,
    endDate: null,
    categoryId: record.categoryId,
    accountId: record.accountId,
  };
}

const UNIT_LABELS: Readonly<Record<CadenceUnit, { one: string; many: string }>> = {
  week: { one: 'settimana', many: 'settimane' },
  month: { one: 'mese', many: 'mesi' },
  year: { one: 'anno', many: 'anni' },
};

/** «Ogni mese», «Ogni 3 mesi», «Ogni 2 settimane». */
export function cadenceLabel(cadence: Cadence): string {
  const labels = UNIT_LABELS[cadence.unit];
  if (cadence.every === 1) {
    return cadence.unit === 'week' ? 'Ogni settimana' : `Ogni ${labels.one}`;
  }
  return `Ogni ${cadence.every} ${labels.many}`;
}

/** L'unità al singolare o al plurale, per il selettore accanto al numero. */
export function cadenceUnitLabel(unit: CadenceUnit, every: number): string {
  const labels = UNIT_LABELS[unit];
  return every === 1 ? labels.one : labels.many;
}

/** I giorni da `from` a `to`: 0 se coincidono, negativo se `to` viene prima. */
export function daysBetween(from: IsoDate, to: IsoDate): number {
  const day = (date: IsoDate) => {
    const [year = 0, month = 1, dayOfMonth = 1] = date.split('-').map(Number);
    return Date.UTC(year, month - 1, dayOfMonth) / 86_400_000;
  };
  return Math.round(day(to) - day(from));
}
