import { Injectable, computed, inject, resource } from '@angular/core';

import {
  IMPORT_CONNECTION_REPOSITORY,
  ImportConnection,
  ImportSource,
} from '@wallet/ingestion-domain';

/**
 * Le sorgenti collegate, una copia sola per tutta l'app: le Impostazioni le
 * modificano, la topbar avvisa quando un token è stato rifiutato.
 */
@Injectable({ providedIn: 'root' })
export class ImportConnections {
  private readonly repository = inject(IMPORT_CONNECTION_REPOSITORY);

  readonly connections = resource({
    loader: ({ abortSignal }) => this.repository.findAll(abortSignal),
    defaultValue: [] as readonly ImportConnection[],
  });

  /** L'ultimo import riuscito fra le sorgenti attive, ISO 8601; `null` se non ce n'è. */
  readonly lastRunAt = computed(() => {
    const istanti = this.connections
      .value()
      .filter((connection) => connection.enabled && connection.lastRunAt !== null)
      .map((connection) => connection.lastRunAt as string);
    return istanti.length === 0
      ? null
      : istanti.reduce((ultimo, istante) =>
          Date.parse(istante) > Date.parse(ultimo) ? istante : ultimo,
        );
  });

  readonly withRejectedCredentials = computed(() =>
    this.connections.value().filter((connection) => connection.credentialsRejectedAt !== null),
  );

  async replaceToken(source: ImportSource, token: string): Promise<void> {
    const updated = await this.repository.replaceToken({ source, token });
    this.connections.value.update((connections) =>
      connections.map((connection) => (connection.source === source ? updated : connection)),
    );
  }
}
