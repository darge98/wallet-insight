import { AccountId, Money } from '@wallet/shared-domain';
import { ImportSource } from '@wallet/ingestion-domain';

/**
 * Natura di un conto, normalizzata dal backend.
 *
 * L'elenco nasce dai tipi che le sorgenti dichiarano davvero, ma i nomi sono di
 * Wallet Insights: un conto corrente è un conto corrente comunque lo chiami la banca che
 * lo espone. `unknown` non è un errore da nascondere — è un conto entrato con un
 * tipo che ancora non conosciamo, e va mostrato per quello che è.
 */
export const ACCOUNT_KINDS = [
  'general',
  'cash',
  'current-account',
  'credit-card',
  'savings',
  'insurance',
  'investment',
  'loan',
  'mortgage',
  'overdraft',
  'bonus',
  'unknown',
] as const;

export type AccountKind = (typeof ACCOUNT_KINDS)[number];

export const ACCOUNT_KIND_LABELS: Readonly<Record<AccountKind, string>> = {
  general: 'Generico',
  cash: 'Contanti',
  'current-account': 'Conto corrente',
  'credit-card': 'Carta di credito',
  savings: 'Risparmio',
  insurance: 'Assicurazione',
  investment: 'Investimenti',
  loan: 'Prestito',
  mortgage: 'Mutuo',
  overdraft: 'Scoperto',
  bonus: 'Bonus',
  unknown: 'Da riconoscere',
};

export const isAccountKind = (value: string): value is AccountKind =>
  (ACCOUNT_KINDS as readonly string[]).includes(value);

/**
 * Un conto dell'utente, nato da una sorgente di importazione.
 *
 * Porta **due** nomi di proprietà diversa. `sourceName` è come il conto si chiama
 * nella sorgente e viene riscritto a ogni import; `name` è come si chiama in
 * Wallet Insights e appartiene all'utente. Finché coincidono il nome segue la sorgente;
 * appena divergono, un import non tocca più `name`. Serve tenerli entrambi perché
 * l'interfaccia possa dire «rinominato da te, là si chiama X» e offrire di
 * tornare indietro senza richiedere niente al server.
 *
 * `color` è l'altra cosa che appartiene a chi guarda, e segue la stessa regola:
 * nessun import lo tocca. È `null` finché l'utente non ne sceglie uno — un colore
 * assegnato d'ufficio sarebbe indistinguibile da una scelta vera.
 *
 * I due saldi ci sono entrambi di proposito: `initialBalance` è il punto di
 * partenza dichiarato dalla sorgente, `balance` è quello di oggi, cioè il primo
 * più la somma dei movimenti importati. La loro differenza è quanto i movimenti
 * dicono di aver spostato, ed è il modo più diretto di accorgersi che ne manca uno.
 */
export interface Account {
  readonly id: AccountId;
  /** Da dove il conto è stato importato: fa parte della sua identità. */
  readonly source: ImportSource;
  readonly name: string;
  readonly sourceName: string;
  readonly kind: AccountKind;
  readonly initialBalance: Money;
  readonly balance: Money;
  /** Colore scelto dall'utente in formato `#rrggbb`; `null` se non l'ha scelto. */
  readonly color: string | null;
  /** Ultime quattro cifre del numero di conto; `null` se non ne ha nessuna. */
  readonly numberLast4: string | null;
  /** Archiviato nella sorgente: resta, perché possiede movimenti storici. */
  readonly archived: boolean;
  readonly excludedFromStats: boolean;
}

/** `true` se il nome mostrato non è più quello della sorgente. */
export const isRenamedByUser = (account: Account): boolean => account.name !== account.sourceName;

export const activeAccounts = (accounts: readonly Account[]): readonly Account[] =>
  accounts.filter((account) => !account.archived);
