import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import {
  IMPORT_CONNECTION_REPOSITORY,
  ImportConnection,
  ImportConnectionRepository,
} from '@wallet/ingestion-domain';

import { ImportConnections } from './import-connections';

const RIFIUTATA: ImportConnection = {
  source: 'budget-bakers',
  enabled: true,
  secretHint: '…a1b2',
  configuredAt: '2026-09-24',
  lastRecordDate: '2026-09-22',
  lastRunAt: '2026-09-24T12:45:21Z',
  credentialsRejectedAt: '2026-10-04T03:00:00Z',
};

const prossimoGiro = () => new Promise((resolve) => setTimeout(resolve, 0));

function store(repository: ImportConnectionRepository): ImportConnections {
  TestBed.configureTestingModule({
    providers: [{ provide: IMPORT_CONNECTION_REPOSITORY, useValue: repository }],
  });
  return TestBed.inject(ImportConnections);
}

describe('ImportConnections', () => {
  it('segnala le sorgenti con il token rifiutato', async () => {
    const connessioni = store({
      findAll: async () => [RIFIUTATA],
      replaceToken: async () => RIFIUTATA,
    });
    await prossimoGiro();

    expect(connessioni.withRejectedCredentials()).toEqual([RIFIUTATA]);
  });

  it('un token nuovo accettato toglie la segnalazione senza rileggere', async () => {
    let letture = 0;
    const connessioni = store({
      findAll: async () => {
        letture += 1;
        return [RIFIUTATA];
      },
      replaceToken: async ({ token }) => {
        expect(token).toBe('a.b.c');
        return { ...RIFIUTATA, credentialsRejectedAt: null, secretHint: '…b.c' };
      },
    });
    await prossimoGiro();

    await connessioni.replaceToken('budget-bakers', 'a.b.c');

    expect(connessioni.withRejectedCredentials()).toEqual([]);
    expect(connessioni.connections.value()[0]?.secretHint).toBe('…b.c');
    expect(letture).toBe(1);
  });
});
