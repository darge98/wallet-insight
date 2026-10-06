import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { asAccountId, asCategoryId, asRecordId, RecordQuery } from '@wallet/shared-domain';

import { API_BASE_URL } from './api-base-url';
import { HttpRecordRepository } from './http-record.repository';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const MOVIMENTI = `/api/users/${UTENTE}/movements`;
const CONTO = asAccountId('0199ab7c-0000-7000-8000-0000000000aa');
const ALTRO_CONTO = asAccountId('0199ab7c-0000-7000-8000-0000000000bb');
const CATEGORIA = asCategoryId('0199ab7c-0000-7000-8000-0000000000cc');

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

const RISPOSTA_VUOTA = {
  page: { items: [], total: 0, index: 0, size: 25, pageCount: 0 },
  totals: { incomeCents: 0, expensesCents: 0, netCents: 0, currencyCode: 'EUR', count: 0 },
};

const MOVIMENTO = asRecordId('0199ab7c-0000-7000-8000-0000000000dd');

const MOVIMENTO_AGGIORNATO = {
  id: MOVIMENTO,
  accountId: CONTO,
  source: 'budget-bakers',
  amountCents: -4453,
  currencyCode: 'EUR',
  date: '2026-09-19',
  direction: 'expense',
  state: 'cleared',
  description: 'PAGAMENTO DEBINT',
  counterParty: 'Conad Langhirano',
  categoryId: CATEGORIA,
};

const QUERY_MINIMA: RecordQuery = {
  filters: { types: [], accountIds: [], categoryIds: [], range: null },
  sort: { field: 'date', direction: 'desc' },
  page: { index: 0, size: 25 },
};

/**
 * Ogni metodo incatena due richieste: l'identificativo dell'utente e poi quella
 * vera. Fra le due c'è un microtask, quindi il test deve cedere il controllo
 * prima di attendersi la seconda.
 */
const prossimaRichiesta = () => new Promise((resolve) => setTimeout(resolve, 0));

/**
 * Qui si verifica l'unico pezzo che nessun test del backend può coprire: come i
 * criteri del dominio diventano una querystring. È il punto in cui un filtro può
 * sparire in silenzio — la richiesta parte, il server risponde, e l'elenco mostra
 * più righe di quante ne erano state chieste.
 */
describe('HttpRecordRepository', () => {
  let repository: HttpRecordRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpRecordRepository,
      ],
    });
    repository = TestBed.inject(HttpRecordRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  async function richiestaPer(query: RecordQuery): Promise<string> {
    const cercati = repository.search(query);
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();

    // I criteri finiscono nell'URL, non in un oggetto `params`: il confronto è
    // quindi sul prefisso, e ciò che il test legge è la stringa intera.
    const richiesta = http.expectOne((candidate) => candidate.url.startsWith(MOVIMENTI));
    richiesta.flush(RISPOSTA_VUOTA);
    await cercati;

    return richiesta.request.url;
  }

  it('manda sempre pagina, dimensione e ordinamento', async () => {
    const url = await richiestaPer(QUERY_MINIMA);

    expect(url).toContain('page=0');
    expect(url).toContain('size=25');
    expect(url).toContain('sortBy=date');
    expect(url).toContain('sortDirection=desc');
  });

  it('ripete il parametro per ogni valore dello stesso filtro', async () => {
    // `?accountId=a&accountId=b` e non una lista separata da virgole: è la forma
    // che il server legge senza inventarsi un separatore, e non si rompe il giorno
    // in cui un valore contiene il separatore stesso.
    const url = await richiestaPer({
      ...QUERY_MINIMA,
      filters: {
        types: ['income', 'transfer'],
        accountIds: [CONTO, ALTRO_CONTO],
        categoryIds: [CATEGORIA],
        range: null,
      },
    });

    expect(url).toContain(`accountId=${CONTO}`);
    expect(url).toContain(`accountId=${ALTRO_CONTO}`);
    expect(url).toContain('type=income');
    expect(url).toContain('type=transfer');
    expect(url).toContain(`categoryId=${CATEGORIA}`);
  });

  it('manda i due estremi del periodo insieme', async () => {
    const url = await richiestaPer({
      ...QUERY_MINIMA,
      filters: { ...QUERY_MINIMA.filters, range: { from: '2026-09-01', to: '2026-09-30' } },
    });

    expect(url).toContain('from=2026-09-01');
    expect(url).toContain('to=2026-09-30');
  });

  it('senza periodo non manda nessuna delle due date', async () => {
    // Una finestra con un solo capo il server la rifiuta, ed è giusto: meglio un
    // errore di un elenco che mostra un periodo diverso da quello chiesto.
    const url = await richiestaPer(QUERY_MINIMA);

    expect(url).not.toContain('from=');
    expect(url).not.toContain('to=');
  });

  it('manda i tre campi così come sono in schermata', async () => {
    // Gli spazi ai bordi non devono diventare un valore: un campo con dentro un
    // solo spazio è un campo vuoto, e il database non deve sapere la differenza.
    const aggiornato = repository.update(MOVIMENTO, {
      description: '  ',
      counterParty: 'Conad Langhirano',
      category: CATEGORIA,
    });
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();

    const richiesta = http.expectOne(`${MOVIMENTI}/${MOVIMENTO}`);
    expect(richiesta.request.method).toBe('PATCH');
    expect(richiesta.request.body).toEqual({
      description: '',
      counterParty: 'Conad Langhirano',
      categoryId: CATEGORIA,
    });

    richiesta.flush(MOVIMENTO_AGGIORNATO);
    await expect(aggiornato).resolves.toMatchObject({ counterParty: 'Conad Langhirano' });
  });

  it('traduce in kebab-case il campo di ordinamento composto', async () => {
    const url = await richiestaPer({
      ...QUERY_MINIMA,
      sort: { field: 'counterParty', direction: 'asc' },
    });

    expect(url).toContain('sortBy=counter-party');
    expect(url).toContain('sortDirection=asc');
  });
});
