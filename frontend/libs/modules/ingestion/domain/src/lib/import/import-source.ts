/**
 * Le sorgenti da cui l'applicazione può importare i movimenti.
 *
 * L'elenco rispecchia `it.walletinsight.core.ingestion.domain.IngestionSource` del
 * backend: i due lati devono restare allineati, perché è questo identificativo
 * che viaggia sul filo.
 */
export const IMPORT_SOURCES = ['budget-bakers', 'psd2'] as const;

export type ImportSource = (typeof IMPORT_SOURCES)[number];

/**
 * Come una sorgente autentica le proprie chiamate.
 *
 * `personal-token` è un segreto che l'utente incolla a mano (BudgetBakers emette
 * un JWT personale dalle impostazioni dell'account); `oauth` è un consenso
 * delegato che richiede un giro di redirect, e per questo non si configura con
 * un campo di testo.
 */
export type ImportCredentialKind = 'personal-token' | 'oauth';

export interface ImportSourceDescriptor {
  readonly source: ImportSource;
  /** Nome commerciale della sorgente: è un nome proprio, non si traduce. */
  readonly name: string;
  readonly credential: ImportCredentialKind;
  /**
   * `false` finché l'integrazione non esiste: la sorgente resta visibile — è
   * un impegno preso con l'utente — ma non selezionabile.
   */
  readonly available: boolean;
}

export const IMPORT_SOURCE_CATALOG: readonly ImportSourceDescriptor[] = [
  {
    source: 'budget-bakers',
    name: 'Wallet by BudgetBakers',
    credential: 'personal-token',
    available: true,
  },
  {
    source: 'psd2',
    name: 'PSD2 · Open Banking',
    credential: 'oauth',
    available: false,
  },
];

export function importSourceDescriptor(source: ImportSource): ImportSourceDescriptor {
  const descriptor = IMPORT_SOURCE_CATALOG.find((entry) => entry.source === source);
  if (!descriptor) {
    throw new Error(`Sorgente di importazione sconosciuta: ${source}`);
  }
  return descriptor;
}

export function isImportSource(value: unknown): value is ImportSource {
  return typeof value === 'string' && (IMPORT_SOURCES as readonly string[]).includes(value);
}
