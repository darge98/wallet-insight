import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '@wallet/shared-data-access';
import { money } from '@wallet/shared-domain';
import { asSubscriptionId, SubscriptionRejectedError } from '@wallet/subscriptions-domain';

import { HttpSubscriptionRepository } from './http-subscription.repository';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const ABBONAMENTI = `/api/users/${UTENTE}/subscriptions`;
const NETFLIX = asSubscriptionId('0199ab7c-0000-7000-8000-0000000000a1');

const PAGINA_UTENTI = {
  items: [{ id: UTENTE }],
  total: 1,
  index: 0,
  size: 1,
  pageCount: 1,
};

const NETFLIX_RISPOSTA = {
  id: NETFLIX,
  name: 'Netflix',
  amountCents: 1_399,
  currencyCode: 'EUR',
  every: 1,
  unit: 'month',
  startDate: '2026-01-20',
  endDate: null,
  active: true,
  nextChargeDate: '2026-10-20',
  monthlyCents: 1_399,
  yearlyCents: 16_788,
};

const prossimaRichiesta = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('HttpSubscriptionRepository', () => {
  let repository: HttpSubscriptionRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpSubscriptionRepository,
      ],
    });
    repository = TestBed.inject(HttpSubscriptionRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('traduce un abbonamento con la cadenza e i costi equivalenti', async () => {
    const letti = repository.findAll();
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(ABBONAMENTI).flush([NETFLIX_RISPOSTA]);

    const [netflix] = await letti;
    expect(netflix).toMatchObject({
      cadence: { every: 1, unit: 'month' },
      categoryId: null,
      endDate: null,
      nextChargeDate: '2026-10-20',
      yearlyCost: money(16_788),
    });
  });

  it('chiede il resoconto per la finestra scelta', async () => {
    const letto = repository.overview(7);
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(`${ABBONAMENTI}/overview?days=7`).flush({
      today: '2026-10-05',
      until: '2026-10-11',
      currencyCode: 'EUR',
      activeCount: 1,
      monthlyCents: 1_399,
      yearlyCents: 16_788,
      upcomingCents: 0,
      upcoming: [],
    });

    await expect(letto).resolves.toMatchObject({ until: '2026-10-11', monthlyCost: money(1_399) });
  });

  it('traduce il mese del calendario', async () => {
    const letto = repository.calendar('2026-10');
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(`${ABBONAMENTI}/calendar?month=2026-10`).flush({
      month: '2026-10',
      currencyCode: 'EUR',
      totalCents: 1_399,
      charges: [
        { subscriptionId: NETFLIX, name: 'Netflix', date: '2026-10-20', amountCents: 1_399 },
      ],
    });

    await expect(letto).resolves.toEqual({
      month: '2026-10',
      total: money(1_399),
      charges: [
        { subscriptionId: NETFLIX, name: 'Netflix', date: '2026-10-20', amount: money(1_399) },
      ],
    });
  });

  it('riscrive tutta la definizione, e senza fine non manda il campo', async () => {
    const aggiornato = repository.update(NETFLIX, {
      name: 'Netflix',
      amount: money(1_399),
      cadence: { every: 1, unit: 'month' },
      startDate: '2026-01-20',
      endDate: null,
      categoryId: null,
      accountId: null,
    });
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();

    const request = http.expectOne(`${ABBONAMENTI}/${NETFLIX}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      name: 'Netflix',
      amountCents: 1_399,
      every: 1,
      unit: 'month',
      startDate: '2026-01-20',
    });
    request.flush(NETFLIX_RISPOSTA);

    await expect(aggiornato).resolves.toMatchObject({ active: true });
  });

  it('porta all’utente il motivo di un rifiuto', async () => {
    const creato = repository.create({
      name: 'Netflix',
      amount: money(1_399),
      cadence: { every: 1, unit: 'month' },
      startDate: '2026-10-01',
      endDate: '2026-09-01',
      categoryId: null,
      accountId: null,
    });
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http
      .expectOne(ABBONAMENTI)
      .flush(
        { detail: 'La fine di un abbonamento non può precedere il primo addebito.' },
        { status: 400, statusText: 'Bad Request' },
      );

    await expect(creato).rejects.toEqual(
      new SubscriptionRejectedError(
        'La fine di un abbonamento non può precedere il primo addebito.',
      ),
    );
  });
});
