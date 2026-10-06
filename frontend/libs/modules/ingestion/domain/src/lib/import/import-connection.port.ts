import { InjectionToken } from '@angular/core';

import { ConfigureImportConnectionCommand, ImportConnection } from './import-connection';

/** Le sorgenti collegate dall'utente. Il segreto entra, non esce mai. */
export interface ImportConnectionRepository {
  findAll(signal?: AbortSignal): Promise<readonly ImportConnection[]>;

  /**
   * Sostituisce il token di una sorgente già collegata. Il backend importa subito:
   * la connessione restituita dice già se il token nuovo funziona.
   */
  replaceToken(
    command: ConfigureImportConnectionCommand,
    signal?: AbortSignal,
  ): Promise<ImportConnection>;
}

export const IMPORT_CONNECTION_REPOSITORY = new InjectionToken<ImportConnectionRepository>(
  'ImportConnectionRepository',
);
