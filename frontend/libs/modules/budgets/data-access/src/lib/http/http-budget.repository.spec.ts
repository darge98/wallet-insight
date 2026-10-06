import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { asBudgetId, BudgetRejectedError } from '@wallet/budgets-domain';
import { API_BASE_URL } from '@wallet/shared-data-access';
import { asCategoryId, money } from '@wallet/shared-domain';

import { HttpBudgetRepository } from './http-budget.repository';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const BUDGETS = `/api/users/${UTENTE}/budgets`;
const CIBO = asBudgetId('0199ab7c-0000-7000-8000-0000000000b1');
const RISTORANTI = asCategoryId('0199ab7c-0000-7000-8000-0000000000c1');

const PAGINA_UTENTI = {
  items: [{ id: UTENTE }],
  total: 1,
  index: 0,
  size: 1,
  pageCount: 1,
};

const prossimaRichiesta = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('HttpBudgetRepository', () => {
  let repository: HttpBudgetRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpBudgetRepository,
      ],
    });
    repository = TestBed.inject(HttpBudgetRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('traduce il mese con i sotto-budget dentro il principale', async () => {
    const letto = repository.month('2026-10');
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(`${BUDGETS}/month?month=2026-10`).flush({
      month: '2026-10',
      currencyCode: 'EUR',
      limitCents: 50_000,
      spentCents: 39_000,
      remainingCents: 11_000,
      unbudgetedCents: 21_000,
      expensesCents: 60_000,
      budgets: [
        {
          id: CIBO,
          name: 'Cibo',
          limitCents: 50_000,
          spentCents: 39_000,
          remainingCents: 11_000,
          categories: [{ categoryId: RISTORANTI, name: 'Ristoranti', spentCents: 24_000 }],
          children: [
            {
              id: 'fuori',
              name: 'Ristoranti',
              limitCents: 20_000,
              spentCents: 24_000,
              remainingCents: -4_000,
              categories: [],
              children: [],
            },
          ],
        },
      ],
    });

    const mese = await letto;
    expect(mese.unbudgeted).toEqual(money(21_000));
    expect(mese.budgets[0]?.categories[0]?.color).toBeNull();
    expect(mese.budgets[0]?.children[0]?.remaining).toEqual(money(-4_000));
  });

  it('manda solo i campi cambiati', async () => {
    const aggiornato = repository.update(CIBO, { limit: money(60_000) });
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();

    const request = http.expectOne(`${BUDGETS}/${CIBO}`);
    expect(request.request.method).toBe('PATCH');
    expect(JSON.parse(JSON.stringify(request.request.body))).toEqual({ limitCents: 60_000 });
    request.flush({
      id: CIBO,
      name: 'Cibo',
      categoryIds: [RISTORANTI],
      limitCents: 60_000,
      currencyCode: 'EUR',
    });

    await expect(aggiornato).resolves.toMatchObject({ parentId: null, limit: money(60_000) });
  });

  it('porta all’utente il motivo di un rifiuto delle regole', async () => {
    const creato = repository.create({
      parentId: null,
      name: 'Uscite',
      categoryIds: [RISTORANTI],
      limit: money(20_000),
    });
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http
      .expectOne(BUDGETS)
      .flush(
        { detail: 'Una delle categorie scelte è già nel budget «Cibo».' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(creato).rejects.toEqual(
      new BudgetRejectedError('Una delle categorie scelte è già nel budget «Cibo».'),
    );
  });

  it('cancella senza aspettarsi un corpo', async () => {
    const cancellato = repository.remove(CIBO);
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    http.expectOne(`${BUDGETS}/${CIBO}`).flush(null, { status: 204, statusText: 'No Content' });

    await expect(cancellato).resolves.toBeUndefined();
  });
});
