import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { asAccountId } from '@wallet/shared-domain';
import { API_BASE_URL } from '@wallet/shared-data-access';

import { HttpAccountRepository } from './http-account.repository';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const CONTI = `/api/users/${UTENTE}/accounts`;
const CONTO = asAccountId('0199ab7c-0000-7000-8000-0000000000aa');

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

const CREDEM = {
  id: CONTO,
  source: 'budget-bakers',
  name: 'Conto stipendio',
  sourceName: 'Credem',
  kind: 'current-account',
  currencyCode: 'EUR',
  initialBalanceCents: 892_001,
  balanceCents: 1_335_207,
  color: '#f97316',
  ibanLast4: '2861',
  archived: false,
  excludedFromStats: false,
};

/**
 * Ogni metodo incatena due richieste: l'identificativo dell'utente e poi quella
 * vera. Fra le due c'è un microtask, quindi il test deve cedere il controllo
 * prima di attendersi la seconda — altrimenti la cerca quando non è ancora partita.
 */
const prossimaRichiesta = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('HttpAccountRepository', () => {
  let repository: HttpAccountRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpAccountRepository,
      ],
    });
    repository = TestBed.inject(HttpAccountRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('traduce i centesimi del server in due importi distinti', async () => {
    const letti = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(CONTI).flush([CREDEM]);

    const conti = await letti;
    // Due saldi e non uno: la differenza è quanto i movimenti dicono di aver
    // spostato, ed è il modo più diretto di accorgersi che ne manca uno.
    expect(conti[0]?.initialBalance).toEqual({ amount: 892_001, currency: 'EUR' });
    expect(conti[0]?.balance).toEqual({ amount: 1_335_207, currency: 'EUR' });
  });

  it('tiene entrambi i nomi, perché dicono cose diverse', async () => {
    const letti = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(CONTI).flush([CREDEM]);

    const conto = (await letti)[0];
    expect(conto?.name).toBe('Conto stipendio');
    expect(conto?.sourceName).toBe('Credem');
  });

  it('traduce in null i campi che il server omette', async () => {
    const letti = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(CONTI).flush([{ ...CREDEM, color: undefined, ibanLast4: undefined }]);

    const conto = (await letti)[0];
    // Nel dominio l'assenza si scrive `null`, mai `undefined`.
    expect(conto?.color).toBeNull();
    expect(conto?.numberLast4).toBeNull();
  });

  it('rifiuta un tipo di conto che il dominio non conosce', async () => {
    const letti = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(CONTI).flush([{ ...CREDEM, kind: 'salvadanaio' }]);

    await expect(letti).rejects.toThrow('salvadanaio');
  });

  it('manda solo il nome quando si rinomina', async () => {
    const rinominato = repository.rename(CONTO, 'Conto stipendio');
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();

    const request = http.expectOne(`${CONTI}/${CONTO}`);
    expect(request.request.method).toBe('PATCH');
    // Il colore non compare: è così che si dice al server «non toccarlo».
    expect(request.request.body).toEqual({ name: 'Conto stipendio' });
    request.flush(CREDEM);

    await expect(rinominato).resolves.toMatchObject({ name: 'Conto stipendio' });
  });

  it('manda solo il colore quando si cambia colore', async () => {
    const colorato = repository.recolor(CONTO, '#0ea5e9');
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();

    const request = http.expectOne(`${CONTI}/${CONTO}`);
    expect(request.request.body).toEqual({ color: '#0ea5e9' });
    request.flush({ ...CREDEM, color: '#0ea5e9' });

    await expect(colorato).resolves.toMatchObject({ color: '#0ea5e9' });
  });

  it('restituisce il conto come lo rimanda il server, non come lo si è mandato', async () => {
    const rinominato = repository.rename(CONTO, '  Conto stipendio  ');
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    // Il server normalizza: è la sua risposta a essere la verità, non ciò che si è digitato.
    http.expectOne(`${CONTI}/${CONTO}`).flush({ ...CREDEM, name: 'Conto stipendio' });

    await expect(rinominato).resolves.toMatchObject({ name: 'Conto stipendio' });
  });
});
