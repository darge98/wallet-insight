import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { asCategoryId, CategoryRejectedError } from '@wallet/shared-domain';

import { API_BASE_URL } from './api-base-url';
import { HttpCategoryRepository } from './http-category.repository';

const UTENTI = '/api/users?page=0&size=1';
const UTENTE = '0199ab7c-0000-7000-8000-000000000001';
const CATEGORIE = `/api/users/${UTENTE}/categories`;
const BAR = asCategoryId('0199ab7c-0000-7000-8000-0000000000aa');
const CAFFE = asCategoryId('0199ab7c-0000-7000-8000-0000000000bb');

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

/** Prima l'utente, poi la richiesta vera: fra le due c'è un microtask. */
const prossimaRichiesta = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('HttpCategoryRepository.remove', () => {
  let repository: HttpCategoryRepository;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        HttpCategoryRepository,
      ],
    });
    repository = TestBed.inject(HttpCategoryRepository);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function rimuovi(into: typeof CAFFE | null) {
    const rimossa = repository.remove(BAR, into);
    http.expectOne(UTENTI).flush(PAGINA_UTENTI);
    await prossimaRichiesta();
    return { rimossa, richiesta: http.expectOne((r) => r.url.startsWith(`${CATEGORIE}/${BAR}`)) };
  }

  it('unendo una sottocategoria dice dove vanno i movimenti', async () => {
    const { rimossa, richiesta } = await rimuovi(CAFFE);

    expect(richiesta.request.method).toBe('DELETE');
    expect(richiesta.request.url).toBe(`${CATEGORIE}/${BAR}?into=${CAFFE}`);
    richiesta.flush(null, { status: 204, statusText: 'No Content' });
    await expect(rimossa).resolves.toBeUndefined();
  });

  it('una macro si toglie senza destinazione', async () => {
    const { rimossa, richiesta } = await rimuovi(null);

    expect(richiesta.request.url).toBe(`${CATEGORIE}/${BAR}`);
    richiesta.flush(null, { status: 204, statusText: 'No Content' });
    await rimossa;
  });

  it('un rifiuto del server arriva col suo motivo', async () => {
    const { rimossa, richiesta } = await rimuovi(CAFFE);

    richiesta.flush(
      { detail: 'La categoria è in un budget.' },
      { status: 409, statusText: 'Conflict' },
    );
    await expect(rimossa).rejects.toEqual(
      new CategoryRejectedError('La categoria è in un budget.'),
    );
  });
});
