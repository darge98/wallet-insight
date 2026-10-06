import { CategoryId } from '../shared/identifier';
import { Money } from '../shared/money';

/**
 * Quanto è uscito in una categoria, in un periodo.
 *
 * Porta il nome e il colore già risolti dal server invece del `Category`
 * intero: è una voce di classifica, non l'anagrafica, e chi la mostra non ha
 * bisogno di incrociare due elenchi per scrivere una riga.
 *
 * `share` è la quota sul totale delle uscite del periodo, **in percentuale**
 * (0–100). La calcola il server su *tutte* le categorie e non solo su quelle in
 * classifica: sulle prime cinque le percentuali sommerebbero a cento e direbbero
 * che lì è finito tutto.
 */
export interface CategoryBreakdownSlice {
  readonly categoryId: CategoryId;
  readonly name: string;
  readonly color: string | null;
  readonly total: Money;
  readonly share: number;
  readonly transactionCount: number;
}
