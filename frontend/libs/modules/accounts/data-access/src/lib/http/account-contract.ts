import { Account, isAccountKind } from '@wallet/accounts-domain';
import { contractError } from '@wallet/shared-data-access';
import { asAccountId, isCurrencyCode, money } from '@wallet/shared-domain';
import { isImportSource } from '@wallet/ingestion-domain';

export interface AccountResponse {
  readonly id: string;
  readonly source: string;
  readonly name: string;
  readonly sourceName: string;
  readonly kind: string;
  readonly currencyCode: string;
  readonly initialBalanceCents: number;
  readonly balanceCents: number;
  readonly color?: string;
  readonly ibanLast4?: string;
  readonly archived: boolean;
  readonly excludedFromStats: boolean;
}

export function toAccount(raw: AccountResponse): Account {
  if (!isImportSource(raw.source)) {
    throw contractError('source', raw.source);
  }
  if (!isAccountKind(raw.kind)) {
    throw contractError('kind', raw.kind);
  }
  if (!isCurrencyCode(raw.currencyCode)) {
    throw contractError('currencyCode', raw.currencyCode);
  }

  return {
    id: asAccountId(raw.id),
    source: raw.source,
    name: raw.name,
    sourceName: raw.sourceName,
    kind: raw.kind,
    initialBalance: money(raw.initialBalanceCents, raw.currencyCode),
    balance: money(raw.balanceCents, raw.currencyCode),
    color: raw.color ?? null,
    numberLast4: raw.ibanLast4 ?? null,
    archived: raw.archived,
    excludedFromStats: raw.excludedFromStats,
  };
}
