import { ImportConnection, isImportSource } from '@wallet/ingestion-domain';
import { contractError } from '@wallet/shared-data-access';

export interface ImportConnectionResponse {
  readonly source: string;
  readonly enabled: boolean;
  readonly secretHint?: string;
  readonly configuredAt: string;
  readonly lastRecordDate?: string;
  readonly lastRunAt?: string;
  readonly credentialsRejectedAt?: string;
}

export function toImportConnection(raw: ImportConnectionResponse): ImportConnection {
  if (!isImportSource(raw.source)) {
    throw contractError('source', raw.source);
  }

  return {
    source: raw.source,
    enabled: raw.enabled,
    secretHint: raw.secretHint ?? null,
    configuredAt: raw.configuredAt,
    lastRecordDate: raw.lastRecordDate ?? null,
    lastRunAt: raw.lastRunAt ?? null,
    credentialsRejectedAt: raw.credentialsRejectedAt ?? null,
  };
}
