import { Money } from '../shared/money';

/**
 * Quanto è uscito verso una controparte: alimenta «Dove spendi di più».
 *
 * La controparte è una stringa e non un identificatore perché non è
 * un'anagrafica: è il nome che compare sul movimento — Amazon, Conad, una
 * persona — e chi non ne ha resta fuori dalla classifica, invece di finire in un
 * gruppo «senza nome» che sarebbe quasi sempre il primo e non direbbe dove sono
 * andati i soldi.
 */
export interface SpendingTarget {
  readonly counterParty: string;
  readonly total: Money;
  readonly transactionCount: number;
}
