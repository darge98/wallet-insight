import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { IMPORT_CONNECTION_REPOSITORY } from '@wallet/ingestion-domain';

import { HttpImportConnectionRepository } from './http-import-connection.repository';

export function provideIngestionHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: IMPORT_CONNECTION_REPOSITORY, useClass: HttpImportConnectionRepository },
  ]);
}
