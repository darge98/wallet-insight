import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  ConfigureImportConnectionCommand,
  ImportConnection,
  ImportConnectionRepository,
} from '@wallet/ingestion-domain';
import { API_BASE_URL, currentUserId, requestJson } from '@wallet/shared-data-access';

import { ImportConnectionResponse, toImportConnection } from './import-connection-contract';

/** Le sorgenti collegate dall'utente corrente, con lo stato dell'ultimo aggiornamento. */
@Injectable()
export class HttpImportConnectionRepository implements ImportConnectionRepository {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  async findAll(signal?: AbortSignal): Promise<readonly ImportConnection[]> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const connections = await requestJson<readonly ImportConnectionResponse[]>(
      this.http,
      'GET',
      `${this.baseUrl}/users/${userId}/import-connections`,
      { signal },
    );

    return connections.map(toImportConnection);
  }

  async replaceToken(
    command: ConfigureImportConnectionCommand,
    signal?: AbortSignal,
  ): Promise<ImportConnection> {
    const userId = await currentUserId(this.http, this.baseUrl, signal);
    const connection = await requestJson<ImportConnectionResponse>(
      this.http,
      'PUT',
      `${this.baseUrl}/users/${userId}/import-connections/${command.source}`,
      { body: { token: command.token }, signal },
    );

    return toImportConnection(connection);
  }
}
