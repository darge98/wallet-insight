import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '@wallet/shared-data-access';

import { HttpImportConnectionRepository } from './http-import-connection.repository';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const CONNESSIONI = `/api/users/${UTENTE}/import-connections`;

const PAGINA_UTENTI = {
  items: [
    {
      id: UTENTE,
      firstName: 'Marta',
      timeZone: 'Europe/Rome',
      language: 'it',
      defaultDashboardPeriod: 'current-month',
    },
  ],
  total: 1,
  index: 0,
  size: 1,
  pageCount: 1,
};

const prossimaRichiesta = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('HttpImportConnectionRepository', () => {
  let repository: HttpImportConnectionRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpImportConnectionRepository,
      ],
    });
    repository = TestBed.inject(HttpImportConnectionRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('tiene distinte la data dei dati e l’istante dell’ultimo controllo', async () => {
    const lette = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(CONNESSIONI).flush([
      {
        source: 'budget-bakers',
        enabled: true,
        secretHint: '…a1b2',
        configuredAt: '2026-09-22',
        lastRecordDate: '2026-09-19',
        lastRunAt: '2026-09-22T20:31:26.121758Z',
      },
    ]);

    const connessione = (await lette)[0];
    // Sono due fatti diversi: fino a quando si hanno i dati, e quando si è guardato.
    expect(connessione?.lastRecordDate).toBe('2026-09-19');
    expect(connessione?.lastRunAt).toBe('2026-09-22T20:31:26.121758Z');
  });

  it('una sorgente mai importata non ha nessuna delle due date', async () => {
    const lette = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http
      .expectOne(CONNESSIONI)
      .flush([{ source: 'budget-bakers', enabled: true, configuredAt: '2026-09-22' }]);

    const connessione = (await lette)[0];
    expect(connessione?.lastRecordDate).toBeNull();
    expect(connessione?.lastRunAt).toBeNull();
    expect(connessione?.secretHint).toBeNull();
  });

  it('sostituisce il token con un PUT e restituisce la connessione riletta', async () => {
    const sostituita = repository.replaceToken({ source: 'budget-bakers', token: 'a.b.c' });
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    const richiesta = http.expectOne(`${CONNESSIONI}/budget-bakers`);
    expect(richiesta.request.method).toBe('PUT');
    expect(richiesta.request.body).toEqual({ token: 'a.b.c' });
    richiesta.flush({
      source: 'budget-bakers',
      enabled: true,
      configuredAt: '2026-10-04',
      credentialsRejectedAt: '2026-10-04T08:00:00Z',
    });

    expect((await sostituita).credentialsRejectedAt).toBe('2026-10-04T08:00:00Z');
  });
});
