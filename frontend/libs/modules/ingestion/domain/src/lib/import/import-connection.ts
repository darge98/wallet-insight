import { IsoDate } from '@wallet/shared-util';

import { ImportSource } from './import-source';

/**
 * Una sorgente collegata dall'utente.
 *
 * Il segreto **non** fa parte dell'entità: una volta consegnato al backend non
 * torna più indietro, e l'interfaccia ne mostra soltanto la traccia. È lo stesso
 * contratto di `ImportConnectionResponse` lato server.
 */
export interface ImportConnection {
  readonly source: ImportSource;
  readonly enabled: boolean;
  readonly secretHint: string | null;
  readonly configuredAt: IsoDate;
  /**
   * Fino a quando si hanno i dati: la data del movimento più recente importato.
   * `null` finché la sorgente non è mai stata letta.
   */
  readonly lastRecordDate: IsoDate | null;
  /**
   * Quando si è guardato l'ultima volta, con l'ora (ISO 8601 completo).
   *
   * Diverso da `lastRecordDate`, ed è proprio la distinzione a essere utile: un
   * aggiornamento che non trova nulla muove questo e lascia fermo quello. Sapere
   * che dieci minuti fa non c'era niente di nuovo è una risposta; non saperlo
   * lascia il dubbio che sia rotto. `null` se un import non è mai arrivato in fondo.
   */
  readonly lastRunAt: string | null;
  /**
   * Quando la sorgente ha rifiutato il token (ISO 8601). Finché non è `null` gli
   * aggiornamenti falliscono, e solo un token nuovo li fa ripartire.
   */
  readonly credentialsRejectedAt: string | null;
}

/** Dati necessari a collegare una sorgente che usa un token personale. */
export interface ConfigureImportConnectionCommand {
  readonly source: ImportSource;
  readonly token: string;
}
